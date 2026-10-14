package fr.cerelios.urne;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Comptes des utilisateurs et vérification de leur mot de passe. Seuls un sel aléatoire et
 * l'empreinte PBKDF2 du mot de passe sont conservés, jamais le mot de passe lui-même.
 */
public class Authentification {

    // Paramètres recommandés par l'OWASP pour PBKDF2-HMAC-SHA256.
    private static final int ITERATIONS = 600_000;
    private static final int OCTETS_SEL = 16;
    private static final int BITS_EMPREINTE = 256;

    private static final SecureRandom ALEA = new SecureRandom();

    private record Compte(byte[] sel, byte[] empreinte) {}

    // Plusieurs clients peuvent se connecter en même temps.
    private final Map<String, Compte> comptes = new ConcurrentHashMap<>();

    /** Crée le compte {@code identifiant} avec son mot de passe. */
    public void inscrire(String identifiant, String motDePasse) {
        byte[] sel = new byte[OCTETS_SEL];
        ALEA.nextBytes(sel);
        if (comptes.putIfAbsent(identifiant, new Compte(sel, empreinte(motDePasse, sel))) != null) {
            throw new IllegalArgumentException("Identifiant déjà inscrit : " + identifiant);
        }
    }

    /** Vrai seulement si le compte {@code identifiant} existe et a ce mot de passe. */
    public boolean verifier(String identifiant, String motDePasse) {
        Compte compte = comptes.get(identifiant);
        // Comparaison en temps constant : la durée ne dit pas combien d'octets sont justes.
        return compte != null
                && MessageDigest.isEqual(compte.empreinte(), empreinte(motDePasse, compte.sel()));
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
