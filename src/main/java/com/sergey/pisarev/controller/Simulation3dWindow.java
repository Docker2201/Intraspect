package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.util.AppIconHelper;
import com.sergey.pisarev.util.I18n;
import java.io.IOException;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Отдельное окно 3D-симуляции.
 */
public final class Simulation3dWindow {
    private static final Logger LOGGER = Logger.getLogger(Simulation3dWindow.class.getName());
    private static final double WINDOW_WIDTH = 1320.0;
    private static final double WINDOW_HEIGHT = 820.0;

    private Stage stage;
    private Window currentOwner;
    private Simulation3dController controller;

    public java.util.Map<String,Object> liveState() {
        if (stage == null || !stage.isShowing() || controller == null) return java.util.Map.of("open",false);
        return controller.liveState();
    }

    public java.util.Map<String,Object> control(java.util.Map<String,Object> arguments) {
        if (stage == null || !stage.isShowing() || controller == null)
            throw new IllegalStateException("Окно 3D не открыто");
        return controller.controlLiveCycle(arguments);
    }

    public com.sergey.pisarev.ai.AppAccess.Picture livePicture(int width,int height) {
        if (stage == null || !stage.isShowing() || controller == null)
            return com.sergey.pisarev.ai.AppAccess.Picture.failed("Окно 3D не открыто");
        var root = stage.getScene().getRoot();
        var bounds = root.getLayoutBounds();
        double scale = Math.min(1,Math.min(Math.max(320,Math.min(2400,width))/bounds.getWidth(),
                Math.max(200,Math.min(1600,height))/bounds.getHeight()));
        var parameters = new javafx.scene.SnapshotParameters();
        parameters.setTransform(javafx.scene.transform.Transform.scale(scale,scale));
        return com.sergey.pisarev.ai.AppAccess.Picture.of(
                com.sergey.pisarev.ai.PngWriter.encode(root.snapshot(parameters,null)));
    }
    /** Кому сообщать о выбранной строке. Храним, чтобы обновление окна не теряло переход к коду. */
    private IntConsumer onSourceLineSelected = line -> { };
    public int displayedSetupSide() {
        return this.controller == null ? 0 : this.controller.displayedSetupSide();
    }
    public void syncEditorLine(int sourceLine) {
        if (this.stage != null && this.stage.isShowing() && this.controller != null) {
            this.controller.syncEditorLine(sourceLine);
        }
    }

    public void refreshIfOpen(SimulationContext context, boolean darkTheme) {
        if (this.stage == null || !this.stage.isShowing() || this.controller == null) {
            return;
        }
        // Раньше здесь ставился пустой обработчик, и после обновления клик в 3D
        // переставал открывать нужную строку программы.
        this.controller.setData(context, darkTheme, this.onSourceLineSelected, this::focusAndFit);
        this.controller.applyTheme(darkTheme);
    }

    public void updateMachiningProgress(SimulationContext context, boolean darkTheme, int contourMoveIndex) {
        if (this.stage == null || this.controller == null) {
            return;
        }
        // The editor executes one setup; it must not replace a complete two-setup 3D session.
        if (this.controller.hasSetupSequence()) return;
        if (!this.stage.isShowing()) {
            this.controller.setInitialMachiningProgress(contourMoveIndex);
            return;
        }
        this.controller.syncContext(context, darkTheme);
        this.controller.setMachiningProgress(contourMoveIndex);
    }

    public void open(
            Window owner,
            SimulationContext context,
            boolean darkTheme,
            IntConsumer onSourceLineSelected,
            boolean rebuildModel
    ) {
        this.open(owner, context, darkTheme, onSourceLineSelected, rebuildModel, -1);
    }

