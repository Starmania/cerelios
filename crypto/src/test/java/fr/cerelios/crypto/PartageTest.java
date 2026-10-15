package fr.cerelios.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Partage.Polynome;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PartageTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;
    private static final BigInteger P = GROUPE.p();

    /** Génération de clé à 3 scrutateurs, seuil 2, sans réseau. */
    private record Cles(BigInteger y, List<BigInteger> parts, List<List<BigInteger>> engagements) {}

    private static Cles generer(int n, int seuil) {
        List<Polynome> polynomes = new ArrayList<>();
        List<List<BigInteger>> engagements = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Polynome f = Partage.tirer(GROUPE, seuil);
            polynomes.add(f);
            engagements.add(Partage.engagements(GROUPE, f));
        }
        List<BigInteger> parts = new ArrayList<>();
        for (int j = 1; j <= n; j++) {
            BigInteger xj = BigInteger.ZERO;
            for (int i = 0; i < n; i++) {
                BigInteger s = Partage.evaluer(GROUPE, polynomes.get(i), j);
                assertTrue(Partage.verifierPart(GROUPE, engagements.get(i), j, s));
                xj = xj.add(s).mod(GROUPE.q());
            }
            parts.add(xj);
            assertEquals(GROUPE.g().modPow(xj, P), Partage.clePartielle(GROUPE, engagements, j));
        }
        return new Cles(Partage.clePublique(GROUPE, engagements), parts, engagements);
    }

    @Test
    void nImporteQuelsSeuilScrutateursDechiffrent() throws DechiffrementImpossibleException {
        Cles cles = generer(3, 2);
        Chiffre total =
                Elgamal.agrege(
                        GROUPE,
                        Elgamal.encrypt(GROUPE, cles.y(), 1),
                        Elgamal.encrypt(GROUPE, cles.y(), 1));

        for (List<Integer> sousEnsemble :
                List.of(List.of(1, 2), List.of(1, 3), List.of(2, 3), List.of(1, 2, 3))) {
            Map<Integer, BigInteger> partiels = new LinkedHashMap<>();
            for (int j : sousEnsemble) {
                partiels.put(
                        j, Elgamal.dechiffrementPartiel(GROUPE, total, cles.parts().get(j - 1)));
            }
            assertEquals(2, Elgamal.decrypt(GROUPE, total, Partage.ponderer(GROUPE, partiels), 2));
        }
    }

    @Test
    void unScrutateurSeulNeDechiffrePasSousUnSeuilDeDeux() {
        Cles cles = generer(3, 2);
        Chiffre chiffre = Elgamal.encrypt(GROUPE, cles.y(), 1);

        Map<Integer, BigInteger> seul =
                Map.of(2, Elgamal.dechiffrementPartiel(GROUPE, chiffre, cles.parts().get(1)));

        assertThrows(
                DechiffrementImpossibleException.class,
                () -> Elgamal.decrypt(GROUPE, chiffre, Partage.ponderer(GROUPE, seul), 1));
    }

    @Test
    void unePartFausseEstDetectee() {
        Polynome f = Partage.tirer(GROUPE, 3);
        List<BigInteger> engagements = Partage.engagements(GROUPE, f);
        BigInteger part = Partage.evaluer(GROUPE, f, 4);

        assertTrue(Partage.verifierPart(GROUPE, engagements, 4, part));
        assertFalse(Partage.verifierPart(GROUPE, engagements, 5, part));
        assertFalse(Partage.verifierPart(GROUPE, engagements, 4, part.add(BigInteger.ONE)));
    }

    @Test
    void evaluerSuitLaDefinitionDuPolynome() {
        Polynome f =
                new Polynome(List.of(BigInteger.valueOf(5), BigInteger.valueOf(3), BigInteger.TWO));

        // 5 + 3·4 + 2·16 = 49
        assertEquals(BigInteger.valueOf(49), Partage.evaluer(GROUPE, f, 4));
    }

    @Test
    void leSeuilVautAuMoinsUn() {
        assertThrows(IllegalArgumentException.class, () -> Partage.tirer(GROUPE, 0));
    }
}
