package fr.cerelios.urne;

/**
 * Envoi des e-mails de l'urne : invitations des scrutateurs, confirmations de vote. Une
 * implémentation réelle passerait par SMTP ; {@link MailJournal} sert au développement.
 */
public interface ServiceMail {

    void envoyer(String destinataire, String sujet, String corps);
}
