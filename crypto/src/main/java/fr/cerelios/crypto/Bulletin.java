package fr.cerelios.crypto;

import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Preuves.PreuveSchnorr;
import fr.cerelios.crypto.Preuves.PreuveZeroOuUn;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Bulletin d'un référendum Oui/Non : le vote chiffré (1 pour Oui, 0 pour Non), la preuve qu'il vaut
 * 0 ou 1, et la signature du credential de l'électeur.
 *
 * <p>Le credential est une paire de clés : sa partie publique figure sur la liste publiée par
 * l'autorité de certification, sa partie secrète n'est connue que de l'électeur. La signature
 * empêche l'urne de fabriquer un bulletin, et la preuve d'y glisser un « 5 » qui compterait cinq
 * Oui.
 */
public record Bulletin(
        BigInteger credential, Chiffre chiffre, PreuveZeroOuUn preuve, PreuveSchnorr signature) {

    /** Chiffre le vote {@code oui} et le signe avec le credential de l'électeur. */
    public static Bulletin creer(
            Groupe groupe, BigInteger y, String referendum, PaireDeCles credential, boolean oui) {
        int vote = oui ? 1 : 0;
        BigInteger r = groupe.tirerExposant();
        Chiffre chiffre = Elgamal.encrypt(groupe, y, vote, r);
        // Le credential est dans le contexte de la preuve : elle ne resservira pas à un autre
        // électeur.
        PreuveZeroOuUn preuve =
                Preuves.prouverZeroOuUn(
                        groupe, y, chiffre, vote, r, contexte(referendum, credential.publique()));
        PreuveSchnorr signature =
                Preuves.prouverConnaissance(
                        groupe, credential, messageSigne(referendum, chiffre, preuve));
        return new Bulletin(credential.publique(), chiffre, preuve, signature);
    }

    /**
     * Vrai si le bulletin est bien formé pour ce référendum : éléments dans le groupe, preuve de 0
     * ou 1 et signature valides. Ne dit pas si le credential est autorisé à voter : c'est à l'urne
     * de le chercher dans la liste publiée.
     */
    public boolean estValide(Groupe groupe, BigInteger y, String referendum) {
        return groupe.appartient(credential)
                && !credential.equals(BigInteger.ONE)
                && Preuves.verifierZeroOuUn(
                        groupe, y, chiffre, preuve, contexte(referendum, credential))
                && Preuves.verifierConnaissance(
                        groupe, credential, signature, messageSigne(referendum, chiffre, preuve));
    }

    /**
     * Traceur du bulletin : SHA-256 de tous ses champs, en hexadécimal. L'électeur le note et le
     * retrouve ensuite dans l'urne publiée.
     */
    public String empreinte() {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible dans ce JDK", e);
        }
        BigInteger[] champs = {
            credential,
            chiffre.u(),
            chiffre.v(),
            preuve.defi0(),
            preuve.reponse0(),
            preuve.defi1(),
            preuve.reponse1(),
            signature.defi(),
            signature.reponse()
        };
        for (BigInteger champ : champs) {
            sha256.update(champ.toString(16).getBytes(StandardCharsets.US_ASCII));
            sha256.update((byte) '|');
        }
        return HexFormat.of().formatHex(sha256.digest());
    }

    private static String contexte(String referendum, BigInteger credential) {
        return referendum + "/" + credential.toString(16);
    }

    /** Ce que signe le credential : le référendum, le chiffré et sa preuve. */
    private static String messageSigne(String referendum, Chiffre chiffre, PreuveZeroOuUn preuve) {
        return String.join(
                "/",
                referendum,
                chiffre.u().toString(16),
                chiffre.v().toString(16),
                preuve.defi0().toString(16),
                preuve.reponse0().toString(16),
                preuve.defi1().toString(16),
                preuve.reponse1().toString(16));
    }
}
