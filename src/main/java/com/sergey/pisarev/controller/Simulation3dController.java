package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.MachineConfiguration;
import com.sergey.pisarev.model.MeshBuildResult;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkpieceDefinition;
import java.util.Locale;
import com.sergey.pisarev.service.LatheEquidistantPath;
import com.sergey.pisarev.util.UserSettings;
import com.sergey.pisarev.service.LatheMachineVisual3d;
import com.sergey.pisarev.service.LatheMeshBuilder;
import com.sergey.pisarev.service.LatheStockRemovalSimulator;
import com.sergey.pisarev.service.SinuTrainSideViewPainter;
import com.sergey.pisarev.service.StockRemovalMeshBuilder;
import com.sergey.pisarev.util.ToolLibraryStore;
import com.sergey.pisarev.util.I18n;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.util.Duration;
import javafx.fxml.Initializable;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.DepthTest;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;

/**
 * Окно 3D-симуляции: токарная деталь, срез SinuTrain-стиля, тонкие точки G-code.
 */
public class Simulation3dController implements Initializable {
    private static final String FINISHED_PART_TAG = "finished-part";
    private static final String STOCK_BLANK_TAG = "stock-blank";
    private static final String SIDE_GRID_TAG = "side-grid";
    private static final String CYCLE_TOOL_TAG = "cycle-tool";
    private static final String CYCLE_CUT_PREVIEW_TAG = "cycle-cut-preview";
    private static final String TRAJECTORY_3D_TAG = "trajectory-3d";
    private static final String TRAJECTORY_POINT_3D_TAG = "trajectory-point-3d";
    private static final String MEASURE_TOOL_TAG = "measure-tool";
    private static final String GCODE_MOVE_PROPERTY = "gcode-move";
    private static final double CYCLE_BASE_FEED_MM_PER_SEC = 38.0;
    /** Clear air a parked support keeps above the stock, mm. */
    private static final double SUPPORT_PARK_CLEARANCE_MM = 40.0;
    private static final long CYCLE_MESH_MIN_INTERVAL_NANOS_SMOOTH = 4_000_000L;
    private static final double CYCLE_ADVANCE_MM_PER_FRAME_SMOOTH = 0.54;
    private static final double CYCLE_SEGMENT_MM_SMOOTH = 0.25;
    private static final int CYCLE_MAX_MESH_GAP_SMOOTH = 6;
    private static final int CYCLE_MIN_VISUAL_MESH_LEAD_FRAMES = 12;
    private static final int CYCLE_MAX_VISUAL_MESH_LEAD_FRAMES = 220;
    private static final java.util.concurrent.ExecutorService CYCLE_MESH_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                var thread = new Thread(task, "chekator-cycle-3d-mesh");
                thread.setDaemon(true);
                return thread;
            });
    private com.sergey.pisarev.service.CyclePreviewMeshCache verticalPreviewMeshes;
    private static final double CYCLE_SPINDLE_RPM = 180.0;
    private static final double CYCLE_SPINDLE_DEG_PER_SEC = CYCLE_SPINDLE_RPM * 6.0;
    // Авто-оптимизация под слабые компьютеры: при устойчивом FPS ниже порога
    // снижаем частоту перестройки меша цикла, при восстановлении — возвращаем.
    private static final double CYCLE_AUTO_QUALITY_LOW_FPS = 55.0;
    private static final double CYCLE_AUTO_QUALITY_HIGH_FPS = 70.0;
    private static final int CYCLE_AUTO_QUALITY_MAX_LEVEL = 2;
    private static final int CYCLE_AUTO_QUALITY_DEGRADE_FRAMES = 90;
    private static final int CYCLE_AUTO_QUALITY_RESTORE_FRAMES = 420;

    private enum ViewMode {
        VIEW_3D,
        CYCLE,
        HALF_CUT,
        /** Та же 3D-деталь, фиксированный боковой ракурс + сетка (без вращения). */
        SIDE_GRAPH
    }

    private record MeshBuildNodes(TriangleMesh stockMesh, TriangleMesh finishedMesh) {
    }

    @FXML
    private BorderPane rootPane;
    @FXML
    private HBox toolbar;
    @FXML
    private HBox toolbarRight;
    @FXML
    private StackPane viewportStack;
    @FXML
    private Button view3dButton;
    @FXML
    private Button cycleButton;
    @FXML
    private Button halfCutButton;
    @FXML
    private Button sideViewButton;
    @FXML
    private Button toolpathButton;
    @FXML
    private Button measureButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label coordLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private StackPane loadingOverlay;
    @FXML
    private Region loadingSpinner;
    @FXML
    private Label loadingLabel;
    @FXML
    private Label loadingHint;
    private RotateTransition loadingSpinAnimation;
    private FadeTransition loadingFadeAnimation;
    private int activeLoadingRequests;

    private SubScene subScene;
    private Canvas halfCutCanvas;
    private Canvas cycleCanvas;
    private Canvas measureCanvas;
    private Label cycleFpsLabel;
    private Label cycleToolLabel;
    private Label measureReadoutLabel;
    private HBox cycleControls;
    private Button cyclePlayPauseButton;
    private Button cycleSpeedButton;
    private final LatheOrbitCamera orbitCamera = new LatheOrbitCamera();
    private final OrbitPivotGizmo pivotGizmo = new OrbitPivotGizmo();
    /** Маркер pivot на детали (для gizmo и pan-компенсации). */
    private final Group pivotMarker = new Group();
    private final SimulationGcodeOverlay gcodeOverlay = new SimulationGcodeOverlay();
    private final SimulationSideViewLabelsOverlay sideViewLabelsOverlay = new SimulationSideViewLabelsOverlay();
    private final Group worldGroup = new Group();
    private final Group orientationGroup = new Group();
    private final Group spinGroup = new Group();
    private final Group modelRoot = new Group();
    private final ArrayList<Point3D> measurePoints = new ArrayList<>(3);
    private Point3D pressedMeasurePoint;
    // Перетаскивание точек рулетки: индекс таскаемой точки и точки под курсором (-1 — нет).
    private int measureDragIndex = -1;
    private int measureHoverIndex = -1;
    private ViewMode viewMode = ViewMode.VIEW_3D;
    private boolean showToolpath = true;
    private boolean measureMode;
    private SimulationContext simulationContext = emptyContext();
    /** Final setup retains the preceding setup; cycle playback selects one at a time. */
    private SimulationContext completeSimulationContext = this.simulationContext;
    private boolean turnoverPreparing;
    private double turnoverProgress = -1.0;
    private TriangleMesh secondSetupInitialMesh;
    private static final double TURNOVER_SECONDS = 2.4;
    private final javafx.scene.transform.Rotate turnoverRotation =
            new javafx.scene.transform.Rotate(0, javafx.scene.transform.Rotate.X_AXIS);
    private String missingToolNotice = "";
    private boolean darkTheme;
    private final PhongMaterial trajectoryPointMaterial = new PhongMaterial();
    private final PhongMaterial trajectoryRapidPointMaterial = new PhongMaterial();
    private IntConsumer sourceLineConsumer;
    private String lastMeshError;
    private boolean view3dFitted;
    /** Контекст, для которого было выполнено первичное вписывание камеры (центрирование). */
    private SimulationContext lastFittedContext = null;
    private boolean lastFitHadFinishedPart;
    private double partCenterRadiusMm = 50.0;
    private double partCenterZMm;
    private double partHalfSpanZMm = 50.0;
    private double partMaxRadiusMm = 50.0;
    private List<double[]> cachedFinishedProfile = List.of();
    private List<GCodeMoveData> displayMoves = List.of();
    private double dragStartX;
    private double dragStartY;
    private boolean orbitDrag;
    private boolean panDrag;
    private boolean clickCandidate;
    private boolean orbitMoved;
    private boolean orbitPivotAnchored;
    private Point3D orbitPickLocal;
    private double orbitPressSceneX;
    private double orbitPressSceneY;
    private double halfCutPanX;
    private double halfCutPanY;
    private double halfCutZoom = 1.0;
    private SinuTrainSideViewPainter.Layout halfCutLayout;
    private GCodeMoveData halfCutHoverMove;
    /** -1 = полное снятие; иначе симуляция нарезки до индекса хода контура. */
    private int machiningMoveIndex = -1;
    private double modelSpinYawDeg;
    private double modelSpinPitchDeg;
    private double spinAnchorX;
    private double spinAnchorY;
    private double spinAnchorZ;
    private int simulationSelectedSourceLine = -1;
    private int profileLoadGeneration;
    private int meshBuildGeneration;
    /** Текущая сборка меша. Отменяем её при новом запросе, иначе старая считает впустую. */
    private javafx.concurrent.Task<?> runningMeshTask;
    private boolean pendingDefaultSideView = true;
    private PauseTransition overlayRefreshDebounce;
    private PauseTransition resizeFitDebounce;
    private long lastSidePanOverlayRefreshNanos;
    private boolean sidePanLabelRefreshQueued;
    private boolean sidePanOverlayRefreshQueued;
    private List<GCodeMoveData> cachedOverlayMoves = List.of();
    private List<GCodeMoveData> cachedCycleMoves = List.of();
    // Эквидистанта G41/G42 для позиции инструмента в цикле, 1:1 с cachedCycleMoves.
    private List<GCodeMoveData> cachedCycleCompMoves = List.of();
    private SimulationContext cachedCycleMovesContext;
    private AnimationTimer cycleAnimation;
    private boolean cyclePlaying;
    private double cycleProgress;
    private double cycleDistanceProgress;
    private double cycleSpeed = 1.0;
    private long lastCycleNanos;
    /** Предрассчитанные профили для всех сегментов цикла (индекс = activeIndex). */
    private final ArrayList<List<double[]>> prebuiltCycleProfiles = new ArrayList<>();
    /** Предрассчитанные MeshView для всех сегментов цикла. */
    private final ArrayList<MeshView> prebuiltCycleMeshViews = new ArrayList<>();
    private boolean cycleAllMeshesPrebuilt;
    private long lastCycleMeshRequestNanos;
    private List<GCodeMoveData> cycleDistanceMoves = List.of();
    private double[] cycleDistanceStops = new double[0];
    private double cycleTotalDistance;
    private SinuTrainSideViewPainter.Layout cycleLayout;
    private List<double[]> cachedCycleProfile = List.of();
    private int cachedCycleProfileIndex = Integer.MIN_VALUE;
    private int renderedCycleMeshIndex = Integer.MIN_VALUE;
    private int renderedCycleMeshFrame = Integer.MIN_VALUE;
    private final LinkedHashMap<Integer, List<double[]>> cycleProfileCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<double[]>> eldest) {
            return size() > 256;
        }
    };
    private boolean cycleMeshBuildRunning;
    private int cycleMeshGeneration;
    private int pendingCycleMeshIndex = Integer.MIN_VALUE;
    private int pendingCycleMeshFrame = Integer.MIN_VALUE;
    private List<GCodeMoveData> pendingCycleMeshMoves = List.of();
    private boolean cycleResetLoadingPending;
    private List<CncToolDefinition> cachedTools = List.of();
    private boolean overlayRefreshQueued;
    private boolean redraw2dQueued;
    private boolean redrawCycleQueued;
    private Group cycleTool3d;
    private com.sergey.pisarev.service.LatheCyclePath verticalCyclePath;
    private com.sergey.pisarev.service.AxialCycleStock verticalCycleStock;
    private Group cycleCutPreview3d;
    private Cylinder cycleCutPreviewBand;
    private Sphere cycleCutPreviewPoint;
    private Box cycleCutPreviewChip;
    private String cycleToolSignature = "";
    private double cycleWorkpieceSpinDeg;
    private double cycleFpsEma;
    private long cycleLastFpsLabelNanos;
    private double cycleLastFrameDtMs;
    private int cycleFrameCounter;
    private double cycleLastMeshBuildMs;
    // 0 — полное качество; 1..MAX — ступени снижения при низком FPS.
    private int cycleAutoQualityLevel;
    private int cycleLowFpsStreak;
    private int cycleHighFpsStreak;
    // Инкрементальная огибающая среза цикла: одна персистентная карта, в которую
    // применяются только новые полные ходы префикса; частичный последний ход
    // применяется с откатом затронутого диапазона Z (без клонирования карты).
    private final Object cycleEnvelopeLock = new Object();
    private java.util.TreeMap<Double, Double> cycleEnvelopeWork;
    private int cycleEnvelopeAppliedCount;
    private List<GCodeMoveData> cycleEnvelopeSourceMoves;
    private com.sergey.pisarev.service.LatheCuttingEnvelope cycleCuttingEnvelopeCache;
    private SimulationContext cycleCuttingEnvelopeContext;

    private boolean viewportFitAfterLayoutPending;
    private boolean resetOrthogonalViewPending;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        this.overlayRefreshDebounce = new PauseTransition(Duration.millis(28));
        this.overlayRefreshDebounce.setOnFinished(event -> this.refreshOverlaysNow());
        this.resizeFitDebounce = new PauseTransition(Duration.millis(120));
        this.resizeFitDebounce.setOnFinished(event -> this.refitViewportAfterLayout());
        this.configureToolbarSizing();
        this.halfCutCanvas = new Canvas(800, 600);
        this.halfCutCanvas.setVisible(false);
        this.halfCutCanvas.setMouseTransparent(true);
        this.halfCutCanvas.widthProperty().bind(this.viewportStack.widthProperty());
        this.halfCutCanvas.heightProperty().bind(this.viewportStack.heightProperty());
        this.halfCutCanvas.widthProperty().addListener(obs -> this.schedule2dCanvasRedraw());
        this.halfCutCanvas.heightProperty().addListener(obs -> this.schedule2dCanvasRedraw());
        this.installHalfCutMouseHandlers();
        this.cycleCanvas = new Canvas(800, 600);
        this.cycleCanvas.setVisible(false);
        this.cycleCanvas.setMouseTransparent(true);
        this.cycleCanvas.widthProperty().bind(this.viewportStack.widthProperty());
        this.cycleCanvas.heightProperty().bind(this.viewportStack.heightProperty());
        this.cycleCanvas.widthProperty().addListener(obs -> this.scheduleCycleCanvasRedraw());
        this.cycleCanvas.heightProperty().addListener(obs -> this.scheduleCycleCanvasRedraw());
        this.cycleFpsLabel = new Label("FPS --");
        this.cycleFpsLabel.setMouseTransparent(true);
        this.cycleFpsLabel.setVisible(false);
        this.cycleFpsLabel.setManaged(false);
        this.cycleFpsLabel.setMinWidth(Region.USE_PREF_SIZE);
        this.cycleFpsLabel.getStyleClass().add("simulation3d-cycle-badge");
        StackPane.setAlignment(this.cycleFpsLabel, Pos.TOP_LEFT);
        StackPane.setMargin(this.cycleFpsLabel, new Insets(12.0, 0.0, 0.0, 12.0));
        this.cycleToolLabel = new Label("T --");
        this.cycleToolLabel.setMouseTransparent(false);
        this.cycleToolLabel.setVisible(false);
        this.cycleToolLabel.setManaged(false);
        this.cycleToolLabel.setMinWidth(Region.USE_PREF_SIZE);
        this.cycleToolLabel.getStyleClass().add("simulation3d-cycle-badge");
        StackPane.setAlignment(this.cycleToolLabel, Pos.TOP_RIGHT);
        StackPane.setMargin(this.cycleToolLabel, new Insets(12.0, 12.0, 0.0, 0.0));
        this.measureReadoutLabel = new Label(I18n.text("sim.001"));
        this.measureReadoutLabel.setMouseTransparent(true);
        this.measureReadoutLabel.setVisible(false);
        this.measureReadoutLabel.setManaged(false);
        this.measureReadoutLabel.getStyleClass().add("simulation3d-measure-readout");
        StackPane.setAlignment(this.measureReadoutLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(this.measureReadoutLabel, new Insets(0.0, 0.0, 46.0, 0.0));
        this.installCycleMouseHandlers();
        this.initCycleControls();
        if (this.viewportStack == null) {
            throw new IllegalStateException(I18n.text("sim.002"));
        }
        this.setupSubScene();
        this.gcodeOverlay.bindSize(this.subScene);
        this.sideViewLabelsOverlay.bindSize(this.subScene);
        this.measureCanvas = new Canvas(800, 600);
        this.measureCanvas.setMouseTransparent(true);
        this.measureCanvas.setVisible(false);
        this.measureCanvas.setManaged(false);
        this.measureCanvas.widthProperty().bind(this.viewportStack.widthProperty());
        this.measureCanvas.heightProperty().bind(this.viewportStack.heightProperty());
        this.measureCanvas.widthProperty().addListener(obs -> this.refreshMeasureOverlay());
        this.measureCanvas.heightProperty().addListener(obs -> this.refreshMeasureOverlay());
        this.viewportStack.getChildren().addAll(
                this.subScene,
                this.sideViewLabelsOverlay.getCanvas(),
                this.halfCutCanvas,
                this.cycleCanvas,
                this.gcodeOverlay.getCanvas(),
                this.measureCanvas,
                this.cycleFpsLabel,
                this.cycleToolLabel,
                this.measureReadoutLabel);
        this.attachCycleControlsToToolbar();
        this.sideViewLabelsOverlay.getCanvas().setVisible(false);
        this.gcodeOverlay.getCanvas().setVisible(false);
        this.gcodeOverlay.getCanvas().setMouseTransparent(true);
        this.restoreViewportLayering();
        // Карусельный станок: деталь лежит на планшайбе, ось вертикальная — класть
        // набок нечего. У токарного ось горизонтальна, поэтому поворот на 90°.
        this.updateMachineOrientation();
        this.spinGroup.getChildren().add(this.modelRoot);
        this.orientationGroup.getChildren().add(this.spinGroup);
        this.pivotMarker.getChildren().add(this.pivotGizmo.getRoot());
        this.orbitCamera.getContentRoot().getChildren().addAll(this.orientationGroup, this.pivotMarker);
        this.worldGroup.getChildren().add(this.orbitCamera.getContentRoot());
        this.syncPivotGizmoVisibility();
        this.installMouseHandlers();
        if (this.rootPane != null) {
            this.rootPane.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        }
        this.viewMode = ViewMode.VIEW_3D;
        this.syncPivotGizmoVisibility();
        if (this.halfCutButton != null) {
            this.halfCutButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(
                    I18n.text("sim.003"))));
        }
        if (this.sideViewButton != null) {
            this.sideViewButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(
                    I18n.text("sim.004"))));
        }
        if (this.view3dButton != null) {
            this.view3dButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.005"))));
        }
        if (this.cycleButton != null) {
            this.cycleButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.006"))));
        }
        this.initMeasureButton();
        this.initLoadingOverlay();
    }

    /** Защита: гарантирует, что оверлей загрузки всегда поверх остальных канвасов. */
    private void ensureLoadingOnTop() {
        if (this.loadingOverlay != null && this.loadingOverlay.isVisible()) {
            this.loadingOverlay.toFront();
        }
    }

    private void initLoadingOverlay() {
        if (this.loadingOverlay == null || this.loadingSpinner == null) {
            return;
        }
        this.loadingSpinAnimation = new RotateTransition(Duration.seconds(1.2), this.loadingSpinner);
        this.loadingSpinAnimation.setByAngle(360.0);
        this.loadingSpinAnimation.setCycleCount(Animation.INDEFINITE);
        this.loadingSpinAnimation.setInterpolator(Interpolator.LINEAR);
        this.loadingFadeAnimation = new FadeTransition(Duration.millis(180), this.loadingOverlay);
        this.loadingOverlay.setOpacity(0.0);
        this.loadingOverlay.setVisible(false);
        this.loadingOverlay.setManaged(false);
        this.loadingOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(this.loadingOverlay, Pos.CENTER);
        this.loadingOverlay.getChildren().forEach(child -> StackPane.setAlignment(child, Pos.CENTER));
        // Растягиваем оверлей на весь viewport, иначе фон не покроет деталь по краям.
        if (this.viewportStack != null) {
            this.loadingOverlay.minWidthProperty().bind(this.viewportStack.widthProperty());
            this.loadingOverlay.minHeightProperty().bind(this.viewportStack.heightProperty());
            this.loadingOverlay.prefWidthProperty().bind(this.viewportStack.widthProperty());
            this.loadingOverlay.prefHeightProperty().bind(this.viewportStack.heightProperty());
        }
        this.loadingOverlay.toFront();
    }

    /** Показать оверлей загрузки (счётчик активных запросов, чтобы не мигало). */
    private void initMeasureButton() {
        if (this.measureButton == null) {
            return;
        }
        this.measureButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.007"))));
        this.measureButton.setMinWidth(40.0);
        this.measureButton.setPrefWidth(40.0);
        this.measureButton.setMinHeight(40.0);
        this.measureButton.setPrefHeight(40.0);
        try {
            Image icon = new Image(getClass().getResource("/linear.png").toExternalForm());
            ImageView imageView = new ImageView(icon);
            imageView.setFitWidth(18.0);
            imageView.setFitHeight(18.0);
            imageView.setPreserveRatio(true);
            this.measureButton.setGraphic(imageView);
            this.measureButton.setText("");
        } catch (RuntimeException ex) {
            this.measureButton.setText("A-B");
        }
    }

    void showLoadingOverlay(String message, String hint) {
        if (this.loadingOverlay == null) {
            return;
        }
        if (message != null && this.loadingLabel != null) {
            this.loadingLabel.setText(message);
        }
        if (hint != null && this.loadingHint != null) {
            this.loadingHint.setText(hint);
        }
        this.activeLoadingRequests++;
        if (this.activeLoadingRequests > 1) {
            return;
        }
        this.loadingOverlay.setManaged(true);
        this.loadingOverlay.setVisible(true);
        this.loadingOverlay.toFront();
        if (this.loadingFadeAnimation != null) {
            this.loadingFadeAnimation.stop();
            this.loadingFadeAnimation.setFromValue(this.loadingOverlay.getOpacity());
            this.loadingFadeAnimation.setToValue(1.0);
            this.loadingFadeAnimation.setOnFinished(null);
            this.loadingFadeAnimation.playFromStart();
        }
        if (this.loadingSpinAnimation != null) {
            this.loadingSpinAnimation.playFromStart();
        }
    }

    void hideLoadingOverlay() {
        if (this.loadingOverlay == null) {
            return;
        }
        this.activeLoadingRequests = Math.max(0, this.activeLoadingRequests - 1);
        if (this.activeLoadingRequests > 0) {
            return;
        }
        if (this.loadingFadeAnimation != null) {
            this.loadingFadeAnimation.stop();
            this.loadingFadeAnimation.setFromValue(this.loadingOverlay.getOpacity());
            this.loadingFadeAnimation.setToValue(0.0);
            this.loadingFadeAnimation.setOnFinished(event -> {
                if (this.activeLoadingRequests == 0) {
                    this.loadingOverlay.setVisible(false);
                    this.loadingOverlay.setManaged(false);
                    if (this.loadingSpinAnimation != null) {
                        this.loadingSpinAnimation.stop();
                    }
                }
            });
            this.loadingFadeAnimation.playFromStart();
        } else {
            this.loadingOverlay.setVisible(false);
            this.loadingOverlay.setManaged(false);
            if (this.loadingSpinAnimation != null) {
                this.loadingSpinAnimation.stop();
            }
        }
    }

    private void hideLoadingOverlayAfterRenderPulse() {
        javafx.application.Platform.runLater(() ->
                javafx.application.Platform.runLater(this::hideLoadingOverlay));
    }

    /** Обновить контекст без полной пересборки сцены (для цикла). */
    private boolean isVerticalMachine() {
        return this.simulationContext != null && this.simulationContext.getMachineConfiguration() != null
                ? this.simulationContext.getMachineConfiguration().isVerticalLathe()
                : com.sergey.pisarev.util.MachineOrientation.isVerticalLathe();
    }

    private void updateMachineOrientation() {
        this.orientationGroup.getTransforms().setAll(
                com.sergey.pisarev.util.MachineOrientation.modelRotation(this.isVerticalMachine()));
    }

    public void syncContext(SimulationContext context, boolean dark) {
        this.simulationContext = context != null ? context : this.simulationContext;
        this.completeSimulationContext = this.simulationContext;
        this.refreshMissingToolNotice();
        this.updateMachineOrientation();
        this.darkTheme = dark;
        this.refreshCycleFpsLabel();
    }

    public void setData(SimulationContext context, boolean dark, IntConsumer onSourceLine) {
        this.setData(context, dark, onSourceLine, null);
    }

    public void setData(SimulationContext context, boolean dark, IntConsumer onSourceLine, Runnable onReady) {
        boolean restartCycle = this.isCycleMode();
        this.stopPlayback();
        this.completeSimulationContext = context != null ? context : this.completeSimulationContext;
        if (restartCycle) this.viewMode = ViewMode.VIEW_3D;
        this.loadSetupData(this.completeSimulationContext, dark, onSourceLine, () -> {
            if (restartCycle) this.onViewCycle();
            if (onReady != null) onReady.run();
        });
    }

    private void loadSetupData(SimulationContext context, boolean dark, IntConsumer onSourceLine, Runnable onReady) {
        // Если пришли НОВЫЕ данные (другой G-code/контекст) — сбрасываем флаг фита,
        // чтобы выполнить центрирование. При «Обновить» (тот же контекст) — не трогаем.
        boolean preserveView = context == null || context == this.simulationContext;
        if (context != null && context != this.simulationContext) {
            this.view3dFitted = false;
            this.lastFittedContext = null;
            this.lastFitHadFinishedPart = false;
            this.clearMeasurePoints();
        }
        this.simulationContext = context != null ? context : this.simulationContext;
        this.refreshMissingToolNotice();
        this.updateMachineOrientation();
        this.clearCyclePathCache();
        this.refreshCycleFpsLabel();
        this.darkTheme = dark;
        this.sourceLineConsumer = onSourceLine;
        this.cachedCycleProfileIndex = Integer.MIN_VALUE;
        this.cachedCycleProfile = List.of();
        this.cycleProfileCache.clear();
        this.renderedCycleMeshIndex = Integer.MIN_VALUE;
        this.renderedCycleMeshFrame = Integer.MIN_VALUE;
        this.lastCycleMeshRequestNanos = 0L;
        this.cycleMeshGeneration++;
        this.cycleMeshBuildRunning = false;
        this.pendingCycleMeshIndex = Integer.MIN_VALUE;
        this.pendingCycleMeshFrame = Integer.MIN_VALUE;
        this.pendingCycleMeshMoves = List.of();
        this.cycleResetLoadingPending = false;
        this.cycleDistanceProgress = 0.0;
        this.cycleDistanceMoves = List.of();
        this.cycleDistanceStops = new double[0];
        this.cycleTotalDistance = 0.0;
        this.viewportFitAfterLayoutPending = true;

        final int generation = ++this.profileLoadGeneration;
        if (this.statusLabel != null) {
            this.statusLabel.setText(I18n.text("sim.008"));
        }
        boolean hasMoves = simulationContext != null && !moves().isEmpty();
        if (hasMoves) {
            this.showLoadingOverlay(I18n.text("sim.009"), I18n.text("sim.010"));
        }
        javafx.concurrent.Task<List<double[]>> profileTask = new javafx.concurrent.Task<>() {
            @Override
            protected List<double[]> call() {
                if (simulationContext == null || moves().isEmpty()) {
                    return List.of();
                }
                int index = machiningMoveIndex;
                return index < 0
                        ? LatheStockRemovalSimulator.buildRevolveProfile(simulationContext)
                        : LatheStockRemovalSimulator.buildRevolveProfile(simulationContext, index);
            }
        };
        profileTask.setOnSucceeded(event -> javafx.application.Platform.runLater(() -> {
            if (generation != this.profileLoadGeneration) {
                if (hasMoves) {
                    this.hideLoadingOverlay();
                }
                return;
            }
            this.finishRebuildScene(profileTask.getValue(), preserveView, onReady);
            this.updateStatus();
            if (hasMoves) {
                // Если mesh-задача не была запущена — баланс счётчика выполнит hideLoadingOverlay().
                // Если запущена — она увеличила счётчик в scheduleAsyncMeshBuild, поэтому общий счёт остаётся положительным.
                this.hideLoadingOverlay();
            }
        }));
        profileTask.setOnFailed(event -> javafx.application.Platform.runLater(() -> {
            if (generation != this.profileLoadGeneration) {
                if (hasMoves) {
                    this.hideLoadingOverlay();
                }
                return;
            }
            Throwable error = profileTask.getException();
            this.showMeshError(I18n.text("sim.011") + (error != null ? error.getMessage() : I18n.text("sim.012")));
            this.updateStatus();
            if (onReady != null) {
                onReady.run();
            }
            if (hasMoves) {
                this.hideLoadingOverlay();
            }
        }));
        Thread worker = new Thread(profileTask, "chekator-3d-profile");
        worker.setDaemon(true);
        worker.start();
        this.scheduleViewportFitAfterLayout();
        if (preserveView && this.viewMode == ViewMode.VIEW_3D) {
            this.updatePivotGizmo();
        }
        this.syncToolpathOverlayVisibility();
        if (this.is2dCanvasMode()) {
            this.schedule2dCanvasRedraw();
        } else if (this.isCycleMode()) {
            this.scheduleCycleCanvasRedraw();
        } else if (this.isSideGraphMode()) {
            this.syncSideGrid(true);
            this.rebuildOverlayMovesCache();
            this.scheduleOverlayRefresh();
        }
    }

    /** Полная пересборка 3D-модели из текущего контекста (после правок G-code). */
    public void rebuildModelFromContext() {
        this.cachedFinishedProfile = List.of();
        this.halfCutLayout = null;
        this.rebuildScene();
        this.updateStatus();
    }

    /** Пошаговая симуляция снятия материала в 3D (-1 = вся программа). */
    public void setInitialMachiningProgress(int contourMoveIndexInclusive) {
        this.machiningMoveIndex = contourMoveIndexInclusive;
    }

    public void setMachiningProgress(int contourMoveIndexInclusive) {
        if (this.machiningMoveIndex == contourMoveIndexInclusive
                && this.cachedFinishedProfile != null
                && this.cachedFinishedProfile.size() >= 2) {
            return;
        }
        this.machiningMoveIndex = contourMoveIndexInclusive;
        this.rebuildScene();
        this.refreshPartBoundsFromProfile();
        this.updatePivotGizmo();
        this.schedule2dCanvasRedraw();
        this.rebuildOverlayMovesCache();
        this.scheduleOverlayRefresh();
        this.updateStatus();
    }

    @FXML
    private void onRefreshModel() {
        // Сохраняем текущее положение камеры/зума: «Обновить» только перестраивает модель из G-code.
        this.rebuildModelFromContext();
    }

    /** Измерения привязаны к вкладке: при переходе на другую вкладку точки сбрасываются. */
    private void resetMeasureOnViewChange(ViewMode next) {
        if (this.viewMode != next && !this.measurePoints.isEmpty()) {
            this.clearMeasurePoints();
        }
    }

    @FXML
    private void onView3d() {
        if (this.restoreCompleteResult(ViewMode.VIEW_3D)) return;
        this.resetMeasureOnViewChange(ViewMode.VIEW_3D);
        this.viewMode = ViewMode.VIEW_3D;
        this.orbitCamera.setOrbitLocked(false);
        this.syncSideGrid(false);
        this.syncPivotGizmoVisibility();
        this.subScene.setVisible(true);
        this.subScene.setMouseTransparent(false);
        this.applyViewportBackground();
        this.halfCutCanvas.setVisible(false);
        this.halfCutCanvas.setMouseTransparent(true);
        this.setCycleVisible(false);
        if (this.lastMeshError != null && !this.hasFinishedPartNode()) {
            this.showMeshError(this.lastMeshError);
        } else {
            this.hideMeshError();
        }
        if (!this.view3dFitted) {
            this.fitToContent();
            this.view3dFitted = true;
        }
        this.viewportFitAfterLayoutPending = true;
        this.scheduleViewportFitAfterLayout();
        this.forceViewportFitAfterLayout();
        this.syncToolpathOverlayVisibility();
        this.updateViewModeButtons();
        this.restoreViewportLayering();
        this.updateStatus();
    }

    @FXML
    private void onViewCycle() {
        this.resetMeasureOnViewChange(ViewMode.CYCLE);
        this.viewMode = ViewMode.CYCLE;
        this.cyclePlaying = false;
        this.lastCycleNanos = 0L;
        this.orbitCamera.setOrbitLocked(false);
        this.syncSideGrid(false);
        this.syncPivotGizmoVisibility();
        this.halfCutZoom = 1.0;
        this.halfCutPanX = 0.0;
        this.halfCutPanY = 0.0;
        this.cycleLayout = null;
        this.cachedCycleProfileIndex = Integer.MIN_VALUE;
        this.cachedCycleProfile = List.of();
        this.cycleProfileCache.clear();
        this.renderedCycleMeshIndex = Integer.MIN_VALUE;
        this.renderedCycleMeshFrame = Integer.MIN_VALUE;
        this.lastCycleMeshRequestNanos = 0L;
        this.cycleDistanceProgress = 0.0;
        this.cycleDistanceMoves = List.of();
        this.cycleDistanceStops = new double[0];
        this.cycleTotalDistance = 0.0;
        this.cycleMeshGeneration++;
        this.cycleMeshBuildRunning = false;
        this.pendingCycleMeshIndex = Integer.MIN_VALUE;
        this.pendingCycleMeshFrame = Integer.MIN_VALUE;
        this.pendingCycleMeshMoves = List.of();
        this.cycleResetLoadingPending = false;
        this.clearCyclePathCache();
        this.applyCycleVisibility();

        this.selectCycleSetup(this.hasSetupSequence()
                ? this.completeSimulationContext.getPrecedingSetup() : this.completeSimulationContext);
        this.restartCyclePreviewFromBeginning();

        this.fitToContent();
        this.view3dFitted = true;
        this.forceViewportFitAfterLayout();
        this.updateCycleControls();
        this.scheduleCycleCanvasRedraw();
        this.updateStatus();
    }

    @FXML
    private void onToggleToolpath() {
        if (this.viewMode == ViewMode.HALF_CUT) {
            this.onView3d();
        }
        this.showToolpath = !this.showToolpath;
        this.scheduleCycleCanvasRedraw();
        this.syncToolpathOverlayVisibility();
        this.updateViewModeButtons();
        this.updateStatus();
    }

    @FXML
    private void onToggleMeasureTool() {
        if (this.viewMode == ViewMode.HALF_CUT) {
            this.onView3d();
        }
        this.measureMode = !this.measureMode;
        if (this.measureMode) {
            this.clearMeasurePoints();
        }
        this.updateMeasureVisibility();
        this.updateViewModeButtons();
        this.restoreViewportLayering();
    }

    @FXML
    private void onViewHalfCut() {
        if (this.restoreCompleteResult(ViewMode.HALF_CUT)) return;
        this.resetMeasureOnViewChange(ViewMode.HALF_CUT);
        this.viewMode = ViewMode.HALF_CUT;
        this.orbitCamera.setOrbitLocked(false);
        this.syncSideGrid(false);
        this.syncPivotGizmoVisibility();
        this.halfCutZoom = 1.0;
        this.halfCutPanX = 0.0;
        this.halfCutPanY = 0.0;
        this.apply2dCanvasVisibility();
        this.ensureProfileCached();
        this.fitToContent();
        this.schedule2dCanvasRedraw();
        this.updateStatus();
    }

    @FXML
    private void onViewSideGraph() {
        if (this.restoreCompleteResult(ViewMode.SIDE_GRAPH)) return;
        this.resetMeasureOnViewChange(ViewMode.SIDE_GRAPH);
        this.viewMode = ViewMode.SIDE_GRAPH;
        this.view3dFitted = false;
        this.applySideGraphVisibility();
        this.ensureProfileCached();
        this.fitToContent();
        this.view3dFitted = true;
        this.forceViewportFitAfterLayout();
        this.updateStatus();
    }

    private void applySideGraphVisibility() {
        this.syncPivotGizmoVisibility();
        this.halfCutCanvas.setVisible(false);
        this.halfCutCanvas.setMouseTransparent(true);
        this.setCycleVisible(false);
        this.subScene.setVisible(true);
        this.subScene.setMouseTransparent(false);
        this.applyViewportBackground();
        this.hideMeshError();
        this.orbitCamera.setOrbitLocked(true);
        this.orbitCamera.applyLockedSideView();
        this.syncSideGrid(true);
        this.syncToolpathOverlayVisibility();
        this.updateViewModeButtons();
        this.restoreViewportLayering();
    }

    private void apply2dCanvasVisibility() {
        this.orbitCamera.setOrbitLocked(false);
        this.syncSideGrid(false);
        this.setCycleVisible(false);
        this.subScene.setVisible(false);
        this.subScene.setMouseTransparent(true);
        this.gcodeOverlay.getCanvas().setVisible(false);
        this.gcodeOverlay.clear();
        this.halfCutCanvas.setVisible(true);
        this.halfCutCanvas.setMouseTransparent(false);
        this.halfCutCanvas.toFront();
        if (this.errorLabel != null) {
            this.errorLabel.toBack();
        }
        this.ensureLoadingOnTop();
        this.hideMeshError();
        this.schedule2dCanvasRedraw();
        this.updateViewModeButtons();
        this.restoreViewportLayering();
    }

    private void applyCycleVisibility() {
        this.orbitCamera.setOrbitLocked(false);
        this.subScene.setVisible(true);
        this.subScene.setMouseTransparent(false);
        this.applyViewportBackground();
        this.halfCutCanvas.setVisible(false);
        this.halfCutCanvas.setMouseTransparent(true);
        if (this.cycleCanvas != null) {
            this.cycleCanvas.setVisible(false);
            this.cycleCanvas.setMouseTransparent(true);
        }
        this.syncSideGrid(true);
        this.syncToolpathOverlayVisibility();
        this.setCycleVisible(true);
        this.hideMeshError();
        this.updateViewModeButtons();
        this.restoreViewportLayering();
    }

    private void setCycleVisible(boolean visible) {
        if (this.cycleCanvas != null) {
            this.cycleCanvas.setVisible(false);
            this.cycleCanvas.setMouseTransparent(true);
        }
        if (this.cycleControls != null) {
            this.cycleControls.setVisible(visible);
            this.cycleControls.setManaged(visible);
        }
        if (this.cycleFpsLabel != null) {
            this.cycleFpsLabel.setVisible(visible);
            this.cycleFpsLabel.setManaged(visible);
            this.cycleFpsLabel.setText(visible ? this.formatCycleFpsLabel(0.0) : "");
        }
        if (this.cycleToolLabel != null) {
            this.cycleToolLabel.setVisible(visible);
            this.cycleToolLabel.setManaged(visible);
            this.cycleToolLabel.setText(visible ? "T --" : "");
        }
        if (!visible) {
            this.cancelTurnover();
            this.cyclePlaying = false;
            if (this.cycleAnimation != null) {
                this.cycleAnimation.stop();
            }
            this.stopCycleSpindle(true);
            this.cycleMeshGeneration++;
            this.cycleMeshBuildRunning = false;
            this.renderedCycleMeshFrame = Integer.MIN_VALUE;
            this.pendingCycleMeshIndex = Integer.MIN_VALUE;
            this.pendingCycleMeshFrame = Integer.MIN_VALUE;
            this.pendingCycleMeshMoves = List.of();
            this.cycleResetLoadingPending = false;
            this.clearCyclePathCache();
            this.modelRoot.getChildren().removeIf(node -> {
                Object tag = node.getUserData();
                return CYCLE_TOOL_TAG.equals(tag) || CYCLE_CUT_PREVIEW_TAG.equals(tag);
            });
            this.cycleTool3d = null;
            this.clearCycleCutPreviewReferences();
            this.cycleToolSignature = "";
            this.hideLoadingOverlay();
            this.updateCycleControls();
        }
    }

    private boolean is2dCanvasMode() {
        return this.viewMode == ViewMode.HALF_CUT;
    }

    private boolean isCycleMode() {
        return this.viewMode == ViewMode.CYCLE;
    }

    private boolean isSideGraphMode() {
        return this.viewMode == ViewMode.SIDE_GRAPH;
    }

    private void applyViewportBackground() {
        if (this.subScene == null) {
            return;
        }
        this.subScene.setFill(this.darkTheme ? Color.web("#101827") : Color.web("#eef2f7"));
    }

    private void syncSideGrid(boolean show) {
        this.orientationGroup.getChildren().removeIf(node -> SIDE_GRID_TAG.equals(node.getUserData()));
        this.sideViewLabelsOverlay.getCanvas().setVisible(show);
        if (!show) {
            this.sideViewLabelsOverlay.clear();
        }
    }

    @FXML
    private void onZoomIn() {
        this.orbitCamera.zoomIn();
        this.scheduleOverlayRefresh();
    }

    @FXML
    private void onZoomOut() {
        this.orbitCamera.zoomOut();
        this.scheduleOverlayRefresh();
    }

    @FXML
    private void onFitView() {
        this.fitToContent();
        this.view3dFitted = true;
        this.lastFittedContext = this.simulationContext;
        this.lastFitHadFinishedPart = this.hasFinishedPartNode();
        if (!this.is2dCanvasMode()) {
            this.forceViewportFitAfterLayout();
        }
    }

    @FXML
    private void onResetView() {
        this.view3dFitted = false;
        if (this.isSideGraphMode()) {
            this.orbitCamera.applyLockedSideView();
        } else {
            this.orbitCamera.resetAngles();
        }
        this.resetOrthogonalViewPending = this.viewMode == ViewMode.VIEW_3D;
        this.spinAnchorX = 0.0;
        this.spinAnchorY = this.partCenterZMm;
        this.spinAnchorZ = 0.0;
        this.resetSpinOrientation();
        this.halfCutPanX = 0.0;
        this.halfCutPanY = 0.0;
        this.halfCutZoom = 1.0;
        this.halfCutLayout = null;
        this.fitToContent();
        this.view3dFitted = true;
        this.lastFittedContext = this.simulationContext;
        this.lastFitHadFinishedPart = this.hasFinishedPartNode();
        this.schedule2dCanvasRedraw();
        if (!this.is2dCanvasMode()) {
            this.forceViewportFitAfterLayout();
            this.refreshOverlaysNow();
            this.restoreViewportLayering();
        }
    }

    private void setupSubScene() {
        this.subScene = new SubScene(this.worldGroup, 800, 600, true, javafx.scene.SceneAntialiasing.BALANCED);
        this.subScene.setCamera(this.orbitCamera.getCamera());
        this.subScene.setFill(Color.web("#eef2f7"));
        this.subScene.setPickOnBounds(true);
        this.subScene.widthProperty().bind(this.viewportStack.widthProperty());
        this.subScene.heightProperty().bind(this.viewportStack.heightProperty());
        Runnable onViewportResize = () -> {
            // Пере-вписывать при ресайзе нужно только «Вид сбоку» (заблокированная
            // проекция, иначе обрезается). В обычном 3D и Цикле камеру НЕ трогаем:
            // JavaFX сам подстраивает проекцию под аспект, а принудительный fit на
            // переходном размере иногда «терял» объект (он пропадал).
            if (this.isSideGraphMode()
                    && this.subScene != null
                    && this.subScene.getWidth() > 80.0
                    && this.subScene.getHeight() > 80.0) {
                this.viewportFitAfterLayoutPending = true;
            }
            if (this.resizeFitDebounce != null) {
                this.resizeFitDebounce.stop();
                this.resizeFitDebounce.playFromStart();
            } else {
                this.scheduleOverlayRefresh();
            }
        };
        this.subScene.widthProperty().addListener((obs, o, n) -> onViewportResize.run());
        this.subScene.heightProperty().addListener((obs, o, n) -> onViewportResize.run());
        // Свет намеренно скромный. Раньше рассеянного было 0.62, а три источника
        // светили почти в полную силу: в сумме больше единицы, поэтому металл
        // выбеливался, а при вращении по нему ползло световое пятно. JavaFX к тому же
        // не гасит PointLight с расстоянием, так что все три источника складывались
        // в любой точке модели. Прозрачность в цвете источника ничего не меняла —
        // шейдер её не учитывает, — поэтому яркость убрана из самих составляющих RGB.
        // Теперь один основной источник, одна мягкая подсветка и слабый контур сзади:
        // форма и уступы читаются, но засвета нет.
        AmbientLight ambient = new AmbientLight(Color.color(0.46, 0.47, 0.50));
        PointLight key = new PointLight(Color.color(0.48, 0.49, 0.53));
        key.setTranslateX(280.0);
        key.setTranslateY(-220.0);
        key.setTranslateZ(-320.0);
        // Две подсветки стоят сбоку и позади детали. Свет идёт по касательной, поэтому
        // ярче всего он ложится там, где поверхность уходит от глаза, — на краях
        // силуэта, которые оставались самыми тёмными. Замер по отрисованной картинке
        // (RenderLighting): край 0.51 против середины 0.48, то есть край теперь самое
        // светлое место; до этого было 0.36 против 0.41, а на старом свете и край и
        // середина упирались в 0.71-0.79 — то есть выбеливались.
        //
        // Источников ровно три. Это не стиль, а предел: измерено (ProbeLights), что
        // четвёртый и следующие PointLight шейдер JavaFX молча игнорирует — яркость
        // перестаёт расти после третьего. Поэтому лишний свет добавлять некуда, и
        // общий уровень поднимается рассеянным светом, а края — этими двумя.
        PointLight rimUpper = new PointLight(Color.color(0.34, 0.35, 0.38));
        rimUpper.setTranslateX(-820.0);
        rimUpper.setTranslateY(-520.0);
        rimUpper.setTranslateZ(120.0);
        PointLight rimLower = new PointLight(Color.color(0.30, 0.31, 0.34));
        rimLower.setTranslateX(760.0);
        rimLower.setTranslateY(560.0);
        rimLower.setTranslateZ(160.0);
        this.worldGroup.getChildren().addAll(ambient, key, rimUpper, rimLower);
    }

    private void rebuildScene() {
        this.setData(this.completeSimulationContext, this.darkTheme, this.sourceLineConsumer);
    }

    private void finishRebuildScene(List<double[]> precomputedProfile, boolean preserveView, Runnable onReady) {
        ++this.meshBuildGeneration;
        this.clearCyclePathCache();
        this.modelRoot.getChildren().removeIf(node -> {
            Object tag = node.getUserData();
            return STOCK_BLANK_TAG.equals(tag)
                    || FINISHED_PART_TAG.equals(tag)
                    || TRAJECTORY_3D_TAG.equals(tag)
                    || TRAJECTORY_POINT_3D_TAG.equals(tag);
        });
        this.displayMoves = List.of();
        if (this.moves().isEmpty()) {
            this.cachedFinishedProfile = List.of();
            this.schedule2dCanvasRedraw();
            this.refreshGcodeOverlay();
            this.syncSideViewLabels();
            this.lastMeshError = I18n.text("sim.013");
            this.showMeshError(this.lastMeshError);
            this.updateStatus();
            this.notifyModelReady(onReady);
            return;
        }
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        List<double[]> profile = precomputedProfile != null && precomputedProfile.size() >= 2
                ? List.copyOf(precomputedProfile)
                : (this.machiningMoveIndex < 0
                        ? LatheStockRemovalSimulator.buildRevolveProfile(this.simulationContext)
                        : LatheStockRemovalSimulator.buildRevolveProfile(
                                this.simulationContext,
                                this.machiningMoveIndex));
        boolean progressive = this.machiningMoveIndex >= 0;
        boolean machined = StockRemovalMeshBuilder.profileHasMaterialRemoval(profile, stockRadius);
        if (!machined && !progressive) {
            this.lastMeshError =
                    I18n.text("sim.014");
            this.cachedFinishedProfile = List.of();
            this.showMeshError(this.lastMeshError);
            this.updateStatus();
            this.notifyModelReady(onReady);
            return;
        }
        this.lastMeshError = null;
        this.hideMeshError();
        this.cachedFinishedProfile = profile.size() >= 2
                ? profile
                : StockRemovalMeshBuilder.buildFinishedProfile(this.simulationContext);
        this.refreshPartBoundsFromProfile();
        this.displayMoves = this.buildDisplayMoves();
        if (false && !this.hasStockOrFinishedNode()) {
            this.lastMeshError = I18n.text("sim.015");
            this.showMeshError(this.lastMeshError);
            this.updateStatus();
            this.notifyModelReady(onReady);
            return;
        }
        if (this.pendingDefaultSideView && !this.moves().isEmpty()) {
            this.pendingDefaultSideView = false;
            if (!this.isVerticalMachine()) {
                this.resetMeasureOnViewChange(ViewMode.SIDE_GRAPH);
                this.viewMode = ViewMode.SIDE_GRAPH;
                this.applySideGraphVisibility();
            }
            preserveView = false;
        }
        // Не сбрасываем view3dFitted при successful machined: позволяет «Обновить» сохранять положение камеры.
        // Первичное вписывание делается в refitViewportAfterLayout/onView3d при первом показе.
        if ((this.viewMode == ViewMode.VIEW_3D || this.isSideGraphMode()) && !this.view3dFitted) {
            this.fitToContent();
            this.view3dFitted = true;
        }
        this.schedule2dCanvasRedraw();
        this.rebuildOverlayMovesCache();
        this.buildToolpath();
        this.scheduleOverlayRefresh();
        if (machined || progressive) {
            if (this.statusLabel != null) {
                this.statusLabel.setText(I18n.text("sim.016"));
            }
            this.scheduleAsyncMeshBuild(this.cachedFinishedProfile, progressive, onReady);
        } else {
            this.notifyModelReady(onReady);
        }
        this.updateStatus();
    }

    private void notifyModelReady(Runnable onReady) {
        if (onReady != null) {
            onReady.run();
        }
    }

    private void addStockBlankMesh() {
        try {
            MeshView stock = StockRemovalMeshBuilder.buildStockRevolvedMeshView(
                    this.simulationContext, this.stockDiffuseColor(), false);
            if (this.isRenderableNode(stock)) {
                stock.setUserData(STOCK_BLANK_TAG);
                this.modelRoot.getChildren().add(0, stock);
                this.restoreViewportLayering();
            }
        } catch (RuntimeException exception) {
            this.lastMeshError = exception.getMessage();
            this.showMeshError(I18n.text("sim.017") + exception.getMessage());
        }
    }

    private void scheduleAsyncMeshBuild(List<double[]> profile, boolean progressive, Runnable onReady) {
        // Прежний результат и так отбрасывался по номеру поколения, но сама задача
        // продолжала считать и занимала процессор. Теперь предыдущую сборку отменяем.
        if (this.runningMeshTask != null && this.runningMeshTask.isRunning()) {
            this.runningMeshTask.cancel(true);
        }
        final int generation = ++this.meshBuildGeneration;
        final double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        // Удерживаем оверлей загрузки на время сборки меша — баланс счётчика сделает hide в onSucceeded/onFailed.
        this.showLoadingOverlay(I18n.text("sim.009"), I18n.text("sim.018"));
        final List<double[]> meshProfile = profile.size() >= 2
                ? List.copyOf(profile)
                : List.of();
        final SimulationContext meshContext = this.simulationContext;
        javafx.concurrent.Task<MeshBuildNodes> meshTask = new javafx.concurrent.Task<>() {
            @Override
            protected MeshBuildNodes call() {
                TriangleMesh stock = StockRemovalMeshBuilder.buildStockRevolvedTriangleMesh(meshContext, false);
                TriangleMesh finished = null;
                // Между этапами проверяем отмену: деталь обычно тяжелее заготовки,
                // и на устаревшем запросе её считать уже незачем.
                if (isCancelled()) {
                    return null;
                }
                if (meshProfile.size() >= 2) {
                    finished = StockRemovalMeshBuilder.buildFinishedPartTriangleMesh(
                            meshContext,
                            meshProfile,
                            stockRadius,
                            false);
                }
                return new MeshBuildNodes(stock, finished);
            }
        };
        meshTask.setOnSucceeded(event -> javafx.application.Platform.runLater(() -> {
            try {
                if (generation != this.meshBuildGeneration) {
                    return;
                }
                MeshBuildNodes nodes = meshTask.getValue();
                javafx.scene.Node stock = nodes != null
                        ? StockRemovalMeshBuilder.createStockMeshView(
                                nodes.stockMesh(),
                                this.stockDiffuseColor())
                        : null;
                javafx.scene.Node finished = nodes != null
                        ? StockRemovalMeshBuilder.createFinishedMeshView(
                                nodes.finishedMesh(),
                                this.partDiffuseColor())
                        : null;
                // Сечение карусельной детали строит OCCT: тело замкнуто, грани наружу.
                // Изнанка тонкой стенки иначе проступает рябью (см. cullCycleBackFaces).
                if (finished instanceof MeshView view
                        && com.sergey.pisarev.service.AxialStockSection.isSection(meshProfile))
                    view.setCullFace(CullFace.BACK);
                this.modelRoot.getChildren().removeIf(node -> {
                    Object tag = node.getUserData();
                    return STOCK_BLANK_TAG.equals(tag) || FINISHED_PART_TAG.equals(tag);
                });
                if (this.isRenderableNode(finished)) {
                    finished.setUserData(FINISHED_PART_TAG);
                    this.applyWorkpieceSpin(finished);
                    this.modelRoot.getChildren().add(finished);
                    this.lastMeshError = null;
                    this.hideMeshError();
                } else if (this.isRenderableNode(stock)) {
                    stock.setUserData(STOCK_BLANK_TAG);
                    this.applyWorkpieceSpin(stock);
                    this.modelRoot.getChildren().add(stock);
                    this.lastMeshError = meshProfile.size() >= 2
                            ? I18n.text("sim.019")
                            : I18n.text("sim.020");
                    this.showMeshError(this.lastMeshError);
                } else {
                    this.lastMeshError =
                            I18n.text("sim.021")
                                    + I18n.text("sim.022");
                    this.showMeshError(this.lastMeshError);
                }
                this.updatePivotGizmo();
                this.restoreViewportLayering();
                if (!this.is2dCanvasMode()) {
                    this.fitToContent();
                    this.view3dFitted = true;
                    this.lastFittedContext = this.simulationContext;
                    this.lastFitHadFinishedPart = this.hasFinishedPartNode();
                    this.viewportFitAfterLayoutPending = true;
                    this.scheduleViewportFitAfterLayout();
                    this.forceViewportFitAfterLayout();
                }
                this.rebuildOverlayMovesCache();
                this.scheduleOverlayRefresh();
                this.scheduleCycleCanvasRedraw();
                this.updateStatus();
                this.notifyModelReady(onReady);
            } finally {
                this.hideLoadingOverlayAfterRenderPulse();
            }
        }));
        meshTask.setOnFailed(event -> javafx.application.Platform.runLater(() -> {
            try {
                if (generation != this.meshBuildGeneration) {
                    return;
                }
                Throwable error = meshTask.getException();
                this.showMeshError(I18n.text("sim.023") + (error != null ? error.getMessage() : I18n.text("sim.012")));
                this.restoreViewportLayering();
                this.updateStatus();
                this.notifyModelReady(onReady);
            } finally {
                this.hideLoadingOverlay();
            }
        }));
        this.runningMeshTask = meshTask;
        Thread worker = new Thread(meshTask, "chekator-3d-mesh");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private Color partDiffuseColor() {
        return this.darkTheme ? Color.web("#7ec8e3") : Color.web("#4f9fb5");
    }

    private Color stockDiffuseColor() {
        return this.darkTheme ? Color.web("#9aa8b8", 0.30) : Color.web("#7b8794", 0.26);
    }

    private void applyPartThemeMaterials() {
        if (this.modelRoot == null) {
            return;
        }
        this.applyMaterialRecursive(this.modelRoot, FINISHED_PART_TAG, this.partDiffuseColor());
        this.applyMaterialRecursive(this.modelRoot, STOCK_BLANK_TAG, this.stockDiffuseColor());
    }

    private void applyMaterialRecursive(javafx.scene.Node node, String tag, Color color) {
        if (node == null) {
            return;
        }
        if (tag.equals(node.getUserData()) && node instanceof Group group
                && Boolean.TRUE.equals(group.getProperties().get("cycle-batches")) && !group.getChildren().isEmpty()) {
            var first=(MeshView)group.getChildren().get(0);
            var material=StockRemovalMeshBuilder.createFinishedMeshView((TriangleMesh)first.getMesh(),color).getMaterial();
            for(var child:group.getChildren())((MeshView)child).setMaterial(material);
            return;
        }
        if (tag.equals(node.getUserData()) && node instanceof MeshView meshView && meshView.getMesh() instanceof TriangleMesh mesh) {
            MeshView materialSource = FINISHED_PART_TAG.equals(tag)
                    ? StockRemovalMeshBuilder.createFinishedMeshView(mesh, color)
                    : StockRemovalMeshBuilder.createStockMeshView(mesh, color);
            if (materialSource != null) {
                meshView.setMaterial(materialSource.getMaterial());
            }
        }
        if (node instanceof Group group) {
            for (javafx.scene.Node child : group.getChildren()) {
                this.applyMaterialRecursive(child, tag, color);
            }
        }
    }

    private void buildToolpath() {
        this.modelRoot.getChildren().removeIf(node -> {
            Object tag = node.getUserData();
            return TRAJECTORY_3D_TAG.equals(tag) || TRAJECTORY_POINT_3D_TAG.equals(tag);
        });
        if (!this.showToolpath || this.turnoverPreparing || this.turnoverProgress >= 0
                || this.simulationContext == null
                || (this.viewMode != ViewMode.VIEW_3D && this.viewMode != ViewMode.CYCLE)) {
            return;
        }
        List<GCodeMoveData> pathMoves = this.graphToolpathMoves();
        if (pathMoves.isEmpty()) {
            pathMoves = this.buildDisplayMoves();
        }
        if (pathMoves.isEmpty()) {
            return;
        }
        // Траектория центра резца: эквидистанта G41/G42 с радиусом пластины/чашки и OFFN.
        pathMoves = this.compensatedDisplayMoves(pathMoves);

        Group trajectory = new Group();
        trajectory.setUserData(TRAJECTORY_3D_TAG);
        trajectory.setMouseTransparent(false);
        trajectory.setPickOnBounds(false);
        trajectory.setDepthTest(DepthTest.DISABLE);
        trajectory.setViewOrder(-50.0);
        // Раскраска общая с 2D-графиком и со стойкой: быстрый ход красный, подача
        // зелёная, дуга того же цвета, что прямая. Точки — контрастные теме, иначе
        // на тёмном фоне тёмные шарики не видно вовсе.
        PhongMaterial feedMaterial = new PhongMaterial(Color.web(MainController.SINUMERIK_FEED_COLOR));
        PhongMaterial rapidMaterial = new PhongMaterial(Color.web(MainController.SINUMERIK_RAPID_COLOR));
        PhongMaterial arcMaterial = feedMaterial;
        this.updateTrajectoryPointMaterials();
        int boundary = com.sergey.pisarev.service.MultiChannelSimulation.channelBoundaryLine(this.simulationContext.getProgramText());
        int firstSupport = com.sergey.pisarev.service.MultiChannelSimulation.supportNumber(this.simulationContext.getProgramText(), 0);
        for (int i = 0; i < pathMoves.size(); i++) {
            GCodeMoveData move = pathMoves.get(i);
            double side = this.isVerticalMachine() && firstSupport == 2 && move.sourceLine() <= boundary ? -1 : 1;
            double startRadius = this.isVerticalMachine() ? side * move.startX() * .5 : Math.max(0.0, LatheMeshBuilder.toRadius(move.startX()));
            double endRadius = this.isVerticalMachine() ? side * move.endX() * .5 : Math.max(0.0, LatheMeshBuilder.toRadius(move.endX()));
            PhongMaterial material = move.rapid()
                    ? rapidMaterial
                    : (move.arcSegment() ? arcMaterial : feedMaterial);
            javafx.scene.Node segment = this.createTrajectorySegment3d(
                    startRadius,
                    move.startZ(),
                    endRadius,
                    move.endZ(),
                    material,
                    move.rapid() ? 0.46 : 0.82);
            if (segment != null) {
                segment.setOpacity(move.rapid() ? 0.9 : 1.0);
                segment.getProperties().put(GCODE_MOVE_PROPERTY, move);
                segment.setMouseTransparent(false);
                segment.setPickOnBounds(true);
                segment.setDepthTest(DepthTest.DISABLE);
                trajectory.getChildren().add(segment);
            }
            if (this.shouldDrawTrajectoryPoint(pathMoves, i)) {
                Sphere point = new Sphere(move.arcSegment() ? 1.35 : 1.22, 16);
                point.setTranslateX(endRadius);
                point.setTranslateY(move.endZ());
                point.setTranslateZ(0.0);
                point.setMaterial(move.rapid() ? this.trajectoryRapidPointMaterial : this.trajectoryPointMaterial);
                point.setOpacity(move.rapid() ? 0.94 : 1.0);
                point.setUserData(TRAJECTORY_POINT_3D_TAG);
                point.getProperties().put(GCODE_MOVE_PROPERTY, move);
                point.setMouseTransparent(false);
                point.setPickOnBounds(true);
                point.setDepthTest(DepthTest.DISABLE);
                trajectory.getChildren().add(point);
            }
        }
        if (!trajectory.getChildren().isEmpty()) {
            this.modelRoot.getChildren().add(trajectory);
            this.restoreViewportLayering();
        }
    }

    /**
     * Эквидистанта G41/G42 для отображения пути и позиции инструмента.
     * Радиус смещения: радиус пластины активного T/D, иначе радиус чашки из настроек; плюс OFFN.
     * Список сохраняется 1:1 — только координаты смещены.
     */
    private List<GCodeMoveData> compensatedDisplayMoves(List<GCodeMoveData> base) {
        if (base == null || base.isEmpty() || this.simulationContext == null) {
            return base == null ? List.of() : base;
        }
        double fallbackRadius = 0.0;
        MachineConfiguration machine = this.simulationContext.getMachineConfiguration();
        if (machine != null && machine.isUseCupRadius()) {
            fallbackRadius = Math.max(0.0, machine.getCupRadiusMm());
        }
        try {
            return LatheEquidistantPath.apply(
                    this.simulationContext.getProgramText(),
                    base,
                    this.simulationContext.getTools(),
                    fallbackRadius,
                    true,
                    equidistantSettings());
        } catch (RuntimeException ex) {
            return base;
        }
    }

    /** Настройки эквидистанты из окна настроек — читаем каждый раз, они меняются на ходу. */
    static LatheEquidistantPath.Settings equidistantSettings() {
        return new LatheEquidistantPath.Settings(
                UserSettings.isEquidistantEnabled(),
                UserSettings.isEquidistantRadiusFromLibrary(),
                UserSettings.getEquidistantRadiusMm(),
                UserSettings.getEquidistantAllowanceMm());
    }

    private boolean shouldDrawTrajectoryPoint(List<GCodeMoveData> pathMoves, int index) {
        if (pathMoves == null || index < 0 || index >= pathMoves.size()) {
            return false;
        }
        GCodeMoveData move = pathMoves.get(index);
        return index == pathMoves.size() - 1 || !move.arcSegment() || move.arcEnd();
    }

    private javafx.scene.Node createTrajectorySegment3d(
            double startRadius,
            double startZ,
            double endRadius,
            double endZ,
            PhongMaterial material,
            double segmentRadius) {
        double dx = endRadius - startRadius;
        double dy = endZ - startZ;
        double length = Math.hypot(dx, dy);
        if (!Double.isFinite(length) || length < 1.0e-6) {
            return null;
        }
        Cylinder cylinder = new Cylinder(segmentRadius, length, 10);
        cylinder.setCullFace(CullFace.NONE);
        cylinder.setMaterial(material);
        cylinder.setPickOnBounds(true);
        cylinder.setDepthTest(DepthTest.DISABLE);
        cylinder.setTranslateX((startRadius + endRadius) * 0.5);
        cylinder.setTranslateY((startZ + endZ) * 0.5);
        cylinder.setTranslateZ(0.0);
        Point3D direction = new Point3D(dx, dy, 0.0).normalize();
        Point3D yAxis = new Point3D(0.0, 1.0, 0.0);
        Point3D axis = yAxis.crossProduct(direction);
        double dot = Math.max(-1.0, Math.min(1.0, yAxis.dotProduct(direction)));
        double angle = Math.toDegrees(Math.acos(dot));
        if (axis.magnitude() < 1.0e-9) {
            if (dot < 0.0) {
                cylinder.getTransforms().add(new javafx.scene.transform.Rotate(
                        180.0, javafx.scene.transform.Rotate.Z_AXIS));
            }
        } else {
            cylinder.getTransforms().add(new javafx.scene.transform.Rotate(angle, axis));
        }
        return cylinder;
    }

    private List<GCodeMoveData> buildDisplayMoves() {
        MarkerBounds bounds = this.markerBounds();
        ArrayList<GCodeMoveData> result = new ArrayList<>();
        for (GCodeMoveData move : this.moves()) {
            double radius = LatheMeshBuilder.toRadius(move.endX());
            if (move.endZ() < bounds.zMin() || move.endZ() > bounds.zMax()) {
                continue;
            }
            if (radius > bounds.maxFeedRadius() + 2.0) {
                continue;
            }
            if (move.rapid() && radius > bounds.maxRapidRadius()) {
                continue;
            }
            result.add(move);
        }
        return result;
    }

    private MarkerBounds markerBounds() {
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        double maxFeedRadius = 0.0;
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        List<double[]> profile = this.cachedFinishedProfile.size() >= 2
                ? StockRemovalMeshBuilder.prepareDisplayProfile(
                        this.simulationContext,
                        this.cachedFinishedProfile,
                        stockRadius)
                : List.of();
        if (!profile.isEmpty()) {
            for (double[] point : profile) {
                zMin = Math.min(zMin, point[0]);
                zMax = Math.max(zMax, point[0]);
                maxFeedRadius = Math.max(maxFeedRadius, point[1]);
            }
        } else {
            for (double[] point : this.cachedFinishedProfile) {
                zMin = Math.min(zMin, point[0]);
                zMax = Math.max(zMax, point[0]);
                maxFeedRadius = Math.max(maxFeedRadius, point[1]);
            }
        }
        WorkpieceDefinition workpiece = this.simulationContext.getWorkpiece();
        if (!Double.isFinite(zMin) && workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
            maxFeedRadius = workpiece.getStockRadiusMm();
        }
        for (GCodeMoveData move : this.moves()) {
            if (move.rapid()) {
                continue;
            }
            zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
            zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            maxFeedRadius = Math.max(maxFeedRadius, LatheMeshBuilder.toRadius(move.endX()));
            maxFeedRadius = Math.max(maxFeedRadius, LatheMeshBuilder.toRadius(move.startX()));
        }
        if (!Double.isFinite(zMin)) {
            if (workpiece != null && workpiece.isValid()) {
                zMin = workpiece.getZMin();
                zMax = workpiece.getZMax();
                maxFeedRadius = workpiece.getStockRadiusMm();
            } else {
                zMin = -50.0;
                zMax = 50.0;
                maxFeedRadius = 50.0;
            }
        }
        double padZ = Math.max(10.0, (zMax - zMin) * 0.03);
        double padR = Math.max(4.0, maxFeedRadius * 0.06);
        return new MarkerBounds(
                zMin - padZ,
                zMax + padZ,
                maxFeedRadius + padR,
                maxFeedRadius + Math.max(8.0, padR * 0.45));
    }

    private void fitToContent() {
        if (this.is2dCanvasMode()) {
            this.halfCutPanX = 0.0;
            this.halfCutPanY = 0.0;
            this.halfCutZoom = 1.0;
            this.halfCutLayout = null;
            if (this.isCycleMode()) {
                this.cycleLayout = null;
                this.scheduleCycleCanvasRedraw();
            } else {
                this.schedule2dCanvasRedraw();
            }
            return;
        }
        this.orbitCamera.resetPan();
        if (this.isSideGraphMode()) {
            this.orbitCamera.applyLockedSideView();
        }
        if (this.moves().isEmpty() && this.cachedFinishedProfile.size() < 2) {
            this.orbitCamera.resetAngles();
            this.orbitCamera.setDistance(2200.0);
            return;
        }
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        List<double[]> fitProfile = this.cachedFinishedProfile.size() >= 2
                ? StockRemovalMeshBuilder.prepareDisplayProfile(
                        this.simulationContext,
                        this.cachedFinishedProfile,
                        stockRadius)
                : List.of();
        double maxR = 10.0;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double[] point : fitProfile) {
            maxR = Math.max(maxR, point[1]);
            minZ = Math.min(minZ, point[0]);
            maxZ = Math.max(maxZ, point[0]);
        }
        if (!Double.isFinite(minZ) && this.cachedFinishedProfile.size() >= 2) {
            for (double[] point : this.cachedFinishedProfile) {
                maxR = Math.max(maxR, point[1]);
                minZ = Math.min(minZ, point[0]);
                maxZ = Math.max(maxZ, point[0]);
            }
        }
        WorkpieceDefinition workpiece = this.simulationContext != null
                ? this.simulationContext.getWorkpiece()
                : null;
        if (workpiece != null && workpiece.isValid()) {
            maxR = Math.max(maxR, workpiece.getStockRadiusMm());
            minZ = Double.isFinite(minZ) ? Math.min(minZ, workpiece.getZMin()) : workpiece.getZMin();
            maxZ = Double.isFinite(maxZ) ? Math.max(maxZ, workpiece.getZMax()) : workpiece.getZMax();
        }
        if (!Double.isFinite(minZ)) {
            minZ = 0.0;
            maxZ = 100.0;
            maxR = 50.0;
        }
        double padZ = Math.max(2.0, (maxZ - minZ) * 0.02);
        minZ -= padZ;
        maxZ += padZ;
        this.updatePartBounds(maxR, minZ, maxZ);
        this.modelRoot.setTranslateX(0.0);
        this.modelRoot.setTranslateY(0.0);
        this.modelRoot.setTranslateZ(0.0);
        double centerZ = (minZ + maxZ) / 2.0;
        this.resetSpinOrientation();
        this.setOrbitPivotAtModelPoint(0.0, centerZ, 0.0, true);
        this.spinAnchorX = 0.0;
        this.spinAnchorY = centerZ;
        this.spinAnchorZ = 0.0;
        boolean exactOrthogonalReset = this.resetOrthogonalViewPending && this.viewMode == ViewMode.VIEW_3D
                && !this.isVerticalMachine();
        if (this.isSideGraphMode() || (this.isCycleMode() && !this.isVerticalMachine()) || exactOrthogonalReset) {
            this.orbitCamera.applyLockedSideView();
        } else {
            this.orbitCamera.applySimulationView(this.isVerticalMachine());
        }
        this.updatePivotGizmo();
        double spanZ = Math.max(20.0, maxZ - minZ);
        double viewW = Math.max(200.0, this.subScene.getWidth());
        double viewH = Math.max(200.0, this.subScene.getHeight());
        if (this.isVerticalMachine()) {
            // Fit the actual orientation: a wheel's diameter is horizontal, its
            // axial thickness is vertical. Include depth projected by the tilt.
            this.orbitCamera.fitToBounds(
                    Math.max(40.0, spanZ + maxR * 2.0),
                    Math.max(40.0, maxR * 2.0), viewW, viewH, 1.25);
        } else if (this.isSideGraphMode()) {
            // При открытии «Вид сбоку» отдаляем камеру ~в 2 раза: деталь мельче,
            // с полями по краям (как просили — «зум в 2 раза поменьше»).
            this.orbitCamera.fitToBounds(
                    Math.max(40.0, maxR * 2.05),
                    spanZ * 0.96,
                    viewW,
                    viewH,
                    2.1);
        } else if (this.isCycleMode()) {
            // Цикл 3D: отдаляем камеру (Сброс/вписывание показывает деталь дальше).
            this.orbitCamera.fitToBounds(
                    Math.max(40.0, maxR * 2.05),
                    spanZ * 0.96,
                    viewW,
                    viewH,
                    1.9);
        } else {
            // Обычный 3D: отдаляем камеру (Сброс/вписывание показывает деталь дальше).
            this.orbitCamera.fitToBounds(
                    Math.max(20.0, maxR * 2.0),
                    spanZ * 1.0,
                    viewW,
                    viewH,
                    1.7);
        }
        this.orbitCamera.centerProjectedBounds(
                this.subScene,
                this.spinGroup,
                -maxR * 1.06,
                minZ,
                maxR * 1.06,
                maxZ);
        this.resetOrthogonalViewPending = false;
        this.scheduleOverlayRefresh();
    }

    private void centerRenderedPartBounds() {
        if (this.subScene == null || this.modelRoot == null) {
            return;
        }
        Bounds bounds = this.renderedPartBounds();
        if (bounds == null || bounds.isEmpty()) {
            return;
        }
        for (int pass = 0; pass < 6; pass++) {
            this.orbitCamera.centerProjectedBounds(this.subScene, this.modelRoot, bounds);
        }
    }

    private Bounds renderedPartBounds() {
        Bounds result = null;
        for (javafx.scene.Node node : this.modelRoot.getChildren()) {
            Object tag = node.getUserData();
            if (!FINISHED_PART_TAG.equals(tag) && !STOCK_BLANK_TAG.equals(tag)) {
                continue;
            }
            Bounds bounds = node.getBoundsInParent();
            if (bounds == null || bounds.isEmpty()
                    || !Double.isFinite(bounds.getMinX())
                    || !Double.isFinite(bounds.getMinY())
                    || !Double.isFinite(bounds.getMinZ())
                    || !Double.isFinite(bounds.getMaxX())
                    || !Double.isFinite(bounds.getMaxY())
                    || !Double.isFinite(bounds.getMaxZ())) {
                continue;
            }
            result = result == null ? bounds : unionBounds(result, bounds);
        }
        return result;
    }

    private static Bounds unionBounds(Bounds a, Bounds b) {
        double minX = Math.min(a.getMinX(), b.getMinX());
        double minY = Math.min(a.getMinY(), b.getMinY());
        double minZ = Math.min(a.getMinZ(), b.getMinZ());
        double maxX = Math.max(a.getMaxX(), b.getMaxX());
        double maxY = Math.max(a.getMaxY(), b.getMaxY());
        double maxZ = Math.max(a.getMaxZ(), b.getMaxZ());
        return new BoundingBox(
                minX,
                minY,
                minZ,
                Math.max(0.0, maxX - minX),
                Math.max(0.0, maxY - minY),
                Math.max(0.0, maxZ - minZ));
    }

    private void scheduleOverlayRefresh() {
        if (this.overlayRefreshDebounce == null) {
            this.refreshOverlaysNow();
            return;
        }
        this.overlayRefreshDebounce.stop();
        this.overlayRefreshDebounce.playFromStart();
    }

    private void scheduleSidePanOverlayRefresh() {
        if (!this.isSideGraphMode()) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.lastSidePanOverlayRefreshNanos > 8_000_000L) {
            this.lastSidePanOverlayRefreshNanos = now;
            this.refreshGcodeOverlay();
            this.refreshMeasureOverlay();
        }
        if (this.sidePanOverlayRefreshQueued) {
            return;
        }
        this.sidePanOverlayRefreshQueued = true;
        javafx.application.Platform.runLater(() -> {
            this.sidePanOverlayRefreshQueued = false;
            if (!this.isSideGraphMode()) {
                return;
            }
            this.lastSidePanOverlayRefreshNanos = System.nanoTime();
            this.refreshGcodeOverlay();
            this.refreshMeasureOverlay();
            this.syncSideViewLabels();
            this.restoreViewportLayering();
        });
    }

    private void refreshOverlaysNow() {
        this.refreshGcodeOverlay();
        this.refreshMeasureOverlay();
        if (this.isSideGraphMode()) {
            this.syncSideViewLabels();
        }
        if (this.isCycleMode()) {
            this.scheduleCycleCanvasRedraw();
        }
        this.refreshMeasureOverlay();
    }

    private void rebuildOverlayMovesCache() {
        this.cachedOverlayMoves = this.graphToolpathMoves();
        if (this.cachedOverlayMoves.isEmpty()) {
            this.cachedOverlayMoves = this.buildDisplayMoves();
        }
    }

    /** Повторное вписывание после показа окна или готовности меша (корректный размер SubScene). */
    public void refitViewportAfterLayout() {
        if (this.is2dCanvasMode() || this.moves().isEmpty()) {
            return;
        }
        // Первичный fit — пока для текущего контекста ещё не делали финальное вписывание
        // на уже разложенный SubScene. Это позволяет точно центрировать модель после готовности меша,
        // но не сбрасывать камеру при «Обновить» с тем же G-code.
        boolean finishedPartReady = this.hasFinishedPartNode();
        if (this.lastFittedContext != this.simulationContext
                || this.viewportFitAfterLayoutPending
                || (finishedPartReady && !this.lastFitHadFinishedPart)) {
            this.fitToContent();
            this.view3dFitted = true;
            this.lastFittedContext = this.simulationContext;
            this.lastFitHadFinishedPart = finishedPartReady;
            this.viewportFitAfterLayoutPending = false;
        }
        this.rebuildOverlayMovesCache();
        this.refreshOverlaysNow();
        this.restoreViewportLayering();
    }

    private void forceViewportFitAfterLayout() {
        this.viewportFitAfterLayoutPending = true;
        javafx.application.Platform.runLater(() -> {
            if (this.subScene == null || this.is2dCanvasMode() || this.moves().isEmpty()) {
                return;
            }
            if (this.subScene.getWidth() <= 80.0 || this.subScene.getHeight() <= 80.0) {
                this.scheduleViewportFitAfterLayout();
                return;
            }
            this.runForcedViewportFitPass();
            this.queueForcedViewportFitPasses(4);
        });
    }

    private void runForcedViewportFitPass() {
        if (this.subScene == null || this.is2dCanvasMode() || this.moves().isEmpty()) {
            return;
        }
        this.resetOrthogonalViewPending = this.viewMode == ViewMode.VIEW_3D;
        this.fitToContent();
        this.view3dFitted = true;
        this.lastFittedContext = this.simulationContext;
        this.lastFitHadFinishedPart = this.hasFinishedPartNode();
        this.viewportFitAfterLayoutPending = false;
        this.refreshOverlaysNow();
        this.restoreViewportLayering();
    }

    private void queueForcedViewportFitPasses(int remainingPasses) {
        if (remainingPasses <= 0) {
            return;
        }
        javafx.application.Platform.runLater(() -> {
            this.runForcedViewportFitPass();
            this.queueForcedViewportFitPasses(remainingPasses - 1);
        });
    }

    void scheduleViewportFitAfterLayout() {
        javafx.application.Platform.runLater(() -> {
            if (this.subScene == null) {
                return;
            }
            if (this.subScene.getWidth() > 80.0 && this.subScene.getHeight() > 80.0) {
                this.refitViewportAfterLayout();
                javafx.application.Platform.runLater(this::refitViewportAfterLayout);
                return;
            }
            javafx.beans.value.ChangeListener<Number> fitWhenSized = new javafx.beans.value.ChangeListener<>() {
                @Override
                public void changed(
                        javafx.beans.value.ObservableValue<? extends Number> observable,
                        Number oldValue,
                        Number newValue
                ) {
                    if (subScene.getWidth() > 80.0 && subScene.getHeight() > 80.0) {
                        subScene.widthProperty().removeListener(this);
                        subScene.heightProperty().removeListener(this);
                        refitViewportAfterLayout();
                        javafx.application.Platform.runLater(Simulation3dController.this::refitViewportAfterLayout);
                    }
                }
            };
            this.subScene.widthProperty().addListener(fitWhenSized);
            this.subScene.heightProperty().addListener(fitWhenSized);
        });
    }

    private void syncSideViewLabels() {
        boolean show = this.isSideGraphMode();
        this.sideViewLabelsOverlay.getCanvas().setVisible(show);
        if (!show) {
            this.sideViewLabelsOverlay.clear();
            return;
        }
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        List<double[]> raw = this.cachedFinishedProfile.size() >= 2
                ? this.cachedFinishedProfile
                : StockRemovalMeshBuilder.buildFinishedProfile(this.simulationContext);
        List<double[]> profile = raw.size() >= 2
                ? StockRemovalMeshBuilder.prepareDisplayProfile(this.simulationContext, raw, stockRadius)
                : raw;
        this.sideViewLabelsOverlay.rebuild(
                this.orbitCamera,
                this.subScene,
                this.spinGroup,
                this.simulationContext,
                profile,
                this.darkTheme);
        this.sideViewLabelsOverlay.getCanvas().toFront();
        Canvas overlayCanvas = this.gcodeOverlay.getCanvas();
        if (this.showToolpath && overlayCanvas.isVisible()) {
            overlayCanvas.toFront();
        }
        this.ensureLoadingOnTop();
    }

    private List<double[]> profileForDisplay() {
        List<double[]> raw = this.cachedFinishedProfile.size() >= 2
                ? this.cachedFinishedProfile
                : StockRemovalMeshBuilder.buildFinishedProfile(this.simulationContext);
        return StockRemovalMeshBuilder.profileForSideView(raw);
    }

    /** Профиль для вкладки «Срез» (min R по Z). */
    private List<double[]> profileForHalfCut() {
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        if (this.cachedFinishedProfile.size() >= 2) {
            List<double[]> display = StockRemovalMeshBuilder.prepareDisplayProfile(
                    this.simulationContext,
                    this.cachedFinishedProfile,
                    stockRadius);
            if (StockRemovalMeshBuilder.profileHasMaterialRemoval(display, stockRadius)) {
                this.lastMeshError = null;
                return StockRemovalMeshBuilder.profileForSideView(display);
            }
        }
        List<double[]> simulated = LatheStockRemovalSimulator.buildRevolveProfile(this.simulationContext);
        if (simulated.size() >= 2) {
            simulated = StockRemovalMeshBuilder.trimToWorkpieceBounds(this.simulationContext, simulated);
            List<double[]> display = StockRemovalMeshBuilder.prepareDisplayProfile(
                    this.simulationContext,
                    simulated,
                    stockRadius);
            if (StockRemovalMeshBuilder.profileHasMaterialRemoval(display, stockRadius)) {
                this.lastMeshError = null;
                return StockRemovalMeshBuilder.profileForSideView(display);
            }
        }
        List<double[]> finished = StockRemovalMeshBuilder.buildFinishedProfile(this.simulationContext);
        if (finished.size() >= 2) {
            List<double[]> display = StockRemovalMeshBuilder.prepareDisplayProfile(
                    this.simulationContext,
                    finished,
                    stockRadius);
            if (StockRemovalMeshBuilder.profileHasMaterialRemoval(display, stockRadius)) {
                this.lastMeshError = null;
                return StockRemovalMeshBuilder.profileForSideView(display);
            }
        }
        if (stockRadius > 0.0 && !StockRemovalMeshBuilder.profileHasMaterialRemoval(simulated, stockRadius)) {
            this.lastMeshError =
                    I18n.text("sim.024")
                            + String.format(java.util.Locale.US, "%.0f", stockRadius * 2.0)
                            + I18n.text("sim.025")
                            + I18n.text("sim.026");
        }
        return StockRemovalMeshBuilder.profileForSideView(this.profileForDisplay());
    }

    private List<GCodeMoveData> movesForHalfCutDisplay() {
        List<GCodeMoveData> graphMoves = this.graphToolpathMoves();
        if (!graphMoves.isEmpty()) {
            return graphMoves;
        }
        return this.buildDisplayMoves();
    }

    private void ensureProfileCached() {
        if (this.cachedFinishedProfile.size() < 2) {
            MeshBuildResult result = StockRemovalMeshBuilder.buildFinishedPart(
                    this.simulationContext,
                    this.simulationContext.getProgramText());
            if (result.isSuccess()) {
                this.cachedFinishedProfile = StockRemovalMeshBuilder.buildFinishedProfile(this.simulationContext);
                this.lastMeshError = null;
            }
        }
    }

    private void syncToolpathOverlayVisibility() {
        Canvas overlayCanvas = this.gcodeOverlay.getCanvas();
        overlayCanvas.setMouseTransparent(true);
        this.buildToolpath();
        if (!this.showToolpath || !this.usesVisibleGcodeOverlay()) {
            overlayCanvas.setVisible(false);
            this.gcodeOverlay.clear();
            this.restoreViewportLayering();
            this.ensureLoadingOnTop();
            return;
        }
        this.rebuildOverlayMovesCache();
        if (this.cachedOverlayMoves.isEmpty()) {
            overlayCanvas.setVisible(false);
            this.gcodeOverlay.clear();
            this.restoreViewportLayering();
            this.ensureLoadingOnTop();
            return;
        }
        this.gcodeOverlay.setFlatProjection(this.isSideGraphMode());
        this.gcodeOverlay.rebuild(
                this.cachedOverlayMoves,
                true,
                this.overlayProjectionRoot(),
                this.simulationContext,
                this.subScene,
                this.orbitCamera,
                this.darkTheme);
        overlayCanvas.setVisible(true);
        this.restoreViewportLayering();
        this.ensureLoadingOnTop();
    }

    /** Управляет видимостью 2D-оверлея при экстремальном зуме.
     *  Сейчас оверлей используется во всех режимах через {@link #syncToolpathOverlayVisibility()}.
     *  При distance < 10мм можно скрыть оверлей для производительности. */
    private void syncOverlayForZoomLevel() {
        Canvas overlayCanvas = this.gcodeOverlay.getCanvas();
        boolean overlayMode = this.usesVisibleGcodeOverlay();
        overlayCanvas.setVisible(false);
        overlayCanvas.setMouseTransparent(true);
        if (this.showToolpath && overlayMode) {
            if (this.cachedOverlayMoves.isEmpty()) {
                this.rebuildOverlayMovesCache();
            }
            this.gcodeOverlay.setFlatProjection(this.isSideGraphMode());
            this.gcodeOverlay.refresh(this.darkTheme, true);
            overlayCanvas.setVisible(true);
        } else {
            this.gcodeOverlay.clear();
        }
    }

    private void refreshGcodeOverlay() {
        if (!this.showToolpath || !this.usesVisibleGcodeOverlay()) {
            this.gcodeOverlay.getCanvas().setVisible(false);
            return;
        }
        if (this.cachedOverlayMoves.isEmpty()) {
            this.rebuildOverlayMovesCache();
            if (!this.cachedOverlayMoves.isEmpty()) {
                this.gcodeOverlay.setFlatProjection(this.isSideGraphMode());
                this.gcodeOverlay.rebuild(
                        this.cachedOverlayMoves,
                        true,
                        this.overlayProjectionRoot(),
                        this.simulationContext,
                        this.subScene,
                        this.orbitCamera,
                        this.darkTheme);
            }
        }
        this.gcodeOverlay.setFlatProjection(this.isSideGraphMode());
        this.gcodeOverlay.refresh(this.darkTheme, true);
        this.gcodeOverlay.getCanvas().setVisible(this.usesVisibleGcodeOverlay());
    }

    private Group overlayProjectionRoot() {
        return this.spinGroup != null ? this.spinGroup : this.modelRoot;
    }

    private Group measureProjectionRoot() {
        return this.modelRoot;
    }

    private boolean usesVisibleGcodeOverlay() {
        return this.viewMode == ViewMode.SIDE_GRAPH;
    }

    private void clearMeasurePoints() {
        this.measurePoints.clear();
        this.pressedMeasurePoint = null;
        this.measureDragIndex = -1;
        this.measureHoverIndex = -1;
        this.refreshMeasureOverlay();
        this.updateMeasureReadout();
    }

    private void updateMeasureVisibility() {
        if (this.measureCanvas != null) {
            this.measureCanvas.setVisible(this.measureMode);
            this.measureCanvas.setMouseTransparent(true);
        }
        if (this.measureReadoutLabel != null) {
            this.measureReadoutLabel.setVisible(this.measureMode);
            this.measureReadoutLabel.setManaged(this.measureMode);
        }
        // Базовый курсор режима измерения — прицел; вне режима — обычный.
        javafx.scene.Cursor base = this.measureMode
                ? javafx.scene.Cursor.CROSSHAIR
                : javafx.scene.Cursor.DEFAULT;
        if (this.subScene != null) {
            this.subScene.setCursor(base);
        }
        if (this.halfCutCanvas != null) {
            this.halfCutCanvas.setCursor(base);
        }
        this.refreshMeasureOverlay();
    }

    private void handleMeasureClick(MouseEvent event) {
        if (event == null || !this.measureMode) {
            return;
        }
        this.addMeasurePoint(this.measurePointFromEvent(event));
    }

    private void addMeasurePoint(Point3D modelHit) {
        if (!this.measureMode) {
            return;
        }
        if (this.measurePoints.size() >= 3) {
            return;
        }
        if (modelHit == null
                || !Double.isFinite(modelHit.getX())
                || !Double.isFinite(modelHit.getY())
                || !Double.isFinite(modelHit.getZ())) {
            return;
        }
        this.measurePoints.add(modelHit);
        this.refreshMeasureOverlay();
        this.updateMeasureReadout();
        this.restoreViewportLayering();
    }

    private Point3D measurePointFromEvent(MouseEvent event) {
        if (this.viewMode == ViewMode.HALF_CUT && this.halfCutCanvas != null && this.halfCutCanvas.isVisible()) {
            Point2D local = this.localPointOnNode(this.halfCutCanvas, event);
            return local == null ? null : this.measurePointFromLayout(local.getX(), local.getY(), this.halfCutLayout);
        }
        if (this.cycleCanvas != null && this.cycleCanvas.isVisible()) {
            Point2D local = this.localPointOnNode(this.cycleCanvas, event);
            return local == null ? null : this.measurePointFromLayout(local.getX(), local.getY(), this.cycleLayout);
        }
        return this.measurePoint3dFromEvent(event);
    }

    /** Индекс точки рулетки под курсором (радиус захвата ~16 px) или -1. */
    private int measurePointIndexAt(double sceneX, double sceneY) {
        if (!this.measureMode || this.measurePoints.isEmpty() || this.measureCanvas == null) {
            return -1;
        }
        Point2D local = this.measureCanvas.sceneToLocal(sceneX, sceneY);
        if (local == null || !Double.isFinite(local.getX()) || !Double.isFinite(local.getY())) {
            return -1;
        }
        int best = -1;
        double bestDistance = 16.0;
        for (int i = 0; i < this.measurePoints.size(); i++) {
            Point2D screen = this.projectMeasurePoint(this.measurePoints.get(i));
            if (!this.isFiniteMeasureProjection(screen)) {
                continue;
            }
            double distance = screen.distance(local);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * Курсор рулетки: прицел в режиме измерения, «ладонь» над точкой,
     * «сжатая ладонь» при перетаскивании. Вне режима — обычный курсор.
     */
    private void updateMeasureCursor(javafx.scene.Node target, double sceneX, double sceneY) {
        if (target == null) {
            return;
        }
        if (!this.measureMode) {
            target.setCursor(javafx.scene.Cursor.DEFAULT);
            return;
        }
        if (this.measureDragIndex >= 0) {
            target.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            return;
        }
        int hover = this.measurePointIndexAt(sceneX, sceneY);
        if (hover != this.measureHoverIndex) {
            this.measureHoverIndex = hover;
            this.refreshMeasureOverlay();
        }
        target.setCursor(hover >= 0 ? javafx.scene.Cursor.OPEN_HAND : javafx.scene.Cursor.CROSSHAIR);
    }

    /** Перемещение таскаемой точки рулетки в новое место (3D или 2D-срез). */
    private void dragMeasurePointTo(Point3D next) {
        if (next == null
                || this.measureDragIndex < 0
                || this.measureDragIndex >= this.measurePoints.size()
                || !Double.isFinite(next.getX())
                || !Double.isFinite(next.getY())
                || !Double.isFinite(next.getZ())) {
            return;
        }
        // Невалидную позицию (за камерой, на бесконечности из-за скользящего луча
        // к плоскости) не принимаем: точка остаётся на последнем видимом месте,
        // а не «пропадает» при оттягивании далеко.
        if (Math.abs(next.getX()) > 100000.0
                || Math.abs(next.getY()) > 100000.0
                || Math.abs(next.getZ()) > 100000.0) {
            return;
        }
        Point2D screen = this.projectMeasurePoint(next);
        if (!this.isFiniteMeasureProjection(screen)) {
            return;
        }
        this.measurePoints.set(this.measureDragIndex, next);
        this.refreshMeasureOverlay();
        this.updateMeasureReadout();
    }

    /**
     * Точка измерения в 3D: пересечение с моделью, а если клик мимо модели —
     * проекция на плоскость Z-X детали (точки можно ставить в любом месте окна).
     */
    private Point3D measurePoint3dFromEvent(MouseEvent event) {
        Point3D picked = this.measurePointFromPick(event);
        if (picked != null) {
            return picked;
        }
        return this.measurePointOnProjectionPlane(event);
    }

    private Point2D localPointOnNode(javafx.scene.Node node, MouseEvent event) {
        if (node == null || event == null) {
            return null;
        }
        Point2D local = node.sceneToLocal(event.getSceneX(), event.getSceneY());
        if (local == null || !Double.isFinite(local.getX()) || !Double.isFinite(local.getY())) {
            return null;
        }
        return local;
    }

    private Point3D measurePointFromLayout(double screenX, double screenY, SinuTrainSideViewPainter.Layout layout) {
        if (layout == null || layout.scale() <= 0.0) {
            return null;
        }
        double modelZ = layout.minZ() + (screenX - layout.left()) / layout.scale();
        double modelR = layout.halfSectionAxis()
                ? (layout.axisR() - screenY) / layout.scale()
                : layout.minR() + (layout.axisR() - screenY) / layout.scale();
        return new Point3D(modelR, modelZ, 0.0);
    }

    private Point3D measurePointFromPick(MouseEvent event) {
        PickResult pick = event.getPickResult();
        if (pick == null
                || pick.getIntersectedNode() == null
                || this.isMeasureIgnoredPickNode(pick.getIntersectedNode())) {
            return null;
        }
        if (!this.isMeasurePickableSceneNode(pick.getIntersectedNode())) {
            return null;
        }
        Point3D localHit = pick.getIntersectedPoint();
        if (localHit == null) {
            return null;
        }
        // Оба преобразования — внутри SubScene. localToScene(..., true) выводил точку
        // во внешнюю сцену через проекцию камеры (глубина терялась), а sceneToLocal без
        // флага читал её как внутренние координаты SubScene — точка A/B/C улетала.
        Point3D sceneHit = pick.getIntersectedNode().localToScene(localHit);
        Group projectionRoot = this.measureProjectionRoot();
        return sceneHit != null && projectionRoot != null ? projectionRoot.sceneToLocal(sceneHit) : null;
    }

    private boolean isMeasurePickableSceneNode(javafx.scene.Node node) {
        if (node == null) {
            return false;
        }
        return this.isDescendantOf(node, this.modelRoot)
                || this.isDescendantOf(node, this.spinGroup)
                || (this.orbitCamera != null && this.isDescendantOf(node, this.orbitCamera.getContentRoot()));
    }

    private boolean isDescendantOf(javafx.scene.Node node, javafx.scene.Node ancestor) {
        if (node == null || ancestor == null) {
            return false;
        }
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private boolean isMeasureIgnoredPickNode(javafx.scene.Node node) {
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (current == this.pivotGizmo.getRoot()) {
                return true;
            }
            Object tag = current.getUserData();
            if (MEASURE_TOOL_TAG.equals(tag) || SIDE_GRID_TAG.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private Point3D measurePointOnProjectionPlane(MouseEvent event) {
        Group projectionRoot = this.measureProjectionRoot();
        if (this.subScene == null || this.orbitCamera == null || projectionRoot == null || event == null) {
            return null;
        }
        Point3D subLocal3 = this.subScene.sceneToLocal(new Point3D(event.getSceneX(), event.getSceneY(), 0.0));
        if (subLocal3 == null || !Double.isFinite(subLocal3.getX()) || !Double.isFinite(subLocal3.getY())) {
            return null;
        }
        try {
            return this.orbitCamera.unprojectSubSceneToLocalPlane(
                    this.subScene,
                    projectionRoot,
                    subLocal3.getX(),
                    subLocal3.getY(),
                    0.0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void refreshMeasureOverlay() {
        if (this.measureCanvas == null) {
            return;
        }
        this.renderMeasurePoints();
    }

    private void renderMeasurePoints() {
        double width = Math.max(1.0, this.measureCanvas.getWidth());
        double height = Math.max(1.0, this.measureCanvas.getHeight());
        GraphicsContext gc = this.measureCanvas.getGraphicsContext2D();
        gc.clearRect(0.0, 0.0, width, height);
        if (!this.measureMode || this.measurePoints.isEmpty()) {
            return;
        }
        Point2D[] screen = new Point2D[this.measurePoints.size()];
        for (int i = 0; i < this.measurePoints.size(); i++) {
            screen[i] = this.projectMeasurePoint(this.measurePoints.get(i));
        }
        if (screen.length >= 2) {
            this.drawMeasureSegment(gc, screen[0], screen[1], Color.web("#38bdf8", 0.98), 1.0);
        }
        if (screen.length >= 3) {
            this.drawMeasureSegment(gc, screen[1], screen[2], Color.web("#f59e0b", 0.98), 1.0);
            this.drawMeasureSegment(gc, screen[0], screen[2], Color.web("#f8fafc", this.darkTheme ? 0.92 : 0.86), 0.86);
        }
        for (int i = 0; i < screen.length; i++) {
            this.drawMeasureMarker(gc, screen[i], i);
        }
    }

    private Point2D projectMeasurePoint(Point3D point) {
        if (point == null) {
            return null;
        }
        if (this.viewMode == ViewMode.HALF_CUT && this.halfCutCanvas != null && this.halfCutCanvas.isVisible()) {
            return this.projectMeasurePoint2d(point, this.halfCutLayout);
        }
        if (this.cycleCanvas != null && this.cycleCanvas.isVisible()) {
            return this.projectMeasurePoint2d(point, this.cycleLayout);
        }
        return this.projectMeasurePoint3d(point);
    }

    private Point2D projectMeasurePoint2d(Point3D point, SinuTrainSideViewPainter.Layout layout) {
        if (layout == null || layout.scale() <= 0.0) {
            return null;
        }
        return new Point2D(layout.toScreenZ(point.getY()), layout.toScreenR(point.getX()));
    }

    private Point2D projectMeasurePoint3d(Point3D point) {
        Group projectionRoot = this.measureProjectionRoot();
        if (this.subScene == null || projectionRoot == null || point == null || this.measureCanvas == null) {
            return null;
        }
        try {
            // Точка позади камеры (при сильном зуме «внутрь») проецируется зеркально
            // и маркер «улетает» — такие точки не рисуем, при отдалении вернутся.
            if (this.orbitCamera != null && this.orbitCamera.getCamera() != null) {
                Point3D innerScene = projectionRoot.localToScene(point);
                Point3D camLocal = this.orbitCamera.getCamera().sceneToLocal(innerScene);
                if (camLocal == null
                        || !Double.isFinite(camLocal.getZ())
                        || camLocal.getZ() <= this.orbitCamera.getCamera().getNearClip()) {
                    return null;
                }
            }
            // Проекция средствами JavaFX: модель → внешняя сцена (через камеру SubScene),
            // затем в координаты канвы измерений. Это ровно тот же путь, что у picking,
            // поэтому маркер ложится точно под клик; ручная матрица камеры давала смещение.
            Point3D scenePt = projectionRoot.localToScene(point, true);
            if (scenePt == null
                    || !Double.isFinite(scenePt.getX())
                    || !Double.isFinite(scenePt.getY())) {
                return null;
            }
            return this.measureCanvas.sceneToLocal(scenePt.getX(), scenePt.getY());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isFiniteMeasureProjection(Point2D point) {
        return point != null
                && Double.isFinite(point.getX())
                && Double.isFinite(point.getY())
                && Math.abs(point.getX()) <= 50_000_000.0
                && Math.abs(point.getY()) <= 50_000_000.0;
    }

    private void drawMeasureSegment(GraphicsContext gc, Point2D first, Point2D second, Color color, double opacity) {
        if (!this.isFiniteMeasureProjection(first) || !this.isFiniteMeasureProjection(second)) {
            return;
        }
        gc.setLineDashes(null);
        gc.setStroke(this.darkTheme ? Color.web("#020617", 0.82) : Color.web("#ffffff", 0.88));
        gc.setLineWidth(6.0);
        gc.strokeLine(first.getX(), first.getY(), second.getX(), second.getY());
        gc.setStroke(new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
        gc.setLineWidth(3.4);
        gc.strokeLine(first.getX(), first.getY(), second.getX(), second.getY());
    }

    private void drawMeasureMarker(GraphicsContext gc, Point2D point, int index) {
        if (!this.isFiniteMeasureProjection(point)) {
            return;
        }
        Color[] fills = {
                Color.web("#22d3ee"),
                Color.web("#facc15"),
                Color.web("#fb7185")
        };
        String[] labels = {"A", "B", "C"};
        boolean dragged = index == this.measureDragIndex;
        boolean hovered = dragged || index == this.measureHoverIndex;
        double radius = hovered ? 13.0 : 11.0;
        Color fill = fills[Math.min(index, fills.length - 1)];
        double x = point.getX();
        double y = point.getY();
        if (hovered) {
            // Ореол вокруг точки под курсором/перетаскиваемой — видно, что её можно тащить.
            gc.setFill(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), dragged ? 0.30 : 0.20));
            double halo = radius + 7.0;
            gc.fillOval(x - halo, y - halo, halo * 2.0, halo * 2.0);
        }
        gc.setFill(fill);
        gc.fillOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
        gc.setStroke(this.darkTheme ? Color.web("#f8fafc", 0.96) : Color.web("#0f172a", 0.96));
        gc.setLineWidth(hovered ? 2.8 : 2.2);
        gc.strokeOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
        gc.setFill(Color.web("#0f172a"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 13.0));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(labels[Math.min(index, labels.length - 1)], x, y - 0.5);
    }

    private void updateMeasureReadout() {
        if (this.measureReadoutLabel == null) {
            return;
        }
        int count = this.measurePoints.size();
        if (count <= 0) {
            this.measureReadoutLabel.setText(I18n.text("sim.001"));
        } else if (count == 1) {
            this.measureReadoutLabel.setText(I18n.text("sim.027"));
        } else if (count == 2) {
            this.measureReadoutLabel.setText(String.format(
                    Locale.US,
                    "A-B %s",
                    this.measureDistanceText(0, 1)));
        } else {
            this.measureReadoutLabel.setText(String.format(
                    Locale.US,
                    "A-B %s   A-C %s   B-C %s",
                    this.measureDistanceText(0, 1),
                    this.measureDistanceText(0, 2),
                    this.measureDistanceText(1, 2)));
        }
    }

    private String measureDistanceText(int first, int second) {
        if (first < 0 || second < 0 || first >= this.measurePoints.size() || second >= this.measurePoints.size()) {
            return "--";
        }
        return String.format(Locale.US, "%.3f mm", this.measurePoints.get(first).distance(this.measurePoints.get(second)));
    }

    private GCodeMoveData pickedTrajectoryMove(MouseEvent event) {
        PickResult pick = event.getPickResult();
        GCodeMoveData hit = pick != null ? this.moveFromPickedNode(pick.getIntersectedNode()) : null;
        if (hit != null) {
            return hit;
        }
        if (!this.usesVisibleGcodeOverlay() || !this.gcodeOverlay.getCanvas().isVisible()) {
            return null;
        }
        javafx.geometry.Point2D local = this.subScene.sceneToLocal(event.getSceneX(), event.getSceneY());
        return this.gcodeOverlay.findPointAt(local.getX(), local.getY());
    }

    private GCodeMoveData moveFromPickedNode(javafx.scene.Node node) {
        javafx.scene.Node current = node;
        while (current != null) {
            Object value = current.getProperties().get(GCODE_MOVE_PROPERTY);
            if (value instanceof GCodeMoveData move) {
                return move;
            }
            current = current.getParent();
        }
        return null;
    }

    private void updatePickedTrajectoryStatus(GCodeMoveData hit) {
        this.gcodeOverlay.setHoveredMove(hit);
        if (this.coordLabel != null) {
            this.coordLabel.setText(this.gcodeOverlay.formatSelectionSummary(
                    this.gcodeOverlay.getSelectedMoves(),
                    hit));
        }
    }

    private void installMouseHandlers() {
        this.subScene.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> event.consume());
        this.subScene.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            this.dragStartX = event.getSceneX();
            this.dragStartY = event.getSceneY();
            this.clickCandidate = false;
            this.panDrag = false;
            this.orbitDrag = false;
            this.orbitPivotAnchored = false;
            this.orbitPickLocal = null;
            if (this.is2dCanvasMode()) {
                return;
            }
            if (this.measureMode && event.getButton() == MouseButton.PRIMARY) {
                int dragIndex = this.measurePointIndexAt(event.getSceneX(), event.getSceneY());
                if (dragIndex >= 0) {
                    // Захват существующей точки A/B/C — перетаскивание вместо камеры.
                    this.measureDragIndex = dragIndex;
                    this.measureHoverIndex = dragIndex;
                    this.pressedMeasurePoint = null;
                    this.subScene.setCursor(javafx.scene.Cursor.CLOSED_HAND);
                    this.refreshMeasureOverlay();
                    event.consume();
                    return;
                }
                this.clickCandidate = true;
                this.pressedMeasurePoint = this.measurePoint3dFromEvent(event);
                if (this.isSideGraphMode()) {
                    this.panDrag = true;
                } else {
                    this.orbitDrag = true;
                    this.orbitMoved = false;
                    this.prepareOrbitGesture(event);
                }
                event.consume();
                return;
            }
            if (this.isSideGraphMode()) {
                if (event.getButton() == MouseButton.PRIMARY) {
                    this.clickCandidate = true;
                    this.panDrag = true;
                } else if (event.getButton() == MouseButton.SECONDARY) {
                    this.panDrag = true;
                }
                event.consume();
                return;
            }
            if (event.getButton() == MouseButton.PRIMARY && !event.isShiftDown()) {
                this.orbitDrag = true;
                this.orbitMoved = false;
                this.clickCandidate = true;
                this.prepareOrbitGesture(event);
                event.consume();
            } else if (event.getButton() == MouseButton.SECONDARY
                    || (event.getButton() == MouseButton.PRIMARY && event.isShiftDown())) {
                this.panDrag = true;
                if (event.getButton() == MouseButton.PRIMARY) {
                    this.clickCandidate = true;
                }
                event.consume();
            }
        });
        this.subScene.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (this.measureDragIndex >= 0) {
                this.dragMeasurePointTo(this.measurePoint3dFromEvent(event));
                event.consume();
                return;
            }
            double dx = event.getSceneX() - this.dragStartX;
            double dy = event.getSceneY() - this.dragStartY;
            if (Math.hypot(dx, dy) > 4.0) {
                this.clickCandidate = false;
                this.pressedMeasurePoint = null;
            }
            if (this.panDrag) {
                this.orbitCamera.dragPan(dx, dy, this.isSideGraphMode(), this.subScene.getHeight());
                this.dragStartX = event.getSceneX();
                this.dragStartY = event.getSceneY();
                if (this.isSideGraphMode()) {
                    this.scheduleSidePanOverlayRefresh();
                } else {
                    this.refreshOverlaysNow();
                    this.syncOverlayForZoomLevel();
                }
                event.consume();
                return;
            }
            if (this.orbitDrag) {
                if (Math.hypot(dx, dy) > 2.0) {
                    this.orbitMoved = true;
                    this.syncPivotGizmoVisibility();
                }
                this.orbitCamera.dragOrbit(dx, dy);
                this.dragStartX = event.getSceneX();
                this.dragStartY = event.getSceneY();
                this.updatePivotGizmo();
                this.refreshOverlaysNow();
                this.syncOverlayForZoomLevel();
                event.consume();
                return;
            }
        });
        this.subScene.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (this.measureDragIndex >= 0 && event.getButton() == MouseButton.PRIMARY) {
                this.measureDragIndex = -1;
                this.panDrag = false;
                this.orbitDrag = false;
                this.clickCandidate = false;
                this.pressedMeasurePoint = null;
                this.updateMeasureCursor(this.subScene, event.getSceneX(), event.getSceneY());
                this.refreshMeasureOverlay();
                event.consume();
                return;
            }
            boolean wasSidePan = this.panDrag && this.isSideGraphMode();
            if (this.measureMode && event.getButton() == MouseButton.PRIMARY && this.clickCandidate) {
                Point3D modelHit = this.pressedMeasurePoint != null
                        ? this.pressedMeasurePoint
                        : this.measurePoint3dFromEvent(event);
                this.addMeasurePoint(modelHit);
                this.pressedMeasurePoint = null;
                this.clickCandidate = false;
                event.consume();
            }
            if (!this.orbitMoved
                    && (this.viewMode == ViewMode.VIEW_3D || this.isSideGraphMode() || this.isCycleMode())
                    && event.getButton() == MouseButton.PRIMARY
                    && this.clickCandidate) {
                GCodeMoveData hit = this.pickedTrajectoryMove(event);
                if (hit != null) {
                    this.toggleSimulationPointSelection(hit);
                } else if (event.isShiftDown()) {
                    this.gcodeOverlay.clearSelection(this.darkTheme, this.showToolpath);
                    this.clearSimulationEditorSelection();
                }
                this.updateSelectionStatus();
            }
            this.orbitDrag = false;
            this.panDrag = false;
            this.lastSidePanOverlayRefreshNanos = 0L;
            this.clickCandidate = false;
            this.orbitMoved = false;
            this.orbitPivotAnchored = false;
            this.orbitPickLocal = null;
            this.pressedMeasurePoint = null;
            this.orbitCamera.endOrbitGesture();
            this.syncPivotGizmoVisibility();
            if (wasSidePan) {
                this.refreshOverlaysNow();
                this.syncOverlayForZoomLevel();
            }
        });
        this.subScene.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            if (this.is2dCanvasMode()) {
                return;
            }
            if (this.measureMode) {
                this.updateMeasureCursor(this.subScene, event.getSceneX(), event.getSceneY());
            }
            if (this.isSideGraphMode()) {
                this.updatePickedTrajectoryStatus(this.pickedTrajectoryMove(event));
                return;
            }
            this.updatePickedTrajectoryStatus(this.pickedTrajectoryMove(event));
        });
        this.subScene.addEventHandler(ScrollEvent.SCROLL, event -> {
            Point2D anchor = this.subScene.sceneToLocal(event.getSceneX(), event.getSceneY());
            this.orbitCamera.zoomByWheelAt(
                    event.getDeltaY(),
                    anchor.getX(),
                    anchor.getY(),
                    this.subScene.getWidth(),
                    this.subScene.getHeight());
            // Всегда обновляем overlay при скролле, даже на близком зуме.
            // syncOverlayForZoomLevel скроет overlay при distance < 10мм.
            if (this.isSideGraphMode()) {
                this.scheduleSidePanOverlayRefresh();
            } else {
                this.refreshOverlaysNow();
                this.syncOverlayForZoomLevel();
            }
            event.consume();
        });
        this.halfCutCanvas.setOnScroll(event -> {
            this.zoomHalfCutCanvasAt(event);
            event.consume();
        });
    }

    private void zoomHalfCutCanvasAt(ScrollEvent event) {
        SinuTrainSideViewPainter.Layout layout = this.halfCutLayout;
        if (layout == null) {
            double width = Math.max(200.0, this.halfCutCanvas.getWidth());
            double height = Math.max(200.0, this.halfCutCanvas.getHeight());
            List<GCodeMoveData> baseMoves = this.graphToolpathMoves();
            List<GCodeMoveData> pathMoves = baseMoves.isEmpty() ? this.buildDisplayMoves() : baseMoves;
            List<double[]> profile = this.profileForHalfCut();
            layout = SinuTrainSideViewPainter.computeHalfSectionLayout(
                        width,
                        height,
                        this.simulationContext,
                        profile,
                        pathMoves,
                        this.halfCutPanX,
                        this.halfCutPanY,
                        this.halfCutZoom);
        }
        this.zoomCanvasAt(event, layout, this::schedule2dCanvasRedraw);
    }

    private void zoomCycleCanvasAt(ScrollEvent event) {
        SinuTrainSideViewPainter.Layout layout = this.cycleLayout;
        if (layout == null) {
            List<GCodeMoveData> pathMoves = this.cycleMoves();
            int activeIndex = this.activeCycleMoveIndex(pathMoves);
            List<double[]> profile = this.interpolatedCycleProfile(activeIndex, pathMoves);
            double width = Math.max(200.0, this.cycleCanvas.getWidth());
            double height = Math.max(200.0, this.cycleCanvas.getHeight());
            layout = SinuTrainSideViewPainter.computeLayout(
                        width,
                        height,
                        this.simulationContext,
                        profile,
                        pathMoves,
                        this.halfCutPanX,
                        this.halfCutPanY,
                        this.halfCutZoom);
        }
        this.zoomCanvasAt(event, layout, this::scheduleCycleCanvasRedraw);
    }

    private void zoomCanvasAt(
            ScrollEvent event,
            SinuTrainSideViewPainter.Layout layout,
            Runnable redraw
    ) {
        if (event == null || layout == null || layout.scale() <= 0.0) {
            return;
        }
        double modelZ = layout.minZ() + (event.getX() - layout.left()) / layout.scale();
        double modelR = layout.halfSectionAxis()
                ? (layout.axisR() - event.getY()) / layout.scale()
                : layout.minR() + (layout.axisR() - event.getY()) / layout.scale();
        double factor = event.getDeltaY() > 0.0 ? 1.08 : 0.92;
        double nextZoom = Math.max(0.06, Math.min(240.0, this.halfCutZoom * factor));
        if (!Double.isFinite(nextZoom) || Math.abs(nextZoom - this.halfCutZoom) < 1.0e-9) {
            return;
        }
        double zoomRatio = nextZoom / this.halfCutZoom;
        double nextScale = layout.scale() * zoomRatio;
        double nextScreenZ = layout.left() + (modelZ - layout.minZ()) * nextScale;
        double nextScreenR = layout.halfSectionAxis()
                ? layout.axisR() - modelR * nextScale
                : layout.axisR() - (modelR - layout.minR()) * nextScale;
        this.halfCutZoom = nextZoom;
        this.halfCutPanX += event.getX() - nextScreenZ;
        this.halfCutPanY += event.getY() - nextScreenR;
        redraw.run();
        this.refreshMeasureOverlay();
    }

    private void schedule2dCanvasRedraw() {
        if (!this.is2dCanvasMode() || this.redraw2dQueued) {
            return;
        }
        this.redraw2dQueued = true;
        javafx.application.Platform.runLater(() -> {
            this.redraw2dQueued = false;
            this.redraw2dCanvas();
        });
    }

    private void redraw2dCanvas() {
        if (!this.halfCutCanvas.isVisible()) {
            return;
        }
        double width = Math.max(200.0, this.halfCutCanvas.getWidth());
        double height = Math.max(200.0, this.halfCutCanvas.getHeight());
        List<GCodeMoveData> pathMoves = this.graphToolpathMoves();
        if (pathMoves.isEmpty()) {
            pathMoves = this.buildDisplayMoves();
        }
        this.displayMoves = pathMoves;
        List<double[]> profile = this.profileForHalfCut();
        // Продольный полусрез: ось Z и шкала закреплены внизу, как в «Вид сбоку»,
        // чтобы при максимальном зуме не терялись нижняя ось и подписи.
        this.halfCutLayout = SinuTrainSideViewPainter.computeHalfSectionLayout(
                width,
                height,
                this.simulationContext,
                profile,
                pathMoves,
                this.halfCutPanX,
                this.halfCutPanY,
                this.halfCutZoom);
        SinuTrainSideViewPainter.paint(
                this.halfCutCanvas.getGraphicsContext2D(),
                width,
                height,
                this.halfCutLayout,
                this.simulationContext,
                StockRemovalMeshBuilder.profileForSideView(profile),
                pathMoves,
                this.halfCutHoverMove,
                this.lastMeshError,
                this.darkTheme,
                true);
        this.refreshMeasureOverlay();
    }

    private void installCycleMouseHandlers() {
        this.cycleCanvas.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> event.consume());
        this.cycleCanvas.setOnScroll(event -> {
            this.zoomCycleCanvasAt(event);
            event.consume();
        });
        this.cycleCanvas.setOnMousePressed(event -> {
            this.dragStartX = event.getX();
            this.dragStartY = event.getY();
            if (event.getButton() == MouseButton.PRIMARY || event.getButton() == MouseButton.SECONDARY) {
                this.panDrag = true;
                this.clickCandidate = event.getButton() == MouseButton.PRIMARY;
                event.consume();
            }
        });
        this.cycleCanvas.setOnMouseDragged(event -> {
            double dx = event.getX() - this.dragStartX;
            double dy = event.getY() - this.dragStartY;
            if (Math.hypot(dx, dy) > 4.0) {
                this.clickCandidate = false;
            }
            if (this.panDrag) {
                this.halfCutPanX += dx;
                this.halfCutPanY += dy;
                this.dragStartX = event.getX();
                this.dragStartY = event.getY();
                this.scheduleCycleCanvasRedraw();
                event.consume();
            }
        });
        this.cycleCanvas.setOnMouseReleased(event -> {
            if (this.clickCandidate && event.getButton() == MouseButton.PRIMARY) {
                if (this.measureMode) {
                    this.handleMeasureClick(event);
                } else {
                    this.handleCycleMouse(event, true);
                }
            }
            this.panDrag = false;
            this.clickCandidate = false;
        });
        this.cycleCanvas.setOnMouseMoved(event -> this.handleCycleMouse(event, false));
    }

    private void configureToolbarSizing() {
        if (this.rootPane != null) {
            this.rootPane.setMinWidth(0.0);
            this.rootPane.setMinHeight(0.0);
        }
        if (this.toolbar != null) {
            this.toolbar.setMinWidth(0.0);
        }
        if (this.viewportStack != null) {
            this.viewportStack.setMinWidth(0.0);
            this.viewportStack.setMinHeight(0.0);
        }
        if (this.statusLabel != null) {
            this.statusLabel.setMinWidth(0.0);
            this.statusLabel.setPrefWidth(0.0);
            this.statusLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(this.statusLabel, Priority.ALWAYS);
            this.statusLabel.setAlignment(Pos.CENTER);
            this.statusLabel.setTextAlignment(TextAlignment.CENTER);
            this.statusLabel.setPadding(new Insets(0.0, 16.0, 0.0, 24.0));
            this.statusLabel.setWrapText(false);
            this.statusLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        }
        if (this.toolbarRight != null) {
            this.toolbarRight.setMinWidth(0.0);
            this.toolbarRight.setPrefWidth(Region.USE_COMPUTED_SIZE);
            HBox.setHgrow(this.toolbarRight, Priority.NEVER);
        }
        if (this.coordLabel != null) {
            this.coordLabel.setMinWidth(170.0);
            this.coordLabel.setPrefWidth(Region.USE_COMPUTED_SIZE);
            this.coordLabel.setMaxWidth(260.0);
            this.coordLabel.setAlignment(Pos.CENTER);
            HBox.setMargin(this.coordLabel, new Insets(0.0, 16.0, 0.0, 24.0));
        }
        if (this.toolbar != null) {
            this.configureToolbarButtons(this.toolbar);
        }
    }

    private void configureToolbarButtons(javafx.scene.Parent parent) {
        if (parent == null) {
            return;
        }
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Button button) {
                this.configureToolbarButton(button);
            }
            if (child instanceof javafx.scene.Parent nested) {
                this.configureToolbarButtons(nested);
            }
        }
    }

    private void configureToolbarButton(Button button) {
        if (button == null) {
            return;
        }
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setPrefWidth(Region.USE_COMPUTED_SIZE);
        button.setMaxWidth(Region.USE_PREF_SIZE);
        button.setMinHeight(Region.USE_PREF_SIZE);
        button.setWrapText(false);
    }

    private void initCycleControls() {
        this.cyclePlayPauseButton = new Button("▶");
        this.cycleSpeedButton = new Button("1x");
        Button cycleBackButton = new Button("<<");
        Button cycleForwardButton = new Button(">>");
        Button cycleRestartButton = new Button("⏹");
        this.cyclePlayPauseButton.getStyleClass().addAll("simulation3d-cycle-control", "simulation3d-cycle-play");
        this.cycleSpeedButton.getStyleClass().addAll("simulation3d-cycle-control", "simulation3d-cycle-speed");
        cycleRestartButton.getStyleClass().addAll("simulation3d-cycle-control", "simulation3d-cycle-restart");
        cycleBackButton.getStyleClass().addAll("simulation3d-cycle-control", "simulation3d-cycle-step");
        cycleForwardButton.getStyleClass().addAll("simulation3d-cycle-control", "simulation3d-cycle-step");
        this.cyclePlayPauseButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.028"))));
        this.cycleSpeedButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.029"))));
        cycleRestartButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.030"))));
        cycleBackButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.031"))));
        cycleForwardButton.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("sim.032"))));
        this.cyclePlayPauseButton.setOnAction(event -> this.toggleCyclePlayback());
        this.cycleSpeedButton.setOnAction(event -> this.advanceCycleSpeed());
        cycleRestartButton.setCancelButton(false);
        cycleRestartButton.setDefaultButton(false);
        cycleRestartButton.setFocusTraversable(false);
        cycleRestartButton.setOnAction(event -> {
            event.consume();
            this.restartCycleFromToolbar();
        });
        cycleBackButton.setOnAction(event -> this.seekCycleSeconds(-10.0));
        cycleForwardButton.setOnAction(event -> this.seekCycleSeconds(10.0));
        this.cycleControls = new HBox(
                6.0,
                cycleRestartButton,
                cycleBackButton,
                this.cyclePlayPauseButton,
                cycleForwardButton,
                this.cycleSpeedButton);
        this.configureToolbarButtons(this.cycleControls);
        this.cycleControls.getStyleClass().add("simulation3d-cycle-controls");
        this.cycleControls.setAlignment(Pos.CENTER);
        this.cycleControls.setPadding(new Insets(0.0));
        HBox.setMargin(this.cycleControls, new Insets(0.0, 0.0, 0.0, 28.0));
        this.cycleControls.setVisible(false);
        this.cycleControls.setManaged(false);
        this.updateCycleControls();
    }

    private void attachCycleControlsToToolbar() {
        HBox target = this.toolbarRight != null ? this.toolbarRight : this.toolbar;
        if (target == null || this.cycleControls == null || target.getChildren().contains(this.cycleControls)) {
            return;
        }
        int coordIndex = this.coordLabel != null ? target.getChildren().indexOf(this.coordLabel) : -1;
        int insertIndex = coordIndex >= 0 ? coordIndex + 1 : target.getChildren().size();
        target.getChildren().add(insertIndex, this.cycleControls);
    }

    public boolean hasSetupSequence() {
        return this.completeSimulationContext != null && this.completeSimulationContext.getPrecedingSetup() != null;
    }

    /** Runs on FX, reads the same frame that positions both tool models. No rebuild. */
    public java.util.Map<String,Object> liveState() {
        var state = new java.util.LinkedHashMap<String,Object>();
        state.put("open",true);
        state.put("view",this.viewMode.name());
        state.put("loading",this.loadingOverlay!=null && this.loadingOverlay.isVisible());
        state.put("playing",this.cyclePlaying);
        state.put("setupSide",this.displayedSetupSide());
        state.put("speed",this.cycleSpeed);
        state.put("requestedSeconds",this.cycleDistanceProgress/CYCLE_BASE_FEED_MM_PER_SEC);
        double time = Math.max(0,this.renderedCycleMeshFrame)*CYCLE_SEGMENT_MM_SMOOTH/CYCLE_BASE_FEED_MM_PER_SEC;
        state.put("renderedSeconds",this.renderedCycleMeshFrame < 0 ? null : time);
        state.put("meshPending",this.cycleMeshBuildRunning || (isCycleMode() && this.renderedCycleMeshFrame != this.cycleMeshFrameForProgress()));
        state.put("turnoverPreparing",this.turnoverPreparing);
        state.put("turnoverFraction",this.turnoverProgress);
        state.put("frame",this.renderedCycleMeshFrame);
        state.put("error",this.lastMeshError);
        state.put("statusText",this.statusLabel == null ? "" : this.statusLabel.getText());
        state.put("geometryNotice",this.missingToolNotice);
        state.put("framesPerSecond",this.cycleFpsEma);
        state.put("lastMeshBuildMs",this.cycleLastMeshBuildMs);
        state.put("limitations",java.util.List.of("Machine-frame G153/SUPA positioning requires machine kinematics; unknown travel is not connected through the workpiece.",
                "Cycle timing does not emulate PLC synchronization or tool-change mechanics.",
                "Holder models are representative library assemblies, not certified machine collision envelopes."));
        if(this.simulationContext!=null && this.simulationContext.getWorkpiece()!=null) {
            var blank=this.simulationContext.getWorkpiece();
            state.put("stock",java.util.Map.of("diameterMm",blank.getDiameterMm(),"zMinMm",blank.getZMin(),
                    "zMaxMm",blank.getZMax(),"initialBoreDiameterMm",blank.getInitialBoreDiameterMm()));
        }
        var channels = new java.util.ArrayList<java.util.Map<String,Object>>();
        if (this.isCycleMode() && this.verticalCyclePath != null && this.renderedCycleMeshFrame >= 0) {
            var lines = this.simulationContext.getProgramText().split("\\R",-1);
            int boundary = com.sergey.pisarev.service.MultiChannelSimulation.channelBoundaryLine(this.simulationContext.getProgramText());
            state.put("durationSeconds",this.verticalCyclePath.durationSeconds());
            for (int c=0;c<this.verticalCyclePath.channelCount();c++) {
                var pose = this.verticalCyclePath.poseAt(c,time);
                if (pose == null) continue;
                var m = pose.contour(); var center = pose.centre(); double f = pose.fraction();
                var row = new java.util.LinkedHashMap<String,Object>();
                row.put("support",this.verticalCyclePath.supportNumber(c));
                row.put("active",this.verticalCyclePath.activeAt(c,time));
                row.put("sourceLine",m.sourceLine());
                row.put("channelLine",m.sourceLine()>boundary ? m.sourceLine()-boundary : m.sourceLine());
                row.put("blockText",m.sourceLine()>0 && m.sourceLine()<=lines.length ? lines[m.sourceLine()-1] : "");
                row.put("toolNumber",m.toolNumber()); row.put("edge",m.edgeNumber());
                row.put("rapid",m.rapid()); row.put("spindleCuttingEnabled",pose.cuttingEnabled()); row.put("fraction",f);
                row.put("contourXmm",m.startX()+(m.endX()-m.startX())*f);
                row.put("contourZmm",m.startZ()+(m.endZ()-m.startZ())*f);
                row.put("centerRadiusMm",(center.startX()+(center.endX()-center.startX())*f)*.5);
                row.put("centerZmm",center.startZ()+(center.endZ()-center.startZ())*f);
                var tool = this.findToolDefinition(m);
                if (tool != null) {
                    row.put("radiusMm",tool.getRadius()); row.put("modelId",tool.getModelId());
                    row.put("toolPosition",tool.getToolPosition());
                    row.put("referencePoint",tool.getToolPosition()==9 ? "nose-centre-S" : "imaginary-tip-P");
                }
                row.put("centerMinusContourRadiusMm",((Number)row.get("centerRadiusMm")).doubleValue()
                        - ((Number)row.get("contourXmm")).doubleValue()*.5);
                row.put("centerMinusContourZmm",((Number)row.get("centerZmm")).doubleValue()
                        - ((Number)row.get("contourZmm")).doubleValue());
                if (this.cycleTool3d != null) for (var node:this.cycleTool3d.getChildren()) {
                    if (!Integer.valueOf(this.verticalCyclePath.supportNumber(c)).equals(node.getUserData())) continue;
                    row.put("displayXmm",node.getTranslateX()); row.put("displayZmm",node.getTranslateY());
                    row.put("presentedSeconds",node.getProperties().get("presented-seconds"));
                    if (node instanceof Group g && !g.getChildren().isEmpty())
                        row.put("asset",g.getChildren().get(0).getProperties().get("tool-asset"));
                }
                channels.add(row);
            }
        }
        state.put("channels",channels);
        return state;
    }

    public java.util.Map<String,Object> controlLiveCycle(java.util.Map<String,Object> args) {
        String action = com.sergey.pisarev.util.MiniJson.string(args,"action","");
        if (!java.util.Set.of("pause","play","seek").contains(action))
            throw new IllegalArgumentException("action: pause, play или seek");
        if (this.simulationContext == null) throw new IllegalStateException("3D ещё загружается");
        if (action.equals("pause")) {
            this.cyclePlaying=false; if (this.cycleAnimation!=null) this.cycleAnimation.stop();
            this.updateCycleControls(); return this.liveState();
        }
        if (!this.isVerticalMachine()) throw new IllegalStateException("Точное управление доступно для карусельного цикла");
        if (!this.isCycleMode()) this.onViewCycle();
        if (action.equals("play")) { if (!this.cyclePlaying) this.toggleCyclePlayback(); return this.liveState(); }
        double seconds=com.sergey.pisarev.util.MiniJson.number(args,"seconds",0);
        int side=(int)com.sergey.pisarev.util.MiniJson.number(args,"setupSide",Math.max(1,this.displayedSetupSide()));
        if (!Double.isFinite(seconds)||seconds<0||side<1||side>2||side==2&&!this.hasSetupSequence())
            throw new IllegalArgumentException("Неверное время или сторона");
        this.cyclePlaying=false; if (this.cycleAnimation!=null) this.cycleAnimation.stop();
        if (this.hasSetupSequence() && side!=this.displayedSetupSide()) {
            this.selectCycleSetup(side==1?this.completeSimulationContext.getPrecedingSetup():this.completeSimulationContext);
            this.restartCyclePreviewFromBeginning();
            this.cyclePlaying=false; if (this.cycleAnimation!=null) this.cycleAnimation.stop();
        }
        this.cancelTurnover();
        var moves=this.cycleMoves(); this.ensureCycleDistanceCache(moves);
        if (args.containsKey("sourceLine")) {
            int line=(int)com.sergey.pisarev.util.MiniJson.number(args,"sourceLine",0);
            double fraction=com.sergey.pisarev.util.MiniJson.number(args,"fraction",0);
            if (!Double.isFinite(fraction)||fraction<0||fraction>1) throw new IllegalArgumentException("fraction: 0..1");
            double start=Double.POSITIVE_INFINITY,end=0;
            for (var channel:this.verticalCyclePath.strokes()) for(var stroke:channel) if(stroke.contour().sourceLine()==line) {
                start=Math.min(start,stroke.startSeconds());end=Math.max(end,stroke.endSeconds());
            }
            if (!Double.isFinite(start)) throw new IllegalArgumentException("В строке нет движения");
            seconds=start+(end-start)*fraction;
        }
        this.cycleDistanceProgress=Math.min(seconds*CYCLE_BASE_FEED_MM_PER_SEC,this.cycleTotalDistance);
        this.cycleProgress=this.cycleProgressFromDistance(this.cycleDistanceProgress);
        int index=this.activeCycleMoveIndex(moves),frame=this.cycleMeshFrameForProgress();
        if(this.cycleMeshBuildRunning) this.queuePendingCycleMeshBuild(index,frame,moves);
        else this.scheduleCycleMeshBuild(index,frame,moves);
        this.lastCycleNanos=0;
        this.updateCycleControls(); this.refreshOverlaysNow();
        return this.liveState();
    }

    public int displayedSetupSide() {
        return !this.hasSetupSequence() ? 0 : this.simulationContext == this.completeSimulationContext ? 2 : 1;
    }

    public void stopPlayback() {
        this.cyclePlaying = false;
        if (this.cycleAnimation != null) this.cycleAnimation.stop();
        this.cancelTurnover();
        this.cycleMeshGeneration++;
        this.profileLoadGeneration++;
        this.meshBuildGeneration++;
        if (this.runningMeshTask != null) this.runningMeshTask.cancel(true);
        this.updateCycleControls();
    }

    private void cancelTurnover() {
        this.turnoverPreparing = false;
        this.turnoverProgress = -1;
        this.secondSetupInitialMesh = null;
        this.turnoverRotation.setAngle(0);
        for (var node : this.modelRoot.getChildren()) node.getTransforms().remove(this.turnoverRotation);
    }

    private boolean restoreCompleteResult(ViewMode next) {
        if (!this.isCycleMode()) return false;
        this.setCycleVisible(false);
        this.viewMode = next;
        this.machiningMoveIndex = -1;
        this.setData(this.completeSimulationContext, this.darkTheme, this.sourceLineConsumer, () -> {
            switch (next) {
                case HALF_CUT -> this.onViewHalfCut();
                case SIDE_GRAPH -> this.onViewSideGraph();
                default -> this.onView3d();
            }
        });
        return true;
    }

    private void selectCycleSetup(SimulationContext setup) {
        this.cancelTurnover();
        this.profileLoadGeneration++;
        this.meshBuildGeneration++;
        if (this.runningMeshTask != null) this.runningMeshTask.cancel(true);
        this.simulationContext = setup;
        this.machiningMoveIndex = -1;
        this.simulationSelectedSourceLine = -1;
        this.clearCyclePathCache();
        this.cycleDistanceMoves = List.of();
        this.cycleDistanceStops = new double[0];
        this.cycleTotalDistance = 0;
        this.cycleWorkpieceSpinDeg = 0;
        this.displayMoves = this.buildDisplayMoves();
        var stock = setup.getWorkpiece();
        this.cachedFinishedProfile = List.of(new double[]{stock.getZMin(), stock.getStockRadiusMm()},
                new double[]{stock.getZMax(), stock.getStockRadiusMm()});
        this.updatePartBounds(stock.getStockRadiusMm(), stock.getZMin(), stock.getZMax());
        this.refreshMissingToolNotice();
        this.refreshCycleFpsLabel();
        this.rebuildOverlayMovesCache();
        this.buildToolpath();
        this.syncToolpathOverlayVisibility();
        this.updateStatus();
    }

    /** Build the exact completed first side and the same material in setup-two coordinates. */
    private void prepareTurnover() {
        if (!this.hasSetupSequence() || this.displayedSetupSide() != 1
                || this.turnoverPreparing || this.turnoverProgress >= 0) return;
        this.turnoverPreparing = true;
        this.stopCycleSpindle(true);
        final int generation = ++this.cycleMeshGeneration;
        this.cycleMeshBuildRunning = false;
        this.pendingCycleMeshIndex = Integer.MIN_VALUE;
        this.pendingCycleMeshFrame = Integer.MIN_VALUE;
        this.pendingCycleMeshMoves = List.of();
        this.modelRoot.getChildren().removeIf(node -> CYCLE_TOOL_TAG.equals(node.getUserData())
                || CYCLE_CUT_PREVIEW_TAG.equals(node.getUserData())
                || TRAJECTORY_3D_TAG.equals(node.getUserData()) || TRAJECTORY_POINT_3D_TAG.equals(node.getUserData()));
        this.cycleTool3d = null;
        this.clearCycleCutPreviewReferences();
        this.gcodeOverlay.clear();
        this.updateStatus();
        final SimulationContext first = this.simulationContext, second = this.completeSimulationContext;
        var task = new javafx.concurrent.Task<TriangleMesh[]>() {
            @Override protected TriangleMesh[] call() {
                var profile = com.sergey.pisarev.service.AxialCycleStock.create(first).sectionAt(Double.POSITIVE_INFINITY);
                return new TriangleMesh[]{StockRemovalMeshBuilder.buildFinalCycleTriangleMesh(profile),
                        StockRemovalMeshBuilder.buildFinalCycleTriangleMesh(
                                com.sergey.pisarev.service.AxialCycleStock.turnOver(profile, second.getTurnoverZSum()))};
            }
        };
        task.setOnSucceeded(event -> {
            if (!this.isCycleMode() || generation != this.cycleMeshGeneration || !this.turnoverPreparing) return;
            var mesh = task.getValue();
            this.replaceCycleMaterial(mesh[0]);
            this.secondSetupInitialMesh = mesh[1];
            this.turnoverPreparing = false;
            this.turnoverProgress = 0;
            this.lastCycleNanos = 0;
            this.applyTurnoverPose();
            this.updateStatus();
        });
        task.setOnFailed(event -> {
            if (generation != this.cycleMeshGeneration) return;
            this.stopPlayback();
            this.showMeshError(I18n.format("sim.turnover.failed", task.getException().getMessage()));
        });
        var worker = new Thread(task, "intraspect-turnover"); worker.setDaemon(true); worker.start();
    }

    private void replaceCycleMaterial(TriangleMesh mesh) {
        var finished = StockRemovalMeshBuilder.createFinishedMeshView(mesh, this.partDiffuseColor());
        if (!this.isRenderableNode(finished)) throw new IllegalStateException(I18n.text("sim.error.nostock"));
        this.modelRoot.getChildren().removeIf(node -> FINISHED_PART_TAG.equals(node.getUserData())
                || STOCK_BLANK_TAG.equals(node.getUserData()));
        finished.setUserData(FINISHED_PART_TAG);
        this.applyWorkpieceSpin(finished);
        this.modelRoot.getChildren().add(finished);
        if (this.verticalCyclePath != null) this.cullCycleBackFaces();
    }

    private void applyTurnoverPose() {
        double t = Math.max(0, Math.min(1, this.turnoverProgress));
        this.turnoverRotation.setPivotY(this.completeSimulationContext.getTurnoverZSum() / 2);
        this.turnoverRotation.setAngle(180 * t * t * (3 - 2 * t));
        for (var node : this.modelRoot.getChildren()) {
            if (FINISHED_PART_TAG.equals(node.getUserData()) || STOCK_BLANK_TAG.equals(node.getUserData())) {
                node.setRotate(0);
                if (!node.getTransforms().contains(this.turnoverRotation)) node.getTransforms().add(this.turnoverRotation);
            }
        }
    }

    private void advanceTurnover(double seconds) {
        if (!this.cyclePlaying || this.turnoverPreparing || this.turnoverProgress < 0) return;
        this.turnoverProgress = Math.min(1, this.turnoverProgress + seconds / TURNOVER_SECONDS);
        this.applyTurnoverPose();
        if (this.turnoverProgress < 1) return;
        this.finishTurnover();
    }

    private void finishTurnover() {
        TriangleMesh initial = this.secondSetupInitialMesh;
        this.selectCycleSetup(this.completeSimulationContext);
        this.restartCyclePreviewFromBeginning(initial);
        this.lastCycleNanos = 0;
        this.updateStatus();
    }

    private void toggleCyclePlayback() {
        if (this.turnoverPreparing || this.turnoverProgress >= 0) {
            this.cyclePlaying = !this.cyclePlaying;
            this.lastCycleNanos = 0;
            this.ensureCycleAnimation();
            if (this.cyclePlaying) this.cycleAnimation.start(); else this.cycleAnimation.stop();
            this.updateCycleControls();
            return;
        }
        List<GCodeMoveData> pathMoves = this.cycleMoves();
        if (pathMoves.isEmpty()) {
            return;
        }
        double end = pathMoves.size();
        this.ensureCycleDistanceCache(pathMoves);
        if (this.cycleProgress >= end || this.cycleDistanceProgress >= this.cycleTotalDistance) {
            if (this.hasSetupSequence()) {
                this.selectCycleSetup(this.completeSimulationContext.getPrecedingSetup());
                this.restartCyclePreviewFromBeginning();
                this.cyclePlaying = false;
                this.toggleCyclePlayback();
                return;
            }
            this.cycleProgress = 0.0;
            this.cycleDistanceProgress = 0.0;
            this.cachedCycleProfileIndex = Integer.MIN_VALUE;
            this.cachedCycleProfile = List.of();
            this.cycleProfileCache.clear();
            this.renderedCycleMeshIndex = Integer.MIN_VALUE;
            this.renderedCycleMeshFrame = Integer.MIN_VALUE;
            this.lastCycleMeshRequestNanos = 0L;
            this.cycleMeshGeneration++;
            this.cycleMeshBuildRunning = false;
            this.pendingCycleMeshIndex = Integer.MIN_VALUE;
            this.pendingCycleMeshFrame = Integer.MIN_VALUE;
            this.pendingCycleMeshMoves = List.of();
            this.prebuiltCycleProfiles.clear();
            // Чистим предрассчитанные меши
            for (MeshView mv : this.prebuiltCycleMeshViews) {
                this.modelRoot.getChildren().remove(mv);
            }
            this.prebuiltCycleMeshViews.clear();
            this.cycleAllMeshesPrebuilt = false;
        }

        this.lastCycleNanos = 0L;
        boolean startPlayback = !this.cyclePlaying;
        this.cyclePlaying = startPlayback;
        this.ensureCycleAnimation();
        if (this.cyclePlaying) {
            this.resetCycleFpsStats();
            this.primeCycleMeshAhead(pathMoves);
            this.applyWorkpieceSpinToScene();
            this.cycleAnimation.start();
        } else {
            this.cycleAnimation.stop();
            this.stopCycleSpindle(false);
        }
        this.updateCycleControls();
        this.scheduleCycleCanvasRedraw();
    }

    private void primeCycleMeshAhead(List<GCodeMoveData> pathMoves) {
        if (pathMoves == null || pathMoves.isEmpty()) {
            return;
        }
        this.ensureCycleDistanceCache(pathMoves);
        int meshFrame = this.cycleTargetMeshFrame(this.cycleMeshFrameForProgress());
        int meshActiveIndex = this.activeCycleMoveIndexForMeshFrame(pathMoves, meshFrame);
        if (this.cycleMeshBuildRunning) {
            this.queuePendingCycleMeshBuild(meshActiveIndex, meshFrame, pathMoves);
        } else {
            this.scheduleCycleMeshBuild(meshActiveIndex, meshFrame, pathMoves);
        }
    }

    private void advanceCycleSpeed() {
        if (this.cycleSpeed < 0.75) {
            this.cycleSpeed = 1.0;
        } else if (this.cycleSpeed < 1.5) {
            this.cycleSpeed = 2.0;
        } else if (this.cycleSpeed < 3.0) {
            this.cycleSpeed = 4.0;
        } else {
            this.cycleSpeed = 0.5;
        }
        this.updateCycleControls();
        this.scheduleCycleCanvasRedraw();
    }

    private void seekCycleSeconds(double seconds) {
        if (this.turnoverPreparing) return;
        if (this.turnoverProgress >= 0) {
            this.turnoverProgress = Math.max(0, Math.min(1, this.turnoverProgress + seconds / TURNOVER_SECONDS));
            this.applyTurnoverPose();
            if (this.turnoverProgress >= 1) this.finishTurnover();
            return;
        }
        List<GCodeMoveData> pathMoves = this.cycleMoves();
        if (pathMoves.isEmpty() || !Double.isFinite(seconds) || Math.abs(seconds) < 0.001) {
            return;
        }
        this.ensureCycleDistanceCache(pathMoves);
        double end = pathMoves.size();
        if (this.cycleTotalDistance > 0.01) {
            double speedFactor = Math.max(0.25, Math.min(6.0, this.cycleSpeed));
            double deltaDistance = seconds * speedFactor * CYCLE_BASE_FEED_MM_PER_SEC;
            if (deltaDistance < -this.cycleDistanceProgress && this.displayedSetupSide() == 2) {
                this.selectCycleSetup(this.completeSimulationContext.getPrecedingSetup());
                this.restartCyclePreviewFromBeginning();
                pathMoves = this.cycleMoves();
                this.ensureCycleDistanceCache(pathMoves);
                this.cycleDistanceProgress = this.cycleTotalDistance;
            }
            this.cycleDistanceProgress = Math.max(
                    0.0,
                    Math.min(this.cycleTotalDistance, this.cycleDistanceProgress + deltaDistance));
            this.cycleProgress = this.cycleProgressFromDistance(this.cycleDistanceProgress);
        } else {
            this.cycleProgress = Math.max(0.0, Math.min(end, this.cycleProgress + seconds * this.cycleSpeed * 4.0));
            this.cycleDistanceProgress = 0.0;
        }
        this.lastCycleNanos = 0L;
        if (this.cycleDistanceProgress >= this.cycleTotalDistance && this.displayedSetupSide() == 1) {
            this.prepareTurnover();
            return;
        }
        int activeIndex = this.activeCycleMoveIndex(pathMoves);
        int meshFrame = this.cycleMeshFrameForProgress();
        if (this.cycleMeshBuildRunning) {
            this.queuePendingCycleMeshBuild(activeIndex, meshFrame, pathMoves);
        } else {
            this.scheduleCycleMeshBuild(activeIndex, meshFrame, pathMoves);
        }
        this.updateCycleTool3d(pathMoves, activeIndex);
        this.updateCycleControls();
        this.redrawCycleCanvas();
        this.refreshOverlaysNow();
    }

    private int cycleMeshFrameForProgress() {
        if (this.cycleTotalDistance > 0.01) {
            double frame = this.cycleDistanceProgress / CYCLE_SEGMENT_MM_SMOOTH;
            return Math.max(0, (int) (this.cycleDistanceProgress >= this.cycleTotalDistance ? Math.ceil(frame) : Math.floor(frame)));
        }
        return Math.max(0, (int) Math.floor(Math.max(0.0, this.cycleProgress) * 16.0));
    }

    private void ensureCycleAnimation() {
        if (this.cycleAnimation != null) {
            return;
        }
        this.cycleAnimation = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isCycleMode() || !cyclePlaying) {
                    stop();
                    return;
                }
                if (lastCycleNanos == 0L) {
                    lastCycleNanos = now;
                    return;
                }
                double elapsed = (now - lastCycleNanos) / 1_000_000_000.0;
                double dt = Math.min(0.08, elapsed);
                lastCycleNanos = now;
                updateCycleFps(now, elapsed);
                if (turnoverPreparing) return;
                if (turnoverProgress >= 0) { advanceTurnover(dt); return; }
                if (!isVerticalMachine()) advanceCycleSpindle(dt);
                List<GCodeMoveData> pathMoves = cycleMoves();
                if (pathMoves.isEmpty()) {
                    cyclePlaying = false;
                    stop();
                    stopCycleSpindle(false);
                    updateCycleControls();
                    return;
                }
                double end = pathMoves.size();
                ensureCycleDistanceCache(pathMoves);
                double advance = 0.0;
                double previousDistance = cycleDistanceProgress;
                if (cycleTotalDistance > 0.01) {
                    double speedFactor = Math.max(0.25, Math.min(6.0, cycleSpeed));
                    double maxAdvance = CYCLE_ADVANCE_MM_PER_FRAME_SMOOTH
                            * Math.max(1.0, Math.sqrt(speedFactor) * 1.6);
                    advance = dt * speedFactor * CYCLE_BASE_FEED_MM_PER_SEC;
                    if (!isVerticalMachine()) advance = Math.min(advance, maxAdvance);
                    double next = Math.min(cycleTotalDistance, cycleDistanceProgress + advance);
                    if (isVerticalMachine() && renderedCycleMeshFrame >= 0) {
                        // Slow simulated time if rendering falls behind; never skip a large cut to catch up.
                        double rendered = renderedCycleMeshFrame * CYCLE_SEGMENT_MM_SMOOTH;
                        next = Math.min(next, Math.max(cycleDistanceProgress,
                                rendered + CYCLE_BASE_FEED_MM_PER_SEC * .08 * speedFactor));
                    }
                    cycleDistanceProgress = next;
                    cycleProgress = cycleProgressFromDistance(cycleDistanceProgress);
                } else {
                    advance = dt * cycleSpeed * 4.0;
                    cycleProgress = Math.min(end, cycleProgress + advance);
                }
                if (isVerticalMachine()) advanceCycleSpindle(
                        (cycleDistanceProgress-previousDistance)/(CYCLE_BASE_FEED_MM_PER_SEC*Math.max(.25,cycleSpeed)));
                if (cycleProgress >= end || (cycleTotalDistance > 0.01 && cycleDistanceProgress >= cycleTotalDistance)) {
                    if (displayedSetupSide() == 1) {
                        if (isVerticalMachine() && renderedCycleMeshFrame < cycleMeshFrameForProgress()) {
                            redrawCycleCanvas();return;
                        }
                        prepareTurnover();return;
                    }
                    cyclePlaying = false;
                    stop();
                    stopCycleSpindle(false);
                }
                updateCycleControls();
                redrawCycleCanvas();
            }
        };
    }

    private void resetCycleFpsStats() {
        this.cycleFpsEma = 0.0;
        this.cycleLastFpsLabelNanos = 0L;
        this.cycleLastFrameDtMs = 0.0;
        this.cycleFrameCounter = 0;
        this.cycleLowFpsStreak = 0;
        this.cycleHighFpsStreak = 0;
        if (this.cycleFpsLabel != null) {
            this.cycleFpsLabel.setText(this.formatCycleFpsLabel(0.0));
        }
    }

    private void refreshCycleFpsLabel() {
        if (this.cycleFpsLabel != null && this.cycleFpsLabel.isVisible()) {
            this.cycleFpsLabel.setText(this.formatCycleFpsLabel(this.cycleFpsEma));
        }
    }

    private void updateCycleFps(long now, double dtSeconds) {
        if (dtSeconds <= 0.0) {
            return;
        }
        this.cycleFrameCounter++;
        this.cycleLastFrameDtMs = dtSeconds * 1000.0;
        double instantFps = 1.0 / dtSeconds;
        this.cycleFpsEma = this.cycleFpsEma <= 0.0
                ? instantFps
                : this.cycleFpsEma * 0.88 + instantFps * 0.12;
        this.updateCycleAutoQuality();
        if (this.cycleFpsLabel != null
                && (this.cycleLastFpsLabelNanos == 0L || now - this.cycleLastFpsLabelNanos > 250_000_000L)) {
            this.cycleFpsLabel.setText(this.formatCycleFpsLabel(this.cycleFpsEma));
            this.cycleLastFpsLabelNanos = now;
        }
    }

    /**
     * Авто-оптимизация под слабые компьютеры: устойчиво низкий FPS (< 55) понижает
     * качество (реже перестраиваем меш цикла), устойчиво высокий — возвращает обратно.
     */
    private void updateCycleAutoQuality() {
        if (this.cycleFpsEma <= 0.0 || this.cycleFrameCounter < 30) {
            return;
        }
        if (this.cycleFpsEma < CYCLE_AUTO_QUALITY_LOW_FPS) {
            this.cycleHighFpsStreak = 0;
            this.cycleLowFpsStreak++;
            if (this.cycleLowFpsStreak >= CYCLE_AUTO_QUALITY_DEGRADE_FRAMES
                    && this.cycleAutoQualityLevel < CYCLE_AUTO_QUALITY_MAX_LEVEL) {
                this.cycleAutoQualityLevel++;
                this.cycleLowFpsStreak = 0;
                // Уровень задаёт и число срезов, поэтому кэш пакетов надо собрать заново.
                this.resetCyclePreviewMeshes();
            }
        } else if (this.cycleFpsEma > CYCLE_AUTO_QUALITY_HIGH_FPS) {
            this.cycleLowFpsStreak = 0;
            this.cycleHighFpsStreak++;
            if (this.cycleHighFpsStreak >= CYCLE_AUTO_QUALITY_RESTORE_FRAMES
                    && this.cycleAutoQualityLevel > 0) {
                this.cycleAutoQualityLevel--;
                this.cycleHighFpsStreak = 0;
                this.resetCyclePreviewMeshes();
            }
        } else {
            this.cycleLowFpsStreak = 0;
            this.cycleHighFpsStreak = 0;
        }
    }

    /**
     * Срезов по кругу в предпросмотре цикла, по уровню авто-качества.
     *
     * <p>На слабой машине уровень поднимается сам, и модель становится грубее по
     * кругу, а не реже обновляется: видеокарта тогда получает вдвое меньше вершин,
     * и картинка идёт ровнее, чем при рывках раз в полсекунды.
     */
    private int cyclePreviewSlices() {
        return switch (this.cycleAutoQualityLevel) {
            case 0 -> com.sergey.pisarev.service.CyclePreviewMeshCache.DEFAULT_SLICES;
            case 1 -> 72;
            default -> 48;
        };
    }

    /** Минимальный интервал между перестройками меша цикла с учётом авто-качества. */
    private long cycleMeshMinIntervalNanos() {
        // Tool motion/camera keep their display cadence. Material needs at most
        // 20 updates/s; rebuilding it on every pulse stalls Prism's render thread.
        if (this.isVerticalMachine()) return 50_000_000L;
        return switch (this.cycleAutoQualityLevel) {
            case 0 -> CYCLE_MESH_MIN_INTERVAL_NANOS_SMOOTH;
            case 1 -> CYCLE_MESH_MIN_INTERVAL_NANOS_SMOOTH * 5L;
            default -> CYCLE_MESH_MIN_INTERVAL_NANOS_SMOOTH * 12L;
        };
    }

    /** Допустимое отставание меша от прогресса (в кадрах меша) с учётом авто-качества. */
    private int cycleMeshMaxGapFrames() {
        return switch (this.cycleAutoQualityLevel) {
            case 0 -> CYCLE_MAX_MESH_GAP_SMOOTH;
            case 1 -> CYCLE_MAX_MESH_GAP_SMOOTH * 3;
            default -> CYCLE_MAX_MESH_GAP_SMOOTH * 6;
        };
    }

    private String formatCycleFpsLabel(double fps) {
        // Только частота кадров: оценка времени обработки из этой подписи убрана.
        return fps > 0.0 ? String.format(Locale.US, "FPS %.0f", fps) : "FPS --";
    }

    private String cycleTimeSignature(List<GCodeMoveData> timingMoves) {
        if (this.simulationContext == null) {
            return "no-context";
        }
        int count = timingMoves != null ? timingMoves.size() : 0;
        GCodeMoveData first = count > 0 ? timingMoves.get(0) : null;
        GCodeMoveData last = count > 0 ? timingMoves.get(count - 1) : null;
        String programText = this.simulationContext.getProgramText();
        int programHash = programText != null ? programText.hashCode() : 0;
        return programHash
                + ":" + this.simulationContext.getFeedOverridePercent()
                + ":" + this.simulationContext.getSpindleOverridePercent()
                + ":" + count
                + ":" + this.moveSignature(first)
                + ":" + this.moveSignature(last);
    }

    private String moveSignature(GCodeMoveData move) {
        if (move == null) {
            return "-";
        }
        return move.sourceLine()
                + "/" + Double.doubleToLongBits(move.startX())
                + "/" + Double.doubleToLongBits(move.startZ())
                + "/" + Double.doubleToLongBits(move.endX())
                + "/" + Double.doubleToLongBits(move.endZ())
                + "/" + move.rapid();
    }

    private double cycleMaxDistanceAllowedByMesh() {
        if (this.cycleTotalDistance <= 0.01) {
            return Double.POSITIVE_INFINITY;
        }
        int anchorFrame = Math.max(this.renderedCycleMeshFrame, this.pendingCycleMeshFrame);
        if (anchorFrame == Integer.MIN_VALUE) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.min(
                this.cycleTotalDistance,
                Math.max(0.0, anchorFrame + CYCLE_MAX_VISUAL_MESH_LEAD_FRAMES) * CYCLE_SEGMENT_MM_SMOOTH);
    }

    private double cycleMeshFollowSpeedFactor() {
        int anchorFrame = Math.max(this.renderedCycleMeshFrame, this.pendingCycleMeshFrame);
        if (anchorFrame == Integer.MIN_VALUE) {
            return 1.0;
        }
        int leadFrames = this.cycleMeshFrameForProgress() - anchorFrame;
        if (leadFrames <= CYCLE_MAX_MESH_GAP_SMOOTH) {
            return 1.0;
        }
        int softRange = Math.max(1, CYCLE_MAX_VISUAL_MESH_LEAD_FRAMES - CYCLE_MAX_MESH_GAP_SMOOTH);
        double t = Math.min(1.0, (leadFrames - CYCLE_MAX_MESH_GAP_SMOOTH) / (double) softRange);
        return 1.0 - t * 0.78;
    }

    private void advanceCycleSpindle(double dtSeconds) {
        if (!this.isCycleMode() || !this.cyclePlaying || dtSeconds <= 0.0) {
            return;
        }
        double speedFactor = Math.max(0.35, Math.min(2.5, Math.sqrt(Math.max(0.25, this.cycleSpeed))));
        double degreesPerSecond = CYCLE_SPINDLE_DEG_PER_SEC;
        if (this.verticalCyclePath != null && this.isVerticalMachine()) {
            degreesPerSecond = 0;
            int active = this.activeCycleMoveIndex(this.cachedCycleMoves);
            for (int channel = 0; channel < this.verticalCyclePath.channelCount(); channel++) {
                int index = this.verticalCyclePath.indexAt(channel, active);
                if (index >= 0 && index <= active && this.verticalCyclePath.steps().get(index).spindleCommanded()) {
                    degreesPerSecond = this.verticalCyclePath.steps().get(index).rpm() * 6;
                    break;
                }
            }
            speedFactor = this.cycleSpeed;
        }
        this.cycleWorkpieceSpinDeg = (this.cycleWorkpieceSpinDeg
                + degreesPerSecond * speedFactor * dtSeconds) % 360.0;
        this.applyWorkpieceSpinToScene();
    }

    private void stopCycleSpindle(boolean resetAngle) {
        if (resetAngle) {
            this.cycleWorkpieceSpinDeg = 0.0;
        }
        this.applyWorkpieceSpinToScene();
    }

    private void applyWorkpieceSpinToScene() {
        for (javafx.scene.Node node : this.modelRoot.getChildren()) {
            Object tag = node.getUserData();
            if (STOCK_BLANK_TAG.equals(tag) || FINISHED_PART_TAG.equals(tag)) {
                this.applyWorkpieceSpin(node);
            }
        }
    }

    private void applyWorkpieceSpin(javafx.scene.Node node) {
        if (node == null) {
            return;
        }
        node.setRotationAxis(javafx.scene.transform.Rotate.Y_AXIS);
        node.setRotate(this.isCycleMode() ? this.cycleWorkpieceSpinDeg : 0.0);
    }

    private void updateCycleControls() {
        if (this.cyclePlayPauseButton != null) {
            this.cyclePlayPauseButton.setText(this.cyclePlaying ? "⏸" : "▶");
        }
        if (this.cycleSpeedButton != null) {
            this.cycleSpeedButton.setText(String.format(Locale.US, "%.1fx", this.cycleSpeed).replace(".0", ""));
        }
    }

    private void restartCycleFromToolbar() {
        if (!this.isCycleMode()) {
            return;
        }
        if (this.cycleResetLoadingPending) {
            // Перезапуск уже идёт — повторные клики не запускают второй сброс.
            return;
        }
        try {
            this.cyclePlaying = false;
            if (this.cycleAnimation != null) {
                this.cycleAnimation.stop();
            }
            this.stopCycleSpindle(true);
            if (this.hasSetupSequence()) this.selectCycleSetup(this.completeSimulationContext.getPrecedingSetup());
            this.restartCyclePreviewFromBeginning();
            this.updateCycleControls();
            if (this.subScene != null) {
                this.subScene.requestFocus();
            }
        } catch (RuntimeException ex) {
            this.cyclePlaying = false;
            if (this.cycleAnimation != null) {
                this.cycleAnimation.stop();
            }
            this.stopCycleSpindle(true);
            this.cycleResetLoadingPending = false;
            this.hideLoadingOverlay();
            this.updateCycleControls();
            this.lastMeshError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            this.showMeshError(this.lastMeshError);
            this.restoreViewportLayering();
        }
    }

    private void restartCyclePreviewFromBeginning() {
        this.restartCyclePreviewFromBeginning(null);
    }

    private void restartCyclePreviewFromBeginning(TriangleMesh initialMesh) {
        this.cycleProgress = 0.0;
        this.cycleDistanceProgress = 0.0;
        this.lastCycleNanos = 0L;
        this.lastCycleMeshRequestNanos = 0L;
        this.cachedCycleProfileIndex = Integer.MIN_VALUE;
        this.renderedCycleMeshIndex = Integer.MIN_VALUE;
        this.renderedCycleMeshFrame = Integer.MIN_VALUE;
        this.cachedCycleProfile = List.of();
        this.cycleProfileCache.clear();
        this.pendingCycleMeshIndex = Integer.MIN_VALUE;
        this.pendingCycleMeshFrame = Integer.MIN_VALUE;
        this.pendingCycleMeshMoves = List.of();
        this.cycleMeshGeneration++;
        this.cycleMeshBuildRunning = false;
        this.cycleResetLoadingPending = false;
        this.clearCyclePathCache();
        List<GCodeMoveData> pathMoves = this.cycleMoves();
        this.prebuiltCycleProfiles.clear();
        for (MeshView mv : this.prebuiltCycleMeshViews) {
            this.modelRoot.getChildren().remove(mv);
        }
        this.prebuiltCycleMeshViews.clear();
        this.cycleAllMeshesPrebuilt = false;
        this.modelRoot.getChildren().removeIf(node -> {
            Object tag = node.getUserData();
            return FINISHED_PART_TAG.equals(tag)
                    || STOCK_BLANK_TAG.equals(tag)
                    || CYCLE_TOOL_TAG.equals(tag)
                    || CYCLE_CUT_PREVIEW_TAG.equals(tag);
        });
        this.cycleTool3d = null;
        this.cycleToolSignature = "";
        this.clearCycleCutPreviewReferences();
        // ВАЖНО: заготовку здесь НЕ собираем синхронно — полный revolve двухметровой
        // детали (и вызов OCCT) на FX-потоке замораживал окно («не отвечает») и мог
        // ронять его. Кадр 0 цикла отрисует полную заготовку фоновой сборкой ниже,
        // а на время пересчёта показываем оверлей загрузки.
        if (!pathMoves.isEmpty()) {
            this.ensureCycleDistanceCache(pathMoves);
            this.updateCycleTool3d(pathMoves, 0);
            if (initialMesh != null) {
                this.replaceCycleMaterial(initialMesh);
                this.renderedCycleMeshFrame = 0;
                this.renderedCycleMeshIndex = -1;
            } else {
                this.cycleResetLoadingPending = true;
                this.showLoadingOverlay(I18n.text("sim.033"), I18n.text("sim.034"));
                this.ensureLoadingOnTop();
                this.scheduleCycleMeshBuild(-1, 0, pathMoves);
            }
        } else {
            this.hideLoadingOverlay();
        }
        this.restoreViewportLayering();
        this.refreshMeasureOverlay();
        this.scheduleCycleCanvasRedraw();
    }

    private void resetCycleSafely() {
        try {
            this.resetCycle();
        } catch (RuntimeException ex) {
            this.cyclePlaying = false;
            if (this.cycleAnimation != null) {
                this.cycleAnimation.stop();
            }
            this.stopCycleSpindle(true);
            this.cycleResetLoadingPending = false;
            this.hideLoadingOverlay();
            this.updateCycleControls();
            this.showExternalError(I18n.text("sim.035")
                    + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            this.restoreViewportLayering();
        }
    }

    /** Сброс цикла в начальное состояние (новый проход). */
    private void resetCycle() {
        if (this.hasSetupSequence()) this.selectCycleSetup(this.completeSimulationContext.getPrecedingSetup());
        this.cyclePlaying = false;
        if (this.cycleAnimation != null) {
            this.cycleAnimation.stop();
        }
        if (this.isCycleMode()) {
            this.cycleResetLoadingPending = true;
            this.showLoadingOverlay(I18n.text("sim.036"), I18n.text("sim.037"));
            this.ensureLoadingOnTop();
        }
        this.cycleProgress = 0.0;
        this.cycleDistanceProgress = 0.0;
        this.stopCycleSpindle(true);
        this.cachedCycleProfileIndex = Integer.MIN_VALUE;
        this.renderedCycleMeshIndex = Integer.MIN_VALUE;
        this.renderedCycleMeshFrame = Integer.MIN_VALUE;
        this.lastCycleNanos = 0L;
        this.lastCycleMeshRequestNanos = 0L;
        this.cycleMeshGeneration++;
        this.cycleMeshBuildRunning = false;
        this.pendingCycleMeshIndex = Integer.MIN_VALUE;
        this.pendingCycleMeshFrame = Integer.MIN_VALUE;
        this.pendingCycleMeshMoves = List.of();
        this.cachedCycleProfile = List.of();
        this.cycleProfileCache.clear();
        this.cycleDistanceMoves = List.of();
        this.cycleDistanceStops = new double[0];
        this.cycleTotalDistance = 0.0;
        this.cycleTool3d = null;
        this.cycleToolSignature = "";
        this.clearCyclePathCache();
        this.prebuiltCycleProfiles.clear();
        // Чистим предрассчитанные меши
        for (MeshView mv : this.prebuiltCycleMeshViews) {
            this.modelRoot.getChildren().remove(mv);
        }
        this.prebuiltCycleMeshViews.clear();
        this.cycleAllMeshesPrebuilt = false;
        this.updateCycleControls();
        // Чистим сцену
        this.modelRoot.getChildren().removeIf(node -> {
            Object tag = node.getUserData();
            return FINISHED_PART_TAG.equals(tag)
                    || STOCK_BLANK_TAG.equals(tag)
                    || CYCLE_TOOL_TAG.equals(tag)
                    || CYCLE_CUT_PREVIEW_TAG.equals(tag);
        });
        // Исходную заготовку синхронно не собираем (полный revolve + OCCT на FX-потоке
        // замораживал окно) — её отрисует фоновая сборка кадра 0 ниже, оверлей уже показан.
        List<GCodeMoveData> pathMoves = this.cycleMoves();
        if (!pathMoves.isEmpty()) {
            this.ensureCycleDistanceCache(pathMoves);
            this.updateCycleTool3d(pathMoves, 0);
            this.scheduleCycleMeshBuild(-1, 0, pathMoves);
        } else {
            this.finishCycleRestartLoading();
        }
        if (!this.is2dCanvasMode()) {
            this.fitToContent();
            this.view3dFitted = true;
            this.lastFittedContext = this.simulationContext;
            this.lastFitHadFinishedPart = this.hasFinishedPartNode();
            this.forceViewportFitAfterLayout();
        }
        this.syncToolpathOverlayVisibility();
        this.refreshOverlaysNow();
        this.restoreViewportLayering();
        this.scheduleCycleCanvasRedraw();
    }

    private void finishCycleRestartLoading() {
        if (!this.cycleResetLoadingPending) {
            return;
        }
        this.cycleResetLoadingPending = false;
        this.hideLoadingOverlayAfterRenderPulse();
    }

    private void scheduleCycleCanvasRedraw() {
        if (!this.isCycleMode() || this.redrawCycleQueued) {
            return;
        }
        this.redrawCycleQueued = true;
        javafx.application.Platform.runLater(() -> {
            this.redrawCycleQueued = false;
            this.redrawCycleCanvas();
        });
    }

    private void redrawCycleCanvas() {
        if (!this.isCycleMode() || this.turnoverPreparing || this.turnoverProgress >= 0) {
            return;
        }
        List<GCodeMoveData> pathMoves = this.cycleMoves();
        int activeIndex = this.activeCycleMoveIndex(pathMoves);

        this.ensureCycleDistanceCache(pathMoves);
        int visibleMeshFrame = this.cycleMeshFrameForProgress();
        int meshFrame = this.cycleTargetMeshFrame(visibleMeshFrame);
        int meshActiveIndex = this.activeCycleMoveIndexForMeshFrame(pathMoves, meshFrame);
        boolean criticalContact = this.isCycleCriticalContactMove(pathMoves, activeIndex);
        boolean frameChanged = meshFrame != this.renderedCycleMeshFrame;
        boolean timeThreshold = System.nanoTime() - this.lastCycleMeshRequestNanos
                > this.cycleMeshMinIntervalNanos();
        boolean pendingSameFrame = this.cycleMeshBuildRunning && this.pendingCycleMeshFrame == meshFrame;
        int meshAnchorFrame = Math.max(this.renderedCycleMeshFrame, this.pendingCycleMeshFrame);
        int maxMeshGap = this.cycleMeshMaxGapFrames();
        int meshGap = meshAnchorFrame == Integer.MIN_VALUE
                ? maxMeshGap + 1
                : visibleMeshFrame - meshAnchorFrame;
        boolean shouldBuild = frameChanged
                && !pendingSameFrame
                && (!this.cyclePlaying || timeThreshold
                    || (!this.isVerticalMachine() && (criticalContact || meshGap > maxMeshGap)));
        if (shouldBuild) {
            if (this.cycleMeshBuildRunning) {
                this.queuePendingCycleMeshBuild(meshActiveIndex, meshFrame, pathMoves);
            } else {
                this.scheduleCycleMeshBuild(meshActiveIndex, meshFrame, pathMoves);
            }
        }
        this.updateCycleTool3d(pathMoves, activeIndex);
    }

    private boolean isCycleCriticalContactMove(List<GCodeMoveData> pathMoves, int activeIndex) {
        if (pathMoves == null || activeIndex < 0 || activeIndex >= pathMoves.size()) {
            return false;
        }
        GCodeMoveData move = pathMoves.get(activeIndex);
        if (move == null || move.rapid()) {
            return false;
        }
        double radialDelta = Math.abs(LatheMeshBuilder.toRadius(move.endX()) - LatheMeshBuilder.toRadius(move.startX()));
        double zDelta = Math.abs(move.endZ() - move.startZ());
        return radialDelta >= 0.08 && (zDelta <= 0.05 || radialDelta >= zDelta * 0.45);
    }

    private int cycleTargetMeshFrame(int visibleFrame) {
        if (this.isVerticalMachine()) return visibleFrame;
        if (!this.cyclePlaying || this.cycleTotalDistance <= 0.01) {
            return visibleFrame;
        }
        double speedFactor = Math.max(0.25, Math.min(6.0, this.cycleSpeed));
        double buildMs = Double.isFinite(this.cycleLastMeshBuildMs)
                ? Math.max(0.0, this.cycleLastMeshBuildMs)
                : 0.0;
        double latencySeconds = Math.min(0.55, buildMs / 1000.0);
        double leadDistance = speedFactor * CYCLE_BASE_FEED_MM_PER_SEC * latencySeconds * 1.1;
        int lead = (int) Math.ceil(leadDistance / CYCLE_SEGMENT_MM_SMOOTH);
        int speedLead = (int) Math.ceil(CYCLE_MIN_VISUAL_MESH_LEAD_FRAMES * Math.max(1.0, Math.sqrt(speedFactor)));
        lead = Math.max(CYCLE_MIN_VISUAL_MESH_LEAD_FRAMES, Math.max(lead, speedLead));
        lead = Math.max(0, Math.min(CYCLE_MAX_VISUAL_MESH_LEAD_FRAMES, lead));
        int maxFrame = Math.max(visibleFrame, (int) Math.ceil(this.cycleTotalDistance / CYCLE_SEGMENT_MM_SMOOTH));
        return Math.min(maxFrame, visibleFrame + lead);
    }

    private int activeCycleMoveIndexForMeshFrame(List<GCodeMoveData> pathMoves, int meshFrame) {
        if (pathMoves == null || pathMoves.isEmpty()) {
            return -1;
        }
        if (this.cycleTotalDistance <= 0.01) {
            return Math.max(0, Math.min(pathMoves.size() - 1, (int) Math.floor(meshFrame / 16.0)));
        }
        double distance = Math.max(0.0, meshFrame * CYCLE_SEGMENT_MM_SMOOTH);
        double progress = this.cycleProgressFromDistance(distance);
        return Math.max(0, Math.min(pathMoves.size() - 1, (int) Math.floor(progress)));
    }

    private List<GCodeMoveData> cycleMeshMovesForFrame(List<GCodeMoveData> pathMoves, int meshFrame) {
        if (pathMoves == null || pathMoves.isEmpty()) {
            return List.of();
        }
        this.ensureCycleDistanceCache(pathMoves);
        if (this.verticalCyclePath != null && pathMoves == this.cachedCycleMoves)
            return this.verticalCyclePath.prefixAt(meshFrame * CYCLE_SEGMENT_MM_SMOOTH / CYCLE_BASE_FEED_MM_PER_SEC, false);
        double progress;
        if (this.cycleTotalDistance <= 0.01) {
            progress = Math.max(0.0, meshFrame / 16.0);
        } else {
            double distance = Math.max(0.0, meshFrame * CYCLE_SEGMENT_MM_SMOOTH);
            progress = this.cycleProgressFromDistance(distance);
        }
        return cycleMovesUpToProgress(pathMoves, progress);
    }

    private static List<GCodeMoveData> cycleMovesUpToProgress(List<GCodeMoveData> pathMoves, double progress) {
        if (pathMoves == null || pathMoves.isEmpty() || !Double.isFinite(progress) || progress <= 0.0) {
            return List.of();
        }
        if (progress >= pathMoves.size()) {
            return pathMoves;
        }
        int completeCount = Math.max(0, Math.min(pathMoves.size(), (int) Math.floor(progress)));
        double localT = Math.max(0.0, Math.min(1.0, progress - completeCount));
        ArrayList<GCodeMoveData> result = new ArrayList<>(completeCount + 1);
        if (completeCount > 0) {
            result.addAll(pathMoves.subList(0, completeCount));
        }
        if (completeCount < pathMoves.size() && localT > 1.0e-6) {
            GCodeMoveData move = pathMoves.get(completeCount);
            double endX = move.startX() + (move.endX() - move.startX()) * localT;
            double endZ = move.startZ() + (move.endZ() - move.startZ()) * localT;
            if (Math.hypot(endX - move.startX(), endZ - move.startZ()) > 1.0e-7) {
                result.add(new GCodeMoveData(
                        move.startX(),
                        move.startZ(),
                        endX,
                        endZ,
                        move.rapid(),
                        move.sourceLine(),
                        move.arcSegment(),
                        move.toolNumber(),
                        move.edgeNumber(),
                        false));
            }
        }
        return List.copyOf(result);
    }

    private List<double[]> interpolatedCycleProfile(int activeIndex, List<GCodeMoveData> pathMoves) {
        if (activeIndex < 0 || pathMoves == null || pathMoves.isEmpty()) {
            return this.stockProfileForCycle();
        }
        List<double[]> currentProfile = this.cycleProfileForIndex(activeIndex, pathMoves);
        if (currentProfile.size() < 2) {
            return this.stockProfileForCycle();
        }
        double localT = Math.max(0.0, Math.min(1.0, this.cycleProgress - Math.floor(this.cycleProgress)));
        if (localT >= 0.995) {
            return currentProfile;
        }
        List<double[]> prevProfile = this.cycleProfileForIndex(activeIndex - 1, pathMoves);
        if (prevProfile.size() < 2) {
            return currentProfile;
        }
        if (localT <= 0.005) {
            return prevProfile;
        }

        TreeSet<Double> zValues = new TreeSet<>();
        for (double[] point : prevProfile) {
            zValues.add(point[0]);
        }
        for (double[] point : currentProfile) {
            zValues.add(point[0]);
        }
        ArrayList<double[]> interpolated = new ArrayList<>();
        for (double z : zValues) {
            double rPrev = this.radiusAtProfileZ(prevProfile, z);
            double rCurrent = this.radiusAtProfileZ(currentProfile, z);
            double radius = rPrev + (rCurrent - rPrev) * localT;
            interpolated.add(new double[]{z, radius});
        }
        return interpolated;
    }

    private double radiusAtProfileZ(List<double[]> profile, double z) {
        if (profile == null || profile.isEmpty()) {
            return 0.0;
        }
        double[] first = profile.get(0);
        if (z <= first[0]) {
            return first[1];
        }
        for (int i = 1; i < profile.size(); i++) {
            double[] previous = profile.get(i - 1);
            double[] current = profile.get(i);
            if (z <= current[0]) {
                double span = current[0] - previous[0];
                if (Math.abs(span) < 1.0e-9) {
                    return Math.min(previous[1], current[1]);
                }
                double t = (z - previous[0]) / span;
                return previous[1] + (current[1] - previous[1]) * Math.max(0.0, Math.min(1.0, t));
            }
        }
        return profile.get(profile.size() - 1)[1];
    }

    private List<GCodeMoveData> cycleMoves() {
        if (this.cachedCycleMovesContext == this.simulationContext && !this.cachedCycleMoves.isEmpty()) {
            return this.cachedCycleMoves;
        }
        List<GCodeMoveData> graphMoves = this.graphToolpathMoves();
        List<GCodeMoveData> base = graphMoves.isEmpty() ? this.buildDisplayMoves() : graphMoves;
        if (base.isEmpty()) {
            this.cachedCycleMovesContext = this.simulationContext;
            this.cachedCycleMoves = List.of();
            this.cachedCycleCompMoves = List.of();
            return this.cachedCycleMoves;
        }
        if (this.isVerticalMachine()) {
            this.verticalCyclePath = new com.sergey.pisarev.service.LatheCyclePath(
                    this.simulationContext, base, CYCLE_SEGMENT_MM_SMOOTH);
            this.cachedCycleMovesContext = this.simulationContext;
            this.cachedCycleMoves = this.verticalCyclePath.steps().stream().map(s -> s.contour()).toList();
            this.cachedCycleCompMoves = this.verticalCyclePath.steps().stream().map(s -> s.centre()).toList();
            return this.cachedCycleMoves;
        }
        // Контурные ходы — для меша/среза, эквидистанта — для позиции инструмента.
        // Оба списка дробятся одинаково, чтобы индексы совпадали 1:1.
        List<GCodeMoveData> compensated = this.compensatedDisplayMoves(base);
        if (compensated.size() != base.size()) {
            compensated = base;
        }
        ArrayList<GCodeMoveData> result = new ArrayList<>();
        ArrayList<GCodeMoveData> compResult = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            GCodeMoveData move = base.get(i);
            GCodeMoveData comp = compensated.get(i);
            double dx = move.endX() - move.startX();
            double dz = move.endZ() - move.startZ();
            double length = Math.hypot(dx, dz);
            if (length <= CYCLE_SEGMENT_MM_SMOOTH) {
                result.add(move);
                compResult.add(comp);
                continue;
            }
            int parts = Math.max(2, (int) Math.ceil(length / CYCLE_SEGMENT_MM_SMOOTH));
            double prevX = move.startX();
            double prevZ = move.startZ();
            double compDx = comp.endX() - comp.startX();
            double compDz = comp.endZ() - comp.startZ();
            double compPrevX = comp.startX();
            double compPrevZ = comp.startZ();
            for (int p = 0; p < parts; p++) {
                double t = (double) (p + 1) / parts;
                double nextX = move.startX() + dx * t;
                double nextZ = move.startZ() + dz * t;
                result.add(new GCodeMoveData(
                        prevX, prevZ, nextX, nextZ,
                        move.rapid(), move.sourceLine(), move.arcSegment(), move.toolNumber(), move.edgeNumber(),
                        move.arcEnd() && p == parts - 1));
                prevX = nextX;
                prevZ = nextZ;
                double compNextX = comp.startX() + compDx * t;
                double compNextZ = comp.startZ() + compDz * t;
                compResult.add(new GCodeMoveData(
                        compPrevX, compPrevZ, compNextX, compNextZ,
                        comp.rapid(), comp.sourceLine(), comp.arcSegment(), comp.toolNumber(), comp.edgeNumber(),
                        comp.arcEnd() && p == parts - 1));
                compPrevX = compNextX;
                compPrevZ = compNextZ;
            }
        }
        this.cachedCycleMovesContext = this.simulationContext;
        this.cachedCycleMoves = List.copyOf(result);
        this.cachedCycleCompMoves = List.copyOf(compResult);
        return this.cachedCycleMoves;
    }

    /** Сбрасывает кэш пакетов: после смены качества срезы должны стать другими. */
    private void resetCyclePreviewMeshes() {
        this.verticalPreviewMeshes = null;
    }

    private void clearCyclePathCache() {
        this.verticalCyclePath = null;
        this.verticalCycleStock = null;
        this.verticalPreviewMeshes = null;
        this.cachedCycleMoves = List.of();
        this.cachedCycleCompMoves = List.of();
        this.cachedCycleMovesContext = null;
        synchronized (this.cycleEnvelopeLock) {
            this.cycleEnvelopeWork = null;
            this.cycleEnvelopeAppliedCount = 0;
            this.cycleEnvelopeSourceMoves = null;
            this.cycleCuttingEnvelopeCache = null;
            this.cycleCuttingEnvelopeContext = null;
        }
    }

    private void ensureCycleDistanceCache(List<GCodeMoveData> pathMoves) {
        if (pathMoves == null || pathMoves.isEmpty()) {
            this.cycleDistanceMoves = List.of();
            this.cycleDistanceStops = new double[0];
            this.cycleTotalDistance = 0.0;
            return;
        }
        if (pathMoves == this.cycleDistanceMoves && this.cycleDistanceStops.length == pathMoves.size() + 1) {
            return;
        }
        this.cycleDistanceMoves = pathMoves;
        this.cycleDistanceStops = new double[pathMoves.size() + 1];
        double total = 0.0;
        for (int i = 0; i < pathMoves.size(); i++) {
            if (this.verticalCyclePath != null && pathMoves == this.cachedCycleMoves) {
                total = this.verticalCyclePath.steps().get(i).endSeconds() * CYCLE_BASE_FEED_MM_PER_SEC;
                this.cycleDistanceStops[i + 1] = total;
                continue;
            }
            GCodeMoveData move = pathMoves.get(i);
            double dx = LatheMeshBuilder.toRadius(move.endX()) - LatheMeshBuilder.toRadius(move.startX());
            double dz = move.endZ() - move.startZ();
            total += Math.max(0.2, Math.hypot(dx, dz));
            this.cycleDistanceStops[i + 1] = total;
        }
        this.cycleTotalDistance = total;
        this.cycleDistanceProgress = Math.min(this.cycleDistanceProgress, total);
    }

    private double cycleProgressFromDistance(double distance) {
        if (this.cycleDistanceStops.length < 2 || this.cycleTotalDistance <= 0.0) {
            return this.cycleProgress;
        }
        double clamped = Math.max(0.0, Math.min(distance, this.cycleTotalDistance));
        int low = 0;
        int high = this.cycleDistanceStops.length - 2;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (this.cycleDistanceStops[mid + 1] < clamped) {
                low = mid + 1;
            } else if (this.cycleDistanceStops[mid] > clamped) {
                high = mid - 1;
            } else {
                double span = Math.max(1e-12, this.cycleDistanceStops[mid + 1] - this.cycleDistanceStops[mid]);
                return mid + (clamped - this.cycleDistanceStops[mid]) / span;
            }
        }
        return Math.max(0, this.cycleDistanceStops.length - 2);
    }

    private int activeCycleMoveIndex(List<GCodeMoveData> pathMoves) {
        if (pathMoves == null || pathMoves.isEmpty()) {
            return -1;
        }
        return Math.max(0, Math.min(pathMoves.size() - 1, (int) Math.floor(this.cycleProgress)));
    }

    private List<double[]> cycleProfileForIndex(int activeIndex, List<GCodeMoveData> pathMoves) {
        if (activeIndex < 0 || pathMoves == null || pathMoves.isEmpty()) {
            return this.stockProfileForCycle();
        }
        int clampedIndex = Math.max(0, Math.min(activeIndex, pathMoves.size() - 1));
        if (clampedIndex == this.cachedCycleProfileIndex && this.cachedCycleProfile.size() >= 2) {
            return this.cachedCycleProfile;
        }
        List<double[]> cached = this.cycleProfileCache.get(clampedIndex);
        if (cached != null && cached.size() >= 2) {
            this.cachedCycleProfileIndex = clampedIndex;
            this.cachedCycleProfile = cached;
            return cached;
        }
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        List<GCodeMoveData> prefix = new ArrayList<>(pathMoves.subList(0, clampedIndex + 1));
        List<double[]> raw = LatheStockRemovalSimulator.buildRevolveProfile(this.simulationContext, prefix);
        List<double[]> profile = raw.size() >= 2
                ? StockRemovalMeshBuilder.prepareDisplayProfile(this.simulationContext, raw, stockRadius)
                : this.stockProfileForCycle();
        if (stockRadius > 0.0
                && clampedIndex > 0
                && !StockRemovalMeshBuilder.profileHasMaterialRemoval(profile, stockRadius)) {
            profile = this.stockProfileForCycle();
        }
        this.cachedCycleProfileIndex = clampedIndex;
        this.cachedCycleProfile = profile;
        this.cycleProfileCache.put(clampedIndex, profile);
        return profile;
    }

    private static List<double[]> buildCycleProfileSnapshot(
            SimulationContext context,
            List<GCodeMoveData> pathMoves,
            int activeIndex
    ) {
        if (context == null || activeIndex < 0 || pathMoves == null || pathMoves.isEmpty()) {
            return stockProfileForCycle(context);
        }
        int clampedIndex = Math.max(0, Math.min(activeIndex, pathMoves.size() - 1));
        List<GCodeMoveData> prefix = new ArrayList<>(pathMoves.subList(0, clampedIndex + 1));
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(context);
        List<double[]> raw = LatheStockRemovalSimulator.buildRevolveProfile(context, prefix);
        List<double[]> profile = raw.size() >= 2
                ? StockRemovalMeshBuilder.prepareDisplayProfile(context, raw, stockRadius)
                : stockProfileForCycle(context);
        if (stockRadius > 0.0
                && clampedIndex > 0
                && !StockRemovalMeshBuilder.profileHasMaterialRemoval(profile, stockRadius)) {
            return stockProfileForCycle(context);
        }
        return profile;
    }

    /** Запас по Z вокруг частичного хода: ширина контакта пластины/чашки с запасом. */
    private static final double CYCLE_ENVELOPE_TAIL_PAD_MM = 100.0;

    /**
     * Профиль среза для префикса ходов цикла. Вместо пересчёта всего префикса с нуля
     * (O(n²) за цикл — из-за этого срез отставал от инструмента на врезаниях)
     * держим одну персистентную огибающую и применяем только новые полные ходы.
     * Частичный последний ход применяется поверх с откатом затронутой полосы Z.
     * Вызывается только из фоновой задачи сборки меша (сборки строго по одной).
     */
    private List<double[]> buildCycleProfileIncremental(
            SimulationContext context,
            List<GCodeMoveData> sourceMoves,
            List<GCodeMoveData> prefix
    ) {
        if (sourceMoves != null && !sourceMoves.isEmpty() && prefix != null && prefix.size() >= sourceMoves.size())
            return LatheStockRemovalSimulator.buildRevolveProfile(context);
        if (com.sergey.pisarev.service.AxialStockSection.enabled(context))
            return com.sergey.pisarev.service.AxialStockSection.build(context, prefix);
        if (context == null || prefix == null || prefix.isEmpty()) {
            return stockProfileForCycle(context);
        }
        double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(context);
        com.sergey.pisarev.service.LatheCuttingEnvelope envelope = this.cuttingEnvelopeFor(context);
        int completeCount = Math.max(0, prefix.size() - 1);
        java.util.TreeMap<Double, Double> radiusByZ;
        int appliedCount;
        synchronized (this.cycleEnvelopeLock) {
            if (this.cycleEnvelopeWork != null
                    && this.cycleEnvelopeSourceMoves == sourceMoves
                    && this.cycleEnvelopeAppliedCount <= completeCount) {
                radiusByZ = this.cycleEnvelopeWork;
                appliedCount = this.cycleEnvelopeAppliedCount;
            } else {
                radiusByZ = null;
                appliedCount = 0;
            }
        }
        if (radiusByZ == null) {
            radiusByZ = LatheStockRemovalSimulator.newStockEnvelope(context);
            if (radiusByZ == null) {
                return stockProfileForCycle(context);
            }
        }
        LatheStockRemovalSimulator.applyMovesToEnvelope(
                radiusByZ, context, envelope, prefix, appliedCount, completeCount);
        synchronized (this.cycleEnvelopeLock) {
            this.cycleEnvelopeWork = radiusByZ;
            this.cycleEnvelopeAppliedCount = completeCount;
            this.cycleEnvelopeSourceMoves = sourceMoves;
        }
        List<double[]> raw;
        if (completeCount < prefix.size()) {
            // Частичный последний ход: сохраняем полосу Z, применяем, считаем профиль, откатываем.
            GCodeMoveData tail = prefix.get(prefix.size() - 1);
            double zLo = Math.min(tail.startZ(), tail.endZ()) - CYCLE_ENVELOPE_TAIL_PAD_MM;
            double zHi = Math.max(tail.startZ(), tail.endZ()) + CYCLE_ENVELOPE_TAIL_PAD_MM;
            java.util.TreeMap<Double, Double> savedBand =
                    new java.util.TreeMap<>(radiusByZ.subMap(zLo, true, zHi, true));
            LatheStockRemovalSimulator.applyMovesToEnvelope(
                    radiusByZ, context, envelope, prefix, completeCount, prefix.size());
            raw = LatheStockRemovalSimulator.envelopeToProfile(context, radiusByZ);
            radiusByZ.subMap(zLo, true, zHi, true).clear();
            radiusByZ.putAll(savedBand);
        } else {
            raw = LatheStockRemovalSimulator.envelopeToProfile(context, radiusByZ);
        }
        List<double[]> profile = raw.size() >= 2
                ? StockRemovalMeshBuilder.prepareDisplayProfile(context, raw, stockRadius)
                : stockProfileForCycle(context);
        if (stockRadius > 0.0
                && prefix.size() > 1
                && !StockRemovalMeshBuilder.profileHasMaterialRemoval(profile, stockRadius)) {
            return stockProfileForCycle(context);
        }
        return profile;
    }

    /** Кэш LatheCuttingEnvelope: его создание сканирует весь текст программы. */
    private com.sergey.pisarev.service.LatheCuttingEnvelope cuttingEnvelopeFor(SimulationContext context) {
        synchronized (this.cycleEnvelopeLock) {
            if (this.cycleCuttingEnvelopeCache == null || this.cycleCuttingEnvelopeContext != context) {
                this.cycleCuttingEnvelopeCache = new com.sergey.pisarev.service.LatheCuttingEnvelope(context);
                this.cycleCuttingEnvelopeContext = context;
            }
            return this.cycleCuttingEnvelopeCache;
        }
    }

    private List<double[]> stockProfileForCycle() {
        return stockProfileForCycle(this.simulationContext);
    }

    private static List<double[]> stockProfileForCycle(SimulationContext context) {
        if (com.sergey.pisarev.service.AxialStockSection.enabled(context))
            return com.sergey.pisarev.service.AxialStockSection.initialSection(context);
        double radius = StockRemovalMeshBuilder.resolveStockRadiusMm(context);
        if (radius <= 0.0) {
            radius = 50.0;
        }
        double zMin = -50.0;
        double zMax = 50.0;
        WorkpieceDefinition workpiece = context != null ? context.getWorkpiece() : null;
        if (workpiece != null && workpiece.isValid()) {
            zMin = workpiece.getZMin();
            zMax = workpiece.getZMax();
        }
        return List.of(new double[]{zMin, radius}, new double[]{zMax, radius});
    }

    private void queuePendingCycleMeshBuild(int activeIndex, int meshFrame, List<GCodeMoveData> pathMoves) {
        this.pendingCycleMeshIndex = activeIndex;
        this.pendingCycleMeshFrame = meshFrame;
        this.pendingCycleMeshMoves = pathMoves != null ? pathMoves : List.of();
    }

    private void scheduleCycleMeshBuild(int activeIndex, int meshFrame, List<GCodeMoveData> pathMoves) {
        if (this.simulationContext == null) {
            this.finishCycleRestartLoading();
            return;
        }
        this.lastCycleMeshRequestNanos = System.nanoTime();
        if (this.cycleMeshBuildRunning) {
            this.queuePendingCycleMeshBuild(activeIndex, meshFrame, pathMoves);
            return;
        }
        this.cycleMeshBuildRunning = true;
        final int buildGeneration = this.cycleMeshGeneration;
        final double stockRadius = StockRemovalMeshBuilder.resolveStockRadiusMm(this.simulationContext);
        final int buildIndex = activeIndex;
        final int buildFrame = meshFrame;
        final List<GCodeMoveData> sourceMoves = pathMoves;
        final var verticalPath = this.verticalCyclePath;
        final List<GCodeMoveData> meshPathMoves = verticalPath == null
                ? this.cycleMeshMovesForFrame(pathMoves, meshFrame) : List.of();
        if (verticalPath != null && this.verticalCycleStock == null)
            this.verticalCycleStock = new com.sergey.pisarev.service.AxialCycleStock(this.simulationContext, verticalPath);
        final var verticalStock = this.verticalCycleStock;
        if (verticalPath != null && this.verticalPreviewMeshes == null)
            this.verticalPreviewMeshes = new com.sergey.pisarev.service.CyclePreviewMeshCache(
                    this.simulationContext.getWorkpiece(), this.cyclePreviewSlices());
        final var previewCache = this.verticalPreviewMeshes;
        final boolean finalVerticalFrame = verticalPath != null
                && meshFrame * CYCLE_SEGMENT_MM_SMOOTH / CYCLE_BASE_FEED_MM_PER_SEC >= verticalPath.durationSeconds();
        final SimulationContext meshContext = this.simulationContext;
        final long buildStartNanos = System.nanoTime();
        javafx.concurrent.Task<Map<Integer,TriangleMesh>> meshTask = new javafx.concurrent.Task<>() {
            @Override
            protected Map<Integer,TriangleMesh> call() {
                List<double[]> meshProfile = verticalPath != null
                        ? verticalStock.sectionAt(buildFrame * CYCLE_SEGMENT_MM_SMOOTH / CYCLE_BASE_FEED_MM_PER_SEC)
                        : buildCycleProfileIncremental(meshContext, sourceMoves, meshPathMoves);
                if (meshProfile.size() < 2) {
                    return Map.of();
                }
                if (finalVerticalFrame) return Map.of(-1,StockRemovalMeshBuilder.buildFinalCycleTriangleMesh(meshProfile));
                if (verticalPath != null) return previewCache.build(meshProfile);
                var mesh = StockRemovalMeshBuilder.buildCyclePreviewTriangleMesh(
                        meshContext,
                        meshProfile,
                        stockRadius);
                return mesh == null ? Map.of() : Map.of(-1,mesh);
            }
        };
        // Task completion handlers already run on the JavaFX application thread.
        meshTask.setOnSucceeded(event -> {
            try {
                if (!this.isCycleMode() || buildGeneration != this.cycleMeshGeneration) {
                    return;
                }
                if (verticalPath != null && buildFrame > this.cycleMeshFrameForProgress()) return;
                var meshes = meshTask.getValue();
                this.cycleLastMeshBuildMs = (System.nanoTime() - buildStartNanos) / 1_000_000.0;
                if (meshes.isEmpty()) {
                    this.finishCycleRestartLoading();
                    return;
                }
                this.renderedCycleMeshIndex = buildIndex;
                this.renderedCycleMeshFrame = buildFrame;
                this.applyCycleMeshes(meshes);
                // Geometry and its matching tool poses are presented atomically.
                if (verticalPath != null) this.updateCycleTool3d(sourceMoves,buildIndex);
                if (!this.cyclePlaying) {
                    this.rebuildOverlayMovesCache();
                    this.refreshOverlaysNow();
                }
            } finally {
                this.finishCycleMeshBuildAndRunPending(buildGeneration);
            }
        });
        meshTask.setOnFailed(event -> {
            this.cycleLastMeshBuildMs = (System.nanoTime() - buildStartNanos) / 1_000_000.0;
            this.finishCycleMeshBuildAndRunPending(buildGeneration);
        });
        meshTask.setOnCancelled(event -> {
            this.cycleLastMeshBuildMs = (System.nanoTime() - buildStartNanos) / 1_000_000.0;
            this.finishCycleMeshBuildAndRunPending(buildGeneration);
        });
        CYCLE_MESH_WORKER.execute(meshTask);
    }

    private void applyCycleMeshes(Map<Integer,TriangleMesh> meshes) {
        var existing = this.modelRoot.getChildren().stream()
                .filter(n -> FINISHED_PART_TAG.equals(n.getUserData())).findFirst().orElse(null);
        if (meshes.containsKey(-1)) {
            var mesh = meshes.get(-1);
            if (existing instanceof MeshView view) view.setMesh(mesh);
            else this.replaceCycleMaterial(mesh);
        } else {
            Group batches;
            if (existing instanceof Group group && Boolean.TRUE.equals(group.getProperties().get("cycle-batches"))) {
                batches = group;
            } else {
                batches = new Group();batches.setUserData(FINISHED_PART_TAG);
                batches.getProperties().put("cycle-batches",true);
                this.modelRoot.getChildren().removeIf(n -> FINISHED_PART_TAG.equals(n.getUserData()) || STOCK_BLANK_TAG.equals(n.getUserData()));
                this.applyWorkpieceSpin(batches);
                this.modelRoot.getChildren().add(batches);
            }
            batches.getChildren().removeIf(n -> !meshes.containsKey(n.getUserData()));
            var views = new java.util.HashMap<Integer,MeshView>();
            for (var child:batches.getChildren()) views.put((Integer)child.getUserData(),(MeshView)child);
            for (var entry:meshes.entrySet()) {
                var view=views.get(entry.getKey());
                if (view==null) {
                    var displayMesh=new TriangleMesh(entry.getValue().getVertexFormat());
                    displayMesh=CycleMeshBuffers.update(displayMesh,entry.getValue());
                    view=StockRemovalMeshBuilder.createFinishedMeshView(displayMesh,this.partDiffuseColor());
                    if (!batches.getChildren().isEmpty())
                        view.setMaterial(((MeshView)batches.getChildren().get(0)).getMaterial());
                    view.getProperties().put("source-mesh",entry.getValue());
                    view.setUserData(entry.getKey());batches.getChildren().add(view);
                } else if (view.getProperties().get("source-mesh")!=entry.getValue()) {
                    view.setMesh(CycleMeshBuffers.update((TriangleMesh)view.getMesh(),entry.getValue()));
                    view.getProperties().put("source-mesh",entry.getValue());
                }
            }
        }
        if (this.verticalCyclePath != null) this.cullCycleBackFaces();
        this.lastMeshError=null;
        this.hideMeshError();
    }

    /**
     * Сечение карусельного цикла — замкнутое тело с внешними нормалями, изнанку не рисуем.
     *
     * <p>Где деталь истончается до сотых (глубокая канавка у самого торца заготовки),
     * лицо и изнанка стоят ближе, чем различает буфер глубины на расстоянии обзора, и
     * изнанка проступала тёмной рябью. И живые пакеты сечения, и итог OCCT обходят
     * грани наружу, поэтому отсечение изнанки убирает только её.
     */
    private void cullCycleBackFaces() {
        for (var node : this.modelRoot.getChildren()) {
            if (!FINISHED_PART_TAG.equals(node.getUserData())) continue;
            if (node instanceof MeshView view) view.setCullFace(CullFace.BACK);
            else if (node instanceof Group group)
                for (var child : group.getChildren())
                    if (child instanceof MeshView view) view.setCullFace(CullFace.BACK);
        }
    }

    private void finishCycleMeshBuildAndRunPending(int generation) {
        if (generation != this.cycleMeshGeneration) {
            return;
        }
        this.cycleMeshBuildRunning = false;
        if (!this.isCycleMode()
                || this.pendingCycleMeshIndex == Integer.MIN_VALUE) {
            this.pendingCycleMeshIndex = Integer.MIN_VALUE;
            this.pendingCycleMeshFrame = Integer.MIN_VALUE;
            this.pendingCycleMeshMoves = List.of();
            this.finishCycleRestartLoading();
            return;
        }
        int nextIndex = this.pendingCycleMeshIndex;
        int nextFrame = this.pendingCycleMeshFrame;
        List<GCodeMoveData> nextMoves = this.pendingCycleMeshMoves;
        this.pendingCycleMeshIndex = Integer.MIN_VALUE;
        this.pendingCycleMeshFrame = Integer.MIN_VALUE;
        this.pendingCycleMeshMoves = List.of();
        if (this.cyclePlaying && this.isVerticalMachine()
                && System.nanoTime()-this.lastCycleMeshRequestNanos < this.cycleMeshMinIntervalNanos()) {
            // The next animation pulse requests the latest position; do not
            // replay an obsolete queue of geometry frames between pulses.
            return;
        }
        if (nextIndex != this.renderedCycleMeshIndex || nextFrame != this.renderedCycleMeshFrame) {
            this.scheduleCycleMeshBuild(nextIndex, nextFrame, nextMoves);
        } else {
            this.finishCycleRestartLoading();
        }
    }

    private void updateCycleTool3d(List<GCodeMoveData> pathMoves, int activeIndex) {
        if (!this.isCycleMode() || pathMoves == null || pathMoves.isEmpty() || activeIndex < 0) {
            this.modelRoot.getChildren().removeIf(node -> {
                Object tag = node.getUserData();
                return CYCLE_TOOL_TAG.equals(tag) || CYCLE_CUT_PREVIEW_TAG.equals(tag);
            });
            this.cycleTool3d = null;
            this.clearCycleCutPreviewReferences();
            this.cycleToolSignature = "";
            this.updateCycleToolLabel(null, null);
            return;
        }
        if (this.verticalCyclePath != null && this.isVerticalMachine()) {
            this.updateVerticalCycleTools(pathMoves, activeIndex);
            return;
        }
        GCodeMoveData move = pathMoves.get(Math.min(activeIndex, pathMoves.size() - 1));
        CncToolDefinition toolDefinition = this.findToolDefinition(move);
        this.updateCycleToolLabel(move, toolDefinition);
        String signature = this.cycleToolSignature(toolDefinition, move);
        if (this.cycleTool3d == null || !signature.equals(this.cycleToolSignature)) {
            this.modelRoot.getChildren().removeIf(node -> CYCLE_TOOL_TAG.equals(node.getUserData()));
            this.cycleTool3d = ToolModel3dFactory.create(toolDefinition, move, this.darkTheme);
            this.cycleTool3d.setUserData(CYCLE_TOOL_TAG);
            this.installCycleToolTooltip(this.cycleTool3d, toolDefinition);
            this.modelRoot.getChildren().add(this.cycleTool3d);
            this.cycleToolSignature = signature;
        }
        double localT = Math.max(0.0, Math.min(1.0, this.cycleProgress - Math.floor(this.cycleProgress)));
        // Инструмент идёт по эквидистанте G41/G42 (смещение на радиус пластины/чашки + OFFN),
        // а срез считается по программному контуру.
        GCodeMoveData positionMove = move;
        int compIndex = Math.min(activeIndex, pathMoves.size() - 1);
        if (pathMoves == this.cachedCycleMoves
                && this.cachedCycleCompMoves.size() == this.cachedCycleMoves.size()
                && compIndex >= 0
                && compIndex < this.cachedCycleCompMoves.size()) {
            positionMove = this.cachedCycleCompMoves.get(compIndex);
        }
        double z = positionMove.startZ() + (positionMove.endZ() - positionMove.startZ()) * localT;
        double x = positionMove.startX() + (positionMove.endX() - positionMove.startX()) * localT;
        double radius = Math.max(0.0, LatheMeshBuilder.toRadius(x));
        // Нулевая точка модели инструмента — режущая вершина. Поэтому ставим её прямо
        // в текущую X/Z-точку траектории, без прежнего сдвига +24/+18 мм.
        this.cycleTool3d.setTranslateX(radius);
        this.cycleTool3d.setTranslateY(z);
        this.cycleTool3d.setTranslateZ(0.0);
        this.hideCycleCutPreview();
    }

    private void updateVerticalCycleTools(List<GCodeMoveData> pathMoves, int activeIndex) {
        if (this.renderedCycleMeshFrame < 0) return;
        if (this.cycleTool3d == null || !Boolean.TRUE.equals(this.cycleTool3d.getProperties().get("vertical-supports"))) {
            this.modelRoot.getChildren().removeIf(n -> CYCLE_TOOL_TAG.equals(n.getUserData()));
            this.cycleTool3d = new Group();
            this.cycleTool3d.setUserData(CYCLE_TOOL_TAG);
            this.cycleTool3d.getProperties().put("vertical-supports", true);
            this.modelRoot.getChildren().add(this.cycleTool3d);
        }
        var label = new StringBuilder();
        double seconds = this.renderedCycleMeshFrame * CYCLE_SEGMENT_MM_SMOOTH / CYCLE_BASE_FEED_MM_PER_SEC;
        var stock = this.simulationContext != null ? this.simulationContext.getWorkpiece() : null;
        double stockTopZ = stock != null && stock.isValid() ? stock.getZMax() : 0.0;
        for (int channel = 0; channel < this.verticalCyclePath.channelCount(); channel++) {
            var pose = this.verticalCyclePath.poseAt(channel, seconds);
            if (pose == null) continue;
            var move = pose.contour();
            var tool = this.findToolDefinition(move);
            int id = this.verticalCyclePath.supportNumber(channel);
            double side = id == 2 ? -1 : 1;
            String signature = this.cycleToolSignature(tool, move);
            Group support = null;
            for (var child : this.cycleTool3d.getChildren())
                if (Integer.valueOf(id).equals(child.getUserData())) support = (Group)child;
            if (support == null) {
                support = new Group(); support.setUserData(id); this.cycleTool3d.getChildren().add(support);
            }
            if (!signature.equals(support.getProperties().get("signature"))) {
                var model = VerticalToolModel3dFactory.create(tool, this.darkTheme);
                // Mirror the radial mounting, retaining the same X/Z cutting plane.
                if (side < 0) model.getTransforms().add(new javafx.scene.transform.Scale(-1,1,1));
                support.getChildren().setAll(model);
                support.getProperties().put("signature", signature);
                this.installCycleToolTooltip(model, tool);
            }
            double t = pose.fraction();
            var centre = pose.centre();
            VerticalToolModel3dFactory.alignContact((Group)support.getChildren().get(0),pose);
            // Signed X allows a support to pass the spindle axis. Only its station is mirrored.
            double supportX = side * (centre.startX() + (centre.endX()-centre.startX())*t) * .5;
            double supportZ = centre.startZ() + (centre.endZ()-centre.startZ())*t;
            // Before its first block and after its last one a support is not being
            // driven. Holding it on the contour leaves the ram standing in metal that
            // has not been cut yet; a carousel keeps a waiting support retracted.
            boolean running = this.verticalCyclePath.activeAt(channel, seconds);
            if (!running) {
                supportZ = Math.max(this.verticalCyclePath.retractZ(channel), stockTopZ + SUPPORT_PARK_CLEARANCE_MM);
            }
            support.setTranslateX(supportX);
            support.setTranslateY(supportZ);
            support.getProperties().put("presented-seconds",seconds);
            support.getProperties().put("source-line", move.sourceLine());
            if (label.length()>0) label.append("\n");
            label.append(I18n.text("sim.support.prefix")).append(id).append("  T").append(move.toolNumber()).append(" D").append(move.edgeNumber());
            if (tool == null) label.append(I18n.text("sim.support.nogeometry"));
            else label.append("  ").append(tool.getName());
            if (!running) label.append(I18n.text("sim.support.retracted"));
        }
        if (this.cycleToolLabel != null) {
            this.cycleToolLabel.setText(label.toString());
            if (this.cycleToolLabel.getTooltip() == null) this.cycleToolLabel.setTooltip(new Tooltip(I18n.text("sim.support.tooltip")));
        }
        this.hideCycleCutPreview();
    }

    private void updateCycleToolLabel(GCodeMoveData move, CncToolDefinition toolDefinition) {
        if (this.cycleToolLabel == null || !this.cycleToolLabel.isVisible()) {
            return;
        }
        if (move == null) {
            this.cycleToolLabel.setText("T --");
            return;
        }
        int toolNumber = move.toolNumber();
        int edgeNumber = Math.max(1, move.edgeNumber());
        StringBuilder text = new StringBuilder();
        text.append(toolNumber > 0 ? "T" + toolNumber : "T --");
        text.append(" D").append(edgeNumber);
        if (toolDefinition != null) {
            text.append(" P").append(toolDefinition.getToolPosition());
            String name = toolDefinition.getName();
            if (name != null && !name.isBlank()) {
                text.append("  ").append(name.trim());
            }
        }
        this.cycleToolLabel.setText(text.toString());
    }

    private void updateCycleCutPreview(GCodeMoveData move, double radius, double z) {
        if (move == null || move.rapid() || radius <= 0.02 || !Double.isFinite(radius) || !Double.isFinite(z)) {
            this.hideCycleCutPreview();
            return;
        }
        this.ensureCycleCutPreview();
        if (this.cycleCutPreview3d == null) {
            return;
        }
        double strokeWidth = Math.max(0.45, Math.min(2.8, Math.hypot(
                move.endX() - move.startX(),
                move.endZ() - move.startZ()) * 0.035 + 0.55));
        double dz = move.endZ() - move.startZ();
        double zDirection = Math.abs(dz) < 1.0e-6 ? 1.0 : Math.signum(dz);
        this.cycleCutPreviewBand.setRadius(Math.max(0.6, radius));
        this.cycleCutPreviewBand.setHeight(strokeWidth);
        this.cycleCutPreviewBand.setTranslateY(z);
        this.cycleCutPreviewPoint.setTranslateX(radius);
        this.cycleCutPreviewPoint.setTranslateY(z);
        this.cycleCutPreviewPoint.setTranslateZ(0.0);
        this.cycleCutPreviewChip.setWidth(Math.max(2.4, Math.min(8.5, radius * 0.08)));
        this.cycleCutPreviewChip.setHeight(Math.max(0.8, Math.min(3.0, strokeWidth * 1.8)));
        this.cycleCutPreviewChip.setDepth(Math.max(0.8, Math.min(2.5, strokeWidth * 1.4)));
        this.cycleCutPreviewChip.setTranslateX(radius + 2.2);
        this.cycleCutPreviewChip.setTranslateY(z - zDirection * 2.0);
        this.cycleCutPreviewChip.setTranslateZ(1.1);
        this.cycleCutPreview3d.setVisible(true);
        this.cycleCutPreview3d.toFront();
    }

    private void ensureCycleCutPreview() {
        if (this.cycleCutPreview3d != null) {
            return;
        }
        this.cycleCutPreview3d = new Group();
        this.cycleCutPreview3d.setUserData(CYCLE_CUT_PREVIEW_TAG);
        this.cycleCutPreview3d.setMouseTransparent(true);
        this.cycleCutPreview3d.setDepthTest(DepthTest.DISABLE);
        this.cycleCutPreview3d.setViewOrder(-60.0);

        PhongMaterial bandMaterial = new PhongMaterial(Color.web("#facc15", 0.66));
        PhongMaterial pointMaterial = new PhongMaterial(Color.web("#fff7ed", 0.96));
        PhongMaterial chipMaterial = new PhongMaterial(Color.web("#f59e0b", 0.82));

        this.cycleCutPreviewBand = new Cylinder(1.0, 0.8, 64);
        this.cycleCutPreviewBand.setDrawMode(DrawMode.LINE);
        this.cycleCutPreviewBand.setCullFace(CullFace.NONE);
        this.cycleCutPreviewBand.setMaterial(bandMaterial);
        this.cycleCutPreviewBand.setOpacity(0.72);
        this.cycleCutPreviewBand.setDepthTest(DepthTest.DISABLE);
        this.cycleCutPreviewBand.setMouseTransparent(true);

        this.cycleCutPreviewPoint = new Sphere(2.7, 24);
        this.cycleCutPreviewPoint.setMaterial(pointMaterial);
        this.cycleCutPreviewPoint.setOpacity(0.94);
        this.cycleCutPreviewPoint.setDepthTest(DepthTest.DISABLE);
        this.cycleCutPreviewPoint.setMouseTransparent(true);

        this.cycleCutPreviewChip = new Box(5.0, 1.4, 1.2);
        this.cycleCutPreviewChip.setMaterial(chipMaterial);
        this.cycleCutPreviewChip.setOpacity(0.82);
        this.cycleCutPreviewChip.setDepthTest(DepthTest.DISABLE);
        this.cycleCutPreviewChip.setMouseTransparent(true);

        this.cycleCutPreview3d.getChildren().addAll(
                this.cycleCutPreviewBand,
                this.cycleCutPreviewPoint,
                this.cycleCutPreviewChip);
        this.modelRoot.getChildren().add(this.cycleCutPreview3d);
    }

    private void hideCycleCutPreview() {
        if (this.cycleCutPreview3d != null) {
            this.cycleCutPreview3d.setVisible(false);
        }
    }

    private void clearCycleCutPreviewReferences() {
        this.cycleCutPreview3d = null;
        this.cycleCutPreviewBand = null;
        this.cycleCutPreviewPoint = null;
        this.cycleCutPreviewChip = null;
    }

    private String cycleToolSignature(CncToolDefinition toolDefinition, GCodeMoveData move) {
        if (toolDefinition == null) {
            return "default:" + move.toolNumber() + ":" + move.edgeNumber();
        }
        return toolDefinition.getToolNumber() + ":"
                + toolDefinition.getEdgeNumber() + ":"
                + toolDefinition.getTypeCode() + ":"
                + toolDefinition.getTypeIdentifier() + ":"
                + toolDefinition.getPlateLength() + ":"
                + toolDefinition.getCutWidth() + ":"
                + toolDefinition.getRadius() + ":"
                + toolDefinition.getHolderAngleDeg() + ":"
                + toolDefinition.getInsertAngleDeg() + ":"
                + toolDefinition.getLengthX() + ":"
                + toolDefinition.getLengthZ() + ":"
                + toolDefinition.getToolPosition() + ":"
                + toolDefinition.getCutDirection() + ":" + toolDefinition.getModelId();
    }

    private void installCycleToolTooltip(Group toolGroup, CncToolDefinition toolDefinition) {
        if (toolGroup == null) {
            return;
        }
        Tooltip tooltip = ToolIconFactory.tooltip(toolDefinition);
        tooltip.setStyle("-fx-font-size: 15px; -fx-background-color: rgba(15, 23, 42, 0.96);"
                + " -fx-text-fill: white; -fx-padding: 10 12 10 12;"
                + " -fx-background-radius: 8; -fx-border-color: rgba(148, 163, 184, 0.7);"
                + " -fx-border-radius: 8;");
        toolGroup.setPickOnBounds(true);
        toolGroup.setMouseTransparent(false);
        Tooltip.install(toolGroup, tooltip);
        for (javafx.scene.Node child : toolGroup.getChildren()) {
            child.setPickOnBounds(true);
            child.setMouseTransparent(false);
            Tooltip.install(child, tooltip);
        }
    }

    private Group buildCycleTool3d(GCodeMoveData move, CncToolDefinition toolDefinition) {
        String descriptor = toolDefinition == null
                ? ""
                : ((toolDefinition.getType() + " " + toolDefinition.getTypeIdentifier() + " " + toolDefinition.getName())
                        .toLowerCase(Locale.ROOT));
        int typeCode = toolDefinition != null ? toolDefinition.getTypeCode() : 500;
        if (typeCode == 550 || descriptor.contains("button") || descriptor.contains("round")) {
            return this.buildButtonTool3d(toolDefinition, move);
        }
        if (typeCode == 580 || typeCode == 585
                || descriptor.contains("probe") || descriptor.contains("calibr")
                || descriptor.contains("зонд") || descriptor.contains("калибр")) {
            return this.buildProbeTool3d(toolDefinition, move);
        }
        if ((typeCode >= 200 && typeCode < 300) || typeCode == 560
                || descriptor.contains("drill") || descriptor.contains("сверл")) {
            return this.buildDrillTool3d(toolDefinition, move);
        }
        if (typeCode == 520 || typeCode == 530
                || descriptor.contains("groov") || descriptor.contains("part") || descriptor.contains("cutoff")
                || descriptor.contains("отрез") || descriptor.contains("канав") || descriptor.contains("plunge")) {
            return this.buildGroovingTool3d(toolDefinition, move);
        }
        if (typeCode == 540 || descriptor.contains("thread") || descriptor.contains("резьб")) {
            return this.buildThreadingTool3d(toolDefinition, move);
        }
        return this.buildTurningTool3d(toolDefinition, move);
    }

    private Group buildButtonTool3d(CncToolDefinition toolDefinition, GCodeMoveData move) {
        double insertRadius = Math.max(8.0, toolDefinition != null && toolDefinition.getRadius() > 0.0
                ? toolDefinition.getRadius() * 1.75 : 10.0);
        double holderLength = toolDefinition != null && toolDefinition.getPlateLength() > 0.0
                ? Math.max(56.0, toolDefinition.getPlateLength() * 4.0) : 66.0;
        double holderWidth = Math.max(18.0, insertRadius * 2.2);
        double direction = move.endZ() >= move.startZ() ? -1.0 : 1.0;
        Group group = new Group();

        PhongMaterial shankMat = new PhongMaterial(Color.web("#334155"));
        shankMat.setSpecularColor(Color.web("#cbd5e1", 0.35));
        shankMat.setSpecularPower(52.0);
        PhongMaterial pocketMat = new PhongMaterial(Color.web("#475569"));
        pocketMat.setSpecularColor(Color.web("#e2e8f0", 0.28));
        pocketMat.setSpecularPower(46.0);
        PhongMaterial insertMat = new PhongMaterial(Color.web("#facc15"));
        insertMat.setSpecularColor(Color.web("#fff7ed", 0.55));
        insertMat.setSpecularPower(84.0);
        PhongMaterial screwMat = new PhongMaterial(Color.web("#64748b"));
        screwMat.setSpecularColor(Color.web("#f8fafc", 0.5));
        screwMat.setSpecularPower(90.0);
        PhongMaterial edgeMat = new PhongMaterial(Color.web("#ef4444"));
        edgeMat.setSpecularColor(Color.web("#fecaca", 0.65));
        edgeMat.setSpecularPower(120.0);

        Box shank = new Box(holderWidth, holderLength, holderWidth * 0.68);
        shank.setTranslateX(34.0);
        shank.setTranslateY(direction * -holderLength * 0.45);
        shank.getTransforms().add(new javafx.scene.transform.Rotate(direction * 3.0, javafx.scene.transform.Rotate.Z_AXIS));
        shank.setMaterial(shankMat);
        group.getChildren().add(shank);

        Box seat = new Box(insertRadius * 2.4, insertRadius * 1.05, holderWidth * 0.72);
        seat.setTranslateX(insertRadius * 0.9);
        seat.setTranslateY(direction * -insertRadius * 0.25);
        seat.setMaterial(pocketMat);
        group.getChildren().add(seat);

        Cylinder insert = new Cylinder(insertRadius, Math.max(4.0, holderWidth * 0.24), 72);
        insert.getTransforms().add(new javafx.scene.transform.Rotate(90.0, javafx.scene.transform.Rotate.X_AXIS));
        insert.setTranslateX(insertRadius);
        insert.setTranslateY(0.0);
        insert.setMaterial(insertMat);
        group.getChildren().add(insert);

        Cylinder screw = new Cylinder(insertRadius * 0.32, Math.max(4.8, holderWidth * 0.3), 40);
        screw.getTransforms().add(new javafx.scene.transform.Rotate(90.0, javafx.scene.transform.Rotate.X_AXIS));
        screw.setTranslateX(insertRadius);
        screw.setTranslateY(0.0);
        screw.setMaterial(screwMat);
        group.getChildren().add(screw);

        Sphere cuttingPoint = new Sphere(Math.max(1.6, insertRadius * 0.13));
        cuttingPoint.setTranslateX(0.0);
        cuttingPoint.setTranslateY(0.0);
        cuttingPoint.setMaterial(edgeMat);
        group.getChildren().add(cuttingPoint);

        return group;
    }

    private Group buildProbeTool3d(CncToolDefinition toolDefinition, GCodeMoveData move) {
        double direction = move.endZ() >= move.startZ() ? -1.0 : 1.0;
        double probeRadius = Math.max(3.8, toolDefinition != null && toolDefinition.getRadius() > 0.0
                ? toolDefinition.getRadius() * 1.3 : 4.5);
        double stemLength = toolDefinition != null && toolDefinition.getLengthZ() > 0.0
                ? Math.max(34.0, Math.min(92.0, toolDefinition.getLengthZ() * 0.55))
                : 48.0;
        Group group = new Group();

        PhongMaterial bodyMat = new PhongMaterial(Color.web("#475569"));
        bodyMat.setSpecularColor(Color.web("#e2e8f0", 0.4));
        bodyMat.setSpecularPower(64.0);
        PhongMaterial collarMat = new PhongMaterial(Color.web("#94a3b8"));
        collarMat.setSpecularColor(Color.web("#f8fafc", 0.55));
        collarMat.setSpecularPower(90.0);
        PhongMaterial rubyMat = new PhongMaterial(Color.web("#ef4444"));
        rubyMat.setSpecularColor(Color.web("#fecaca", 0.8));
        rubyMat.setSpecularPower(160.0);

        Cylinder stem = new Cylinder(Math.max(1.8, probeRadius * 0.42), stemLength, 32);
        stem.setTranslateY(direction * -(stemLength * 0.5 + probeRadius));
        stem.setMaterial(bodyMat);
        group.getChildren().add(stem);

        Cylinder collar = new Cylinder(probeRadius * 0.85, Math.max(4.0, probeRadius * 0.8), 40);
        collar.setTranslateY(direction * -(probeRadius * 1.45));
        collar.setMaterial(collarMat);
        group.getChildren().add(collar);

        Sphere tip = new Sphere(probeRadius, 40);
        tip.setMaterial(rubyMat);
        group.getChildren().add(tip);

        return group;
    }

    private Group buildTurningTool3d(CncToolDefinition toolDefinition, GCodeMoveData move) {
        double holderLength = toolDefinition != null && toolDefinition.getPlateLength() > 0.0
                ? Math.max(52.0, toolDefinition.getPlateLength() * 4.2) : 74.0;
        double holderThickness = toolDefinition != null && toolDefinition.getCutWidth() > 0.0
                ? Math.max(12.0, toolDefinition.getCutWidth() * 3.0) : 18.0;
        double noseRadius = Math.max(1.0, toolDefinition != null && toolDefinition.getRadius() > 0.0
                ? toolDefinition.getRadius() * 5.0 : 4.8);
        double dir = move.endZ() >= move.startZ() ? -1.0 : 1.0;
        Group group = new Group();

        // Materials: steel holder + carbide insert, closer to CAM simulators such as VERICUT.
        PhongMaterial shankMat = new PhongMaterial(Color.web("#475569"));
        shankMat.setSpecularColor(Color.web("#e2e8f0", 0.38));
        shankMat.setSpecularPower(52.0);
        PhongMaterial headMat = new PhongMaterial(Color.web("#334155"));
        headMat.setSpecularColor(Color.web("#cbd5e1", 0.35));
        headMat.setSpecularPower(46.0);
        PhongMaterial insertMat = new PhongMaterial(Color.web("#d4a017"));
        insertMat.setSpecularColor(Color.web("#fff7ed", 0.62));
        insertMat.setSpecularPower(96.0);
        PhongMaterial cuttingMat = new PhongMaterial(Color.web("#ef4444"));
        cuttingMat.setSpecularColor(Color.web("#fca5a5", 0.5));
        cuttingMat.setSpecularPower(80.0);
        PhongMaterial clampMat = new PhongMaterial(Color.web("#0f172a"));
        clampMat.setSpecularColor(Color.web("#cbd5e1", 0.28));
        clampMat.setSpecularPower(54.0);
        PhongMaterial screwMat = new PhongMaterial(Color.web("#64748b"));
        screwMat.setSpecularColor(Color.web("#f8fafc", 0.55));
        screwMat.setSpecularPower(92.0);

        double d = dir;
        double l = holderLength;
        double t = holderThickness;
        double nr = noseRadius;

        // === 1. SHANK (державка) — от -12 до -74 по X ===
        double sw = Math.max(16.0, t * 0.9);
        Box shank = new Box(sw, l * 0.55, sw);
        shank.setTranslateX(-(l * 0.275 + 12.0));
        shank.setTranslateY(d * -(l * 0.5 - l * 0.275 - 8.0));
        shank.setMaterial(shankMat);
        group.getChildren().add(shank);

        // === 2. HEAD (голова) — от -12 до 0 по X, шире державки ===
        double hw = Math.max(22.0, t * 1.15);
        Box head = new Box(hw, 16.0, sw * 0.9);
        head.setTranslateX(-5.0);
        head.setTranslateY(d * (l * 0.5 - 8.0));
        head.setMaterial(headMat);
        head.getTransforms().add(new javafx.scene.transform.Rotate(d * 5.0, javafx.scene.transform.Rotate.Z_AXIS));
        group.getChildren().add(head);

        // === 3. INSERT (пластина) — треугольный карбид на кромке ===
        double iw = Math.max(14.0, nr * 3.5);
        double ih = Math.max(12.0, nr * 3.0);
        double id = Math.max(4.0, t * 0.45);
        MeshView insert = this.createTriangularInsertMesh(iw, ih, id, insertMat);
        // Пластина слегка выступает за голову — её вершина у (0,0,0)
        insert.setTranslateX(-iw * 0.2);
        insert.setTranslateY(d * -ih * 0.1);
        insert.setTranslateZ(0.0);
        insert.getTransforms().add(new javafx.scene.transform.Rotate(d * 5.0, javafx.scene.transform.Rotate.Z_AXIS));
        group.getChildren().add(insert);

        // === 4. CUTTING EDGE (режущая кромка — красный nose radius) ===
        Box clamp = new Box(iw * 0.42, Math.max(2.2, ih * 0.16), id * 1.25);
        clamp.setTranslateX(iw * 0.28);
        clamp.setTranslateY(d * -ih * 0.34);
        clamp.setMaterial(clampMat);
        group.getChildren().add(clamp);

        Cylinder screw = new Cylinder(Math.max(2.4, id * 0.32), Math.max(3.5, id * 0.8), 32);
        screw.getTransforms().add(new javafx.scene.transform.Rotate(90.0, javafx.scene.transform.Rotate.X_AXIS));
        screw.setTranslateX(iw * 0.42);
        screw.setTranslateY(d * -ih * 0.12);
        screw.setMaterial(screwMat);
        group.getChildren().add(screw);

        MeshView nose = this.createNoseRadiusMesh(nr * 2.0, id * 0.6);
        nose.setTranslateX(-iw * 0.4);
        nose.setTranslateY(d * ih * 0.05);
        nose.setTranslateZ(0.0);
        nose.setMaterial(cuttingMat);
        group.getChildren().add(nose);

        // Сдвиг: режущая кромка → (0,0,0)
        double noseX = -iw * 0.4;
        double noseY = d * ih * 0.05;
        group.getTransforms().add(new javafx.scene.transform.Translate(-noseX, -noseY, 0));

        return group;
    }

    /**
     * Создаёт детализированный меш закруглённой вершины (nose radius).
     * 16 сегментов по дуге, 4 слоя по высоте.
     */
    private MeshView createNoseRadiusMesh(double radius, double height) {
        TriangleMesh mesh = new TriangleMesh();
        int segments = 16;
        int layers = 4;
        float r = (float) Math.max(1.0, radius);
        float h = (float) (height * 0.5);

        // Вершины: половина окружности (0..PI), layered
        for (int layer = 0; layer <= layers; layer++) {
            float z = -h + (float) layer * 2.0f * h / (float) layers;
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * (double) i / (double) segments;
                float x = r * (float) Math.cos(angle);
                float y = r * (float) Math.sin(angle);
                mesh.getPoints().addAll(x, y, z);
            }
        }

        // Текстурные координаты
        mesh.getTexCoords().addAll(0f, 0f);

        // Индексы (два треугольника на квад)
        for (int layer = 0; layer < layers; layer++) {
            for (int i = 0; i < segments; i++) {
                int a = layer * (segments + 1) + i;
                int b = a + 1;
                int c = (layer + 1) * (segments + 1) + i;
                int d = c + 1;
                mesh.getFaces().addAll(a, 0, b, 0, c, 0);
                mesh.getFaces().addAll(b, 0, d, 0, c, 0);
            }
        }

        // Торцы (один треугольник на начало и конец)
        int tip = 0;
        int tipEnd = layers * (segments + 1);
        for (int i = 0; i < segments; i++) {
            mesh.getFaces().addAll(tip + i, 0, tip + i + 1, 0, tipEnd + i, 0);
            mesh.getFaces().addAll(tipEnd + i + 1, 0, tipEnd + i, 0, tip + i + 1, 0);
        }

        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        return view;
    }

    private Group buildGroovingTool3d(CncToolDefinition toolDefinition, GCodeMoveData move) {
        double width = toolDefinition != null && toolDefinition.getCutWidth() > 0.0 ? Math.max(3.0, toolDefinition.getCutWidth() * 2.0) : 7.0;
        double length = toolDefinition != null && toolDefinition.getPlateLength() > 0.0 ? Math.max(42.0, toolDefinition.getPlateLength() * 3.6) : 58.0;
        double direction = move.endZ() >= move.startZ() ? -1.0 : 1.0;
        Group group = new Group();

        PhongMaterial holderMat = new PhongMaterial(this.darkTheme ? Color.web("#1e293b") : Color.web("#334155"));
        holderMat.setSpecularColor(Color.web("#cbd5e1", 0.3));
        holderMat.setSpecularPower(48.0);
        PhongMaterial bladeMat = new PhongMaterial(Color.web("#64748b"));
        bladeMat.setSpecularColor(Color.web("#e2e8f0", 0.55));
        bladeMat.setSpecularPower(68.0);
        PhongMaterial cuttingMat = new PhongMaterial(Color.web("#f59e0b"));
        cuttingMat.setSpecularColor(Color.web("#fff7ed", 0.62));
        cuttingMat.setSpecularPower(110.0);
        PhongMaterial clampMat = new PhongMaterial(Color.web("#111827"));
        clampMat.setSpecularColor(Color.web("#e5e7eb", 0.35));
        clampMat.setSpecularPower(64.0);
        PhongMaterial screwMat = new PhongMaterial(Color.web("#64748b"));
        screwMat.setSpecularColor(Color.web("#f8fafc", 0.55));
        screwMat.setSpecularPower(90.0);

        // Державка с фасками
        Box holder = new Box(18.0, length, 18.0);
        holder.setTranslateX(38.0);
        holder.setTranslateY(direction * -length * 0.44);
        holder.setMaterial(holderMat);
        group.getChildren().add(holder);

        // Верхняя фаска державки
        Box holderChamfer = new Box(0.6, length, 18.6);
        holderChamfer.setTranslateX(38.0 + 8.7);
        holderChamfer.setTranslateY(direction * -length * 0.44);
        holderChamfer.setMaterial(new PhongMaterial(Color.web("#64748b")));
        group.getChildren().add(holderChamfer);

        // Шейка (neck) — переход от державки к лезвию, скошенная
        Box neck = new Box(26.0, Math.max(3.0, width * 0.6), 7.0);
        neck.setTranslateX(28.0);
        neck.setTranslateY(direction * -4.0);
        neck.setMaterial(bladeMat);
        group.getChildren().add(neck);

        // Основное лезвие
        Box blade = new Box(34.0, Math.max(5.0, width), 8.0);
        blade.setTranslateX(17.0);
        blade.setMaterial(bladeMat);
        group.getChildren().add(blade);

        // Фаска лезвия (светлая кромка)
        Box clamp = new Box(18.0, Math.max(4.0, width * 0.85), 9.5);
        clamp.setTranslateX(19.0);
        clamp.setTranslateY(direction * -Math.max(5.0, width * 0.75));
        clamp.setTranslateZ(1.0);
        clamp.setMaterial(clampMat);
        group.getChildren().add(clamp);

        Cylinder screw = new Cylinder(2.8, 6.0, 32);
        screw.getTransforms().add(new javafx.scene.transform.Rotate(90.0, javafx.scene.transform.Rotate.X_AXIS));
        screw.setTranslateX(20.0);
        screw.setTranslateY(direction * -Math.max(5.0, width * 0.78));
        screw.setTranslateZ(1.0);
        screw.setMaterial(screwMat);
        group.getChildren().add(screw);

        Box bladeEdge = new Box(34.0, Math.max(3.0, width * 0.7), 0.6);
        bladeEdge.setTranslateX(17.0);
        bladeEdge.setTranslateZ(4.3);
        bladeEdge.setMaterial(new PhongMaterial(Color.web("#fbbf24")));
        group.getChildren().add(bladeEdge);

        // Режущая кромка с радиусом
        Box cuttingEdge = new Box(7.0, Math.max(4.0, width * 0.9), 9.0);
        cuttingEdge.setTranslateX(0.0);
        cuttingEdge.setMaterial(cuttingMat);
        group.getChildren().add(cuttingEdge);

        // Блик на кромке
        Box edgeGlint = new Box(6.0, Math.max(2.0, width * 0.5), 3.0);
        edgeGlint.setTranslateX(-0.5);
        edgeGlint.setTranslateZ(5.0);
        PhongMaterial glintMat = new PhongMaterial(Color.web("#ffffff", 0.0));
        glintMat.setSpecularColor(Color.web("#ffffff", 0.8));
        glintMat.setSpecularPower(200.0);
        edgeGlint.setMaterial(glintMat);
        group.getChildren().add(edgeGlint);

        return group;
    }

    private Group buildThreadingTool3d(CncToolDefinition toolDefinition, GCodeMoveData move) {
        double direction = move.endZ() >= move.startZ() ? -1.0 : 1.0;
        Group group = new Group();
        PhongMaterial holderMaterial = new PhongMaterial(this.darkTheme ? Color.web("#334155") : Color.web("#475569"));
        holderMaterial.setSpecularColor(Color.web("#dbeafe", 0.3));
        holderMaterial.setSpecularPower(40.0);
        PhongMaterial edgeMaterial = new PhongMaterial(Color.web("#d4a017"));
        edgeMaterial.setSpecularColor(Color.web("#fff7ed", 0.62));
        edgeMaterial.setSpecularPower(96.0);
        PhongMaterial clampMaterial = new PhongMaterial(Color.web("#111827"));
        clampMaterial.setSpecularColor(Color.web("#e5e7eb", 0.35));
        clampMaterial.setSpecularPower(70.0);
        PhongMaterial screwMaterial = new PhongMaterial(Color.web("#64748b"));
        screwMaterial.setSpecularColor(Color.web("#f8fafc", 0.55));
        screwMaterial.setSpecularPower(90.0);
        // Tapered holder
        Box holder = new Box(18.0, 78.0, 16.0);
        holder.setTranslateX(48.0);
        holder.setTranslateY(direction * -32.0);
        holder.setMaterial(holderMaterial);
        // Edge highlight
        Box holderEdge = new Box(0.6, 78.0, 16.6);
        holderEdge.setTranslateX(48.0 - 8.7);
        holderEdge.setTranslateY(direction * -32.0);
        holderEdge.setMaterial(new PhongMaterial(Color.web("#94a3b8")));
        group.getChildren().addAll(holder, holderEdge);
        // V-shaped threading insert with bevel
        MeshView vInsert = this.createTriangularInsertMesh(30.0, 30.0, 8.0, edgeMaterial);
        vInsert.getTransforms().add(new javafx.scene.transform.Rotate(direction > 0 ? 180.0 : 0.0, javafx.scene.transform.Rotate.Z_AXIS));
        group.getChildren().add(vInsert);
        // V-edge profiles (threading insert shape)
        PhongMaterial vEdgeMat = new PhongMaterial(Color.web("#ef4444"));
        vEdgeMat.setSpecularColor(Color.web("#fecaca", 0.58));
        for (double angle : new double[]{30.0, -30.0}) {
            Box vEdge = new Box(18.0, 3.5, 8.5);
            vEdge.getTransforms().add(new javafx.scene.transform.Rotate(angle, javafx.scene.transform.Rotate.Z_AXIS));
            vEdge.setMaterial(vEdgeMat);
            group.getChildren().add(vEdge);
        }
        Box clamp = new Box(14.0, 5.0, 8.5);
        clamp.setTranslateX(18.0);
        clamp.setTranslateY(direction * -6.0);
        clamp.getTransforms().add(new javafx.scene.transform.Rotate(direction * 12.0, javafx.scene.transform.Rotate.Z_AXIS));
        clamp.setMaterial(clampMaterial);
        group.getChildren().add(clamp);

        Cylinder screw = new Cylinder(2.6, 6.0, 32);
        screw.getTransforms().add(new javafx.scene.transform.Rotate(90.0, javafx.scene.transform.Rotate.X_AXIS));
        screw.setTranslateX(16.0);
        screw.setTranslateY(direction * -4.0);
        screw.setMaterial(screwMaterial);
        group.getChildren().add(screw);
        return group;
    }

    private MeshView createTriangularInsertMesh(double length, double width, double thickness, PhongMaterial material) {
        float l = (float) length;
        float w = (float) (width * 0.5);
        float t = (float) (thickness * 0.5);
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(
                0f, 0f, -t,
                l, -w, -t,
                l, w, -t,
                0f, 0f, t,
                l, -w, t,
                l, w, t);
        mesh.getTexCoords().addAll(0f, 0f);
        mesh.getFaces().addAll(
                0, 0, 1, 0, 2, 0,
                3, 0, 5, 0, 4, 0,
                0, 0, 3, 0, 4, 0,
                0, 0, 4, 0, 1, 0,
                1, 0, 4, 0, 5, 0,
                1, 0, 5, 0, 2, 0,
                2, 0, 5, 0, 3, 0,
                2, 0, 3, 0, 0, 0);
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        view.setMaterial(material);
        return view;
    }

    private Group buildDrillTool3d(CncToolDefinition toolDefinition, GCodeMoveData move) {
        double diameter = toolDefinition != null && toolDefinition.getCutWidth() > 0.0
                ? Math.max(8.0, toolDefinition.getCutWidth() * 2.0)
                : 12.0;
        double length = toolDefinition != null && toolDefinition.getLengthZ() > 0.0
                ? Math.max(70.0, Math.min(140.0, toolDefinition.getLengthZ() * 0.7))
                : 86.0;
        double direction = move.endZ() >= move.startZ() ? -1.0 : 1.0;
        Group group = new Group();
        PhongMaterial steel = new PhongMaterial(Color.web("#94a3b8"));
        steel.setSpecularColor(Color.web("#f8fafc", 0.55));

        // Shank body
        Cylinder body = new Cylinder(diameter * 0.5, length);
        body.setTranslateY(direction * (length * 0.5 + diameter * 0.45));
        body.setMaterial(steel);
        group.getChildren().add(body);

        // Fluted section (slightly smaller diameter)
        Cylinder fluted = new Cylinder(diameter * 0.47, length * 0.65);
        fluted.setTranslateY(direction * (length * 0.5 + diameter * 0.45 - length * 0.175));
        fluted.setMaterial(new PhongMaterial(Color.web("#64748b")));
        group.getChildren().add(fluted);

        // Spiral flute representation (angled boxes along the fluted section)
        PhongMaterial fluteMat = new PhongMaterial(Color.web("#475569"));
        for (int side : new int[]{-1, 1}) {
            for (int seg = 0; seg < 3; seg++) {
                double segOffset = seg * length * 0.2 - length * 0.1;
                Box flute = new Box(diameter * 0.15, length * 0.22, 1.2);
                flute.setTranslateX(side * diameter * 0.36);
                flute.setTranslateY(direction * (length * 0.44 + diameter * 0.45 + segOffset));
                flute.getTransforms().add(new javafx.scene.transform.Rotate(
                        side * 12.0 * seg, javafx.scene.transform.Rotate.Z_AXIS));
                flute.setMaterial(fluteMat);
                group.getChildren().add(flute);
            }
        }

        // Pointed tip — cone with 118° point angle
        double tipHeight = diameter * 0.55;
        PhongMaterial tipMat = new PhongMaterial(Color.web("#ef4444"));
        tipMat.setSpecularColor(Color.web("#fca5a5", 0.4));
        // Use a cone approximation: stack of cylinders decreasing in size
        int tipSegments = 6;
        for (int i = 0; i < tipSegments; i++) {
            double t = (double) i / tipSegments;
            double r = diameter * 0.45 * (1.0 - t);
            double zOffset = direction * (length + diameter * 0.45 + i * (tipHeight / tipSegments));
            if (r > 0.5) {
                Cylinder seg = new Cylinder(r, tipHeight / tipSegments + 0.1);
                seg.setTranslateY(direction * (length * 0.5 + diameter * 0.45 - length * 0.5 + zOffset * 0.5));
                seg.setMaterial(tipMat);
                group.getChildren().add(seg);
            }
        }

        // Chisel edge (flat at tip)
        Box chiselEdge = new Box(diameter * 0.85, Math.max(2.0, diameter * 0.15), diameter * 0.15);
        chiselEdge.setTranslateY(direction * (length * 0.5 + diameter * 0.45 + tipHeight));
        chiselEdge.setMaterial(new PhongMaterial(Color.web("#f97316")));
        chiselEdge.getTransforms().add(new javafx.scene.transform.Rotate(18.0, javafx.scene.transform.Rotate.Z_AXIS));
        group.getChildren().add(chiselEdge);

        return group;
    }

    private CncToolDefinition findToolDefinition(GCodeMoveData move) {
        if (move == null) {
            return null;
        }
        return this.findToolDefinition(move.toolNumber(), move.edgeNumber());
    }

    private CncToolDefinition findToolDefinition(int toolNumber, int edgeNumber) {
        if (toolNumber <= 0) {
            return null;
        }
        // The stock solver and the visual tool must share this exact snapshot,
        // including unsaved library edits. Reloading disk here can select a
        // different radius/type from the one used by the compensated path.
        List<CncToolDefinition> tools;
        if (this.simulationContext != null) tools=this.simulationContext.getTools();
        else {
            if (this.cachedTools == null || this.cachedTools.isEmpty()) this.cachedTools=ToolLibraryStore.load();
            tools=this.cachedTools;
        }
        int safeEdge = Math.max(1, edgeNumber);
        for (CncToolDefinition tool : tools) {
            if (tool.getToolNumber() == toolNumber && tool.getEdgeNumber() == safeEdge) {
                return tool;
            }
        }
        for (CncToolDefinition tool : tools) {
            if (tool.getLocation() == toolNumber && tool.getEdgeNumber() == safeEdge) {
                return tool;
            }
        }
        return null;
    }

    private void drawCycleActivePath(
            GraphicsContext gc,
            SinuTrainSideViewPainter.Layout layout,
            List<GCodeMoveData> pathMoves,
            int activeIndex
    ) {
        if (!this.showToolpath || layout == null || pathMoves == null || pathMoves.isEmpty()) {
            return;
        }
        int end = Math.max(0, Math.min(activeIndex, pathMoves.size() - 1));
        for (int i = 0; i <= end; i++) {
            GCodeMoveData move = pathMoves.get(i);
            double sx = layout.toScreenZ(move.startZ());
            double sy = layout.toScreenR(LatheMeshBuilder.toRadius(move.startX()));
            double ex = layout.toScreenZ(move.endZ());
            double ey = layout.toScreenR(LatheMeshBuilder.toRadius(move.endX()));
            gc.setLineWidth(i == end ? 2.8 : 1.6);
            if (move.rapid()) {
                gc.setStroke(Color.web(MainController.SINUMERIK_RAPID_COLOR, i == end ? 0.95 : 0.65));
                gc.setLineDashes(8.0, 5.0);
            } else {
                gc.setStroke(i == end ? Color.web("#f97316") : Color.web(MainController.SINUMERIK_FEED_COLOR, 0.88));
                gc.setLineDashes();
            }
            gc.strokeLine(sx, sy, ex, ey);
            gc.setLineDashes();
            if (this.shouldDrawTrajectoryPoint(pathMoves, i)) {
                gc.setFill(i == end ? Color.web("#f97316")
                        : SimulationGcodeOverlay.pointColor(this.darkTheme, move.rapid()));
                gc.fillOval(ex - 3.0, ey - 3.0, 6.0, 6.0);
                gc.setStroke(Color.web("#22d3ee"));
                gc.strokeOval(ex - 4.0, ey - 4.0, 8.0, 8.0);
            }
        }
    }

    private void drawCycleTool(
            GraphicsContext gc,
            SinuTrainSideViewPainter.Layout layout,
            List<GCodeMoveData> pathMoves,
            int activeIndex
    ) {
        if (layout == null || pathMoves == null || pathMoves.isEmpty() || activeIndex < 0) {
            return;
        }
        GCodeMoveData move = pathMoves.get(Math.min(activeIndex, pathMoves.size() - 1));
        double localT = Math.max(0.0, Math.min(1.0, this.cycleProgress - Math.floor(this.cycleProgress)));
        double z = move.startZ() + (move.endZ() - move.startZ()) * localT;
        double x = move.startX() + (move.endX() - move.startX()) * localT;
        double sx = layout.toScreenZ(z);
        double sy = layout.toScreenR(Math.max(0.0, LatheMeshBuilder.toRadius(x)));
        double direction = move.endZ() >= move.startZ() ? -1.0 : 1.0;
        double holderW = 72.0;
        double holderH = 16.0;
        double holderX = sx + direction * 26.0 - (direction < 0 ? holderW : 0.0);
        double holderY = sy - 54.0;
        gc.setGlobalAlpha(0.96);
        gc.setFill(Color.web("#1e293b", this.darkTheme ? 0.94 : 0.82));
        gc.fillRoundRect(holderX, holderY, holderW, holderH, 3.0, 3.0);
        gc.setStroke(Color.web("#93c5fd"));
        gc.setLineWidth(2.0);
        gc.strokeRoundRect(holderX, holderY, holderW, holderH, 3.0, 3.0);
        double insertX = sx + direction * 8.0;
        double insertY = sy - 16.0;
        gc.setStroke(Color.web("#60a5fa", 0.75));
        gc.strokeLine(holderX + (direction > 0 ? 0.0 : holderW), holderY + holderH / 2.0, insertX, insertY);
        this.drawCycleInsert(gc, insertX, insertY);
        gc.setGlobalAlpha(1.0);
    }

    private void drawCycleInsert(GraphicsContext gc, double sx, double sy) {
        double s = 14.0;
        double[] xs = {sx, sx + s * 0.55, sx, sx - s * 0.55};
        double[] ys = {sy - s * 0.55, sy, sy + s * 0.55, sy};
        gc.setFill(Color.web("#fde047"));
        gc.fillPolygon(xs, ys, 4);
        gc.setStroke(Color.web("#0f172a"));
        gc.setLineWidth(1.4);
        gc.strokePolygon(xs, ys, 4);
        gc.setFill(Color.web("#ef4444"));
        gc.fillOval(sx - 2.2, sy - 2.2, 4.4, 4.4);
    }

    private void handleCycleMouse(MouseEvent event, boolean click) {
        if (this.cycleLayout == null) {
            return;
        }
        GCodeMoveData hit = this.pickCyclePoint(event.getX(), event.getY());
        if (click) {
            if (hit != null) {
                this.toggleSimulationPointSelection(hit);
            } else {
                this.gcodeOverlay.clearSelection(this.darkTheme, this.showToolpath);
                this.clearSimulationEditorSelection();
            }
            this.scheduleCycleCanvasRedraw();
            return;
        }
        if (hit != this.halfCutHoverMove) {
            this.halfCutHoverMove = hit;
            this.scheduleCycleCanvasRedraw();
        }
        if (this.coordLabel != null && hit != null) {
            this.coordLabel.setText(LatheMeshBuilder.formatCoordLine(hit.endX(), hit.endZ()) + "  ·  N" + hit.sourceLine());
        }
    }

    private GCodeMoveData pickCyclePoint(double mouseX, double mouseY) {
        if (this.cycleLayout == null) {
            return null;
        }
        GCodeMoveData best = null;
        double bestDist = 10.0;
        for (GCodeMoveData move : this.cycleMoves()) {
            double ex = this.cycleLayout.toScreenZ(move.endZ());
            double ey = this.cycleLayout.toScreenR(LatheMeshBuilder.toRadius(move.endX()));
            double dist = Math.hypot(mouseX - ex, mouseY - ey);
            if (dist < bestDist) {
                bestDist = dist;
                best = move;
            }
        }
        return best;
    }

    /** Подсветка точки из редактора (без изменения 3D-модели). */
    public void syncEditorLine(int sourceLine) {
        if (sourceLine <= 0) {
            return;
        }
        if (this.isCycleMode()) {
            this.halfCutHoverMove = null;
            for (GCodeMoveData move : this.cycleMoves()) {
                if (move.sourceLine() == sourceLine) {
                    this.halfCutHoverMove = move;
                    break;
                }
            }
            this.scheduleCycleCanvasRedraw();
            return;
        }
        if (this.is2dCanvasMode()) {
            this.halfCutHoverMove = null;
            for (GCodeMoveData move : this.moves()) {
                if (move.sourceLine() == sourceLine) {
                    this.halfCutHoverMove = move;
                    break;
                }
            }
            this.schedule2dCanvasRedraw();
            return;
        }
        if (this.viewMode != ViewMode.VIEW_3D && !this.isSideGraphMode()) {
            return;
        }
        for (GCodeMoveData move : this.moves()) {
            if (move.sourceLine() == sourceLine) {
                if (this.showToolpath) {
                    this.gcodeOverlay.selectSingle(move, this.darkTheme, true);
                }
                if (this.coordLabel != null) {
                    this.coordLabel.setText(
                            LatheMeshBuilder.formatCoordLine(move.endX(), move.endZ()) + "  ·  N" + sourceLine);
                }
                break;
            }
        }
    }

    private void prepareOrbitGesture(MouseEvent event) {
        this.orbitPressSceneX = event.getSceneX();
        this.orbitPressSceneY = event.getSceneY();
        if (event.isShiftDown()) {
            double modelX = 0.0;
            double modelY = this.partCenterZMm;
            double modelZ = 0.0;
            PickResult pick = event.getPickResult();
            if (pick != null && pick.getIntersectedNode() != null && this.isOrbitPickableNode(pick.getIntersectedNode())) {
                Point3D hit = pick.getIntersectedPoint();
                if (hit != null) {
                    Point3D inSpin = this.spinGroup.sceneToLocal(hit);
                    if (inSpin != null
                            && Double.isFinite(inSpin.getX())
                            && Double.isFinite(inSpin.getY())
                            && Double.isFinite(inSpin.getZ())) {
                        modelX = inSpin.getX();
                        modelY = inSpin.getY();
                        modelZ = inSpin.getZ();
                    }
                }
            } else {
                double zMin = this.partCenterZMm - this.partHalfSpanZMm;
                double zMax = this.partCenterZMm + this.partHalfSpanZMm;
                javafx.geometry.Point2D local = this.subScene.sceneToLocal(event.getSceneX(), event.getSceneY());
                double width = Math.max(1.0, this.subScene.getWidth());
                double fraction = Math.max(0.0, Math.min(1.0, local.getX() / width));
                modelY = zMin + fraction * Math.max(1.0, zMax - zMin);
                modelX = this.radiusOnProfileAtZ(modelY);
                modelZ = 0.0;
            }
            Point3D pivot = this.modelToContentPoint(modelX, modelY, modelZ);
            this.orbitCamera.setPivotWithScreenAnchor(
                    pivot.getX(),
                    pivot.getY(),
                    pivot.getZ(),
                    this.subScene,
                    this.pivotMarker,
                    event.getSceneX(),
                    event.getSceneY());
            this.orbitPivotAnchored = true;
            this.syncPivotMarkerFromCamera();
        }
        this.orbitCamera.beginOrbitGesture(
                this.partMaxRadiusMm,
                this.partCenterZMm,
                this.partCenterRadiusMm,
                this.partCenterZMm,
                this.partHalfSpanZMm,
                this.partMaxRadiusMm);
    }

    private double radiusOnProfileAtZ(double zMm) {
        List<double[]> profile = this.profileForHalfCut();
        if (profile.size() < 2) {
            return this.partMaxRadiusMm * 0.5;
        }
        double bestR = profile.get(0)[1];
        for (int i = 0; i < profile.size() - 1; i++) {
            double z0 = profile.get(i)[0];
            double r0 = profile.get(i)[1];
            double z1 = profile.get(i + 1)[0];
            double r1 = profile.get(i + 1)[1];
            if (zMm >= Math.min(z0, z1) - 0.02 && zMm <= Math.max(z0, z1) + 0.02) {
                if (Math.abs(z1 - z0) < 0.02) {
                    return Math.max(r0, r1);
                }
                double t = (zMm - z0) / (z1 - z0);
                return r0 + t * (r1 - r0);
            }
            bestR = Math.max(bestR, r0);
        }
        bestR = Math.max(bestR, profile.get(profile.size() - 1)[1]);
        return bestR;
    }

    /** Якорь вращения на детали (камера и pan не трогаются). */
    private void setSpinAnchor(double modelX, double modelY, double modelZ) {
        this.spinAnchorX = modelX;
        this.spinAnchorY = modelY;
        this.spinAnchorZ = modelZ;
        this.modelSpinYawDeg = 0.0;
        this.modelSpinPitchDeg = 0.0;
        this.orbitPickLocal = this.modelToContentPoint(modelX, modelY, modelZ);
        this.applySpinGroupTransforms();
    }

    private void applySpinGroupTransforms() {
        this.spinGroup.getTransforms().setAll(
                new javafx.scene.transform.Translate(this.spinAnchorX, this.spinAnchorY, this.spinAnchorZ),
                new javafx.scene.transform.Rotate(this.modelSpinYawDeg, javafx.scene.transform.Rotate.Y_AXIS),
                new javafx.scene.transform.Rotate(this.modelSpinPitchDeg, javafx.scene.transform.Rotate.X_AXIS),
                new javafx.scene.transform.Translate(-this.spinAnchorX, -this.spinAnchorY, -this.spinAnchorZ));
    }

    private void resetSpinOrientation() {
        this.modelSpinYawDeg = 0.0;
        this.modelSpinPitchDeg = 0.0;
        this.applySpinGroupTransforms();
    }

    /**
     * modelRoot: X/Z — радиус, Y — ось Z программы; rotateGroup — после поворота orientationGroup на 90°.
     */
    private Point3D modelToContentPoint(double modelX, double modelY, double modelZ) {
        Point3D scene = this.spinGroup.localToScene(new Point3D(modelX, modelY, modelZ), true);
        if (scene == null) {
            return new Point3D(0.0, 0.0, 0.0);
        }
        Point3D local = this.orbitCamera.getContentRoot().sceneToLocal(scene);
        if (local == null || !Double.isFinite(local.getX())) {
            return new Point3D(0.0, 0.0, 0.0);
        }
        return local;
    }

    private void setOrbitPivotAtModelPoint(double modelX, double modelY, double modelZ, boolean quiet) {
        Point3D pivot = this.modelToContentPoint(modelX, modelY, modelZ);
        if (quiet) {
            this.orbitCamera.setPivotQuiet(pivot.getX(), pivot.getY(), pivot.getZ());
        } else {
            this.orbitCamera.setPivot(pivot.getX(), pivot.getY(), pivot.getZ());
        }
        this.syncPivotMarkerFromCamera();
    }

    private void syncPivotMarkerFromCamera() {
        Point3D pivot = this.orbitCamera.pivotLocalPoint();
        this.pivotMarker.setTranslateX(pivot.getX());
        this.pivotMarker.setTranslateY(pivot.getY());
        this.pivotMarker.setTranslateZ(pivot.getZ());
    }

    private void updatePivotGizmo() {
        if (this.viewMode != ViewMode.VIEW_3D) {
            return;
        }
        this.syncPivotMarkerFromCamera();
        double scale = Math.max(0.35, Math.min(1.8, this.partMaxRadiusMm / 55.0));
        this.pivotGizmo.setPosition(0.0, 0.0, 0.0);
        this.pivotGizmo.setScale(scale);
    }

    private void refreshPartBoundsFromProfile() {
        double maxR = 10.0;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double[] point : this.profileForDisplay()) {
            maxR = Math.max(maxR, point[1]);
            minZ = Math.min(minZ, point[0]);
            maxZ = Math.max(maxZ, point[0]);
        }
        if (!Double.isFinite(minZ)) {
            return;
        }
        this.updatePartBounds(maxR, minZ, maxZ);
    }

    private void updatePartBounds(double maxR, double minZ, double maxZ) {
        this.partMaxRadiusMm = Math.max(10.0, maxR);
        this.partCenterZMm = (minZ + maxZ) / 2.0;
        this.partHalfSpanZMm = Math.max(10.0, (maxZ - minZ) / 2.0);
        this.partCenterRadiusMm = 0.0;
    }

    private boolean isOrbitPickableNode(javafx.scene.Node node) {
        if (node == null || LatheMachineVisual3d.isMachineNode(node)) {
            return false;
        }
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (current == this.pivotGizmo.getRoot()) {
                return false;
            }
            Object tag = current.getUserData();
            if (FINISHED_PART_TAG.equals(tag) || STOCK_BLANK_TAG.equals(tag)) {
                return true;
            }
            if (current instanceof Cylinder cylinder && STOCK_BLANK_TAG.equals(cylinder.getUserData())) {
                return true;
            }
        }
        return false;
    }

    private void installHalfCutMouseHandlers() {
        this.halfCutCanvas.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> event.consume());
        this.halfCutCanvas.setOnMousePressed(event -> {
            this.dragStartX = event.getX();
            this.dragStartY = event.getY();
            if (this.measureMode && event.getButton() == MouseButton.PRIMARY) {
                int dragIndex = this.measurePointIndexAt(event.getSceneX(), event.getSceneY());
                if (dragIndex >= 0) {
                    // Захват точки A/B/C в «Срезе» — перетаскивание вместо панорамирования.
                    this.measureDragIndex = dragIndex;
                    this.measureHoverIndex = dragIndex;
                    this.halfCutCanvas.setCursor(javafx.scene.Cursor.CLOSED_HAND);
                    this.refreshMeasureOverlay();
                    event.consume();
                    return;
                }
            }
            if (event.getButton() == MouseButton.PRIMARY || event.getButton() == MouseButton.SECONDARY) {
                this.panDrag = true;
                if (event.getButton() == MouseButton.PRIMARY) {
                    this.clickCandidate = true;
                }
                event.consume();
            }
        });
        this.halfCutCanvas.setOnMouseDragged(event -> {
            if (this.measureDragIndex >= 0) {
                this.dragMeasurePointTo(
                        this.measurePointFromLayout(event.getX(), event.getY(), this.halfCutLayout));
                event.consume();
                return;
            }
            double dx = event.getX() - this.dragStartX;
            double dy = event.getY() - this.dragStartY;
            if (Math.hypot(dx, dy) > 4.0) {
                this.clickCandidate = false;
            }
            if (this.panDrag) {
                this.halfCutPanX += dx;
                this.halfCutPanY += dy;
                this.dragStartX = event.getX();
                this.dragStartY = event.getY();
                this.schedule2dCanvasRedraw();
                event.consume();
            }
        });
        this.halfCutCanvas.setOnMouseReleased(event -> {
            if (this.measureDragIndex >= 0 && event.getButton() == MouseButton.PRIMARY) {
                this.measureDragIndex = -1;
                this.panDrag = false;
                this.clickCandidate = false;
                this.updateMeasureCursor(this.halfCutCanvas, event.getSceneX(), event.getSceneY());
                this.refreshMeasureOverlay();
                event.consume();
                return;
            }
            if (this.clickCandidate && event.getButton() == MouseButton.PRIMARY) {
                if (this.measureMode) {
                    this.handleMeasureClick(event);
                } else {
                    this.handleHalfCutClick(event);
                }
            }
            this.panDrag = false;
            this.clickCandidate = false;
        });
        this.halfCutCanvas.setOnMouseMoved(event -> {
            if (this.measureMode) {
                this.updateMeasureCursor(this.halfCutCanvas, event.getSceneX(), event.getSceneY());
            }
            this.handleHalfCutMouse(event);
        });
    }

    private void handleHalfCutMouse(MouseEvent event) {
        if (this.halfCutLayout == null) {
            return;
        }
        GCodeMoveData hit = this.pickHalfCutPoint(event.getX(), event.getY(), this.halfCutPickRadiusPx(9.0));
        if (hit == this.halfCutHoverMove) {
            return;
        }
        this.halfCutHoverMove = hit;
        this.schedule2dCanvasRedraw();
        if (this.coordLabel != null && hit != null) {
            this.coordLabel.setText(LatheMeshBuilder.formatCoordLine(hit.endX(), hit.endZ()) + "  ·  N" + hit.sourceLine());
        }
    }

    private void handleHalfCutClick(MouseEvent event) {
        if (this.halfCutLayout == null) {
            return;
        }
        GCodeMoveData hit = this.pickHalfCutPoint(event.getX(), event.getY(), this.halfCutPickRadiusPx(11.0));
        if (hit != null) {
            this.toggleSimulationPointSelection(hit);
        }
    }

    private double halfCutPickRadiusPx(double baseRadius) {
        return Math.max(baseRadius, Math.min(34.0, baseRadius + this.halfCutZoom * 2.0));
    }

    private GCodeMoveData pickHalfCutPoint(double mouseX, double mouseY, double radius) {
        GCodeMoveData best = null;
        double bestDist = radius;
        int index = 0;
        for (GCodeMoveData move : this.displayMoves) {
            if (move.arcSegment() && !move.arcEnd()) {
                index++;
                continue;
            }
            double ex = this.halfCutLayout.toScreenZ(move.endZ());
            double ey = this.halfCutLayout.toScreenR(LatheMeshBuilder.toRadius(move.endX()));
            double dist = Math.hypot(mouseX - ex, mouseY - ey);
            if (dist <= bestDist) {
                bestDist = dist;
                best = move;
            }
            index++;
        }
        return best;
    }

    private void toggleSimulationPointSelection(GCodeMoveData hit) {
        if (hit == null || hit.sourceLine() <= 0) {
            return;
        }
        if (hit.sourceLine() == this.simulationSelectedSourceLine) {
            this.simulationSelectedSourceLine = -1;
            this.gcodeOverlay.clearSelection(this.darkTheme, this.showToolpath);
            this.clearSimulationEditorSelection();
            if (this.coordLabel != null) {
                this.coordLabel.setText(I18n.text("sim.038"));
            }
            return;
        }
        this.simulationSelectedSourceLine = hit.sourceLine();
        this.gcodeOverlay.selectSingle(hit, this.darkTheme, this.showToolpath);
        this.selectSourceLine(hit.sourceLine(), hit.endX(), hit.endZ());
    }

    private void clearSimulationEditorSelection() {
        if (this.sourceLineConsumer != null) {
            this.sourceLineConsumer.accept(0);
        }
    }

    private void selectSourceLine(int line, double x, double z) {
        if (this.coordLabel != null) {
            this.coordLabel.setText(LatheMeshBuilder.formatCoordLine(x, z) + "  ·  N" + line);
        }
        if (this.sourceLineConsumer != null && line > 0) {
            this.sourceLineConsumer.accept(line);
        }
    }

    private void updateSelectionStatus() {
        if (this.coordLabel == null) {
            return;
        }
        this.coordLabel.setText(this.gcodeOverlay.formatSelectionSummary(
                this.gcodeOverlay.getSelectedMoves(),
                this.gcodeOverlay.getHoveredMove()));
    }

    private void showMeshError(String message) {
        this.lastMeshError = message;
        if (this.errorLabel != null) {
            this.errorLabel.setText(message);
            this.errorLabel.setVisible(true);
            this.errorLabel.setManaged(true);
            this.errorLabel.toFront();
        }
        if (this.subScene != null && !this.is2dCanvasMode()) {
            this.subScene.setVisible(true);
            this.subScene.setMouseTransparent(false);
        }
        if (this.statusLabel != null) {
            this.statusLabel.setText(I18n.text("sim.039"));
        }
        this.restoreViewportLayering();
    }

    void showExternalError(String message) {
        this.hideLoadingOverlay();
        this.showMeshError(message);
    }

    private boolean hasFinishedPartNode() {
        return this.modelRoot != null
                && this.modelRoot.getChildren().stream().anyMatch(node -> FINISHED_PART_TAG.equals(node.getUserData()));
    }

    private boolean hasStockOrFinishedNode() {
        return this.modelRoot != null
                && this.modelRoot.getChildren().stream().anyMatch(node -> {
                    Object tag = node.getUserData();
                    return STOCK_BLANK_TAG.equals(tag) || FINISHED_PART_TAG.equals(tag);
                });
    }

    private boolean isRenderableNode(javafx.scene.Node node) {
        if (node == null) {
            return false;
        }
        javafx.geometry.Bounds bounds = node.getBoundsInLocal();
        if (bounds == null
                || !Double.isFinite(bounds.getMinX())
                || !Double.isFinite(bounds.getMinY())
                || !Double.isFinite(bounds.getMinZ())
                || !Double.isFinite(bounds.getMaxX())
                || !Double.isFinite(bounds.getMaxY())
                || !Double.isFinite(bounds.getMaxZ())) {
            return false;
        }
        double span = Math.abs(bounds.getWidth()) + Math.abs(bounds.getHeight()) + Math.abs(bounds.getDepth());
        return span > 0.01;
    }

    private void restoreViewportLayering() {
        if (this.subScene != null) {
            boolean showSubScene = !this.is2dCanvasMode();
            this.subScene.setVisible(showSubScene);
            this.subScene.setMouseTransparent(!showSubScene);
            if (showSubScene) {
                this.subScene.toBack();
            }
        }
        if (this.sideViewLabelsOverlay != null) {
            this.sideViewLabelsOverlay.getCanvas().setMouseTransparent(true);
            this.sideViewLabelsOverlay.getCanvas().toFront();
        }
        if (this.halfCutCanvas != null) {
            this.halfCutCanvas.toFront();
        }
        if (this.cycleCanvas != null) {
            this.cycleCanvas.toFront();
        }
        if (this.gcodeOverlay != null) {
            Canvas overlayCanvas = this.gcodeOverlay.getCanvas();
            overlayCanvas.setMouseTransparent(true);
            if (overlayCanvas.isVisible()) {
                overlayCanvas.toFront();
            }
        }
        if (this.measureCanvas != null && this.measureCanvas.isVisible()) {
            this.measureCanvas.setMouseTransparent(true);
            this.measureCanvas.toFront();
        }
        if (this.cycleFpsLabel != null && this.cycleFpsLabel.isVisible()) {
            this.cycleFpsLabel.toFront();
        }
        if (this.cycleToolLabel != null && this.cycleToolLabel.isVisible()) {
            this.cycleToolLabel.toFront();
        }
        if (this.measureReadoutLabel != null && this.measureReadoutLabel.isVisible()) {
            this.measureReadoutLabel.toFront();
        }
        if (this.cycleControls != null) {
            this.cycleControls.toFront();
        }
        if (this.errorLabel != null && this.errorLabel.isVisible()) {
            this.errorLabel.toFront();
        }
        this.ensureLoadingOnTop();
    }

    private void hideMeshError() {
        if (this.errorLabel != null) {
            this.errorLabel.setVisible(false);
            this.errorLabel.setManaged(false);
        }
        if (this.viewMode == ViewMode.VIEW_3D && this.subScene != null) {
            this.subScene.setVisible(true);
            this.subScene.setMouseTransparent(false);
        }
        this.restoreViewportLayering();
    }

    private void refreshMissingToolNotice() {
        var missing = new java.util.LinkedHashSet<String>();
        var nominal = new java.util.LinkedHashSet<String>();
        boolean boreNotSet = false;
        boolean automaticBore = false;
        for (SimulationContext setup = this.simulationContext; setup != null; setup = setup.getPrecedingSetup()) {
            var envelope = new com.sergey.pisarev.service.LatheCuttingEnvelope(setup);
            var boring = new com.sergey.pisarev.service.BoringPassAnalysis(setup);
            boolean hasBore = setup.getWorkpiece()!=null && setup.getWorkpiece().getInitialBoreDiameterMm()>0;
            automaticBore |= hasBore && setup.getWorkpiece().isBoreFromProgram();
            boreNotSet |= boring.hasPasses() && !hasBore;
            for (var move : setup.getContourMoves()) {
                if (!move.rapid() && move.toolNumber() > 0 && envelope.activeToolAt(move.sourceLine()) == null) {
                    String name="T" + move.toolNumber() + " D" + move.edgeNumber();
                    if (hasBore && boring.isNominal(move)) nominal.add(name);
                    else missing.add(name);
                }
            }
        }
        this.missingToolNotice = missing.isEmpty() ? "" : I18n.format("sim.notice.missing", String.join(", ", missing));
        if (!nominal.isEmpty()) this.missingToolNotice += I18n.format("sim.notice.nominal", String.join(", ", nominal));
        if (boreNotSet) this.missingToolNotice += I18n.text("sim.notice.boreunset");
        if (automaticBore) this.missingToolNotice += I18n.text("sim.notice.boreauto");
        if (this.statusLabel!=null) this.statusLabel.setTooltip(this.missingToolNotice.isEmpty() ? null
                : new javafx.scene.control.Tooltip(this.missingToolNotice
                + (automaticBore ? I18n.text("sim.notice.boreauto.tip") : "")
                + I18n.text("sim.notice.missing.tip")));
    }

    private void updateStatus() {
        if (this.statusLabel == null) {
            return;
        }
        String mode = switch (this.viewMode) {
            case VIEW_3D -> I18n.text("sim.040");
            case CYCLE -> I18n.text("sim.041");
            case HALF_CUT -> I18n.text("sim.042");
            case SIDE_GRAPH -> I18n.text("sim.043");
        };
        if (this.lastMeshError != null && this.viewMode == ViewMode.VIEW_3D) {
            return;
        }
        WorkpieceDefinition workpiece = this.simulationContext.getWorkpiece();
        String blank = workpiece != null && workpiece.isValid()
                ? workpiece.describeSize() + I18n.text("sim.044")
                    + String.format(Locale.US, " · L %.3f · Z [%.3f; %.3f]",
                            workpiece.getLengthMm(), workpiece.getZMin(), workpiece.getZMax())
                : I18n.text("sim.045");
        int activeOffset = this.simulationContext.getActiveWorkOffsetCode();
        String offsetText = activeOffset >= 54 ? " · G" + activeOffset : "";
        String setup = !this.hasSetupSequence() ? "" : !this.isCycleMode() ? I18n.text("sim.setup.result")
                : this.turnoverPreparing ? I18n.text("sim.setup.preparing") + " · "
                : this.turnoverProgress >= 0 ? I18n.text("sim.setup.turning.status")
                : I18n.format("sim.setup.side", this.displayedSetupSide());
        if (this.cycleToolLabel != null && (this.turnoverPreparing || this.turnoverProgress >= 0))
            this.cycleToolLabel.setText(this.turnoverPreparing ? I18n.text("sim.setup.preparing") : I18n.text("sim.setup.turning"));
        String preform = this.simulationContext.getPreformAllowanceMm()>0
                ? I18n.format("sim.preform", this.simulationContext.getPreformAllowanceMm()) : "";
        this.statusLabel.setText(this.missingToolNotice + preform + setup + mode + " · " + blank + offsetText + " · F"
                + this.simulationContext.getFeedOverridePercent() + " S"
                + this.simulationContext.getSpindleOverridePercent());
    }

    private void updateViewModeButtons() {
        if (this.view3dButton == null || this.halfCutButton == null) {
            return;
        }
        this.view3dButton.getStyleClass().remove("simulation3d-mode-active");
        this.halfCutButton.getStyleClass().remove("simulation3d-mode-active");
        if (this.cycleButton != null) {
            this.cycleButton.getStyleClass().remove("simulation3d-mode-active");
        }
        if (this.sideViewButton != null) {
            this.sideViewButton.getStyleClass().remove("simulation3d-mode-active");
        }
        switch (this.viewMode) {
            case VIEW_3D -> {
                if (!this.view3dButton.getStyleClass().contains("simulation3d-mode-active")) {
                    this.view3dButton.getStyleClass().add("simulation3d-mode-active");
                }
            }
            case CYCLE -> {
                if (this.cycleButton != null
                        && !this.cycleButton.getStyleClass().contains("simulation3d-mode-active")) {
                    this.cycleButton.getStyleClass().add("simulation3d-mode-active");
                }
            }
            case HALF_CUT -> {
                if (!this.halfCutButton.getStyleClass().contains("simulation3d-mode-active")) {
                    this.halfCutButton.getStyleClass().add("simulation3d-mode-active");
                }
            }
            case SIDE_GRAPH -> {
                if (this.sideViewButton != null
                        && !this.sideViewButton.getStyleClass().contains("simulation3d-mode-active")) {
                    this.sideViewButton.getStyleClass().add("simulation3d-mode-active");
                }
            }
        }
        if (this.toolpathButton != null) {
            if (this.showToolpath && (this.viewMode == ViewMode.VIEW_3D || this.isCycleMode())) {
                if (!this.toolpathButton.getStyleClass().contains("simulation3d-mode-active")) {
                    this.toolpathButton.getStyleClass().add("simulation3d-mode-active");
                }
            } else {
                this.toolpathButton.getStyleClass().remove("simulation3d-mode-active");
            }
        }
        if (this.measureButton != null) {
            if (this.measureMode) {
                if (!this.measureButton.getStyleClass().contains("simulation3d-mode-active")) {
                    this.measureButton.getStyleClass().add("simulation3d-mode-active");
                }
            } else {
                this.measureButton.getStyleClass().remove("simulation3d-mode-active");
            }
        }
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.PERIOD || event.getCode() == KeyCode.DECIMAL) {
            this.onFitView();
            event.consume();
        }
    }

    public void applyTheme(boolean dark) {
        this.darkTheme = dark;
        this.updateTrajectoryPointMaterials();
        this.applyViewportBackground();
        this.applyPartThemeMaterials();
        if (this.isSideGraphMode()) {
            this.syncSideGrid(true);
        }
        this.schedule2dCanvasRedraw();
        this.scheduleCycleCanvasRedraw();
        this.rebuildOverlayMovesCache();
        this.scheduleOverlayRefresh();
    }

    private void updateTrajectoryPointMaterials() {
        this.trajectoryPointMaterial.setDiffuseColor(SimulationGcodeOverlay.pointColor(this.darkTheme, false));
        this.trajectoryRapidPointMaterial.setDiffuseColor(SimulationGcodeOverlay.pointColor(this.darkTheme, true));
    }

    private List<GCodeMoveData> moves() {
        List<GCodeMoveData> contour = this.simulationContext.getContourMoves();
        return contour.isEmpty() ? this.simulationContext.getMoves() : contour;
    }

    private List<GCodeMoveData> graphToolpathMoves() {
        if (this.simulationContext == null) {
            return List.of();
        }
        List<GCodeMoveData> graphMoves = this.simulationContext.getMoves();
        return graphMoves == null || graphMoves.isEmpty() ? List.of() : graphMoves;
    }

    /** Только рабочие ходы нарезки (контур), без G00 и парковок. */
    private List<GCodeMoveData> cuttingToolpathMoves() {
        return StockRemovalMeshBuilder.filterToolpathFor3dOverlay(this.simulationContext);
    }

    /** Ходы для оверлея и вкладки «Срез» (нарезка + подводы в зоне детали). */
    private List<GCodeMoveData> toolpathOverlayMoves() {
        List<double[]> profile = this.cachedFinishedProfile.size() >= 2
                ? this.cachedFinishedProfile
                : List.of();
        List<GCodeMoveData> overlay = StockRemovalMeshBuilder.filterToolpathFor3dOverlay(
                this.simulationContext,
                profile);
        if (!overlay.isEmpty()) {
            return overlay;
        }
        return this.buildDisplayMoves();
    }

    private void syncPivotGizmoVisibility() {
        this.pivotGizmo.setVisible(false);
        this.pivotMarker.setVisible(false);
    }

    private static SimulationContext emptyContext() {
        return new SimulationContext(
                List.of(),
                new MachineConfiguration(
                        MachineConfiguration.ControlSystem.SIEMENS_TURNING,
                        MachineConfiguration.MachineType.GENERIC_LATHE,
                        java.util.Set.of('X', 'Z'),
                        0.0,
                        false),
                null,
                List.of(),
                100,
                100,
                "",
                false);
    }

    private record MarkerBounds(double zMin, double zMax, double maxFeedRadius, double maxRapidRadius) {
    }
}
