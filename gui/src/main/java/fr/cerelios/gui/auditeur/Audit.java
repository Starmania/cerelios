package fr.cerelios.gui.auditeur;

import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.FicheReferendum;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.Verificateur;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Audit d'un référendum : n'importe qui refait tous les contrôles, sans aucun secret. */
public final class Audit {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private final TextField referendum = new TextField();
    private final FicheReferendum fiche = new FicheReferendum();
    private final VBox resume = new VBox(6);
    private final ListView<String> anomalies = new ListView<>();
    private final VBox carteResultat;

    public Audit(Contexte contexte) {
        this.contexte = contexte;
        anomalies.setPrefHeight(200);
        anomalies.setPlaceholder(new Label("Aucune anomalie."));
        carteResultat = Ui.carte("Contrôles", resume, anomalies);
        carteResultat.setVisible(false);
        carteResultat.setManaged(false);
        fiche.setVisible(false);
        fiche.setManaged(false);
    }

    public Parent vue() {
        referendum.setPromptText("Numéro du référendum");
        HBox.setHgrow(referendum, Priority.ALWAYS);
        Button verifier = Ui.principal("Vérifier", this::verifier);
        verifier.setDefaultButton(true);
        return Ui.page(
                contexte,
                "Auditeur",
                Ui.colonne(
                        Ui.carte(
                                "Référendum",
                                Ui.ligne(referendum, verifier),
                                Ui.aide(
                                        "Télécharge les données publiques et vérifie : preuves des"
                                            + " clés, certificat, chaque bulletin et sa signature,"
                                            + " le total chiffré, les déchiffrements partiels et le"
                                            + " résultat.")),
                        fiche,
                        carteResultat),
                statut);
    }

    private void verifier() {
        String id = referendum.getText().strip();
        contexte.executer(
                statut,
                () -> {
                    DonneesPubliques donnees =
                            contexte.urne()
                                    .appeler(
                                            Requete.newBuilder()
                                                    .setObtenirDonneesPubliques(
                                                            Referendum.newBuilder()
                                                                    .setReferendum(id)))
                                    .getDonneesPubliques();
                    return new Audite(donnees, new Verificateur(Contexte.GROUPE).verifier(donnees));
                },
                this::afficher);
    }

    private record Audite(DonneesPubliques donnees, List<String> anomalies) {}

    private void afficher(Audite audit) {
        ReferendumPublic r = audit.donnees().getReferendum();
        fiche.afficher(r);
        fiche.setVisible(true);
        fiche.setManaged(true);
        resume.getChildren()
                .setAll(
                        Ui.texte(audit.donnees().getCredentialsCount() + " credential(s) publiés"),
                        Ui.texte(audit.donnees().getBulletinsCount() + " bulletin(s) vérifiés"),
                        Ui.texte(
                                r.getSeuil() > 0
                                        ? "Clé à seuil : "
                                                + r.getSeuil()
                                                + " scrutateurs sur "
                                                + r.getScrutateursCount()
                                        : r.getScrutateursCount()
                                                + " scrutateur(s), tous nécessaires"),
                        Ui.texte(
                                audit.donnees().hasCertificat()
                                        ? "Credentials certifiés par un serveur de credentials"
                                        : "Credentials d'une autorité de certification manuelle"),
                        Ui.texte(
                                audit.donnees().getScellementsCount() > 0
                                        ? "Calendrier scellé"
                                        : "Calendrier non scellé"));
        anomalies.getItems().setAll(audit.anomalies());
        carteResultat.setVisible(true);
        carteResultat.setManaged(true);
        if (audit.anomalies().isEmpty()) {
            statut.succes("Tous les contrôles passent.");
        } else {
            statut.erreur(audit.anomalies().size() + " anomalie(s) : voir la liste.");
        }
    }
}
