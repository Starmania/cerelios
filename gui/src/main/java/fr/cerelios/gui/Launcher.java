package fr.cerelios.gui;

/**
 * Point d'entrée à lancer depuis l'IDE ou avec {@code java -cp}. Une classe qui étend {@code
 * Application} lancée directement exige JavaFX en modules et échoue sinon ; passer par une classe
 * qui n'en hérite pas évite ce contrôle.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
