package fr.cerelios.urne;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.autorite.AutoriteCertification;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.electeur.Electeur;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.MailJournal;
import fr.cerelios.protocole.Verificateur;
import fr.cerelios.protocole.messages.AjouterScrutateur;
import fr.cerelios.protocole.messages.CreerReferendum;
import fr.cerelios.protocole.messages.Dates;
import fr.cerelios.protocole.messages.DefinirDates;
import fr.cerelios.protocole.messages.DefinirElecteurs;
import fr.cerelios.protocole.messages.DefinirSeuil;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Etat;
import fr.cerelios.protocole.messages.EtatPartage;
import fr.cerelios.protocole.messages.Recepisse;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import fr.cerelios.scrutateur.Scrutateur;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Référendum complet par le réseau, chaque rôle passant par son vrai programme client. */
class ServeurUrneTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;
    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant OUVERTURE = T0.plusSeconds(60);
    private static final Instant CLOTURE = T0.plusSeconds(120);
    private static final Instant PUBLICATION = T0.plusSeconds(180);

    private static IdentitesLocales idp;

    @TempDir Path dossier;
    private final HorlogeControle horloge = new HorlogeControle(T0);
    private final MailJournal mail = new MailJournal();
    private ServeurUrne serveur;
    private ClientUrne admin;
    private String id;

    @BeforeAll
    static void comptes() {
        idp = new IdentitesLocales();
        for (String compte : List.of("alice", "bob", "carol")) {
            idp.inscrire(compte, "mdp-" + compte, compte + "@exemple.fr");
        }
    }

    @BeforeEach
    void demarrer() throws Exception {
        serveur = new ServeurUrne(new Urne(idp, mail, horloge), 0);
        serveur.demarrer();
        admin = client();
        new Electeur(admin, GROUPE).connecter("alice", "mdp-alice");
        id =
                admin.appeler(
                                Requete.newBuilder()
                                        .setCreerReferendum(
                                                CreerReferendum.newBuilder()
                                                        .setQuestion("Adopter le budget ?")))
                        .getIdentifiant()
                        .getId();
        admin.appeler(
                Requete.newBuilder()
                        .setDefinirElecteurs(
                                DefinirElecteurs.newBuilder()
                                        .setReferendum(id)
                                        .addElecteurs("bob")
                                        .addElecteurs("carol")));
        admin.appeler(
                Requete.newBuilder()
                        .setDefinirDates(
                                DefinirDates.newBuilder()
                                        .setReferendum(id)
                                        .setDates(
                                                Dates.newBuilder()
                                                        .setOuverture(OUVERTURE.toEpochMilli())
                                                        .setCloture(CLOTURE.toEpochMilli())
                                                        .setPublication(
                                                                PUBLICATION.toEpochMilli()))));
    }

    @AfterEach
    void arreter() throws Exception {
        admin.close();
        serveur.close();
    }

    @Test
    void referendumCompletParLeReseau() throws Exception {
        List<Scrutateur> scrutateurs = inscrireScrutateurs(1);
        Path secret = dossier.resolve("S1.cle");
        scrutateurs.getFirst().genererCle(secret);

        Map<String, String> credentials = certifierEtValider();

        horloge.regleHorloge(OUVERTURE);
        Recepisse recuBob = voter("bob", credentials.get("bob"), true);
        voter("carol", credentials.get("carol"), true);
        assertTrue(voter("carol", credentials.get("carol"), false).getRevote());

        horloge.regleHorloge(CLOTURE);
        Scrutateur s1 = scrutateurs.getFirst();
        assertTrue(s1.cleUtilisee(secret));
        s1.dechiffrer(secret);
        assertTrue(s1.dechiffrementPublie().isPresent());

        horloge.regleHorloge(PUBLICATION);
        try (ClientUrne client = client()) {
            Electeur bob = new Electeur(client, GROUPE);
            ReferendumPublic resultat = bob.referendum(id);
            assertEquals(Etat.PUBLIE, resultat.getEtat());
            assertEquals(1, resultat.getResultat().getOui());
            assertEquals(1, resultat.getResultat().getNon());
            assertTrue(bob.bulletinPresent(id, recuBob.getEmpreinte()));

            AutoriteCertification autorite =
                    new AutoriteCertification(client, GROUPE, id, jetonAutorite());
            assertEquals(
                    List.of(),
                    autorite.verifierPublication(
                            AutoriteCertification.relire(dossier.resolve("credentials.txt"))));
        }
        assertEquals(List.of(), new Verificateur(GROUPE).verifier(donneesPubliques()));
    }

    @Test
    void deuxScrutateursSurTroisSuffisentADechiffrer() throws Exception {
        admin.appeler(
                Requete.newBuilder()
                        .setDefinirSeuil(DefinirSeuil.newBuilder().setReferendum(id).setSeuil(2)));
        List<Scrutateur> scrutateurs = inscrireScrutateurs(3);
        List<Path> secrets = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            secrets.add(dossier.resolve("S" + (i + 1) + ".canal"));
        }

        // Chaque scrutateur avance quand il peut ; une étape attend que tous aient fini la
        // précédente, d'où les tours.
        EtatPartage.Etape etape = EtatPartage.Etape.CANAUX;
        for (int tour = 0; tour < 4 && etape != EtatPartage.Etape.TERMINE; tour++) {
            for (int i = 0; i < 3; i++) {
                etape = scrutateurs.get(i).avancerPartage(secrets.get(i));
            }
        }
        assertEquals(EtatPartage.Etape.TERMINE, etape);

        Map<String, String> credentials = certifierEtValider();
        for (int i = 0; i < 3; i++) {
            assertTrue(scrutateurs.get(i).cleUtilisee(secrets.get(i)));
        }

        horloge.regleHorloge(OUVERTURE);
        voter("bob", credentials.get("bob"), true);
        voter("carol", credentials.get("carol"), true);

        // S2 ne répond pas : S1 et S3 suffisent.
        horloge.regleHorloge(PUBLICATION);
        scrutateurs.get(0).dechiffrer(secrets.get(0));
        assertEquals(Etat.DEPOUILLEMENT, referendum().getEtat());
        scrutateurs.get(2).dechiffrer(secrets.get(2));

        ReferendumPublic resultat = referendum();
        assertEquals(Etat.PUBLIE, resultat.getEtat());
        assertEquals(2, resultat.getResultat().getOui());
        assertEquals(0, resultat.getResultat().getNon());
        assertEquals(List.of(), new Verificateur(GROUPE).verifier(donneesPubliques()));
    }

    // ---- Outils --------------------------------------------------------------------------------

    private ClientUrne client() throws Exception {
        return new ClientUrne("localhost", serveur.port());
    }

    /** Inscrit S1…Sn ; chacun reçoit son jeton par e-mail et ouvre sa propre connexion. */
    private List<Scrutateur> inscrireScrutateurs(int n) throws Exception {
        List<Scrutateur> scrutateurs = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String nom = "S" + i;
            String email = nom.toLowerCase() + "@exemple.fr";
            admin.appeler(
                    Requete.newBuilder()
                            .setAjouterScrutateur(
                                    AjouterScrutateur.newBuilder()
                                            .setReferendum(id)
                                            .setNom(nom)
                                            .setEmail(email)));
            scrutateurs.add(
                    new Scrutateur(
                            client(),
                            GROUPE,
                            champ(email, "référendum"),
                            champ(email, "nom"),
                            champ(email, "jeton")));
        }
        return scrutateurs;
    }

    /** L'autorité génère les credentials, puis l'administratrice valide. */
    private Map<String, String> certifierEtValider() throws Exception {
        Map<String, String> credentials;
        try (ClientUrne client = client()) {
            AutoriteCertification autorite =
                    new AutoriteCertification(client, GROUPE, id, jetonAutorite());
            assertEquals(List.of("bob", "carol"), autorite.electeurs());
            credentials = autorite.generer();
            AutoriteCertification.enregistrer(credentials, dossier.resolve("credentials.txt"));
        }
        admin.appeler(Requete.newBuilder().setValider(ref()));
        return credentials;
    }

    private String jetonAutorite() throws Exception {
        return admin.appeler(Requete.newBuilder().setObtenirJetonAutorite(ref()))
                .getJeton()
                .getJeton();
    }

    private Recepisse voter(String electeur, String credential, boolean oui) throws Exception {
        try (ClientUrne client = client()) {
            Electeur e = new Electeur(client, GROUPE);
            e.connecter(electeur, "mdp-" + electeur);
            return e.voter(id, credential, oui);
        }
    }

    private ReferendumPublic referendum() throws Exception {
        return admin.appeler(Requete.newBuilder().setObtenirReferendum(ref())).getReferendum();
    }

    private DonneesPubliques donneesPubliques() throws Exception {
        return admin.appeler(Requete.newBuilder().setObtenirDonneesPubliques(ref()))
                .getDonneesPubliques();
    }

    private Referendum.Builder ref() {
        return Referendum.newBuilder().setReferendum(id);
    }

    private String champ(String destinataire, String nom) {
        String corps =
                mail.envoyes().stream()
                        .filter(m -> m.destinataire().equals(destinataire))
                        .findFirst()
                        .orElseThrow()
                        .corps();
        Matcher m = Pattern.compile(nom + " : (\\S+)").matcher(corps);
        assertTrue(m.find());
        return m.group(1);
    }
}
