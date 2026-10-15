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
    private void initialize() {
        fichier.setGeneration(nomParDefaut(), this::genererFichier);
        referendum
                .textProperty()
                .addListener((o, avant, apres) -> fichier.setNomParDefaut(nomParDefaut()));
    }

    private String nomParDefaut() {
        return ChampFichier.nom("cerelios", "credentials", referendum.getText().strip()) + ".txt";
    }

    /** Tire un credential par électeur de la liste et les écrit à l'emplacement choisi. */
    private void genererFichier(Path f) {
        if (Files.exists(f)) {
            statut.erreur(
                    "« "
                            + f.getFileName()
                            + " » existe déjà : choisissez un autre nom pour ne rien écraser.");
            return;
        }
        contexte.executer(
                statut,
                () -> {
                    Map<String, String> secrets = autorite().genererSecrets();
                    AutoriteCertification.enregistrer(secrets, f);
                    return secrets.size();
                },
                n -> {
                    fichier.setChemin(f);
                    statut.succes(
                            n
                                    + " credentials écrits dans "
                                    + f
                                    + ". Relisez-le si besoin, puis publiez.");
                });
    }

    @FXML
    private void publier() {
        Path f = fichierOuErreur();
        if (f == null) {
            return;
        }
        contexte.executer(
                statut,
                () -> {
                    Map<String, String> secrets = AutoriteCertification.relire(f);
                    autorite().deposer(secrets);
                    return secrets.size();
                },
                n ->
                        statut.succes(
                                n
                                        + " credentials publiés : l'urne n'a reçu que leur partie"
                                        + " publique."));
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
