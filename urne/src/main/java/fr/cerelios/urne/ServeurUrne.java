package fr.cerelios.urne;

import fr.cerelios.protocole.messages.Reponse;
import fr.cerelios.protocole.messages.Requete;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Serveur réseau de l'urne : un fil par client, qui lit des requêtes préfixées par leur longueur et
 * y répond dans l'ordre.
 */
public class ServeurUrne implements AutoCloseable {

    private static final System.Logger JOURNAL = System.getLogger(ServeurUrne.class.getName());

    private final Urne urne;
    private final ServerSocket ecoute;
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService politique = Executors.newSingleThreadScheduledExecutor();

    /** Écoute sur {@code port} ; 0 choisit un port libre, lisible ensuite avec {@link #port()}. */
    public ServeurUrne(Urne urne, int port) throws IOException {
        this.urne = urne;
        this.ecoute = new ServerSocket(port);
    }

    public int port() {
        return ecoute.getLocalPort();
    }

    /**
     * Accepte les clients en arrière-plan et lance la politique de conservation toutes les heures.
     */
    public void demarrer() {
        clients.submit(this::accepter);
        politique.scheduleAtFixedRate(urne::appliquerPolitiqueDonnees, 1, 1, TimeUnit.HOURS);
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
            InputStream entree = client.getInputStream();
            OutputStream sortie = client.getOutputStream();
            Requete requete;
            while ((requete = Requete.parseDelimitedFrom(entree)) != null) {
                Reponse reponse = urne.traiter(requete);
                reponse.writeDelimitedTo(sortie);
                sortie.flush();
            }
        } catch (IOException e) {
            JOURNAL.log(System.Logger.Level.DEBUG, "Client déconnecté", e);
        }
    }

    @Override
    public void close() throws IOException {
        ecoute.close();
        politique.shutdownNow();
        clients.shutdownNow();
    }

    /**
     * Lance l'urne : {@code ServeurUrne <port> <comptes>}, où chaque ligne du fichier de comptes
     * est {@code identifiant:email:motDePasse}. Ces comptes alimentent le fournisseur d'identité
     * local, en attendant un vrai IdP.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage : ServeurUrne <port> <fichier de comptes>");
            System.exit(2);
        }
        IdentitesLocales idp = new IdentitesLocales();
        for (String ligne : Files.readAllLines(Path.of(args[1]))) {
            if (ligne.isBlank() || ligne.startsWith("#")) {
                continue;
            }
            String[] champs = ligne.split(":", 3);
            idp.inscrire(champs[0], champs[2], champs[1]);
        }
        ServeurUrne serveur =
                new ServeurUrne(
                        new Urne(idp, new MailJournal(), Clock.systemUTC()),
                        Integer.parseInt(args[0]));
        serveur.demarrer();
        System.out.println("Urne à l'écoute sur le port " + serveur.port());
    }
}
