package fr.cerelios.protocole;

import fr.cerelios.protocole.messages.ReponseCertification;
import fr.cerelios.protocole.messages.RequeteCertification;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/** Connexion au serveur de credentials, pour l'urne comme pour l'opérateur. */
public class ClientCertification implements Closeable {

    private final Socket socket;
    private final InputStream entree;
    private final OutputStream sortie;

    public ClientCertification(String hote, int port) throws IOException {
        socket = new Socket(hote, port);
        entree = socket.getInputStream();
        sortie = socket.getOutputStream();
    }

    /** Se connecte à une adresse {@code hôte:port}. */
    public static ClientCertification vers(String adresse) throws IOException {
        int separateur = adresse.lastIndexOf(':');
        if (separateur <= 0) {
            throw new IOException("Adresse attendue sous la forme hôte:port : " + adresse);
        }
        return new ClientCertification(
                adresse.substring(0, separateur),
                Integer.parseInt(adresse.substring(separateur + 1)));
    }

    /**
     * @throws RefusException si le serveur répond par une erreur
     */
    public synchronized ReponseCertification appeler(RequeteCertification.Builder requete)
            throws IOException, RefusException {
        requete.build().writeDelimitedTo(sortie);
        sortie.flush();
        ReponseCertification reponse = ReponseCertification.parseDelimitedFrom(entree);
        if (reponse == null) {
            throw new IOException("Connexion fermée par le serveur de credentials");
        }
        if (reponse.hasErreur()) {
            throw new RefusException(reponse.getErreur().getMessage());
        }
        return reponse;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
