package fr.cerelios.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

/**
 * Fichier à fournir : chemin saisi, ou choisi parmi les fichiers existants. Si l'écran sait le
 * générer, un bouton « Générer… » propose d'enregistrer un nouveau fichier où l'on veut, sous un
 * nom par défaut parlant, puis remplit le champ ; le fichier reste modifiable hors de
 * l'application.
 *
 * <p>Dans un FXML : {@code <ChampFichier invite="…" libelleGenerer="Générer un fichier…"/>} ; le
 * contrôleur branche la génération avec {@link #setGeneration}.
 */
public final class ChampFichier extends HBox {

    private final TextField chemin = new TextField();
    private final Button generer = new Button();
    private String nomParDefaut = "fichier.txt";
    private Consumer<Path> generation;

    public ChampFichier() {
        super(8);
        HBox.setHgrow(chemin, Priority.ALWAYS);
        Button choisir = new Button("Choisir…");
        choisir.setOnAction(e -> choisir());
        generer.setOnAction(e -> generer());
        Ui.montrer(generer, false);
        getChildren().addAll(chemin, choisir, generer);
    }

    public String getInvite() {
        return chemin.getPromptText();
    }

    public void setInvite(String invite) {
        chemin.setPromptText(invite);
    }

    public String getLibelleGenerer() {
        return generer.getText();
    }

    /** Texte du bouton de génération ; le bouton n'apparaît qu'une fois la génération branchée. */
    public void setLibelleGenerer(String libelle) {
        generer.setText(libelle);
    }

    /**
     * Branche la génération : après le choix de l'emplacement, {@code generation} reçoit le chemin
     * (qui n'existe pas encore) et doit y écrire le fichier.
     *
     * @param nomParDefaut nom proposé, qui dit à quoi sert le fichier pour le retrouver ensuite
     */
    public void setGeneration(String nomParDefaut, Consumer<Path> generation) {
        this.nomParDefaut = nomParDefaut;
        this.generation = generation;
        Ui.montrer(generer, true);
    }

    /** Change le nom proposé, par exemple quand le numéro du référendum est saisi. */
    public void setNomParDefaut(String nomParDefaut) {
        this.nomParDefaut = nomParDefaut;
    }

    public void setChemin(Path p) {
        chemin.setText(p.toString());
    }

    private void choisir() {
        FileChooser dialogue = new FileChooser();
        dialogue.setTitle(getInvite());
        File f = dialogue.showOpenDialog(getScene().getWindow());
        if (f != null) {
            chemin.setText(f.getAbsolutePath());
        }
    }

    private void generer() {
        FileChooser dialogue = new FileChooser();
        dialogue.setTitle(generer.getText());
        dialogue.setInitialFileName(nomParDefaut);
        File f = dialogue.showSaveDialog(getScene().getWindow());
        if (f != null) {
            generation.accept(f.toPath());
        }
    }

    /** Chemin saisi, vide si le champ l'est. */
    public Optional<Path> cheminSaisi() {
        String texte = chemin.getText().strip();
        return texte.isEmpty() ? Optional.empty() : Optional.of(Path.of(texte));
    }

    /**
     * @throws IllegalArgumentException si aucun fichier n'est indiqué ou s'il n'existe pas
     */
    public Path chemin() {
        Path p =
                cheminSaisi()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Indiquez un fichier : "
                                                        + getInvite().toLowerCase()));
        if (!Files.isRegularFile(p)) {
            throw new IllegalArgumentException("Fichier introuvable : " + p);
        }
        return p;
    }

    /** Nom de fichier sans caractère gênant : lettres, chiffres, point, tiret et soulignement. */
    public static String nom(String... parties) {
        return String.join("-", parties).replaceAll("[^\\p{L}\\p{N}._-]", "_");
    }
}
