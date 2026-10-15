package fr.cerelios.urne;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthentificationTest {

    private final Authentification authentification = new Authentification();

    @Test
    void seulLeBonMotDePasseOuvreLeCompte() {
        authentification.inscrire("alice", "s3cret");

        assertTrue(authentification.verifier("alice", "s3cret"));
        assertFalse(authentification.verifier("alice", "S3cret"));
        assertFalse(authentification.verifier("alice", ""));
        assertFalse(authentification.verifier("bob", "s3cret"));
    }

    @Test
    void unIdentifiantNEstInscritQuUneFois() {
        authentification.inscrire("alice", "s3cret");

        assertThrows(
                IllegalArgumentException.class,
                () -> authentification.inscrire("alice", "autre"));
        assertTrue(authentification.verifier("alice", "s3cret"));
        assertFalse(authentification.verifier("alice", "autre"));
    }
}
