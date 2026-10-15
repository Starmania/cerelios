package fr.cerelios.crypto;

import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import java.math.BigInteger;

/**
 * Preuves à divulgation nulle de connaissance, rendues non interactives par Fiat-Shamir : le défi
 * est l'empreinte de l'énoncé et des engagements ({@link Groupe#defi}).
 *
 * <p>Chaque preuve est liée à un {@code contexte} (le référendum, le credential, le scrutateur) :
 * recopiée dans un autre contexte, elle ne se vérifie plus.
 */
public final class Preuves {

    private Preuves() {}

    /** Preuve de Schnorr : connaissance de x tel que y = g^x. Sert aussi de signature. */
    public record PreuveSchnorr(BigInteger defi, BigInteger reponse) {}

    /** Preuve qu'un chiffré contient 0 ou 1, sans dire lequel (Chaum-Pedersen disjonctive). */
    public record PreuveZeroOuUn(
            BigInteger defi0, BigInteger reponse0, BigInteger defi1, BigInteger reponse1) {}

    /** Preuve que d = u^x pour le même x que y = g^x (Chaum-Pedersen). */
    public record PreuveDechiffrement(BigInteger defi, BigInteger reponse) {}

    private static final String SCHNORR = "cerelios/schnorr/";
    private static final String ZERO_OU_UN = "cerelios/zero-ou-un/";
    private static final String DECHIFFREMENT = "cerelios/dechiffrement/";

    /**
     * Prouve la connaissance de la partie secrète de {@code cles}. Avec un {@code contexte} qui
     * contient le message à signer, c'est une signature de Schnorr.
     */
    public static PreuveSchnorr prouverConnaissance(
            Groupe groupe, PaireDeCles cles, String contexte) {
        BigInteger w = groupe.tirerExposant();
        BigInteger a = groupe.g().modPow(w, groupe.p());
        BigInteger c = groupe.defi(SCHNORR + contexte, cles.publique(), a);
        return new PreuveSchnorr(c, w.subtract(c.multiply(cles.secrete())).mod(groupe.q()));
    }

    /** Vérifie une preuve de {@link #prouverConnaissance} pour la clé publique {@code y}. */
    public static boolean verifierConnaissance(
            Groupe groupe, BigInteger y, PreuveSchnorr preuve, String contexte) {
        if (!groupe.appartient(y) || !exposantValide(groupe, preuve.defi(), preuve.reponse())) {
            return false;
        }
        BigInteger p = groupe.p();
        // g^s · y^c = g^(w - c·x) · g^(x·c) = g^w : on retrouve l'engagement.
        BigInteger a =
                groupe.g().modPow(preuve.reponse(), p).multiply(y.modPow(preuve.defi(), p)).mod(p);
        return preuve.defi().equals(groupe.defi(SCHNORR + contexte, y, a));
    }

    /**
     * Prouve que {@code chiffre}, obtenu avec l'exposant {@code r}, contient {@code vote}, 0 ou 1.
     * La branche du vote réel est prouvée ; l'autre est simulée à partir d'un défi tiré au hasard.
     * Les deux défis doivent avoir pour somme le défi de Fiat-Shamir, si bien qu'au plus une
     * branche peut être simulée.
     */
    static PreuveZeroOuUn prouverZeroOuUn(
            Groupe groupe, BigInteger y, Chiffre chiffre, int vote, BigInteger r, String contexte) {
        BigInteger p = groupe.p();
        BigInteger q = groupe.q();
        BigInteger g = groupe.g();
        BigInteger[] a = new BigInteger[2];
        BigInteger[] b = new BigInteger[2];
        BigInteger[] defis = new BigInteger[2];
        BigInteger[] reponses = new BigInteger[2];

        int autre = 1 - vote;
        defis[autre] = groupe.tirerExposant();
        reponses[autre] = groupe.tirerExposant();
        a[autre] = engagementU(groupe, chiffre, defis[autre], reponses[autre]);
        b[autre] = engagementV(groupe, y, chiffre, autre, defis[autre], reponses[autre]);

        BigInteger w = groupe.tirerExposant();
        a[vote] = g.modPow(w, p);
        b[vote] = y.modPow(w, p);

        BigInteger c = defiZeroOuUn(groupe, y, chiffre, a, b, contexte);
        defis[vote] = c.subtract(defis[autre]).mod(q);
        reponses[vote] = w.subtract(defis[vote].multiply(r)).mod(q);
        return new PreuveZeroOuUn(defis[0], reponses[0], defis[1], reponses[1]);
    }

