package fr.cerelios.gui;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Texte en lecture seule, sélectionnable, avec un bouton pour le copier. */
public final class ChampCopiable extends HBox {

    private final TextField champ = new TextField();

    public ChampCopiable() {
        super(8);
        champ.setEditable(false);
        champ.getStyleClass().add("copiable");
        HBox.setHgrow(champ, Priority.ALWAYS);
        Button copier = new Button("Copier");
        copier.setOnAction(e -> Ui.copier(champ.getText()));
        getChildren().addAll(champ, copier);
    }

    public String getTexte() {
        return champ.getText();
    }

    public void setTexte(String texte) {
        champ.setText(texte);
    }

    public String getInvite() {
        return champ.getPromptText();
    }

    public void setInvite(String invite) {
        champ.setPromptText(invite);
    }
}
