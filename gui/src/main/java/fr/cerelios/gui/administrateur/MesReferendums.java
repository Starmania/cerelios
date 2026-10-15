package fr.cerelios.gui.administrateur;

import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.messages.ReferendumPublic;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/** Référendums de l'administrateur connecté, et création d'un nouveau. */
public final class MesReferendums {

    private final Contexte contexte;
    private final Administration admin;
    private final Statut statut = new Statut();
    @FXML private TextField question;
    @FXML private Button creer;
    @FXML private TableView<ReferendumPublic> table;
    @FXML private TableColumn<ReferendumPublic, String> colQuestion;
    @FXML private TableColumn<ReferendumPublic, String> colEtat;
    @FXML private TableColumn<ReferendumPublic, String> colBulletins;
    @FXML private TableColumn<ReferendumPublic, String> colNumero;
    @FXML private Button ouvrir;

    public MesReferendums(Contexte contexte) {
        this.contexte = contexte;
        this.admin = new Administration(contexte);
    }

    public Parent vue() {
        return Ui.page(contexte, "Administrateur", Ui.charger("MesReferendums.fxml", this), statut);
    }

    @FXML
    private void initialize() {
        colQuestion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getQuestion()));
        colEtat.setCellValueFactory(
                c -> new SimpleStringProperty(Ui.libelle(c.getValue().getEtat())));
        colBulletins.setCellValueFactory(
                c ->
                        new SimpleStringProperty(
                                c.getValue().getNombreBulletins()
                                        + " / "
                                        + c.getValue().getNombreElecteurs()));
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getId()));
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
        ouvrir.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        creer.disableProperty().bind(question.textProperty().isEmpty());
        charger();
    }

    @FXML
    private void charger() {
        contexte.executer(statut, admin::mesReferendums, liste -> table.getItems().setAll(liste));
    }

    @FXML
    private void creer() {
        String texte = question.getText().strip();
        contexte.executer(statut, () -> admin.creer(texte), this::ouvrir);
    }

    @FXML
    private void ouvrirSelection() {
        ouvrir(table.getSelectionModel().getSelectedItem().getId());
    }

    private void ouvrir(String id) {
        contexte.afficher(new EditionReferendum(contexte, id).vue());
    }
}