    /** Vérifie que {@code chiffre} contient 0 ou 1 sous la clé {@code y}. */
    public static boolean verifierZeroOuUn(
            Groupe groupe, BigInteger y, Chiffre chiffre, PreuveZeroOuUn preuve, String contexte) {
        if (!groupe.appartient(y)
                || !groupe.appartient(chiffre.u())
                || !groupe.appartient(chiffre.v())
                || !exposantValide(groupe, preuve.defi0(), preuve.reponse0())
                || !exposantValide(groupe, preuve.defi1(), preuve.reponse1())) {
            return false;
        }
        BigInteger[] defis = {preuve.defi0(), preuve.defi1()};
        BigInteger[] reponses = {preuve.reponse0(), preuve.reponse1()};
        BigInteger[] a = new BigInteger[2];
        BigInteger[] b = new BigInteger[2];
        for (int m = 0; m <= 1; m++) {
            a[m] = engagementU(groupe, chiffre, defis[m], reponses[m]);
            b[m] = engagementV(groupe, y, chiffre, m, defis[m], reponses[m]);
        }
        BigInteger c = defiZeroOuUn(groupe, y, chiffre, a, b, contexte);
        return defis[0].add(defis[1]).mod(groupe.q()).equals(c);
    }

    /** g^s · u^c : vaut g^w quand u = g^r et s = w - c·r. */
    private static BigInteger engagementU(
            Groupe groupe, Chiffre chiffre, BigInteger c, BigInteger s) {
        BigInteger p = groupe.p();
        return groupe.g().modPow(s, p).multiply(chiffre.u().modPow(c, p)).mod(p);
    }

    /** y^s · (v / g^m)^c : vaut y^w quand v = g^m · y^r et s = w - c·r. */
    private static BigInteger engagementV(
            Groupe groupe, BigInteger y, Chiffre chiffre, int m, BigInteger c, BigInteger s) {
        BigInteger p = groupe.p();
        BigInteger vSansVote =
                m == 0 ? chiffre.v() : chiffre.v().multiply(groupe.g().modInverse(p)).mod(p);
        return y.modPow(s, p).multiply(vSansVote.modPow(c, p)).mod(p);
    }

    private static BigInteger defiZeroOuUn(
            Groupe groupe,
            BigInteger y,
            Chiffre chiffre,
            BigInteger[] a,
            BigInteger[] b,
            String contexte) {
        return groupe.defi(
                ZERO_OU_UN + contexte, y, chiffre.u(), chiffre.v(), a[0], b[0], a[1], b[1]);
    }

    /**
     * Prouve qu'un déchiffrement partiel {@code u^x} a été calculé avec la part secrète dont la
     * part publique est publiée : sans cette preuve, un scrutateur pourrait faire publier le
     * résultat de son choix.
     */
    public static PreuveDechiffrement prouverDechiffrement(
            Groupe groupe, PaireDeCles cles, Chiffre chiffre, String contexte) {
        BigInteger p = groupe.p();
        BigInteger w = groupe.tirerExposant();
        BigInteger d = chiffre.u().modPow(cles.secrete(), p);
        BigInteger a = groupe.g().modPow(w, p);
        BigInteger b = chiffre.u().modPow(w, p);
        BigInteger c = groupe.defi(DECHIFFREMENT + contexte, cles.publique(), chiffre.u(), d, a, b);
        return new PreuveDechiffrement(c, w.subtract(c.multiply(cles.secrete())).mod(groupe.q()));
    }

    /** Vérifie que {@code partiel} vaut u^x, x étant la part secrète de {@code clePublique}. */
    public static boolean verifierDechiffrement(
            Groupe groupe,
            BigInteger clePublique,
            Chiffre chiffre,
            BigInteger partiel,
            PreuveDechiffrement preuve,
            String contexte) {
        if (!groupe.appartient(clePublique)
                || !groupe.appartient(chiffre.u())
                || !groupe.appartient(partiel)
                || !exposantValide(groupe, preuve.defi(), preuve.reponse())) {
            return false;
        }
        BigInteger p = groupe.p();
        BigInteger s = preuve.reponse();
        BigInteger c = preuve.defi();
        BigInteger a = groupe.g().modPow(s, p).multiply(clePublique.modPow(c, p)).mod(p);
        BigInteger b = chiffre.u().modPow(s, p).multiply(partiel.modPow(c, p)).mod(p);
        return c.equals(
                groupe.defi(DECHIFFREMENT + contexte, clePublique, chiffre.u(), partiel, a, b));
    }

    private static boolean exposantValide(Groupe groupe, BigInteger... exposants) {
        for (BigInteger e : exposants) {
            if (e == null || e.signum() < 0 || e.compareTo(groupe.q()) >= 0) {
                return false;
            }
        }
        return true;
    }
}
