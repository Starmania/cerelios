package fr.cerelios.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
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
        return encrypt(groupe, y, vote, groupe.tirerExposant());
    }

    /** Comme {@link #encrypt(Groupe, BigInteger, int)}, avec un aléa choisi : réservé aux tests. */
    @TestOnly
    static Chiffre encrypt(Groupe groupe, BigInteger y, int vote, SecureRandom alea) {
        return encrypt(groupe, y, vote, groupe.tirerExposant(alea));
    }

    private static Chiffre encrypt(Groupe groupe, BigInteger y, int vote, BigInteger r) {
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
        BigInteger gVote = vote == 1 ? groupe.g() : BigInteger.ONE;
        return new Chiffre(groupe.g().modPow(r, p), gVote.multiply(y.modPow(r, p)).mod(p));
    }

    /**
     * Agrège deux chiffrés en (u × u′ mod p, v × v′ mod p), qui chiffre la somme de leurs votes.
     * Le calcul n'utilise aucun secret : chacun peut le refaire à partir des bulletins publiés.
     */
    public static Chiffre agrege(Groupe groupe, Chiffre a, Chiffre b) {
        // Les chiffrés ne sont pas vérifiés : un seul hors du groupe, (0, 0) par exemple, rend
        // l'agrégat indéchiffrable.
        BigInteger p = groupe.p();
        return new Chiffre(a.u().multiply(b.u()).mod(p), a.v().multiply(b.v()).mod(p));
    }

    /**
     * Déchiffrement partiel d'un scrutateur : u^x mod p, calculé avec sa part secrète {@code x}. Il
     * ne suffit pas à déchiffrer tant qu'il manque la part d'un autre scrutateur.
     */
    public static BigInteger dechiffrementPartiel(Groupe groupe, Chiffre chiffre, BigInteger x) {
        // u n'est pas vérifié : hors du sous-groupe d'ordre q, u^x révélerait la parité de x.
        return chiffre.u().modPow(x, groupe.p());
    }

    /**
     * Combine les déchiffrements partiels de tous les scrutateurs et retrouve la somme des votes :
     * g^somme = v / (produit des u^x) mod p, la somme étant cherchée de 0 à {@code borne}.
     *
     * @throws IllegalArgumentException si aucune somme ne convient : il manque une part, une part
     *     est fausse ou le chiffré dépasse la borne
     */
    public static int decrypt(
            Groupe groupe, Chiffre chiffre, List<BigInteger> partiels, int borne) {
        BigInteger p = groupe.p();
        BigInteger masque =
                partiels.stream().reduce(BigInteger.ONE, (a, b) -> a.multiply(b).mod(p));
        if (masque.signum() == 0) {
            throw new IllegalArgumentException("Déchiffrement partiel nul modulo p");
        }
        BigInteger gSomme = chiffre.v().multiply(masque.modInverse(p)).mod(p);
        // Recherche exhaustive : suffit tant que la borne est le nombre de bulletins reçus.
        BigInteger puissance = BigInteger.ONE;
        for (int somme = 0; somme <= borne; somme++) {
            if (puissance.equals(gSomme)) {
                return somme;
            }
            puissance = puissance.multiply(groupe.g()).mod(p);
        }
        throw new IllegalArgumentException("Aucune somme entre 0 et " + borne + " ne correspond");
    }
}
