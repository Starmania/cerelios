package fr.cerelios.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.ChiffrementHybride.Message;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChiffrementHybrideTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;
    private final PaireDeCles destinataire = Elgamal.keyGen(GROUPE);
    private final byte[] clair = "part secrète".getBytes(StandardCharsets.UTF_8);

    @Test
    void seulLeDestinataireDechiffre() {
        Message m = ChiffrementHybride.chiffrer(GROUPE, destinataire.publique(), clair, "ctx");

        assertArrayEquals(
                clair,
                ChiffrementHybride.dechiffrer(GROUPE, destinataire.secrete(), m, "ctx")
                        .orElseThrow());
        assertTrue(
                ChiffrementHybride.dechiffrer(GROUPE, Elgamal.keyGen(GROUPE).secrete(), m, "ctx")
                        .isEmpty());
    }

    @Test
    void unMessageModifieOuDeplaceNeSeDechiffrePas() {
        Message m = ChiffrementHybride.chiffrer(GROUPE, destinataire.publique(), clair, "ctx");
        byte[] modifie = m.donnees().clone();
        modifie[0] ^= 1;

        assertTrue(
                ChiffrementHybride.dechiffrer(GROUPE, destinataire.secrete(), m, "autre")
                        .isEmpty());
        assertTrue(
                ChiffrementHybride.dechiffrer(
                                GROUPE,
                                destinataire.secrete(),
                                new Message(m.r(), m.nonce(), modifie),
                                "ctx")
                        .isEmpty());
    }
}
