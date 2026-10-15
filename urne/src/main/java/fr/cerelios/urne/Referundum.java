package fr.cerelios.urne;

import fr.cerelios.crypto.Bulletin;
import fr.cerelios.crypto.ChiffrementHybride;
import fr.cerelios.crypto.DechiffrementImpossibleException;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.crypto.Partage;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.crypto.Preuves.PreuveDechiffrement;
import fr.cerelios.crypto.Preuves.PreuveSchnorr;
import fr.cerelios.protocole.Certificats;
import fr.cerelios.protocole.Contextes;
import fr.cerelios.protocole.messages.Certificat;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import org.jetbrains.annotations.TestOnly;

/**
 * Un référendum Oui/Non, de sa préparation à son archivage.
 *
 * <p>Aucune action ne fait avancer le référendum dans le temps : chaque lecture de {@link #etat()}
 * applique les changements dus aux dates (ouverture, clôture, fin du délai de grâce, publication).
 * Il n'y a donc ni tâche planifiée ni bouton « ouvrir » ou « publier ».
 *
 * <p>Les méthodes ne sont pas synchronisées : l'appelant ({@link Urne}) verrouille le référendum.
 */
public class Referundum {

    private static final SecureRandom ALEA = new SecureRandom();

    /** Scrutateur inscrit par l'administrateur, connu de l'urne par son jeton secret. */
    public static final class Scrutateur {
        private final String nom;
        private final String email;
        private final String jeton;
        private BigInteger clePublique;
        private PreuveSchnorr preuveCle;
        private BigInteger dechiffrement;
        private PreuveDechiffrement preuveDechiffrement;
        private BigInteger cleCanal;
        private PreuveSchnorr preuveCanal;
        private Polynome polynome;

        private Scrutateur(String nom, String email, String jeton) {
            this.nom = nom;
            this.email = email;
            this.jeton = jeton;
        }

        public String nom() {
            return nom;
        }

        public String email() {
            return email;
        }

        public BigInteger clePublique() {
            return clePublique;
        }

        public PreuveSchnorr preuveCle() {
            return preuveCle;
        }

        public BigInteger dechiffrement() {
            return dechiffrement;
        }

        public PreuveDechiffrement preuveDechiffrement() {
            return preuveDechiffrement;
        }

        public BigInteger cleCanal() {
            return cleCanal;
        }

        public PreuveSchnorr preuveCanal() {
            return preuveCanal;
        }

        public Polynome polynome() {
            return polynome;
        }
    }

    /**
     * Contribution d'un scrutateur au partage à seuil : engagements de son polynôme et parts
     * chiffrées pour chaque scrutateur, par indice. L'urne ne peut pas lire les parts.
     */
    public record Polynome(
            List<BigInteger> engagements,
            SortedMap<Integer, ChiffrementHybride.Message> parts,
            PreuveSchnorr signature) {}

    /** Étapes du partage à seuil, communes à tous les scrutateurs. */
    public enum EtapePartage {
        CANAUX,
        POLYNOMES,
        CLES_PARTIELLES,
        TERMINE
    }

    /** Fermeture du calendrier : les dates ne peuvent plus changer. */
    public record Scellement(
            Instant date, Instant ouverture, Instant cloture, Instant publication) {}

    /** Bulletin accepté, avec l'électeur qui l'a déposé. */
    public record BulletinDepose(Bulletin bulletin, String electeur, Instant date) {}

    public record Recepisse(String empreinte, boolean revote) {}

    public record Resultat(int oui, int non) {}

    private final String id;
    private final Groupe groupe;
    private final Clock horloge;
    private final Instant creation;
    private final Set<String> proprietaires = new LinkedHashSet<>();
    private final String jetonAutorite = jeton();

    private String question;
    private Etat etat = Etat.BROUILLON;

