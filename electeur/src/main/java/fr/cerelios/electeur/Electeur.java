package fr.cerelios.electeur;

import static fr.cerelios.protocole.Conversions.entier;
import static fr.cerelios.protocole.Conversions.versProto;

import fr.cerelios.crypto.Bulletin;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.Conversions;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.messages.Connexion;
import fr.cerelios.protocole.messages.DeposerBulletin;
import fr.cerelios.protocole.messages.Recepisse;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.ReferendumPublic;
import fr.cerelios.protocole.messages.Requete;
import java.io.IOException;
import java.math.BigInteger;

/** Client de l'électeur : chiffre le vote sur son poste, le signe et l'envoie à l'urne. */
public class Electeur {

    private final ClientUrne urne;
    private final Groupe groupe;

    public Electeur(ClientUrne urne, Groupe groupe) {
        this.urne = urne;
        this.groupe = groupe;
    }

    /** Ouvre une session auprès du fournisseur d'identité de l'urne. */
    public void connecter(String identifiant, String secret) throws IOException, RefusException {
        String jeton =
                urne.appeler(
                                Requete.newBuilder()
                                        .setConnexion(
                                                Connexion.newBuilder()
                                                        .setIdentifiant(identifiant)
                                                        .setSecret(secret)))
                        .getJeton()
                        .getJeton();
        urne.utiliserJeton(jeton);
    }

    public ReferendumPublic referendum(String id) throws IOException, RefusException {
        return urne.appeler(
                        Requete.newBuilder()
                                .setObtenirReferendum(Referendum.newBuilder().setReferendum(id)))
                .getReferendum();
    }

    /**
     * Vote Oui ou Non avec le credential reçu de l'autorité de certification (sa partie secrète, en
     * hexadécimal). Rend le récépissé : le traceur du bulletin et s'il en remplace un autre.
     */
    public Recepisse voter(String id, String credential, boolean oui)
            throws IOException, RefusException {
        ReferendumPublic referendum = referendum(id);
        if (referendum.getCle().isEmpty()) {
            throw new RefusException("Le référendum n'est pas encore validé");
        }
        BigInteger cle = entier(referendum.getCle());
        // Avec une clé hors du groupe, le chiffré révélerait le vote : on ne chiffre pas.
        if (!groupe.appartient(cle)) {
            throw new RefusException("Clé du référendum invalide");
        }
        Bulletin bulletin =
                Bulletin.creer(
                        groupe,
                        cle,
                        id,
                        Elgamal.depuisSecrete(groupe, new BigInteger(credential.strip(), 16)),
                        oui);
        Recepisse recepisse =
                urne.appeler(
                                Requete.newBuilder()
                                        .setDeposerBulletin(
                                                DeposerBulletin.newBuilder()
                                                        .setReferendum(id)
                                                        .setBulletin(versProto(bulletin))))
                        .getRecepisse();
        if (!recepisse.getEmpreinte().equals(bulletin.empreinte())) {
            throw new RefusException("Le récépissé ne correspond pas au bulletin envoyé");
        }
        return recepisse;
    }

    /** Vrai si le bulletin de traceur {@code empreinte} est dans l'urne publiée. */
    public boolean bulletinPresent(String id, String empreinte) throws IOException, RefusException {
        return urne
                .appeler(
                        Requete.newBuilder()
                                .setObtenirDonneesPubliques(
                                        Referendum.newBuilder().setReferendum(id)))
                .getDonneesPubliques()
                .getBulletinsList()
                .stream()
                .anyMatch(b -> Conversions.depuisProto(b).empreinte().equals(empreinte));
    }
}
