/**
 * Serveur de credentials : joue l'autorité de certification pour le compte d'un opérateur humain.
 *
 * <p>Il génère les credentials, n'en donne que la partie publique à l'urne et envoie la partie
 * secrète à chaque électeur. Il ne garde les secrets que chiffrés sous une clé dérivée d'une graine
 * qu'il remet à l'opérateur puis oublie : sans l'opérateur, il ne peut plus les relire.
 */
package fr.cerelios.certification;
