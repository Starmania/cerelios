package fr.cerelios.gui.scrutateur;

import fr.cerelios.electeur.Electeur;
import fr.cerelios.gui.ChampFichier;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.FicheReferendum;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.messages.EtatPartage;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.scrutateur.Scrutateur;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Espace du scrutateur : clé (seul ou à seuil), vérifications, déchiffrement. */
public final class EspaceScrutateur {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private final Timeline minuterie =
            new Timeline(new KeyFrame(Duration.seconds(3), e -> avancer()));
    @FXML private TextField referendum;
    @FXML private TextField nom;
    @FXML private TextField jeton;
    @FXML private ChampFichier fichier;
    @FXML private FicheReferendum fiche;
    @FXML private VBox carteSeul;
    @FXML private VBox carteSeuil;
    @FXML private VBox carteDepouillement;
    @FXML private Label etape;
    @FXML private CheckBox automatique;

    public EspaceScrutateur(Contexte contexte) {
        this.contexte = contexte;
        minuterie.setCycleCount(Timeline.INDEFINITE);
    }

    public Parent vue() {
        Parent page =
                Ui.page(contexte, "Scrutateur", Ui.charger("EspaceScrutateur.fxml", this), statut);
        // La minuterie s'arrête quand on quitte l'écran.
        page.sceneProperty()
                .addListener(
                        (o, avant, scene) -> {
                            if (scene == null) {
                                automatique.setSelected(false);
                            }
                        });
        return page;
    }

    @FXML
    private void initialize() {
        automatique
                .selectedProperty()
                .addListener(
                        (o, avant, coche) -> {
                            if (coche) {
                                minuterie.play();
                            } else {
                                minuterie.stop();
                            }
                        });
    }

    private Scrutateur scrutateur() throws IOException {
        return new Scrutateur(
                contexte.urne(),
                Contexte.GROUPE,
                referendum.getText().strip(),
                nom.getText().strip(),
                jeton.getText().strip());
    }

    @FXML
    private void ouvrir() {
        String id = referendum.getText().strip();
        contexte.executer(
                statut,
                () -> new Electeur(contexte.urne(), Contexte.GROUPE).referendum(id),
                this::afficher);
    }

    private void afficher(ReferendumPublic r) {
        fiche.afficher(r);
        Ui.montrer(fiche, true);
        boolean aSeuil = r.getSeuil() > 0;
        Ui.montrer(carteSeul, !aSeuil);
        Ui.montrer(carteSeuil, aSeuil);
        Ui.montrer(carteDepouillement, true);
        if (aSeuil) {
            etape.setText(
                    "Seuil : " + r.getSeuil() + " sur " + r.getScrutateursCount() + " scrutateurs");
        }
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
                () -> scrutateur().genererCle(f),
                cle -> statut.succes("Clé déposée. Partie secrète enregistrée dans " + f + "."));
    }

    @FXML
    private void avancer() {
        Path f = fichierOuErreur();
        if (f == null) {
            automatique.setSelected(false);
            return;
        }
        contexte.executer(
                statut,
                () -> scrutateur().avancerPartage(f),
                e -> {
                    etape.setText("Étape : " + libelle(e));
                    if (e == EtatPartage.Etape.TERMINE) {
                        automatique.setSelected(false);
                        statut.succes("Clé générée : votre part est prête.");
                    }
                });
    }

    @FXML
    private void verifierCle() {
        Path f = fichierOuErreur();
        if (f == null) {
            return;
        }
        contexte.executer(
                statut,
                () -> scrutateur().cleUtilisee(f),
                ok -> {
                    if (ok) {
                        statut.succes("Votre clé entre bien dans la clé du référendum.");
                    } else {
                        statut.erreur(
                                "Votre clé n'est pas celle publiée, ou le référendum n'est pas"
                                        + " encore validé.");
                    }
                });
    }

    @FXML
    private void dechiffrer() {
        Path f = fichierOuErreur();
        if (f == null) {
            return;
        }
        contexte.executer(
                statut,
                () -> {
                    scrutateur().dechiffrer(f);
                    return null;
                },
                ok -> statut.succes("Déchiffrement partiel déposé, avec sa preuve."));
    }

    @FXML
    private void verifierDechiffrement() {
        contexte.executer(
                statut,
                () -> scrutateur().dechiffrementPublie(),
                d -> {
                    if (d.isPresent()) {
                        statut.succes("Votre déchiffrement partiel est publié.");
                    } else {
                        statut.erreur("Aucun déchiffrement publié à votre nom.");
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

    private static String libelle(EtatPartage.Etape e) {
        return switch (e) {
            case CANAUX -> "1/3 – clés de canal";
            case POLYNOMES -> "2/3 – polynômes";
            case CLES_PARTIELLES -> "3/3 – clés des parts";
            case TERMINE -> "terminé";
            default -> "inconnue";
        };
    }
}
