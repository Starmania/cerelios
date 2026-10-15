package fr.cerelios.urne;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fournisseur d'identité local, pour le développement et les tests : comptes et mots de passe
 * hachés gardés par {@link Authentification}, plus l'adresse e-mail de chacun.
 */
public class IdentitesLocales implements FournisseurIdentite {

    private final Authentification authentification = new Authentification();
    private final Map<String, String> emails = new ConcurrentHashMap<>();

    /** Crée un compte. */
    public void inscrire(String identifiant, String motDePasse, String email) {
        authentification.inscrire(identifiant, motDePasse);
        emails.put(identifiant, email);
    }

    @Override
    public Optional<Identite> authentifier(String identifiant, String secret) {
        if (!authentification.verifier(identifiant, secret)) {
            return Optional.empty();
        }
        return Optional.of(new Identite(identifiant, emails.get(identifiant)));
    }
}
