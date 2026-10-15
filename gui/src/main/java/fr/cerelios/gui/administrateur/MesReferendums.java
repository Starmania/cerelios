package fr.cerelios.gui.administrateur;

import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.messages.ReferendumPublic;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Référendums de l'administrateur connecté, et création d'un nouveau. */
public final class MesReferendums {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private final TableView<ReferendumPublic> table = new TableView<>();
    private final TextField question = new TextField();

    public MesReferendums(Contexte contexte) {
        this.contexte = contexte;
    }

    public Parent vue() {
        TableColumn<ReferendumPublic, String> colQuestion = new TableColumn<>("Question");
        colQuestion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getQuestion()));
        colQuestion.setPrefWidth(380);
        TableColumn<ReferendumPublic, String> colEtat = new TableColumn<>("État");
        colEtat.setCellValueFactory(
                c -> new SimpleStringProperty(Ui.libelle(c.getValue().getEtat())));
        colEtat.setPrefWidth(190);
        TableColumn<ReferendumPublic, String> colVotes = new TableColumn<>("Bulletins");
        colVotes.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue().getNombreBulletins()
                                        + " / "
                                        + c.getValue().getNombreElecteurs()));
        TableColumn<ReferendumPublic, String> colId = new TableColumn<>("Numéro");
        colId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getId()));
        colId.setPrefWidth(200);
        table.getColumns().setAll(java.util.List.of(colQuestion, colEtat, colVotes, colId));
        table.setPlaceholder(new Label("Aucun référendum : créez-en un ci-dessus."));
        table.setPrefHeight(360);
        table.setRowFactory(
                t -> {
                    TableRow<ReferendumPublic> ligne = new TableRow<>();
                    ligne.setOnMouseClicked(
                            e -> {
                                if (e.getClickCount() == 2 && !ligne.isEmpty()) {
                                    ouvrir(ligne.getItem().getId());
                                }
                            });
                    return ligne;
                });
        Button ouvrir =
                Ui.bouton(
                        "Ouvrir",
                        () -> ouvrir(table.getSelectionModel().getSelectedItem().getId()));
        ouvrir.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        question.setPromptText("Question posée, par exemple « Approuvez-vous le budget 2027 ? »");
        HBox.setHgrow(question, Priority.ALWAYS);
        Button creer = Ui.principal("Créer", this::creer);
        creer.disableProperty().bind(question.textProperty().isEmpty());

        charger();
        return Ui.page(
                contexte,
                "Administrateur",
                Ui.colonne(
                        Ui.carte("Nouveau référendum", Ui.ligne(question, creer)),
                        Ui.carte(
                                "Mes référendums",
                                table,
                                Ui.ligne(ouvrir, Ui.bouton("Actualiser", this::charger)))),
                statut);
    }

    private void charger() {
        Administration admin = administration();
        contexte.executer(statut, admin::mesReferendums, liste -> table.getItems().setAll(liste));
    }

    private void creer() {
        String texte = question.getText().strip();
        Administration admin = administration();
        contexte.executer(statut, () -> admin.creer(texte), this::ouvrir);
    }

    private void ouvrir(String id) {
        contexte.afficher(new EditionReferendum(contexte, id).vue());
    }

    private Administration administration() {
        return new Administration(contexte);
    }
}
