package fr.cerelios.gui;

import fr.cerelios.gui.administrateur.MesReferendums;
import fr.cerelios.gui.auditeur.Audit;
import fr.cerelios.gui.autorite.Autorite;
import fr.cerelios.gui.autorite.Operateur;
import fr.cerelios.gui.electeur.Vote;
import fr.cerelios.gui.scrutateur.EspaceScrutateur;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Écran d'accueil : adresse de l'urne et choix du rôle. */
final class Accueil {

    private final Contexte contexte;
    private final TextField adresse = new TextField();

    Accueil(Contexte contexte) {
        this.contexte = contexte;
    }

    Parent vue() {
        Label titre = new Label("Cerelios");
        titre.getStyleClass().add("titre-accueil");
        Label sousTitre = new Label("Référendum Oui / Non vérifiable");
        sousTitre.getStyleClass().add("sous-titre");

        adresse.setText(contexte.adresseUrne());
        adresse.setPromptText("hôte:port");
        adresse.setPrefColumnCount(18);

        FlowPane roles = new FlowPane(14, 14);
        roles.setAlignment(Pos.CENTER);
        roles.setPrefWrapLength(720);
        roles.getChildren()
                .addAll(
                        role(
                                "Électeur",
                                "Voter avec votre credential et vérifier votre bulletin",
                                () ->
                                        new Connexion(contexte, "Électeur")
                                                .vue(
                                                        () ->
                                                                contexte.afficher(
                                                                        new Vote(contexte).vue()))),
                        role(
                                "Administrateur",
                                "Préparer, valider et suivre un référendum",
                                () ->
                                        new Connexion(contexte, "Administrateur")
                                                .vue(
                                                        () ->
                                                                contexte.afficher(
                                                                        new MesReferendums(contexte)
                                                                                .vue()))),
                        role(
                                "Scrutateur",
                                "Générer votre clé puis déchiffrer le résultat",
                                () -> new EspaceScrutateur(contexte).vue()),
                        role(
                                "Autorité de certification",
                                "Générer les credentials des électeurs",
                                () -> new Autorite(contexte).vue()),
                        role(
                                "Opérateur des credentials",
                                "Vérifier le certificat et renvoyer des credentials",
                                () -> new Operateur(contexte).vue()),
                        role(
                                "Auditeur",
                                "Revérifier tout un référendum, sans aucun secret",
                                () -> new Audit(contexte).vue()));

        VBox page = new VBox(18, titre, sousTitre, Ui.ligne(new Label("Urne :"), adresse), roles);
        page.setAlignment(Pos.TOP_CENTER);
        page.setPadding(new Insets(48, 24, 24, 24));
        page.getStyleClass().add("accueil");
        ((javafx.scene.layout.HBox) page.getChildren().get(2)).setAlignment(Pos.CENTER);
        return page;
    }

    /** Tuile de rôle : ouvre son écran avec l'adresse d'urne saisie. */
    private Button role(String nom, String description, java.util.function.Supplier<Parent> ecran) {
        VBox contenu = new VBox(6, new Label(nom), Ui.aide(description));
        contenu.getChildren().getFirst().getStyleClass().add("titre-role");
        Button tuile = new Button();
        tuile.setGraphic(contenu);
        tuile.getStyleClass().add("tuile");
        tuile.setOnAction(
                e -> {
                    contexte.changerUrne(adresse.getText().strip());
                    contexte.afficher(ecran.get());
                });
        return tuile;
    }
}
