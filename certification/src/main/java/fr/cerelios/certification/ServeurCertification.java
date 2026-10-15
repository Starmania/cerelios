package fr.cerelios.certification;

import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.MailJournal;
import fr.cerelios.protocole.messages.RequeteCertification;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serveur réseau du serveur de credentials : un fil par client, une réponse par requête. */
public class ServeurCertification implements AutoCloseable {

    private static final System.Logger JOURNAL =
            System.getLogger(ServeurCertification.class.getName());

    private final ServiceCertification service;
    private final ServerSocket ecoute;
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();

    /** Écoute sur {@code port} ; 0 choisit un port libre, lisible avec {@link #port()}. */
    public ServeurCertification(ServiceCertification service, int port) throws IOException {
        this.service = service;
        this.ecoute = new ServerSocket(port);
    }

    public int port() {
        return ecoute.getLocalPort();
    }

    public void demarrer() {
        clients.submit(this::accepter);
    }

    private void accepter() {
        while (!ecoute.isClosed()) {
            try {
                Socket client = ecoute.accept();
                clients.submit(() -> servir(client));
            } catch (IOException e) {
                if (!ecoute.isClosed()) {
                    JOURNAL.log(System.Logger.Level.WARNING, "Connexion refusée", e);
                }
            }
        }
    }

    private void servir(Socket client) {
        try (client) {
            RequeteCertification requete;
            while ((requete = RequeteCertification.parseDelimitedFrom(client.getInputStream()))
                    != null) {
                service.traiter(requete).writeDelimitedTo(client.getOutputStream());
                client.getOutputStream().flush();
            }
        } catch (IOException e) {
            JOURNAL.log(System.Logger.Level.DEBUG, "Client déconnecté", e);
        }
    }

    @Override
    public void close() throws IOException {
        ecoute.close();
        clients.shutdownNow();
    }

    /** Lance le serveur : {@code ServeurCertification <port>}. Les e-mails vont au journal. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage : ServeurCertification <port>");
            System.exit(2);
        }
        ServeurCertification serveur =
                new ServeurCertification(
                        new ServiceCertification(Groupe.RFC_3526, new MailJournal(), 2),
                        Integer.parseInt(args[0]));
        serveur.demarrer();
        System.out.println("Serveur de credentials à l'écoute sur le port " + serveur.port());
    }
}
