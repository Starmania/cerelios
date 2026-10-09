package fr.cerelios.urne;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReferundumTest {

    Instant debut = Instant.parse("2026-10-09T10:15:30.00Z");
    Instant fin = debut.plusSeconds(5);

    class HorlogeControle extends Clock{

        Instant instant;

        public HorlogeControle(Instant mtn){
            instant = mtn;
        }

        public void regleHorloge(Instant mtn){
            instant = mtn;
        }

        public Instant instant() {
            return this.instant;
        }

        public ZoneId getZone(){
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone){
            return this;
        }

    }

    HorlogeControle horloge = new HorlogeControle(debut);

    Referundum r = new Referundum(fin, horloge);
    
    @Test 
    void ReferundumPasFini() {
        assertFalse(r.verifFini());
        horloge.regleHorloge(fin.minusNanos(1));
        assertFalse(r.verifFini());
    }

    @Test
    void ReferundumFini() {
        horloge.regleHorloge(fin.plusSeconds(1));
        assertTrue(r.verifFini());
    }

}
