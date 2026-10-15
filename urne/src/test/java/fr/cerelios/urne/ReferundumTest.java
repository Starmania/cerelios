package fr.cerelios.urne;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ReferundumTest {

    Instant debut = Instant.parse("2026-10-09T10:15:30.00Z");
    Instant fin = debut.plusSeconds(5);

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

    @Test
    void lEtatSuitLesDatesAChaqueLecture() {
        assertEquals(Etat.OUVERT, r.etat());
        horloge.regleHorloge(fin.minusNanos(1));
        assertEquals(Etat.OUVERT, r.etat());
        // Sans scrutateur ni bulletin, rien n'attend : clôture, dépouillement et publication
        // s'enchaînent dans la même lecture.
        horloge.regleHorloge(fin);
        assertEquals(Etat.PUBLIE, r.etat());
        assertEquals(new Referundum.Resultat(0, 0), r.resultat());
    }

    @Test
    void lesDatesDoiventSeSuivre() {
        Referundum brouillon =
                new Referundum(
                        "id", "Question ?", "admin", fr.cerelios.crypto.Groupe.RFC_3526, horloge);

        assertThrows(
                RequeteRefusee.class, () -> brouillon.definirDates(fin, debut, fin, Duration.ZERO));
        assertThrows(
                RequeteRefusee.class,
                () -> brouillon.definirDates(debut, fin, debut, Duration.ZERO));
        assertThrows(
                RequeteRefusee.class,
                () -> brouillon.definirDates(debut, fin, fin, Duration.ofSeconds(-1)));
    }

    @Test
    void unElecteurNEstInscritQuUneFois() {
        Referundum brouillon =
                new Referundum(
                        "id", "Question ?", "admin", fr.cerelios.crypto.Groupe.RFC_3526, horloge);

        assertThrows(
                RequeteRefusee.class, () -> brouillon.definirElecteurs(List.of("Alice", "alice ")));
        assertThrows(RequeteRefusee.class, () -> brouillon.definirElecteurs(List.of("bob", " ")));
    }
}
