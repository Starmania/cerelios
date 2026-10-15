package fr.cerelios.crypto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Partage de secret à seuil (Shamir), vérifiable (Feldman), pour qu'il suffise de {@code seuil}
 * scrutateurs sur {@code n} pour déchiffrer.
 *
 * <p>Chaque scrutateur i tire un polynôme f_i de degré seuil - 1 et envoie f_i(j) au scrutateur j.
 * La part de j est x_j = somme des f_i(j) ; la clé secrète du référendum, somme des f_i(0),
 * n'existe nulle part. Les engagements g^(coefficients) publiés permettent à chacun de vérifier les
 * parts reçues, et à tous de calculer la clé publique g^(x_j) de chaque part.
 */
public final class Partage {

    private Partage() {}

    /** Coefficients a_0 … a_(seuil-1) d'un polynôme secret, modulo q. */
    public record Polynome(List<BigInteger> coefficients) {

        /** N'affiche pas les coefficients, qui sont secrets. */
        @Override
        public String toString() {
            return "Polynome[degre=" + (coefficients.size() - 1) + "]";
        }
    }

    /** Tire un polynôme de degré {@code seuil} - 1. */
    public static Polynome tirer(Groupe groupe, int seuil) {
        if (seuil < 1) {
            throw new IllegalArgumentException("Seuil inférieur à 1");
        }
        List<BigInteger> coefficients = new ArrayList<>();
        for (int i = 0; i < seuil; i++) {
            coefficients.add(groupe.tirerExposant());
        }
        return new Polynome(List.copyOf(coefficients));
    }

    /** f(x) mod q, par la méthode de Horner. */
    public static BigInteger evaluer(Groupe groupe, Polynome polynome, int x) {
        BigInteger resultat = BigInteger.ZERO;
        BigInteger bx = BigInteger.valueOf(x);
        List<BigInteger> a = polynome.coefficients();
        for (int i = a.size() - 1; i >= 0; i--) {
            resultat = resultat.multiply(bx).add(a.get(i)).mod(groupe.q());
        }
        return resultat;
    }

    /** Engagements publics g^(a_0) … g^(a_(seuil-1)) ; le premier est la part de clé publique. */
    public static List<BigInteger> engagements(Groupe groupe, Polynome polynome) {
        return polynome.coefficients().stream().map(a -> groupe.g().modPow(a, groupe.p())).toList();
    }

    /** g^(f(x)) calculé à partir des seuls engagements : produit des C_l^(x^l). */
    public static BigInteger evaluerEngagements(
            Groupe groupe, List<BigInteger> engagements, int x) {
        BigInteger p = groupe.p();
        BigInteger resultat = BigInteger.ONE;
        BigInteger puissance = BigInteger.ONE;
        BigInteger bx = BigInteger.valueOf(x);
        for (BigInteger c : engagements) {
            resultat = resultat.multiply(c.modPow(puissance, p)).mod(p);
            puissance = puissance.multiply(bx).mod(groupe.q());
        }
        return resultat;
    }

    /** Vrai si {@code part} est bien f(x) pour le polynôme engagé par {@code engagements}. */
    public static boolean verifierPart(
            Groupe groupe, List<BigInteger> engagements, int x, BigInteger part) {
        return part.signum() >= 0
                && part.compareTo(groupe.q()) < 0
                && groupe.g()
                        .modPow(part, groupe.p())
                        .equals(evaluerEngagements(groupe, engagements, x));
    }

    /**
     * Clé publique g^(x_j) de la part du scrutateur d'indice {@code j}, calculée par n'importe qui
     * à partir des engagements de tous les scrutateurs.
     */
    public static BigInteger clePartielle(
            Groupe groupe, List<List<BigInteger>> engagementsDeChacun, int j) {
        BigInteger p = groupe.p();
        return engagementsDeChacun.stream()
                .map(e -> evaluerEngagements(groupe, e, j))
                .reduce(BigInteger.ONE, (a, b) -> a.multiply(b).mod(p));
    }

    /** Clé publique du référendum : produit des premiers engagements, g^(somme des f_i(0)). */
    public static BigInteger clePublique(
            Groupe groupe, List<List<BigInteger>> engagementsDeChacun) {
        BigInteger p = groupe.p();
        return engagementsDeChacun.stream()
                .map(List::getFirst)
                .reduce(BigInteger.ONE, (a, b) -> a.multiply(b).mod(p));
    }

    /** Coefficient de Lagrange en 0 de l'indice {@code j} parmi {@code indices}, modulo q. */
    public static BigInteger lagrange(Groupe groupe, Collection<Integer> indices, int j) {
        BigInteger q = groupe.q();
        BigInteger numerateur = BigInteger.ONE;
        BigInteger denominateur = BigInteger.ONE;
        for (int m : indices) {
            if (m != j) {
                numerateur = numerateur.multiply(BigInteger.valueOf(m)).mod(q);
                denominateur = denominateur.multiply(BigInteger.valueOf(m - j)).mod(q);
            }
        }
        return numerateur.multiply(denominateur.modInverse(q)).mod(q);
    }

    /**
     * Élève chaque déchiffrement partiel u^(x_j) à son coefficient de Lagrange : le produit du
     * résultat vaut u^x, comme si la clé secrète entière avait servi. Le résultat se passe tel quel
     * à {@link Elgamal#decrypt}.
     *
     * @param partiels déchiffrements partiels par indice de scrutateur, au moins {@code seuil}
     */
    public static List<BigInteger> ponderer(Groupe groupe, Map<Integer, BigInteger> partiels) {
        return partiels.entrySet().stream()
                .map(
                        e ->
                                e.getValue()
                                        .modPow(
                                                lagrange(groupe, partiels.keySet(), e.getKey()),
                                                groupe.p()))
                .toList();
    }
}