    // Électeurs, en minuscules pour qu'« Alice » et « alice » ne votent pas deux fois.
    private final Set<String> electeurs = new LinkedHashSet<>();
    private int nombreElecteurs;

    private Instant ouverture;
    private Instant cloture;
    private Instant publication;
    private Duration delaiGrace = Duration.ZERO;
    private final List<Scellement> scellements = new ArrayList<>();

    private final List<Scrutateur> scrutateurs = new ArrayList<>();
    private BigInteger cle;
    // Nombre de scrutateurs qui suffisent à déchiffrer ; 0 : tous, chacun avec sa propre clé.
    private int seuil;

    // Lien électeur -> credential : secret, il relierait un bulletin à son électeur.
    private final Map<String, BigInteger> credentials = new HashMap<>();
    // Adresses des électeurs, pour le serveur de credentials qui leur écrit.
    private final Map<String, String> emails = new HashMap<>();
    private ServeurCertification serveurCertification;
    private Certificat certificat;
    private final Map<BigInteger, BulletinDepose> urne = new LinkedHashMap<>();
    private final Set<String> empreintes = new HashSet<>();
    private final Map<String, Instant> emargement = new LinkedHashMap<>();

    private Chiffre total;
    private Resultat resultat;
    private Instant datePublication;
    private Instant dateArchivage;

    public Referundum(
            String id, String question, String proprietaire, Groupe groupe, Clock horloge) {
        this.id = id;
        this.question = question;
        this.groupe = groupe;
        this.horloge = horloge;
        this.creation = horloge.instant();
        proprietaires.add(proprietaire);
    }

    /** Référendum déjà validé qui se termine à {@code dateFin} : réservé aux tests. */
    @TestOnly
    Referundum(Instant dateFin, Clock horloge) {
        this("test", "Question ?", "admin", Groupe.RFC_3526, horloge);
        Instant maintenant = horloge.instant();
        definirDates(maintenant, dateFin, dateFin, Duration.ZERO);
        etat = Etat.VALIDE;
    }

    // ---- Lecture -------------------------------------------------------------------------------

    public String id() {
        return id;
    }

    public String question() {
        return question;
    }

    public Groupe groupe() {
        return groupe;
    }

    public Instant creation() {
        return creation;
    }

    public boolean estProprietaire(String identifiant) {
        return proprietaires.contains(identifiant);
    }

    public Instant ouverture() {
        return ouverture;
    }

    public Instant cloture() {
        return cloture;
    }

    public Instant publication() {
        return publication;
    }

    public Duration delaiGrace() {
        return delaiGrace;
    }

    public boolean scelle() {
        return !scellements.isEmpty();
    }

    public List<Scellement> scellements() {
        return List.copyOf(scellements);
    }

    public List<Scrutateur> scrutateurs() {
        return List.copyOf(scrutateurs);
    }

    public BigInteger cle() {
        return cle;
    }

    public int nombreElecteurs() {
        return etat == Etat.BROUILLON ? electeurs.size() : nombreElecteurs;
    }

    public int nombreBulletins() {
        return urne.size();
    }

    /** Credentials publiés, triés : l'ordre ne trahit pas celui de la liste des électeurs. */
    public List<BigInteger> credentialsPublics() {
        return credentials.values().stream().sorted().toList();
    }

    /** Bulletins publiés, triés par empreinte : l'ordre ne trahit pas l'heure du vote. */
    public List<Bulletin> bulletins() {
        return urne.values().stream()
                .map(BulletinDepose::bulletin)
                .sorted(Comparator.comparing(Bulletin::empreinte))
                .toList();
    }

    /** Qui a voté et quand ; jamais pour quoi. Vide une fois archivé. */
    public Map<String, Instant> emargement() {
        return Map.copyOf(emargement);
    }

    public Chiffre total() {
        return total;
    }

    public Resultat resultat() {
        return resultat;
    }

    public Instant datePublication() {
        return datePublication;
    }

