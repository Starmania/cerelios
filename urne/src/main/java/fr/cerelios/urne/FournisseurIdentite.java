package fr.cerelios.urne;

import java.util.Optional;

/**
 * Fournisseur d'identité (IdP) qui authentifie électeurs et administrateurs. L'urne ne garde aucun
 * mot de passe : elle demande à l'IdP qui est la personne, puis vérifie elle-même ses droits.
 *
 * <p>Une implémentation réelle s'appuierait sur OIDC ou CAS ; {@link IdentitesLocales} sert au
 * développement et aux tests.
 */
public interface FournisseurIdentite {

    /**
     * Rend l'identité de {@code identifiant} si {@code secret} la prouve, vide sinon. Le sens de
     * {@code secret} dépend du fournisseur : mot de passe, code reçu, jeton d'accès…
     */
    Optional<Identite> authentifier(String identifiant, String secret);
}
