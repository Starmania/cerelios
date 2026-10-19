package fr.cerelios.urne;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class AuthentificationTest {

    private final Authentification authentification = new Authentification();

    // Critère d'acceptation 1 d'US-01.
    @Test
    void desIdentifiantsCorrectsDonnentLeRoleDuCompteEtLuiSeul() {
        authentification.inscrire("alice", "s3cret", Role.ELECTEUR);
        authentification.inscrire("bob", "autre", Role.ADMINISTRATEUR);

        assertEquals(Optional.of(Role.ELECTEUR), authentification.authentifier("alice", "s3cret"));
        assertEquals(Optional.of(Role.ADMINISTRATEUR), authentification.authentifier("bob", "autre"));
    }

    // Critère d'acceptation 2 d'US-01.
    @Test
    void unMotDePasseIncorrectEstRefuseEtLEchecEstJournalise() {
        authentification.inscrire("alice", "s3cret", Role.ELECTEUR);
        List<LogRecord> journal = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord ligne) {
                journal.add(ligne);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Logger logger = Logger.getLogger(Authentification.class.getName());
        logger.addHandler(capture);
        try {
            assertEquals(Optional.empty(), authentification.authentifier("alice", "S3cret"));
        } finally {
            logger.removeHandler(capture);
        }

        assertEquals(1, journal.size());
        assertTrue(journal.get(0).getMessage().contains("alice"));
        assertFalse(journal.get(0).getMessage().contains("S3cret"));
    }

    @Test
    void unIdentifiantInconnuOuUnMotDePasseVideSontRefuses() {
        authentification.inscrire("alice", "s3cret", Role.ELECTEUR);

        assertEquals(Optional.empty(), authentification.authentifier("alice", ""));
        assertEquals(Optional.empty(), authentification.authentifier("bob", "s3cret"));
    }

    @Test
    void unIdentifiantNEstInscritQuUneFois() {
        authentification.inscrire("alice", "s3cret", Role.ELECTEUR);

        assertThrows(
                IllegalArgumentException.class,
                () -> authentification.inscrire("alice", "autre", Role.ADMINISTRATEUR));
        assertEquals(Optional.of(Role.ELECTEUR), authentification.authentifier("alice", "s3cret"));
        assertEquals(Optional.empty(), authentification.authentifier("alice", "autre"));
    }

    @Test
    void unParametreNulEstRefuseToutDeSuite() {
        assertThrows(NullPointerException.class, () -> authentification.inscrire(null, "s3cret", Role.ELECTEUR));
        assertThrows(NullPointerException.class, () -> authentification.inscrire("alice", null, Role.ELECTEUR));
        assertThrows(NullPointerException.class, () -> authentification.inscrire("alice", "s3cret", null));
        assertThrows(NullPointerException.class, () -> authentification.authentifier(null, "s3cret"));
        assertThrows(NullPointerException.class, () -> authentification.authentifier("alice", null));
    }
}
