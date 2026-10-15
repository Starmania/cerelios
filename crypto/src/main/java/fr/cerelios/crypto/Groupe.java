package fr.cerelios.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.jetbrains.annotations.TestOnly;

/**
 * Groupe dans lequel se font les calculs Elgamal : {@code g} engendre le sous-groupe d'ordre
 * premier {@code q} des entiers modulo {@code p}.
 */
public record Groupe(BigInteger p, BigInteger q, BigInteger g) {

    private static final SecureRandom ALEA = new SecureRandom();

    private static final BigInteger P_2048 = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
                    + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                    + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
                    + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                    + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
                    + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
                    + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
                    + "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
                    + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9"
                    + "DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
                    + "15728E5A8AACAA68FFFFFFFFFFFFFFFF",
            16);

    /** Groupe 14 de la RFC 3526 (2048 bits) : p = 2q + 1 et g = 2 est d'ordre q. */
    public static final Groupe RFC_3526 = new Groupe(P_2048, P_2048.shiftRight(1), BigInteger.TWO);

    /**
     * Vrai si {@code x} est dans le sous-groupe d'ordre q. Tout élément reçu d'un autre programme
     * passe par ce test : hors du sous-groupe, un calcul avec un secret en révélerait une partie.
     */
    public boolean appartient(BigInteger x) {
        return x.signum() > 0 && x.compareTo(p) < 0 && x.modPow(q, p).equals(BigInteger.ONE);
    }

    /**
     * Défi d'une preuve non interactive (heuristique de Fiat-Shamir) : SHA-256 du domaine puis de
     * chaque élément précédé de sa longueur, réduit modulo q. Le domaine sépare les types de
     * preuve, pour qu'une preuve d'un type ne puisse pas resservir comme preuve d'un autre.
     */
    public BigInteger defi(String domaine, BigInteger... elements) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible dans ce JDK", e);
        }
        ajouter(sha256, domaine.getBytes(StandardCharsets.UTF_8));
        for (BigInteger element : elements) {
            ajouter(sha256, element.toByteArray());
        }
        return new BigInteger(1, sha256.digest()).mod(q);
    }

    /** Longueur sur 4 octets puis contenu : deux suites différentes ne donnent pas les mêmes octets. */
    private static void ajouter(MessageDigest sha256, byte[] octets) {
        sha256.update(ByteBuffer.allocate(Integer.BYTES).putInt(octets.length).array());
        sha256.update(octets);
    }

    /** Tire un exposant uniformément dans [1, q - 1]. */
    public BigInteger tirerExposant() {
        return tirerExposant(ALEA);
    }

    /** Comme {@link #tirerExposant()}, avec un aléa choisi : réservé aux tests. */
    @TestOnly
    BigInteger tirerExposant(SecureRandom alea) {
        BigInteger x;
        do {
            x = new BigInteger(q.bitLength(), alea);
        } while (x.signum() == 0 || x.compareTo(q) >= 0);
        return x;
    }
}
