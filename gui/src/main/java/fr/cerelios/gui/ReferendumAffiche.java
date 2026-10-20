package fr.cerelios.gui;

/** Référendum tel qu'affiché dans l'interface (titre dans la liste, question au vote). */
public record ReferendumAffiche(String titre, String question) {

    @Override
    public String toString() {
        return titre; // texte affiché dans la ListView
    }
}
