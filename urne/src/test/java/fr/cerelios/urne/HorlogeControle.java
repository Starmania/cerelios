package fr.cerelios.urne;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Horloge que les tests règlent à la main. */
class HorlogeControle extends Clock {

    private Instant instant;

    HorlogeControle(Instant mtn) {
        instant = mtn;
    }

    void regleHorloge(Instant mtn) {
        instant = mtn;
    }

    void avance(Duration duree) {
        instant = instant.plus(duree);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
