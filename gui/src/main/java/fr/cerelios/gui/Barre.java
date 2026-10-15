package fr.cerelios.gui;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Barre proportionnelle Oui / Non, avec les pourcentages. */
final class Barre {

    private Barre() {}

    static HBox oui(int oui, int non) {
        double total = oui + non;
        Label partOui = new Label(String.format("Oui %.0f %%", 100 * oui / total));
        Label partNon = new Label(String.format("Non %.0f %%", 100 * non / total));
        partOui.getStyleClass().add("barre-oui");
        partNon.getStyleClass().add("barre-non");
        for (Label l : new Label[] {partOui, partNon}) {
            l.setMaxWidth(Double.MAX_VALUE);
            l.setMinWidth(0);
        }
        HBox.setHgrow(partOui, Priority.ALWAYS);
        HBox.setHgrow(partNon, Priority.ALWAYS);
        HBox barre = new HBox(partOui, partNon);
        // Largeurs proportionnelles aux voix.
        partOui.prefWidthProperty().bind(barre.widthProperty().multiply(oui / total));
        partNon.prefWidthProperty().bind(barre.widthProperty().multiply(non / total));
        partOui.setVisible(oui > 0);
        partOui.setManaged(oui > 0);
        partNon.setVisible(non > 0);
        partNon.setManaged(non > 0);
        barre.getStyleClass().add("barre-resultat");
        return barre;
    }
}
