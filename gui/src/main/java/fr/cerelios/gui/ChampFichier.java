package fr.cerelios.gui;

import java.io.File;
import java.nio.file.Path;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Chemin de fichier saisi ou choisi dans une boîte de dialogue. */
public final class ChampFichier extends HBox {

    private final TextField chemin = new TextField();

    /**
     * @param enregistrer vrai pour un fichier à créer, faux pour un fichier à ouvrir
     */
    public ChampFichier(String invite, boolean enregistrer, Window fenetre) {
        super(8);
        chemin.setPromptText(invite);
        HBox.setHgrow(chemin, Priority.ALWAYS);
        Button choisir =
                Ui.bouton(
                        "Choisir…",
                        () -> {
                            FileChooser dialogue = new FileChooser();
                            dialogue.setTitle(invite);
                            File f =
                                    enregistrer
                                            ? dialogue.showSaveDialog(fenetre)
                                            : dialogue.showOpenDialog(fenetre);
                            if (f != null) {
                                chemin.setText(f.getAbsolutePath());
                            }
                        });
        getChildren().addAll(chemin, choisir);
    }

    /**
     * @throws IllegalArgumentException si aucun fichier n'est indiqué
     */
    public Path chemin() {
        String texte = chemin.getText().strip();
        if (texte.isEmpty()) {
            throw new IllegalArgumentException("Indiquez un fichier");
        }
        return Path.of(texte);
    }
}