    public Instant dateArchivage() {
        return dateArchivage;
    }

    /** Vrai une fois la date de clôture passée. */
    public boolean verifFini() {
        return cloture != null && Instant.now(horloge).isAfter(cloture);
    }

    // ---- Transitions paresseuses ---------------------------------------------------------------

    /** État courant, après application des changements dus aux dates. */
    public Etat etat() {
        Instant maintenant = horloge.instant();
        Etat avant;
        do {
            avant = etat;
            switch (etat) {
                case VALIDE -> {
                    if (!maintenant.isBefore(ouverture)) {
                        etat = Etat.OUVERT;
                    }
                }
                case OUVERT -> {
                    if (!maintenant.isBefore(cloture)) {
                        etat = Etat.CLOS;
                    }
                }
                case CLOS -> {
                    // Le total n'est calculé qu'après le délai de grâce : plus aucun bulletin
                    // ne peut arriver.
                    if (!maintenant.isBefore(cloture.plus(delaiGrace))) {
                        total = agregerUrne();
                        etat = Etat.DEPOUILLEMENT;
                    }
                }
                case DEPOUILLEMENT -> {
                    if (!maintenant.isBefore(publication) && tousLesDechiffrementsSontLa()) {
                        publier(maintenant);
                    }
                }
                default -> {}
            }
        } while (etat != avant);
        return etat;
    }

    private Chiffre agregerUrne() {
        Chiffre somme = new Chiffre(BigInteger.ONE, BigInteger.ONE);
        for (BulletinDepose depose : urne.values()) {
            somme = Elgamal.agrege(groupe, somme, depose.bulletin().chiffre());
        }
        return somme;
    }

    private boolean tousLesDechiffrementsSontLa() {
        long recus = scrutateurs.stream().filter(s -> s.dechiffrement != null).count();
        return seuil == 0 ? recus == scrutateurs.size() : recus >= seuil;
    }

    private void publier(Instant maintenant) {
        List<BigInteger> partiels;
        if (seuil == 0) {
            partiels = scrutateurs.stream().map(s -> s.dechiffrement).toList();
        } else {
            Map<Integer, BigInteger> parIndice = new LinkedHashMap<>();
            for (Scrutateur s : scrutateurs) {
                if (s.dechiffrement != null) {
                    parIndice.put(indice(s), s.dechiffrement);
                }
            }
            partiels = Partage.ponderer(groupe, parIndice);
        }
        try {
            int oui = Elgamal.decrypt(groupe, total, partiels, nombreElecteurs);
            resultat = new Resultat(oui, urne.size() - oui);
            datePublication = maintenant;
            etat = Etat.PUBLIE;
        } catch (DechiffrementImpossibleException e) {
            // Impossible si chaque déchiffrement partiel a passé sa preuve : on ne publie rien,
            // les auditeurs verront l'incohérence dans les données publiques.
            System.getLogger(Referundum.class.getName())
                    .log(System.Logger.Level.ERROR, "Dépouillement impossible de " + id, e);
        }
    }

    // ---- Préparation (administrateur) ----------------------------------------------------------

    public void ajouterProprietaire(String identifiant) {
        proprietaires.add(identifiant);
    }

    public void definirQuestion(String question) {
        exigerEtat(Etat.BROUILLON);
        if (question.isBlank()) {
            throw new RequeteRefusee("La question est vide");
        }
        this.question = question;
    }

    public void definirElecteurs(List<String> liste) {
        definirElecteurs(liste, Map.of());
    }

