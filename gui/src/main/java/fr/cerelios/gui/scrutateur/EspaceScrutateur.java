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
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Espace du scrutateur : clé (seul ou à seuil), vérifications, déchiffrement. */
public final class EspaceScrutateur {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private final TextField referendum = new TextField();
    private final TextField nom = new TextField();
    private final TextField jeton = new TextField();
    private final ChampFichier fichier;
    private final FicheReferendum fiche = new FicheReferendum();
    private final Label etape = new Label();
    private final CheckBox automatique = new CheckBox("Avancer automatiquement toutes les 3 s");
    private final Timeline minuterie;
    private final VBox carteSeul;
    private final VBox carteSeuil;
    private final VBox carteDepouillement;

    public EspaceScrutateur(Contexte contexte) {
        this.contexte = contexte;
        fichier = new ChampFichier("Fichier de votre clé secrète", true, contexte.stage());
        minuterie = new Timeline(new KeyFrame(Duration.seconds(3), e -> avancer()));
        minuterie.setCycleCount(Timeline.INDEFINITE);
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

        carteSeul =
                Ui.carte(
                        "Votre clé",
                        Ui.aide(
                                "Chaque scrutateur génère sa propre clé. La partie secrète est"
                                        + " enregistrée dans le fichier choisi, sur ce poste"
                                        + " seulement : sans elle, le résultat ne pourra pas être"
                                        + " déchiffré. Gardez-en une copie sûre."),
                        Ui.principal("Générer ma clé", this::generer));
        etape.getStyleClass().add("etape");
        carteSeuil =
                Ui.carte(
                        "Génération de la clé à plusieurs",
                        Ui.aide(
                                "Les scrutateurs génèrent la clé ensemble, en trois étapes : clés"
                                    + " de canal, polynômes, clés des parts. Chaque étape attend"
                                    + " que tous aient fini la précédente. Le fichier ne garde que"
                                    + " votre clé de canal : votre part se recalcule à partir de ce"
                                    + " que l'urne conserve."),
                        etape,
                        Ui.ligne(Ui.principal("Avancer", this::avancer), automatique));
        carteDepouillement =
                Ui.carte(
                        "Vérifications et dépouillement",
                        Ui.ligne(
                                Ui.bouton("Ma clé est-elle utilisée ?", this::verifierCle),
                                Ui.principal("Déchiffrer le total", this::dechiffrer),
                                Ui.bouton(
                                        "Mon déchiffrement est-il publié ?",
                                        this::verifierDechiffrement)),
                        Ui.aide(
                                "Le total ne se déchiffre qu'après la clôture et le délai de grâce."
                                        + " Seul le total est déchiffré, jamais un bulletin."));
        for (VBox carte : new VBox[] {carteSeul, carteSeuil, carteDepouillement}) {
            montrer(carte, false);
        }
        montrer(fiche, false);
    }

    public Parent vue() {
        referendum.setPromptText("Numéro du référendum");
        nom.setPromptText("Votre nom de scrutateur");
        jeton.setPromptText("Jeton reçu par e-mail");
        Button ouvrir = Ui.principal("Ouvrir", this::ouvrir);
        ouvrir.setDefaultButton(true);
        VBox invitation =
                Ui.carte(
                        "Votre invitation",
                        Ui.aide("Recopiez les champs de l'e-mail d'invitation."),
                        Ui.formulaire(
                                "Référendum",
                                referendum,
                                "Nom",
                                nom,
                                "Jeton",
                                jeton,
                                "Fichier secret",
                                fichier),
                        ouvrir);
        return Ui.page(
                contexte,
                "Scrutateur",
                Ui.colonne(invitation, fiche, carteSeul, carteSeuil, carteDepouillement),
                statut);
    }

    private Scrutateur scrutateur() throws java.io.IOException {
        return new Scrutateur(
                contexte.urne(),
                Contexte.GROUPE,
                referendum.getText().strip(),
                nom.getText().strip(),
                jeton.getText().strip());
    }

    private void ouvrir() {
        String id = referendum.getText().strip();
        contexte.executer(
                statut,
                () -> new Electeur(contexte.urne(), Contexte.GROUPE).referendum(id),
                this::afficher);
    }

    private void afficher(ReferendumPublic r) {
        fiche.afficher(r);
        montrer(fiche, true);
        boolean aSeuil = r.getSeuil() > 0;
        montrer(carteSeul, !aSeuil);
        montrer(carteSeuil, aSeuil);
        montrer(carteDepouillement, true);
        if (aSeuil) {
            etape.setText(
                    "Seuil : " + r.getSeuil() + " sur " + r.getScrutateursCount() + " scrutateurs");
        }
    }

    private void generer() {
        Path f;
        try {
            f = fichier.chemin();
        } catch (IllegalArgumentException e) {
            statut.erreur(e.getMessage());
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

    private void avancer() {
        Path f;
        try {
            f = fichier.chemin();
        } catch (IllegalArgumentException e) {
            automatique.setSelected(false);
            statut.erreur(e.getMessage());
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

    private static void montrer(javafx.scene.Node noeud, boolean visible) {
        noeud.setVisible(visible);
        noeud.setManaged(visible);
    }
}
