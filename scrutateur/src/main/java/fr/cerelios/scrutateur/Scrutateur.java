package fr.cerelios.scrutateur;

import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.octets;
import static fr.cerelios.protocole.Conversions.versProto;

import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.Contextes;
import fr.cerelios.protocole.Conversions;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.messages.DeposerCle;
import fr.cerelios.protocole.messages.DeposerDechiffrement;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import fr.cerelios.protocole.messages.ScrutateurPublic;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/**
 * Programme du scrutateur : génère sa part de clé, puis déchiffre partiellement le total. La part
 * secrète ne quitte pas ce programme : elle est gardée dans un fichier que seul le scrutateur
 * détient.
 */
public class Scrutateur {

    private final ClientUrne urne;
    private final Groupe groupe;
    private final String referendum;
    private final String nom;
    private final String jeton;

    /** {@code referendum}, {@code nom} et {@code jeton} viennent de l'e-mail d'invitation. */
    public Scrutateur(ClientUrne urne, Groupe groupe, String referendum, String nom, String jeton) {
        this.urne = urne;
        this.groupe = groupe;
        this.referendum = referendum;
        this.nom = nom;
        this.jeton = jeton;
    }

    /**
     * Génère la part de clé, enregistre sa partie secrète dans {@code fichierSecret} puis dépose la
     * partie publique et sa preuve. Le fichier est écrit avant l'envoi : une clé publiée dont le
     * secret serait perdu bloquerait le dépouillement.
     */
    public BigInteger genererCle(Path fichierSecret) throws IOException, RefusException {
        PaireDeCles cles = Elgamal.keyGen(groupe);
        enregistrer(fichierSecret, cles.secrete());
        urne.appeler(
                Requete.newBuilder()
                        .setJeton(jeton)
                        .setDeposerCle(
                                DeposerCle.newBuilder()
                                        .setReferendum(referendum)
                                        .setClePublique(octets(cles.publique()))
                                        .setPreuve(
                                                versProto(
                                                        Preuves.prouverConnaissance(
                                                                groupe,
                                                                cles,
                                                                Contextes.cleScrutateur(
                                                                        referendum, nom))))));
        return cles.publique();
    }

    /**
     * Vérifie que la part publique de {@code fichierSecret} est celle publiée pour ce scrutateur et
     * qu'elle entre dans la clé du référendum.
     */
    public boolean cleUtilisee(Path fichierSecret) throws IOException, RefusException {
        PaireDeCles cles = relire(fichierSecret);
        ReferendumPublic publique = referendumPublic();
        BigInteger produit = BigInteger.ONE;
        boolean trouvee = false;
        for (ScrutateurPublic s : publique.getScrutateursList()) {
            BigInteger part = entier(s.getClePublique());
            produit = produit.multiply(part).mod(groupe.p());
            trouvee |= s.getNom().equals(nom) && part.equals(cles.publique());
        }
        return trouvee && !publique.getCle().isEmpty() && produit.equals(entier(publique.getCle()));
    }

    /** Déchiffre partiellement le total publié et dépose le résultat avec sa preuve. */
    public void dechiffrer(Path fichierSecret) throws IOException, RefusException {
        PaireDeCles cles = relire(fichierSecret);
        DonneesPubliques donnees = donneesPubliques();
        if (!donnees.hasTotal()) {
            throw new RefusException("Le total n'est pas encore calculé");
        }
        Chiffre total = Conversions.depuisProto(donnees.getTotal());
        urne.appeler(
                Requete.newBuilder()
                        .setJeton(jeton)
                        .setDeposerDechiffrement(
                                DeposerDechiffrement.newBuilder()
                                        .setReferendum(referendum)
                                        .setDechiffrement(
                                                octets(
                                                        Elgamal.dechiffrementPartiel(
                                                                groupe, total, cles.secrete())))
                                        .setPreuve(
                                                versProto(
                                                        Preuves.prouverDechiffrement(
                                                                groupe,
                                                                cles,
                                                                total,
                                                                Contextes.dechiffrement(
                                                                        referendum, nom))))));
    }

    /** Déchiffrement partiel publié pour ce scrutateur, s'il y en a un. */
    public Optional<BigInteger> dechiffrementPublie() throws IOException, RefusException {
        return referendumPublic().getScrutateursList().stream()
                .filter(s -> s.getNom().equals(nom) && !s.getDechiffrement().isEmpty())
                .map(s -> entier(s.getDechiffrement()))
                .findFirst();
    }

    private ReferendumPublic referendumPublic() throws IOException, RefusException {
        return urne.appeler(
                        Requete.newBuilder()
                                .setObtenirReferendum(
                                        Referendum.newBuilder().setReferendum(referendum)))
                .getReferendum();
    }

    private DonneesPubliques donneesPubliques() throws IOException, RefusException {
        return urne.appeler(
                        Requete.newBuilder()
                                .setObtenirDonneesPubliques(
                                        Referendum.newBuilder().setReferendum(referendum)))
                .getDonneesPubliques();
    }

    private PaireDeCles relire(Path fichierSecret) throws IOException {
        return Elgamal.depuisSecrete(
                groupe, new BigInteger(Files.readString(fichierSecret).strip(), 16));
    }

    /**
     * Écrit la part secrète en hexadécimal, lisible par son seul propriétaire si le système le
     * permet.
     */
    private static void enregistrer(Path fichier, BigInteger secrete) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(
                    fichier,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(fichier);
        }
        Files.writeString(fichier, secrete.toString(16) + "\n");
    }
}
