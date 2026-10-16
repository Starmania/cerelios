package fr.cerelios.crypto;

/** Aucune somme de 0 à la borne ne correspond au chiffré et aux déchiffrements partiels reçus. */
public class DechiffrementImpossibleException extends Exception {

    public DechiffrementImpossibleException(String message) {
        super(message);
    }
}
