package fr.cerelios.urne;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Comptes des utilisateurs, avec leur rôle, et vérification de leur mot de passe. Seuls un sel
 * aléatoire et l'empreinte PBKDF2 du mot de passe sont conservés, jamais le mot de passe lui-même.
 */
public class Authentification {

    // Paramètres recommandés par l'OWASP pour PBKDF2-HMAC-SHA256.
    private static final int ITERATIONS = 600_000;
    private static final int OCTETS_SEL = 16;
    private static final int BITS_EMPREINTE = 256;

    private static final SecureRandom ALEA = new SecureRandom();
    private static final Logger JOURNAL = Logger.getLogger(Authentification.class.getName());

    private record Compte(byte[] sel, byte[] empreinte, Role role) {}

    // Plusieurs clients peuvent se connecter en même temps.
    private final Map<String, Compte> comptes = new ConcurrentHashMap<>();

    /** Crée le compte {@code identifiant} avec son mot de passe et son rôle. */
    public void inscrire(String identifiant, String motDePasse, Role role) {
        Objects.requireNonNull(identifiant, "identifiant");
        Objects.requireNonNull(motDePasse, "motDePasse");
        Objects.requireNonNull(role, "role");
        byte[] sel = new byte[OCTETS_SEL];
        ALEA.nextBytes(sel);
        if (comptes.putIfAbsent(identifiant, new Compte(sel, empreinte(motDePasse, sel), role)) != null) {
            throw new IllegalArgumentException("Identifiant déjà inscrit : " + identifiant);
        }
    }

    /**
     * Rôle du compte {@code identifiant} si le mot de passe est le bon, vide sinon. Chaque échec est
     * journalisé avec l'identifiant, jamais avec le mot de passe.
     */
    public Optional<Role> authentifier(String identifiant, String motDePasse) {
        Objects.requireNonNull(identifiant, "identifiant");
        Objects.requireNonNull(motDePasse, "motDePasse");
        Compte compte = comptes.get(identifiant);
        // Comparaison en temps constant : la durée ne dit pas combien d'octets sont justes.
        if (compte != null && MessageDigest.isEqual(compte.empreinte(), empreinte(motDePasse, compte.sel()))) {
            return Optional.of(compte.role());
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