    /** Liste des électeurs et, pour ceux qui en ont, leur adresse e-mail. */
    public void definirElecteurs(List<String> liste, Map<String, String> adresses) {
        exigerEtat(Etat.BROUILLON);
        if (!credentials.isEmpty()) {
            throw new RequeteRefusee("Les credentials sont déjà générés : liste figée");
        }
        Set<String> nouveaux = new LinkedHashSet<>();
        for (String electeur : liste) {
            String normalise = normaliser(electeur);
            if (normalise.isEmpty()) {
                throw new RequeteRefusee("Identifiant d'électeur vide");
            }
            if (!nouveaux.add(normalise)) {
                throw new RequeteRefusee("Électeur en double : " + electeur);
            }
        }
        electeurs.clear();
        electeurs.addAll(nouveaux);
        emails.clear();
        adresses.forEach(
                (electeur, email) -> {
                    String normalise = normaliser(electeur);
                    if (!nouveaux.contains(normalise)) {
                        throw new RequeteRefusee(
                                "Adresse d'un électeur absent de la liste : " + electeur);
                    }
                    emails.put(normalise, email);
                });
    }

    /** Fixe le calendrier ; toutes les dates sont obligatoires puisque rien n'est manuel. */
    public void definirDates(
            Instant ouverture, Instant cloture, Instant publication, Duration delaiGrace) {
        if (scelle()) {
            throw new RequeteRefusee("Référendum scellé : dates figées");
        }
        Etat courant = etat();
        if (courant != Etat.BROUILLON && courant != Etat.VALIDE && courant != Etat.OUVERT) {
            throw new RequeteRefusee("Dates figées une fois le vote clos");
        }
        if (!ouverture.isBefore(cloture) || publication.isBefore(cloture)) {
            throw new RequeteRefusee("Il faut ouverture < clôture ≤ publication");
        }
        if (delaiGrace.isNegative()) {
            throw new RequeteRefusee("Délai de grâce négatif");
        }
        if (courant == Etat.OUVERT && !ouverture.equals(this.ouverture)) {
            throw new RequeteRefusee("Vote déjà ouvert : la date d'ouverture ne change plus");
        }
        this.ouverture = ouverture;
        this.cloture = cloture;
        this.publication = publication;
        this.delaiGrace = delaiGrace;
    }

    /**
     * Passe en mode à seuil : {@code seuil} scrutateurs sur tous suffiront à déchiffrer. 0 revient
     * au mode où chacun a sa propre clé et où tous doivent déchiffrer.
     */
    public void definirSeuil(int seuil) {
        exigerEtat(Etat.BROUILLON);
        exigerPartageNonCommence();
        if (seuil < 0) {
            throw new RequeteRefusee("Seuil négatif");
        }
        if (scrutateurs.stream().anyMatch(s -> s.clePublique != null)) {
            throw new RequeteRefusee("Des scrutateurs ont déjà déposé leur clé : mode figé");
        }
        this.seuil = seuil;
    }

    public int seuil() {
        return seuil;
    }

    /** Indice d'un scrutateur dans le partage à seuil, à partir de 1. */
    public int indice(Scrutateur scrutateur) {
        return scrutateurs.indexOf(scrutateur) + 1;
    }

    /** Inscrit un scrutateur et rend son jeton secret, à lui transmettre. */
    public String ajouterScrutateur(String nom, String email) {
        exigerEtat(Etat.BROUILLON);
        // Les indices et le nombre de parts sont fixés dès la première clé de canal.
        exigerPartageNonCommence();
        if (nom.isBlank() || scrutateurs.stream().anyMatch(s -> s.nom.equals(nom))) {
            throw new RequeteRefusee("Nom de scrutateur vide ou déjà pris : " + nom);
        }
        Scrutateur scrutateur = new Scrutateur(nom, email, jeton());
        scrutateurs.add(scrutateur);
        return scrutateur.jeton;
    }

    /** Jeton à transmettre à l'autorité de certification. */
    public String jetonAutorite() {
        return jetonAutorite;
    }

