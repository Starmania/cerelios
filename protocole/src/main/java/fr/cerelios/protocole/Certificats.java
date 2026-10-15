package fr.cerelios.protocole;

import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.octets;

import com.google.protobuf.ByteString;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.protocole.messages.Certificat;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/**
 * Certificat du serveur de credentials et clés dérivées de la graine.
 *
 * <p>La graine, envoyée à l'opérateur puis oubliée par le serveur, donne deux clés : une de
 * signature, qui signe le certificat, et une de chiffrement, sous laquelle le serveur garde les
 * credentials secrets. Sans la graine, le serveur ne peut plus les lire : il lui faut l'opérateur
 * pour en renvoyer un.
 */
public final class Certificats {

    private Certificats() {}

    public static PaireDeCles cleSignature(Groupe groupe, String graine) {
        return deriver(groupe, "cerelios/graine/signature/", graine);
    }

    public static PaireDeCles cleChiffrement(Groupe groupe, String graine) {
        return deriver(groupe, "cerelios/graine/chiffrement/", graine);
    }

    private static PaireDeCles deriver(Groupe groupe, String domaine, String graine) {
        BigInteger g = new BigInteger(1, graine.getBytes(StandardCharsets.UTF_8));
        return Elgamal.depuisSecrete(groupe, groupe.defi(domaine, g));
    }

    /** Vrai si {@code graine} est celle dont dérivent les clés du certificat. */
    public static boolean graineDuCertificat(Groupe groupe, String graine, Certificat certificat) {
        return cleSignature(groupe, graine)
                        .publique()
                        .equals(entier(certificat.getCleVerification()))
                && cleChiffrement(groupe, graine)
                        .publique()
                        .equals(entier(certificat.getCleChiffrement()));
    }

    /** SHA-256 des credentials publics triés : ne dépend pas de l'ordre de la liste. */
    public static byte[] empreinteCredentials(Collection<BigInteger> credentials) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            credentials.stream()
                    .sorted()
                    .forEach(
                            c -> {
                                sha256.update(c.toString(16).getBytes(StandardCharsets.US_ASCII));
                                sha256.update((byte) '|');
                            });
            return sha256.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible dans ce JDK", e);
        }
    }

    public static Certificat signer(
            Groupe groupe, String graine, String referendum, Collection<BigInteger> credentials) {
        PaireDeCles signature = cleSignature(groupe, graine);
        Certificat sansSignature =
                Certificat.newBuilder()
                        .setReferendum(referendum)
                        .setNombreElecteurs(credentials.size())
                        .setEmpreinteCredentials(
                                ByteString.copyFrom(empreinteCredentials(credentials)))
                        .setCleVerification(octets(signature.publique()))
                        .setCleChiffrement(octets(cleChiffrement(groupe, graine).publique()))
                        .build();
        return sansSignature.toBuilder()
                .setSignature(
                        Conversions.versProto(
                                Preuves.prouverConnaissance(
                                        groupe, signature, texteSigne(sansSignature))))
                .build();
    }

    /**
     * Vérifie le certificat contre les credentials publiés. Ne dit pas que les clés viennent de la
     * bonne graine : seul l'opérateur peut le vérifier, avec {@link #graineDuCertificat}.
     */
    public static List<String> verifier(
            Groupe groupe,
            Certificat certificat,
            String referendum,
            Collection<BigInteger> credentials,
            int nombreElecteurs) {
        List<String> anomalies = new ArrayList<>();
        if (!Preuves.verifierConnaissance(
                groupe,
                entier(certificat.getCleVerification()),
                Conversions.depuisProto(certificat.getSignature()),
                texteSigne(certificat))) {
            anomalies.add("Signature du certificat invalide");
        }
        if (!certificat.getReferendum().equals(referendum)) {
            anomalies.add("Le certificat est celui d'un autre référendum");
        }
        if (certificat.getNombreElecteurs() != nombreElecteurs
                || credentials.size() != nombreElecteurs) {
            anomalies.add("Le certificat ne porte pas sur le bon nombre d'électeurs");
        }
        if (!MessageDigest.isEqual(
                certificat.getEmpreinteCredentials().toByteArray(),
                empreinteCredentials(credentials))) {
            anomalies.add("Les credentials publiés ne sont pas ceux du certificat");
        }
        return anomalies;
    }

    private static String texteSigne(Certificat c) {
        HexFormat hex = HexFormat.of();
        return String.join(
                "/",
                "certificat",
                c.getReferendum(),
                Integer.toString(c.getNombreElecteurs()),
                hex.formatHex(c.getEmpreinteCredentials().toByteArray()),
                entier(c.getCleVerification()).toString(16),
                entier(c.getCleChiffrement()).toString(16));
    }

    /** Contexte du chiffrement du credential secret de {@code electeur}, stocké par le serveur. */
    public static String contexteCredential(String referendum, String electeur) {
        return referendum + "/credential/" + electeur;
    }
}
