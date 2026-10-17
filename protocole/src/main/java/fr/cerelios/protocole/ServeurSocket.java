package fr.cerelios.protocole;

import fr.cerelios.protocole.messages.Message;
import fr.cerelios.protocole.messages.TestReponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Serveur Socket réseau gérant la réception et l'aiguillage des messages du protocole.
 */
public abstract class ServeurSocket {

    private final int port;

    public ServeurSocket(int port) {
        this.port = port;
    }

    /**
     * Démare la boucle d'écoute du serveur Socket.
     * Pour chaque connexion client acceptée, la communication est déléguée
     * à la méthode {@link #traiterClient(Socket)}.
     */
    public void demarrer() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                traiterClient(socket);
            }
        } catch (Exception e) {
            System.err.println("Erreur serveur : " + e.getMessage());
        }
    }

    /**
     * Traite un échange individuel avec un client connecté.
     *
     * @param socket la connexion {@link Socket} avec le client
     */
    public void traiterClient(Socket socket) {
        try (
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream()
        ) {

            Message requete = Message.parseDelimitedFrom(in);
            if (requete == null) return;

            Message reponse = traiterMessage(requete);
            if (reponse != null) reponse.writeDelimitedTo(out);

        } catch (Exception e) {
            System.err.println("Erreur traitement client : " + e.getMessage());
        }
    }

    /**
     * Traite un message Protobuf reçu et retourne le message de réponse correspondant.
     *
     * @param requete le message Protobuf reçu du client
     * @return le message Protobuf de réponse, ou {@code null} si aucune réponse n'est requise
     */
    protected abstract Message traiterMessage(Message requete);
}
