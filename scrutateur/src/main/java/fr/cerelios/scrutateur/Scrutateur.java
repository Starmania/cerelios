package fr.cerelios.scrutateur;

import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.octets;
import static fr.cerelios.protocole.Conversions.versProto;

import fr.cerelios.crypto.ChiffrementHybride;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Elgamal.Chiffre;
import fr.cerelios.crypto.Elgamal.PaireDeCles;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.crypto.Partage;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.Contextes;
import fr.cerelios.protocole.Conversions;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.messages.DeposerCle;
import fr.cerelios.protocole.messages.DeposerDechiffrement;
import fr.cerelios.protocole.messages.DeposerPolynome;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.EtatPartage;
import fr.cerelios.protocole.messages.Polynome;
import fr.cerelios.protocole.messages.PolynomeRecu;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import fr.cerelios.protocole.messages.ScrutateurPublic;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Programme du scrutateur : génère sa part de clé, puis déchiffre partiellement le total. La part
 * secrète ne quitte pas ce programme : elle est gardée dans un fichier que seul le scrutateur
 * détient.
 */
public class Scrutateur {

    private final ClientUrne urne;
    private final Groupe groupe;
    private final String referendum;
    private final String nom;
    private final String jeton;

    /** {@code referendum}, {@code nom} et {@code jeton} viennent de l'e-mail d'invitation. */
    public Scrutateur(ClientUrne urne, Groupe groupe, String referendum, String nom, String jeton) {
        this.urne = urne;
        this.groupe = groupe;
        this.referendum = referendum;
        this.nom = nom;
        this.jeton = jeton;
    }

    /**
     * Écrit une clé secrète tirée au hasard dans un nouveau fichier, en hexadécimal, lisible par
     * son seul propriétaire si le système le permet. Le scrutateur garde ce fichier : il sert à
     * déposer sa clé (ou, en mode à seuil, sa clé de canal) puis à déchiffrer.
     *
     * @throws java.nio.file.FileAlreadyExistsException si le fichier existe : jamais d'écrasement
     */
    public static void genererFichierSecret(Groupe groupe, Path fichier) throws IOException {
        enregistrer(fichier, Elgamal.keyGen(groupe).secrete());
    }

    /**
     * Mode où chaque scrutateur a sa propre clé : dépose la partie publique de la clé contenue dans
     * {@code fichierSecret}, avec la preuve qu'il en connaît la partie secrète.
     */
    public BigInteger deposerCle(Path fichierSecret) throws IOException, RefusException {
        PaireDeCles cles = relire(fichierSecret);
        urne.appeler(
                Requete.newBuilder()
                        .setJeton(jeton)
                        .setDeposerCle(
                                DeposerCle.newBuilder()
                                        .setReferendum(referendum)
                                        .setClePublique(octets(cles.publique()))
                                        .setPreuve(
                                                versProto(
                                                        Preuves.prouverConnaissance(
                                                                groupe,
                                                                cles,
                                                                Contextes.cleScrutateur(
                                                                        referendum, nom))))));
        return cles.publique();
    }

    /**
     * Vérifie que la part publique de {@code fichierSecret} est celle publiée pour ce scrutateur et
     * qu'elle entre dans la clé du référendum.
     */
    public boolean cleUtilisee(Path fichierSecret) throws IOException, RefusException {
        ReferendumPublic publique = referendumPublic();
        if (publique.getCle().isEmpty()) {
            return false;
        }
        PaireDeCles cles = cleDeDechiffrement(fichierSecret, publique);
        if (publique.getSeuil() > 0) {
            List<List<BigInteger>> engagements = new ArrayList<>();
            boolean trouvee = false;
            for (ScrutateurPublic s : publique.getScrutateursList()) {
                engagements.add(Conversions.entiers(s.getEngagementsList()));
                trouvee |=
                        s.getNom().equals(nom)
                                && entier(s.getClePublique()).equals(cles.publique());
            }
            return trouvee
                    && Partage.clePublique(groupe, engagements).equals(entier(publique.getCle()));
        }
        BigInteger produit = BigInteger.ONE;
        boolean trouvee = false;
        for (ScrutateurPublic s : publique.getScrutateursList()) {
            BigInteger part = entier(s.getClePublique());
            produit = produit.multiply(part).mod(groupe.p());
            trouvee |= s.getNom().equals(nom) && part.equals(cles.publique());
        }
        return trouvee && produit.equals(entier(publique.getCle()));
    }

