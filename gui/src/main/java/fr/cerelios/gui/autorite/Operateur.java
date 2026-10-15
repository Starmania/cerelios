package fr.cerelios.gui.autorite;

import fr.cerelios.autorite.OperateurCredentials;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.ClientCertification;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Opérateur du serveur de credentials : certificat, renvois, crédits. */
public final class Operateur {

    private final Contexte contexte;
    private final Statut statut = new Statut();
    @FXML private TextField serveur;
    @FXML private TextField referendum;
    @FXML private PasswordField graine;
    @FXML private Label credits;
    @FXML private TextField destinataires;

    public Operateur(Contexte contexte) {
        this.contexte = contexte;
    }

    public Parent vue() {
        return Ui.page(
                contexte, "Opérateur des credentials", Ui.charger("Operateur.fxml", this), statut);
    }

    private interface Usage<T> {
        T avec(OperateurCredentials operateur) throws Exception;
    }

    /** Ouvre une connexion au serveur de credentials le temps d'une opération. */
    private <T> void operer(Usage<T> usage, Consumer<T> succes) {
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

    @FXML
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

    @FXML
    private void credits() {
        operer(OperateurCredentials::creditsRestants, n -> credits.setText(Integer.toString(n)));
    }

    @FXML
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
        renvoyer(o -> o.renvoyerA(liste), "Credential renvoyé à " + String.join(", ", liste) + ".");
    }

    @FXML
    private void renvoyerSansBulletin() {
        renvoyer(
                OperateurCredentials::renvoyerSansBulletin,
                "Credentials renvoyés à ceux qui n'ont pas voté.");
    }

    @FXML
    private void renvoyerATous() {
        renvoyer(OperateurCredentials::renvoyerATous, "Credentials renvoyés à tous les électeurs.");
    }

    private interface Renvoi {
        void faire(OperateurCredentials operateur) throws Exception;
    }

    private void renvoyer(Renvoi renvoi, String succes) {
        operer(
                o -> {
                    renvoi.faire(o);
                    return o.creditsRestants();
                },
                n -> {
                    credits.setText(Integer.toString(n));
                    statut.succes(succes);
                });
    }
}
