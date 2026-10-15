package fr.cerelios.gui;

import fr.cerelios.certification.ServeurCertification;
import fr.cerelios.certification.ServiceCertification;
import fr.cerelios.crypto.Groupe;
import fr.cerelios.protocole.ServiceMail;
import fr.cerelios.urne.ConnecteurCertification;
import fr.cerelios.urne.IdentitesLocales;
import fr.cerelios.urne.ServeurUrne;
import fr.cerelios.urne.Urne;
import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Lance sur ce poste une urne (port 5000) et un serveur de credentials (port 5001) pour essayer
 * l'interface. Les e-mails ne partent pas : ils s'affichent en entier ici, car les jetons, la
 * graine et les credentials n'arrivent que par e-mail.
 *
 * <p>Comptes : alice (administratrice), bob, carol, dave ; mot de passe {@code demo} pour tous.
 */
public final class Demo {

    private Demo() {}

    /** Écrit chaque e-mail dans la console, corps compris. */
    private static final class MailConsole implements ServiceMail {
        @Override
        public synchronized void envoyer(String destinataire, String sujet, String corps) {
            System.out.printf(
                    "%n──── E-mail pour %s ────%nObjet : %s%n%s%n%n", destinataire, sujet, corps);
            System.out.flush();
        }
    }

    public static void main(String[] args) throws IOException {
        IdentitesLocales idp = new IdentitesLocales();
        for (String compte : List.of("alice", "bob", "carol", "dave")) {
            idp.inscrire(compte, "demo", compte + "@exemple.fr");
        }
        MailConsole mail = new MailConsole();
        ServeurUrne urne =
                new ServeurUrne(
                        new Urne(
                                idp,
                                mail,
                                Clock.systemDefaultZone(),
                                Groupe.RFC_3526,
                                new ConnecteurCertification(),
                                "localhost:5000"),
                        5000);
        ServeurCertification certification =
                new ServeurCertification(new ServiceCertification(Groupe.RFC_3526, mail, 2), 5001);
        urne.demarrer();
        certification.demarrer();
        System.out.println("Urne sur localhost:5000, serveur de credentials sur localhost:5001.");
        System.out.println("Comptes : alice, bob, carol, dave — mot de passe « demo ».");
        System.out.println("Arrêt : Ctrl+C.");
    }
}
