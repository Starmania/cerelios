package fr.cerelios.protocole;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

/** N'envoie rien : garde les e-mails en mémoire et les écrit dans le journal. */
public class MailJournal implements ServiceMail {

    private static final System.Logger JOURNAL = System.getLogger(MailJournal.class.getName());

    public record Mail(String destinataire, String sujet, String corps) {}

    private final List<Mail> envoyes = new ArrayList<>();

    @Override
    public synchronized void envoyer(String destinataire, String sujet, String corps) {
        envoyes.add(new Mail(destinataire, sujet, corps));
        JOURNAL.log(Level.INFO, "Mail à {0} : {1}", destinataire, sujet);
    }

    /** E-mails envoyés jusqu'ici, du plus ancien au plus récent. */
    public synchronized List<Mail> envoyes() {
        return List.copyOf(envoyes);
    }
}
