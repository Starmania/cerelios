package fr.cerelios.protocole;

import fr.cerelios.protocole.messages.Message;
import fr.cerelios.protocole.messages.TestDemande;
import fr.cerelios.protocole.messages.TestReponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ServeurSocketTest {

    private static Message requeteDemande;

    @BeforeAll
    static void message() {
        requeteDemande = Message.newBuilder()
            .setDemandeCle(TestDemande.newBuilder().build())
            .build();
    }

    private ServeurSocket creerServeurTest(int port) {
        return new ServeurSocket(port) {
            @Override
            protected Message traiterMessage(Message requete) {
                if (requete.getPayloadCase() == Message.PayloadCase.DEMANDE_CLE) {
                    TestReponse rep = TestReponse.newBuilder()
                        .setReponse("Message de test recu avec succes")
                        .build();
                    return Message.newBuilder().setReponseCle(rep).build();
                }
                return null;
            }
        };
    }

    @Test
    void envoiUnique() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        ServeurSocket serveur = creerServeurTest(port);

        Thread threadServeur = new Thread(() -> {
            try {
                Socket socketClient = serverSocket.accept();
                serveur.traiterClient(socketClient);
                serverSocket.close();
            } catch (Exception e) {}
        });
        threadServeur.start();

        try (Socket socket = connecter(port)) {
            Message reponse = envoyer(socket, requeteDemande);
            assertNotNull(reponse);
            assertEquals(Message.PayloadCase.REPONSE_CLE, reponse.getPayloadCase());
            assertEquals("Message de test recu avec succes", reponse.getReponseCle().getReponse());
        }
    }

    @Test
    void testEnvoisMultipleTrois() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        ServeurSocket serveur = creerServeurTest(port);

        CountDownLatch latch = new CountDownLatch(3);
        List<Exception> erreurs = Collections.synchronizedList(new ArrayList<>());

        // Le serveur accepte et traite chaque client dans un thread dédié pour gérer la concurrence
        Thread threadServeur = new Thread(() -> {
            try {
                while (!serverSocket.isClosed() && latch.getCount() > 0) {
                    Socket socketClient = serverSocket.accept();
                    new Thread(() -> serveur.traiterClient(socketClient)).start();
                }
                serverSocket.close();
            } catch (Exception e) {}
        });
        threadServeur.start();

        // Lancement de 3 clients en parallèle
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                try (Socket socket = connecter(port)) {
                    Message reponse = envoyer(socket, requeteDemande);
                    assertNotNull(reponse);
                    assertEquals(Message.PayloadCase.REPONSE_CLE, reponse.getPayloadCase());
                } catch (Exception e) {
                    erreurs.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Attente que les 3 clients terminent leur échange
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Les 3 requetes simultanees auraient du se terminer a temps");
        assertTrue(erreurs.isEmpty(), "Des erreurs sont survenues lors des envois : " + erreurs);
    }

    @Test
    void clientInactifEtClientConcurrent() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        ServeurSocket serveur = creerServeurTest(port);

        CountDownLatch client2Termine = new CountDownLatch(1);

        Thread threadServeur = new Thread(() -> {
            try {
                while (!serverSocket.isClosed() && client2Termine.getCount() > 0) {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> serveur.traiterClient(socket)).start();
                }
                serverSocket.close();
            } catch (Exception e) {}
        });
        threadServeur.start();

        // 1. Client inactif (ouvre la connexion sans rien envoyer)
        Socket client1Inactif = connecter(port);

        // 2. Client concourrant (ouvre et envoie un message en parallèle)
        Thread client2Thread = new Thread(() -> {
            try (Socket client2 = connecter(port)) {
                Message reponse = envoyer(client2, requeteDemande);
                assertNotNull(reponse);
                assertEquals(Message.PayloadCase.REPONSE_CLE, reponse.getPayloadCase());
            } catch (Exception e) {
                fail("Erreur client 2 : " + e.getMessage());
            } finally {
                client2Termine.countDown();
            }
        });
        client2Thread.start();

        assertTrue(client2Termine.await(2, TimeUnit.SECONDS), "Le client 2 aurait du etre traite sans etre bloque");

        client1Inactif.close();
    }

    private Socket connecter(int port) throws Exception {
        return new Socket("localhost", port);
    }

    private Message envoyer(Socket socket, Message requete) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        requete.writeDelimitedTo(out);
        return Message.parseDelimitedFrom(in);
    }
}
