package fr.cerelios.protocole;

import fr.cerelios.protocole.messages.Message;
import fr.cerelios.protocole.messages.TestDemande;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ServeurSocketTest {

    @Test
    void testServeurSocket() throws Exception {
        int port = 9998;
        ServeurSocket serveur = new ServeurSocket(port);

        // Lancement du serveur socket sur un thread séparé
        Thread threadServeur = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                Socket socketClient = serverSocket.accept();
                serveur.traiterClient(socketClient);
            } catch (Exception e) {
                fail("Erreur serveur socket : " + e.getMessage());
            }
        });
        threadServeur.start();

        Thread.sleep(100); // Attente que la socket soit prête

        // Client test
        try (
            Socket clientSocket = new Socket("localhost", port);
            OutputStream out = clientSocket.getOutputStream();
            InputStream in = clientSocket.getInputStream()
        ) {

            Message requete = Message.newBuilder()
                .setDemandeCle(TestDemande.newBuilder().build())
                .build();
            requete.writeDelimitedTo(out);

            Message reponse = Message.parseDelimitedFrom(in);

            assertNotNull(reponse);
            assertEquals(Message.PayloadCase.REPONSE_CLE, reponse.getPayloadCase());
            assertEquals("recu", reponse.getReponseCle().getReponse());
        }
    }
}
