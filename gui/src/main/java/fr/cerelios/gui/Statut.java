package fr.cerelios.gui;

import javafx.scene.control.Label;

/** Ligne d'état d'un écran : travail en cours, succès ou erreur. */
public final class Statut extends Label {

    public Statut() {
        getStyleClass().add("statut");
        setWrapText(true);
        setMaxWidth(Double.MAX_VALUE);
    }

    void enCours() {
        afficher("Patientez…", "statut-en-cours");
    }

    void effacer() {
        afficher("", null);
    }

    public void succes(String message) {
        afficher(message, "statut-succes");
    }

    public void erreur(String message) {
        afficher(message, "statut-erreur");
    }

    private void afficher(String message, String classe) {
        getStyleClass().removeAll("statut-en-cours", "statut-succes", "statut-erreur");
        if (classe != null) {
            getStyleClass().add(classe);
        }
        setText(message);
    }
}
