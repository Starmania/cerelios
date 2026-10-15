package fr.cerelios.gui;

import fr.cerelios.electeur.Electeur;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Connexion auprès du fournisseur d'identité de l'urne, commune aux électeurs et administrateurs.
 */
public final class Connexion {

    private final Contexte contexte;
    private final String role;
    private final TextField identifiant = new TextField();
    private final PasswordField secret = new PasswordField();
    private final Statut statut = new Statut();

    public Connexion(Contexte contexte, String role) {
        this.contexte = contexte;
        this.role = role;
    }

    /**
     * @param suite écran à ouvrir une fois connecté
     */
    public Parent vue(Runnable suite) {
        identifiant.setPromptText("Identifiant");
        secret.setPromptText("Mot de passe");
        Button connecter =
                Ui.principal(
                        "Se connecter",
                        () -> {
                            String id = identifiant.getText().strip();
                            if (id.isEmpty() || secret.getText().isEmpty()) {
                                statut.erreur("Saisissez l'identifiant et le mot de passe.");
                                return;
                            }
                            String motDePasse = secret.getText();
                            contexte.executer(
                                    statut,
                                    () -> {
                                        new Electeur(contexte.urne(), Contexte.GROUPE)
                                                .connecter(id, motDePasse);
                                        return null;
                                    },
                                    ok -> suite.run());
                        });
        connecter.setDefaultButton(true);
        VBox carte =
                Ui.carte(
                        "Connexion",
                        Ui.aide(
                                "Vos identifiants sont vérifiés par le fournisseur d'identité de"
                                        + " l'urne ("
                                        + contexte.adresseUrne()
                                        + ")."),
                        Ui.formulaire("Identifiant", identifiant, "Mot de passe", secret),
                        connecter);
        carte.setMaxWidth(440);
        VBox centre = new VBox(carte);
        centre.setAlignment(Pos.TOP_CENTER);
        centre.setStyle("-fx-padding: 40 20 20 20;");
        return Ui.page(contexte, role, centre, statut);
    }
}
