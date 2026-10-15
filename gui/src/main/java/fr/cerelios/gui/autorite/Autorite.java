package fr.cerelios.gui.autorite;

import fr.cerelios.autorite.AutoriteCertification;
import fr.cerelios.gui.ChampCopiable;
import fr.cerelios.gui.ChampFichier;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/** Autorité de certification manuelle : liste des électeurs, génération, vérification. */
public final class Autorite {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    @FXML private TextField referendum;
    @FXML private TextField jeton;
    @FXML private ListView<String> electeurs;
    @FXML private ChampFichier fichier;
    @FXML private TextField recherche;
    @FXML private ChampCopiable credential;

    public Autorite(Contexte contexte) {
        this.contexte = contexte;
    }

    public Parent vue() {
        return Ui.page(
                contexte, "Autorité de certification", Ui.charger("Autorite.fxml", this), statut);
    }

    private AutoriteCertification autorite() throws IOException {
        return new AutoriteCertification(
                contexte.urne(),
                Contexte.GROUPE,
                referendum.getText().strip(),
                jeton.getText().strip());
    }

    @FXML
    private void charger() {
        contexte.executer(
                statut,
                () -> autorite().electeurs(),
                liste -> {
                    electeurs.getItems().setAll(liste);
                    statut.succes(liste.size() + " électeur(s).");
                });
    }

    @FXML
    private void generer() {
        Path f = fichierOuErreur();
        if (f == null) {
            return;
        }
        if (Files.exists(f)) {
            statut.erreur(
                    "Ce fichier existe déjà : choisissez-en un nouveau pour ne rien écraser.");
            return;
        }
        contexte.executer(
                statut,
                () -> {
                    Map<String, String> secrets = autorite().generer();
                    AutoriteCertification.enregistrer(secrets, f);
                    return secrets.size();
                },
                n -> statut.succes(n + " credentials générés ; parties secrètes dans " + f + "."));
    }

    @FXML
    private void verifier() {
        Path f = fichierOuErreur();
        if (f == null) {
            return;
        }
        contexte.executer(
                statut,
                () -> autorite().verifierPublication(AutoriteCertification.relire(f)),
                anomalies -> {
                    if (anomalies.isEmpty()) {
                        statut.succes("L'urne publie exactement vos credentials.");
                    } else {
                        statut.erreur(String.join(" · ", anomalies));
                    }
                });
    }

    @FXML
    private void retrouver() {
        Path f = fichierOuErreur();
        if (f == null) {
            return;
        }
        // L'urne enregistre les identifiants en minuscules.
        String electeur = recherche.getText().strip().toLowerCase(Locale.ROOT);
        contexte.executer(
                statut,
                () -> AutoriteCertification.relire(f).getOrDefault(electeur, ""),
                c -> {
                    credential.setTexte(c);
                    if (c.isEmpty()) {
                        statut.erreur(
                                "Aucun credential pour « " + electeur + " » dans ce fichier.");
                    }
                });
    }

    private Path fichierOuErreur() {
        try {
            return fichier.chemin();
        } catch (IllegalArgumentException e) {
            statut.erreur(e.getMessage());
            return null;
        }
    }
}
