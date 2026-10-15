package fr.cerelios.urne;

import java.time.Clock;
import java.time.Instant;
import org.jetbrains.annotations.TestOnly;

public class Referundum {

    private Instant dateFin;
    Clock horloge;

    public Referundum(Instant dateFin) {
        this(dateFin, Clock.systemUTC());
    }

    public boolean verifFini() {
        return Instant.now(horloge).isAfter(dateFin);
    }

    @TestOnly
    Referundum(Instant dateFin, Clock horloge) {
        this.dateFin = dateFin;
        this.horloge = horloge;
    }
}
