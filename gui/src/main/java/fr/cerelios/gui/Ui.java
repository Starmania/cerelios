package fr.cerelios.gui;

import fr.cerelios.protocole.messages.Etat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/** Cadre commun des écrans et petites briques qui ne relèvent pas d'un FXML. */
public final class Ui {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private Ui() {}

    /**
     * Charge le FXML {@code fichier}, rangé à côté de la classe du contrôleur, avec ce contrôleur
     * déjà construit : le {@code fx:controller} du FXML sert à Scene Builder, l'instance vient
     * d'ici.
     */
    public static <T extends Parent> T charger(String fichier, Object controleur) {
        URL url = controleur.getClass().getResource(fichier);
        if (url == null) {
            throw new IllegalStateException("FXML introuvable : " + fichier);
        }
        FXMLLoader chargeur = new FXMLLoader(url);
        chargeur.setControllerFactory(type -> controleur);
        try {
            return chargeur.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Chargement de " + fichier + " impossible", e);
        }
    }

    /**
     * Page type : barre de titre avec retour à l'accueil, contenu centré et défilant, ligne d'état
     * en bas. Le contenu suit la fenêtre dans les deux sens, jusqu'à sa largeur maximale.
     */
    public static BorderPane page(Contexte contexte, String titre, Parent contenu, Statut statut) {
        Button retour = new Button("← Accueil");
        retour.getStyleClass().add("lien");
        retour.setOnAction(
                e -> {
                    contexte.fermer();
                    contexte.accueil();
                });
        Label libelle = new Label(titre);
        libelle.getStyleClass().add("titre-page");
        HBox barre = new HBox(16, retour, libelle);
        barre.setAlignment(Pos.CENTER_LEFT);
        barre.getStyleClass().add("barre");

        contenu.getStyleClass().add("contenu");
        StackPane centre = new StackPane(contenu);
        StackPane.setAlignment(contenu, Pos.TOP_CENTER);
        ScrollPane defilement = new ScrollPane(centre);
        defilement.setFitToWidth(true);
        // Le contenu prend toute la hauteur tant qu'il y tient, pour que tables et listes
        // grandissent avec la fenêtre ; au-delà, la page défile.
        defilement.setFitToHeight(true);
        defilement.getStyleClass().add("defilement");

        HBox bas = new HBox(statut);
        bas.getStyleClass().add("bas");
        HBox.setHgrow(statut, Priority.ALWAYS);

        BorderPane page = new BorderPane(defilement);
        page.setTop(barre);
        page.setBottom(bas);
        return page;
    }

    public static void montrer(Node noeud, boolean visible) {
        noeud.setVisible(visible);
        noeud.setManaged(visible);
    }

    public static void copier(String texte) {
        ClipboardContent contenu = new ClipboardContent();
        contenu.putString(texte);
        Clipboard.getSystemClipboard().setContent(contenu);
    }

    public static boolean confirmer(Contexte contexte, String message) {
        Alert alerte =
                new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alerte.setHeaderText(null);
        alerte.initOwner(contexte.stage());
        return alerte.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public static HBox ligne(Node... noeuds) {
        HBox ligne = new HBox(8, noeuds);
        ligne.setAlignment(Pos.CENTER_LEFT);
        return ligne;
    }

    public static Label texte(String texte) {
        Label label = new Label(texte);
        label.setWrapText(true);
        return label;
    }

    public static Label aide(String texte) {
        Label label = texte(texte);
        label.getStyleClass().add("aide");
        return label;
    }

    /** Pastille colorée selon l'état du référendum. */
    public static Label pastille(Etat etat) {
        Label pastille = new Label(libelle(etat));
        pastille.getStyleClass().addAll("pastille", "pastille-" + etat.name().toLowerCase());
        return pastille;
    }

    public static String libelle(Etat etat) {
        return switch (etat) {
            case BROUILLON -> "Brouillon";
            case VALIDE -> "Validé, pas encore ouvert";
            case OUVERT -> "Vote ouvert";
            case CLOS -> "Clos (délai de grâce)";
            case DEPOUILLEMENT -> "Dépouillement";
            case PUBLIE -> "Résultat publié";
            case ARCHIVE -> "Archivé";
            default -> "Inconnu";
        };
    }

    public static String date(long epochMilli) {
        return FORMAT_DATE.format(Instant.ofEpochMilli(epochMilli));
    }
}
