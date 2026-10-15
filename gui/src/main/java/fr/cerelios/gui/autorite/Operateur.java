package fr.cerelios.gui.autorite;

import fr.cerelios.autorite.OperateurCredentials;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.ClientCertification;
import java.util.Arrays;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Opérateur du serveur de credentials : certificat, renvois, crédits. */
public final class Operateur {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    private final TextField serveur = new TextField("localhost:5001");
    private final TextField referendum = new TextField();
    private final PasswordField graine = new PasswordField();
    private final TextField destinataires = new TextField();
    private final Label credits = new Label("—");

    public Operateur(Contexte contexte) {
        this.contexte = contexte;
    }

    public Parent vue() {
        referendum.setPromptText("Numéro du référendum");
        graine.setPromptText("Graine reçue par e-mail");
        destinataires.setPromptText("identifiants séparés par des virgules");
        return Ui.page(
                contexte,
                "Opérateur des credentials",
                Ui.colonne(
                        Ui.carte(
                                "Accès",
                                Ui.formulaire(
                                        "Serveur de credentials",
                                        serveur,
                                        "Référendum",
                                        referendum,
                                        "Graine",
                                        graine),
                                Ui.aide(
                                        "Sans la graine, le serveur ne peut plus relire les"
                                            + " credentials qu'il garde chiffrés : elle n'existe"
                                            + " que dans votre e-mail.")),
                        Ui.carte(
                                "Certificat",
                                Ui.aide(
                                        "Vérifie que le certificat publié par l'urne est signé avec"
                                            + " les clés de votre graine et porte sur les"
                                            + " credentials publiés."),
                                Ui.principal("Vérifier le certificat", this::verifier)),
                        Ui.carte(
                                "Renvoyer des credentials",
                                Ui.ligne(
                                        new Label("Crédits restants :"),
                                        credits,
                                        Ui.bouton("Actualiser", this::credits)),
                                Ui.ligne(
                                        destinataires,
                                        Ui.bouton("Renvoyer à ces électeurs", this::renvoyerA)),
                                Ui.ligne(
                                        Ui.bouton(
                                                "Renvoyer à ceux qui n'ont pas voté",
                                                () -> renvoyer(Cible.SANS_BULLETIN)),
                                        Ui.bouton("Renvoyer à tous", () -> renvoyer(Cible.TOUS))),
                                Ui.aide("Chaque e-mail envoyé coûte un crédit."))),
                statut);
    }

    private enum Cible {
        TOUS,
        SANS_BULLETIN
    }

    private interface Usage<T> {
        T avec(OperateurCredentials operateur) throws Exception;
    }

    /** Ouvre une connexion au serveur de credentials le temps d'une opération. */
    private <T> void operer(Usage<T> usage, java.util.function.Consumer<T> succes) {
        String adresse = serveur.getText().strip();
        String id = referendum.getText().strip();
        String g = graine.getText();
        contexte.executer(
                statut,
                () -> {
                    try (ClientCertification client = ClientCertification.vers(adresse)) {
                        return usage.avec(
                                new OperateurCredentials(
                                        client, contexte.urne(), Contexte.GROUPE, id, g));
                    }
                },
                succes);
    }

    private void verifier() {
        operer(
                OperateurCredentials::verifierCertificat,
                anomalies -> {
                    if (anomalies.isEmpty()) {
                        statut.succes(
                                "Certificat valide : il vient de votre graine et couvre les"
                                    + " credentials publiés.");
                    } else {
                        statut.erreur(String.join(" · ", anomalies));
                    }
                });
    }

    private void credits() {
        operer(OperateurCredentials::creditsRestants, n -> credits.setText(Integer.toString(n)));
    }

    private void renvoyerA() {
        List<String> liste =
                Arrays.stream(destinataires.getText().split(","))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (liste.isEmpty()) {
            statut.erreur("Indiquez au moins un identifiant.");
            return;
        }
        operer(
                o -> {
                    o.renvoyerA(liste);
                    return o.creditsRestants();
                },
                n -> {
                    credits.setText(Integer.toString(n));
                    statut.succes("Credential renvoyé à " + String.join(", ", liste) + ".");
                });
    }

    private void renvoyer(Cible cible) {
        operer(
                o -> {
                    if (cible == Cible.TOUS) {
                        o.renvoyerATous();
                    } else {
                        o.renvoyerSansBulletin();
                    }
                    return o.creditsRestants();
                },
                n -> {
                    credits.setText(Integer.toString(n));
                    statut.succes("Credentials renvoyés.");
                });
    }
}
