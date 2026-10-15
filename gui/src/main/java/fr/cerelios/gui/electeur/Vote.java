package fr.cerelios.gui.electeur;

import fr.cerelios.electeur.Electeur;
import fr.cerelios.gui.ChampCopiable;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.FicheReferendum;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.messages.Etat;
import fr.cerelios.protocole.messages.Recepisse;
import fr.cerelios.protocole.messages.ReferendumPublic;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

/** Écran de l'électeur connecté : ouvrir un référendum, voter, vérifier son bulletin. */
public final class Vote {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private String referendum;
    @FXML private TextField numero;
    @FXML private FicheReferendum fiche;
    @FXML private VBox carteVote;
    @FXML private VBox carteRecepisse;
    @FXML private ToggleGroup choix;
    @FXML private RadioButton oui;
    @FXML private PasswordField credential;
    @FXML private Button voter;
    @FXML private ChampCopiable traceur;

    public Vote(Contexte contexte) {
        this.contexte = contexte;
    }

    public Parent vue() {
        return Ui.page(contexte, "Électeur", Ui.charger("Vote.fxml", this), statut);
    }

    @FXML
    private void initialize() {
        voter.disableProperty()
                .bind(
                        choix.selectedToggleProperty()
                                .isNull()
                                .or(credential.textProperty().isEmpty()));
    }

    @FXML
    private void ouvrir() {
        String id = numero.getText().strip();
        contexte.executer(
                statut,
                () -> new Electeur(contexte.urne(), Contexte.GROUPE).referendum(id),
                r -> {
                    referendum = id;
                    afficher(r);
                });
    }

    private void afficher(ReferendumPublic r) {
        fiche.afficher(r);
        Ui.montrer(fiche, true);
        boolean ouvert = r.getEtat() == Etat.OUVERT || r.getEtat() == Etat.CLOS;
        Ui.montrer(carteVote, ouvert);
        if (r.getEtat() == Etat.BROUILLON || r.getEtat() == Etat.VALIDE) {
            statut.succes("Le vote n'est pas encore ouvert.");
        }
    }

    @FXML
    private void voter() {
        boolean pourOui = choix.getSelectedToggle() == oui;
        if (!Ui.confirmer(
                contexte, "Vous allez voter « " + (pourOui ? "Oui" : "Non") + " ». Confirmer ?")) {
            return;
        }
        String secret = credential.getText();
        contexte.executer(
                statut,
                () ->
                        new Electeur(contexte.urne(), Contexte.GROUPE)
                                .voter(referendum, secret, pourOui),
                this::recu);
    }

    private void recu(Recepisse recepisse) {
        traceur.setTexte(recepisse.getEmpreinte());
        Ui.montrer(carteRecepisse, true);
        choix.selectToggle(null);
        statut.succes(
                recepisse.getRevote()
                        ? "Vote enregistré : il remplace votre vote précédent."
                        : "Vote enregistré. Une confirmation vous a été envoyée par e-mail.");
    }

    @FXML
    private void verifier() {
        String empreinte = traceur.getTexte();
        contexte.executer(
                statut,
                () ->
                        new Electeur(contexte.urne(), Contexte.GROUPE)
                                .bulletinPresent(referendum, empreinte),
                present -> {
                    if (present) {
                        statut.succes("Votre bulletin est bien dans l'urne publiée.");
                    } else {
                        statut.erreur(
                                "Bulletin introuvable dans l'urne publiée : signalez-le à"
                                        + " l'administrateur du référendum.");
                    }
                });
    }
}
