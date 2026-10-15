package fr.cerelios.gui.autorite;

import fr.cerelios.autorite.AutoriteCertification;
import fr.cerelios.gui.ChampFichier;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/** Autorité de certification manuelle : liste des électeurs, génération, vérification. */
public final class Autorite {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private final TextField referendum = new TextField();
    private final TextField jeton = new TextField();
    private final ChampFichier fichier;
    private final ListView<String> electeurs = new ListView<>();
    private final TextField recherche = new TextField();
    private final TextField credential = new TextField();

    public Autorite(Contexte contexte) {
        this.contexte = contexte;
        fichier = new ChampFichier("Fichier des credentials secrets", true, contexte.stage());
    }

    public Parent vue() {
        referendum.setPromptText("Numéro du référendum");
        jeton.setPromptText("Jeton remis par l'administrateur");
        electeurs.setPrefHeight(200);
        electeurs.setPlaceholder(new Label("Chargez la liste pour la relire avant de générer."));
        recherche.setPromptText("Identifiant de l'électeur");
        credential.setPromptText("Credential retrouvé");
        return Ui.page(
                contexte,
                "Autorité de certification",
                Ui.colonne(
                        Ui.carte(
                                "Référendum",
                                Ui.formulaire("Référendum", referendum, "Jeton", jeton),
                                Ui.bouton("Charger la liste des électeurs", this::charger),
                                electeurs),
                        Ui.carte(
                                "Credentials",
                                Ui.formulaire("Fichier", fichier),
                                Ui.aide(
                                        "Les credentials sont générés sur ce poste. Seule leur"
                                            + " partie publique part à l'urne ; les parties"
                                            + " secrètes sont enregistrées dans le fichier, à"
                                            + " remettre à chaque électeur par vos propres"
                                            + " moyens."),
                                Ui.ligne(
                                        Ui.principal("Générer et enregistrer", this::generer),
                                        Ui.bouton("Vérifier la publication", this::verifier))),
                        Ui.carte(
                                "Credential perdu",
                                Ui.aide("Retrouve le credential d'un électeur dans le fichier."),
                                Ui.ligne(recherche, Ui.bouton("Retrouver", this::retrouver)),
                                Ui.copiable(credential))),
                statut);
    }

    private AutoriteCertification autorite() throws java.io.IOException {
        return new AutoriteCertification(
                contexte.urne(),
                Contexte.GROUPE,
                referendum.getText().strip(),
                jeton.getText().strip());
    }

    private void charger() {
        contexte.executer(
                statut,
                () -> autorite().electeurs(),
                liste -> {
                    electeurs.getItems().setAll(liste);
                    statut.succes(liste.size() + " électeur(s).");
                });
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
                () -> {
                    Map<String, String> secrets = autorite().generer();
                    AutoriteCertification.enregistrer(secrets, f);
                    return secrets.size();
                },
                n -> statut.succes(n + " credentials générés ; parties secrètes dans " + f + "."));
    }

    private void verifier() {
        Path f;
        try {
            f = fichier.chemin();
        } catch (IllegalArgumentException e) {
            statut.erreur(e.getMessage());
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

    private void retrouver() {
        Path f;
        try {
            f = fichier.chemin();
        } catch (IllegalArgumentException e) {
            statut.erreur(e.getMessage());
            return;
        }
        String electeur = recherche.getText().strip().toLowerCase();
        contexte.executer(
                statut,
                () -> AutoriteCertification.relire(f).get(electeur),
                c -> {
                    if (c == null) {
                        credential.clear();
                        statut.erreur(
                                "Aucun credential pour « " + electeur + " » dans ce fichier.");
                    } else {
                        credential.setText(c);
                    }
                });
    }
}
