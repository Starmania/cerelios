package fr.cerelios.urne;

import fr.cerelios.protocole.ClientCertification;
import fr.cerelios.protocole.RefusException;
import fr.cerelios.protocole.messages.CredentialsCertifies;
import fr.cerelios.protocole.messages.NouvelleDemande;
import fr.cerelios.protocole.messages.RequeteCertification;
import fr.cerelios.protocole.messages.ValidationCertification;
import java.io.IOException;

/** Appels de l'urne au serveur de credentials, par le réseau. */
public class ConnecteurCertification {

    /**
     * Demande les credentials : le serveur les génère et rend leurs parties publiques, certifiées.
     */
    public CredentialsCertifies demander(String adresse, NouvelleDemande demande)
            throws IOException, RefusException {
        try (ClientCertification serveur = ClientCertification.vers(adresse)) {
            return serveur.appeler(RequeteCertification.newBuilder().setDemande(demande))
                    .getCredentials();
        }
    }

    /** Annonce la validation : le serveur envoie alors son credential à chaque électeur. */
    public void valider(String adresse, ValidationCertification validation)
            throws IOException, RefusException {
        try (ClientCertification serveur = ClientCertification.vers(adresse)) {
            serveur.appeler(RequeteCertification.newBuilder().setValidation(validation));
        }
    }
}
