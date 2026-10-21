package fr.cerelios.gui.controller;

import java.util.List;
import java.util.function.Consumer;

import fr.cerelios.gui.ReferendumAffiche;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** ecran de choix du référendum après connexion. */
public class ChoixReferendumController {

    /** mettre à true pour tester l'affichage quand aucun référendum n'est en cours. */
    private static final boolean TEST_LISTE_VIDE = false;

    private final String identifiant;
    private final Runnable surDeconnexion;
    private final Consumer<ReferendumAffiche> surChoix;
    private final ListView<ReferendumAffiche> liste = new ListView<>();
    private final Label message = new Label();

    public ChoixReferendumController(String identifiant, Runnable surDeconnexion,Consumer<ReferendumAffiche> surChoix) {
        this.identifiant = identifiant;
        this.surDeconnexion = surDeconnexion;
        this.surChoix = surChoix;
    }

    public Parent getVue() {
        liste.setItems(FXCollections.observableArrayList(chargerReferendums()));
        liste.setPlaceholder(new Label("Aucun référendum en cours."));

        // double-clic sur un référendum = le choisir
        liste.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                choisir();
            }
        });

        Button choisir = new Button("Choisir");
        choisir.setDefaultButton(true);
        choisir.disableProperty().bind(liste.getSelectionModel().selectedItemProperty().isNull());
        choisir.setOnAction(e -> choisir());

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

    private List<ReferendumAffiche> chargerReferendums() {
        if (TEST_LISTE_VIDE) {
            return List.of();
        }
        // remplacer par la vraie liste récupérée auprès de l'urne
        return List.of(
                new ReferendumAffiche("Référendum 1", "Êtes-vous favorable à la question 1 ?"),
                new ReferendumAffiche("Référendum 2", "Êtes-vous favorable à la question 2 ?"),
                new ReferendumAffiche("Référendum 3", "Êtes-vous favorable à la question 3 ?"));
    }

    private void choisir() {
        ReferendumAffiche r = liste.getSelectionModel().getSelectedItem();
        if (r != null) {
            surChoix.accept(r);
        }
    }
}
