package fr.cerelios.protocole;

/**
 * Envoi des e-mails : invitations des scrutateurs, confirmations de vote, credentials. Une
 * implémentation réelle passerait par SMTP ; {@link MailJournal} sert au développement.
 */
public interface ServiceMail {

    void envoyer(String destinataire, String sujet, String corps);
}
