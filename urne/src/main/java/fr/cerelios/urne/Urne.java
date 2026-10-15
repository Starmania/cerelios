package fr.cerelios.urne;

import static fr.cerelios.protocole.Conversions.depuisProto;
import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.octets;
import static fr.cerelios.protocole.Conversions.versProto;

import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.Conversions;
import fr.cerelios.protocole.ServiceMail;
import fr.cerelios.protocole.messages.CredentialPublic;
import fr.cerelios.protocole.messages.Dates;
import fr.cerelios.protocole.messages.DeposerCle;
import fr.cerelios.protocole.messages.DeposerPolynome;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Electeurs;
import fr.cerelios.protocole.messages.Emargement;
import fr.cerelios.protocole.messages.Erreur;
import fr.cerelios.protocole.messages.EtatPartage;
import fr.cerelios.protocole.messages.EvenementScellement;
import fr.cerelios.protocole.messages.Identifiant;
import fr.cerelios.protocole.messages.Jeton;
import fr.cerelios.protocole.messages.ListeReferendums;
import fr.cerelios.protocole.messages.Polynome;
import fr.cerelios.protocole.messages.PolynomeRecu;
import fr.cerelios.protocole.messages.Recepisse;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Reponse;
import fr.cerelios.protocole.messages.Requete;
import fr.cerelios.protocole.messages.Resultat;
import fr.cerelios.protocole.messages.ScrutateurPublic;
import fr.cerelios.protocole.messages.Statut;
import fr.cerelios.protocole.messages.Vide;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * L'urne : traite chaque requête reçue et rend la réponse. Ne connaît aucune clé secrète, ni des
 * scrutateurs, ni des électeurs.
 */
public class Urne {

    /** Durée de vie d'une session ouverte par {@link Requete#hasConnexion()}. */
    static final Duration DUREE_SESSION = Duration.ofHours(24);

    /** Délai avant l'archivage automatique d'un référendum publié. */
    static final Duration AVANT_ARCHIVAGE = Duration.ofDays(7);

    /** Délai avant la suppression d'un brouillon abandonné ou d'un référendum archivé. */
    static final Duration AVANT_SUPPRESSION = Duration.ofDays(365);

    private static final System.Logger JOURNAL = System.getLogger(Urne.class.getName());

    private record Session(Identite identite, Instant debut) {}

    private final FournisseurIdentite idp;
    private final ServiceMail mail;
    private final Clock horloge;
    private final Groupe groupe;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Referundum> referendums = new ConcurrentHashMap<>();

    public Urne(FournisseurIdentite idp, ServiceMail mail, Clock horloge) {
        this(idp, mail, horloge, Groupe.RFC_3526);
    }

    public Urne(FournisseurIdentite idp, ServiceMail mail, Clock horloge, Groupe groupe) {
        this.idp = idp;
        this.mail = mail;
        this.horloge = horloge;
        this.groupe = groupe;
    }

    /** Traite une requête ; toute erreur devient une réponse {@link Erreur}. */
    public Reponse traiter(Requete requete) {
        try {
            return executer(requete);
        } catch (RequeteRefusee | IllegalArgumentException e) {
            return erreur(e.getMessage());
        } catch (RuntimeException e) {
            JOURNAL.log(System.Logger.Level.ERROR, "Erreur inattendue", e);
            return erreur("Erreur interne de l'urne");
        }
    }

