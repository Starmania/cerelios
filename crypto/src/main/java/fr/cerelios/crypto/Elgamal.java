package fr.cerelios.crypto;

import java.math.BigInteger;

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

    /** Génère une part de clé : x tiré au hasard, y = g^x mod p. */
    public static PaireDeCles keyGen(Groupe groupe) {
        BigInteger x = groupe.tirerExposant();
        return new PaireDeCles(x, groupe.g().modPow(x, groupe.p()));
    }
}
