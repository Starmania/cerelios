package fr.cerelios.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import org.jetbrains.annotations.TestOnly;

/** Chiffrement Elgamal homomorphe. */
public final class Elgamal {

    private Elgamal() {}

    /**
     * Part de clé d'un scrutateur. {@code secrete} ne quitte jamais son programme ; seule {@code
     * publique} est envoyée à l'urne.
     */
    public record PaireDeCles(BigInteger secrete, BigInteger publique) {

        /** N'affiche pas la clé secrète, pour qu'elle ne finisse pas dans un journal. */
        @Override
        public String toString() {
            return "PaireDeCles[publique=" + publique + "]";
        }
    }

    /** Vote chiffré : u = g^r mod p et v = g^vote · y^r mod p. */
    public record Chiffre(BigInteger u, BigInteger v) {}

    /** Génère une part de clé : x tiré au hasard, y = g^x mod p. */
    public static PaireDeCles keyGen(Groupe groupe) {
        return keyGen(groupe, groupe.tirerExposant());
    }

    /** Comme {@link #keyGen(Groupe)}, avec un aléa choisi : réservé aux tests. */
    @TestOnly
    static PaireDeCles keyGen(Groupe groupe, SecureRandom alea) {
        return keyGen(groupe, groupe.tirerExposant(alea));
    }

    private static PaireDeCles keyGen(Groupe groupe, BigInteger x) {
        return new PaireDeCles(x, groupe.g().modPow(x, groupe.p()));
    }

    /**
     * Chiffre un vote 0 ou 1 avec la clé {@code y} du référendum. Le vote est placé en exposant
     * pour que le produit de deux chiffrés soit un chiffré de la somme des votes.
     */
    public static Chiffre encrypt(Groupe groupe, BigInteger y, int vote) {
        if (vote != 0 && vote != 1) {
            throw new IllegalArgumentException("Le vote doit valoir 0 ou 1");
        }
        BigInteger p = groupe.p();
        // Avec une clé hors du sous-groupe d'ordre q (1 ou p - 1 par exemple), v révélerait le vote.
        if (y.compareTo(BigInteger.ONE) <= 0
                || y.compareTo(p) >= 0
                || !y.modPow(groupe.q(), p).equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Clé publique hors du sous-groupe d'ordre q");
        }
        BigInteger r = groupe.tirerExposant();
        BigInteger gVote = vote == 1 ? groupe.g() : BigInteger.ONE;
        return new Chiffre(groupe.g().modPow(r, p), gVote.multiply(y.modPow(r, p)).mod(p));
    }
}
