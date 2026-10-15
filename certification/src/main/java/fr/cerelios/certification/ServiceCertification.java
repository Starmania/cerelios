package fr.cerelios.certification;

import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.octets;

import fr.cerelios.crypto.ChiffrementHybride;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.Certificats;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.ServiceMail;
import fr.cerelios.protocole.messages.Certificat;
import fr.cerelios.protocole.messages.ConsulterCredits;
import fr.cerelios.protocole.messages.CredentialPublic;
import fr.cerelios.protocole.messages.CredentialsCertifies;
import fr.cerelios.protocole.messages.Credits;
import fr.cerelios.protocole.messages.ElecteurAContacter;
import fr.cerelios.protocole.messages.Erreur;
import fr.cerelios.protocole.messages.NouvelleDemande;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.Renvoi;
import fr.cerelios.protocole.messages.ReponseCertification;
import fr.cerelios.protocole.messages.Requete;
import fr.cerelios.protocole.messages.RequeteCertification;
import fr.cerelios.protocole.messages.ValidationCertification;
import fr.cerelios.protocole.messages.Vide;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Traite les requêtes de l'urne et de l'opérateur ; un dossier par référendum. */
public class ServiceCertification {

    private static final SecureRandom ALEA = new SecureRandom();
    private static final System.Logger JOURNAL =
            System.getLogger(ServiceCertification.class.getName());

    /** Ce qui est gardé d'un électeur : son adresse et son credential secret, chiffré. */
    private record Enregistrement(String email, ChiffrementHybride.Message credential) {}

    private static final class Dossier {
        final String referendum;
        final String question;
        final String jeton;
        final String adresseUrne;
        final Certificat certificat;
        final Map<String, Enregistrement> electeurs;
        String graine; // oubliée dès que les credentials sont envoyés
        int credits;

        Dossier(
                NouvelleDemande demande,
                Certificat certificat,
                Map<String, Enregistrement> electeurs,
                String graine) {
            this.referendum = demande.getReferendum();
            this.question = demande.getQuestion();
            this.jeton = demande.getJeton();
            this.adresseUrne = demande.getAdresseUrne();
            this.certificat = certificat;
            this.electeurs = electeurs;
            this.graine = graine;
        }
    }

    private final Groupe groupe;
    private final ServiceMail mail;
    private final int multiplicateurCredits;
    private final Map<String, Dossier> dossiers = new HashMap<>();

    /**
     * @param multiplicateurCredits nombre de renvois accordés par électeur, une fois les
     *     credentials envoyés
     */
    public ServiceCertification(Groupe groupe, ServiceMail mail, int multiplicateurCredits) {
        this.groupe = groupe;
        this.mail = mail;
        this.multiplicateurCredits = multiplicateurCredits;
    }

    public ReponseCertification traiter(RequeteCertification requete) {
        try {
            return switch (requete.getContenuCase()) {
                case DEMANDE -> demande(requete.getDemande());
                case VALIDATION -> validation(requete.getValidation());
                case RENVOI -> renvoi(requete.getRenvoi());
                case CREDITS -> credits(requete.getCredits());
                case CONTENU_NOT_SET -> throw new RefusException("Requête vide");
            };
        } catch (RefusException | IllegalArgumentException e) {
            return ReponseCertification.newBuilder()
                    .setErreur(Erreur.newBuilder().setMessage(e.getMessage()))
                    .build();
        } catch (IOException | RuntimeException e) {
            JOURNAL.log(System.Logger.Level.ERROR, "Erreur inattendue", e);
            return ReponseCertification.newBuilder()
                    .setErreur(Erreur.newBuilder().setMessage("Erreur interne du serveur"))
                    .build();
        }
    }

