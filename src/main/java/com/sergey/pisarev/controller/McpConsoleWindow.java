package com.sergey.pisarev.controller;

import com.sergey.pisarev.ai.AppAccess;
import com.sergey.pisarev.ai.ClientConfig;
import com.sergey.pisarev.ai.McpServer;
import com.sergey.pisarev.util.AppIconHelper;
import com.sergey.pisarev.util.I18n;
import com.sergey.pisarev.util.UserSettings;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно «МСП»: одна кнопка — и нейросеть подключена.
 *
 * <p>Раньше здесь надо было включить сервер, потом скопировать команду, потом найти
 * конфиг клиента и вставить её туда. Теперь всё это делает кнопка «Подключить»:
 * поднимает сервер и сама прописывает адрес в настройки Claude Code (и Codex, если он
 * стоит). Наладчику остаётся перезапустить клиента.
 *
 * <p>Оформление берётся из общей темы приложения ({@code settings-window-root} и
 * карточки {@code settings-card}), а не задаётся здесь заново: иначе в светлой теме
 * подписи оказывались белыми на белом — так и было в первой версии этого окна.
 */
final class McpConsoleWindow {
    private static Stage stage;
    private static Timeline refresh;
    private static Label statusLabel;
    private static Label accessHint;
    private static Label resultLabel;
    private static Label clientsLabel;
    private static TextField addressField;
    private static TextField portField;
    private static TextArea journalArea;
    private static Button connectButton;
    private static Button stopButton;
    private static CheckBox fullAccessBox;
    private static CheckBox autoStartBox;

    private McpConsoleWindow() {
    }

