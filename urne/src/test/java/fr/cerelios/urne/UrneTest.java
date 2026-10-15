package fr.cerelios.urne;

import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.octets;
import static fr.cerelios.protocole.Conversions.versProto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Bulletin;
import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.protocole.Contextes;
import fr.cerelios.protocole.Conversions;
import fr.cerelios.protocole.Verificateur;
import fr.cerelios.protocole.messages.AjouterScrutateur;
import fr.cerelios.protocole.messages.Connexion;
import fr.cerelios.protocole.messages.CredentialPublic;
import fr.cerelios.protocole.messages.CreerReferendum;
import fr.cerelios.protocole.messages.Dates;
import fr.cerelios.protocole.messages.DefinirDates;
import fr.cerelios.protocole.messages.DefinirElecteurs;
import fr.cerelios.protocole.messages.DeposerBulletin;
import fr.cerelios.protocole.messages.DeposerCle;
import fr.cerelios.protocole.messages.DeposerCredentials;
import fr.cerelios.protocole.messages.DeposerDechiffrement;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Etat;
import fr.cerelios.protocole.messages.Recepisse;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Reponse;
import fr.cerelios.protocole.messages.Requete;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Parcours complet d'un référendum, en appelant l'urne directement, sans réseau : les rôles des
 * clients (scrutateurs, autorité, électeurs) sont joués ici avec le module crypto.
 */
class UrneTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;
    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant OUVERTURE = T0.plus(Duration.ofHours(1));
    private static final Instant CLOTURE = T0.plus(Duration.ofHours(2));
    private static final Instant PUBLICATION = T0.plus(Duration.ofHours(3));
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final List<String> ELECTEURS = List.of("bob", "carol", "dave");

    // PBKDF2 est volontairement lent : les comptes sont créés une seule fois.
    private static IdentitesLocales idp;

    private final HorlogeControle horloge = new HorlogeControle(T0);
    private final MailJournal mail = new MailJournal();
    private Urne urne;
    private String admin;
    private String id;
    private final Map<String, PaireDeCles> scrutateurs = new HashMap<>();
    private final Map<String, String> jetonsScrutateurs = new HashMap<>();
    private final Map<String, PaireDeCles> credentials = new HashMap<>();

    @BeforeAll
    static void comptes() {
        idp = new IdentitesLocales();
        idp.inscrire("alice", "mdp-alice", "alice@exemple.fr");
        for (String e : ELECTEURS) {
            idp.inscrire(e, "mdp-" + e, e + "@exemple.fr");
        }
        idp.inscrire("eve", "mdp-eve", "eve@exemple.fr");
    }

    @BeforeEach
    void preparerUnReferendumValide() {
        urne = new Urne(idp, mail, horloge);
        admin = connecter("alice");
        id =
                ok(requete(admin)
                                .setCreerReferendum(
                                        CreerReferendum.newBuilder().setQuestion("Pour ?")))
                        .getIdentifiant()
                        .getId();
        ok(
                requete(admin)
                        .setDefinirElecteurs(
                                DefinirElecteurs.newBuilder()
                                        .setReferendum(id)
                                        .addAllElecteurs(ELECTEURS)));
        ok(
                requete(admin)
                        .setDefinirDates(
                                DefinirDates.newBuilder()
                                        .setReferendum(id)
                                        .setDates(
                                                Dates.newBuilder()
                                                        .setOuverture(OUVERTURE.toEpochMilli())
                                                        .setCloture(CLOTURE.toEpochMilli())
                                                        .setPublication(PUBLICATION.toEpochMilli())
                                                        .setDelaiGrace(GRACE.toMillis()))));

        for (String nom : List.of("S1", "S2")) {
            ok(
                    requete(admin)
                            .setAjouterScrutateur(
                                    AjouterScrutateur.newBuilder()
                                            .setReferendum(id)
                                            .setNom(nom)
                                            .setEmail(nom + "@exemple.fr")));
            String jeton = jetonDuMail(nom + "@exemple.fr");
            PaireDeCles cles = Elgamal.keyGen(GROUPE);
            ok(
                    requete(jeton)
                            .setDeposerCle(
                                    DeposerCle.newBuilder()
                                            .setReferendum(id)
                                            .setClePublique(octets(cles.publique()))
                                            .setPreuve(
                                                    versProto(
                                                            Preuves.prouverConnaissance(
                                                                    GROUPE,
                                                                    cles,
                                                                    Contextes.cleScrutateur(
                                                                            id, nom))))));
            scrutateurs.put(nom, cles);
            jetonsScrutateurs.put(nom, jeton);
        }

        String jetonAutorite =
                ok(requete(admin).setObtenirJetonAutorite(ref())).getJeton().getJeton();
        List<String> liste =
                ok(requete(jetonAutorite).setObtenirElecteurs(ref()))
                        .getElecteurs()
                        .getElecteursList();
        assertEquals(ELECTEURS, liste);
        DeposerCredentials.Builder depot = DeposerCredentials.newBuilder().setReferendum(id);
        for (String electeur : liste) {
            PaireDeCles credential = Elgamal.keyGen(GROUPE);
            credentials.put(electeur, credential);
            depot.addCredentials(
                    CredentialPublic.newBuilder()
                            .setElecteur(electeur)
                            .setCredential(octets(credential.publique())));
        }
        ok(requete(jetonAutorite).setDeposerCredentials(depot));

        assertEquals(
                List.of(), ok(requete(admin).setObtenirStatut(ref())).getStatut().getManquesList());
        ok(requete(admin).setValider(ref()));
    }

    @Test
    void parcoursCompletJusquALaPublication() {
        assertEquals(Etat.VALIDE, publique().getEtat());
        assertRefus(voter("bob", true), "pas ouvert");

        horloge.regleHorloge(OUVERTURE);
        assertEquals(Etat.OUVERT, publique().getEtat());
        Recepisse bob = ok(voter("bob", true)).getRecepisse();
        ok(voter("carol", false));
        ok(voter("dave", true));
        Recepisse revote = ok(voter("dave", false)).getRecepisse();

        assertFalse(bob.getRevote());
        assertTrue(revote.getRevote());
        assertTrue(mail.envoyes().stream().anyMatch(m -> m.corps().contains(bob.getEmpreinte())));
        assertEquals(3, publique().getNombreBulletins());

        horloge.regleHorloge(CLOTURE.plus(GRACE));
        assertEquals(Etat.DEPOUILLEMENT, publique().getEtat());
        DonneesPubliques avant = donnees();
        assertTrue(avant.hasTotal());
        assertTrue(
                avant.getBulletinsList().stream()
                        .anyMatch(b -> traceur(b).equals(bob.getEmpreinte())));

        dechiffrer("S1");
        horloge.regleHorloge(PUBLICATION);
        // Il manque S2 : le résultat attend.
        assertEquals(Etat.DEPOUILLEMENT, publique().getEtat());
        dechiffrer("S2");

        ReferendumPublic publie = publique();
        assertEquals(Etat.PUBLIE, publie.getEtat());
        assertEquals(1, publie.getResultat().getOui());
        assertEquals(2, publie.getResultat().getNon());
        assertEquals(List.of(), new Verificateur(GROUPE).verifier(donnees()));
    }

    @Test
    void lesDechiffrementsArrivesAvantLaPublicationNeLaDevancentPas() {
        horloge.regleHorloge(OUVERTURE);
        ok(voter("bob", true));
        horloge.regleHorloge(CLOTURE.plus(GRACE));
        dechiffrer("S1");
        dechiffrer("S2");

        assertEquals(Etat.DEPOUILLEMENT, publique().getEtat());
        horloge.regleHorloge(PUBLICATION);
        assertEquals(Etat.PUBLIE, publique().getEtat());
    }

    @Test
    void seulsLesElecteursInscritsVotentAvecLeurPropreCredential() {
        horloge.regleHorloge(OUVERTURE);

        assertRefus(voterAvec("eve", credentials.get("bob"), true), "liste des électeurs");
        assertRefus(voterAvec("carol", credentials.get("bob"), true), "pas le vôtre");
        assertRefus(voterAvec("bob", Elgamal.keyGen(GROUPE), true), "pas le vôtre");
        assertEquals(0, publique().getNombreBulletins());
    }

    @Test
    void unBulletinTruqueOuRejoueEstRefuse() {
        horloge.regleHorloge(OUVERTURE);
        String session = connecter("bob");
        Bulletin bulletin = Bulletin.creer(GROUPE, cle(), id, credentials.get("bob"), true);
        // L'électeur remplace son chiffré par un chiffré de 2 : deux Oui pour un bulletin.
        BigInteger r = GROUPE.tirerExposant();
        BigInteger p = GROUPE.p();
        Chiffre deux =
                new Chiffre(
                        GROUPE.g().modPow(r, p),
                        GROUPE.g().pow(2).multiply(cle().modPow(r, p)).mod(p));
        Bulletin truque =
                new Bulletin(bulletin.credential(), deux, bulletin.preuve(), bulletin.signature());

        assertRefus(deposer(session, truque), "mal formé");
        ok(deposer(session, bulletin));
        assertRefus(deposer(session, bulletin), "déjà reçu");
    }

    @Test
    void leDelaiDeGraceNeProfiteQuAuxElecteursConnectesAvantLaCloture() {
        horloge.regleHorloge(CLOTURE.minusSeconds(30));
        String bob = connecter("bob");
        horloge.regleHorloge(CLOTURE.plusSeconds(60));
        String carol = connecter("carol");

        assertEquals(Etat.CLOS, publique().getEtat());
        ok(deposer(bob, Bulletin.creer(GROUPE, cle(), id, credentials.get("bob"), true)));
        assertRefus(
                deposer(carol, Bulletin.creer(GROUPE, cle(), id, credentials.get("carol"), true)),
                "pas ouvert");

        horloge.regleHorloge(CLOTURE.plus(GRACE));
        assertRefus(
                deposer(bob, Bulletin.creer(GROUPE, cle(), id, credentials.get("bob"), false)),
                "pas ouvert");
    }

    @Test
    void unDechiffrementForgeEstRefuse() {
        horloge.regleHorloge(OUVERTURE);
        ok(voter("bob", true));
        horloge.regleHorloge(CLOTURE.plus(GRACE));
        Chiffre total = Conversions.depuisProto(donnees().getTotal());
        PaireDeCles s1 = scrutateurs.get("S1");
        // Attaque A5 : S1 envoie v · g^(-3) pour faire publier 3 Oui.
        BigInteger forge =
                total.v().multiply(GROUPE.g().pow(3).modInverse(GROUPE.p())).mod(GROUPE.p());

        assertRefus(
                requete(jetonsScrutateurs.get("S1"))
                        .setDeposerDechiffrement(
                                DeposerDechiffrement.newBuilder()
                                        .setReferendum(id)
                                        .setDechiffrement(octets(forge))
                                        .setPreuve(
                                                versProto(
                                                        Preuves.prouverDechiffrement(
                                                                GROUPE,
                                                                s1,
                                                                total,
                                                                Contextes.dechiffrement(
                                                                        id, "S1"))))),
                "Preuve de déchiffrement invalide");
    }

    @Test
    void leScellementFigeLesDates() {
        ok(requete(admin).setSceller(ref()));

        assertTrue(publique().getScelle());
        assertEquals(1, donnees().getScellementsCount());
        assertRefus(
                requete(admin)
                        .setDefinirDates(
                                DefinirDates.newBuilder()
                                        .setReferendum(id)
                                        .setDates(
                                                publique().getDates().toBuilder()
                                                        .setPublication(
                                                                PUBLICATION
                                                                        .plusSeconds(1)
                                                                        .toEpochMilli()))),
                "scellé");
    }

    @Test
    void seulUnProprietaireAdministre() {
        String eve = connecter("eve");

        assertRefus(requete(eve).setObtenirEmargement(ref()), "n'administrez pas");
        assertRefus(requete(eve).setObtenirJetonAutorite(ref()), "n'administrez pas");
        assertRefus(requete("faux").setObtenirStatut(ref()), "Session inconnue");
    }

    @Test
    void lEmargementDitQuiAVoteEtLArchivageLEfface() {
        horloge.regleHorloge(OUVERTURE);
        ok(voter("carol", true));
        assertEquals(
                List.of("carol"),
                ok(requete(admin).setObtenirEmargement(ref()))
                        .getEmargement()
                        .getLignesList()
                        .stream()
                        .map(l -> l.getElecteur())
                        .toList());

        horloge.regleHorloge(CLOTURE.plus(GRACE));
        dechiffrer("S1");
        dechiffrer("S2");
        horloge.regleHorloge(PUBLICATION);
        assertEquals(Etat.PUBLIE, publique().getEtat());

        horloge.avance(Urne.AVANT_ARCHIVAGE.plusSeconds(1));
        admin = connecter("alice");
        urne.appliquerPolitiqueDonnees();
        assertEquals(Etat.ARCHIVE, publique().getEtat());
        assertEquals(
                0, ok(requete(admin).setObtenirEmargement(ref())).getEmargement().getLignesCount());
        assertEquals(List.of(), new Verificateur(GROUPE).verifier(donnees()));

        horloge.avance(Urne.AVANT_SUPPRESSION.plusSeconds(1));
        urne.appliquerPolitiqueDonnees();
        assertRefus(requete("").setObtenirReferendum(ref()), "inconnu");
    }

    @Test
    void uneSessionExpireApres24Heures() {
        horloge.avance(Urne.DUREE_SESSION.plusSeconds(1));

        assertRefus(requete(admin).setObtenirStatut(ref()), "expirée");
    }

    // ---- Outils --------------------------------------------------------------------------------

    private String connecter(String identifiant) {
        return ok(requete("")
                        .setConnexion(
                                Connexion.newBuilder()
                                        .setIdentifiant(identifiant)
                                        .setSecret("mdp-" + identifiant)))
                .getJeton()
                .getJeton();
    }

    private Requete.Builder voter(String electeur, boolean oui) {
        return voterAvec(electeur, credentials.get(electeur), oui);
    }

    private Requete.Builder voterAvec(String electeur, PaireDeCles credential, boolean oui) {
        return deposer(connecter(electeur), Bulletin.creer(GROUPE, cle(), id, credential, oui));
    }

    private Requete.Builder deposer(String session, Bulletin bulletin) {
        return requete(session)
                .setDeposerBulletin(
                        DeposerBulletin.newBuilder()
                                .setReferendum(id)
                                .setBulletin(versProto(bulletin)));
    }

    private void dechiffrer(String nom) {
        Chiffre total = Conversions.depuisProto(donnees().getTotal());
        PaireDeCles cles = scrutateurs.get(nom);
        ok(
                requete(jetonsScrutateurs.get(nom))
                        .setDeposerDechiffrement(
                                DeposerDechiffrement.newBuilder()
                                        .setReferendum(id)
                                        .setDechiffrement(
                                                octets(
                                                        Elgamal.dechiffrementPartiel(
                                                                GROUPE, total, cles.secrete())))
                                        .setPreuve(
                                                versProto(
                                                        Preuves.prouverDechiffrement(
                                                                GROUPE,
                                                                cles,
                                                                total,
                                                                Contextes.dechiffrement(
                                                                        id, nom))))));
    }

    private BigInteger cle() {
        return entier(publique().getCle());
    }

    private ReferendumPublic publique() {
        return ok(requete("").setObtenirReferendum(ref())).getReferendum();
    }

    private DonneesPubliques donnees() {
        return ok(requete("").setObtenirDonneesPubliques(ref())).getDonneesPubliques();
    }

    private static String traceur(fr.cerelios.protocole.messages.Bulletin b) {
        return Conversions.depuisProto(b).empreinte();
    }

    private String jetonDuMail(String destinataire) {
        MailJournal.Mail invitation =
                mail.envoyes().stream()
                        .filter(m -> m.destinataire().equals(destinataire))
                        .findFirst()
                        .orElseThrow();
        Matcher m = Pattern.compile("jeton : (\\S+)").matcher(invitation.corps());
        assertTrue(m.find());
        return m.group(1);
    }

    private Referendum.Builder ref() {
        return Referendum.newBuilder().setReferendum(id);
    }

    private static Requete.Builder requete(String jeton) {
        return Requete.newBuilder().setJeton(jeton);
    }

    private Reponse ok(Requete.Builder requete) {
        Reponse reponse = urne.traiter(requete.build());
        assertFalse(reponse.hasErreur(), () -> "Refusé : " + reponse.getErreur().getMessage());
        return reponse;
    }

    private void assertRefus(Requete.Builder requete, String extrait) {
        Reponse reponse = urne.traiter(requete.build());
        assertTrue(reponse.hasErreur(), "La requête aurait dû être refusée");
        assertTrue(
                reponse.getErreur().getMessage().contains(extrait),
                () -> "Message inattendu : " + reponse.getErreur().getMessage());
    }
}
