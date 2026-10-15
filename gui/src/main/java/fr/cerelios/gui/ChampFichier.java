package fr.cerelios.gui;

import java.io.File;
import java.nio.file.Path;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

/**
 * Chemin de fichier saisi ou choisi dans une boîte de dialogue. Dans un FXML : {@code <ChampFichier
 * invite="…" enregistrer="true"/>}.
 */
public final class ChampFichier extends HBox {

    private final TextField chemin = new TextField();
    private boolean enregistrer;

    public ChampFichier() {
        super(8);
        HBox.setHgrow(chemin, Priority.ALWAYS);
        Button choisir = new Button("Choisir…");
        choisir.setOnAction(e -> choisir());
        getChildren().addAll(chemin, choisir);
    }

    public String getInvite() {
        return chemin.getPromptText();
    }

    public void setInvite(String invite) {
        chemin.setPromptText(invite);
    }

    public boolean isEnregistrer() {
        return enregistrer;
    }

    /** Vrai pour un fichier à créer, faux (par défaut) pour un fichier à ouvrir. */
    public void setEnregistrer(boolean enregistrer) {
        this.enregistrer = enregistrer;
    }

    private void choisir() {
        FileChooser dialogue = new FileChooser();
        dialogue.setTitle(getInvite());
        File f =
                enregistrer
                        ? dialogue.showSaveDialog(getScene().getWindow())
                        : dialogue.showOpenDialog(getScene().getWindow());
        if (f != null) {
            chemin.setText(f.getAbsolutePath());
        }
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
