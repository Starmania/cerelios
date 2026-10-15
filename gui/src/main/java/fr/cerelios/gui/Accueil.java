package fr.cerelios.gui;

import fr.cerelios.gui.administrateur.MesReferendums;
import fr.cerelios.gui.auditeur.Audit;
import fr.cerelios.gui.autorite.Autorite;
import fr.cerelios.gui.autorite.Operateur;
import fr.cerelios.gui.electeur.Vote;
import fr.cerelios.gui.scrutateur.EspaceScrutateur;
import java.util.function.Supplier;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Écran d'accueil : adresse de l'urne et choix du rôle. */
public final class Accueil {

    private final Contexte contexte;
    @FXML private VBox racine;
    @FXML private TextField adresse;
    @FXML private FlowPane roles;

    Accueil(Contexte contexte) {
        this.contexte = contexte;
    }

    Parent vue() {
        return Ui.charger("Accueil.fxml", this);
    }

    @FXML
    private void initialize() {
        adresse.setText(contexte.adresseUrne());
        // Sans cela, le FlowPane garde sa largeur de retour à la ligne par défaut.
        roles.prefWrapLengthProperty().bind(racine.widthProperty().subtract(48));
    }

    @FXML
    private void electeur() {
        ouvrir(
                () ->
                        new Connexion(contexte, "Électeur")
                                .vue(() -> contexte.afficher(new Vote(contexte).vue())));
    }

    @FXML
    private void administrateur() {
        ouvrir(
                () ->
                        new Connexion(contexte, "Administrateur")
                                .vue(() -> contexte.afficher(new MesReferendums(contexte).vue())));
    }

    @FXML
    private void scrutateur() {
        ouvrir(() -> new EspaceScrutateur(contexte).vue());
    }

    @FXML
    private void autorite() {
        ouvrir(() -> new Autorite(contexte).vue());
    }

    @FXML
    private void operateur() {
        ouvrir(() -> new Operateur(contexte).vue());
    }

    @FXML
    private void auditeur() {
        ouvrir(() -> new Audit(contexte).vue());
    }

    private void ouvrir(Supplier<Parent> ecran) {
        contexte.changerUrne(adresse.getText().strip());
        contexte.afficher(ecran.get());
    }
}
