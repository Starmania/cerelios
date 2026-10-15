package fr.cerelios.gui;

import fr.cerelios.electeur.Electeur;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Connexion auprès du fournisseur d'identité de l'urne, commune aux électeurs et administrateurs.
 */
public final class Connexion {

    private final Contexte contexte;
    private final String role;
    private final Statut statut = new Statut();
    private Runnable suite;
    @FXML private Label aide;
    @FXML private TextField identifiant;
    @FXML private PasswordField secret;

    public Connexion(Contexte contexte, String role) {
        this.contexte = contexte;
        this.role = role;
    }

    /**
     * @param suite écran à ouvrir une fois connecté
     */
    public Parent vue(Runnable suite) {
        this.suite = suite;
        return Ui.page(contexte, role, Ui.charger("Connexion.fxml", this), statut);
    }

    @FXML
    private void initialize() {
        aide.setText(
                "Vos identifiants sont vérifiés par le fournisseur d'identité de l'urne ("
                        + contexte.adresseUrne()
                        + ").");
    }

    @FXML
    private void connecter() {
        String id = identifiant.getText().strip();
        String motDePasse = secret.getText();
        if (id.isEmpty() || motDePasse.isEmpty()) {
            statut.erreur("Saisissez l'identifiant et le mot de passe.");
            return;
        }
        contexte.executer(
                statut,
                () -> {
                    new Electeur(contexte.urne(), Contexte.GROUPE).connecter(id, motDePasse);
                    return null;
                },
                ok -> suite.run());
    }
}
