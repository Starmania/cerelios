package fr.cerelios.gui;

import fr.cerelios.protocole.messages.Etat;
import fr.cerelios.protocole.messages.ReferendumPublic;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Résumé public d'un référendum : question, état, calendrier, participation, résultat. */
public final class FicheReferendum extends VBox {

    public FicheReferendum() {
        super(8);
        getStyleClass().add("carte");
    }

    public void afficher(ReferendumPublic r) {
        getChildren().clear();
        Label question = new Label(r.getQuestion());
        question.getStyleClass().add("question");
        question.setWrapText(true);
        getChildren()
                .addAll(question, Ui.ligne(Ui.pastille(r.getEtat()), Ui.aide("n° " + r.getId())));
        if (r.hasDates()) {
            getChildren()
                    .add(
                            Ui.aide(
                                    "Vote du "
                                            + Ui.date(r.getDates().getOuverture())
                                            + " au "
                                            + Ui.date(r.getDates().getCloture())
                                            + " · résultat à partir du "
                                            + Ui.date(r.getDates().getPublication())));
        }
        if (r.getEtat() != Etat.BROUILLON) {
            getChildren()
                    .add(
                            Ui.texte(
                                    r.getNombreBulletins()
                                            + " bulletin(s) pour "
                                            + r.getNombreElecteurs()
                                            + " électeur(s)"));
        }
        if (r.hasResultat()) {
            int oui = r.getResultat().getOui();
            int non = r.getResultat().getNon();
            Label resultat = new Label("Oui : " + oui + "    Non : " + non);
            resultat.getStyleClass().add("resultat");
            getChildren().add(resultat);
            if (oui + non > 0) {
                getChildren().add(Barre.oui(oui, non));
            }
        }
    }
}