    private Reponse executer(Requete r) {
        String jeton = r.getJeton();
        return switch (r.getContenuCase()) {
            case CONNEXION ->
                    connexion(r.getConnexion().getIdentifiant(), r.getConnexion().getSecret());
            case CREER_REFERENDUM -> creer(jeton, r.getCreerReferendum().getQuestion());
            case DEFINIR_QUESTION -> {
                var m = r.getDefinirQuestion();
                yield commeProprietaire(
                        jeton, m.getReferendum(), ref -> ref.definirQuestion(m.getQuestion()));
            }
            case DEFINIR_ELECTEURS -> {
                var m = r.getDefinirElecteurs();
                yield commeProprietaire(
                        jeton,
                        m.getReferendum(),
                        ref -> ref.definirElecteurs(m.getElecteursList()));
            }
            case DEFINIR_DATES -> {
                var m = r.getDefinirDates();
                Dates d = m.getDates();
                yield commeProprietaire(
                        jeton,
                        m.getReferendum(),
                        ref ->
                                ref.definirDates(
                                        Instant.ofEpochMilli(d.getOuverture()),
                                        Instant.ofEpochMilli(d.getCloture()),
                                        Instant.ofEpochMilli(d.getPublication()),
                                        Duration.ofMillis(d.getDelaiGrace())));
            }
            case AJOUTER_SCRUTATEUR -> ajouterScrutateur(jeton, r.getAjouterScrutateur());
            case AJOUTER_PROPRIETAIRE -> {
                var m = r.getAjouterProprietaire();
                yield commeProprietaire(
                        jeton,
                        m.getReferendum(),
                        ref -> ref.ajouterProprietaire(m.getIdentifiant()));
            }
            case OBTENIR_JETON_AUTORITE -> {
                Referundum ref = proprietaire(jeton, r.getObtenirJetonAutorite().getReferendum());
                yield Reponse.newBuilder()
                        .setJeton(Jeton.newBuilder().setJeton(ref.jetonAutorite()))
                        .build();
            }
            case OBTENIR_STATUT -> {
                Referundum ref = proprietaire(jeton, r.getObtenirStatut().getReferendum());
                synchronized (ref) {
                    yield Reponse.newBuilder()
                            .setStatut(Statut.newBuilder().addAllManques(ref.manques()))
                            .build();
                }
            }
            case VALIDER ->
                    commeProprietaire(jeton, r.getValider().getReferendum(), Referundum::valider);
            case SCELLER ->
                    commeProprietaire(jeton, r.getSceller().getReferendum(), Referundum::sceller);
            case OBTENIR_EMARGEMENT -> emargement(jeton, r.getObtenirEmargement().getReferendum());
            case ARCHIVER ->
                    commeProprietaire(jeton, r.getArchiver().getReferendum(), Referundum::archiver);
            case SUPPRIMER -> {
                Referundum ref = proprietaire(jeton, r.getSupprimer().getReferendum());
                referendums.remove(ref.id());
                yield ok();
            }
            case LISTER_MES_REFERENDUMS -> mesReferendums(jeton);
            case DEFINIR_SEUIL ->
                    commeProprietaire(
                            jeton,
                            r.getDefinirSeuil().getReferendum(),
                            ref -> ref.definirSeuil(r.getDefinirSeuil().getSeuil()));
            case DEPOSER_CLE_CANAL -> deposerCleCanal(jeton, r.getDeposerCleCanal());
            case DEPOSER_POLYNOME -> deposerPolynome(jeton, r.getDeposerPolynome());
            case OBTENIR_PARTAGE -> partage(jeton, r.getObtenirPartage().getReferendum());
            case DEPOSER_CLE -> {
                var m = r.getDeposerCle();
                yield sur(
                        m.getReferendum(),
                        ref ->
                                ref.deposerCle(
                                        jeton,
                                        entier(m.getClePublique()),
                                        depuisProto(m.getPreuve())));
            }
            case DEPOSER_DECHIFFREMENT -> {
                var m = r.getDeposerDechiffrement();
                yield sur(
                        m.getReferendum(),
                        ref ->
                                ref.deposerDechiffrement(
                                        jeton,
                                        entier(m.getDechiffrement()),
                                        depuisProto(m.getPreuve())));
            }
            case OBTENIR_ELECTEURS -> {
                Referundum ref = referendum(r.getObtenirElecteurs().getReferendum());
                synchronized (ref) {
                    yield Reponse.newBuilder()
                            .setElecteurs(
                                    Electeurs.newBuilder()
                                            .addAllElecteurs(ref.electeursPourAutorite(jeton)))
                            .build();
                }
            }
            case DEPOSER_CREDENTIALS -> {
                var m = r.getDeposerCredentials();
                Map<String, BigInteger> parElecteur = new LinkedHashMap<>();
                for (CredentialPublic c : m.getCredentialsList()) {
                    if (parElecteur.put(c.getElecteur(), entier(c.getCredential())) != null) {
                        throw new RequeteRefusee("Deux credentials pour " + c.getElecteur());
                    }
                }
                yield sur(m.getReferendum(), ref -> ref.deposerCredentials(jeton, parElecteur));
            }
            case OBTENIR_REFERENDUM -> {
                Referundum ref = referendum(r.getObtenirReferendum().getReferendum());
                synchronized (ref) {
                    yield Reponse.newBuilder().setReferendum(versPublic(ref)).build();
                }
            }
            case DEPOSER_BULLETIN -> deposerBulletin(jeton, r.getDeposerBulletin());
            case OBTENIR_DONNEES_PUBLIQUES ->
                    donneesPubliques(r.getObtenirDonneesPubliques().getReferendum());
            case CONTENU_NOT_SET -> throw new RequeteRefusee("Requête vide");
        };
    }

