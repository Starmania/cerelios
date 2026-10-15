package fr.cerelios.autorite;

import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.octets;

import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.messages.CredentialPublic;
import fr.cerelios.protocole.messages.DeposerCredentials;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.Requete;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Autorité de certification d'un référendum, connue de l'urne par le jeton que lui a remis
 * l'administrateur.
 */
public class AutoriteCertification {

    private final ClientUrne urne;
    private final Groupe groupe;
    private final String referendum;
    private final String jeton;

    public AutoriteCertification(ClientUrne urne, Groupe groupe, String referendum, String jeton) {
        this.urne = urne;
        this.groupe = groupe;
        this.referendum = referendum;
        this.jeton = jeton;
    }

    /** Liste des électeurs, à relire avant de générer quoi que ce soit. */
    public List<String> electeurs() throws IOException, RefusException {
        return urne.appeler(
                        Requete.newBuilder()
                                .setJeton(jeton)
                                .setObtenirElecteurs(
                                        Referendum.newBuilder().setReferendum(referendum)))
                .getElecteurs()
                .getElecteursList();
    }

    /**
     * Génère un credential par électeur, dépose leurs parties publiques et rend les parties
     * secrètes (électeur → secret en hexadécimal), à transmettre à chacun hors de l'urne.
     */
    public Map<String, String> generer() throws IOException, RefusException {
        Map<String, String> secrets = new LinkedHashMap<>();
        DeposerCredentials.Builder depot =
                DeposerCredentials.newBuilder().setReferendum(referendum);
        for (String electeur : electeurs()) {
            PaireDeCles credential = Elgamal.keyGen(groupe);
            secrets.put(electeur, credential.secrete().toString(16));
            depot.addCredentials(
                    CredentialPublic.newBuilder()
                            .setElecteur(electeur)
                            .setCredential(octets(credential.publique())));
        }
        urne.appeler(Requete.newBuilder().setJeton(jeton).setDeposerCredentials(depot));
        return secrets;
    }

    /**
     * Compare les credentials publiés par l'urne à ceux générés ici : un credential en trop
     * permettrait à l'urne de bourrer l'urne. Rend la liste des anomalies, vide si tout concorde.
     */
    public List<String> verifierPublication(Map<String, String> secrets)
            throws IOException, RefusException {
        DonneesPubliques donnees =
                urne.appeler(
                                Requete.newBuilder()
                                        .setObtenirDonneesPubliques(
                                                Referendum.newBuilder().setReferendum(referendum)))
                        .getDonneesPubliques();
        Set<BigInteger> attendus = new HashSet<>();
        for (String secret : secrets.values()) {
            attendus.add(Elgamal.depuisSecrete(groupe, new BigInteger(secret, 16)).publique());
        }
        Set<BigInteger> publies =
                donnees.getCredentialsList().stream()
                        .map(c -> entier(c))
                        .collect(Collectors.toSet());
        List<String> anomalies = new ArrayList<>();
        if (donnees.getReferendum().getNombreElecteurs() != secrets.size()) {
            anomalies.add("Nombre d'électeurs publié différent de la liste certifiée");
        }
        if (!publies.equals(attendus)) {
            anomalies.add("Les credentials publiés ne sont pas ceux générés par l'autorité");
        }
        return anomalies;
    }

    /** Enregistre les secrets, une ligne {@code électeur:secret} par électeur. */
    public static void enregistrer(Map<String, String> secrets, Path fichier) throws IOException {
        Files.write(
                fichier,
                secrets.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).toList());
    }

    /**
     * Relit un fichier écrit par {@link #enregistrer}, par exemple pour renvoyer un credential
     * perdu.
     */
    public static Map<String, String> relire(Path fichier) throws IOException {
        Map<String, String> secrets = new LinkedHashMap<>();
        for (String ligne : Files.readAllLines(fichier)) {
            int separateur = ligne.lastIndexOf(':');
            if (separateur > 0) {
                secrets.put(ligne.substring(0, separateur), ligne.substring(separateur + 1));
            }
        }
        return secrets;
    }
}
