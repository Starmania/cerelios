package fr.cerelios.gui.administrateur;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

/** Préparation et suivi d'un référendum par son administrateur, un onglet par sujet. */
final class EditionReferendum {

    private final Contexte contexte;
    private final Administration admin;
    private final String id;
    private final Statut statut = new Statut();
    private final FicheReferendum fiche = new FicheReferendum();
    // Vrai tant que le référendum est un brouillon : la plupart des champs ne servent qu'alors.
    private final SimpleBooleanProperty brouillon = new SimpleBooleanProperty(true);
    private ReferendumPublic dernier;

    // Question et calendrier
    private final TextField question = new TextField();
    private final ChampDate ouverture = new ChampDate();
    private final ChampDate cloture = new ChampDate();
    private final ChampDate publication = new ChampDate();
    private final Spinner<Integer> grace = new Spinner<>(0, 24 * 60, 0, 5);

    // Électeurs
    private final TextArea electeurs = new TextArea();

    // Scrutateurs
    private final Spinner<Integer> seuil = new Spinner<>(0, 50, 0);
    private final TableView<ScrutateurPublic> scrutateurs = new TableView<>();
    private final TextField nomScrutateur = new TextField();
    private final TextField emailScrutateur = new TextField();

    // Credentials
    private final RadioButton manuelle = new RadioButton("Autorité de certification manuelle");
    private final RadioButton serveur = new RadioButton("Serveur de credentials");
    private final TextField jetonAutorite = new TextField();
    private final TextField adresseServeur = new TextField();
    private final TextField emailOperateur = new TextField();

    // Validation et suivi
    private final ListView<String> manques = new ListView<>();
    private final TableView<Emargement.Ligne> emargement = new TableView<>();

    EditionReferendum(Contexte contexte, String id) {
        this.contexte = contexte;
        this.admin = new Administration(contexte);
        this.id = id;
    }

    Parent vue() {
        TabPane onglets =
                new TabPane(
                        onglet("Question et calendrier", ongletQuestion()),
                        onglet("Électeurs", ongletElecteurs()),
                        onglet("Scrutateurs", ongletScrutateurs()),
                        onglet("Credentials", ongletCredentials()),
                        onglet("Validation", ongletValidation()),
                        onglet("Suivi", ongletSuivi()));
        onglets.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        onglets.getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (o, avant, apres) -> {
                            if (apres.intValue() == 4) {
                                chargerManques();
                            } else if (apres.intValue() == 5) {
                                chargerEmargement();
                            }
                        });