    /** Déchiffre partiellement le total publié et dépose le résultat avec sa preuve. */
    public void dechiffrer(Path fichierSecret) throws IOException, RefusException {
        PaireDeCles cles = cleDeDechiffrement(fichierSecret, referendumPublic());
        DonneesPubliques donnees = donneesPubliques();
        if (!donnees.hasTotal()) {
            throw new RefusException("Le total n'est pas encore calculé");
        }
        Chiffre total = Conversions.depuisProto(donnees.getTotal());
        urne.appeler(
                Requete.newBuilder()
                        .setJeton(jeton)
                        .setDeposerDechiffrement(
                                DeposerDechiffrement.newBuilder()
                                        .setReferendum(referendum)
                                        .setDechiffrement(
                                                octets(
                                                        Elgamal.dechiffrementPartiel(
                                                                groupe, total, cles.secrete())))
                                        .setPreuve(
                                                versProto(
                                                        Preuves.prouverDechiffrement(
                                                                groupe,
                                                                cles,
                                                                total,
                                                                Contextes.dechiffrement(
                                                                        referendum, nom))))));
    }

    /** Déchiffrement partiel publié pour ce scrutateur, s'il y en a un. */
    public Optional<BigInteger> dechiffrementPublie() throws IOException, RefusException {
        return referendumPublic().getScrutateursList().stream()
                .filter(s -> s.getNom().equals(nom) && !s.getDechiffrement().isEmpty())
                .map(s -> entier(s.getDechiffrement()))
                .findFirst();
    }

    // ---- Partage à seuil -----------------------------------------------------------------------

    /**
     * Fait ce que ce scrutateur peut faire à l'étape courante du partage à seuil, puis rend l'étape
     * atteinte. À rappeler tant qu'elle n'est pas {@code TERMINE} : chaque étape attend que tous
     * les scrutateurs aient fini la précédente.
     *
     * <p>{@code fichierSecret}, créé par {@link #genererFichierSecret}, contient la clé de canal,
     * seul secret à conserver : la part de clé se recalcule à partir des parts chiffrées que l'urne
     * garde.
     */
    public EtatPartage.Etape avancerPartage(Path fichierSecret) throws IOException, RefusException {
        EtatPartage etat = partage();
        ScrutateurPublic moi = moi(etat);
        switch (etat.getEtape()) {
            case CANAUX -> {
                if (moi.getCleCanal().isEmpty()) {
                    deposerCleCanal(relire(fichierSecret));
                }
            }
            case POLYNOMES -> {
                if (etat.getPolynomesList().stream().noneMatch(p -> p.getEmetteur().equals(nom))) {
                    deposerPolynome(etat, relire(fichierSecret));
                }
            }
            case CLES_PARTIELLES -> {
                if (moi.getClePublique().isEmpty()) {
                    PaireDeCles part = recomposer(etat, relire(fichierSecret));
                    urne.appeler(
                            Requete.newBuilder()
                                    .setJeton(jeton)
                                    .setDeposerCle(
                                            DeposerCle.newBuilder()
                                                    .setReferendum(referendum)
                                                    .setClePublique(octets(part.publique()))
                                                    .setPreuve(
                                                            versProto(
                                                                    Preuves.prouverConnaissance(
                                                                            groupe,
                                                                            part,
                                                                            Contextes.cleScrutateur(
                                                                                    referendum,
                                                                                    nom))))));
                }
            }
            default -> {}
        }
        return partage().getEtape();
    }

    private void deposerCleCanal(PaireDeCles canal) throws IOException, RefusException {
        urne.appeler(
                Requete.newBuilder()
                        .setJeton(jeton)
                        .setDeposerCleCanal(
                                DeposerCle.newBuilder()
                                        .setReferendum(referendum)
                                        .setClePublique(octets(canal.publique()))
                                        .setPreuve(
                                                versProto(
                                                        Preuves.prouverConnaissance(
                                                                groupe,
                                                                canal,
                                                                Contextes.cleCanal(
                                                                        referendum, nom))))));
    }

    /** Tire un polynôme et chiffre sa valeur en j pour chaque scrutateur j, soi compris. */
    private void deposerPolynome(EtatPartage etat, PaireDeCles canal)
            throws IOException, RefusException {
        Partage.Polynome f = Partage.tirer(groupe, etat.getSeuil());
        List<BigInteger> engagements = Partage.engagements(groupe, f);
        SortedMap<Integer, ChiffrementHybride.Message> parts = new TreeMap<>();
        for (ScrutateurPublic s : etat.getScrutateursList()) {
            int j = s.getIndice();
            parts.put(
                    j,
                    ChiffrementHybride.chiffrer(
                            groupe,
                            entier(s.getCleCanal()),
                            Partage.evaluer(groupe, f, j).toByteArray(),
                            Contextes.part(referendum, nom, j)));
        }
        Polynome.Builder polynome =
                Polynome.newBuilder()
                        .setSignature(
                                versProto(
                                        Preuves.prouverConnaissance(
                                                groupe,
                                                canal,
                                                Contextes.polynome(
                                                        referendum, nom, engagements, parts))));
        engagements.forEach(e -> polynome.addEngagements(octets(e)));
        parts.forEach((j, m) -> polynome.addParts(Conversions.versProto(j, m)));
        urne.appeler(
                Requete.newBuilder()
                        .setJeton(jeton)
                        .setDeposerPolynome(
                                DeposerPolynome.newBuilder()
                                        .setReferendum(referendum)
                                        .setPolynome(polynome)));
    }

