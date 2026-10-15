package fr.cerelios.protocole;

import fr.cerelios.protocole.messages.Reponse;
import fr.cerelios.protocole.messages.Requete;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Connexion d'un programme client à l'urne. Chaque requête est suivie de sa réponse ; les messages
 * sont préfixés par leur longueur ({@code writeDelimitedTo}).
 */
public class ClientUrne implements Closeable {

    private final Socket socket;
    private final InputStream entree;
    private final OutputStream sortie;
    private String jeton = "";

    public ClientUrne(String hote, int port) throws IOException {
        socket = new Socket(hote, port);
        entree = socket.getInputStream();
        sortie = socket.getOutputStream();
    }

    /** Jeton joint aux requêtes suivantes : session, jeton de scrutateur ou d'autorité. */
    public void utiliserJeton(String jeton) {
        this.jeton = jeton;
    }

    /**
     * Envoie la requête et rend la réponse.
     *
     * @throws RefusException si l'urne répond par une erreur
     * @throws IOException si la connexion est coupée
     */
    public synchronized Reponse appeler(Requete.Builder requete)
            throws IOException, RefusException {
        if (requete.getJeton().isEmpty()) {
            requete.setJeton(jeton);
        }
        requete.build().writeDelimitedTo(sortie);
        sortie.flush();
        Reponse reponse = Reponse.parseDelimitedFrom(entree);
        if (reponse == null) {
            throw new IOException("Connexion fermée par l'urne");
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
