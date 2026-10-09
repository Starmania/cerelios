package fr.cerelios.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Elgamal.PaireDeCles;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ElgamalTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;

    @Test
    void groupeRfc3526EstValide() {
        assertTrue(GROUPE.p().isProbablePrime(40));
        assertTrue(GROUPE.q().isProbablePrime(40));
        assertEquals(GROUPE.p(), GROUPE.q().shiftLeft(1).add(BigInteger.ONE));
        assertEquals(BigInteger.ONE, GROUPE.g().modPow(GROUPE.q(), GROUPE.p()));
    }

    @Test
    void lesPartsPubliquesSeCombinentEnUneSeuleCle() {
        PaireDeCles a = Elgamal.keyGen(GROUPE);
        PaireDeCles b = Elgamal.keyGen(GROUPE);

        assertTrue(a.secrete().signum() > 0 && a.secrete().compareTo(GROUPE.q()) < 0);

        BigInteger cleElection = a.publique().multiply(b.publique()).mod(GROUPE.p());
        BigInteger secretComplet = a.secrete().add(b.secrete()).mod(GROUPE.q());
        assertEquals(GROUPE.g().modPow(secretComplet, GROUPE.p()), cleElection);
    }

    @Test
    void toStringNeReveleQueLaPartPublique() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);

        assertFalse(cles.toString().contains(cles.secrete().toString()));
    }
}
