package fr.cerelios.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Preuves.PreuveDechiffrement;
import fr.cerelios.crypto.Preuves.PreuveSchnorr;
import fr.cerelios.crypto.Preuves.PreuveZeroOuUn;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class PreuvesTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;
    private static final BigInteger P = GROUPE.p();

    @Test
    void laPreuveDeConnaissanceNeVautQuePourSaCleEtSonContexte() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);
        PreuveSchnorr preuve = Preuves.prouverConnaissance(GROUPE, cles, "ref/alice");

        assertTrue(Preuves.verifierConnaissance(GROUPE, cles.publique(), preuve, "ref/alice"));
        assertFalse(Preuves.verifierConnaissance(GROUPE, cles.publique(), preuve, "ref/bob"));
        assertFalse(
                Preuves.verifierConnaissance(
                        GROUPE, Elgamal.keyGen(GROUPE).publique(), preuve, "ref/alice"));
        assertFalse(
                Preuves.verifierConnaissance(
                        GROUPE,
                        cles.publique(),
                        new PreuveSchnorr(preuve.defi(), preuve.reponse().add(BigInteger.ONE)),
                        "ref/alice"));
    }

    @Test
    void laPreuveDeConnaissanceRefuseUneCleHorsDuGroupe() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);
        PreuveSchnorr preuve = Preuves.prouverConnaissance(GROUPE, cles, "ctx");

        assertFalse(
                Preuves.verifierConnaissance(GROUPE, P.subtract(BigInteger.ONE), preuve, "ctx"));
        assertFalse(Preuves.verifierConnaissance(GROUPE, BigInteger.ZERO, preuve, "ctx"));
    }

    @Test
    void unChiffreDeZeroOuDeUnSeProuve() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);

        for (int vote = 0; vote <= 1; vote++) {
            BigInteger r = GROUPE.tirerExposant();
            Chiffre chiffre = Elgamal.encrypt(GROUPE, cles.publique(), vote, r);
            PreuveZeroOuUn preuve =
                    Preuves.prouverZeroOuUn(GROUPE, cles.publique(), chiffre, vote, r, "ctx");

            assertTrue(Preuves.verifierZeroOuUn(GROUPE, cles.publique(), chiffre, preuve, "ctx"));
            assertFalse(
                    Preuves.verifierZeroOuUn(GROUPE, cles.publique(), chiffre, preuve, "autre"));
        }
    }

    @Test
    void unChiffreDeDeuxNePeutPasSeFairePasserPourZeroOuUn() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);
        BigInteger y = cles.publique();
        BigInteger r = GROUPE.tirerExposant();
        // Chiffré de 2, comme le ferait un électeur qui veut compter deux fois.
        Chiffre deux =
                new Chiffre(
                        GROUPE.g().modPow(r, P), GROUPE.g().pow(2).multiply(y.modPow(r, P)).mod(P));

        for (int pretendu = 0; pretendu <= 1; pretendu++) {
            PreuveZeroOuUn preuve = Preuves.prouverZeroOuUn(GROUPE, y, deux, pretendu, r, "ctx");
            assertFalse(Preuves.verifierZeroOuUn(GROUPE, y, deux, preuve, "ctx"));
        }
    }

    @Test
    void unePreuveZeroOuUnNeSuitPasUnAutreChiffre() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);
        BigInteger r = GROUPE.tirerExposant();
        Chiffre chiffre = Elgamal.encrypt(GROUPE, cles.publique(), 1, r);
        PreuveZeroOuUn preuve =
                Preuves.prouverZeroOuUn(GROUPE, cles.publique(), chiffre, 1, r, "ctx");

        Chiffre autre = Elgamal.encrypt(GROUPE, cles.publique(), 1);
        assertFalse(Preuves.verifierZeroOuUn(GROUPE, cles.publique(), autre, preuve, "ctx"));
        Chiffre horsGroupe = new Chiffre(chiffre.u(), P.subtract(chiffre.v()));
        assertFalse(Preuves.verifierZeroOuUn(GROUPE, cles.publique(), horsGroupe, preuve, "ctx"));
    }

    @Test
    void unDechiffrementPartielCorrectSeProuve() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);
        Chiffre chiffre = Elgamal.encrypt(GROUPE, cles.publique(), 1);
        BigInteger partiel = Elgamal.dechiffrementPartiel(GROUPE, chiffre, cles.secrete());

        PreuveDechiffrement preuve = Preuves.prouverDechiffrement(GROUPE, cles, chiffre, "ctx");

        assertTrue(
                Preuves.verifierDechiffrement(
                        GROUPE, cles.publique(), chiffre, partiel, preuve, "ctx"));
        assertFalse(
                Preuves.verifierDechiffrement(
                        GROUPE, cles.publique(), chiffre, partiel, preuve, "autre"));
    }

    @Test
    void unDechiffrementPartielForgeEstDetecte() {
        // Attaque A5 : le scrutateur renvoie v · g^(-m) pour faire publier m.
        PaireDeCles cles = Elgamal.keyGen(GROUPE);
        Chiffre chiffre = Elgamal.encrypt(GROUPE, cles.publique(), 1);
        BigInteger forge = chiffre.v().multiply(GROUPE.g().pow(7).modInverse(P)).mod(P);

        PreuveDechiffrement preuve = Preuves.prouverDechiffrement(GROUPE, cles, chiffre, "ctx");

        assertFalse(
                Preuves.verifierDechiffrement(
                        GROUPE, cles.publique(), chiffre, forge, preuve, "ctx"));
    }
}