    // ---- Sessions ------------------------------------------------------------------------------

    private Reponse connexion(String identifiant, String secret) {
        Identite identite =
                idp.authentifier(identifiant, secret)
                        .orElseThrow(() -> new RequeteRefusee("Identifiant ou secret incorrect"));
        String jeton = Referundum.jeton();
        sessions.put(jeton, new Session(identite, horloge.instant()));
        return Reponse.newBuilder().setJeton(Jeton.newBuilder().setJeton(jeton)).build();
    }

    private Session session(String jeton) {
        Session session = sessions.get(jeton);
        if (session == null) {
            throw new RequeteRefusee("Session inconnue : connectez-vous");
        }
        if (horloge.instant().isAfter(session.debut().plus(DUREE_SESSION))) {
            sessions.remove(jeton);
            throw new RequeteRefusee("Session expirée : reconnectez-vous");
        }
        return session;
    }

    // ---- Administration ------------------------------------------------------------------------

    private Reponse creer(String jeton, String question) {
        Identite identite = session(jeton).identite();
        String id = Referundum.jeton();
        referendums.put(id, new Referundum(id, question, identite.identifiant(), groupe, horloge));
        return Reponse.newBuilder().setIdentifiant(Identifiant.newBuilder().setId(id)).build();
    }

    private Reponse ajouterScrutateur(
            String jeton, fr.cerelios.protocole.messages.AjouterScrutateur m) {
        Referundum ref = proprietaire(jeton, m.getReferendum());
        String jetonScrutateur;
        synchronized (ref) {
            jetonScrutateur = ref.ajouterScrutateur(m.getNom(), m.getEmail());
        }
        mail.envoyer(
                m.getEmail(),
                "Vous êtes scrutateur du référendum « " + ref.question() + " »",
                "Ouvrez l'application Cerelios en tant que scrutateur et saisissez :\n"
                        + "  référendum : "
                        + ref.id()
                        + "\n  nom : "
                        + m.getNom()
                        + "\n  jeton : "
                        + jetonScrutateur
                        + "\nGardez ce jeton secret : il vous identifie auprès de l'urne.");
        return ok();
    }

