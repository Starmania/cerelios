package fr.cerelios.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Optional;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chiffrement d'un message quelconque pour le détenteur d'une clé Elgamal h = g^x : échange
 * Diffie-Hellman éphémère (R = g^r, secret partagé h^r = R^x) puis AES-256-GCM.
 *
 * <p>Le {@code contexte} est authentifié sans être chiffré : un message recopié dans un autre
 * contexte (autre destinataire, autre référendum) ne se déchiffre plus.
 */
public final class ChiffrementHybride {

    private static final SecureRandom ALEA = new SecureRandom();
    private static final int OCTETS_NONCE = 12;
    private static final int BITS_TAG = 128;

    private ChiffrementHybride() {}

    public record Message(BigInteger r, byte[] nonce, byte[] donnees) {}

    public static Message chiffrer(
            Groupe groupe, BigInteger destinataire, byte[] clair, String contexte) {
        if (!groupe.appartient(destinataire) || destinataire.equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Clé du destinataire hors du sous-groupe d'ordre q");
        }
        BigInteger exposant = groupe.tirerExposant();
        BigInteger r = groupe.g().modPow(exposant, groupe.p());
        byte[] nonce = new byte[OCTETS_NONCE];
        ALEA.nextBytes(nonce);
        byte[] cle = deriverCle(destinataire.modPow(exposant, groupe.p()));
        try {
            return new Message(
                    r, nonce, aes(Cipher.ENCRYPT_MODE, cle, nonce, contexte).doFinal(clair));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM indisponible dans ce JDK", e);
        }
    }

    /**
     * Déchiffre avec la clé secrète {@code x} ; vide si le message a été modifié ou n'est pas pour
     * nous.
     */
    public static Optional<byte[]> dechiffrer(
            Groupe groupe, BigInteger x, Message message, String contexte) {
        if (!groupe.appartient(message.r()) || message.nonce().length != OCTETS_NONCE) {
            return Optional.empty();
        }
        byte[] cle = deriverCle(message.r().modPow(x, groupe.p()));
        try {
            return Optional.of(
                    aes(Cipher.DECRYPT_MODE, cle, message.nonce(), contexte)
                            .doFinal(message.donnees()));
        } catch (AEADBadTagException e) {
            return Optional.empty();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM indisponible dans ce JDK", e);
        }
    }

    private static Cipher aes(int mode, byte[] cle, byte[] nonce, String contexte)
            throws GeneralSecurityException {
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(mode, new SecretKeySpec(cle, "AES"), new GCMParameterSpec(BITS_TAG, nonce));
        aes.updateAAD(contexte.getBytes(StandardCharsets.UTF_8));
        return aes;
    }

    private static byte[] deriverCle(BigInteger secretPartage) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update("cerelios/hybride/".getBytes(StandardCharsets.UTF_8));
            return sha256.digest(secretPartage.toByteArray());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponible dans ce JDK", e);
        }
    }
}
