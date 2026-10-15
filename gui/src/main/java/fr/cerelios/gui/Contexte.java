package fr.cerelios.gui;

import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.RefusException;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * État partagé par les écrans : fenêtre, adresse de l'urne et connexion. Les appels réseau passent
 * par {@link #executer}, sur un fil à part, pour que la fenêtre ne se fige jamais.
 */
public final class Contexte {

    public static final Groupe GROUPE = Groupe.RFC_3526;

    private final Stage stage;
    // Un seul fil : les requêtes d'un même écran partent dans l'ordre où on les lance.
    private final ExecutorService reseau =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "reseau");
                        t.setDaemon(true);
                        return t;
                    });
    private String adresseUrne = "localhost:5000";
    private ClientUrne urne;

    Contexte(Stage stage) {
        this.stage = stage;
    }

    public Stage stage() {
        return stage;
    }

    public String adresseUrne() {
        return adresseUrne;
    }

    /** Change d'urne : la connexion actuelle, et donc la session, est fermée. */
    public void changerUrne(String adresse) {
        if (!adresse.equals(adresseUrne)) {
            fermer();
            adresseUrne = adresse;
        }
    }

    /** Connexion à l'urne, ouverte au premier appel. À n'utiliser que dans {@link #executer}. */
    public ClientUrne urne() throws IOException {
        if (urne == null) {
            urne = ClientUrne.vers(adresseUrne);
        }
        return urne;
    }

    /** Ferme la connexion : la session ouverte avec elle est perdue. */
    public void fermer() {
        if (urne != null) {
            try {
                urne.close();
            } catch (IOException e) {
                // Rien à faire : la connexion est abandonnée de toute façon.
            }
            urne = null;
        }
    }

    public void afficher(Parent vue) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(vue, 980, 680);
            scene.getStylesheets().add(Contexte.class.getResource("cerelios.css").toExternalForm());
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(vue);
        }
    }

    public void accueil() {
        afficher(new Accueil(this).vue());
    }

    /** Travail réseau qui peut être refusé par l'urne ou échouer faute de connexion. */
    public interface Travail<T> extends Callable<T> {}

    /**
     * Lance {@code travail} hors du fil de l'interface, puis appelle {@code succes} avec son
     * résultat, ou affiche l'erreur dans {@code statut}.
     */
    public <T> void executer(Statut statut, Travail<T> travail, Consumer<T> succes) {
        statut.enCours();
        reseau.submit(
                () -> {
                    try {
                        T resultat = travail.call();
                        Platform.runLater(
                                () -> {
                                    statut.effacer();
                                    succes.accept(resultat);
                                });
                    } catch (RefusException e) {
                        Platform.runLater(() -> statut.erreur(e.getMessage()));
                    } catch (IOException e) {
                        fermer();
                        Platform.runLater(
                                () ->
                                        statut.erreur(
                                                "Connexion impossible ("
                                                        + e.getMessage()
                                                        + "). Vérifiez l'adresse et réessayez."));
                    } catch (Exception e) {
                        Platform.runLater(() -> statut.erreur(e.getMessage()));
                    }
                });
    }
}
