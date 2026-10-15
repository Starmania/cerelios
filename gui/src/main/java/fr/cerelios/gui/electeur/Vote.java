package fr.cerelios.gui.electeur;

import fr.cerelios.electeur.Electeur;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.FicheReferendum;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.messages.Etat;
import fr.cerelios.protocole.messages.Recepisse;
import fr.cerelios.protocole.messages.ReferendumPublic;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

/** Écran de l'électeur connecté : ouvrir un référendum, voter, vérifier son bulletin. */
public final class Vote {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private final TextField numero = new TextField();
    private final FicheReferendum fiche = new FicheReferendum();
    private final PasswordField credential = new PasswordField();
    private final ToggleGroup choix = new ToggleGroup();
    private final RadioButton oui = new RadioButton("Oui");
    private final RadioButton non = new RadioButton("Non");
    private final TextField traceur = new TextField();
    private final VBox carteVote;
    private final VBox carteRecepisse;
    private String referendum;

    public Vote(Contexte contexte) {
        this.contexte = contexte;
        oui.setToggleGroup(choix);
        non.setToggleGroup(choix);
        oui.getStyleClass().add("choix");
        non.getStyleClass().add("choix");
        credential.setPromptText("Reçu par e-mail ou de l'autorité de certification");

        Button voter = Ui.principal("Voter", this::voter);
        voter.disableProperty()
                .bind(
                        choix.selectedToggleProperty()
                                .isNull()
                                .or(credential.textProperty().isEmpty()));
        carteVote =
                Ui.carte(
                        "Votre vote",
                        Ui.ligne(oui, non),
                        Ui.formulaire("Credential", credential),
                        Ui.aide(
                                "Le vote est chiffré sur ce poste avant d'être envoyé. Vous pouvez"
                                        + " revoter tant que le vote est ouvert : seul le dernier"
                                        + " bulletin compte."),
                        voter);
        carteRecepisse =
                Ui.carte(
                        "Votre récépissé",
                        Ui.aide(
                                "Notez ce traceur : il permet de retrouver votre bulletin dans"
                                        + " l'urne publiée, sans révéler votre vote."),
                        Ui.copiable(traceur),
                        Ui.bouton("Vérifier qu'il est dans l'urne", this::verifier));
        montrer(carteVote, false);
        montrer(carteRecepisse, false);
        montrer(fiche, false);
    }

    public Parent vue() {
        numero.setPromptText("Numéro du référendum, reçu avec votre credential");
        Button ouvrir = Ui.principal("Ouvrir", this::ouvrir);
        ouvrir.setDefaultButton(true);
        VBox recherche = Ui.carte("Référendum", Ui.ligne(numero, ouvrir));
        javafx.scene.layout.HBox.setHgrow(numero, javafx.scene.layout.Priority.ALWAYS);
        return Ui.page(
                contexte,
                "Électeur",
                Ui.colonne(recherche, fiche, carteVote, carteRecepisse),
                statut);
    }

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
        montrer(fiche, true);
        boolean ouvert = r.getEtat() == Etat.OUVERT || r.getEtat() == Etat.CLOS;
        montrer(carteVote, ouvert);
        if (!ouvert && r.getEtat().getNumber() < Etat.OUVERT.getNumber()) {
            statut.succes("Le vote n'est pas encore ouvert.");
        }
    }

    private void voter() {
        boolean pourOui = choix.getSelectedToggle() == oui;
        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Vous allez voter « " + (pourOui ? "Oui" : "Non") + " ». Confirmer ?",
                        ButtonType.OK,
                        ButtonType.CANCEL);
        confirmation.setHeaderText(null);
        confirmation.initOwner(contexte.stage());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
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
        traceur.setText(recepisse.getEmpreinte());
        montrer(carteRecepisse, true);
        choix.selectToggle(null);
        statut.succes(
                recepisse.getRevote()
                        ? "Vote enregistré : il remplace votre vote précédent."
                        : "Vote enregistré. Une confirmation vous a été envoyée par e-mail.");
    }

    private void verifier() {
        String empreinte = traceur.getText();
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

    private static void montrer(javafx.scene.Node noeud, boolean visible) {
        noeud.setVisible(visible);
        noeud.setManaged(visible);
    }
}
