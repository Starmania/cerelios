package fr.cerelios.gui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/** Saisie d'un instant : date au calendrier et heure HH:mm, dans le fuseau du poste. */
public final class ChampDate extends HBox {

    private final DatePicker jour = new DatePicker();
    private final TextField heure = new TextField();

    public ChampDate() {
        super(8);
        heure.setPromptText("HH:mm");
        heure.setPrefColumnCount(5);
        getChildren().addAll(jour, heure);
    }

    public void afficher(long epochMilli) {
        ZonedDateTime z = Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault());
        jour.setValue(z.toLocalDate());
        heure.setText(String.format("%02d:%02d", z.getHour(), z.getMinute()));
    }

    /**
     * @throws IllegalArgumentException si la date ou l'heure manque ou est mal écrite
     */
    public Instant instant() {
        LocalDate date = jour.getValue();
        if (date == null) {
            throw new IllegalArgumentException("Choisissez une date");
        }
        try {
            LocalTime t = LocalTime.parse(heure.getText().strip());
            return date.atTime(t).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Heure attendue sous la forme HH:mm");
        }
    }
}