    /** Ce qui manque avant de pouvoir valider ; vide si tout est prêt. */
    public List<String> manques() {
        List<String> manques = new ArrayList<>();
        if (etat() != Etat.BROUILLON) {
            manques.add("Le référendum n'est plus un brouillon");
            return manques;
        }
        if (question.isBlank()) {
            manques.add("Question");
        }
        if (electeurs.isEmpty()) {
            manques.add("Liste des électeurs");
        }
        if (credentials.size() != electeurs.size() || electeurs.isEmpty()) {
            manques.add("Credentials de l'autorité de certification");
        }
        if (serveurCertification != null && !emails.keySet().containsAll(electeurs)) {
            manques.add("Adresse e-mail de chaque électeur, pour le serveur de credentials");
        }
        if (scrutateurs.isEmpty()) {
            manques.add("Au moins un scrutateur");
        }
        if (seuil > scrutateurs.size()) {
            manques.add("Seuil supérieur au nombre de scrutateurs");
        }
        for (Scrutateur s : scrutateurs) {
            if (s.clePublique == null) {
                manques.add((seuil == 0 ? "Clé du scrutateur " : "Partage à seuil de ") + s.nom);
            }
        }
        if (ouverture == null) {
            manques.add("Dates d'ouverture, de clôture et de publication");
        } else if (!horloge.instant().isBefore(cloture)) {
            manques.add("Date de clôture déjà passée");
        }
        return manques;
    }

    /** Fige le référendum : questions, électeurs, credentials et scrutateurs ne changent plus. */
    public void valider() {
        List<String> manques = manques();
        if (!manques.isEmpty()) {
            throw new RequeteRefusee(
                    "Impossible de valider, il manque : " + String.join(", ", manques));
        }
        cle =
                seuil == 0
                        ? scrutateurs.stream()
                                .map(s -> s.clePublique)
                                .reduce(BigInteger.ONE, (a, b) -> a.multiply(b).mod(groupe.p()))
                        : Partage.clePublique(groupe, engagementsDeChacun());
        nombreElecteurs = electeurs.size();
        etat = Etat.VALIDE;
    }

    /** Scelle le calendrier : les dates sont inscrites au journal de scellement et figées. */
    public void sceller() {
        if (etat() == Etat.BROUILLON) {
            throw new RequeteRefusee("Valider le référendum avant de le sceller");
        }
        if (scelle()) {
            throw new RequeteRefusee("Déjà scellé");
        }
        scellements.add(new Scellement(horloge.instant(), ouverture, cloture, publication));
    }

    // ---- Scrutateurs ---------------------------------------------------------------------------

    public Scrutateur scrutateurParJeton(String jeton) {
        return scrutateurs.stream()
                .filter(s -> memeJeton(s.jeton, jeton))
                .findFirst()
                .orElseThrow(() -> new RequeteRefusee("Jeton de scrutateur inconnu"));
    }

    /**
     * Dépose la clé publique du scrutateur. En mode à seuil, c'est la clé de sa part g^(x_j), que
     * tout le monde peut recalculer à partir des engagements : elle doit leur correspondre.
     */
    public void deposerCle(String jeton, BigInteger clePublique, PreuveSchnorr preuve) {
        exigerEtat(Etat.BROUILLON);
        Scrutateur s = scrutateurParJeton(jeton);
        if (s.clePublique != null) {
            throw new RequeteRefusee("Clé déjà déposée");
        }
        if (seuil > 0) {
            if (etapePartage() != EtapePartage.CLES_PARTIELLES) {
                throw new RequeteRefusee("Le partage n'en est pas aux clés partielles");
            }
            if (!clePublique.equals(
                    Partage.clePartielle(groupe, engagementsDeChacun(), indice(s)))) {
                throw new RequeteRefusee("La clé ne correspond pas aux engagements publiés");
            }
        }
        if (clePublique.equals(BigInteger.ONE)
                || !Preuves.verifierConnaissance(
                        groupe, clePublique, preuve, Contextes.cleScrutateur(id, s.nom))) {
            throw new RequeteRefusee("Preuve de connaissance de la clé invalide");
        }
        s.clePublique = clePublique;
        s.preuveCle = preuve;
    }