    /**
     * Génère un credential par électeur, garde les secrets chiffrés sous la clé de la graine, et
     * envoie la graine à l'opérateur. Les credentials partiront aux électeurs à la validation.
     */
    private synchronized ReponseCertification demande(NouvelleDemande demande)
            throws RefusException {
        if (dossiers.containsKey(demande.getReferendum())) {
            throw new RefusException("Credentials déjà générés pour ce référendum");
        }
        if (demande.getElecteursCount() == 0) {
            throw new RefusException("Aucun électeur");
        }
        String graine = graine();
        BigInteger cleChiffrement = Certificats.cleChiffrement(groupe, graine).publique();
        Map<String, Enregistrement> electeurs = new LinkedHashMap<>();
        CredentialsCertifies.Builder reponse = CredentialsCertifies.newBuilder();
        List<BigInteger> publics = new ArrayList<>();
        for (ElecteurAContacter e : demande.getElecteursList()) {
            PaireDeCles credential = Elgamal.keyGen(groupe);
            ChiffrementHybride.Message chiffre =
                    ChiffrementHybride.chiffrer(
                            groupe,
                            cleChiffrement,
                            credential.secrete().toString(16).getBytes(StandardCharsets.US_ASCII),
                            Certificats.contexteCredential(
                                    demande.getReferendum(), e.getIdentifiant()));
            if (electeurs.put(e.getIdentifiant(), new Enregistrement(e.getEmail(), chiffre))
                    != null) {
                throw new RefusException("Électeur en double : " + e.getIdentifiant());
            }
            publics.add(credential.publique());
            reponse.addCredentials(
                    CredentialPublic.newBuilder()
                            .setElecteur(e.getIdentifiant())
                            .setCredential(octets(credential.publique())));
        }
        Certificat certificat =
                Certificats.signer(groupe, graine, demande.getReferendum(), publics);
        dossiers.put(demande.getReferendum(), new Dossier(demande, certificat, electeurs, graine));
        mail.envoyer(
                demande.getEmailOperateur(),
                "Graine des credentials du référendum « " + demande.getQuestion() + " »",
                "Vous êtes l'opérateur des credentials de ce référendum.\n"
                        + "  référendum : "
                        + demande.getReferendum()
                        + "\n  graine : "
                        + graine
                        + "\nGardez la graine secrète : elle seule permet de renvoyer un"
                        + " credential perdu et de vérifier le certificat publié.");
        return ReponseCertification.newBuilder()
                .setCredentials(reponse.setCertificat(certificat))
                .build();
    }

    /**
     * L'urne a validé le référendum : chaque électeur reçoit son credential, la graine est oubliée.
     */
    private synchronized ReponseCertification validation(ValidationCertification validation)
            throws RefusException {
        Dossier dossier = dossier(validation.getReferendum());
        if (!MessageDigest.isEqual(
                dossier.jeton.getBytes(StandardCharsets.UTF_8),
                validation.getJeton().getBytes(StandardCharsets.UTF_8))) {
            throw new RefusException("Jeton invalide");
        }
        if (dossier.graine == null) {
            throw new RefusException("Credentials déjà envoyés");
        }
        PaireDeCles cle = Certificats.cleChiffrement(groupe, dossier.graine);
        for (String electeur : dossier.electeurs.keySet()) {
            envoyer(dossier, cle, electeur);
        }
        dossier.graine = null;
        dossier.credits = multiplicateurCredits * dossier.electeurs.size();
        return ok();
    }

    /**
     * L'opérateur fait renvoyer des credentials, en fournissant la graine : à tous, à certains, ou
     * à ceux qui n'ont pas encore voté. Chaque e-mail coûte un crédit.
     */
    private synchronized ReponseCertification renvoi(Renvoi renvoi)
            throws RefusException, IOException {
        Dossier dossier = dossier(renvoi.getReferendum());
        PaireDeCles cle = cleDeLaGraine(dossier, renvoi.getGraine());
        List<String> cibles =
                switch (renvoi.getCibleCase()) {
                    case TOUS -> List.copyOf(dossier.electeurs.keySet());
                    case ELECTEURS -> {
                        List<String> liste = renvoi.getElecteurs().getElecteursList();
                        for (String e : liste) {
                            if (!dossier.electeurs.containsKey(e)) {
                                throw new RefusException("Électeur inconnu : " + e);
                            }
                        }
                        yield liste;
                    }
                    case SANS_BULLETIN -> sansBulletin(dossier, cle);
                    case CIBLE_NOT_SET -> throw new RefusException("Préciser à qui renvoyer");
                };
        if (cibles.size() > dossier.credits) {
            throw new RefusException(
                    "Crédits insuffisants : " + dossier.credits + " pour " + cibles.size());
        }
        for (String electeur : cibles) {
            envoyer(dossier, cle, electeur);
        }
        dossier.credits -= cibles.size();
        return ok();
    }

