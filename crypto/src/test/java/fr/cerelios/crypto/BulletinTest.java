package fr.cerelios.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class BulletinTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;

    private final PaireDeCles scrutateur = Elgamal.keyGen(GROUPE);
    private final BigInteger y = scrutateur.publique();
    private final PaireDeCles credential = Elgamal.keyGen(GROUPE);

    @Test
    void unBulletinOuiOuNonEstValideEtSeDechiffre() throws DechiffrementImpossibleException {
        for (boolean oui : new boolean[] {true, false}) {
            Bulletin bulletin = Bulletin.creer(GROUPE, y, "ref", credential, oui);

            assertTrue(bulletin.estValide(GROUPE, y, "ref"));
            assertEquals(credential.publique(), bulletin.credential());
            BigInteger partiel =
                    Elgamal.dechiffrementPartiel(GROUPE, bulletin.chiffre(), scrutateur.secrete());
            assertEquals(
                    oui ? 1 : 0, Elgamal.decrypt(GROUPE, bulletin.chiffre(), List.of(partiel), 1));
        }
    }

    @Test
    void unBulletinNeSertQuePourSonReferendum() {
        Bulletin bulletin = Bulletin.creer(GROUPE, y, "ref", credential, true);

        assertFalse(bulletin.estValide(GROUPE, y, "autre-ref"));
        assertFalse(bulletin.estValide(GROUPE, Elgamal.keyGen(GROUPE).publique(), "ref"));
    }

    @Test
    void lUrneNePeutPasSubstituerLeCredential() {
        Bulletin bulletin = Bulletin.creer(GROUPE, y, "ref", credential, true);
        BigInteger autreCredential = Elgamal.keyGen(GROUPE).publique();

        Bulletin vole =
                new Bulletin(
                        autreCredential,
                        bulletin.chiffre(),
                        bulletin.preuve(),
                        bulletin.signature());

        assertFalse(vole.estValide(GROUPE, y, "ref"));
    }

    @Test
    void unChiffreRemplaceInvalideLaSignature() {
        Bulletin bulletin = Bulletin.creer(GROUPE, y, "ref", credential, true);
        Chiffre autre = Bulletin.creer(GROUPE, y, "ref", credential, false).chiffre();

        Bulletin modifie =
                new Bulletin(bulletin.credential(), autre, bulletin.preuve(), bulletin.signature());

        assertFalse(modifie.estValide(GROUPE, y, "ref"));
    }

    @Test
    void lEmpreinteIdentifieLeBulletin() {
        Bulletin a = Bulletin.creer(GROUPE, y, "ref", credential, true);
        Bulletin b = Bulletin.creer(GROUPE, y, "ref", credential, true);

        assertEquals(64, a.empreinte().length());
        assertEquals(
                a.empreinte(),
                new Bulletin(a.credential(), a.chiffre(), a.preuve(), a.signature()).empreinte());
        assertNotEquals(a.empreinte(), b.empreinte());
    }

    @Test
    void unCredentialSeReconstruitDepuisSaPartieSecrete() {
        assertEquals(
                credential.publique(),
                Elgamal.depuisSecrete(GROUPE, credential.secrete()).publique());
    }
}
