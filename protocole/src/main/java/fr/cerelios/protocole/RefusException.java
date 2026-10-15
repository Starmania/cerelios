package fr.cerelios.protocole;

/** L'urne a répondu par une erreur : requête refusée, avec la raison qu'elle donne. */
public class RefusException extends Exception {

    public RefusException(String message) {
        super(message);
    }
}
