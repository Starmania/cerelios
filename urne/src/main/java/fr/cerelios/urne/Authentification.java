package fr.cerelios.urne;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Comptes des utilisateurs, avec leurs rôles, et vérification de leur mot de passe. Seuls un sel
 * aléatoire et l'empreinte PBKDF2 du mot de passe sont conservés, jamais le mot de passe lui-même.
 */
public class Authentification {

    // Paramètres recommandés par l'OWASP pour PBKDF2-HMAC-SHA256.
    private static final int ITERATIONS = 600_000;
    private static final int OCTETS_SEL = 16;
    private static final int BITS_EMPREINTE = 256;

    private static final SecureRandom ALEA = new SecureRandom();
    private static final Logger JOURNAL = Logger.getLogger(Authentification.class.getName());

    private record Compte(byte[] sel, byte[] empreinte, Set<Role> roles) {}

    // Plusieurs clients peuvent se connecter en même temps.
    private final Map<String, Compte> comptes = new ConcurrentHashMap<>();

    /**
     * Crée le compte {@code identifiant} avec son mot de passe et ses rôles. Une personne peut en
     * cumuler plusieurs, mais l'administrateur et le scrutateur sont deux personnes distinctes
     * (dossier d'analyse, §6.9).
     */
    public void inscrire(String identifiant, String motDePasse, Set<Role> roles) {
        Objects.requireNonNull(identifiant, "identifiant");
        Objects.requireNonNull(motDePasse, "motDePasse");
        Set<Role> copie = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        if (copie.isEmpty()) {
            throw new IllegalArgumentException("Un compte a au moins un rôle");
        }
        if (copie.contains(Role.ADMINISTRATEUR) && copie.contains(Role.SCRUTATEUR)) {
            throw new IllegalArgumentException("Un administrateur ne peut pas être scrutateur");
        }
        byte[] sel = new byte[OCTETS_SEL];
        ALEA.nextBytes(sel);
        if (comptes.putIfAbsent(identifiant, new Compte(sel, empreinte(motDePasse, sel), copie)) != null) {
            throw new IllegalArgumentException("Identifiant déjà inscrit : " + identifiant);
        }
    }

    /**
     * Rôles du compte {@code identifiant} si le mot de passe est le bon, vide sinon. Chaque échec est
     * journalisé avec l'identifiant, jamais avec le mot de passe.
     */
    public Optional<Set<Role>> authentifier(String identifiant, String motDePasse) {
        Objects.requireNonNull(identifiant, "identifiant");
        Objects.requireNonNull(motDePasse, "motDePasse");
        Compte compte = comptes.get(identifiant);
        // Comparaison en temps constant : la durée ne dit pas combien d'octets sont justes.
        if (compte != null && MessageDigest.isEqual(compte.empreinte(), empreinte(motDePasse, compte.sel()))) {
            return Optional.of(compte.roles());
        }
        // L'identifiant vient du réseau : sans ses caractères de contrôle, il ne peut pas forger de ligne.
        JOURNAL.warning("Échec d'authentification pour l'identifiant " + identifiant.replaceAll("\\p{Cntrl}", "?"));
        return Optional.empty();
    }

    private static byte[] empreinte(String motDePasse, byte[] sel) {
        PBEKeySpec spec = new PBEKeySpec(motDePasse.toCharArray(), sel, ITERATIONS, BITS_EMPREINTE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 indisponible dans ce JDK", e);
        } finally {
            spec.clearPassword();
        }
    }
}