        Button retour =
                Ui.bouton(
                        "← Mes référendums",
                        () -> contexte.afficher(new MesReferendums(contexte).vue()));
        retour.getStyleClass().add("lien");
        recharger();
        chargerElecteurs();
        return Ui.page(
                contexte,
                "Administrateur",
                Ui.colonne(
                        Ui.ligne(retour, Ui.bouton("Actualiser", this::recharger)), fiche, onglets),
                statut);
    }

    private static Tab onglet(String titre, Node contenu) {
        VBox boite = new VBox(12, contenu);
        boite.getStyleClass().add("onglet");
        return new Tab(titre, boite);
    }

    // ---- Onglets -------------------------------------------------------------------------------

    private Node ongletQuestion() {
        Button enregistrerQuestion =
                Ui.bouton(
                        "Enregistrer la question",
                        () ->
                                agir(
                                        () -> admin.definirQuestion(id, question.getText().strip()),
                                        "Question enregistrée."));
        enregistrerQuestion.disableProperty().bind(brouillon.not());
        question.disableProperty().bind(brouillon.not());
        grace.setEditable(true);
        Button enregistrerDates = Ui.principal("Enregistrer le calendrier", this::enregistrerDates);
        return new VBox(
                12,
                Ui.formulaire("Question", question),
                enregistrerQuestion,
                Ui.formulaire(
                        "Ouverture",
                        ouverture,
                        "Clôture",
                        cloture,
                        "Publication",
                        publication,
                        "Délai de grâce (min)",
                        grace),
                Ui.aide(
                        "Rien n'est manuel : le vote s'ouvre et se clôt à ces dates, et le résultat"
                            + " paraît dès la publication passée et les déchiffrements reçus. Le"
                            + " délai de grâce laisse finir les électeurs connectés avant la"
                            + " clôture."),
                enregistrerDates);
    }

    private Node ongletElecteurs() {
        electeurs.setPromptText("bob bob@exemple.fr\ncarol carol@exemple.fr\ndave");
        electeurs.setPrefRowCount(12);
        electeurs.disableProperty().bind(brouillon.not());
        Button enregistrer = Ui.principal("Enregistrer la liste", this::enregistrerElecteurs);
        enregistrer.disableProperty().bind(brouillon.not());
        return new VBox(
                12,
                Ui.aide(
                        "Un électeur par ligne : son identifiant auprès du fournisseur"
                                + " d'identité, puis, séparée par une espace, son adresse e-mail."
                                + " L'adresse est obligatoire avec un serveur de credentials, qui"
                                + " écrit lui-même aux électeurs. Les adresses ne sont pas"
                                + " relues : ressaisissez-les si vous modifiez la liste."),
                electeurs,
                enregistrer);
    }

    private Node ongletScrutateurs() {
        TableColumn<ScrutateurPublic, String> colIndice =
                colonne("N°", s -> Integer.toString(s.getIndice()));
        TableColumn<ScrutateurPublic, String> colNom = colonne("Nom", ScrutateurPublic::getNom);
        colNom.setPrefWidth(180);
        TableColumn<ScrutateurPublic, String> colCle = colonne("Clé", this::avancementCle);
        colCle.setPrefWidth(220);
        TableColumn<ScrutateurPublic, String> colDechiffre =
                colonne("Déchiffrement", s -> s.getDechiffrement().isEmpty() ? "—" : "déposé");
        colDechiffre.setPrefWidth(140);
        scrutateurs.getColumns().setAll(List.of(colIndice, colNom, colCle, colDechiffre));
        scrutateurs.setPrefHeight(200);
        scrutateurs.setPlaceholder(new Label("Aucun scrutateur"));

        seuil.setEditable(true);
        seuil.disableProperty().bind(brouillon.not());
        Button enregistrerSeuil =
                Ui.bouton(
                        "Enregistrer le seuil",
                        () ->
                                agir(
                                        () -> admin.definirSeuil(id, seuil.getValue()),
                                        "Seuil enregistré."));
        enregistrerSeuil.disableProperty().bind(brouillon.not());

        nomScrutateur.setPromptText("Nom");
        emailScrutateur.setPromptText("adresse e-mail");
        Button inviter =
                Ui.principal(
                        "Inviter",
                        () -> {
                            String nom = nomScrutateur.getText().strip();
                            String email = emailScrutateur.getText().strip();
                            agir(
                                    () -> admin.ajouterScrutateur(id, nom, email),
                                    "Invitation envoyée à " + email + ".");
                            nomScrutateur.clear();
                            emailScrutateur.clear();
                        });
        inviter.disableProperty()
                .bind(
                        brouillon
                                .not()
                                .or(nomScrutateur.textProperty().isEmpty())
                                .or(emailScrutateur.textProperty().isEmpty()));
        return new VBox(
                12,
                Ui.ligne(new Label("Seuil"), seuil, enregistrerSeuil),
                Ui.aide(
                        "0 : chaque scrutateur a sa propre clé et tous devront déchiffrer. k > 0 :"
                            + " les scrutateurs génèrent la clé ensemble et k d'entre eux"
                            + " suffiront. Le seuil se fixe avant que les scrutateurs commencent."),
                scrutateurs,
                Ui.ligne(nomScrutateur, emailScrutateur, inviter),
                Ui.aide(
                        "Chaque scrutateur reçoit par e-mail le numéro du référendum, son nom et"
                            + " son jeton."));
    }

    private Node ongletCredentials() {
        ToggleGroup mode = new ToggleGroup();
        manuelle.setToggleGroup(mode);
        serveur.setToggleGroup(mode);
        manuelle.setSelected(true);
        manuelle.disableProperty().bind(brouillon.not());
        serveur.disableProperty().bind(brouillon.not());

        jetonAutorite.setPromptText("Cliquez sur « Afficher le jeton »");
        VBox blocManuel =
                new VBox(
                        8,
                        Ui.aide(
                                "Transmettez ce jeton et le numéro du référendum à l'autorité de"
                                        + " certification. Elle génère les credentials et les remet"
                                        + " elle-même aux électeurs."),
                        Ui.copiable(jetonAutorite),
                        Ui.bouton(
                                "Afficher le jeton",
                                () ->
                                        contexte.executer(
                                                statut,
                                                () -> admin.jetonAutorite(id),
                                                jetonAutorite::setText)));

        adresseServeur.setPromptText("hôte:port, par exemple localhost:5001");
        emailOperateur.setPromptText("adresse de l'opérateur, qui recevra la graine");
        Button enregistrerServeur =
                Ui.bouton(
                        "Enregistrer le serveur",
                        () ->
                                agir(
                                        () ->
                                                admin.definirServeurCertification(
                                                        id,
                                                        adresseServeur.getText().strip(),
                                                        emailOperateur.getText().strip()),
                                        "Serveur de credentials enregistré."));
        Button demander =
                Ui.principal(
                        "Demander les credentials",
                        () ->
                                agir(
                                        () -> admin.demanderCredentials(id),
                                        "Credentials reçus et certificat vérifié."));
        VBox blocServeur =
                new VBox(
                        8,
                        Ui.formulaire("Serveur", adresseServeur, "Opérateur", emailOperateur),
                        Ui.ligne(enregistrerServeur, demander),
                        Ui.aide(
                                "Le serveur génère les credentials, envoie la graine à l'opérateur,"
                                    + " puis écrit à chaque électeur quand vous validez. Il faut"
                                    + " une adresse e-mail par électeur."));
        blocServeur.disableProperty().bind(brouillon.not());
        blocServeur.visibleProperty().bind(serveur.selectedProperty());
        blocServeur.managedProperty().bind(serveur.selectedProperty());
        blocManuel.visibleProperty().bind(manuelle.selectedProperty());
        blocManuel.managedProperty().bind(manuelle.selectedProperty());
        return new VBox(12, Ui.ligne(manuelle, serveur), blocManuel, blocServeur);
    }

    private Node ongletValidation() {
        manques.setPrefHeight(180);
        manques.setPlaceholder(new Label("Rien ne manque : le référendum peut être validé."));
        Button valider =
                Ui.principal(
                        "Valider",
                        () -> {
                            if (confirmer(
                                    "Après validation, la question, les électeurs, les scrutateurs"
                                            + " et les credentials ne pourront plus changer."
                                            + " Valider ?")) {
                                agir(() -> admin.valider(id), "Référendum validé.");
                            }
                        });
        valider.disableProperty().bind(brouillon.not());
        Button sceller =
                Ui.bouton(
                        "Sceller le calendrier",
                        () -> {
                            if (confirmer(
                                    "Une fois scellées, les dates ne pourront plus changer. Sceller"
                                        + " ?")) {
                                agir(() -> admin.sceller(id), "Calendrier scellé.");
                            }
                        });
        sceller.disableProperty().bind(brouillon);
        return new VBox(
                12,
                new Label("Ce qui manque avant de valider"),
                manques,
                Ui.ligne(valider, Ui.bouton("Actualiser", this::chargerManques)),
                Ui.aide(
                        "Sceller inscrit les dates au journal public et les fige : personne, pas"
                                + " même vous, ne pourra avancer ou repousser le vote."),
                sceller);
    }

    private Node ongletSuivi() {
        TableColumn<Emargement.Ligne, String> colElecteur = new TableColumn<>("Électeur");
        colElecteur.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getElecteur()));
        colElecteur.setPrefWidth(260);
        TableColumn<Emargement.Ligne, String> colDate = new TableColumn<>("Dernier vote");
        colDate.setCellValueFactory(c -> new SimpleStringProperty(Ui.date(c.getValue().getDate())));
        colDate.setPrefWidth(200);
        emargement.getColumns().setAll(List.of(colElecteur, colDate));
        emargement.setPlaceholder(new Label("Personne n'a encore voté."));
        emargement.setPrefHeight(240);
        Button archiver =
                Ui.bouton(
                        "Archiver",
                        () -> {
                            if (confirmer(
                                    "L'archivage efface la liste des électeurs et l'émargement ; le"
                                            + " résultat reste publié. Archiver ?")) {
                                agir(() -> admin.archiver(id), "Référendum archivé.");
                            }
                        });
        Button supprimer =
                Ui.bouton(
                        "Supprimer",
                        () -> {
                            if (confirmer("Supprimer définitivement ce référendum ?")) {
                                contexte.executer(
                                        statut,
                                        () -> {
                                            admin.supprimer(id);
                                            return null;
                                        },
                                        ok ->
                                                contexte.afficher(
                                                        new MesReferendums(contexte).vue()));
                            }
                        });
        supprimer.getStyleClass().add("danger");
        return new VBox(
                12,
                Ui.aide("Qui a voté et quand ; jamais pour quoi."),
                emargement,
                Ui.ligne(Ui.bouton("Actualiser", this::chargerEmargement), archiver, supprimer));
    }

    // ---- Actions -------------------------------------------------------------------------------

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

    private void recharger() {
        contexte.executer(statut, () -> admin.referendum(id), this::afficher);
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

    private void enregistrerDates() {
        Dates dates;
        try {
            Instant o = ouverture.instant();
            Instant c = cloture.instant();
            Instant p = publication.instant();
            dates =
                    Dates.newBuilder()
                            .setOuverture(o.toEpochMilli())
                            .setCloture(c.toEpochMilli())
                            .setPublication(p.toEpochMilli())
                            .setDelaiGrace(grace.getValue() * 60_000L)
                            .build();
        } catch (IllegalArgumentException e) {
            statut.erreur(e.getMessage());
            return;
        }
        agir(() -> admin.definirDates(id, dates), "Calendrier enregistré.");
    }

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

    private void chargerManques() {
        contexte.executer(
                statut, () -> admin.manques(id), liste -> manques.getItems().setAll(liste));
    }

    private void chargerEmargement() {
        contexte.executer(
                statut, () -> admin.emargement(id), lignes -> emargement.getItems().setAll(lignes));
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

    private boolean confirmer(String message) {
        Alert alerte =
                new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alerte.setHeaderText(null);
        alerte.initOwner(contexte.stage());
        return alerte.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static <T> TableColumn<T, String> colonne(
            String titre, java.util.function.Function<T, String> valeur) {
        TableColumn<T, String> colonne = new TableColumn<>(titre);
        colonne.setCellValueFactory(c -> new SimpleStringProperty(valeur.apply(c.getValue())));
        return colonne;
    }
}