    private synchronized ReponseCertification credits(ConsulterCredits demande)
            throws RefusException {
        Dossier dossier = dossier(demande.getReferendum());
        cleDeLaGraine(dossier, demande.getGraine());
        return ReponseCertification.newBuilder()
                .setCredits(Credits.newBuilder().setRestants(dossier.credits))
                .build();
    }

    /** Électeurs dont le credential n'a déposé aucun bulletin dans l'urne publiée. */
    private List<String> sansBulletin(Dossier dossier, PaireDeCles cle)
            throws IOException, RefusException {
        Set<BigInteger> ontVote;
        try (ClientUrne urne = ClientUrne.vers(dossier.adresseUrne)) {
            ontVote =
                    urne
                            .appeler(
                                    Requete.newBuilder()
                                            .setObtenirDonneesPubliques(
                                                    Referendum.newBuilder()
                                                            .setReferendum(dossier.referendum)))
                            .getDonneesPubliques()
                            .getBulletinsList()
                            .stream()
                            .map(b -> entier(b.getCredential()))
                            .collect(Collectors.toSet());
        }
        List<String> cibles = new ArrayList<>();
        for (String electeur : dossier.electeurs.keySet()) {
            BigInteger publique =
                    Elgamal.depuisSecrete(
                                    groupe, new BigInteger(dechiffrer(dossier, cle, electeur), 16))
                            .publique();
            if (!ontVote.contains(publique)) {
                cibles.add(electeur);
            }
        }
        return cibles;
    }

    private void envoyer(Dossier dossier, PaireDeCles cle, String electeur) throws RefusException {
        Enregistrement e = dossier.electeurs.get(electeur);
        mail.envoyer(
                e.email(),
                "Votre credential pour le référendum « " + dossier.question + " »",
                "Pour voter, ouvrez l'application Cerelios et saisissez :\n"
                        + "  référendum : "
                        + dossier.referendum
                        + "\n  credential : "
                        + dechiffrer(dossier, cle, electeur)
                        + "\n"
                        + "Ne le communiquez à personne : il prouve que le bulletin est le"
                        + " vôtre.");
    }

    private String dechiffrer(Dossier dossier, PaireDeCles cle, String electeur)
            throws RefusException {
        return ChiffrementHybride.dechiffrer(
                        groupe,
                        cle.secrete(),
                        dossier.electeurs.get(electeur).credential(),
                        Certificats.contexteCredential(dossier.referendum, electeur))
                .map(octets -> new String(octets, StandardCharsets.US_ASCII))
                .orElseThrow(() -> new RefusException("Credential illisible avec cette graine"));
    }

    private PaireDeCles cleDeLaGraine(Dossier dossier, String graine) throws RefusException {
        if (!Certificats.graineDuCertificat(groupe, graine, dossier.certificat)) {
            throw new RefusException("Graine invalide");
        }
        if (dossier.graine != null) {
            throw new RefusException("Les credentials ne sont pas encore envoyés");
        }
        return Certificats.cleChiffrement(groupe, graine);
    }

    private Dossier dossier(String referendum) throws RefusException {
        Dossier dossier = dossiers.get(referendum);
        if (dossier == null) {
            throw new RefusException("Référendum inconnu du serveur de credentials");
        }
        return dossier;
    }

    /** Graine de 264 bits, en base64 : 44 caractères à recopier par l'opérateur. */
    private static String graine() {
        byte[] octets = new byte[33];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    private static ReponseCertification ok() {
        return ReponseCertification.newBuilder().setOk(Vide.getDefaultInstance()).build();
    }
}