    public void deposerDechiffrement(String jeton, BigInteger partiel, PreuveDechiffrement preuve) {
        Scrutateur s = scrutateurParJeton(jeton);
        exigerEtat(Etat.DEPOUILLEMENT);
        if (s.dechiffrement != null) {
            throw new RequeteRefusee("Déchiffrement déjà déposé");
        }
        if (!Preuves.verifierDechiffrement(
                groupe,
                s.clePublique,
                total,
                partiel,
                preuve,
                Contextes.dechiffrement(id, s.nom))) {
            throw new RequeteRefusee("Preuve de déchiffrement invalide");
        }
        s.dechiffrement = partiel;
        s.preuveDechiffrement = preuve;
        etat();
    }

    // ---- Partage à seuil -----------------------------------------------------------------------

    public EtapePartage etapePartage() {
        if (scrutateurs.stream().anyMatch(s -> s.cleCanal == null)) {
            return EtapePartage.CANAUX;
        }
        if (scrutateurs.stream().anyMatch(s -> s.polynome == null)) {
            return EtapePartage.POLYNOMES;
        }
        if (scrutateurs.stream().anyMatch(s -> s.clePublique == null)) {
            return EtapePartage.CLES_PARTIELLES;
        }
        return EtapePartage.TERMINE;
    }

    /** Première étape : la clé qui chiffrera les parts destinées à ce scrutateur. */
    public void deposerCleCanal(String jeton, BigInteger cleCanal, PreuveSchnorr preuve) {
        exigerEtat(Etat.BROUILLON);
        Scrutateur s = scrutateurParJeton(jeton);
        if (seuil == 0) {
            throw new RequeteRefusee("Pas de partage à seuil pour ce référendum");
        }
        if (seuil > scrutateurs.size()) {
            throw new RequeteRefusee("Seuil supérieur au nombre de scrutateurs");
        }
        if (s.cleCanal != null) {
            throw new RequeteRefusee("Clé de canal déjà déposée");
        }
        if (cleCanal.equals(BigInteger.ONE)
                || !Preuves.verifierConnaissance(
                        groupe, cleCanal, preuve, Contextes.cleCanal(id, s.nom))) {
            throw new RequeteRefusee("Preuve de connaissance de la clé de canal invalide");
        }
        s.cleCanal = cleCanal;
        s.preuveCanal = preuve;
    }

    /**
     * Deuxième étape : engagements du polynôme et une part chiffrée par scrutateur. L'urne ne peut
     * pas vérifier les parts, chiffrées : chaque destinataire le fera avec les engagements.
     */
    public void deposerPolynome(String jeton, Polynome polynome) {
        exigerEtat(Etat.BROUILLON);
        Scrutateur s = scrutateurParJeton(jeton);
        if (etapePartage() != EtapePartage.POLYNOMES) {
            throw new RequeteRefusee("Le partage n'en est pas aux polynômes");
        }
        if (s.polynome != null) {
            throw new RequeteRefusee("Polynôme déjà déposé");
        }
        if (polynome.engagements().size() != seuil
                || !polynome.engagements().stream().allMatch(groupe::appartient)) {
            throw new RequeteRefusee("Il faut " + seuil + " engagements dans le groupe");
        }
        Set<Integer> attendus = new HashSet<>();
        for (int j = 1; j <= scrutateurs.size(); j++) {
            attendus.add(j);
        }
        if (!polynome.parts().keySet().equals(attendus)) {
            throw new RequeteRefusee("Il faut une part pour chaque scrutateur");
        }
        if (!Preuves.verifierConnaissance(
                groupe,
                s.cleCanal,
                polynome.signature(),
                Contextes.polynome(id, s.nom, polynome.engagements(), polynome.parts()))) {
            throw new RequeteRefusee("Signature du polynôme invalide");
        }
        s.polynome = polynome;
    }

