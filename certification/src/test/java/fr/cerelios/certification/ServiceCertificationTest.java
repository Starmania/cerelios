package fr.cerelios.certification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.Certificats;
import fr.cerelios.protocole.Conversions;
import fr.cerelios.protocole.MailJournal;
import fr.cerelios.protocole.messages.ConsulterCredits;
import fr.cerelios.protocole.messages.CredentialsCertifies;
import fr.cerelios.protocole.messages.ElecteurAContacter;
import fr.cerelios.protocole.messages.Electeurs;
import fr.cerelios.protocole.messages.NouvelleDemande;
import fr.cerelios.protocole.messages.Renvoi;
import fr.cerelios.protocole.messages.ReponseCertification;
import fr.cerelios.protocole.messages.RequeteCertification;
import fr.cerelios.protocole.messages.ValidationCertification;
import fr.cerelios.protocole.messages.Vide;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceCertificationTest {

    private static final Groupe GROUPE = Groupe.RFC_3526;

    private final MailJournal mail = new MailJournal();
    private final ServiceCertification service = new ServiceCertification(GROUPE, mail, 2);
    private CredentialsCertifies certifies;
    private String graine;

    @BeforeEach
    void demande() {
        NouvelleDemande.Builder demande =
                NouvelleDemande.newBuilder()
                        .setReferendum("ref")
                        .setQuestion("Pour ?")
                        .setEmailOperateur("op@exemple.fr")
                        .setJeton("jeton-urne");
        for (String e : List.of("bob", "carol")) {
            demande.addElecteurs(
                    ElecteurAContacter.newBuilder().setIdentifiant(e).setEmail(e + "@exemple.fr"));
        }
        certifies = ok(RequeteCertification.newBuilder().setDemande(demande)).getCredentials();
        graine = champ("op@exemple.fr", "graine");
    }

    @Test
    void leCertificatEngageLeServeurSurLesCredentialsRendus() {
        List<BigInteger> publics =
                certifies.getCredentialsList().stream()
                        .map(c -> Conversions.entier(c.getCredential()))
                        .toList();

        assertEquals(2, publics.size());
        assertEquals(
                List.of(),
                Certificats.verifier(GROUPE, certifies.getCertificat(), "ref", publics, 2));
        assertTrue(Certificats.graineDuCertificat(GROUPE, graine, certifies.getCertificat()));
        assertFalse(
                Certificats.graineDuCertificat(GROUPE, graine + "x", certifies.getCertificat()));
    }

    @Test
    void seuleLUrneQuiConnaitLeJetonDeclencheLEnvoi() {
        refus(validation("autre"), "Jeton invalide");
        assertEquals(1, mail.envoyes().size());

        ok(validation("jeton-urne"));

        // Chaque électeur reçoit le credential dont la partie publique a été certifiée.
        for (int i = 0; i < 2; i++) {
            String electeur = certifies.getCredentials(i).getElecteur();
            BigInteger secret = new BigInteger(champ(electeur + "@exemple.fr", "credential"), 16);
            assertEquals(
                    Conversions.entier(certifies.getCredentials(i).getCredential()),
                    Elgamal.depuisSecrete(GROUPE, secret).publique());
        }
        refus(validation("jeton-urne"), "déjà envoyés");
    }

    @Test
    void sansLaGraineRienNeSeRenvoie() {
        refus(renvoi(graine).setTous(Vide.getDefaultInstance()), "pas encore envoyés");
        ok(validation("jeton-urne"));

        refus(renvoi("mauvaise").setTous(Vide.getDefaultInstance()), "Graine invalide");
        refus(credits("mauvaise"), "Graine invalide");
    }

    @Test
    void chaqueRenvoiCouteUnCreditParElecteur() {
        ok(validation("jeton-urne"));
        assertEquals(4, ok(credits(graine)).getCredits().getRestants());

        ok(renvoi(graine).setTous(Vide.getDefaultInstance()));
        ok(renvoi(graine).setElecteurs(Electeurs.newBuilder().addElecteurs("bob")));
        assertEquals(1, ok(credits(graine)).getCredits().getRestants());
        assertEquals(1 + 2 + 2 + 1, mail.envoyes().size());

        refus(renvoi(graine).setTous(Vide.getDefaultInstance()), "Crédits insuffisants");
        refus(renvoi(graine).setElecteurs(Electeurs.newBuilder().addElecteurs("eve")), "inconnu");
    }

    @Test
    void uneDemandeNeSeFaitQuUneFois() {
        refus(
                RequeteCertification.newBuilder()
                        .setDemande(
                                NouvelleDemande.newBuilder()
                                        .setReferendum("ref")
                                        .addElecteurs(
                                                ElecteurAContacter.newBuilder()
                                                        .setIdentifiant("x"))),
                "déjà générés");
    }

    // ---- Outils --------------------------------------------------------------------------------

    private static RequeteCertification.Builder validation(String jeton) {
        return RequeteCertification.newBuilder()
                .setValidation(
                        ValidationCertification.newBuilder().setReferendum("ref").setJeton(jeton));
    }

    private static Renvoi.Builder renvoi(String graine) {
        return Renvoi.newBuilder().setReferendum("ref").setGraine(graine);
    }

    private static RequeteCertification.Builder credits(String graine) {
        return RequeteCertification.newBuilder()
                .setCredits(ConsulterCredits.newBuilder().setReferendum("ref").setGraine(graine));
    }

    private ReponseCertification ok(Renvoi.Builder renvoi) {
        return ok(RequeteCertification.newBuilder().setRenvoi(renvoi));
    }

    private void refus(Renvoi.Builder renvoi, String extrait) {
        refus(RequeteCertification.newBuilder().setRenvoi(renvoi), extrait);
    }

    private ReponseCertification ok(RequeteCertification.Builder requete) {
        ReponseCertification reponse = service.traiter(requete.build());
        assertFalse(reponse.hasErreur(), () -> "Refusé : " + reponse.getErreur().getMessage());
        return reponse;
    }

    private void refus(RequeteCertification.Builder requete, String extrait) {
        ReponseCertification reponse = service.traiter(requete.build());
        assertTrue(reponse.hasErreur(), "La requête aurait dû être refusée");
        assertTrue(
                reponse.getErreur().getMessage().contains(extrait),
                () -> "Message inattendu : " + reponse.getErreur().getMessage());
    }

    private String champ(String destinataire, String nom) {
        List<MailJournal.Mail> recus =
                mail.envoyes().stream().filter(m -> m.destinataire().equals(destinataire)).toList();
        Matcher m = Pattern.compile(nom + " : (\\S+)").matcher(recus.getLast().corps());
        assertTrue(m.find());
        return m.group(1);
    }
}
