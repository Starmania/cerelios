package fr.cerelios.gui.controller;

import java.util.function.IntConsumer;

import fr.cerelios.gui.ReferendumAffiche;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** ecran de vote : la question, deux réponses et un bouton Voter. */
public class VoteController {

    private final ReferendumAffiche referendum;
    private final Runnable surRetour;
    private final IntConsumer surVote;

    public VoteController(ReferendumAffiche referendum, Runnable surRetour, IntConsumer surVote) {
        this.referendum = referendum;
        this.surRetour = surRetour;
        this.surVote = surVote;
    }

    public Parent getVue() {
        Label question = new Label(referendum.question());
        question.setWrapText(true);

        ToggleGroup groupe = new ToggleGroup();
        RadioButton oui = new RadioButton("Oui");
        oui.setUserData(1);
        oui.setToggleGroup(groupe);
        RadioButton non = new RadioButton("Non");
        non.setUserData(0);
        non.setToggleGroup(groupe);

        Button voter = new Button("Voter");
        voter.setDefaultButton(true);
        voter.disableProperty().bind(groupe.selectedToggleProperty().isNull());
        voter.setOnAction(e -> {
            int choix = (Integer) groupe.getSelectedToggle().getUserData();
            //chiffrer le bulletin et l'envoyer à l'urne
            surVote.accept(choix); // retour à l'accueil
        });

        Button retour = new Button("Retour");
        retour.setOnAction(e -> surRetour.run());

        HBox boutons = new HBox(10, retour, voter);
        boutons.setAlignment(Pos.CENTER_RIGHT);

        VBox racine = new VBox(10, new Label(referendum.titre()), question, oui, non, boutons);
        racine.setPadding(new Insets(20));
        return racine;
    }
}
