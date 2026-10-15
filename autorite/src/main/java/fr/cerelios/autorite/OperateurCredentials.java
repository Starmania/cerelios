package fr.cerelios.autorite;

import static fr.cerelios.protocole.Conversions.entier;

import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.Certificats;
import fr.cerelios.protocole.ClientCertification;
import fr.cerelios.protocole.ClientUrne;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.messages.ConsulterCredits;
import fr.cerelios.protocole.messages.DonneesPubliques;
import fr.cerelios.protocole.messages.Electeurs;
import fr.cerelios.protocole.messages.Referendum;
import fr.cerelios.protocole.messages.Renvoi;
import fr.cerelios.protocole.messages.Requete;
import fr.cerelios.protocole.messages.RequeteCertification;
import fr.cerelios.protocole.messages.Vide;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Opérateur des credentials : personne qui garde la graine reçue par e-mail. Sans elle, le serveur
 * de credentials ne peut plus relire ni renvoyer aucun credential.
 */
public class OperateurCredentials {

    private final ClientCertification serveur;
    private final ClientUrne urne;
    private final Groupe groupe;
    private final String referendum;
    private final String graine;

    public OperateurCredentials(
            ClientCertification serveur,
            ClientUrne urne,
            Groupe groupe,
            String referendum,
            String graine) {
        this.serveur = serveur;
        this.urne = urne;
        this.groupe = groupe;
        this.referendum = referendum;
        this.graine = graine;
    }

    /**
     * Vérifie le certificat publié par l'urne : signé avec les clés de cette graine, et portant sur
     * les credentials publiés. Rend les anomalies, vide si tout concorde.
     */
    public List<String> verifierCertificat() throws IOException, RefusException {
        DonneesPubliques donnees =
                urne.appeler(
                                Requete.newBuilder()
                                        .setObtenirDonneesPubliques(
                                                Referendum.newBuilder().setReferendum(referendum)))
                        .getDonneesPubliques();
        List<String> anomalies = new ArrayList<>();
        if (!donnees.hasCertificat()) {
            anomalies.add("Aucun certificat publié");
            return anomalies;
        }
        if (!Certificats.graineDuCertificat(groupe, graine, donnees.getCertificat())) {
            anomalies.add("Le certificat n'a pas été signé avec les clés de votre graine");
        }
        anomalies.addAll(
                Certificats.verifier(
                        groupe,
                        donnees.getCertificat(),
                        referendum,
                        donnees.getCredentialsList().stream().map(c -> entier(c)).toList(),
                        donnees.getReferendum().getNombreElecteurs()));
        return anomalies;
    }

    /** Renvoie leur credential aux électeurs nommés, par exemple à qui l'a perdu. */
    public void renvoyerA(List<String> electeurs) throws IOException, RefusException {
        renvoyer(renvoi().setElecteurs(Electeurs.newBuilder().addAllElecteurs(electeurs)));
    }

    /** Renvoie son credential à chaque électeur. */
    public void renvoyerATous() throws IOException, RefusException {
        renvoyer(renvoi().setTous(Vide.getDefaultInstance()));
    }

    /** Renvoie leur credential aux électeurs qui n'ont pas encore voté. */
    public void renvoyerSansBulletin() throws IOException, RefusException {
        renvoyer(renvoi().setSansBulletin(Vide.getDefaultInstance()));
    }

    /** Nombre d'e-mails que le serveur accepte encore d'envoyer. */
    public int creditsRestants() throws IOException, RefusException {
        return serveur.appeler(
                        RequeteCertification.newBuilder()
                                .setCredits(
                                        ConsulterCredits.newBuilder()
                                                .setReferendum(referendum)
                                                .setGraine(graine)))
                .getCredits()
                .getRestants();
    }

    private Renvoi.Builder renvoi() {
        return Renvoi.newBuilder().setReferendum(referendum).setGraine(graine);
    }

    private void renvoyer(Renvoi.Builder renvoi) throws IOException, RefusException {
        serveur.appeler(RequeteCertification.newBuilder().setRenvoi(renvoi));
    }
}