    private Reponse emargement(String jeton, String id) {
        Referundum ref = proprietaire(jeton, id);
        Emargement.Builder emargement = Emargement.newBuilder();
        synchronized (ref) {
            ref.emargement().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            e ->
                                    emargement.addLignes(
                                            Emargement.Ligne.newBuilder()
                                                    .setElecteur(e.getKey())
                                                    .setDate(e.getValue().toEpochMilli())));
        }
        return Reponse.newBuilder().setEmargement(emargement).build();
    }

    private Reponse mesReferendums(String jeton) {
        String identifiant = session(jeton).identite().identifiant();
        ListeReferendums.Builder liste = ListeReferendums.newBuilder();
        referendums.values().stream()
                .sorted(Comparator.comparing(Referundum::creation))
                .forEach(
                        ref -> {
                            synchronized (ref) {
                                if (ref.estProprietaire(identifiant)) {
                                    liste.addReferendums(versPublic(ref));
                                }
                            }
                        });
        return Reponse.newBuilder().setReferendums(liste).build();
    }

    // ---- Partage à seuil -----------------------------------------------------------------------

    private Reponse deposerCleCanal(String jeton, DeposerCle m) {
        return sur(
                m.getReferendum(),
                ref ->
                        ref.deposerCleCanal(
                                jeton, entier(m.getClePublique()), depuisProto(m.getPreuve())));
    }

    private Reponse deposerPolynome(String jeton, DeposerPolynome m) {
        Polynome p = m.getPolynome();
        Referundum.Polynome polynome =
                new Referundum.Polynome(
                        Conversions.entiers(p.getEngagementsList()),
                        Conversions.parts(p),
                        depuisProto(p.getSignature()));
        return sur(m.getReferendum(), ref -> ref.deposerPolynome(jeton, polynome));
    }

    /** Ce qu'un scrutateur doit savoir pour avancer dans le partage, polynômes de tous compris. */
    private Reponse partage(String jeton, String id) {
        Referundum ref = referendum(id);
        EtatPartage.Builder etat = EtatPartage.newBuilder();
        synchronized (ref) {
            Referundum.Scrutateur moi = ref.scrutateurParJeton(jeton);
            etat.setIndice(ref.indice(moi))
                    .setSeuil(ref.seuil())
                    .setEtape(EtatPartage.Etape.valueOf(ref.etapePartage().name()))
                    .addAllScrutateurs(versPublic(ref).getScrutateursList());
            for (Referundum.Scrutateur s : ref.scrutateurs()) {
                if (s.polynome() != null) {
                    etat.addPolynomes(
                            PolynomeRecu.newBuilder()
                                    .setEmetteur(s.nom())
                                    .setPolynome(polynomeVersProto(s.polynome())));
                }
            }
        }
        return Reponse.newBuilder().setPartage(etat).build();
    }

    private static Polynome polynomeVersProto(Referundum.Polynome polynome) {
        Polynome.Builder p = Polynome.newBuilder().setSignature(versProto(polynome.signature()));
        polynome.engagements().forEach(e -> p.addEngagements(octets(e)));
        polynome.parts().forEach((j, part) -> p.addParts(Conversions.versProto(j, part)));
        return p.build();
    }

    // ---- Vote ----------------------------------------------------------------------------------

    private Reponse deposerBulletin(
            String jeton, fr.cerelios.protocole.messages.DeposerBulletin m) {
        Session session = session(jeton);
        Referundum ref = referendum(m.getReferendum());
        fr.cerelios.crypto.Bulletin bulletin = depuisProto(m.getBulletin());
        Referundum.Recepisse recepisse;
        synchronized (ref) {
            recepisse =
                    ref.deposerBulletin(
                            session.identite().identifiant(), session.debut(), bulletin);
        }
        if (session.identite().email() != null) {
            mail.envoyer(
                    session.identite().email(),
                    "Votre vote au référendum « " + ref.question() + " »",
                    "Votre bulletin a été enregistré"
                            + (recepisse.revote() ? " et remplace le précédent" : "")
                            + ".\nSon traceur est "
                            + recepisse.empreinte()
                            + " : vous pouvez le retrouver dans l'urne publiée.");
        }
        return Reponse.newBuilder()
                .setRecepisse(
                        Recepisse.newBuilder()
                                .setEmpreinte(recepisse.empreinte())
                                .setRevote(recepisse.revote()))
                .build();
    }

    // ---- Données publiques ---------------------------------------------------------------------

    private Reponse donneesPubliques(String id) {
        Referundum ref = referendum(id);
        DonneesPubliques.Builder donnees = DonneesPubliques.newBuilder();
        synchronized (ref) {
            donnees.setReferendum(versPublic(ref));
            ref.credentialsPublics().forEach(c -> donnees.addCredentials(octets(c)));
            ref.bulletins().forEach(b -> donnees.addBulletins(versProto(b)));
            if (ref.total() != null) {
                donnees.setTotal(versProto(ref.total()));
            }
            for (Referundum.Scellement s : ref.scellements()) {
                donnees.addScellements(
                        EvenementScellement.newBuilder()
                                .setDate(s.date().toEpochMilli())
                                .setDates(
                                        dates(s.ouverture(), s.cloture(), s.publication(), null)));
            }
        }
        return Reponse.newBuilder().setDonneesPubliques(donnees).build();
    }

    private static ReferendumPublic versPublic(Referundum ref) {
        ReferendumPublic.Builder p =
                ReferendumPublic.newBuilder()
                        .setId(ref.id())
                        .setQuestion(ref.question())
                        .setEtat(fr.cerelios.protocole.messages.Etat.valueOf(ref.etat().name()))
                        .setNombreElecteurs(ref.nombreElecteurs())
                        .setNombreBulletins(ref.nombreBulletins())
                        .setScelle(ref.scelle())
                        .setSeuil(ref.seuil());
        if (ref.ouverture() != null) {
            p.setDates(dates(ref.ouverture(), ref.cloture(), ref.publication(), ref.delaiGrace()));
        }
        if (ref.cle() != null) {
            p.setCle(octets(ref.cle()));
        }
        for (Referundum.Scrutateur s : ref.scrutateurs()) {
            ScrutateurPublic.Builder sp =
                    ScrutateurPublic.newBuilder().setNom(s.nom()).setIndice(ref.indice(s));
            if (s.cleCanal() != null) {
                sp.setCleCanal(octets(s.cleCanal())).setPreuveCanal(versProto(s.preuveCanal()));
            }
            if (s.polynome() != null) {
                s.polynome().engagements().forEach(e -> sp.addEngagements(octets(e)));
            }
            if (s.clePublique() != null) {
                sp.setClePublique(octets(s.clePublique())).setPreuveCle(versProto(s.preuveCle()));
            }
            if (s.dechiffrement() != null) {
                sp.setDechiffrement(octets(s.dechiffrement()))
                        .setPreuveDechiffrement(versProto(s.preuveDechiffrement()));
            }
            p.addScrutateurs(sp);
        }
        if (ref.resultat() != null) {
            p.setResultat(
                    Resultat.newBuilder()
                            .setOui(ref.resultat().oui())
                            .setNon(ref.resultat().non()));
        }
        return p.build();
    }

    private static Dates dates(
            Instant ouverture, Instant cloture, Instant publication, Duration grace) {
        Dates.Builder d =
                Dates.newBuilder()
                        .setOuverture(ouverture.toEpochMilli())
                        .setCloture(cloture.toEpochMilli())
                        .setPublication(publication.toEpochMilli());
        if (grace != null) {
            d.setDelaiGrace(grace.toMillis());
        }
        return d.build();
    }

    // ---- Politique de conservation -------------------------------------------------------------

    /**
     * Archive les référendums publiés depuis {@link #AVANT_ARCHIVAGE} et supprime les brouillons
     * abandonnés et les archives de plus de {@link #AVANT_SUPPRESSION}. Seule tâche périodique de
     * l'urne : personne ne consulte un référendum abandonné, une lecture ne la déclencherait pas.
     */
    public void appliquerPolitiqueDonnees() {
        Instant maintenant = horloge.instant();
        for (Referundum ref : referendums.values()) {
            synchronized (ref) {
                Etat etat = ref.etat();
                if (etat == Etat.PUBLIE
                        && maintenant.isAfter(ref.datePublication().plus(AVANT_ARCHIVAGE))) {
                    ref.archiver();
                    JOURNAL.log(System.Logger.Level.INFO, "Référendum {0} archivé", ref.id());
                } else if ((etat == Etat.BROUILLON
                                && maintenant.isAfter(ref.creation().plus(AVANT_SUPPRESSION)))
                        || (etat == Etat.ARCHIVE
                                && maintenant.isAfter(
                                        ref.dateArchivage().plus(AVANT_SUPPRESSION)))) {
                    referendums.remove(ref.id());
                    JOURNAL.log(System.Logger.Level.INFO, "Référendum {0} supprimé", ref.id());
                }
            }
        }
    }

    // ---- Outils --------------------------------------------------------------------------------

    private Referundum referendum(String id) {
        Referundum ref = referendums.get(id);
        if (ref == null) {
            throw new RequeteRefusee("Référendum inconnu : " + id);
        }
        return ref;
    }

    private Referundum proprietaire(String jeton, String id) {
        String identifiant = session(jeton).identite().identifiant();
        Referundum ref = referendum(id);
        synchronized (ref) {
            if (!ref.estProprietaire(identifiant)) {
                throw new RequeteRefusee("Vous n'administrez pas ce référendum");
            }
        }
        return ref;
    }

    private Reponse commeProprietaire(String jeton, String id, Consumer<Referundum> action) {
        Referundum ref = proprietaire(jeton, id);
        synchronized (ref) {
            action.accept(ref);
        }
        return ok();
    }

    private Reponse sur(String id, Consumer<Referundum> action) {
        Referundum ref = referendum(id);
        synchronized (ref) {
            action.accept(ref);
        }
        return ok();
    }

    private static Reponse ok() {
        return Reponse.newBuilder().setOk(Vide.getDefaultInstance()).build();
    }

    private static Reponse erreur(String message) {
        return Reponse.newBuilder().setErreur(Erreur.newBuilder().setMessage(message)).build();
    }
}
