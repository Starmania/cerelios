package fr.cerelios.gui;

import javafx.application.Application;
import javafx.stage.Stage;

/** Application unique de tous les rôles ; le rôle se choisit sur l'écran d'accueil. */
public class MainApp extends Application {

    private Contexte contexte;

    @Override
    public void start(Stage stage) {
        contexte = new Contexte(stage);
        stage.setTitle("Cerelios");
        contexte.accueil();
        stage.show();
    }

    @Override
    public void stop() {
        contexte.fermer();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
