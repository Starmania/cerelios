package fr.cerelios.protocole;

import fr.cerelios.crypto.ChiffrementHybride;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedMap;

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

    /** Preuve de connaissance de la clé de canal du scrutateur {@code nom} (mode à seuil). */
    public static String cleCanal(String referendum, String nom) {
        return referendum + "/canal/" + nom;
    }

    /**
     * Chiffrement de la part que le scrutateur {@code emetteur} envoie au scrutateur d'indice
     * {@code destinataire}.
     */
    public static String part(String referendum, String emetteur, int destinataire) {
        return referendum + "/part/" + emetteur + "/" + destinataire;
    }

    /**
     * Signature d'un polynôme par son scrutateur : couvre les engagements et toutes les parts
     * chiffrées, pour que l'urne ne puisse ni en substituer ni en retirer.
     */
    public static String polynome(
            String referendum,
            String nom,
            List<BigInteger> engagements,
            SortedMap<Integer, ChiffrementHybride.Message> parts) {
        StringBuilder texte = new StringBuilder(referendum).append("/polynome/").append(nom);
        for (BigInteger e : engagements) {
            texte.append('/').append(e.toString(16));
        }
        HexFormat hex = HexFormat.of();
        parts.forEach(
                (j, m) ->
                        texte.append('/')
                                .append(j)
                                .append(':')
                                .append(m.r().toString(16))
                                .append(':')
                                .append(hex.formatHex(m.nonce()))
                                .append(':')
                                .append(hex.formatHex(m.donnees())));
        return texte.toString();
    }

    /** Preuve du déchiffrement partiel du scrutateur {@code nom}. */
    public static String dechiffrement(String referendum, String nom) {
        return referendum + "/dechiffrement/" + nom;
    }
}
