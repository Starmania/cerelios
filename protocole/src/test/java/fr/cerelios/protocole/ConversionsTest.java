package fr.cerelios.protocole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import fr.cerelios.crypto.Bulletin;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ConversionsTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;

    @Test
    void unBulletinSurvitAuPassageParLeReseau() throws Exception {
        BigInteger y = Elgamal.keyGen(GROUPE).publique();
        Bulletin bulletin = Bulletin.creer(GROUPE, y, "ref", Elgamal.keyGen(GROUPE), true);

        byte[] octets = Conversions.versProto(bulletin).toByteArray();
        Bulletin recu =
                Conversions.depuisProto(fr.cerelios.protocole.messages.Bulletin.parseFrom(octets));

        assertEquals(bulletin, recu);
        assertTrue(recu.estValide(GROUPE, y, "ref"));
    }

    @Test
    void lesEntiersNegatifsEtLesGrandsEntiersSontConserves() {
        for (BigInteger n :
                new BigInteger[] {BigInteger.ONE.negate(), GROUPE.p(), BigInteger.valueOf(255)}) {
            assertEquals(n, Conversions.entier(Conversions.octets(n)));
        }
    }

    @Test
    void unChampVideNEstPasZero() {
        assertThrows(IllegalArgumentException.class, () -> Conversions.entier(ByteString.EMPTY));
    }
}
