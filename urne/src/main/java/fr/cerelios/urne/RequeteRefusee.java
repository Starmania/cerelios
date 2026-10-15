package fr.cerelios.urne;

/** Requête refusée par l'urne ; le message est renvoyé tel quel au client. */
public class RequeteRefusee extends RuntimeException {

    public RequeteRefusee(String message) {
        super(message);
    }
}
