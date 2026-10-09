package fr.cerelios.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Elgamal.PaireDeCles;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
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

    @Test
    void laPartPubliqueEstGPuissanceLaPartSecrete() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE, aleaFixe(0x42));

        // 2047 bits tirés : le bit de poids fort du premier octet 0x42 est déjà nul.
        BigInteger x = new BigInteger("42".repeat(256), 16);
        assertEquals(x, cles.secrete());
        assertEquals(GROUPE.g().modPow(x, GROUPE.p()), cles.publique());
    }

    @Test
    void lesExposantsHorsIntervalleSontRetires() {
        // 0 puis une valeur supérieure à q sont rejetés, la troisième est gardée.
        BigInteger x = GROUPE.tirerExposant(aleaFixe(0x00, 0xFF, 0x01));

        assertEquals(new BigInteger("01".repeat(256), 16), x);
    }

    /** Aléa dont le n-ième tirage ne contient que le n-ième octet donné. */
    private static SecureRandom aleaFixe(int... octets) {
        return new SecureRandom() {
            private int tirage = 0;

            @Override
            public void nextBytes(byte[] tampon) {
                Arrays.fill(tampon, (byte) octets[tirage++]);
            }
        };
    }
}
