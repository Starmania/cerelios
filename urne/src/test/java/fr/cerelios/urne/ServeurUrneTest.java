package fr.cerelios.urne;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.autorite.AutoriteCertification;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.electeur.Electeur;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.Verificateur;
import fr.cerelios.protocole.messages.AjouterScrutateur;
import fr.cerelios.protocole.messages.Connexion;
import fr.cerelios.protocole.messages.CreerReferendum;
import fr.cerelios.protocole.messages.Dates;
import fr.cerelios.protocole.messages.DefinirDates;
import fr.cerelios.protocole.messages.DefinirElecteurs;
import fr.cerelios.protocole.messages.Etat;
import fr.cerelios.protocole.messages.Recepisse;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import fr.cerelios.scrutateur.Scrutateur;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Référendum complet par le réseau, chaque rôle passant par son vrai programme client. */
class ServeurUrneTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;
    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");

    @TempDir Path dossier;

    @Test
    void referendumCompletParLeReseau() throws Exception {
        IdentitesLocales idp = new IdentitesLocales();
        idp.inscrire("alice", "mdp-alice", "alice@exemple.fr");
        idp.inscrire("bob", "mdp-bob", "bob@exemple.fr");
        idp.inscrire("carol", "mdp-carol", "carol@exemple.fr");
        HorlogeControle horloge = new HorlogeControle(T0);
        MailJournal mail = new MailJournal();

        try (ServeurUrne serveur = new ServeurUrne(new Urne(idp, mail, horloge), 0)) {
            serveur.demarrer();
            int port = serveur.port();

            // Administratrice : brouillon, électeurs, dates, scrutateur.
            String id;
            String jetonAutorite;
            try (ClientUrne admin = new ClientUrne("localhost", port)) {
                admin.utiliserJeton(
                        admin.appeler(
                                        Requete.newBuilder()
                                                .setConnexion(
                                                        Connexion.newBuilder()
                                                                .setIdentifiant("alice")
                                                                .setSecret("mdp-alice")))
                                .getJeton()
                                .getJeton());
                id =
                        admin.appeler(
                                        Requete.newBuilder()
                                                .setCreerReferendum(
                                                        CreerReferendum.newBuilder()
                                                                .setQuestion(
                                                                        "Adopter le budget ?")))
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
                                                                .setOuverture(
                                                                        T0.plusSeconds(60)
                                                                                .toEpochMilli())
                                                                .setCloture(
                                                                        T0.plusSeconds(120)
                                                                                .toEpochMilli())
                                                                .setPublication(
                                                                        T0.plusSeconds(180)
                                                                                .toEpochMilli()))));
                admin.appeler(
                        Requete.newBuilder()
                                .setAjouterScrutateur(
                                        AjouterScrutateur.newBuilder()
                                                .setReferendum(id)
                                                .setNom("S1")
                                                .setEmail("s1@exemple.fr")));
                jetonAutorite =
                        admin.appeler(
                                        Requete.newBuilder()
                                                .setObtenirJetonAutorite(
                                                        Referendum.newBuilder().setReferendum(id)))
                                .getJeton()
                                .getJeton();
            }

            // Scrutateur : invité par e-mail, il génère sa clé.
            Path secretS1 = dossier.resolve("s1.cle");
            try (ClientUrne client = new ClientUrne("localhost", port)) {
                new Scrutateur(client, GROUPE, id, "S1", champ(mail, "s1@exemple.fr", "jeton"))
                        .genererCle(secretS1);
            }

            // Autorité de certification : credentials générés hors de l'urne.
            Map<String, String> credentials;
            try (ClientUrne client = new ClientUrne("localhost", port)) {
                AutoriteCertification autorite =
                        new AutoriteCertification(client, GROUPE, id, jetonAutorite);
                assertEquals(List.of("bob", "carol"), autorite.electeurs());
                credentials = autorite.generer();
                AutoriteCertification.enregistrer(credentials, dossier.resolve("credentials.txt"));
            }

            try (ClientUrne admin = new ClientUrne("localhost", port)) {
                Electeur connexion = new Electeur(admin, GROUPE);
                connexion.connecter("alice", "mdp-alice");
                admin.appeler(
                        Requete.newBuilder().setValider(Referendum.newBuilder().setReferendum(id)));
            }

            // Électeurs.
            horloge.regleHorloge(T0.plusSeconds(60));
            Recepisse recuBob = voter(port, "bob", id, credentials.get("bob"), true);
            voter(port, "carol", id, credentials.get("carol"), true);
            Recepisse revoteCarol = voter(port, "carol", id, credentials.get("carol"), false);
            assertTrue(revoteCarol.getRevote());

            // Dépouillement : le total est calculé à la première lecture après la clôture.
            horloge.regleHorloge(T0.plusSeconds(120));
            try (ClientUrne client = new ClientUrne("localhost", port)) {
                Scrutateur s1 =
                        new Scrutateur(
                                client, GROUPE, id, "S1", champ(mail, "s1@exemple.fr", "jeton"));
                assertTrue(s1.cleUtilisee(secretS1));
                s1.dechiffrer(secretS1);
                assertTrue(s1.dechiffrementPublie().isPresent());
            }

            horloge.regleHorloge(T0.plusSeconds(180));
            try (ClientUrne client = new ClientUrne("localhost", port)) {
                Electeur bob = new Electeur(client, GROUPE);
                ReferendumPublic resultat = bob.referendum(id);
                assertEquals(Etat.PUBLIE, resultat.getEtat());
                assertEquals(1, resultat.getResultat().getOui());
                assertEquals(1, resultat.getResultat().getNon());
                assertTrue(bob.bulletinPresent(id, recuBob.getEmpreinte()));

                AutoriteCertification autorite =
                        new AutoriteCertification(client, GROUPE, id, jetonAutorite);
                assertEquals(
                        List.of(),
                        autorite.verifierPublication(
                                AutoriteCertification.relire(dossier.resolve("credentials.txt"))));
                assertEquals(
                        List.of(),
                        new Verificateur(GROUPE)
                                .verifier(
                                        client.appeler(
                                                        Requete.newBuilder()
                                                                .setObtenirDonneesPubliques(
                                                                        Referendum.newBuilder()
                                                                                .setReferendum(id)))
                                                .getDonneesPubliques()));
            }
        }
    }

    private static Recepisse voter(
            int port, String electeur, String id, String credential, boolean oui) throws Exception {
        try (ClientUrne client = new ClientUrne("localhost", port)) {
            Electeur e = new Electeur(client, GROUPE);
            e.connecter(electeur, "mdp-" + electeur);
            return e.voter(id, credential, oui);
        }
    }

    private static String champ(MailJournal mail, String destinataire, String nom) {
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