    public void open(
            Window owner,
            SimulationContext context,
            boolean darkTheme,
            IntConsumer onSourceLineSelected,
            boolean rebuildModel,
            int initialContourMoveIndex
    ) {
        if (onSourceLineSelected != null) {
            this.onSourceLineSelected = onSourceLineSelected;
        }
        if (this.stage != null && this.stage.isShowing()) {
            if (owner != null) {
                this.currentOwner = owner;
            }
            this.controller.setInitialMachiningProgress(initialContourMoveIndex);
            this.controller.setData(context, darkTheme, onSourceLineSelected, this::focusAndFit);
            this.controller.applyTheme(darkTheme);
            this.raiseTemporarily();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(Simulation3dWindow.class.getResource("/simulation3d.fxml"),
                    com.sergey.pisarev.util.I18n.bundle());
            Parent root = loader.load();
            this.controller = loader.getController();
            // Окно не должно превышать видимую область экрана (маленькие разрешения).
            Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
            double sceneWidth = Math.min(WINDOW_WIDTH, visualBounds.getWidth() * 0.92);
            double sceneHeight = Math.min(WINDOW_HEIGHT, visualBounds.getHeight() * 0.9);
            Scene scene = new Scene(root, sceneWidth, sceneHeight);
            java.net.URL css = Simulation3dWindow.class.getResource("/app.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            if (darkTheme) {
                root.getStyleClass().add("dark-theme");
            }
            this.stage = new Stage();
            this.currentOwner = owner;
            this.stage.setTitle(I18n.text("sim.046"));
            if (owner != null) {
                this.stage.initOwner(owner);
            }
            this.stage.setScene(scene);
            this.stage.setOnHidden(event -> this.controller.stopPlayback());
            this.centerStageOnOwner(owner);
            AppIconHelper.applyToStage(this.stage, owner);
            this.stage.setMinWidth(Math.min(820.0, visualBounds.getWidth() * 0.85));
            this.stage.setMinHeight(Math.min(560.0, visualBounds.getHeight() * 0.85));
            this.stage.show();
            this.centerStageOnOwner(owner);
            this.raiseTemporarily();
            this.controller.setInitialMachiningProgress(initialContourMoveIndex);
            this.controller.showLoadingOverlay(I18n.text("sim.047"), I18n.text("sim.048"));
            Platform.runLater(() -> this.loadDataAfterFirstLayout(
                    context,
                    darkTheme,
                    onSourceLineSelected));
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Failed to open 3D simulation window", exception);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to initialize 3D simulation", exception);
            this.stage = null;
            this.controller = null;
        }
    }

    private void loadDataAfterFirstLayout(
            SimulationContext context,
            boolean darkTheme,
            IntConsumer onSourceLineSelected
    ) {
        if (this.stage == null || this.controller == null) {
            return;
        }
        try {
            this.controller.setData(context, darkTheme, onSourceLineSelected, this::focusFitAndReleaseInitialLoading);
            this.controller.applyTheme(darkTheme);
            this.raiseTemporarily();
        } catch (RuntimeException dataError) {
            LOGGER.log(Level.WARNING, "3D model build failed after window open", dataError);
            this.controller.hideLoadingOverlay();
            this.controller.showExternalError(I18n.text("sim.049") + this.errorMessage(dataError));
            if (!this.stage.isShowing()) {
                this.stage.show();
            }
        }
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message;
    }


    private void centerStageOnOwner(Window owner) {
        if (this.stage == null || owner == null) {
            return;
        }
        double ownerW = Math.max(1.0, owner.getWidth());
        double ownerH = Math.max(1.0, owner.getHeight());
        double stageW = this.stage.getWidth() > 1.0 ? this.stage.getWidth() : WINDOW_WIDTH;
        double stageH = this.stage.getHeight() > 1.0 ? this.stage.getHeight() : WINDOW_HEIGHT;
        this.stage.setX(owner.getX() + (ownerW - stageW) / 2.0);
        this.stage.setY(owner.getY() + (ownerH - stageH) / 2.0);
    }

    private void raiseTemporarily() {
        if (this.stage == null) {
            return;
        }
        if (!this.stage.isShowing()) {
            this.stage.show();
        }
        this.centerStageOnOwner(this.currentOwner);
        // Не включаем AlwaysOnTop: initOwner(owner) уже держит 3D-окно в группе Chekator,
        // а setAlwaysOnTop поднимает его поверх вообще всех окон Windows.
        this.stage.setAlwaysOnTop(false);
        this.stage.toFront();
        this.stage.requestFocus();
        Platform.runLater(() -> {
            if (this.stage == null) {
                return;
            }
            this.stage.toFront();
            this.stage.requestFocus();
        });
    }

    private void focusAndFit() {
        if (this.stage == null || this.controller == null) {
            return;
        }
        this.raiseTemporarily();
        this.controller.scheduleViewportFitAfterLayout();
    }

    private void focusFitAndReleaseInitialLoading() {
        this.focusAndFit();
        if (this.controller != null) {
            this.controller.hideLoadingOverlay();
        }
    }
}
