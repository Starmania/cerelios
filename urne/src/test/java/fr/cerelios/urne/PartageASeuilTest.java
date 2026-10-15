package fr.cerelios.urne;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.cerelios.crypto.ChiffrementHybride;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.crypto.Partage;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.protocole.Contextes;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ce que l'urne refuse pendant le partage à seuil, testé sur le référendum directement. */
class PartageASeuilTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;

    private final Referundum ref =
            new Referundum("ref", "Pour ?", "alice", GROUPE, new HorlogeControle(Instant.EPOCH));
    private final List<String> jetons = new ArrayList<>();
    private final List<PaireDeCles> canaux = new ArrayList<>();

    @BeforeEach
    void deuxScrutateursSeuilDeux() {
        ref.definirSeuil(2);
        for (String nom : List.of("S1", "S2")) {
            jetons.add(ref.ajouterScrutateur(nom, nom + "@exemple.fr"));
        }
    }

    private void deposerCanaux() {
        for (int i = 0; i < 2; i++) {
            PaireDeCles canal = Elgamal.keyGen(GROUPE);
            canaux.add(canal);
            String nom = "S" + (i + 1);
            ref.deposerCleCanal(
                    jetons.get(i),
                    canal.publique(),
                    Preuves.prouverConnaissance(GROUPE, canal, Contextes.cleCanal("ref", nom)));
        }
    }

    /** Polynôme honnête du scrutateur d'indice i (à partir de 0), signé avec sa clé de canal. */
    private Referundum.Polynome polynome(int i, Partage.Polynome f) {
        String nom = "S" + (i + 1);
        List<BigInteger> engagements = Partage.engagements(GROUPE, f);
        SortedMap<Integer, ChiffrementHybride.Message> parts = new TreeMap<>();
        for (int j = 1; j <= 2; j++) {
            parts.put(
                    j,
                    ChiffrementHybride.chiffrer(
                            GROUPE,
                            canaux.get(j - 1).publique(),
                            Partage.evaluer(GROUPE, f, j).toByteArray(),
                            Contextes.part("ref", nom, j)));
        }
        return new Referundum.Polynome(
                engagements,
                parts,
                Preuves.prouverConnaissance(
                        GROUPE, canaux.get(i), Contextes.polynome("ref", nom, engagements, parts)));
    }

    @Test
    void scrutateursEtSeuilSontFigesDesLaPremiereCleDeCanal() {
        deposerCanaux();

        assertThrows(RequeteRefusee.class, () -> ref.ajouterScrutateur("S3", "s3@exemple.fr"));
        assertThrows(RequeteRefusee.class, () -> ref.definirSeuil(1));
    }

    @Test
    void unPolynomeMalSigneOuIncompletEstRefuse() {
        deposerCanaux();
        Referundum.Polynome honnete = polynome(0, Partage.tirer(GROUPE, 2));

        // Signé par S2 alors qu'il est déposé par S1.
        Referundum.Polynome usurpe =
                new Referundum.Polynome(
                        honnete.engagements(),
                        honnete.parts(),
                        Preuves.prouverConnaissance(GROUPE, canaux.get(1), "n'importe quoi"));
        assertThrows(RequeteRefusee.class, () -> ref.deposerPolynome(jetons.get(0), usurpe));

        SortedMap<Integer, ChiffrementHybride.Message> uneSeulePart =
                new TreeMap<>(honnete.parts());
        uneSeulePart.remove(2);
        assertThrows(
                RequeteRefusee.class,
                () ->
                        ref.deposerPolynome(
                                jetons.get(0),
                                new Referundum.Polynome(
                                        honnete.engagements(), uneSeulePart, honnete.signature())));

        ref.deposerPolynome(jetons.get(0), honnete);
        assertEquals(Referundum.EtapePartage.POLYNOMES, ref.etapePartage());
    }

    @Test
    void uneClePartielleQuiNeSuitPasLesEngagementsEstRefusee() {
        deposerCanaux();
        ref.deposerPolynome(jetons.get(0), polynome(0, Partage.tirer(GROUPE, 2)));
        ref.deposerPolynome(jetons.get(1), polynome(1, Partage.tirer(GROUPE, 2)));
        assertEquals(Referundum.EtapePartage.CLES_PARTIELLES, ref.etapePartage());

        PaireDeCles inventee = Elgamal.keyGen(GROUPE);
        assertThrows(
                RequeteRefusee.class,
                () ->
                        ref.deposerCle(
                                jetons.get(0),
                                inventee.publique(),
                                Preuves.prouverConnaissance(
                                        GROUPE, inventee, Contextes.cleScrutateur("ref", "S1"))));
    }

    @Test
    void unSeuilSuperieurAuNombreDeScrutateursBloqueLePartage() {
        ref.definirSeuil(3);
        PaireDeCles canal = Elgamal.keyGen(GROUPE);

        assertThrows(
                RequeteRefusee.class,
                () ->
                        ref.deposerCleCanal(
                                jetons.get(0),
                                canal.publique(),
                                Preuves.prouverConnaissance(
                                        GROUPE, canal, Contextes.cleCanal("ref", "S1"))));
    }
}
