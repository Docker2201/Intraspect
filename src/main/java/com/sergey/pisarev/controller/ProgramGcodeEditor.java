package com.sergey.pisarev.controller;

import com.sergey.pisarev.util.I18n;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Point2D;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.TwoDimensional;
import org.reactfx.Subscription;

public final class ProgramGcodeEditor {
    private static final double GUTTER_WIDTH = 24.0;
    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final AnchorPane editorPane;
    private final Region gutterShield;
    private final HBox root;
    private int highlightLine = -1;
    private String highlightStyle = "program-line-active";
    private int startMarkerLine = -1;
    private boolean textSelectionActive;
    private int fontSizePx = 12;
    private boolean suppressTextEvents;
    private Runnable textChangeListener;
    private Runnable focusLostListener;
    private IntConsumer gutterClickListener;
    private IntConsumer lineClickListener;
    private IntConsumer startMarkerToggleListener;
    private IntConsumer caretLineListener;
    private Runnable replacementPromptListener;
    private Subscription textSubscription;
    private int lastCaretLine = -1;

    public ProgramGcodeEditor() {
        this.codeArea = new CodeArea();
        this.codeArea.setEditable(true);
        this.codeArea.setWrapText(false);
        this.codeArea.setFocusTraversable(true);
        this.codeArea.getStyleClass().addAll("gcode-code-area", "gcode-editor");
        this.installEditingMenu();
        this.installKeyboardShortcuts();
        this.installMarkerGutter();
        this.codeArea.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() != MouseButton.PRIMARY || mouseEvent.isMiddleButtonDown()) {
                return;
            }
            if (mouseEvent.getX() <= GUTTER_WIDTH + 2.0) {
                return;
            }
            int line = this.lineAt(mouseEvent.getX(), mouseEvent.getY());
            if (line > 0 && this.lineClickListener != null) {
                mouseEvent.consume();
                this.lineClickListener.accept(line);
            }
        });
        this.textSubscription = this.codeArea.plainTextChanges().successionEnds(Duration.ofMillis(40)).subscribe(ignore -> {
            // Раскраска нужна и при правке, и когда текст подставили программно.
            this.applySyntaxHighlighting();
            if (this.suppressTextEvents || this.textChangeListener == null) {
                return;
            }
            this.textChangeListener.run();
        });
        this.codeArea.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused && this.focusLostListener != null) {
                this.focusLostListener.run();
            }
        });
        this.codeArea.selectionProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasSelection = newValue != null && newValue.getLength() > 0;
            // Тяжёлый O(N) рестайл подсветки строки нужен только при СМЕНЕ наличия
            // выделения (появилось/исчезло), а не на каждый шаг протяжки ЛКМ — иначе
            // выделение блока лагает. Сама подсветка/выделение при этом не ломаются.
            if (hasSelection != this.textSelectionActive) {
                this.textSelectionActive = hasSelection;
                Platform.runLater(this::refreshParagraphStyles);
            }
            Platform.runLater(this::notifyCaretLine);
        });
        this.codeArea.caretPositionProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(this::notifyCaretLine));
        this.scrollPane = new VirtualizedScrollPane<>(this.codeArea);
        this.scrollPane.setFocusTraversable(false);
        this.scrollPane.getStyleClass().add("gcode-editor-scroll");
        this.gutterShield = new Region();
        this.gutterShield.getStyleClass().add("program-gutter-shield");
        this.gutterShield.setMinWidth(GUTTER_WIDTH);
        this.gutterShield.setPrefWidth(GUTTER_WIDTH);
        this.gutterShield.setMaxWidth(GUTTER_WIDTH);
        this.gutterShield.setPickOnBounds(true);
        this.editorPane = new AnchorPane(this.scrollPane, this.gutterShield);
        AnchorPane.setTopAnchor(this.scrollPane, 0.0);
        AnchorPane.setBottomAnchor(this.scrollPane, 4.0);
        AnchorPane.setLeftAnchor(this.scrollPane, 0.0);
        AnchorPane.setRightAnchor(this.scrollPane, 0.0);
        AnchorPane.setTopAnchor(this.gutterShield, 0.0);
        AnchorPane.setBottomAnchor(this.gutterShield, 4.0);
        AnchorPane.setLeftAnchor(this.gutterShield, 0.0);
        this.installGutterShield();
        this.root = new HBox(this.editorPane);
        this.root.setAlignment(Pos.CENTER_LEFT);
        this.root.getStyleClass().add("program-editor-row");
        HBox.setHgrow(this.editorPane, Priority.ALWAYS);
        this.gutterShield.toFront();
        this.installRoundedClip(this.root);
        this.applyFontSize(12);
    }

    private void installRoundedClip(Region region) {
        Runnable runnable = () -> {
            double d = region.getWidth();
            double d2 = region.getHeight();
            if (d <= 0.0 || d2 <= 0.0) {
                return;
            }
            Rectangle rectangle = new Rectangle(d, d2);
            rectangle.setArcWidth(16.0);
            rectangle.setArcHeight(16.0);
            region.setClip(rectangle);
        };
        region.widthProperty().addListener((observableValue, number, number2) -> runnable.run());
        region.heightProperty().addListener((observableValue, number, number2) -> runnable.run());
        runnable.run();
    }

    private void installGutterShield() {
        this.gutterShield.setOnMousePressed(mouseEvent -> {
            if (mouseEvent.getButton() != MouseButton.PRIMARY) {
                return;
            }
            mouseEvent.consume();
            this.handleGutterShieldPress(mouseEvent);
        });
    }

    private void handleGutterShieldPress(MouseEvent mouseEvent) {
        Point2D local = this.codeArea.screenToLocal(mouseEvent.getScreenX(), mouseEvent.getScreenY());
        int line = this.lineAt(GUTTER_WIDTH * 0.5, local.getY());
        if (line <= 0) {
            return;
        }
        if (line == this.startMarkerLine && this.startMarkerToggleListener != null) {
            this.startMarkerToggleListener.accept(line);
            return;
        }
        if (this.gutterClickListener != null) {
            this.gutterClickListener.accept(line);
        }
    }

    private void installMarkerGutter() {
        this.refreshMarkerGutter();
    }

    private void refreshMarkerGutter() {
        this.codeArea.setParagraphGraphicFactory(this::createMarkerCell);
    }

    private Node createMarkerCell(int n) {
        int n2 = n + 1;
        StackPane stackPane = new StackPane();
        stackPane.setMinWidth(GUTTER_WIDTH);
        stackPane.setPrefWidth(GUTTER_WIDTH);
        stackPane.setMaxWidth(GUTTER_WIDTH);
        stackPane.setMinHeight(Math.max(14.0, this.fontSizePx * 1.15));
        stackPane.getStyleClass().add("program-marker-cell");
        if (n2 == this.highlightLine) {
            stackPane.getStyleClass().add("program-marker-cell-frame");
        }
        stackPane.setCursor(Cursor.HAND);
        if (n2 == this.startMarkerLine) {
            double d = Math.max(4.0, Math.min(9.0, this.fontSizePx * 0.32));
            Circle circle = new Circle(d, Color.web("#dc2626"));
            circle.setStroke(Color.web("#7f1d1d"));
            circle.setStrokeWidth(1.0);
            circle.setCursor(Cursor.HAND);
            circle.setOnMousePressed(mouseEvent -> {
                mouseEvent.consume();
                if (this.startMarkerToggleListener != null) {
                    this.startMarkerToggleListener.accept(n2);
                }
            });
            stackPane.getChildren().add(circle);
            StackPane.setAlignment(circle, Pos.CENTER);
        }
        stackPane.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (mouseEvent.getTarget() instanceof Circle) {
                return;
            }
            mouseEvent.consume();
            if (this.gutterClickListener != null) {
                this.gutterClickListener.accept(n2);
            }
        });
        return stackPane;
    }

    private void installEditingMenu() {
        MenuItem cut = new MenuItem(I18n.text("editor.001"));
        cut.setAccelerator(KeyCombination.keyCombination("Ctrl+X"));
        cut.setOnAction(event -> this.codeArea.cut());
        MenuItem copy = new MenuItem(I18n.text("editor.002"));
        copy.setAccelerator(KeyCombination.keyCombination("Ctrl+C"));
        copy.setOnAction(event -> this.codeArea.copy());
        MenuItem paste = new MenuItem(I18n.text("editor.003"));
        paste.setAccelerator(KeyCombination.keyCombination("Ctrl+V"));
        paste.setOnAction(event -> this.codeArea.paste());
        MenuItem selectAll = new MenuItem(I18n.text("editor.004"));
        selectAll.setAccelerator(KeyCombination.keyCombination("Ctrl+A"));
        selectAll.setOnAction(event -> this.codeArea.selectAll());
        MenuItem replaceAll = new MenuItem(I18n.text("editor.005"));
        replaceAll.setOnAction(event -> this.replaceAllSelected());
        MenuItem setReplacement = new MenuItem(I18n.text("editor.006"));
        setReplacement.setOnAction(event -> {
            if (this.replacementPromptListener != null) {
                this.replacementPromptListener.run();
            }
        });
        ContextMenu contextMenu = new ContextMenu(
                cut, copy, paste, new SeparatorMenuItem(), selectAll,
                new SeparatorMenuItem(), replaceAll, setReplacement);
        this.codeArea.setContextMenu(contextMenu);
    }

    /** ПКМ → «Заменить всё выделенное»: меняет ВСЕ вхождения выделенного текста на введённое. */
    private void replaceAllSelected() {
        String selected = this.codeArea.getSelectedText();
        if (selected == null || selected.isEmpty()) {
            this.showEditorDialog(Alert.AlertType.INFORMATION, I18n.text("editor.007"),
                    I18n.text("editor.008"));
            return;
        }
        try {
            TextInputDialog dialog = new TextInputDialog("");
            dialog.setTitle(I18n.text("editor.009"));
            dialog.setHeaderText(I18n.text("editor.010") + abbreviate(selected) + I18n.text("app.026"));
            dialog.setContentText(I18n.text("app.028"));
            this.attachDialogOwner(dialog);
            Optional<String> answer = dialog.showAndWait();
            if (answer.isEmpty()) {
                return;
            }
            String replacement = answer.get();
            if (selected.equals(replacement)) {
                this.showEditorDialog(Alert.AlertType.INFORMATION, I18n.text("editor.007"),
                        I18n.text("editor.012"));
                return;
            }
            String text = this.codeArea.getText();
            int count = countOccurrences(text, selected);
            if (count == 0) {
                this.showEditorDialog(Alert.AlertType.INFORMATION, I18n.text("editor.007"), I18n.text("editor.013"));
                return;
            }
            String replaced = text.replace(selected, replacement);
            int caret = Math.min(this.codeArea.getCaretPosition(), replaced.length());
            this.codeArea.replaceText(replaced);
            this.codeArea.moveTo(Math.max(0, caret));
            this.showEditorDialog(Alert.AlertType.INFORMATION, I18n.text("editor.007"),
                    I18n.text("editor.014") + count + ".");
        } catch (RuntimeException ex) {
            this.showEditorDialog(Alert.AlertType.ERROR, I18n.text("editor.015"),
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private static int countOccurrences(String text, String sub) {
        if (text == null || sub == null || sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(sub, index)) >= 0) {
            ++count;
            index += sub.length();
        }
        return count;
    }

    private static String abbreviate(String value) {
        String oneLine = value.replace("\r", "").replace("\n", "⏎");
        return oneLine.length() > 40 ? oneLine.substring(0, 40) + "…" : oneLine;
    }

    private void attachDialogOwner(javafx.scene.control.Dialog<?> dialog) {
        if (this.codeArea.getScene() != null && this.codeArea.getScene().getWindow() != null) {
            dialog.initOwner(this.codeArea.getScene().getWindow());
        }
        // Тема диалога = текущей теме приложения: тот же app.css + класс dark-theme
        // на корне диалога, что и у настроек/истории.
        javafx.scene.control.DialogPane pane = dialog.getDialogPane();
        java.net.URL css = ProgramGcodeEditor.class.getResource("/app.css");
        if (css != null) {
            String href = css.toExternalForm();
            // Лист — на сцену окна: правилам нужен корень сцены (.root).
            pane.getStylesheets().remove(href);
            if (pane.getScene() != null) {
                if (!pane.getScene().getStylesheets().contains(href)) pane.getScene().getStylesheets().add(href);
            } else if (!pane.getStylesheets().contains(href)) {
                pane.getStylesheets().add(href);
            }
        }
        boolean dark = this.isDarkThemeActive();
        pane.getStyleClass().remove("dark-theme");
        if (dark) {
            pane.getStyleClass().add("dark-theme");
        }
    }

    /** Тёмная тема активна, если у корня сцены/предков редактора есть класс dark-theme. */
    private boolean isDarkThemeActive() {
        if (this.codeArea.getScene() != null
                && this.codeArea.getScene().getRoot() != null
                && this.codeArea.getScene().getRoot().getStyleClass().contains("dark-theme")) {
            return true;
        }
        javafx.scene.Parent parent = this.codeArea.getParent();
        while (parent != null) {
            if (parent.getStyleClass().contains("dark-theme")) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private void showEditorDialog(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        this.attachDialogOwner(alert);
        alert.showAndWait();
    }

    private void installKeyboardShortcuts() {
        this.codeArea.addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (!keyEvent.isControlDown()) {
                return;
            }
            switch (keyEvent.getCode()) {
                case V -> {
                    this.codeArea.paste();
                    keyEvent.consume();
                }
                case C -> {
                    this.codeArea.copy();
                    keyEvent.consume();
                }
                case X -> {
                    this.codeArea.cut();
                    keyEvent.consume();
                }
                case A -> {
                    this.codeArea.selectAll();
                    keyEvent.consume();
                }
                case Z -> {
                    if (keyEvent.isShiftDown()) {
                        this.codeArea.redo();
                    } else {
                        this.codeArea.undo();
                    }
                    keyEvent.consume();
                }
                case Y -> {
                    this.codeArea.redo();
                    keyEvent.consume();
                }
                default -> {
                }
            }
        });
    }

    public Node getNode() {
        return this.root;
    }

    public CodeArea getCodeArea() {
        return this.codeArea;
    }

    public void dispose() {
        if (this.textSubscription != null) {
            this.textSubscription.unsubscribe();
            this.textSubscription = null;
        }
    }

    /**
     * Красит текст: G, M, оси, числа, комментарии, слова синхронизации каналов.
     *
     * <p>Раскладка считается одним проходом по тексту, поэтому на программе в
     * несколько тысяч строк это незаметно. Ошибку тут глотаем молча: подсветка —
     * не то, из-за чего редактор должен перестать работать.
     */
    private void applySyntaxHighlighting() {
        try {
            String text = this.codeArea.getText();
            this.codeArea.setStyleSpans(0, GcodeSyntaxHighlighter.highlight(text));
        } catch (RuntimeException error) {
            // оставляем текст без раскраски
        }
    }

    public void setOnTextChange(Runnable runnable) {
        this.textChangeListener = runnable;
    }

    public void setOnFocusLost(Runnable runnable) {
        this.focusLostListener = runnable;
    }

    public void setOnGutterClick(IntConsumer intConsumer) {
        this.gutterClickListener = intConsumer;
    }

    public void setOnLineClick(IntConsumer intConsumer) {
        this.lineClickListener = intConsumer;
    }

    public void setOnStartMarkerToggle(IntConsumer intConsumer) {
        this.startMarkerToggleListener = intConsumer;
    }

    public int getStartMarkerLine() {
        return this.startMarkerLine;
    }

    public String getText() {
        return this.codeArea.getText();
    }

    public void setText(String string) {
        this.suppressTextEvents = true;
        this.codeArea.replaceText(string != null ? string : "");
        this.suppressTextEvents = false;
        this.applySyntaxHighlighting();
        this.refreshParagraphStyles();
        this.refreshMarkerGutter();
        this.codeArea.requestLayout();
    }

    public void clear() {
        this.setText("");
    }

    public void requestFocus() {
        this.codeArea.requestFocus();
    }

    public void releaseEditorFocus() {
        this.codeArea.deselect();
    }

    public void setOnCaretLineChanged(IntConsumer listener) {
        this.caretLineListener = listener;
    }

    public int getCaretLineNumber() {
        String text = this.codeArea.getText();
        int caret = Math.max(0, Math.min(this.codeArea.getCaretPosition(), text.length()));
        int line = 1;
        for (int i = 0; i < caret; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private void notifyCaretLine() {
        if (this.caretLineListener == null) {
            return;
        }
        int line = this.getCaretLineNumber();
        if (line == this.lastCaretLine) {
            return;
        }
        this.lastCaretLine = line;
        this.caretLineListener.accept(line);
    }

    public int getCaretPosition() {
        return this.codeArea.getCaretPosition();
    }

    public void positionCaret(int n) {
        this.codeArea.moveTo(n);
    }

    public void selectRange(int n, int n2) {
        this.codeArea.selectRange(n, n2);
    }

    public void revealAndSelectRange(int n, int n2) {
        if (n < 0) {
            return;
        }
        int n3 = Math.max(n, n2);
        this.codeArea.selectRange(n, n3);
        int n4 = Math.max(0, Math.min(this.codeArea.offsetToPosition(n, TwoDimensional.Bias.Forward).getMajor(), this.codeArea.getParagraphs().size() - 1));
        this.codeArea.showParagraphInViewport(n4);
        Platform.runLater(() -> {
            this.codeArea.showParagraphInViewport(n4);
            this.codeArea.requestFocus();
        });
    }

    /** Заменяет диапазон [start, end) на replacement (для пошаговой замены «Заменить»). */
    public void replaceRange(int start, int end, String replacement) {
        int length = this.codeArea.getLength();
        int from = Math.max(0, Math.min(start, length));
        int to = Math.max(from, Math.min(end, length));
        this.codeArea.replaceText(from, to, replacement != null ? replacement : "");
    }

    /** Слушатель пункта ПКМ «Заменить на…» — спрашивает у пользователя новый текст замены. */
    public void setOnRequestReplacementPrompt(Runnable listener) {
        this.replacementPromptListener = listener;
    }

    public int getLineCount() {
        return Math.max(1, this.codeArea.getParagraphs().size());
    }

    public String getLineText(int n) {
        if (n <= 0) {
            return "";
        }
        int n2 = n - 1;
        if (n2 >= this.codeArea.getParagraphs().size()) {
            return "";
        }
        int n3 = this.codeArea.getAbsolutePosition(n2, 0);
        int n4 = n2 + 1 < this.codeArea.getParagraphs().size() ? this.codeArea.getAbsolutePosition(n2 + 1, 0) : this.codeArea.getLength();
        return this.codeArea.getText(n3, Math.max(n3, n4)).replace("\n", "");
    }

    public void applyFontSize(int n) {
        this.fontSizePx = Math.max(10, Math.min(32, n));
        this.codeArea.setStyle("-fx-font-family: 'Consolas', 'Cascadia Mono', 'Courier New', monospace; -fx-font-size: " + this.fontSizePx + "px;");
        this.refreshParagraphStyles();
        this.refreshMarkerGutter();
        this.codeArea.requestLayout();
    }

    public int getFontSizePx() {
        return this.fontSizePx;
    }

    public void showLine(int n) {
        if (n <= 0) {
            return;
        }
        int n2 = Math.min(n - 1, Math.max(0, this.codeArea.getParagraphs().size() - 1));
        this.codeArea.showParagraphInViewport(n2);
    }

    public void setStartMarkerLine(int n) {
        this.startMarkerLine = n;
        this.refreshMarkerGutter();
        if (n > 0) {
            Platform.runLater(() -> this.showLine(n));
        }
    }

    public void clearStartMarker() {
        this.setStartMarkerLine(-1);
    }

    public void setHighlightLine(int n, String string) {
        this.highlightLine = n;
        this.highlightStyle = string != null && !string.isBlank() ? string : "program-line-active";
        this.refreshParagraphStyles();
        this.refreshMarkerGutter();
        Platform.runLater(() -> {
            this.refreshParagraphStyles();
            this.refreshMarkerGutter();
            this.codeArea.requestLayout();
        });
    }

    public void clearHighlight() {
        this.setHighlightLine(-1, null);
    }

    public int lineAt(double d, double d2) {
        CharacterHit characterHit = this.codeArea.hit(d, d2);
        if (characterHit == null) {
            return -1;
        }
        return this.codeArea.offsetToPosition(characterHit.getInsertionIndex(), TwoDimensional.Bias.Backward).getMajor() + 1;
    }

    /**
     * Подсветка активной строки — стилем абзаца, а не стилем текста.
     *
     * <p>Раньше здесь на каждый абзац шёл {@code clearStyle}, и раскраска синтаксиса
     * стиралась сразу после того, как её применили: левое окно оставалось одноцветным.
     * Стиль абзаца красит фон строки и с раскраской текста не спорит.
     */
    private void refreshParagraphStyles() {
        int n = this.codeArea.getParagraphs().size();
        boolean suppressLineHighlight = this.textSelectionActive;
        for (int i = 0; i < n; ++i) {
            boolean active = !suppressLineHighlight
                    && this.highlightLine > 0
                    && i + 1 == this.highlightLine;
            this.codeArea.setParagraphStyle(i, active
                    ? Collections.singleton(this.highlightStyle)
                    : Collections.emptyList());
        }
    }

    public void scrollToLine(int n) {
        if (n <= 0) {
            return;
        }
        this.showLine(n);
    }

    /** Переход к строке (нумерация с 1): показывает её и выделяет целиком — «перейти к ошибке». */
    public void selectLine(int line) {
        if (line <= 0) {
            return;
        }
        String text = this.codeArea.getText();
        int start = 0;
        int current = 1;
        for (int i = 0; i < text.length() && current < line; ++i) {
            if (text.charAt(i) == '\n') {
                start = i + 1;
                ++current;
            }
        }
        if (current < line) {
            return;
        }
        int end = text.indexOf('\n', start);
        if (end < 0) {
            end = text.length();
        }
        this.revealAndSelectRange(start, end);
    }
}
