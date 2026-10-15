package fr.cerelios.gui.auditeur;

import fr.cerelios.electeur.Electeur;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.FicheReferendum;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.Verificateur;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import java.util.List;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Audit d'un référendum : n'importe qui refait tous les contrôles, sans aucun secret. */
public final class Audit {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    @FXML private TextField referendum;
    @FXML private TextField traceur;
    @FXML private Button rechercher;
    @FXML private FicheReferendum fiche;
    @FXML private VBox carteResultat;
    @FXML private VBox resume;
    @FXML private ListView<String> anomalies;

    public Audit(Contexte contexte) {
        this.contexte = contexte;
    }

    public Parent vue() {
        return Ui.page(contexte, "Auditeur", Ui.charger("Audit.fxml", this), statut);
    }

    @FXML
    private void initialize() {
        rechercher
                .disableProperty()
                .bind(referendum.textProperty().isEmpty().or(traceur.textProperty().isEmpty()));
    }

    /** Cherche le bulletin dont l'empreinte est {@code traceur} dans l'urne publiée. */
    @FXML
    private void rechercher() {
        String id = referendum.getText().strip();
        // Les traceurs sont en hexadécimal minuscule ; on tolère une copie en majuscules.
        String empreinte = traceur.getText().strip().toLowerCase(Locale.ROOT);
        contexte.executer(
                statut,
                () -> new Electeur(contexte.urne(), Contexte.GROUPE).bulletinPresent(id, empreinte),
                present -> {
                    if (present) {
                        statut.succes(
                                "Ce bulletin est dans l'urne publiée : il compte dans le total.");
                    } else {
                        statut.erreur(
                                "Aucun bulletin avec ce traceur. L'électeur a pu revoter (seul son"
                                        + " dernier bulletin reste), sinon le bulletin a disparu :"
                                        + " c'est à signaler.");
                    }
                });
    }

    private record Audite(DonneesPubliques donnees, List<String> anomalies) {}

    @FXML
    private void verifier() {
        String id = referendum.getText().strip();
        contexte.executer(
                statut,
                () -> {
                    DonneesPubliques donnees =
                            contexte.urne()
                                    .appeler(
                                            Requete.newBuilder()
                                                    .setObtenirDonneesPubliques(
                                                            Referendum.newBuilder()
                                                                    .setReferendum(id)))
                                    .getDonneesPubliques();
                    return new Audite(donnees, new Verificateur(Contexte.GROUPE).verifier(donnees));
                },
                this::afficher);
    }

    private void afficher(Audite audit) {
        DonneesPubliques d = audit.donnees();
        ReferendumPublic r = d.getReferendum();
        fiche.afficher(r);
        Ui.montrer(fiche, true);
        resume.getChildren()
                .setAll(
                        Ui.texte(d.getCredentialsCount() + " credential(s) publiés"),
                        Ui.texte(d.getBulletinsCount() + " bulletin(s) vérifiés"),
                        Ui.texte(
                                r.getSeuil() > 0
                                        ? "Clé à seuil : "
                                                + r.getSeuil()
                                                + " scrutateurs sur "
                                                + r.getScrutateursCount()
                                        : r.getScrutateursCount()
                                                + " scrutateur(s), tous nécessaires"),
                        Ui.texte(
                                d.hasCertificat()
                                        ? "Credentials certifiés par un serveur de credentials"
                                        : "Credentials d'une autorité de certification manuelle"),
                        Ui.texte(
                                d.getScellementsCount() > 0
                                        ? "Calendrier scellé"
                                        : "Calendrier non scellé"));
        anomalies.getItems().setAll(audit.anomalies());
        Ui.montrer(carteResultat, true);
        if (audit.anomalies().isEmpty()) {
            statut.succes("Tous les contrôles passent.");
        } else {
            statut.erreur(audit.anomalies().size() + " anomalie(s) : voir la liste.");
        }
    }
}
