package fr.cerelios.gui.administrateur;

import fr.cerelios.gui.ChampCopiable;
import fr.cerelios.gui.ChampDate;
import fr.cerelios.gui.Contexte;
import fr.cerelios.gui.FicheReferendum;
import fr.cerelios.gui.Statut;
import fr.cerelios.gui.Ui;
import fr.cerelios.protocole.messages.Dates;
import fr.cerelios.protocole.messages.Emargement;
import fr.cerelios.protocole.messages.Etat;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.ScrutateurPublic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Préparation et suivi d'un référendum par son administrateur, un onglet par sujet. */
final class EditionReferendum {

    private static final int ONGLET_VALIDATION = 4;
    private static final int ONGLET_SUIVI = 5;

    private final Contexte contexte;
    private final Administration admin;
    private final String id;
    private final Statut statut = new Statut();
    // Vrai tant que le référendum est un brouillon : la plupart des champs ne servent qu'alors.
    private final SimpleBooleanProperty brouillon = new SimpleBooleanProperty(true);
    private ReferendumPublic dernier;

    @FXML private FicheReferendum fiche;
    @FXML private TabPane onglets;

    @FXML private TextField question;
    @FXML private Button enregistrerQuestion;
    @FXML private ChampDate ouverture;
    @FXML private ChampDate cloture;
    @FXML private ChampDate publication;
    @FXML private Spinner<Integer> grace;

    @FXML private TextArea electeurs;
    @FXML private Button enregistrerElecteurs;

    @FXML private Spinner<Integer> seuil;
    @FXML private Button enregistrerSeuil;
    @FXML private TableView<ScrutateurPublic> scrutateurs;
    @FXML private TableColumn<ScrutateurPublic, String> colIndice;
    @FXML private TableColumn<ScrutateurPublic, String> colNom;
    @FXML private TableColumn<ScrutateurPublic, String> colCle;
    @FXML private TableColumn<ScrutateurPublic, String> colDechiffrement;
    @FXML private TextField nomScrutateur;
    @FXML private TextField emailScrutateur;
    @FXML private Button inviter;

    @FXML private RadioButton manuelle;
    @FXML private RadioButton serveur;
    @FXML private VBox blocManuel;
    @FXML private VBox blocServeur;
    @FXML private ChampCopiable jetonAutorite;
    @FXML private TextField adresseServeur;
    @FXML private TextField emailOperateur;

    @FXML private ListView<String> manques;
    @FXML private Button valider;
    @FXML private Button sceller;

    @FXML private TableView<Emargement.Ligne> emargement;
    @FXML private TableColumn<Emargement.Ligne, String> colElecteur;
    @FXML private TableColumn<Emargement.Ligne, String> colDate;

    EditionReferendum(Contexte contexte, String id) {
        this.contexte = contexte;
        this.admin = new Administration(contexte);
        this.id = id;
    }

    Parent vue() {
        return Ui.page(
                contexte, "Administrateur", Ui.charger("EditionReferendum.fxml", this), statut);
    }

    @FXML
    private void initialize() {
        // Champs du brouillon, figés une fois le référendum validé.
        for (Node n :
                List.of(
                        question,
                        enregistrerQuestion,
                        electeurs,
                        enregistrerElecteurs,
                        seuil,
                        enregistrerSeuil,
                        manuelle,
                        serveur,
                        blocServeur,
                        valider)) {
            n.disableProperty().bind(brouillon.not());
        }
        sceller.disableProperty().bind(brouillon);
        inviter.disableProperty()
                .bind(
                        brouillon
                                .not()
                                .or(nomScrutateur.textProperty().isEmpty())
                                .or(emailScrutateur.textProperty().isEmpty()));

        blocManuel.visibleProperty().bind(manuelle.selectedProperty());
        blocManuel.managedProperty().bind(manuelle.selectedProperty());
        blocServeur.visibleProperty().bind(serveur.selectedProperty());
        blocServeur.managedProperty().bind(serveur.selectedProperty());

        lier(colIndice, s -> Integer.toString(s.getIndice()));
        lier(colNom, ScrutateurPublic::getNom);
        lier(colCle, this::avancementCle);
        lier(colDechiffrement, s -> s.getDechiffrement().isEmpty() ? "—" : "déposé");
        lier(colElecteur, Emargement.Ligne::getElecteur);
        lier(colDate, l -> Ui.date(l.getDate()));

        onglets.getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (o, avant, apres) -> {
                            if (apres.intValue() == ONGLET_VALIDATION) {
                                chargerManques();
                            } else if (apres.intValue() == ONGLET_SUIVI) {
                                chargerEmargement();
                            }
                        });

