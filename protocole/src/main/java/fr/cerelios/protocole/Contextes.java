package fr.cerelios.protocole;

/**
 * Contextes des preuves échangées : le même texte doit être utilisé par celui qui prouve et par
 * ceux qui vérifient, ici l'urne et les auditeurs.
 */
public final class Contextes {

    private Contextes() {}

    /** Preuve de connaissance de la part secrète du scrutateur {@code nom}. */
    public static String cleScrutateur(String referendum, String nom) {
        return referendum + "/cle/" + nom;
    }

    /** Preuve du déchiffrement partiel du scrutateur {@code nom}. */
    public static String dechiffrement(String referendum, String nom) {
        return referendum + "/dechiffrement/" + nom;
    }
}
