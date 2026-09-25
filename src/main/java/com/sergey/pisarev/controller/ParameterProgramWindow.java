package com.sergey.pisarev.controller;

import com.sergey.pisarev.service.MachineParameterProgram;
import com.sergey.pisarev.util.AppIconHelper;
import com.sergey.pisarev.util.I18n;
import com.sergey.pisarev.util.UserSettings;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Окно «Параметры»: программа размеров, по именам которых ходит обрабатывающая.
 *
 * <p>На двухканальных станках Hegenscheidt в обрабатывающей программе размеров нет
 * вообще — она написана именами вроде {@code TREAD_DIAM} и {@code WHEEL_HEIGHT}.
 * Значения оператор правит здесь, под исполнение колеса, и сразу видит, что из этого
 * посчиталось: без параметров траектория выходит нулевой, и понять почему было
 * невозможно.
 *
 * <p>Оформление берётся из общей темы приложения ({@code settings-window-root},
 * карточки {@code settings-card}), иначе в светлой теме подписи белеют на белом.
 */
final class ParameterProgramWindow {
    private static Stage stage;
    private static TextArea programArea;
    private static Label summaryLabel;
    private static Label valuesLabel;
    private static Label unresolvedLabel;
    private static Consumer<String> onSaved;

    private ParameterProgramWindow() {
    }

