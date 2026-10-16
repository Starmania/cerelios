package fr.cerelios.gui;

import fr.cerelios.gui.controller.ChoixReferendumController;
import fr.cerelios.gui.controller.ConnexionController;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Cerelios - Référendum");
        afficherConnexion();
        stage.show();
    }

    private void afficherConnexion() {
        ConnexionController connexion = new ConnexionController(this::afficherChoix);
        changerVue(connexion.getVue());
    }

    private void afficherChoix(String identifiant) {
        ChoixReferendumController choix =
                new ChoixReferendumController(identifiant, this::afficherConnexion);
        changerVue(choix.getVue());
    }

    private void changerVue(Parent vue) {
        if(stage.getScene() == null) {
            stage.setScene(new Scene(vue, 450, 350));
        } else {
            stage.getScene().setRoot(vue);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