    private List<List<BigInteger>> engagementsDeChacun() {
        return scrutateurs.stream().map(s -> s.polynome.engagements()).toList();
    }

    private void exigerPartageNonCommence() {
        if (scrutateurs.stream().anyMatch(s -> s.cleCanal != null)) {
            throw new RequeteRefusee("Le partage à seuil a commencé : scrutateurs et seuil figés");
        }
    }

    // ---- Autorité de certification -------------------------------------------------------------

    public List<String> electeursPourAutorite(String jeton) {
        exigerJetonAutorite(jeton);
        return List.copyOf(electeurs);
    }

    /** Enregistre un credential public par électeur, fournis par l'autorité de certification. */
    public void deposerCredentials(String jeton, Map<String, BigInteger> parElecteur) {
        exigerJetonAutorite(jeton);
        if (serveurCertification != null) {
            throw new RequeteRefusee("Les credentials viennent du serveur de credentials");
        }
        enregistrerCredentials(parElecteur);
    }

    // ---- Serveur de credentials ----------------------------------------------------------------

    /** Adresse du serveur de credentials et e-mail de l'opérateur qui recevra la graine. */
    public record ServeurCertification(String adresse, String emailOperateur) {}

    /** Confie les credentials à un serveur de credentials plutôt qu'à une autorité manuelle. */
    public void definirServeurCertification(String adresse, String emailOperateur) {
        exigerEtat(Etat.BROUILLON);
        if (!credentials.isEmpty()) {
            throw new RequeteRefusee("Credentials déjà déposés");
        }
        if (adresse.isBlank() || !emailOperateur.contains("@")) {
            throw new RequeteRefusee("Adresse du serveur ou e-mail de l'opérateur invalide");
        }
        serveurCertification = new ServeurCertification(adresse, emailOperateur);
    }

    public ServeurCertification serveurCertification() {
        return serveurCertification;
    }

    /** Électeurs et leur adresse, à envoyer au serveur de credentials. */
    public Map<String, String> electeursAContacter() {
        exigerEtat(Etat.BROUILLON);
        if (serveurCertification == null) {
            throw new RequeteRefusee("Aucun serveur de credentials défini");
        }
        if (!credentials.isEmpty()) {
            throw new RequeteRefusee("Credentials déjà déposés");
        }
        if (electeurs.isEmpty() || !emails.keySet().containsAll(electeurs)) {
            throw new RequeteRefusee("Il faut une adresse e-mail pour chaque électeur");
        }
        Map<String, String> aContacter = new LinkedHashMap<>();
        electeurs.forEach(e -> aContacter.put(e, emails.get(e)));
        return aContacter;
    }

    /** Enregistre les credentials du serveur, après avoir vérifié son certificat. */
    public void deposerCredentialsCertifies(
            Map<String, BigInteger> parElecteur, Certificat certificat) {
        if (serveurCertification == null) {
            throw new RequeteRefusee("Aucun serveur de credentials défini");
        }
        List<String> anomalies =
                Certificats.verifier(
                        groupe, certificat, id, parElecteur.values(), electeurs.size());
        if (!anomalies.isEmpty()) {
            throw new RequeteRefusee("Certificat refusé : " + String.join(", ", anomalies));
        }
        enregistrerCredentials(parElecteur);
        this.certificat = certificat;
    }

    public Certificat certificat() {
        return certificat;
    }

    private void enregistrerCredentials(Map<String, BigInteger> parElecteur) {
        exigerEtat(Etat.BROUILLON);
        if (!credentials.isEmpty()) {
            throw new RequeteRefusee("Credentials déjà déposés");
        }
        Map<String, BigInteger> normalises = new HashMap<>();
        parElecteur.forEach(
                (electeur, credential) -> normalises.put(normaliser(electeur), credential));
        if (!normalises.keySet().equals(electeurs)) {
            throw new RequeteRefusee("Il faut exactement un credential par électeur de la liste");
        }
        Set<BigInteger> distincts = new HashSet<>();
        for (BigInteger credential : normalises.values()) {
            if (!groupe.appartient(credential)
                    || credential.equals(BigInteger.ONE)
                    || !distincts.add(credential)) {
                throw new RequeteRefusee("Credential invalide ou en double");
            }
        }
        credentials.putAll(normalises);
    }

