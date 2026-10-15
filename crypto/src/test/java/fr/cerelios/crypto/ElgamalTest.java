package fr.cerelios.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
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
    void encryptCacheLeVoteSousLaCleDuReferendum() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);

        for (int vote = 0; vote <= 1; vote++) {
            Chiffre chiffre = Elgamal.encrypt(GROUPE, cles.publique(), vote);

            assertEquals(GROUPE.g().pow(vote), gPuissanceVote(chiffre, cles.secrete()));
        }
        assertNotEquals(
                Elgamal.encrypt(GROUPE, cles.publique(), 1),
                Elgamal.encrypt(GROUPE, cles.publique(), 1));
    }

    @Test
    void encryptRefuseUnVoteOuUneCleInvalide() {
        BigInteger y = Elgamal.keyGen(GROUPE).publique();

        assertThrows(IllegalArgumentException.class, () -> Elgamal.encrypt(GROUPE, y, 2));
        assertThrows(IllegalArgumentException.class, () -> Elgamal.encrypt(GROUPE, y, -1));
        assertThrows(IllegalArgumentException.class, () -> Elgamal.encrypt(GROUPE, y.negate(), 1));
        assertThrows(
                IllegalArgumentException.class, () -> Elgamal.encrypt(GROUPE, BigInteger.ZERO, 1));
        assertThrows(
                IllegalArgumentException.class, () -> Elgamal.encrypt(GROUPE, BigInteger.ONE, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> Elgamal.encrypt(GROUPE, GROUPE.p().subtract(BigInteger.ONE), 1));
        assertThrows(IllegalArgumentException.class, () -> Elgamal.encrypt(GROUPE, GROUPE.p(), 1));
    }

    @Test
    void encryptCalculeUEtVAvecLExposantTire() {
        BigInteger p = GROUPE.p();
        BigInteger y = Elgamal.keyGen(GROUPE, aleaFixe(0x01)).publique();

        // Même tirage que dans laPartPubliqueEstGPuissanceLaPartSecrete.
        BigInteger r = new BigInteger("42".repeat(256), 16);
        for (int vote = 0; vote <= 1; vote++) {
            Chiffre chiffre = Elgamal.encrypt(GROUPE, y, vote, aleaFixe(0x42));

            assertEquals(GROUPE.g().modPow(r, p), chiffre.u());
            assertEquals(GROUPE.g().pow(vote).multiply(y.modPow(r, p)).mod(p), chiffre.v());
        }
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

    @Test
    void agregeMultiplieLesComposantesModuloP() {
        BigInteger p = GROUPE.p();
        Chiffre a = new Chiffre(p.subtract(BigInteger.ONE), p.subtract(BigInteger.TWO));
        Chiffre b = new Chiffre(p.subtract(BigInteger.ONE), BigInteger.TWO);

        // (-1)(-1) = 1 et (-2)(2) = -4 : les deux produits dépassent p avant réduction.
        assertEquals(
                new Chiffre(BigInteger.ONE, p.subtract(BigInteger.valueOf(4))),
                Elgamal.agrege(GROUPE, a, b));
    }

    @Test
    void agregeDeDeuxBulletinsChiffreLaSommeDesVotes() {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);

        for (int vote1 = 0; vote1 <= 1; vote1++) {
            for (int vote2 = 0; vote2 <= 1; vote2++) {
                Chiffre agregat =
                        Elgamal.agrege(
                                GROUPE,
                                Elgamal.encrypt(GROUPE, cles.publique(), vote1),
                                Elgamal.encrypt(GROUPE, cles.publique(), vote2));

                assertEquals(
                        GROUPE.g().pow(vote1 + vote2), gPuissanceVote(agregat, cles.secrete()));
            }
        }
    }

    @Test
    void leDechiffrementDeLAgregatDeNBulletinsEstLaSommeDesNVotes()
            throws DechiffrementImpossibleException {
        PaireDeCles cles = Elgamal.keyGen(GROUPE);
        int[] votes = {1, 0, 1, 1, 0, 1};

        Chiffre agregat = totalChiffre(cles.publique(), votes);

        BigInteger partiel = Elgamal.dechiffrementPartiel(GROUPE, agregat, cles.secrete());
        assertEquals(4, Elgamal.decrypt(GROUPE, agregat, List.of(partiel), votes.length));
    }

    @Test
    void agregeNeDependPasDeLOrdreDesBulletins() {
        BigInteger y = Elgamal.keyGen(GROUPE).publique();
        Chiffre a = Elgamal.encrypt(GROUPE, y, 1);
        Chiffre b = Elgamal.encrypt(GROUPE, y, 0);
        Chiffre c = Elgamal.encrypt(GROUPE, y, 1);

        assertEquals(Elgamal.agrege(GROUPE, a, b), Elgamal.agrege(GROUPE, b, a));
        assertEquals(
                Elgamal.agrege(GROUPE, Elgamal.agrege(GROUPE, a, b), c),
                Elgamal.agrege(GROUPE, a, Elgamal.agrege(GROUPE, b, c)));
    }

    @Test
    void decryptRetrouveLaSommeDesVotesAPlusieursScrutateurs()
            throws DechiffrementImpossibleException {
        PaireDeCles a = Elgamal.keyGen(GROUPE);
        PaireDeCles b = Elgamal.keyGen(GROUPE);
        BigInteger y = a.publique().multiply(b.publique()).mod(GROUPE.p());
        Chiffre total = totalChiffre(y, 1, 0, 1, 1);

        List<BigInteger> partiels =
                List.of(
                        Elgamal.dechiffrementPartiel(GROUPE, total, a.secrete()),
                        Elgamal.dechiffrementPartiel(GROUPE, total, b.secrete()));

        assertEquals(3, Elgamal.decrypt(GROUPE, total, partiels, 4));
    }

    @Test
    void decryptEchoueSansToutesLesPartsOuAuDelaDeLaBorne() {
        PaireDeCles a = Elgamal.keyGen(GROUPE);
        PaireDeCles b = Elgamal.keyGen(GROUPE);
        BigInteger y = a.publique().multiply(b.publique()).mod(GROUPE.p());
        Chiffre total = totalChiffre(y, 1, 1, 1);
        BigInteger partielA = Elgamal.dechiffrementPartiel(GROUPE, total, a.secrete());
        BigInteger partielB = Elgamal.dechiffrementPartiel(GROUPE, total, b.secrete());

        // Un scrutateur seul ne déchiffre rien.
        assertThrows(
                DechiffrementImpossibleException.class,
                () -> Elgamal.decrypt(GROUPE, total, List.of(partielA), 3));
        // Trois votes à 1 ne tiennent pas dans une borne de 2.
        assertThrows(
                DechiffrementImpossibleException.class,
                () -> Elgamal.decrypt(GROUPE, total, List.of(partielA, partielB), 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> Elgamal.decrypt(GROUPE, total, List.of(partielA, BigInteger.ZERO), 3));
    }

    @Test
    void leDechiffrementPartielVautUPuissanceLaPartSecrete()
            throws DechiffrementImpossibleException {
        PaireDeCles cles = Elgamal.keyGen(GROUPE, aleaFixe(0x01));
        Chiffre chiffre = Elgamal.encrypt(GROUPE, cles.publique(), 1, aleaFixe(0x42));

        BigInteger partiel = Elgamal.dechiffrementPartiel(GROUPE, chiffre, cles.secrete());

        // u = g^r, donc u^x = g^(r·x) pour les tirages fixés r = 0x42…42 et x = 0x01…01.
        BigInteger r = new BigInteger("42".repeat(256), 16);
        BigInteger x = new BigInteger("01".repeat(256), 16);
        assertEquals(GROUPE.g().modPow(r.multiply(x), GROUPE.p()), partiel);
        assertEquals(1, Elgamal.decrypt(GROUPE, chiffre, List.of(partiel), 1));
    }

    /** Déchiffrement à la main, indépendant de Decrypt : v / u^x redonne g^vote. */
    private static BigInteger gPuissanceVote(Chiffre chiffre, BigInteger x) {
        BigInteger p = GROUPE.p();
        return chiffre.v().multiply(chiffre.u().modPow(x, p).modInverse(p)).mod(p);
    }

    /**
     * Chiffre chaque vote avec {@code y} puis agrège les chiffrés, en partant du chiffré neutre.
     */
    private static Chiffre totalChiffre(BigInteger y, int... votes) {
        Chiffre total = new Chiffre(BigInteger.ONE, BigInteger.ONE);
        for (int vote : votes) {
            total = Elgamal.agrege(GROUPE, total, Elgamal.encrypt(GROUPE, y, vote));
        }
        return total;
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
