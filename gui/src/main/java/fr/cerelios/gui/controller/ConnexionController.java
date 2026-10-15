package fr.cerelios.gui.controller;

import java.util.function.Consumer;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** Écran de connexion : identifiant + mot de passe + bouton. */
public class ConnexionController {

    private final TextField identifiant = new TextField();
    private final PasswordField motDePasse = new PasswordField();
    private final Label message = new Label();
    private final Consumer<String> surConnexionReussie;

    public ConnexionController(Consumer<String> surConnexionReussie) {
        this.surConnexionReussie = surConnexionReussie;
    }

    public Parent getVue() {
        identifiant.setPromptText("Identifiant");
        motDePasse.setPromptText("Mot de passe");

        Button connecter = new Button("Se connecter");
        connecter.setDefaultButton(true);
        connecter.setOnAction(e -> valider());

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        grille.setPadding(new Insets(20));
        grille.setAlignment(Pos.CENTER);

        grille.add(new Label("Identifiant :"), 0, 0);
        grille.add(identifiant, 1, 0);
        grille.add(new Label("Mot de passe :"), 0, 1);
        grille.add(motDePasse, 1, 1);
        grille.add(connecter, 1, 2);
        grille.add(message, 0, 3, 2, 1);
        GridPane.setHalignment(connecter, HPos.RIGHT);
        return grille;
    }

    private void valider() {
        String id = identifiant.getText().trim();
        if (id.isEmpty() || motDePasse.getText().isEmpty()) {
            message.setText("Merci de remplir les deux champs.");
            return;
        }
        // TODO : vérifier les identifiants auprès du serveur (module protocole)
        surConnexionReussie.accept(id);
    }
}