    // ---- Vote ----------------------------------------------------------------------------------

    /**
     * Dépose le bulletin de {@code electeur}, connecté à l'instant {@code connexion}. Un nouveau
     * bulletin du même électeur remplace le précédent.
     */
    public Recepisse deposerBulletin(String electeur, Instant connexion, Bulletin bulletin) {
        Etat courant = etat();
        boolean dansLaGrace = courant == Etat.CLOS && !connexion.isAfter(cloture);
        if (courant != Etat.OUVERT && !dansLaGrace) {
            throw new RequeteRefusee("Le vote n'est pas ouvert");
        }
        String normalise = normaliser(electeur);
        if (!electeurs.contains(normalise)) {
            throw new RequeteRefusee("Vous n'êtes pas sur la liste des électeurs");
        }
        String empreinte = bulletin.empreinte();
        if (empreintes.contains(empreinte)) {
            throw new RequeteRefusee("Bulletin déjà reçu");
        }
        if (!bulletin.estValide(groupe, cle, id)) {
            throw new RequeteRefusee("Bulletin mal formé : preuve ou signature invalide");
        }
        if (!bulletin.credential().equals(credentials.get(normalise))) {
            throw new RequeteRefusee("Ce credential n'est pas le vôtre");
        }
        Instant maintenant = horloge.instant();
        BulletinDepose precedent =
                urne.put(
                        bulletin.credential(), new BulletinDepose(bulletin, normalise, maintenant));
        empreintes.add(empreinte);
        emargement.put(normalise, maintenant);
        return new Recepisse(empreinte, precedent != null);
    }

    // ---- Fin de vie ----------------------------------------------------------------------------

    /** Efface les données personnelles (électeurs, émargement) et garde le résultat. */
    public void archiver() {
        exigerEtat(Etat.PUBLIE);
        electeurs.clear();
        emails.clear();
        emargement.clear();
        // Les credentials publics restent pour l'audit, sans le nom de leur électeur.
        Map<String, BigInteger> anonymes = new HashMap<>();
        int i = 0;
        for (BigInteger credential : credentials.values()) {
            anonymes.put("#" + i++, credential);
        }
        credentials.clear();
        credentials.putAll(anonymes);
        urne.replaceAll((c, d) -> new BulletinDepose(d.bulletin(), null, d.date()));
        dateArchivage = horloge.instant();
        etat = Etat.ARCHIVE;
    }

    // ---- Outils --------------------------------------------------------------------------------

    private void exigerEtat(Etat attendu) {
        Etat courant = etat();
        if (courant != attendu) {
            throw new RequeteRefusee("Action impossible dans l'état " + courant);
        }
    }

    private void exigerJetonAutorite(String jeton) {
        if (!memeJeton(jetonAutorite, jeton)) {
            throw new RequeteRefusee("Jeton de l'autorité de certification invalide");
        }
    }

    private static String normaliser(String identifiant) {
        return identifiant.strip().toLowerCase(Locale.ROOT);
    }

    /** Comparaison en temps constant : la durée ne dit pas combien de caractères sont justes. */
    static boolean memeJeton(String attendu, String recu) {
        return recu != null
                && MessageDigest.isEqual(
                        attendu.getBytes(StandardCharsets.UTF_8),
                        recu.getBytes(StandardCharsets.UTF_8));
    }

    /** Jeton aléatoire de 128 bits, en base64 sans remplissage. */
    static String jeton() {
        byte[] octets = new byte[16];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }
}
