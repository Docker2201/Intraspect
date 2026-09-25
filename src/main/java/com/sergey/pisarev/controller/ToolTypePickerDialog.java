package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.ToolTypeCatalog;
import com.sergey.pisarev.model.ToolTypeCategory;
import com.sergey.pisarev.model.ToolTypeEntry;
import com.sergey.pisarev.util.I18n;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Tool type and insert-position selector, close to the SinuTrain Tool types window.
 */
public final class ToolTypePickerDialog {
    public record ToolTypeSelection(ToolTypeEntry entry, int position) {
    }

    private ToolTypePickerDialog() {
    }

    public static Optional<ToolTypeSelection> show(Window owner, boolean darkTheme) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.text("toolpick.001"));
        // \u041c\u0438\u043d\u0438\u043c\u0430\u043b\u044c\u043d\u044b\u0439 \u0440\u0430\u0437\u043c\u0435\u0440 \u043d\u0435 \u0434\u043e\u043b\u0436\u0435\u043d \u043f\u0440\u0435\u0432\u044b\u0448\u0430\u0442\u044c \u044d\u043a\u0440\u0430\u043d (\u043c\u0430\u0441\u0448\u0442\u0430\u0431 Windows 125\u2013200%),
        // \u0438\u043d\u0430\u0447\u0435 \u043e\u043a\u043d\u043e \u0432\u044b\u043b\u0435\u0437\u0430\u0435\u0442 \u0437\u0430 \u043a\u0440\u0430\u0439 \u0438 \u043a\u043d\u043e\u043f\u043a\u0443 \u0437\u0430\u043a\u0440\u044b\u0442\u0438\u044f \u043d\u0435 \u0432\u0438\u0434\u043d\u043e.
        javafx.geometry.Rectangle2D pickerScreen = javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(980.0, pickerScreen.getWidth() * 0.94));
        stage.setMinHeight(Math.min(620.0, pickerScreen.getHeight() * 0.92));
        loadStageIcons(stage);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("tool-picker-root");
        if (darkTheme) {
            root.getStyleClass().add("dark-theme");
        }

        HBox header = new HBox(14.0);
        header.getStyleClass().add("tool-picker-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 18, 12, 18));
        ImageView iconView = createHeaderIcon();
        if (iconView != null) {
            header.getChildren().add(iconView);
        }
        VBox titles = new VBox(2.0);
        Label title = new Label(I18n.text("toolpick.002"));
        title.getStyleClass().add("tool-picker-title");
        Label subtitle = new Label(I18n.text("toolpick.003"));
        subtitle.getStyleClass().add("tool-picker-subtitle");
        titles.getChildren().addAll(title, subtitle);
        header.getChildren().add(titles);
        root.setTop(header);

        TableView<ToolTypeEntry> table = new TableView<>();
        table.getStyleClass().add("tool-picker-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(40.0);
        table.setMinWidth(0.0);

        TableColumn<ToolTypeEntry, ToolTypeEntry> iconCol = new TableColumn<>("");
        iconCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        iconCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(ToolTypeEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                setGraphic(ToolIconFactory.smallIcon(item, 1));
                setTooltip(ToolIconFactory.tooltip(item, 1));
            }
        });
        iconCol.setPrefWidth(46.0);
        iconCol.setMaxWidth(56.0);

        TableColumn<ToolTypeEntry, Number> typeCol = new TableColumn<>(I18n.text("app.078"));
        typeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().typeCode()));
        typeCol.setPrefWidth(72.0);

        TableColumn<ToolTypeEntry, String> idCol = new TableColumn<>(I18n.text("toolpick.004"));
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                ToolTypeCatalog.russianName(data.getValue())));
        idCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                ToolTypeEntry entry = getTableRow() != null ? getTableRow().getItem() : null;
                if (empty || entry == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                setTooltip(ToolIconFactory.tooltip(entry, 1));
            }
        });

        TableColumn<ToolTypeEntry, Number> posCol = new TableColumn<>(I18n.text("toolpick.005"));
        posCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().positionVariants()));
        posCol.setPrefWidth(86.0);
        table.getColumns().addAll(iconCol, typeCol, idCol, posCol);

        StackPane previewPane = new StackPane();
        // Высота по содержимому: фиксированные 150px были меньше иконки с подписями,
        // и превью «вылетало» за карточку вверх на крупном масштабе.
        previewPane.setMinHeight(Region.USE_PREF_SIZE);
        previewPane.getStyleClass().add("tool-picker-preview");
        GridPane positionGrid = new GridPane();
        positionGrid.setHgap(8.0);
        positionGrid.setVgap(8.0);
        positionGrid.setAlignment(Pos.CENTER);
        HBox positionPageControls = new HBox(8.0);
        positionPageControls.getStyleClass().add("tool-position-page-controls");
        positionPageControls.setAlignment(Pos.CENTER);
        final int[] selectedPosition = {1};
        final int[] positionPage = {0};

        Runnable[] updatePreview = new Runnable[1];
        updatePreview[0] = () -> {
            ToolTypeEntry entry = table.getSelectionModel().getSelectedItem();
            updatePreview(previewPane, positionGrid, positionPageControls, table, entry,
                    selectedPosition, positionPage, updatePreview[0]);
        };

        table.setRowFactory(view -> {
            TableRow<ToolTypeEntry> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                row.setTooltip(newValue != null ? ToolIconFactory.tooltip(newValue, selectedPosition[0]) : null);
            });
            return row;
        });

        final ToolTypeCategory[] category = {ToolTypeCategory.TURNING};
        Runnable refresh = () -> {
            table.setItems(FXCollections.observableArrayList(ToolTypeCatalog.byCategory(category[0])));
            if (!table.getItems().isEmpty()) {
                table.getSelectionModel().selectFirst();
            }
            updatePreview[0].run();
        };

        VBox rightPanel = new VBox(10.0);
        rightPanel.getStyleClass().add("tool-picker-categories");
        rightPanel.setPadding(new Insets(12, 16, 12, 12));
        rightPanel.setPrefWidth(236.0);
        rightPanel.setMinWidth(224.0);
        rightPanel.setMaxWidth(260.0);
        rightPanel.setFillWidth(true);
        Label groupsTitle = new Label(I18n.text("toolpick.006"));
        groupsTitle.getStyleClass().add("tool-picker-groups-title");
        rightPanel.getChildren().add(groupsTitle);
        for (ToolTypeCategory candidate : ToolTypeCategory.values()) {
            Button button = new Button(candidate.getTitle());
            button.getStyleClass().add("tool-picker-category-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setWrapText(true);
            button.setMinHeight(38.0);
            button.setOnAction(event -> {
                category[0] = candidate;
                refresh.run();
            });
            rightPanel.getChildren().add(button);
        }
        Label previewLabel = new Label(I18n.text("app.083"));
        previewLabel.getStyleClass().add("tool-picker-groups-title");
        rightPanel.getChildren().addAll(previewLabel, previewPane, positionGrid, positionPageControls);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        rightPanel.getChildren().add(spacer);

        HBox buttons = new HBox(10.0);
        // По центру колонки — на одной оси с пагинацией «1 / 2».
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(8, 0, 0, 0));
        Button cancel = new Button(I18n.text("app.039"));
        cancel.getStyleClass().add("secondary-button");
        Button ok = new Button(I18n.text("toolpick.007"));
        ok.getStyleClass().add("primary-button");
        ok.setDefaultButton(true);
        buttons.getChildren().addAll(cancel, ok);
        rightPanel.getChildren().add(buttons);

        BorderPane tablePane = new BorderPane(table);
        tablePane.getStyleClass().add("tool-picker-table-pane");
        tablePane.setPadding(new Insets(12, 8, 12, 16));
        tablePane.setMinWidth(0.0);
        root.setCenter(tablePane);
        // На маленьких экранах (масштаб Windows 150–200%) правая колонка не помещается
        // по высоте — оборачиваем в ScrollPane с аккуратным скроллбаром.
        javafx.scene.control.ScrollPane rightScroll = new javafx.scene.control.ScrollPane(rightPanel);
        rightScroll.setFitToWidth(true);
        rightScroll.setFitToHeight(true);
        rightScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        rightScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightScroll.getStyleClass().add("tool-picker-right-scroll");
        rightScroll.setMinWidth(Region.USE_PREF_SIZE);
        root.setRight(rightScroll);

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedPosition[0] = 1;
            positionPage[0] = 0;
            updatePreview[0].run();
        });

        final ToolTypeSelection[] selected = new ToolTypeSelection[1];
        ok.setOnAction(event -> {
            ToolTypeEntry entry = table.getSelectionModel().getSelectedItem();
            if (entry != null) {
                selected[0] = new ToolTypeSelection(entry, selectedPosition[0]);
            }
            stage.close();
        });
        cancel.setOnAction(event -> stage.close());
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                ToolTypeEntry entry = table.getSelectionModel().getSelectedItem();
                if (entry != null) {
                    selected[0] = new ToolTypeSelection(entry, selectedPosition[0]);
                }
                stage.close();
            }
        });

        refresh.run();
        // Диалог не должен превышать видимую область экрана (маленькие разрешения).
        javafx.geometry.Rectangle2D pickerBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(
                root,
                Math.min(1500.0, pickerBounds.getWidth() * 0.94),
                Math.min(900.0, pickerBounds.getHeight() * 0.92));
        scene.getStylesheets().add(ToolTypePickerDialog.class.getResource("/app.css").toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setScene(scene);
        stage.setMaxWidth(pickerBounds.getWidth());
        stage.setMaxHeight(pickerBounds.getHeight());
        // Центрируем в пределах экрана — кнопка закрытия всегда видна.
        stage.setX(pickerBounds.getMinX() + Math.max(0.0, (pickerBounds.getWidth() - scene.getWidth()) / 2.0));
        stage.setY(pickerBounds.getMinY() + Math.max(0.0, (pickerBounds.getHeight() - scene.getHeight()) / 2.0));
        stage.showAndWait();
        return Optional.ofNullable(selected[0]);
    }

    private static void updatePreview(
            StackPane previewPane,
            GridPane positionGrid,
            HBox positionPageControls,
            TableView<ToolTypeEntry> table,
            ToolTypeEntry entry,
            int[] selectedPosition,
            int[] positionPage,
            Runnable refresh
    ) {
        previewPane.getChildren().clear();
        positionGrid.getChildren().clear();
        positionPageControls.getChildren().clear();
        if (entry == null) {
            return;
        }
        selectedPosition[0] = Math.max(1, Math.min(selectedPosition[0], entry.positionVariants()));
        previewPane.getChildren().add(ToolIconFactory.preview(entry, selectedPosition[0]));
        int variants = Math.max(1, entry.positionVariants());
        int pageSize = 4;
        int pageCount = Math.max(1, (variants + pageSize - 1) / pageSize);
        positionPage[0] = Math.max(0, Math.min(positionPage[0], pageCount - 1));
        int first = positionPage[0] * pageSize + 1;
        int last = Math.min(variants, first + pageSize - 1);
        for (int position = first; position <= last; position++) {
            Button button = new Button();
            button.getStyleClass().add("secondary-button");
            button.getStyleClass().add("tool-position-button");
            button.setMinSize(70.0, 48.0);
            button.setPrefSize(70.0, 48.0);
            button.setGraphic(ToolIconFactory.positionGraphic(entry, position, 34.0));
            button.setTooltip(ToolIconFactory.tooltip(entry, position));
            boolean active = position == selectedPosition[0];
            if (active) {
                button.getStyleClass().add("simulation3d-mode-active");
            }
            configurePositionNumberColor(button, active);
            int selected = position;
            button.setOnAction(event -> {
                selectedPosition[0] = selected;
                table.refresh();
                refresh.run();
            });
            int index = position - first;
            positionGrid.add(button, index % 2, index / 2);
        }
        if (pageCount > 1) {
            Button previous = new Button("\u25c0");
            previous.getStyleClass().add("secondary-button");
            previous.setDisable(positionPage[0] == 0);
            Label pageLabel = new Label((positionPage[0] + 1) + " / " + pageCount);
            pageLabel.getStyleClass().add("muted-label");
            Button next = new Button("\u25b6");
            next.getStyleClass().add("secondary-button");
            next.setDisable(positionPage[0] >= pageCount - 1);
            previous.setOnAction(event -> {
                positionPage[0]--;
                refresh.run();
            });
            next.setOnAction(event -> {
                positionPage[0]++;
                refresh.run();
            });
            positionPageControls.getChildren().addAll(previous, pageLabel, next);
        }
    }

    private static void configurePositionNumberColor(Button button, boolean active) {
        Label number = findPositionNumber(button.getGraphic());
        if (number != null) {
            number.setStyle("");
        }
    }

    private static Label findPositionNumber(Node node) {
        if (node == null) {
            return null;
        }
        if (node instanceof Label label && label.getStyleClass().contains("tool-position-number")) {
            return label;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Label found = findPositionNumber(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ImageView createHeaderIcon() {
        java.net.URL url = ToolTypePickerDialog.class.getResource("/icon_512.png");
        if (url == null) {
            url = ToolTypePickerDialog.class.getResource("/icon_16.png");
        }
        if (url == null) {
            return null;
        }
        ImageView icon = new ImageView(new Image(url.toExternalForm(), 48, 48, true, true));
        icon.setFitWidth(48);
        icon.setFitHeight(48);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        return icon;
    }

    private static void loadStageIcons(Stage stage) {
        java.net.URL url = ToolTypePickerDialog.class.getResource("/icon_512.png");
        if (url == null) {
            url = ToolTypePickerDialog.class.getResource("/icon_16.png");
        }
        if (url != null) {
            stage.getIcons().add(new Image(url.toExternalForm()));
        }
    }
}