    static void show(Window owner, boolean darkTheme, Consumer<String> saveListener) {
        onSaved = saveListener;
        if (stage != null) {
            applyTheme(darkTheme);
            programArea.setText(UserSettings.getParameterProgramText());
            updateSummary();
            stage.show();
            stage.requestFocus();
            stage.toFront();
            return;
        }
        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(I18n.text("parameters.title"));
        AppIconHelper.applyToStage(stage, owner);

        VBox content = new VBox(14.0);
        content.setPadding(new Insets(18.0));
        content.getChildren().addAll(programCard(), summaryCard());

        BorderPane root = new BorderPane(content);
        root.getStyleClass().add("settings-window-root");

        HBox footer = new HBox(10.0);
        footer.getStyleClass().add("settings-window-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 14, 12, 14));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button save = new Button(I18n.text("parameters.save"));
        save.getStyleClass().addAll("primary-button", "settings-footer-button");
        save.setMinSize(150.0, 42.0);
        save.setOnAction(event -> save());
        Button close = new Button(I18n.text("parameters.close"));
        close.getStyleClass().addAll("secondary-button", "settings-footer-button");
        close.setMinSize(128.0, 42.0);
        close.setOnAction(event -> stage.hide());
        footer.getChildren().addAll(spacer, save, close);
        root.setBottom(footer);

        double height = Math.min(760.0, Screen.getPrimary().getVisualBounds().getHeight() - 80.0);
        Scene scene = new Scene(root, 720.0, height);
        stage.setScene(scene);
        stage.setMinWidth(560.0);
        stage.setMinHeight(420.0);
        applyTheme(darkTheme);
        programArea.setText(UserSettings.getParameterProgramText());
        updateSummary();
        stage.show();
    }

    private static VBox programCard() {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("settings-card");
        card.setPadding(new Insets(14.0));
        Label title = new Label(I18n.text("parameters.card.program"));
        title.getStyleClass().add("section-title");
        Label hint = new Label(I18n.text("parameters.hint"));
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);
        programArea = new TextArea();
        programArea.getStyleClass().add("parameter-program-area");
        // Крупнее обычного текста: программу параметров правят у станка, глядя в лист.
        programArea.setStyle(
                "-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 18px;");
        programArea.setPrefRowCount(14);
        VBox.setVgrow(programArea, Priority.ALWAYS);
        programArea.textProperty().addListener((observable, before, after) -> updateSummary());
        card.getChildren().addAll(title, hint, programArea);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private static VBox summaryCard() {
        VBox card = new VBox(6.0);
        card.getStyleClass().add("settings-card");
        card.setPadding(new Insets(14.0));
        Label title = new Label(I18n.text("parameters.card.values"));
        title.getStyleClass().add("section-title");
        summaryLabel = new Label();
        summaryLabel.setStyle("-fx-font-size: 15px;");
        valuesLabel = new Label();
        valuesLabel.setStyle("-fx-font-size: 15px;");
        valuesLabel.setWrapText(true);
        valuesLabel.getStyleClass().add("muted-label");
        unresolvedLabel = new Label();
        unresolvedLabel.setWrapText(true);
        unresolvedLabel.getStyleClass().add("diagnostic-badge");
        unresolvedLabel.setVisible(false);
        unresolvedLabel.setManaged(false);
        card.getChildren().addAll(title, summaryLabel, valuesLabel, unresolvedLabel);
        return card;
    }

    /** Считает программу параметров на каждое изменение: видно сразу, что вышло. */
    private static void updateSummary() {
        String text = programArea == null ? "" : programArea.getText();
        if (text == null || text.isBlank()) {
            summaryLabel.setText(I18n.text("parameters.empty"));
            valuesLabel.setText("");
            hide(unresolvedLabel);
            return;
        }
        MachineParameterProgram.Variables variables = MachineParameterProgram.read(text);
        summaryLabel.setText(I18n.format("parameters.count", variables.size()));
        valuesLabel.setText(describe(variables.values()));
        if (variables.unresolved().isEmpty()) {
            hide(unresolvedLabel);
        } else {
            unresolvedLabel.setText(I18n.format("parameters.unresolved",
                    String.join(", ", variables.unresolved())));
            unresolvedLabel.setVisible(true);
            unresolvedLabel.setManaged(true);
        }
    }

    /** Главные размеры вперёд: по ним оператор сразу узнаёт своё колесо. */
    private static String describe(Map<String, Double> values) {
        List<String> parts = new ArrayList<>();
        for (String name : new String[]{
                "TREAD_DIAM", "WHEEL_HEIGHT", "TREAD_HEIGHT_S1", "TREAD_HEIGHT_S2",
                "BORE_DIAM", "DIAM_KANAV", "GLOBAL_ALLOWANCE", "TREAD_ALLOWANCE"}) {
            Double value = values.get(name);
            if (value != null) {
                parts.add(name + " = " + trim(value));
            }
        }
        int shown = parts.size();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (parts.size() >= shown + 10) {
                break;
            }
            if (entry.getKey().startsWith("N_") || values.containsKey(entry.getKey())
                    && parts.stream().anyMatch(part -> part.startsWith(entry.getKey() + " "))) {
                continue;
            }
            parts.add(entry.getKey() + " = " + trim(entry.getValue()));
        }
        return String.join("   ", parts);
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(Math.round(value * 1000.0) / 1000.0);
    }

    private static void hide(Label label) {
        label.setVisible(false);
        label.setManaged(false);
        label.setText("");
    }

    private static void save() {
        String text = programArea.getText();
        UserSettings.setParameterProgramText(text);
        if (onSaved != null) {
            onSaved.accept(text);
        }
        summaryLabel.setText(summaryLabel.getText() + " — " + I18n.text("parameters.saved"));
    }

    /** Текст в окне, если оно открыто: нужно, когда параметры пришли из набора файлов. */
    static void setProgramText(String text) {
        if (programArea != null) {
            programArea.setText(text == null ? "" : text);
            updateSummary();
        }
    }

    private static void applyTheme(boolean darkTheme) {
        if (stage == null || stage.getScene() == null) {
            return;
        }
        Scene scene = stage.getScene();
        scene.getStylesheets().clear();
        var appCss = ParameterProgramWindow.class.getResource("/app.css");
        if (appCss != null) {
            scene.getStylesheets().add(appCss.toExternalForm());
        }
        scene.getRoot().getStyleClass().remove("dark-theme");
        if (darkTheme) {
            scene.getRoot().getStyleClass().add("dark-theme");
        }
    }
}
