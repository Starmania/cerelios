package fr.cerelios.gui.administrateur;

import fr.cerelios.gui.Contexte;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.messages.AjouterScrutateur;
import fr.cerelios.protocole.messages.CreerReferendum;
import fr.cerelios.protocole.messages.Dates;
import fr.cerelios.protocole.messages.DefinirDates;
import fr.cerelios.protocole.messages.DefinirElecteurs;
import fr.cerelios.protocole.messages.DefinirQuestion;
import fr.cerelios.protocole.messages.DefinirServeurCertification;
import fr.cerelios.protocole.messages.DefinirSeuil;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Emargement;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Requêtes de l'administrateur connecté, sur un référendum qu'il possède. */
final class Administration {

    private final Contexte contexte;

    Administration(Contexte contexte) {
        this.contexte = contexte;
    }

    /** Connexion ouverte à la demande : ces méthodes ne tournent que dans Contexte.executer. */
    private ClientUrne urne() throws IOException {
        return contexte.urne();
    }

    List<ReferendumPublic> mesReferendums() throws IOException, RefusException {
        return urne().appeler(
                        Requete.newBuilder()
                                .setListerMesReferendums(Referendum.getDefaultInstance()))
                .getReferendums()
                .getReferendumsList();
    }

    String creer(String question) throws IOException, RefusException {
        return urne().appeler(
                        Requete.newBuilder()
                                .setCreerReferendum(
                                        CreerReferendum.newBuilder().setQuestion(question)))
                .getIdentifiant()
                .getId();
    }

    ReferendumPublic referendum(String id) throws IOException, RefusException {
        return urne().appeler(Requete.newBuilder().setObtenirReferendum(ref(id))).getReferendum();
    }

    DonneesPubliques donneesPubliques(String id) throws IOException, RefusException {
        return urne().appeler(Requete.newBuilder().setObtenirDonneesPubliques(ref(id)))
                .getDonneesPubliques();
    }

    void definirQuestion(String id, String question) throws IOException, RefusException {
        urne().appeler(
                        Requete.newBuilder()
                                .setDefinirQuestion(
                                        DefinirQuestion.newBuilder()
                                                .setReferendum(id)
                                                .setQuestion(question)));
    }

    void definirElecteurs(String id, List<String> electeurs, Map<String, String> emails)
            throws IOException, RefusException {
        urne().appeler(
                        Requete.newBuilder()
                                .setDefinirElecteurs(
                                        DefinirElecteurs.newBuilder()
                                                .setReferendum(id)
                                                .addAllElecteurs(electeurs)
                                                .putAllEmails(emails)));
    }

    void definirDates(String id, Dates dates) throws IOException, RefusException {
        urne().appeler(
                        Requete.newBuilder()
                                .setDefinirDates(
                                        DefinirDates.newBuilder()
                                                .setReferendum(id)
                                                .setDates(dates)));
    }

    void definirSeuil(String id, int seuil) throws IOException, RefusException {
        urne().appeler(
                        Requete.newBuilder()
                                .setDefinirSeuil(
                                        DefinirSeuil.newBuilder()
                                                .setReferendum(id)
                                                .setSeuil(seuil)));
    }

    void ajouterScrutateur(String id, String nom, String email) throws IOException, RefusException {
        urne().appeler(
                        Requete.newBuilder()
                                .setAjouterScrutateur(
                                        AjouterScrutateur.newBuilder()
                                                .setReferendum(id)
                                                .setNom(nom)
                                                .setEmail(email)));
    }

    void definirServeurCertification(String id, String adresse, String emailOperateur)
            throws IOException, RefusException {
        urne().appeler(
                        Requete.newBuilder()
                                .setDefinirServeurCertification(
                                        DefinirServeurCertification.newBuilder()
                                                .setReferendum(id)
                                                .setAdresse(adresse)
                                                .setEmailOperateur(emailOperateur)));
    }

    void demanderCredentials(String id) throws IOException, RefusException {
        urne().appeler(Requete.newBuilder().setDemanderCredentials(ref(id)));
    }

    String jetonAutorite(String id) throws IOException, RefusException {
        return urne().appeler(Requete.newBuilder().setObtenirJetonAutorite(ref(id)))
                .getJeton()
                .getJeton();
    }

    /**
     * Liste des électeurs enregistrée, lue avec le jeton de l'autorité que l'administrateur
     * détient.
     */
    List<String> electeurs(String id) throws IOException, RefusException {
        return urne().appeler(
                        Requete.newBuilder()
                                .setJeton(jetonAutorite(id))
                                .setObtenirElecteurs(ref(id)))
                .getElecteurs()
                .getElecteursList();
    }

    List<String> manques(String id) throws IOException, RefusException {
        return urne().appeler(Requete.newBuilder().setObtenirStatut(ref(id)))
                .getStatut()
                .getManquesList();
    }

    void valider(String id) throws IOException, RefusException {
        urne().appeler(Requete.newBuilder().setValider(ref(id)));
    }

    void sceller(String id) throws IOException, RefusException {
        urne().appeler(Requete.newBuilder().setSceller(ref(id)));
    }

    List<Emargement.Ligne> emargement(String id) throws IOException, RefusException {
        return urne().appeler(Requete.newBuilder().setObtenirEmargement(ref(id)))
                .getEmargement()
                .getLignesList();
    }

    void archiver(String id) throws IOException, RefusException {
        urne().appeler(Requete.newBuilder().setArchiver(ref(id)));
    }

    void supprimer(String id) throws IOException, RefusException {
        urne().appeler(Requete.newBuilder().setSupprimer(ref(id)));
    }

    private static Referendum.Builder ref(String id) {
        return Referendum.newBuilder().setReferendum(id);
    }
}