    /**
     * Recalcule la part x_j : déchiffre la part reçue de chaque scrutateur, vérifie qu'elle
     * correspond à ses engagements, et les additionne.
     *
     * @throws RefusException si une part est invalide : le partage doit alors être recommencé
     */
    private PaireDeCles recomposer(EtatPartage etat, PaireDeCles canal) throws RefusException {
        int j = etat.getIndice();
        if (etat.getPolynomesCount() != etat.getScrutateursCount()) {
            throw new RefusException("Tous les polynômes ne sont pas encore déposés");
        }
        BigInteger x = BigInteger.ZERO;
        for (PolynomeRecu recu : etat.getPolynomesList()) {
            String emetteur = recu.getEmetteur();
            ScrutateurPublic auteur =
                    etat.getScrutateursList().stream()
                            .filter(s -> s.getNom().equals(emetteur))
                            .findFirst()
                            .orElseThrow(
                                    () -> new RefusException("Émetteur inconnu : " + emetteur));
            List<BigInteger> engagements =
                    Conversions.entiers(recu.getPolynome().getEngagementsList());
            SortedMap<Integer, ChiffrementHybride.Message> parts =
                    Conversions.parts(recu.getPolynome());
            // La signature empêche l'urne de substituer un polynôme ou une part.
            if (!Preuves.verifierConnaissance(
                    groupe,
                    entier(auteur.getCleCanal()),
                    Conversions.depuisProto(recu.getPolynome().getSignature()),
                    Contextes.polynome(referendum, emetteur, engagements, parts))) {
                throw new RefusException("Polynôme de " + emetteur + " mal signé");
            }
            ChiffrementHybride.Message message = parts.get(j);
            BigInteger part =
                    message == null
                            ? null
                            : ChiffrementHybride.dechiffrer(
                                            groupe,
                                            canal.secrete(),
                                            message,
                                            Contextes.part(referendum, emetteur, j))
                                    .map(octets -> new BigInteger(octets))
                                    .orElse(null);
            if (part == null || !Partage.verifierPart(groupe, engagements, j, part)) {
                throw new RefusException("Part invalide reçue de " + emetteur);
            }
            x = x.add(part).mod(groupe.q());
        }
        return Elgamal.depuisSecrete(groupe, x);
    }

    /** Clé qui déchiffre : la part secrète du fichier, ou en mode à seuil la part recomposée. */
    private PaireDeCles cleDeDechiffrement(Path fichierSecret, ReferendumPublic publique)
            throws IOException, RefusException {
        PaireDeCles fichier = relire(fichierSecret);
        return publique.getSeuil() == 0 ? fichier : recomposer(partage(), fichier);
    }

    private EtatPartage partage() throws IOException, RefusException {
        return urne.appeler(
                        Requete.newBuilder()
                                .setJeton(jeton)
                                .setObtenirPartage(
                                        Referendum.newBuilder().setReferendum(referendum)))
                .getPartage();
    }

    private ScrutateurPublic moi(EtatPartage etat) throws RefusException {
        return etat.getScrutateursList().stream()
                .filter(s -> s.getIndice() == etat.getIndice())
                .findFirst()
                .orElseThrow(() -> new RefusException("Scrutateur absent du partage"));
    }

    private ReferendumPublic referendumPublic() throws IOException, RefusException {
        return urne.appeler(
                        Requete.newBuilder()
                                .setObtenirReferendum(
                                        Referendum.newBuilder().setReferendum(referendum)))
                .getReferendum();
    }

    private DonneesPubliques donneesPubliques() throws IOException, RefusException {
        return urne.appeler(
                        Requete.newBuilder()
                                .setObtenirDonneesPubliques(
                                        Referendum.newBuilder().setReferendum(referendum)))
                .getDonneesPubliques();
    }

    /**
     * Relit la clé d'un fichier secret : un nombre hexadécimal, espaces autour ignorés.
     *
     * @throws IOException si le fichier manque ou ne contient pas une clé valide
     */
    private PaireDeCles relire(Path fichierSecret) throws IOException {
        String contenu = Files.readString(fichierSecret).strip();
        try {
            return Elgamal.depuisSecrete(groupe, new BigInteger(contenu, 16));
        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "Le fichier "
                            + fichierSecret.getFileName()
                            + " ne contient pas une clé valide");
        }
    }

    /**
     * Écrit la part secrète en hexadécimal, lisible par son seul propriétaire si le système le
     * permet.
     */
    private static void enregistrer(Path fichier, BigInteger secrete) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(
                    fichier,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(fichier);
        }
        Files.writeString(fichier, secrete.toString(16) + "\n");
    }
}
