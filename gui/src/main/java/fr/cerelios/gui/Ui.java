package fr.cerelios.gui;

import fr.cerelios.protocole.messages.Etat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Briques communes des écrans. */
public final class Ui {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private Ui() {}

    /**
     * Page type : barre de titre avec retour à l'accueil, contenu défilant au centre, ligne d'état
     * en bas.
     */
    public static BorderPane page(Contexte contexte, String titre, Node contenu, Statut statut) {
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

        javafx.scene.control.ScrollPane defilement = new javafx.scene.control.ScrollPane(contenu);
        defilement.setFitToWidth(true);
        defilement.getStyleClass().add("defilement");

        HBox bas = new HBox(statut);
        bas.getStyleClass().add("bas");
        HBox.setHgrow(statut, Priority.ALWAYS);

        BorderPane page = new BorderPane(defilement);
        page.setTop(barre);
        page.setBottom(bas);
        return page;
    }

    /** Carte : bloc titré qui regroupe des champs. */
    public static VBox carte(String titre, Node... contenu) {
        Label libelle = new Label(titre);
        libelle.getStyleClass().add("titre-carte");
        VBox carte = new VBox(10);
        carte.getChildren().add(libelle);
        carte.getChildren().addAll(contenu);
        carte.getStyleClass().add("carte");
        return carte;
    }

    /** Colonne de cartes, avec la marge de la page. */
    public static VBox colonne(Node... cartes) {
        VBox colonne = new VBox(16, cartes);
        colonne.setPadding(new Insets(20));
        colonne.setMaxWidth(860);
        return colonne;
    }

    /** Grille libellé / champ. */
    public static GridPane formulaire(Object... libellesEtChamps) {
        GridPane grille = new GridPane();
        grille.setHgap(12);
        grille.setVgap(8);
        for (int i = 0; i < libellesEtChamps.length; i += 2) {
            Label libelle = new Label((String) libellesEtChamps[i]);
            libelle.getStyleClass().add("libelle");
            Node champ = (Node) libellesEtChamps[i + 1];
            grille.add(libelle, 0, i / 2);
            grille.add(champ, 1, i / 2);
            GridPane.setHgrow(champ, Priority.ALWAYS);
            if (champ instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
            }
        }
        return grille;
    }

    public static HBox ligne(Node... noeuds) {
        HBox ligne = new HBox(8, noeuds);
        ligne.setAlignment(Pos.CENTER_LEFT);
        return ligne;
    }

    public static Button bouton(String texte, Runnable action) {
        Button bouton = new Button(texte);
        bouton.setOnAction(e -> action.run());
        return bouton;
    }

    public static Button principal(String texte, Runnable action) {
        Button bouton = bouton(texte, action);
        bouton.getStyleClass().add("principal");
        return bouton;
    }

    /** Champ en lecture seule dont le texte se sélectionne, avec un bouton pour le copier. */
    public static HBox copiable(TextField champ) {
        champ.setEditable(false);
        champ.getStyleClass().add("copiable");
        HBox.setHgrow(champ, Priority.ALWAYS);
        Button copier =
                bouton(
                        "Copier",
                        () -> {
                            ClipboardContent contenu = new ClipboardContent();
                            contenu.putString(champ.getText());
                            Clipboard.getSystemClipboard().setContent(contenu);
                        });
        return ligne(champ, copier);
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
