package fr.cerelios.gui.controller;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Écran de choix du référendum après connexion. */
public class ChoixReferendumController {

    private final String identifiant;
    private final Runnable surDeconnexion;
    private final ListView<String> liste = new ListView<>();
    private final Label message = new Label();

    public ChoixReferendumController(String identifiant, Runnable surDeconnexion) {
        this.identifiant = identifiant;
        this.surDeconnexion = surDeconnexion;
    }

    public Parent getVue() {
        // TODO a remplacer surement avec count
        liste.setItems(FXCollections.observableArrayList(
                "Référendum 1", "Référendum 2", "Référendum 3"));

        Button choisir = new Button("Choisir");
        choisir.setDefaultButton(true);
        choisir.disableProperty().bind(liste.getSelectionModel().selectedItemProperty().isNull());
        choisir.setOnAction(e -> {
            String r = liste.getSelectionModel().getSelectedItem();
            // TODO ouvre ecran de vote
            message.setText("Sélectionné : " + r);
        });

        Button deconnexion = new Button("Déconnexion");
        deconnexion.setOnAction(e -> surDeconnexion.run());

        HBox boutons = new HBox(10, deconnexion, choisir);
        boutons.setAlignment(Pos.CENTER_RIGHT);

        VBox racine = new VBox(10, new Label("Connecté : " + identifiant),
                new Label("Choisissez un référendum :"), liste, boutons, message);
        VBox.setVgrow(liste, Priority.ALWAYS);
        racine.setPadding(new Insets(20));
        return racine;
    }
}