    static void show(Window owner, AppAccess app, String appVersion, boolean darkTheme) {
        if (stage != null) {
            applyTheme(darkTheme);
            update();
            refreshClients();
            stage.show();
            stage.requestFocus();
            stage.toFront();
            return;
        }
        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(I18n.text("mcp.title"));
        AppIconHelper.applyToStage(stage, owner);

        VBox content = new VBox(14.0);
        content.setPadding(new Insets(18.0));
        content.getChildren().addAll(
                connectionCard(app, appVersion),
                accessCard(),
                addressCard(app, appVersion),
                journalCard());

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("sidebar-scroll");

        BorderPane root = new BorderPane(scroll);
        root.getStyleClass().add("settings-window-root");

        HBox footer = new HBox(10.0);
        footer.getStyleClass().add("settings-window-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 14, 12, 14));
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        Button close = new Button(I18n.text("mcp.close"));
        close.getStyleClass().addAll("secondary-button", "settings-footer-button");
        close.setMinSize(128.0, 42.0);
        close.setOnAction(event -> stage.hide());
        footer.getChildren().addAll(footerSpacer, close);
        root.setBottom(footer);

        javafx.geometry.Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root,
                Math.min(820.0, bounds.getWidth() * 0.9),
                Math.min(860.0, bounds.getHeight() * 0.9));
        java.net.URL css = McpConsoleWindow.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        applyTheme(darkTheme);

        // Состояние и журнал подтягиваются сами: человек видит вызовы, ничего не нажимая.
        refresh = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> update()));
        refresh.setCycleCount(Animation.INDEFINITE);
        refresh.play();
        stage.setOnHidden(event -> {
            if (refresh != null) {
                refresh.stop();
            }
        });
        stage.setOnShown(event -> {
            if (refresh != null && refresh.getStatus() != Animation.Status.RUNNING) {
                refresh.play();
            }
        });
        update();
        refreshClients();
        stage.show();
    }

    // ------------------------------------------------------------------
    // Карточки
    // ------------------------------------------------------------------

    /** Главная карточка: состояние и одна кнопка на всё подключение. */
    private static VBox connectionCard(AppAccess app, String appVersion) {
        VBox card = card();
        card.getChildren().add(title(I18n.text("mcp.heading")));
        card.getChildren().add(muted(I18n.text("mcp.about")));

        statusLabel = new Label();
        statusLabel.getStyleClass().add("settings-badge");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setWrapText(true);
        card.getChildren().add(statusLabel);

        connectButton = new Button(I18n.text("mcp.connect"));
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMinSize(240.0, 46.0);
        connectButton.setOnAction(event -> connect(app, appVersion));

        stopButton = new Button(I18n.text("mcp.stop"));
        stopButton.getStyleClass().add("secondary-button");
        stopButton.setMinSize(150.0, 46.0);
        stopButton.setOnAction(event -> {
            McpServer.stop();
            update();
        });

        HBox buttons = new HBox(10.0, connectButton, stopButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(buttons);

        clientsLabel = new Label();
        clientsLabel.getStyleClass().add("muted-label");
        clientsLabel.setWrapText(true);
        clientsLabel.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(clientsLabel);

        resultLabel = new Label();
        resultLabel.getStyleClass().add("muted-label");
        resultLabel.setWrapText(true);
        resultLabel.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(resultLabel);

        autoStartBox = new CheckBox(I18n.text("mcp.autostart"));
        autoStartBox.setSelected(UserSettings.isMcpAutoStart());
        autoStartBox.selectedProperty().addListener((observable, was, now) ->
                UserSettings.setMcpAutoStart(now));
        card.getChildren().add(autoStartBox);

        return card;
    }

    /** Что нейросети разрешено: одна галочка вместо двух переключателей. */
    private static VBox accessCard() {
        VBox card = card();
        card.getChildren().add(subTitle(I18n.text("mcp.access")));
        fullAccessBox = new CheckBox(I18n.text("mcp.access.full"));
        fullAccessBox.setSelected("FULL".equals(UserSettings.getMcpAccess()));
        fullAccessBox.selectedProperty().addListener((observable, was, now) -> {
            UserSettings.setMcpAccess(now ? "FULL" : "READ_ONLY");
            McpServer server = McpServer.current();
            if (server != null) {
                server.setAccess(now ? McpServer.Access.FULL : McpServer.Access.READ_ONLY);
            }
            update();
        });
        card.getChildren().add(fullAccessBox);
        accessHint = muted("");
        card.getChildren().add(accessHint);
        return card;
    }

    /** Адрес — для клиентов, которые настраиваются вручную. */
    private static VBox addressCard(AppAccess app, String appVersion) {
        VBox card = card();
        card.getChildren().add(subTitle(I18n.text("mcp.address")));
        addressField = new TextField();
        addressField.setEditable(false);
        addressField.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(addressField);

        Button copyAddress = new Button(I18n.text("mcp.copy.address"));
        copyAddress.getStyleClass().add("secondary-button");
        copyAddress.setOnAction(event -> copy(addressField.getText()));

        Button newKey = new Button(I18n.text("mcp.newkey"));
        newKey.getStyleClass().add("secondary-button");
        newKey.setOnAction(event -> {
            UserSettings.resetMcpKey();
            // Ключ проверяет работающий сервер, поэтому его надо поднять заново.
            boolean wasRunning = McpServer.isRunning();
            if (wasRunning) {
                McpServer.stop();
                start(app, appVersion);
            }
            McpServer.note(I18n.text("mcp.log.newkey"));
            resultLabel.setText(I18n.text("mcp.newkey.done"));
            update();
        });

        portField = new TextField(String.valueOf(UserSettings.getMcpPort()));
        portField.setPrefWidth(90.0);
        portField.focusedProperty().addListener((observable, was, now) -> {
            if (!now) {
                UserSettings.setMcpPort(parsePort());
            }
        });
        Label portLabel = new Label(I18n.text("mcp.port"));
        portLabel.getStyleClass().add("settings-field-label");

        HBox row = new HBox(8.0, copyAddress, newKey, portLabel, portField);
        row.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(row);
        card.getChildren().add(muted(I18n.text("mcp.hint")));
        return card;
    }

    private static VBox journalCard() {
        VBox card = card();
        VBox.setVgrow(card, Priority.ALWAYS);
        card.getChildren().add(subTitle(I18n.text("mcp.journal")));
        journalArea = new TextArea();
        journalArea.setEditable(false);
        journalArea.setWrapText(true);
        journalArea.setPrefRowCount(8);
        journalArea.getStyleClass().add("mcp-journal");
        VBox.setVgrow(journalArea, Priority.ALWAYS);
        card.getChildren().add(journalArea);
        return card;
    }

    // ------------------------------------------------------------------
    // Действия
    // ------------------------------------------------------------------

    /**
     * Одна кнопка на всё: поднять сервер и прописать себя в конфиги клиентов.
     *
     * <p>Порядок именно такой: адрес с ключом становится известен только после
     * запуска сервера, а прописывать в конфиг надо уже готовый адрес.
     */
    private static void connect(AppAccess app, String appVersion) {
        if (!McpServer.isRunning() && !start(app, appVersion)) {
            return;
        }
        McpServer server = McpServer.current();
        if (server == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (ClientConfig.Result result : List.of(
                ClientConfig.configureClaudeCode(server.url()),
                ClientConfig.configureCodex(server.url()))) {
            String line = describe(result) + "  (" + ClientConfig.shortPath(result.path()) + ")";
            lines.add(line);
            McpServer.note(describe(result));
        }
        lines.add(I18n.text("mcp.connect.restart"));
        resultLabel.setText(String.join(System.lineSeparator(), lines));
        update();
        refreshClients();
    }

    private static boolean start(AppAccess app, String appVersion) {
        try {
            McpServer.start(app, parsePort(),
                    fullAccessBox.isSelected() ? McpServer.Access.FULL : McpServer.Access.READ_ONLY,
                    appVersion);
            return true;
        } catch (Exception failure) {
            String message = I18n.text("mcp.log.startfailed") + " " + failure.getMessage();
            McpServer.note(message);
            resultLabel.setText(message);
            update();
            return false;
        }
    }

    private static int parsePort() {
        try {
            int value = Integer.parseInt(portField.getText().trim());
            return value >= 0 && value <= 65535 ? value : McpServer.DEFAULT_PORT;
        } catch (NumberFormatException wrong) {
            return McpServer.DEFAULT_PORT;
        }
    }

    /** Строки «что уже прописано» — читают файлы, поэтому вызываются по событию. */
    private static void refreshClients() {
        McpServer server = McpServer.current();
        String url = server == null ? "" : server.url();
        List<String> lines = new ArrayList<>();
        for (ClientConfig.Result result : ClientConfig.status(url)) {
            lines.add(describe(result));
        }
        clientsLabel.setText(String.join(System.lineSeparator(), lines));
    }

    private static void update() {
        McpServer server = McpServer.current();
        boolean on = server != null;
        statusLabel.setText(on
                ? I18n.format("mcp.status.on", server.port(), server.calls(),
                server.lastTool().isEmpty() ? "-" : server.lastTool())
                : I18n.text("mcp.status.off"));
        // Работающий сервер виден цветом плашки, а не только текстом.
        statusLabel.getStyleClass().remove("mcp-status-on");
        if (on) statusLabel.getStyleClass().add("mcp-status-on");
        connectButton.setText(on ? I18n.text("mcp.connect.again") : I18n.text("mcp.connect"));
        stopButton.setDisable(!on);
        portField.setDisable(on);
        addressField.setText(on ? server.url() : "");
        accessHint.setText(fullAccessBox.isSelected()
                ? I18n.text("mcp.access.full.about") : I18n.text("mcp.access.read.about"));
        String text = String.join(System.lineSeparator(), McpServer.journal());
        if (!text.equals(journalArea.getText())) {
            journalArea.setText(text);
            journalArea.positionCaret(text.length());
            journalArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    /** Одна строка про клиента на языке интерфейса. */
    private static String describe(ClientConfig.Result result) {
        String key = switch (result.state()) {
            case NO_FILE -> "mcp.client.nofile";
            case ABSENT -> "mcp.client.absent";
            case SAME -> "mcp.client.same";
            case OTHER -> "mcp.client.other";
            case WRITTEN -> "mcp.client.written";
            case FAILED -> "mcp.client.failed";
        };
        String text = (result.ok() ? "OK  " : "--  ") + I18n.format(key, result.client());
        if (!result.detail().isBlank()) {
            text = text + " — " + result.detail();
        }
        return text;
    }

    private static void copy(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        McpServer.note(I18n.text("mcp.log.copied"));
    }

    // ------------------------------------------------------------------
    // Оформление
    // ------------------------------------------------------------------

    private static void applyTheme(boolean darkTheme) {
        if (stage == null || stage.getScene() == null) {
            return;
        }
        var styles = stage.getScene().getRoot().getStyleClass();
        styles.remove("dark-theme");
        if (darkTheme) {
            styles.add("dark-theme");
        }
    }

    private static VBox card() {
        VBox card = new VBox(10.0);
        card.getStyleClass().add("settings-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-card-title");
        label.setWrapText(true);
        return label;
    }

    private static Label subTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-sub-title");
        return label;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-label");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