        recharger();
        chargerElecteurs();
    }

    // ---- Actions des boutons -------------------------------------------------------------------

    @FXML
    private void retour() {
        contexte.afficher(new MesReferendums(contexte).vue());
    }

    @FXML
    private void recharger() {
        contexte.executer(statut, () -> admin.referendum(id), this::afficher);
    }

    @FXML
    private void enregistrerQuestion() {
        String texte = question.getText().strip();
        agir(() -> admin.definirQuestion(id, texte), "Question enregistrée.");
    }

    @FXML
    private void enregistrerDates() {
        Dates dates;
        try {
            dates =
                    Dates.newBuilder()
                            .setOuverture(ouverture.instant().toEpochMilli())
                            .setCloture(cloture.instant().toEpochMilli())
                            .setPublication(publication.instant().toEpochMilli())
                            .setDelaiGrace(grace.getValue() * 60_000L)
                            .build();
        } catch (IllegalArgumentException e) {
            statut.erreur(e.getMessage());
            return;
        }
        agir(() -> admin.definirDates(id, dates), "Calendrier enregistré.");
    }

    @FXML
    private void enregistrerElecteurs() {
        List<String> liste = new ArrayList<>();
        Map<String, String> emails = new LinkedHashMap<>();
        for (String ligne : electeurs.getText().split("\n")) {
            String[] champs = ligne.strip().split("\\s+");
            if (champs[0].isEmpty()) {
                continue;
            }
            liste.add(champs[0]);
            if (champs.length > 1) {
                emails.put(champs[0], champs[1]);
            }
        }
        agir(
                () -> admin.definirElecteurs(id, liste, emails),
                liste.size()
                        + " électeur(s) enregistré(s), dont "
                        + emails.size()
                        + " avec une adresse.");
    }

    @FXML
    private void enregistrerSeuil() {
        int valeur = seuil.getValue();
        agir(() -> admin.definirSeuil(id, valeur), "Seuil enregistré.");
    }

    @FXML
    private void inviter() {
        String nom = nomScrutateur.getText().strip();
        String email = emailScrutateur.getText().strip();
        agir(() -> admin.ajouterScrutateur(id, nom, email), "Invitation envoyée à " + email + ".");
        nomScrutateur.clear();
        emailScrutateur.clear();
    }

    @FXML
    private void afficherJeton() {
        contexte.executer(statut, () -> admin.jetonAutorite(id), jetonAutorite::setTexte);
    }

    @FXML
    private void enregistrerServeur() {
        String adresse = adresseServeur.getText().strip();
        String email = emailOperateur.getText().strip();
        agir(
                () -> admin.definirServeurCertification(id, adresse, email),
                "Serveur de credentials enregistré.");
    }

    @FXML
    private void demanderCredentials() {
        agir(() -> admin.demanderCredentials(id), "Credentials reçus et certificat vérifié.");
    }

    @FXML
    private void valider() {
        if (Ui.confirmer(
                contexte,
                "Après validation, la question, les électeurs, les scrutateurs et les credentials"
                    + " ne pourront plus changer. Valider ?")) {
            agir(() -> admin.valider(id), "Référendum validé.");
        }
    }

    @FXML
    private void sceller() {
        if (Ui.confirmer(
                contexte, "Une fois scellées, les dates ne pourront plus changer. Sceller ?")) {
            agir(() -> admin.sceller(id), "Calendrier scellé.");
        }
    }

    @FXML
    private void chargerManques() {
        contexte.executer(
                statut, () -> admin.manques(id), liste -> manques.getItems().setAll(liste));
    }

    @FXML
    private void chargerEmargement() {
        contexte.executer(
                statut, () -> admin.emargement(id), lignes -> emargement.getItems().setAll(lignes));
    }

    @FXML
    private void archiver() {
        if (Ui.confirmer(
                contexte,
                "L'archivage efface la liste des électeurs et l'émargement ; le résultat reste"
                    + " publié. Archiver ?")) {
            agir(() -> admin.archiver(id), "Référendum archivé.");
        }
    }

    @FXML
    private void supprimer() {
        if (Ui.confirmer(contexte, "Supprimer définitivement ce référendum ?")) {
            contexte.executer(
                    statut,
                    () -> {
                        admin.supprimer(id);
                        return null;
                    },
                    ok -> retour());
        }
    }

    // ---- Outils --------------------------------------------------------------------------------

    private interface Action {
        void lancer() throws Exception;
    }

    /** Lance une action sur l'urne puis recharge la fiche. */
    private void agir(Action action, String succes) {
        contexte.executer(
                statut,
                () -> {
                    action.lancer();
                    return admin.referendum(id);
                },
                r -> {
                    afficher(r);
                    statut.succes(succes);
                });
    }

    private void afficher(ReferendumPublic r) {
        boolean premier = dernier == null;
        dernier = r;
        fiche.afficher(r);
        brouillon.set(r.getEtat() == Etat.BROUILLON);
        scrutateurs.getItems().setAll(r.getScrutateursList());
        if (premier) {
            question.setText(r.getQuestion());
            seuil.getValueFactory().setValue(r.getSeuil());
            if (r.hasDates()) {
                ouverture.afficher(r.getDates().getOuverture());
                cloture.afficher(r.getDates().getCloture());
                publication.afficher(r.getDates().getPublication());
                grace.getValueFactory().setValue((int) (r.getDates().getDelaiGrace() / 60_000));
            }
        }
    }

    private void chargerElecteurs() {
        contexte.executer(
                statut,
                () -> admin.electeurs(id),
                liste -> {
                    if (electeurs.getText().isEmpty()) {
                        electeurs.setText(String.join("\n", liste));
                    }
                });
    }

    private String avancementCle(ScrutateurPublic s) {
        if (!s.getClePublique().isEmpty()) {
            return dernier != null && dernier.getSeuil() > 0 ? "partage terminé" : "clé déposée";
        }
        if (s.getEngagementsCount() > 0) {
            return "polynôme déposé";
        }
        if (!s.getCleCanal().isEmpty()) {
            return "clé de canal déposée";
        }
        return "en attente";
    }

    private static <T> void lier(TableColumn<T, String> colonne, Function<T, String> valeur) {
        colonne.setCellValueFactory(c -> new SimpleStringProperty(valeur.apply(c.getValue())));
    }
}
