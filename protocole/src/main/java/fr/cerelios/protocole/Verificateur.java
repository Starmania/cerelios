package fr.cerelios.protocole;

import static fr.cerelios.protocole.Conversions.depuisProto;
import static fr.cerelios.protocole.Conversions.entier;

import com.google.protobuf.ByteString;
import fr.cerelios.crypto.DechiffrementImpossibleException;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.protocole.messages.Bulletin;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.ScrutateurPublic;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vérifications d'un auditeur sur les données publiques d'un référendum : n'importe qui peut les
 * refaire, sans aucun secret. Chaque anomalie est décrite par une phrase ; une liste vide veut dire
 * que tout est cohérent.
 */
public final class Verificateur {

    private final Groupe groupe;

    public Verificateur(Groupe groupe) {
        this.groupe = groupe;
    }

    public List<String> verifier(DonneesPubliques donnees) {
        List<String> anomalies = new ArrayList<>();
        ReferendumPublic referendum = donnees.getReferendum();
        if (referendum.getCle().isEmpty()) {
            anomalies.add("Référendum non validé : rien à vérifier");
            return anomalies;
        }
        BigInteger cle = entier(referendum.getCle());
        verifierScrutateurs(referendum, cle, anomalies);
        Set<BigInteger> credentials = verifierCredentials(donnees, anomalies);
        Elgamal.Chiffre total = verifierBulletins(donnees, cle, credentials, anomalies);
        if (donnees.hasTotal()) {
            verifierDepouillement(donnees, total, anomalies);
        }
        return anomalies;
    }

    private void verifierScrutateurs(
            ReferendumPublic referendum, BigInteger cle, List<String> anomalies) {
        BigInteger produit = BigInteger.ONE;
        for (ScrutateurPublic s : referendum.getScrutateursList()) {
            if (s.getClePublique().isEmpty()) {
                anomalies.add("Le scrutateur " + s.getNom() + " n'a pas de clé publique");
                continue;
            }
            BigInteger part = entier(s.getClePublique());
            if (!Preuves.verifierConnaissance(
                    groupe,
                    part,
                    depuisProto(s.getPreuveCle()),
                    Contextes.cleScrutateur(referendum.getId(), s.getNom()))) {
                anomalies.add("Preuve de clé invalide pour le scrutateur " + s.getNom());
            }
            produit = produit.multiply(part).mod(groupe.p());
        }
        if (!produit.equals(cle)) {
            anomalies.add("La clé du référendum n'est pas le produit des clés des scrutateurs");
        }
    }

    private Set<BigInteger> verifierCredentials(DonneesPubliques donnees, List<String> anomalies) {
        Set<BigInteger> credentials = new HashSet<>();
        for (ByteString octets : donnees.getCredentialsList()) {
            BigInteger credential = entier(octets);
            if (!groupe.appartient(credential) || !credentials.add(credential)) {
                anomalies.add("Credential publié invalide ou en double");
            }
        }
        if (credentials.size() != donnees.getReferendum().getNombreElecteurs()) {
            anomalies.add(
                    "Le nombre de credentials ("
                            + credentials.size()
                            + ") diffère du nombre d'électeurs ("
                            + donnees.getReferendum().getNombreElecteurs()
                            + ")");
        }
        return credentials;
    }

    /** Vérifie chaque bulletin et rend leur agrégat, à comparer au total publié. */
    private Elgamal.Chiffre verifierBulletins(
            DonneesPubliques donnees,
            BigInteger cle,
            Set<BigInteger> credentials,
            List<String> anomalies) {
        String id = donnees.getReferendum().getId();
        Set<BigInteger> dejaVus = new HashSet<>();
        Elgamal.Chiffre total = new Elgamal.Chiffre(BigInteger.ONE, BigInteger.ONE);
        for (Bulletin message : donnees.getBulletinsList()) {
            fr.cerelios.crypto.Bulletin bulletin = depuisProto(message);
            String traceur = bulletin.empreinte();
            if (!bulletin.estValide(groupe, cle, id)) {
                anomalies.add("Bulletin invalide : " + traceur);
            }
            if (!credentials.contains(bulletin.credential())) {
                anomalies.add("Bulletin d'un credential inconnu : " + traceur);
            }
            if (!dejaVus.add(bulletin.credential())) {
                anomalies.add("Deux bulletins pour le même credential : " + traceur);
            }
            total = Elgamal.agrege(groupe, total, bulletin.chiffre());
        }
        if (donnees.getBulletinsCount() != donnees.getReferendum().getNombreBulletins()) {
            anomalies.add("Le nombre de bulletins annoncé ne correspond pas à l'urne publiée");
        }
        return total;
    }

    private void verifierDepouillement(
            DonneesPubliques donnees, Elgamal.Chiffre totalRecalcule, List<String> anomalies) {
        ReferendumPublic referendum = donnees.getReferendum();
        Elgamal.Chiffre total = depuisProto(donnees.getTotal());
        if (!total.equals(totalRecalcule)) {
            anomalies.add("Le total chiffré n'est pas l'agrégat des bulletins publiés");
        }
        List<BigInteger> partiels = new ArrayList<>();
        for (ScrutateurPublic s : referendum.getScrutateursList()) {
            if (s.getDechiffrement().isEmpty()) {
                continue;
            }
            BigInteger partiel = entier(s.getDechiffrement());
            if (!Preuves.verifierDechiffrement(
                    groupe,
                    entier(s.getClePublique()),
                    total,
                    partiel,
                    depuisProto(s.getPreuveDechiffrement()),
                    Contextes.dechiffrement(referendum.getId(), s.getNom()))) {
                anomalies.add("Déchiffrement partiel invalide pour le scrutateur " + s.getNom());
            }
            partiels.add(partiel);
        }
        if (!referendum.hasResultat()) {
            return;
        }
        if (partiels.size() != referendum.getScrutateursCount()) {
            anomalies.add("Résultat publié sans le déchiffrement de tous les scrutateurs");
            return;
        }
        int oui = referendum.getResultat().getOui();
        int non = referendum.getResultat().getNon();
        if (oui + non != donnees.getBulletinsCount()) {
            anomalies.add("Oui + Non ne fait pas le nombre de bulletins");
        }
        try {
            int somme = Elgamal.decrypt(groupe, total, partiels, referendum.getNombreElecteurs());
            if (somme != oui) {
                anomalies.add("Le total déchiffré donne " + somme + " Oui, pas " + oui);
            }
        } catch (DechiffrementImpossibleException | IllegalArgumentException e) {
            anomalies.add("Le total ne se déchiffre pas : " + e.getMessage());
        }
    }
}
