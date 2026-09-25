/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javafx.application.Platform
 *  javafx.beans.value.ObservableValue
 *  javafx.fxml.FXML
 *  javafx.geometry.Point2D
 *  javafx.print.PrinterJob
 *  javafx.scene.CacheHint
 *  javafx.scene.Group
 *  javafx.scene.Node
 *  javafx.scene.canvas.Canvas
 *  javafx.scene.canvas.GraphicsContext
 *  javafx.scene.control.Alert
 *  javafx.scene.control.Alert$AlertType
 *  javafx.scene.control.Button
 *  javafx.scene.control.CheckBox
 *  javafx.scene.control.Label
 *  javafx.scene.control.ProgressBar
 *  javafx.scene.control.TextArea
 *  javafx.scene.control.TextField
 *  javafx.scene.control.TextInputDialog
 *  javafx.scene.image.Image
 *  javafx.scene.input.DragEvent
 *  javafx.scene.input.MouseEvent
 *  javafx.scene.input.TransferMode
 *  javafx.scene.layout.AnchorPane
 *  javafx.scene.layout.StackPane
 *  javafx.scene.paint.Color
 *  javafx.scene.paint.Paint
 *  javafx.scene.text.Text
 *  javafx.stage.FileChooser
 *  javafx.stage.FileChooser$ExtensionFilter
 *  javafx.stage.Stage
 */
package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.DrawingEngine;
import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.ToolTypeCatalog;
import com.sergey.pisarev.model.ToolCutDirection;
import com.sergey.pisarev.model.ToolTypeEntry;
import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.MachineConfiguration;
import com.sergey.pisarev.model.ProgramWorkOffsetScan;
import com.sergey.pisarev.model.SimulationContext;
import com.sergey.pisarev.model.WorkOffsetValues;
import com.sergey.pisarev.model.WorkpieceDefinition;
import com.sergey.pisarev.service.GraphWorkOffsetTransform;
import com.sergey.pisarev.service.GCodeExpressionEvaluator;
import com.sergey.pisarev.service.GCodeProgramParser;
import com.sergey.pisarev.service.ProgramDiagnostics;
import com.sergey.pisarev.service.LatheCompensationProcessor;
import com.sergey.pisarev.service.MachineParameterProgram;
import com.sergey.pisarev.service.ProgramFileRole;
import com.sergey.pisarev.service.LatheMeshBuilder;
import com.sergey.pisarev.service.SiemensCycleExpander;
import com.sergey.pisarev.service.SinuTrainCanvasPainter;
import com.sergey.pisarev.service.WorkOffsetStore;
import com.sergey.pisarev.util.AppIconHelper;
import com.sergey.pisarev.util.DialogIcons;
import com.sergey.pisarev.ai.McpServer;
import com.sergey.pisarev.util.I18n;
import com.sergey.pisarev.util.ProgramEditHistoryStore;
import com.sergey.pisarev.util.ToolLibraryStore;
import com.sergey.pisarev.util.UiScale;
import com.sergey.pisarev.util.PortableStorage;
import com.sergey.pisarev.util.ProgramTextFile;
import com.sergey.pisarev.util.UserSettings;
import java.io.File;
import java.net.URL;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.print.PrinterJob;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.Parent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.control.ScrollBar;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

public class MainController {
    private static final DateTimeFormatter HISTORY_HEADER_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final double MIN_CANVAS_ZOOM = 0.1;
    private static final double MAX_CANVAS_ZOOM = 10000.0;
    private static final double MAX_CANVAS_EFFECTIVE_SCALE = 8000.0;
    private static final double GRAPH_HOVER_REDRAW_MIN_DELTA_PX = 3.0;
    private static final String MPF_EXTENSION = ".mpf";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), runnable -> {
        Thread thread = new Thread(runnable, "chekator-worker");
        thread.setDaemon(true);
        return thread;
    });
    /** Карусельный станок выбран в настройках: график поворачивается под него. */
    private boolean verticalLatheGraph;
    /** Подпись процентов рядом с кнопками масштаба окна. */
    @FXML
    private Label uiScaleLabel;
    @FXML
    private AnchorPane anchorPaneProgram;
    @FXML
    private AnchorPane anchorPaneCanvas;
    @FXML
    private StackPane paneCanvas;
    @FXML
    private Button buttonCycleStart;
    @FXML
    private Button buttonCyclePause;
    @FXML
    private Button buttonSimulation3d;
    @FXML
    private Button buttonAppSettings;
    @FXML
    private Button buttonExpandGraph;
    @FXML
    private Button buttonReset;
    @FXML
    private Button buttonStart;
    @FXML
    private Button buttonSingleBlock;
    @FXML
    private Button buttonFitView;
    @FXML
    private javafx.scene.layout.AnchorPane statusPanelPane;
    @FXML
    private javafx.scene.layout.VBox cycleSpeedBlock;
    @FXML
    private Button buttonFrameUp;
    @FXML
    private Button buttonFrameDown;
    @FXML
    private Button buttonProgramFind;
    @FXML
    private FlowPane programToolbarFlow;
    @FXML
    private Button buttonProgramReplace;
    @FXML
    private Button buttonProgramHistory;
    /** Текст, на который меняет «Заменить»; null — ещё не задан (спросим при первом нажатии). */
    private String programReplaceText = null;
    /** Диапазон последнего найденного лупой вхождения; «Заменить» меняет только его. -1 — нет. */
    private int lastSearchMatchStart = -1;
    private int lastSearchMatchEnd = -1;
    /** Минимальная комфортная ширина карточки настроек — по ней считаем число колонок. */
    private static final double SETTINGS_CARD_MIN_WIDTH = 300.0;
    private int settingsCardColumns = -1;
    @FXML
    private CheckBox checkBoxToolRadius;
    @FXML
    private Text textCoordinateX;
    @FXML
    private Text textCoordinateZ;
    @FXML
    private Text textZooming;
    @FXML
    private Text textFrame;
    @FXML
    private Text textFrameCoordinateX;
    @FXML
    private Text textFrameCoordinateZ;
    @FXML
    private TextField turningBlankDiameter;
    @FXML
    private CheckBox checkBoxUseBlankDiameter;
    @FXML
    private Label labelCupOuterDiameter;
    @FXML
    private TextField cupRadius;
    @FXML
    private CheckBox checkBoxUseCupRadius;
    private CheckBox checkBoxEquidistant;
    private ComboBox<String> equidistantRadiusSourceComboBox;
    private TextField equidistantRadius;
    private TextField equidistantAllowance;
    @FXML
    private TextField workOffsetCountField;
    @FXML
    private Label activeWorkOffsetLabel;
    @FXML
    private VBox workOffsetsBox;
    /** Снимок ручных G54… после «Сохранить» — парсер графика не зависит от открытого окна настроек. */
    private final Map<Integer, WorkOffset> graphManualOffsetsSnapshot = new LinkedHashMap<>();
    private boolean workOffsetsUiReady = false;
    private int activeWorkOffsetFromProgram = -1;
    @FXML
    private TextField programSearchField;
    @FXML
    private Label labelProgramTitle;
    @FXML
    private Label labelProgramHint;
    /** Плашка проблем программы справа от заголовка: код + краткий текст, красная или жёлтая. */
    @FXML
    private Label programDiagnosticBadge;
    private List<ProgramDiagnostics.Diagnostic> programDiagnostics = new ArrayList<>();
    /** Кодировка открытого файла программы — в ней же и сохраняем, чтобы текст не портился. */
    private java.nio.charset.Charset programFileCharset = ProgramTextFile.DEFAULT_CHARSET;
    @FXML
    private HBox programEditorBox;
    @FXML
    private Button rightPaneToggleButton;
    @FXML
    private Button sideToggleButton;
    private ProgramGcodeEditor programEditor;
    @FXML
    private ComboBox<Integer> programFontSizeComboBox;
    @FXML
    private SplitPane splitPane;
    @FXML
    private AnchorPane sidebarPane;
    private boolean mainSplitDividerAdjusted;
    /** Пользователь сам подвинул разделитель — дальше его не трогаем. */
    private boolean userMovedMainDivider;
    private boolean mainDividerWatched;
    private Parent settingsPanelRoot;
    private Stage settingsStage;
    /** Снимок настроек на момент открытия окна: «Отмена» возвращает именно его. */
    private SettingsDraft settingsDraftBaseline;
    /** true после «Сохранить» или «Отмена» — не откатывать черновик в onCloseRequest. */
    private boolean settingsPanelCloseHandled = false;
    private TableView<CncToolDefinition> toolsTableView;
    private ObservableList<CncToolDefinition> toolsObservableList;
    private MachineConfiguration.MachineType toolsMachine;
    private final Map<MachineConfiguration.MachineType, List<CncToolDefinition>> toolLibraryDrafts =
            new java.util.EnumMap<>(MachineConfiguration.MachineType.class);
    private Label toolsMachineLabel;
    private Button addToolButton;
    private Button removeToolButton;
    private Button pickToolTypeButton;
    private ThermoOverrideControl feedOverrideDial;
    private ThermoOverrideControl spindleOverrideDial;
    private static final int[] FEED_OVERRIDE_STOPS = {0, 2, 4, 6, 10, 30, 50, 70, 80, 90, 100, 110, 120};
    private static final int[] SPINDLE_OVERRIDE_STOPS = {50, 60, 70, 80, 90, 100, 110, 120};
    private boolean toolsSettingsInitialized;
    @FXML
    private Label labelExecutionStatus;
    @FXML
    private Label labelExecutionProgress;
    @FXML
    private ProgressBar progressExecution;
    @FXML
    private Slider sliderDrawSpeed;
    @FXML
    private RadioMenuItem menuThemeLight;
    @FXML
    private RadioMenuItem menuThemeDark;
    @FXML
    private ToggleGroup themeToggleGroup;
    @FXML
    private RadioMenuItem menuLanguageAuto;
    @FXML
    private RadioMenuItem menuLanguageRu;
    @FXML
    private RadioMenuItem menuLanguageEn;
    @FXML
    private RadioMenuItem menuLanguageUk;
    @FXML
    private ToggleGroup languageToggleGroup;
    /** Пока идёт расстановка галочек в меню языка, обработчики пунктов ничего не делают. */
    private boolean suppressLanguageMenuHandler;
    private AiAppAccess aiAppAccess;
    private Canvas drawingCanvas;
    private Group canvasGroup;
    private GraphicsContext gc;
    private DrawingEngine drawingEngine;
    private double currentZoom = 1.0;
    private double translateX = 0.0;
    private double translateY = 0.0;
    private boolean isDrawing = false;
    private double lastMouseX = 0.0;
    private double lastMouseY = 0.0;
    /** Последняя директория сохранения — сначала Рабочий стол, потом запоминается. */
    private File lastSaveDirectory;
    private double hoverMouseX = Double.NaN;
    private double hoverMouseY = Double.NaN;
    private double lastGraphHoverRedrawPaneX = Double.NaN;
    private double lastGraphHoverRedrawPaneY = Double.NaN;
    private HoverPoint activeHoverPoint = null;
    private volatile int currentExecutionIndex = -1;
    private volatile int selectedStartIndex = -1;
    private volatile int selectedMarkerLine = -1;
    private int programStartMarkerLine = -1;
    private int highlightedProgramLine = -1;
    private ToolMove highlightedProgramMove = null;
    /** Выбор точки из редактора (тёмно-зелёный маркер на графике). */
    private int graphEditorSelectionIndex = -1;
    private double previewLayoutOriginX = 0.0;
    private double previewLayoutOriginY = 0.0;
    private double previewLayoutScale = 1.0;
    private double previewLayoutMinX = 0.0;
    private double previewLayoutMinZ = 0.0;
    private double previewLayoutMaxX = 0.0;
    private double previewLayoutMaxZ = 0.0;
    private double previewLayoutWidth = 1.0;
    private double previewLayoutHeight = 1.0;
    private boolean previewLayoutValid = false;
    private static final double GRAPH_GRID_LABEL_SCALE = 1.32;
    private static final double GRAPH_GRID_FONT = 11.0 * GRAPH_GRID_LABEL_SCALE;
    private static final double GRAPH_GRID_MIN_SPACING_PX = 5.0;
    private static final double GRAPH_GRID_MIN_LABEL_SPACING_PX = 42.0;
    private static final double GRAPH_GRID_VERTICAL_LABEL_SPACING_PX = 28.0;
    private static final double GRAPH_GRID_MIN_STEP = 0.001;
    private static final double SETTINGS_SHORT_FIELD_WIDTH = 220.0;
    private boolean launchFocusPending = false;
    private static final double DEFAULT_ORIGIN_VIEW_HALF_SPAN_MM = 500.0;
    private static final double MIN_ORIGIN_VIEW_HALF_SPAN_MM = 400.0;
    private static final double COORD_TAG_MIN_WIDTH = 120.0;
    private static final double COORD_TAG_HEIGHT = 34.0;
    private static final double COORD_TAG_PAD_X = 8.0;
    private static final Pattern MANDATORY_STOP_PATTERN =
            Pattern.compile("(^|[^A-Z0-9])M0{1,2}(?=$|[^0-9])", Pattern.CASE_INSENSITIVE);
    private int visibleMovesFromIndex = 0;
    private volatile boolean executionRunning = false;
    private volatile boolean cyclePaused = true;
    private boolean fullPreviewVisible = false;
    private boolean previewFromProgramHead = false;
    private boolean pathDisplayActive = false;
    private int pathDisplayStartIndex = 0;
    /** СТАРТ отрисовал траекторию на 100% — можно выбирать точки на графике. */
    private boolean trajectoryPreviewComplete = false;
    /** Траектория на графике — только после СТАРТ / КАДР / ЦИКЛ. */
    private boolean toolpathGraphVisible = false;
    private boolean darkTheme = false;
    private boolean suppressThemeMenuHandler = false;
    private final ProgramEditHistoryStore programEditHistory = new ProgramEditHistoryStore();
    private Stage programHistoryStage;
    private final Simulation3dWindow simulation3dWindow = new Simulation3dWindow();
    private ComboBox<String> controlSystemComboBox;
    private ComboBox<String> machineTypeComboBox;
    private Label axisLabelX;
    private Label axisLabelY;
    private Label axisLabelZ;
    private Label axisLabelA;
    private Label axisLabelB;
    private Label axisLabelC;
    private ListView<HistoryRow> programHistoryListView;
    private Button programHistoryClearButton;
    private HBox programHistoryToolbar;
    private ScrollBar programHistoryExternalScrollBar;
    private ColumnConstraints historyScrollColumn;
    private boolean historyHasScrollableEntries;
    private boolean historyScrollBarsLinked = false;
    private ObservableList<HistoryRow> programHistoryRows = FXCollections.observableArrayList();
    // Поиск по истории: полный список строк + текущий фильтр.
    private ObservableList<HistoryRow> programHistoryAllRows = FXCollections.observableArrayList();
    private TextField programHistorySearchField;
    private Button programHistorySearchButton;
    private Label programHistorySearchStatus;
    private String programHistorySearchQuery = "";
    private Image historyDeleteIcon;
    private Image historyBackIcon;
    private String appliedProgramText = "";
    /** Разобранная программа параметров и текст, из которого она получена. */
    private String parameterProgramCacheKey;
    private MachineParameterProgram.Variables parameterProgramCache;
    /** Левое окно кода — основная программа, на двухканальном станке это канал 2. */
    private ProgramGcodeEditor leftProgramEditor;
    /** Правое окно кода — канал 1. Создаётся, когда его развернут. */
    private ProgramGcodeEditor rightProgramEditor;
    /** Развёрнут ли правый блок кода. */
    private boolean rightPaneVisible;
    /** Какое окно исполняется: правое (канал 1) или левое (канал 2). */
    private boolean rightPaneActive;
    private Label leftPaneTitleLabel;
    private Label rightPaneTitleLabel;
    /** Область правого окна кода: третий элемент разделителя, справа от графика. */
    private VBox rightPaneArea;
    /** Сторона детали в окнах кода: 1 (внутренняя) или 2 (наружная). */
    private int activeSide = UserSettings.getActiveSide();
    private WorkpieceDefinition sharedTurnoverStock;
    private double turnoverZSum;
    /**
     * Рисовать оба канала сразу, на одной плоскости.
     *
     * <p>Деталь точат две головки, и по отдельности каждый канал даёт только свою
     * половину профиля: канал 1 — обод с гребнем, канал 2 — диск и ступицу. Целое
     * колесо видно только когда оба канала показаны вместе.
     */
    private boolean bothChannelsActive;
    /** Положение разделителя до развёртывания: при сворачивании возвращаем как было. */
    private double sidebarDividerBeforeExpand = 0.34;
    /** Быстрый ход G0 — красный, как на стойке SINUMERIK Operate. Общий с окном 3D. */
    static final String SINUMERIK_RAPID_COLOR = "#e33d3d";
    /** Рабочая подача G1/G2/G3 — зелёная, как на стойке SINUMERIK Operate. Общий с окном 3D. */
    static final String SINUMERIK_FEED_COLOR = "#19c25a";
    private List<ToolMove> cachedToolMoves = List.of();
    /**
     * Последняя разобранная программа задаёт X радиусом (Siemens DIAMOF), а не диаметром.
     * Внутри ходы всё равно хранятся диаметром, поэтому флаг нужен только для подписей:
     * оператор читает программу в тех же числах, что написал технолог.
     */
    private boolean programRadiusMode;
    private String cachedToolMovesSource = "";
    /** sourceLine по индексу хода — для бинарного поиска строки в редакторе. */
    private int[] cachedMoveSourceLines = new int[0];
    private List<ToolMove> executionMoves = List.of();
    private final AtomicBoolean canvasRedrawRequested = new AtomicBoolean(false);
    private final AtomicBoolean canvasRedrawInFlight = new AtomicBoolean(false);
    /** UI-кадр цикла в работе: новые кадры не ставятся в очередь, пока не дорисован текущий. */
    private final AtomicBoolean cycleTickUiInFlight = new AtomicBoolean(false);
    /** Последняя строка редактора, синхронизированная в цикле (пропуск повторной подсветки). */
    private int lastCycleSyncedEditorLine = -1;
    private boolean applyingProgramText = false;
    private AxisMode axisMode = AxisMode.STANDARD;
    private final Map<Integer, String[]> workOffsetFieldCache = new LinkedHashMap<Integer, String[]>();
    private boolean programEditorEditingActive = false;
    private boolean programSpaceKeyHandlersInstalled = false;
    private final AtomicBoolean spaceSingleBlockRepeatRunning = new AtomicBoolean(false);
    private final AtomicBoolean spaceHoldPlaybackInFlight = new AtomicBoolean(false);
    private static final long SPACE_HOLD_SLEEP_POLL_MS = 25L;
    private static final double DEFAULT_LEFT_PANE_RATIO = 0.34;
    private static final double HISTORY_SCROLLBAR_WIDTH = 19.0;
    private static final double HISTORY_WINDOW_WIDTH = 1100.0;
    private static final double HISTORY_WINDOW_HEIGHT = 560.0;

    @FXML
    public void initialize() {
        WorkOffsetStore.applySinuTrainDemoLathePresetsIfUnset();
        this.refreshUiScaleLabel();
        this.refreshGraphMachineOrientation();
        this.darkTheme = UserSettings.isDarkTheme();
        this.ensureSettingsPanel();
        this.setupLanguageMenu();
        this.setupCanvas();
        this.setupGraphOverlayPickability();
        this.setupEventHandlers();
        this.setupGeometryCalculations();
        this.setupWorkOffsets();
        this.refreshGraphManualOffsetsSnapshotFromSavedSettings();
        this.restoreLastProgramText();
        String program = this.programEditor != null ? this.programEditor.getText() : this.appliedProgramText;
        if (program != null && !program.isBlank()) {
            this.syncTurningSettingsFromGCode(program);
            this.syncAxisModeForProgram(program);
            this.syncWorkOffsetsFromProgram(program);
        }
        this.setupPerformanceOptimizations();
        this.setupStatusPanelCompactMode();
        this.applyStatusPanelMainButtonFonts();
        this.applyStatusCenterButtonStyle();
        this.applyProgramToolbarStyles();
        this.applyProgramSectionHeadingFonts();
        this.scheduleDefaultMainSplitDivider();
        Platform.runLater(() -> {
            this.applySavedTheme();
            Platform.runLater(() -> this.updateThemeMenuSelection(this.darkTheme));
            this.redrawCanvas();
            this.syncProgramEditorVisuals();
            this.applySimActionButtonStyles();
            this.applyStatusPanelMainButtonFonts();
            this.applyStatusCenterButtonStyle();
            this.applyProgramToolbarStyles();
            this.applyProgramSectionHeadingFonts();
            this.applyDefaultMainSplitDivider();
            this.aiStartMcpIfAsked();
        });
    }

    /**
     * Узкая статус-панель (маленькие экраны, масштаб Windows 150–200%):
     * блок «Скорость цикла» прижат к правому краю абсолютными якорями и при
     * нехватке ширины наезжал на РАДИУС/ЦЕНТРИРОВАТЬ — прячем его первым.
     */
    @FXML
    private HBox statusSpeedRow;
    @FXML
    private HBox statusProgressRow;

    private void setupStatusPanelCompactMode() {
        if (this.statusPanelPane == null || this.cycleSpeedBlock == null) {
            return;
        }
        this.statusPanelPane.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            boolean compact = newWidth.doubleValue() > 1.0 && newWidth.doubleValue() < 790.0;
            this.cycleSpeedBlock.setVisible(!compact);
            this.cycleSpeedBlock.setManaged(!compact);
        });
        // Процент не сжимается в «...»: уступает полоса прогресса.
        if (this.labelExecutionProgress != null) this.labelExecutionProgress.setMinWidth(Region.USE_PREF_SIZE);
        // Строка состояния и прогресс занимают всё между кнопками и правым блоком.
        // Отступ справа был вшит под блок скорости (382 px) и оставался, когда блок
        // прятался, — подпись сжималась до «...», а справа зияла пустота.
        if (this.statusSpeedRow != null) {
            this.statusSpeedRow.widthProperty().addListener((observable, oldWidth, newWidth) -> {
                double right = Math.max(150.0, newWidth.doubleValue() + 20.0);
                if (this.labelExecutionStatus != null) AnchorPane.setRightAnchor(this.labelExecutionStatus, right);
                if (this.statusProgressRow != null) AnchorPane.setRightAnchor(this.statusProgressRow, right);
            });
        }
    }

    private void applyStatusPanelMainButtonFonts() {
        Font font = Font.font(null, FontWeight.BOLD, 16.0);
        String fontStyle = "-fx-font-size: 16px; -fx-font-weight: bold;";
        Button[] buttons = new Button[]{
                this.buttonStart,
                this.buttonCycleStart,
                this.buttonReset
        };
        for (Button button : buttons) {
            if (button != null) {
                button.setFont(font);
            }
        }
        if (this.buttonSingleBlock != null) {
            this.buttonSingleBlock.setFont(font);
            // Цвет — из темы (app.css): вшитый светлый фон оставался светлым и в тёмной теме.
            this.buttonSingleBlock.setStyle(fontStyle);
        }
        if (this.checkBoxToolRadius != null) {
            this.checkBoxToolRadius.setFont(font);
            this.checkBoxToolRadius.setStyle(fontStyle);
        }
    }

    private void applyStatusCenterButtonStyle() {
        if (this.buttonFitView == null) {
            return;
        }
        String fontRules = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 6 4 6 4;";
        this.buttonFitView.setStyle(fontRules);
    }

    private static final double PROGRAM_TOOLBAR_FONT_SIZE = 14.0;
    private static final String PROGRAM_TOOLBAR_FONT_STYLE =
            "-fx-font-size: " + (int) PROGRAM_TOOLBAR_FONT_SIZE + "px; -fx-font-weight: bold;";

    private void scheduleDefaultMainSplitDivider() {
        if (this.splitPane == null) {
            return;
        }
        this.splitPane.widthProperty().addListener((observable, oldWidth, newWidth) -> this.applyDefaultMainSplitDivider());
    }

    private void applyDefaultMainSplitDivider() {
        if (this.splitPane == null || this.userMovedMainDivider) {
            return;
        }
        double width = this.splitPane.getWidth();
        if (width <= 0.0) {
            return;
        }
        this.attachMainDividerWatcher();
        this.setMainDividerToProgramPaneWidth(width);
        // Повторяем после раскладки: значение, выставленное до пересчёта скина SplitPane,
        // он нередко перебивает своим — и панель остаётся прежней ширины.
        Platform.runLater(() -> {
            if (!this.userMovedMainDivider && this.splitPane != null && this.splitPane.getWidth() > 0.0) {
                this.setMainDividerToProgramPaneWidth(this.splitPane.getWidth());
            }
        });
        this.mainSplitDividerAdjusted = true;
    }

    private void setMainDividerToProgramPaneWidth(double splitWidth) {
        double divider = this.programToolbarSingleRowWidth() / splitWidth;
        this.splitPane.setDividerPositions(Math.max(0.18, Math.min(0.55, divider)));
    }

    /**
     * Отпускаем разделитель только когда его реально тянут мышью.
     * Раньше здесь слушалась позиция, но SplitPane правит её и сам при раскладке —
     * это принималось за действие пользователя и навсегда отключало установку ширины.
     */
    private void attachMainDividerWatcher() {
        if (this.mainDividerWatched) {
            return;
        }
        java.util.Set<Node> dividers = this.splitPane.lookupAll(".split-pane-divider");
        if (dividers.isEmpty()) {
            return;
        }
        this.mainDividerWatched = true;
        for (Node divider : dividers) {
            divider.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                    event -> this.userMovedMainDivider = true);
        }
    }

    /** Ширина левой панели: фиксировано 790 px — кнопки в один ряд плюс небольшой отступ справа. */
    private double programToolbarSingleRowWidth() {
        return 790.0;
    }

    private void styleProgramToolbarButton(Button button) {
        if (button == null) {
            return;
        }
        Font font = Font.font(null, FontWeight.BOLD, PROGRAM_TOOLBAR_FONT_SIZE);
        button.setFont(font);
        button.setStyle(PROGRAM_TOOLBAR_FONT_STYLE);
    }

    private void applyProgramSectionHeadingFonts() {
        Font font = Font.font(null, FontWeight.BOLD, PROGRAM_TOOLBAR_FONT_SIZE);
        if (this.labelProgramTitle != null) {
            this.labelProgramTitle.setFont(font);
        }
        if (this.labelProgramHint != null) {
            this.labelProgramHint.setFont(font);
        }
    }

    private void applyProgramToolbarStyles() {
        this.styleProgramToolbarButton(this.buttonFrameUp);
        this.styleProgramToolbarButton(this.buttonFrameDown);
        this.styleProgramToolbarButton(this.buttonProgramFind);
        this.styleProgramToolbarButton(this.buttonProgramReplace);
        this.styleProgramToolbarButton(this.buttonProgramHistory);
        this.installFindButtonIcon();
        Font font = Font.font(null, FontWeight.BOLD, PROGRAM_TOOLBAR_FONT_SIZE);
        if (this.programSearchField != null) {
            this.programSearchField.setFont(font);
        }
        if (this.programFontSizeComboBox != null) {
            this.programFontSizeComboBox.setStyle(PROGRAM_TOOLBAR_FONT_STYLE);
        }
    }

    private void applySimActionButtonStyles() {
        // Цвета и рельеф — в app.css (sim-action-*), здесь только размеры.
        String size = "-fx-min-width: 124px; -fx-pref-width: 124px;";
        if (this.buttonSimulation3d != null) {
            this.buttonSimulation3d.getStyleClass().removeAll("accent-orange-button", "sim-action-button", "sim-action-primary");
            this.buttonSimulation3d.getStyleClass().addAll("sim-action-button", "sim-action-primary");
            this.buttonSimulation3d.setStyle(size + "-fx-min-height: 36px; -fx-pref-height: 36px; -fx-max-height: 36px;");
        }
        if (this.buttonAppSettings != null) {
            this.buttonAppSettings.getStyleClass().removeAll("accent-orange-button", "sim-action-button", "sim-action-secondary");
            this.buttonAppSettings.getStyleClass().addAll("sim-action-button", "sim-action-secondary");
            this.buttonAppSettings.setStyle(size + "-fx-min-height: 30px; -fx-pref-height: 30px; -fx-max-height: 30px;");
        }
    }

    private void setupCanvas() {
        this.drawingCanvas = new Canvas();
        this.drawingCanvas.setWidth(3840.0);
        this.drawingCanvas.setHeight(2160.0);
        this.gc = this.drawingCanvas.getGraphicsContext2D();
        this.drawingEngine = new DrawingEngine(this.drawingCanvas);
        this.drawingCanvas.setCache(false);
        this.canvasGroup = new Group(new Node[]{this.drawingCanvas});
        this.paneCanvas.getChildren().add(this.canvasGroup);
        this.drawingCanvas.widthProperty().bind((ObservableValue)this.paneCanvas.widthProperty());
        this.drawingCanvas.heightProperty().bind((ObservableValue)this.paneCanvas.heightProperty());
        this.paneCanvas.widthProperty().addListener((observableValue, number, number2) -> this.redrawCanvas());
        this.paneCanvas.heightProperty().addListener((observableValue, number, number2) -> this.redrawCanvas());
        this.paneCanvas.setOnScroll(scrollEvent -> {
            double d = 1.05;
            double d2 = scrollEvent.getDeltaY();
            if (d2 < 0.0) {
                d = 2.0 - d;
            }
            Point2D anchor = this.paneCanvas.sceneToLocal(scrollEvent.getSceneX(), scrollEvent.getSceneY());
            double anchorX = anchor != null && Double.isFinite(anchor.getX()) ? anchor.getX() : scrollEvent.getX();
            double anchorY = anchor != null && Double.isFinite(anchor.getY()) ? anchor.getY() : scrollEvent.getY();
            this.zoomPreviewCanvasAt(anchorX, anchorY, d);
            this.updateZoomDisplay();
            this.redrawCanvasForInteraction();
            scrollEvent.consume();
        });
        this.paneCanvas.setOnMousePressed(mouseEvent -> {
            if (mouseEvent.isSecondaryButtonDown()) {
                this.isDrawing = true;
                this.lastMouseX = mouseEvent.getX();
                this.lastMouseY = mouseEvent.getY();
            }
        });
        this.paneCanvas.setOnMouseDragged(mouseEvent -> {
            if (this.isDrawing && mouseEvent.isSecondaryButtonDown()) {
                this.translateX += mouseEvent.getX() - this.lastMouseX;
                this.translateY += mouseEvent.getY() - this.lastMouseY;
                this.lastMouseX = mouseEvent.getX();
                this.lastMouseY = mouseEvent.getY();
                this.redrawCanvasForInteraction();
            }
        });
        this.paneCanvas.setOnMouseReleased(mouseEvent -> {
            this.isDrawing = false;
        });
        this.paneCanvas.setOnMouseMoved(mouseEvent -> {
            double d = mouseEvent.getX();
            double d2 = mouseEvent.getY();
            Point2D point2D = this.canvasGroup.sceneToLocal(this.paneCanvas.localToScene(d, d2));
            this.hoverMouseX = point2D.getX();
            this.hoverMouseY = point2D.getY();
            this.textCoordinateX.setText(String.format(Locale.US, "X %.1f", d));
            this.textCoordinateZ.setText(String.format(Locale.US, "Z %.1f", d2));
            this.textFrameCoordinateX.setText(String.format(Locale.US, "\u0394X %.1f", d));
            this.textFrameCoordinateZ.setText(String.format(Locale.US, "\u0394Z %.1f", d2));
            if (this.graphInteractionEnabled() && this.shouldRedrawGraphHover(d, d2)) {
                this.redrawCanvas();
            }
        });
        this.paneCanvas.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (mouseEvent.getClickCount() == 2) {
                if (this.graphEditorSelectionIndex >= 0 && this.isClickNearGraphSelection(mouseEvent.getX(), mouseEvent.getY())) {
                    this.clearGraphEditorSelection();
                    this.clearProgramHighlight();
                    this.redrawCanvas();
                    return;
                }
                this.resetCanvasView();
                return;
            }
            this.handleGraphPointClick(mouseEvent.getX(), mouseEvent.getY());
        });
        this.paneCanvas.setFocusTraversable(true);
        this.paneCanvas.addEventFilter(MouseEvent.MOUSE_PRESSED, this::dismissProgramEditingOnMousePressed);
        if (this.anchorPaneCanvas != null) {
            this.anchorPaneCanvas.setFocusTraversable(true);
            this.anchorPaneCanvas.addEventFilter(MouseEvent.MOUSE_PRESSED, this::dismissProgramEditingOnMousePressed);
        }
    }

    private void setupGraphOverlayPickability() {
        for (Text text : new Text[]{
                this.textCoordinateX,
                this.textCoordinateZ,
                this.textFrameCoordinateX,
                this.textFrameCoordinateZ,
                this.textZooming,
                this.textFrame
        }) {
            if (text != null) {
                text.setMouseTransparent(true);
                text.setPickOnBounds(false);
            }
        }
    }

    private void setupEventHandlers() {
        this.checkBoxToolRadius.setOnAction(actionEvent -> {
            if (this.checkBoxToolRadius.isSelected()) {
                this.enableToolRadiusCompensation();
            } else {
                this.disableToolRadiusCompensation();
            }
        });
        this.buttonStart.setDisable(false);
        this.buttonReset.setDisable(false);
        this.buttonCycleStart.setDisable(false);
        this.buttonSingleBlock.setDisable(false);
        this.updateProgressDisplay(0, I18n.text("app.001"));
        this.initProgramGcodeEditor();
        this.installProgramEditingDismissHandlers();
        // Второй блок кода возвращаем в том же виде, в каком его оставили.
        if (UserSettings.isRightPaneVisible()) {
            this.rightPaneVisible = true;
            this.applyRightPaneLayout();
            Platform.runLater(() -> this.moveSidebarDivider(true));
        }
        this.updateSideToggle();
    }

    private void installProgramEditingDismissHandlers() {
        if (this.sidebarPane != null) {
            this.sidebarPane.addEventFilter(MouseEvent.MOUSE_PRESSED, this::dismissProgramEditingOnMousePressed);
        }
        if (this.buttonStart != null) {
            Node node = this.buttonStart.getParent();
            if (node != null) {
                node.addEventFilter(MouseEvent.MOUSE_PRESSED, this::dismissProgramEditingOnMousePressed);
            }
        }
    }

    private void initProgramGcodeEditor() {
        if (this.programEditorBox == null) {
            return;
        }
        this.programEditorBox.getChildren().clear();
        if (!this.programEditorBox.getStyleClass().contains("program-editor-panel")) {
            this.programEditorBox.getStyleClass().add("program-editor-panel");
        }
        this.programEditor = new ProgramGcodeEditor();
        this.leftProgramEditor = this.programEditor;
        HBox.setHgrow(this.programEditor.getNode(), Priority.ALWAYS);
        this.programEditorBox.getChildren().add(this.programEditor.getNode());
        this.programEditor.setOnTextChange(() -> {
            if (this.applyingProgramText) {
                return;
            }
            this.programEditorEditingActive = true;
            this.stopSpaceSingleBlockRepeat();
            if (this.isProgramExecutionActive()) {
                this.reconcileProgramWhileEditing();
            }
            String liveText = this.programEditor.getText();
            if (liveText != null && !liveText.isBlank()) {
                Platform.runLater(() -> this.syncWorkOffsetsFromProgram(liveText));
            }
        });
        this.programEditor.setOnFocusLost(() -> Platform.runLater(() -> {
            if (this.programEditor == null || this.getMainScene() == null) {
                return;
            }
            Node node = this.getMainScene().getFocusOwner();
            if (node == null || !this.isEventTargetInNode(node, this.programEditor.getCodeArea())) {
                if (this.programEditorEditingActive || this.isProgramTextDirty()) {
                    this.applyProgramEditorChanges();
                }
                this.programEditorEditingActive = false;
                this.programEditor.releaseEditorFocus();
            }
        }));
        this.programEditor.setOnGutterClick(n -> {
            if (this.executionRunning) {
                return;
            }
            this.commitProgramEditing();
            this.selectProgramLineAsStart(n);
        });
        this.programEditor.setOnLineClick(n -> Platform.runLater(() -> this.handleEditorLineClick(n)));
        this.programEditor.setOnCaretLineChanged(line -> Platform.runLater(() -> this.handleEditorCaretLine(line)));
        this.programEditor.setOnStartMarkerToggle(n -> this.clearProgramStartMarker());
        this.programEditor.setOnRequestReplacementPrompt(() -> this.promptProgramReplacement());
        this.programEditor.getCodeArea().addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (keyEvent.isControlDown() && keyEvent.getCode() == KeyCode.F) {
                if (this.programSearchField != null) {
                    this.programSearchField.requestFocus();
                    this.programSearchField.selectAll();
                }
                keyEvent.consume();
            }
        });
        this.installProgramSpaceKeyHandlers();
        Platform.runLater(() -> {
            Scene scene = this.getMainScene();
            if (scene != null) {
                this.attachProgramSpaceKeyHandlers(scene);
            }
        });
        if (this.programFontSizeComboBox != null) {
            this.programFontSizeComboBox.getItems().setAll(10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32);
            int savedFontSize = UserSettings.getProgramFontSize();
            this.programFontSizeComboBox.setValue(savedFontSize);
            this.applyProgramFontSize(savedFontSize);
            this.programFontSizeComboBox.valueProperty().addListener((observableValue, number, number2) -> {
                if (number2 != null) {
                    int fontSize = UserSettings.clampProgramFontSize(number2.intValue());
                    this.applyProgramFontSize(fontSize);
                    UserSettings.setProgramFontSize(fontSize);
                }
            });
            this.installProgramFontSizeCloseHandler();
        }
    }

    private boolean isProgramExecutionActive() {
        return this.executionRunning || this.cyclePaused || this.currentExecutionIndex >= 0;
    }

    private void handleClickOutsideProgramEditor(MouseEvent mouseEvent) {
        this.dismissProgramEditingOnMousePressed(mouseEvent);
    }

    private void dismissProgramEditingOnMousePressed(MouseEvent mouseEvent) {
        if (this.programEditor == null) {
            return;
        }
        if (this.isEventTargetInNode(mouseEvent.getTarget(), this.programEditor.getNode())
                || this.isEventTargetInNode(mouseEvent.getTarget(), this.programEditor.getCodeArea())) {
            return;
        }
        Node node = null;
        if (mouseEvent.getTarget() instanceof Node node2) {
            node = node2;
        }
        this.finishProgramEditingSession(node);
    }

    private void finishProgramEditingSession() {
        this.finishProgramEditingSession(null);
    }

    private void finishProgramEditingSession(Node node) {
        if (this.programEditor == null) {
            return;
        }
        if (this.programEditorEditingActive || this.isProgramTextDirty()) {
            this.applyProgramEditorChanges();
        }
        this.programEditorEditingActive = false;
        this.releaseProgramEditorFocus(node);
    }

    private void releaseProgramEditorFocus(Node node) {
        if (this.programEditor == null) {
            return;
        }
        this.programEditor.releaseEditorFocus();
        if (node instanceof Button) {
            ((Button)node).requestFocus();
            return;
        }
        if (node != null && node.focusTraversableProperty().get()) {
            node.requestFocus();
            if (!this.isProgramCodeAreaFocused()) {
                return;
            }
        }
        if (this.paneCanvas != null) {
            this.paneCanvas.requestFocus();
        } else if (this.anchorPaneCanvas != null) {
            this.anchorPaneCanvas.requestFocus();
        }
    }

    private void commitProgramEditing() {
        this.applyProgramEditorChanges();
        this.programEditorEditingActive = false;
    }

    private boolean isProgramTextDirty() {
        return this.programEditor != null && !this.programEditor.getText().equals(this.appliedProgramText);
    }

    private boolean canUseSpaceForSingleBlock() {
        if (this.isProgramCodeAreaFocused()) {
            return false;
        }
        if (this.isAuxiliaryTextInputFocused()) {
            return false;
        }
        if (this.buttonSingleBlock == null || this.buttonSingleBlock.isDisabled()) {
            return false;
        }
        return true;
    }

    private boolean isProgramCodeAreaFocused() {
        if (this.programEditor == null) {
            return false;
        }
        Scene scene = this.getMainScene();
        if (scene == null) {
            return false;
        }
        Node node = scene.getFocusOwner();
        return node != null && this.isEventTargetInNode(node, this.programEditor.getCodeArea());
    }

    private boolean isAuxiliaryTextInputFocused() {
        Scene scene = this.getMainScene();
        if (scene == null) {
            return false;
        }
        Node node = scene.getFocusOwner();
        while (node != null) {
            if (node instanceof TextInputControl) {
                return this.programEditor == null || !this.isEventTargetInNode(node, this.programEditor.getCodeArea());
            }
            node = node.getParent();
        }
        return false;
    }

    private Scene getMainScene() {
        if (this.programEditor != null) {
            Scene scene = this.programEditor.getCodeArea().getScene();
            if (scene != null) {
                return scene;
            }
        }
        if (this.buttonSingleBlock != null) {
            return this.buttonSingleBlock.getScene();
        }
        if (this.paneCanvas != null) {
            return this.paneCanvas.getScene();
        }
        return null;
    }

    private void installProgramSpaceKeyHandlers() {
        Scene scene = this.getMainScene();
        if (scene != null) {
            this.attachProgramSpaceKeyHandlers(scene);
            return;
        }
        if (this.programEditor != null) {
            this.programEditor.getCodeArea().sceneProperty().addListener((observableValue, scene2, scene3) -> {
                if (scene3 != null) {
                    this.attachProgramSpaceKeyHandlers(scene3);
                }
            });
        } else if (this.buttonSingleBlock != null) {
            this.buttonSingleBlock.sceneProperty().addListener((observableValue, scene2, scene3) -> {
                if (scene3 != null) {
                    this.attachProgramSpaceKeyHandlers(scene3);
                }
            });
        }
    }

    private void attachProgramSpaceKeyHandlers(Scene scene) {
        if (this.programSpaceKeyHandlersInstalled) {
            return;
        }
        this.programSpaceKeyHandlersInstalled = true;
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleClickOutsideProgramEditor);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::filterProgramSpacePressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, this::filterProgramSpaceReleased);
        scene.addEventHandler(KeyEvent.KEY_RELEASED, this::filterProgramSpaceReleased);
        scene.windowProperty().addListener((observableValue, window, window2) -> {
            if (window2 != null) {
                window2.focusedProperty().addListener((observableValue2, bl, bl2) -> {
                    if (!bl2.booleanValue()) {
                        this.stopSpaceSingleBlockRepeat();
                    }
                });
            }
        });
        if (scene.getWindow() != null) {
            scene.getWindow().focusedProperty().addListener((observableValue, bl, bl2) -> {
                if (!bl2.booleanValue()) {
                    this.stopSpaceSingleBlockRepeat();
                }
            });
        }
    }

    private void filterProgramSpacePressed(KeyEvent keyEvent) {
        if (this.tryHandleProgramSpaceAsSingleBlock(keyEvent)) {
            keyEvent.consume();
        }
    }

    private void filterProgramSpaceReleased(KeyEvent keyEvent) {
        if (keyEvent.getCode() != KeyCode.SPACE) {
            return;
        }
        this.stopSpaceSingleBlockRepeat();
    }

    private boolean isEventTargetInNode(Object object, Node node) {
        if (!(object instanceof Node target)) {
            return false;
        }
        while (target != null) {
            if (target == node) {
                return true;
            }
            target = target.getParent();
        }
        return false;
    }

    private boolean tryHandleProgramSpaceAsSingleBlock(KeyEvent keyEvent) {
        if (keyEvent.getCode() != KeyCode.SPACE || keyEvent.isControlDown() || keyEvent.isAltDown() || keyEvent.isMetaDown()) {
            return false;
        }
        if (!this.canUseSpaceForSingleBlock()) {
            return false;
        }
        if (this.spaceSingleBlockRepeatRunning.get()) {
            if (this.spaceHoldPlaybackInFlight.get()) {
                return true;
            }
            this.spaceSingleBlockRepeatRunning.set(false);
        }
        this.startSpaceSingleBlockRepeat();
        return true;
    }

    private void startSpaceSingleBlockRepeat() {
        this.spaceSingleBlockRepeatRunning.set(true);
        if (!this.spaceHoldPlaybackInFlight.get()) {
            CompletableFuture.runAsync(this::runSpaceHoldPlayback, EXECUTOR);
        }
    }

    private void stopSpaceSingleBlockRepeat() {
        this.spaceSingleBlockRepeatRunning.set(false);
    }

    private void runSpaceHoldPlayback() {
        if (!this.spaceHoldPlaybackInFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            while (this.spaceSingleBlockRepeatRunning.get() && this.canUseSpaceForSingleBlock()) {
                if (!this.advanceSingleFrameSmoothWhileSpaceHeld()) {
                    break;
                }
            }
        } finally {
            this.spaceHoldPlaybackInFlight.set(false);
            Platform.runLater(() -> this.updateButtonStates(this.executionRunning));
        }
    }

    private boolean advanceSingleFrameSmoothWhileSpaceHeld() {
        if (!this.spaceSingleBlockRepeatRunning.get()) {
            return false;
        }
        List<ToolMove> list = this.refreshExecutionMoves();
        if (list.isEmpty()) {
            this.updateProgressDisplay(0, I18n.text("app.002"));
            return false;
        }
        if (!this.hasFrameMoves(list)) {
            this.updateProgressDisplay(0, I18n.text("app.003"));
            return false;
        }
        this.fullPreviewVisible = false;
        this.previewFromProgramHead = false;
        int nFrom = this.currentExecutionIndex;
        int nTarget = nFrom < 0 ? this.resolvePathStartIndex(list) : this.nextFrameIndex(list, nFrom, 1);
        if (nTarget >= list.size()) {
            this.currentExecutionIndex = -1;
            this.pathDisplayActive = false;
            this.executionRunning = false;
            this.cyclePaused = true;
            this.updateProgressDisplay(100, I18n.text("app.004"));
            Platform.runLater(() -> {
                this.updateButtonStates(false);
                this.redrawCanvas();
            });
            return false;
        }
        boolean blWasInactive = !this.pathDisplayActive;
        if (nFrom < 0) {
            this.beginPathDisplay(list, nTarget);
            if (blWasInactive) {
                this.markLaunchFocusPending();
            }
            int nAnimFrom = this.pathDisplayStartIndex;
            this.currentExecutionIndex = nAnimFrom;
            if (nAnimFrom < nTarget) {
                this.animateExecutionSegment(list, nTarget);
            } else {
                this.finalizeSingleFrameDisplay(list, nTarget);
            }
        } else {
            if (!this.pathDisplayActive) {
                this.beginPathDisplay(list, nFrom);
                if (blWasInactive) {
                    this.markLaunchFocusPending();
                }
            }
            this.currentExecutionIndex = nFrom;
            if (nFrom < nTarget) {
                this.animateExecutionSegment(list, nTarget);
            } else {
                this.finalizeSingleFrameDisplay(list, nTarget);
            }
        }
        return this.spaceSingleBlockRepeatRunning.get();
    }

    private void animateExecutionSegment(List<ToolMove> list, int n) {
        int n2 = this.pathDisplayStartIndex;
        while (this.spaceSingleBlockRepeatRunning.get() && this.currentExecutionIndex < n) {
            int n3 = this.currentExecutionIndex;
            if (n3 < 0) {
                this.currentExecutionIndex = n2;
                continue;
            }
            if (n3 < n2) {
                this.currentExecutionIndex = n2;
                continue;
            }
            if (n3 >= list.size()) {
                break;
            }
            ToolMove toolMove = list.get(n3);
            this.selectedMarkerLine = this.markerLineForMove(toolMove);
            Platform.runLater(() -> {
                this.syncProgramSelectionToCurrentFrame(list);
                this.drawCanvasNow();
            });
            this.updateProgressDisplay(this.progressPercentForMove(list, n3), this.formatExecutionStatus(list, toolMove, n3));
            this.sleepWhileSpaceHeld(this.getDrawDelayMillis());
            if (!this.spaceSingleBlockRepeatRunning.get()) {
                break;
            }
            this.currentExecutionIndex = this.nextMoveIndex(list, n3, 1);
        }
        if (!this.spaceSingleBlockRepeatRunning.get()) {
            if (this.currentExecutionIndex >= 0 && this.currentExecutionIndex < list.size()) {
                this.finalizeSingleFrameDisplay(list, this.currentExecutionIndex);
            }
            return;
        }
        this.currentExecutionIndex = Math.min(n, list.size() - 1);
        this.finalizeSingleFrameDisplay(list, this.currentExecutionIndex);
    }

    private void finalizeSingleFrameDisplay(List<ToolMove> list, int n) {
        ToolMove toolMove = list.get(n);
        this.selectedMarkerLine = this.markerLineForMove(toolMove);
        this.executionRunning = false;
        this.cyclePaused = true;
        this.updateProgressDisplay(this.progressPercentForMove(list, n), this.formatExecutionStatus(list, toolMove, n));
        Platform.runLater(() -> {
            this.syncProgramSelectionToCurrentFrame(list);
            this.updateButtonStates(false);
            this.redrawCanvas();
        });
    }

    private void zoomPreviewCanvasAt(double anchorX, double anchorY, double zoomFactor) {
        if (this.drawingCanvas == null || !Double.isFinite(anchorX) || !Double.isFinite(anchorY)) {
            this.currentZoom = Math.max(
                    MIN_CANVAS_ZOOM,
                    Math.min(MAX_CANVAS_ZOOM, this.currentZoom * zoomFactor));
            return;
        }
        double width = Math.max(1.0, this.drawingCanvas.getWidth());
        double height = Math.max(1.0, this.drawingCanvas.getHeight());
        if (!this.computeProgramBoundsLayout(width, height)) {
            this.currentZoom = Math.max(
                    MIN_CANVAS_ZOOM,
                    Math.min(MAX_CANVAS_ZOOM, this.currentZoom * zoomFactor));
            return;
        }
        double oldScale = this.previewLayoutScale;
        if (!Double.isFinite(oldScale) || oldScale <= 0.0) {
            return;
        }
        double anchorHorizontal = this.previewLayoutMinZ + (anchorX - this.previewLayoutOriginX) / oldScale;
        double anchorVertical = this.previewLayoutMaxX - (anchorY - this.previewLayoutOriginY) / oldScale;
        double marginSide = 88.0;
        double marginTop = 88.0;
        double marginBottom = 88.0;
        double spanHorizontal = Math.max(1.0, this.previewLayoutMaxZ - this.previewLayoutMinZ);
        double spanVertical = Math.max(1.0, this.previewLayoutMaxX - this.previewLayoutMinX);
        double plotW = Math.max(1.0, width - marginSide * 2.0);
        double plotH = Math.max(1.0, height - marginTop - marginBottom);
        double baseScale = Math.min(plotW / spanHorizontal, plotH / spanVertical);
        if (!Double.isFinite(baseScale) || baseScale <= 0.0) {
            baseScale = 1.0;
        }
        double nextZoom = this.clampPreviewZoom(this.currentZoom * zoomFactor, baseScale);
        if (Math.abs(nextZoom - this.currentZoom) < 1.0e-9) {
            return;
        }
        double nextScale = baseScale * nextZoom;
        double baseOriginX = marginSide + (plotW - spanHorizontal * nextScale) / 2.0;
        double baseOriginY = marginTop + (plotH - spanVertical * nextScale) / 2.0;
        this.currentZoom = nextZoom;
        this.translateX = anchorX - baseOriginX - (anchorHorizontal - this.previewLayoutMinZ) * nextScale;
        this.translateY = anchorY - baseOriginY - (this.previewLayoutMaxX - anchorVertical) * nextScale;
        this.applyPreviewLayoutBounds(
                this.previewLayoutMinX,
                this.previewLayoutMaxX,
                this.previewLayoutMinZ,
                this.previewLayoutMaxZ,
                width,
                height);
    }

    private boolean graphInteractionEnabled() {
        return !this.executionRunning || this.cyclePaused;
    }

    private boolean shouldRedrawGraphHover(double paneX, double paneY) {
        if (!Double.isFinite(this.lastGraphHoverRedrawPaneX) || !Double.isFinite(this.lastGraphHoverRedrawPaneY)) {
            this.lastGraphHoverRedrawPaneX = paneX;
            this.lastGraphHoverRedrawPaneY = paneY;
            return true;
        }
        double dx = paneX - this.lastGraphHoverRedrawPaneX;
        double dy = paneY - this.lastGraphHoverRedrawPaneY;
        if (Math.hypot(dx, dy) < GRAPH_HOVER_REDRAW_MIN_DELTA_PX) {
            return false;
        }
        this.lastGraphHoverRedrawPaneX = paneX;
        this.lastGraphHoverRedrawPaneY = paneY;
        return true;
    }

    private void applyProgramEditorChanges() {
        if (this.programEditor == null) {
            return;
        }
        String string = this.programEditor.getText();
        if (string.equals(this.appliedProgramText)) {
            return;
        }
        try {
            if (this.isProgramExecutionActive()) {
                this.reconcileProgramWhileEditing();
            } else {
                List<ToolMove> list = this.parseGCodeMoves(string);
                this.recordProgramHistoryIfChanged(this.appliedProgramText, string);
                this.appliedProgramText = string;
                this.saveLastProgramText(string);
                this.syncTurningSettingsFromGCode(string);
                this.syncWorkOffsetsFromProgram(string);
                this.syncAxisModeForProgram(string);
                this.fullPreviewVisible = false;
                this.toolpathGraphVisible = false;
                this.trajectoryPreviewComplete = false;
                this.currentExecutionIndex = -1;
                this.selectedMarkerLine = -1;
                this.remapProgramStartMarker(list);
                this.clearProgramHighlight();
                this.updateProgressDisplay(
                        0,
                        list.isEmpty()
                                ? I18n.text("app.005")
                                : I18n.text("app.006"));
                this.syncProgramEditorVisuals();
                this.redrawCanvas();
                this.refreshOpenSimulation3d();
                // 2D-путь: диалогов не показываем, только плашка у заголовка.
                this.refreshProgramDiagnostics(string, list.size());
            }
        }
        catch (RuntimeException runtimeException) {
            this.recordProgramHistoryIfChanged(this.appliedProgramText, string);
            this.appliedProgramText = string;
            this.saveLastProgramText(string);
            this.cachedToolMovesSource = "";
            this.previewLayoutValid = false;
            this.refreshProgramHistoryView();
            this.refreshProgramDiagnostics(string, 0);
            this.updateProgressDisplay(0, I18n.text("app.007") + runtimeException.getMessage());
        }
    }

    private void reconcileProgramWhileEditing() {
        if (this.programEditor == null) {
            return;
        }
        String string = this.programEditor.getText();
        if (string.equals(this.appliedProgramText)) {
            return;
        }
        List<ToolMove> list = this.parseGCodeMoves(this.appliedProgramText);
        List<ToolMove> list2 = this.parseGCodeMoves(string);
        int n = this.currentExecutionIndex;
        int n2 = this.programStartMarkerLine;
        int n3 = this.selectedStartIndex;
        this.recordProgramHistoryIfChanged(this.appliedProgramText, string);
        this.appliedProgramText = string;
        this.saveLastProgramText(string);
        this.syncTurningSettingsFromGCode(string);
        this.syncWorkOffsetsFromProgram(string);
        this.fullPreviewVisible = false;
        if (list2.isEmpty()) {
            this.currentExecutionIndex = -1;
            this.clearProgramHighlight();
            this.updateProgressDisplay(0, I18n.text("app.008"));
            this.syncProgramEditorVisuals();
            this.redrawCanvas();
            return;
        }
        String string2 = null;
        if (n >= 0) {
            int n4 = this.remapExecutionIndex(list, list2, n);
            if (n4 < 0) {
                n4 = Math.min(Math.max(0, n), list2.size() - 1);
                int n5 = n < list.size() ? list.get(n).sourceLine : -1;
                string2 = n5 > 0 ? String.format(Locale.US, I18n.text("app.009"), n5, n4 + 1) : I18n.text("app.010");
            }
            this.currentExecutionIndex = n4;
        }
        if (n2 > 0) {
            if (this.isProgramMarkerableLine(this.getProgramLineText(n2))) {
                int n6 = this.findMoveIndexForMarkerLine(list2, n2);
                if (n6 >= 0) {
                    this.programStartMarkerLine = n2;
                    this.selectedStartIndex = n6;
                    this.selectedMarkerLine = n2;
                } else {
                    this.programStartMarkerLine = -1;
                    this.selectedStartIndex = -1;
                    this.selectedMarkerLine = -1;
                    if (string2 == null) {
                        string2 = String.format(Locale.US, I18n.text("app.011"), n2);
                    }
                }
            } else {
                this.programStartMarkerLine = -1;
                this.selectedStartIndex = -1;
                this.selectedMarkerLine = -1;
                if (string2 == null) {
                    string2 = this.explainMarkerRejectionForLine(n2);
                }
            }
        } else if (n3 >= 0 && n3 < list2.size()) {
            this.selectedStartIndex = n3;
        }
        if (this.currentExecutionIndex >= 0) {
            this.syncProgramSelectionToCurrentFrame(list2);
        }
        int n7 = this.currentExecutionIndex >= 0 ? (int)Math.round((double)(this.currentExecutionIndex + 1) * 100.0 / (double)list2.size()) : this.progressPercent(list2);
        if (string2 == null) {
            if (this.executionRunning && !this.cyclePaused) {
                string2 = I18n.text("app.012");
            } else if (this.cyclePaused || this.currentExecutionIndex >= 0) {
                string2 = I18n.text("app.013");
            } else {
                string2 = I18n.text("app.014");
            }
        }
        this.updateProgressDisplay(n7, string2);
        this.syncProgramEditorVisuals();
        this.redrawCanvas();
        this.refreshOpenSimulation3d();
    }

    private int remapExecutionIndex(List<ToolMove> list, List<ToolMove> list2, int n) {
        if (list2.isEmpty() || n < 0) {
            return -1;
        }
        if (list != null && n < list.size()) {
            int n2 = this.findMoveIndexForExactLine(list2, list.get(n).sourceLine);
            if (n2 >= 0) {
                return n2;
            }
            int n3 = this.findMoveIndexForLine(list2, list.get(n).sourceLine);
            if (n3 >= 0) {
                return n3;
            }
        }
        if (n < list2.size()) {
            return n;
        }
        return list2.size() - 1;
    }

    @FXML
    private void onProgramSearch() {
        if (this.programEditor == null || this.programSearchField == null) {
            return;
        }
        String string = this.programSearchField.getText();
        if (string == null || string.isBlank()) {
            return;
        }
        String string2 = this.programEditor.getText();
        int n = Math.max(0, this.programEditor.getCaretPosition());
        int n2 = string2.toLowerCase(Locale.US).indexOf(string.toLowerCase(Locale.US), n);
        if (n2 < 0 && n > 0) {
            n2 = string2.toLowerCase(Locale.US).indexOf(string.toLowerCase(Locale.US));
        }
        if (n2 >= 0) {
            this.programEditorEditingActive = true;
            this.lastSearchMatchStart = n2;
            this.lastSearchMatchEnd = n2 + string.length();
            this.programEditor.requestFocus();
            this.programEditor.revealAndSelectRange(n2, n2 + string.length());
            this.updateProgressDisplay(0, I18n.text("app.015") + string);
        } else {
            this.lastSearchMatchStart = -1;
            this.lastSearchMatchEnd = -1;
            this.updateProgressDisplay(0, I18n.text("app.016") + string);
        }
    }

    /**
     * \u00ab\u0417\u0430\u043c\u0435\u043d\u0438\u0442\u044c\u00bb: \u043c\u0435\u043d\u044f\u0435\u0442 \u0422\u041e\u041b\u042c\u041a\u041e \u0442\u043e \u0432\u0445\u043e\u0436\u0434\u0435\u043d\u0438\u0435, \u0447\u0442\u043e \u0441\u0435\u0439\u0447\u0430\u0441 \u043d\u0430\u0439\u0434\u0435\u043d\u043e \u043a\u043d\u043e\u043f\u043a\u043e\u0439 \u043f\u043e\u0438\u0441\u043a\u0430 (\u043b\u0443\u043f\u043e\u0439) \u0438 \u0432\u044b\u0434\u0435\u043b\u0435\u043d\u043e.
     * \u0414\u0430\u043b\u044c\u0448\u0435 \u043f\u043e \u0442\u0435\u043a\u0441\u0442\u0443 \u043d\u0435 \u0438\u0434\u0451\u0442 \u2014 \u0447\u0442\u043e\u0431\u044b \u0437\u0430\u043c\u0435\u043d\u0438\u0442\u044c \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0435, \u043d\u0443\u0436\u043d\u043e \u0441\u043d\u043e\u0432\u0430 \u043d\u0430\u0436\u0430\u0442\u044c \u043f\u043e\u0438\u0441\u043a (\u043b\u0443\u043f\u0443).
     * \u041f\u0440\u0438 \u043f\u0435\u0440\u0432\u043e\u043c \u043d\u0430\u0436\u0430\u0442\u0438\u0438 (\u0435\u0441\u043b\u0438 \u0435\u0449\u0451 \u043d\u0435 \u0437\u0430\u0434\u0430\u043d\u043e) \u0441\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u0435\u0442, \u043d\u0430 \u0447\u0442\u043e \u043c\u0435\u043d\u044f\u0442\u044c; \u0446\u0435\u043b\u044c \u0437\u0430\u043c\u0435\u043d\u044b \u0442\u0430\u043a\u0436\u0435 \u043c\u043e\u0436\u043d\u043e
     * \u0437\u0430\u0434\u0430\u0442\u044c/\u0441\u043c\u0435\u043d\u0438\u0442\u044c \u0447\u0435\u0440\u0435\u0437 \u041f\u041a\u041c \u00ab\u0417\u0430\u043c\u0435\u043d\u0438\u0442\u044c \u043d\u0430\u2026\u00bb.
     */
    @FXML
    private void onProgramReplace() {
        if (this.programEditor == null || this.programSearchField == null) {
            return;
        }
        String term = this.programSearchField.getText();
        if (term == null || term.isEmpty()) {
            this.updateProgressDisplay(0, I18n.text("app.017"));
            this.programSearchField.requestFocus();
            return;
        }
        // \u041c\u0435\u043d\u044f\u0435\u043c \u0442\u043e\u043b\u044c\u043a\u043e \u0442\u043e \u0432\u0445\u043e\u0436\u0434\u0435\u043d\u0438\u0435, \u0447\u0442\u043e \u043d\u0430\u0448\u043b\u0438 \u043b\u0443\u043f\u043e\u0439. \u041f\u0440\u043e\u0432\u0435\u0440\u044f\u0435\u043c, \u0447\u0442\u043e \u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d\u043d\u044b\u0439 \u0434\u0438\u0430\u043f\u0430\u0437\u043e\u043d
        // \u0432\u0441\u0451 \u0435\u0449\u0451 \u0443\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0438\u043c\u0435\u043d\u043d\u043e \u043d\u0430 \u0438\u0441\u043a\u043e\u043c\u044b\u0439 \u0442\u0435\u043a\u0441\u0442 (\u0438\u043d\u0430\u0447\u0435 \u0442\u0435\u043a\u0441\u0442 \u043f\u0440\u0430\u0432\u0438\u043b\u0438 \u2014 \u043d\u0443\u0436\u043d\u043e \u0438\u0441\u043a\u0430\u0442\u044c \u0437\u0430\u043d\u043e\u0432\u043e).
        String text = this.programEditor.getText();
        if (text == null) {
            text = "";
        }
        int start = this.lastSearchMatchStart;
        int end = this.lastSearchMatchEnd;
        boolean validMatch = start >= 0 && end > start && end <= text.length()
                && text.substring(start, end).equalsIgnoreCase(term);
        if (!validMatch) {
            this.updateProgressDisplay(0, I18n.text("app.018") + term + I18n.text("app.019"));
            return;
        }
        if (this.programReplaceText == null && !this.promptProgramReplacement()) {
            return; // \u043e\u0442\u043c\u0435\u043d\u0430 \u0432\u0432\u043e\u0434\u0430 \u0437\u0430\u043c\u0435\u043d\u044b
        }
        String replacement = this.programReplaceText != null ? this.programReplaceText : "";
        this.programEditorEditingActive = true;
        this.programEditor.requestFocus();
        this.programEditor.replaceRange(start, end, replacement);
        // \u041a\u0443\u0440\u0441\u043e\u0440 \u0441\u0442\u0430\u0432\u0438\u043c \u043f\u043e\u0441\u043b\u0435 \u0437\u0430\u043c\u0435\u043d\u044b, \u043d\u043e \u041d\u0415 \u0432\u044b\u0434\u0435\u043b\u044f\u0435\u043c \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0435 \u2014 \u0434\u043e \u043f\u043e\u0432\u0442\u043e\u0440\u043d\u043e\u0433\u043e \u043d\u0430\u0436\u0430\u0442\u0438\u044f \u043b\u0443\u043f\u044b.
        this.programEditor.positionCaret(start + replacement.length());
        // \u0421\u0431\u0440\u0430\u0441\u044b\u0432\u0430\u0435\u043c \u043d\u0430\u0439\u0434\u0435\u043d\u043d\u043e\u0435: \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0435 \u00ab\u0417\u0430\u043c\u0435\u043d\u0438\u0442\u044c\u00bb \u0441\u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \u0442\u043e\u043b\u044c\u043a\u043e \u043f\u043e\u0441\u043b\u0435 \u043d\u043e\u0432\u043e\u0439 \u043b\u0443\u043f\u044b.
        this.lastSearchMatchStart = -1;
        this.lastSearchMatchEnd = -1;
        int remaining = countOccurrencesIgnoreCase(this.programEditor.getText(), term);
        if (remaining > 0) {
            this.updateProgressDisplay(0, I18n.text("app.020") + term + "\u00bb: " + remaining
                    + I18n.text("app.021"));
        } else {
            this.updateProgressDisplay(0, I18n.text("app.022") + term + I18n.text("app.023"));
        }
    }

    /** \u0421\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u0435\u0442, \u043d\u0430 \u0447\u0442\u043e \u0437\u0430\u043c\u0435\u043d\u044f\u0442\u044c (\u041f\u041a\u041c \u00ab\u0417\u0430\u043c\u0435\u043d\u0438\u0442\u044c \u043d\u0430\u2026\u00bb \u0438\u043b\u0438 \u043f\u0435\u0440\u0432\u043e\u0435 \u043d\u0430\u0436\u0430\u0442\u0438\u0435). true \u2014 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u0437\u0430\u0434\u0430\u043d\u043e. */
    private boolean promptProgramReplacement() {
        String term = this.programSearchField != null ? this.programSearchField.getText() : "";
        TextInputDialog dialog = new TextInputDialog(this.programReplaceText != null ? this.programReplaceText : "");
        dialog.setTitle(I18n.text("app.024"));
        if (term != null && !term.isEmpty()) {
            dialog.setHeaderText(I18n.text("app.025") + term + I18n.text("app.026"));
        } else {
            dialog.setHeaderText(I18n.text("app.027"));
        }
        dialog.setContentText(I18n.text("app.028"));
        this.applyDialogTheme(dialog);
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return false;
        }
        this.programReplaceText = answer.get();
        this.updateProgressDisplay(0, I18n.text("app.029")
                + (this.programReplaceText.isEmpty() ? I18n.text("app.030") : this.programReplaceText));
        return true;
    }

    /** \u041f\u0440\u0438\u0432\u043e\u0434\u0438\u0442 \u0434\u0438\u0430\u043b\u043e\u0433 \u043a \u0442\u0435\u043a\u0443\u0449\u0435\u0439 \u0442\u0435\u043c\u0435 (\u0442\u043e\u0442 \u0436\u0435 app.css + \u043a\u043b\u0430\u0441\u0441 dark-theme) \u0438 \u043f\u0440\u0438\u0432\u044f\u0437\u044b\u0432\u0430\u0435\u0442 \u043a \u043e\u043a\u043d\u0443. */
    private void applyDialogTheme(javafx.scene.control.Dialog<?> dialog) {
        javafx.scene.control.DialogPane pane = dialog.getDialogPane();
        java.net.URL css = MainController.class.getResource("/app.css");
        if (css != null) {
            String href = css.toExternalForm();
            // Лист — на сцену окна, а не на саму панель: правилам нужен корень
            // сцены (.root), а лист, висящий на панели, его не видит.
            pane.getStylesheets().remove(href);
            if (pane.getScene() != null && !pane.getScene().getStylesheets().contains(href)) {
                pane.getScene().getStylesheets().add(href);
            } else if (pane.getScene() == null && !pane.getStylesheets().contains(href)) {
                pane.getStylesheets().add(href);
            }
        }
        pane.getStyleClass().remove("dark-theme");
        if (this.darkTheme) {
            pane.getStyleClass().add("dark-theme");
        }
        if (this.programSearchField != null && this.programSearchField.getScene() != null) {
            dialog.initOwner(this.programSearchField.getScene().getWindow());
        }
    }

    private static int countOccurrencesIgnoreCase(String text, String sub) {
        if (text == null || sub == null || sub.isEmpty()) {
            return 0;
        }
        String lowerText = text.toLowerCase(Locale.US);
        String lowerSub = sub.toLowerCase(Locale.US);
        int count = 0;
        int index = 0;
        while ((index = lowerText.indexOf(lowerSub, index)) >= 0) {
            ++count;
            index += lowerSub.length();
        }
        return count;
    }

    /** \u041c\u0435\u043d\u044f\u0435\u0442 \u0442\u0435\u043a\u0441\u0442 \u043a\u043d\u043e\u043f\u043a\u0438 \u00ab\u041d\u0430\u0439\u0442\u0438\u00bb \u043d\u0430 \u0438\u043a\u043e\u043d\u043a\u0443-\u043b\u0443\u043f\u0443 (\u043a\u0430\u043a \u0432 \u0438\u0441\u0442\u043e\u0440\u0438\u0438 \u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0439). */
    private void installFindButtonIcon() {
        if (this.buttonProgramFind == null) {
            return;
        }
        this.buttonProgramFind.setText("");
        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent("M9.5 2a7.5 7.5 0 0 1 5.93 12.09l4.24 4.24-1.34 1.34-4.24-4.24"
                + "A7.5 7.5 0 1 1 9.5 2zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z");
        // Цвет — из темы (app.css, find-icon): вшитый чёрный терялся на тёмной кнопке.
        icon.getStyleClass().add("find-icon");
        icon.setScaleX(0.85);
        icon.setScaleY(0.85);
        this.buttonProgramFind.setGraphic(icon);
        this.buttonProgramFind.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("app.031"))));
    }

    @FXML
    private void onStart() {
        // Код в двух окнах — сначала спрашиваем, какой канал гнать.
        if (!this.chooseChannelBeforeRun()) {
            return;
        }
        this.handleStart();
    }

    @FXML
    private void onReset() {
        this.handleReset();
    }

    @FXML
    private void onCycleStart() {
        if (!this.chooseChannelBeforeRun()) {
            return;
        }
        this.handleCycleStart();
    }

    @FXML
    private void onCyclePause() {
        this.handleCyclePause();
    }

    @FXML
    private void onSimulation3d() {
        if (this.useBothSimulationChannels()) this.setBothChannels(true);
        this.syncAppliedProgramFromEditor();
        WorkOffsetStore.applySinuTrainDemoLathePresetsIfUnset();
        this.refreshGraphManualOffsetsSnapshotFromSavedSettings();
        List<GCodeMoveData> moves = this.buildSimulationContourMoveDataList();
        Window owner = null;
        if (this.anchorPaneCanvas != null && this.anchorPaneCanvas.getScene() != null) {
            owner = this.anchorPaneCanvas.getScene().getWindow();
        }
        String programText = this.programEditor != null ? this.programEditor.getText() : this.appliedProgramText;
        if (programText == null || programText.isBlank()) {
            programText = this.appliedProgramText;
        }
        this.syncTurningSettingsFromGCode(programText);
        this.syncWorkOffsetsFromProgram(programText);

        // \u0420\u0430\u0437\u0431\u043e\u0440 \u043f\u0440\u043e\u0431\u043b\u0435\u043c \u043f\u0435\u0440\u0435\u0434 \u0437\u0430\u043f\u0443\u0441\u043a\u043e\u043c: \u043a\u0440\u0430\u0441\u043d\u044b\u0435 \u043d\u0435 \u043f\u0443\u0441\u043a\u0430\u044e\u0442 \u0434\u0430\u043b\u044c\u0448\u0435, \u0436\u0451\u043b\u0442\u044b\u0435 \u0441\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u044e\u0442.
        List<ProgramDiagnostics.Diagnostic> diagnostics =
                this.analyzeProgramDiagnostics(programText, moves.size(), moves);
        this.programDiagnostics = diagnostics;
        this.updateDiagnosticBadge();
        if (!diagnostics.isEmpty() && !this.resolveDiagnosticsBeforeSimulation(diagnostics)) {
            return;
        }

        if (!this.configureTurnoverBefore3d()) return;
        SimulationContext context = this.buildCompleteSimulationContext(moves);
        int initialContourMoveIndex = -1;
        this.simulation3dWindow.open(owner, context, this.darkTheme,
                line -> {
                    int side = this.simulation3dWindow.displayedSetupSide();
                    Platform.runLater(() -> {
                        if (line > 0 && side > 0 && side != this.activeSide) {
                            boolean both = this.bothChannelsActive;
                            this.switchSide(side);
                            if (both) this.setBothChannels(true);
                        }
                        this.navigateSimulationChannelLine(line);
                    });
                },
                true,
                initialContourMoveIndex);
    }

    /** Разбирает программу на проблемы (красные/жёлтые) для плашки и для запуска 3D. */
    private List<ProgramDiagnostics.Diagnostic> analyzeProgramDiagnostics(
            String programText, int moveCount, List<GCodeMoveData> moves) {
        WorkpieceDefinition inferred = null;
        try {
            inferred = GCodeProgramParser.inferWorkpiece(
                    programText, moves, this.buildSavedMachineConfiguration());
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Не удалось вычислить заготовку по траектории", exception);
        }
        if (inferred == null) {
            // inferWorkpiece сдаётся на крупных деталях (там стоит потолок 800 мм) — считаем сами,
            // иначе у «нет заготовки» не будет автоисправления именно там, где оно нужнее всего.
            inferred = this.workpieceFromMoveExtents(moves);
        }
        // Программа может адресовать инструмент и номером T, и номером ячейки магазина
        // (SINUMERIK с управлением инструментом), поэтому в «заведённые» идут оба.
        List<Integer> toolNumbers =
                new ArrayList<>(LatheCompensationProcessor.addressableToolNumbers(this.currentToolLibrary()));
        List<ProgramDiagnostics.Diagnostic> found = new ArrayList<>(
                ProgramDiagnostics.analyze(programText, moveCount, toolNumbers, inferred,
                        this.configuredWorkOffsetCodes()));
        // Заготовка из программы против габаритов траектории. inferWorkpiece возвращает
        // саму объявленную заготовку, когда она есть, поэтому сравнивать надо с расчётом
        // по ходам — иначе проверка всегда сходилась бы сама с собой.
        // Инструменты для 3D-среза: раньше это была отдельная проверка с окном «ОК»,
        // мимо деления на красные и жёлтые. Теперь она такая же диагностика, как все.
        found.addAll(this.toolSimulationProblems(programText, moves));
        ProgramDiagnostics.Diagnostic twoEnds = this.twoEndProgramProblem(programText);
        if (twoEnds != null) {
            found.add(twoEnds);
        }
        WorkpieceDefinition declared = GCodeProgramParser.findWorkpiece(programText).orElse(null);
        WorkpieceDefinition fromMoves = this.workpieceFromMoveExtents(moves);
        if (declared != null && declared.getShape() == WorkpieceDefinition.Shape.CYLINDER) {
            ProgramDiagnostics.Diagnostic mismatch =
                    ProgramDiagnostics.blankMismatch(declared, fromMoves);
            if (mismatch != null) {
                found.add(mismatch);
            }
        }
        return found;
    }

    /** Коды смещений, реально заведённые в настройках: G54… по счётчику плюс расширенные. */
    private java.util.Set<Integer> configuredWorkOffsetCodes() {
        java.util.TreeSet<Integer> codes = new java.util.TreeSet<>();
        int count = this.workOffsetCountField != null
                ? this.parseWorkOffsetCount(this.workOffsetCountField.getText())
                : UserSettings.getWorkOffsetCount();
        for (int i = 0; i < count; ++i) {
            codes.add(54 + i);
        }
        for (Integer code : UserSettings.getWorkOffsetExtraCodes()) {
            if (code != null && WorkOffsetStore.isSettableCode(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    /**
     * Заготовка по габаритам траектории — запасной расчёт без ограничения на диаметр.
     *
     * <p>Считаем ТОЛЬКО по рабочим ходам. Быстрые ходы уходят на смену инструмента
     * (у токарных программ это X600–700), металла они не снимают, и заготовка по ним
     * получалась в разы больше детали.
     */
    private WorkpieceDefinition workpieceFromMoveExtents(List<GCodeMoveData> moves) {
        if (moves == null || moves.isEmpty()) {
            return null;
        }
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        double maxAbsX = 0.0;
        for (GCodeMoveData move : moves) {
            if (move == null || move.rapid()) {
                continue;
            }
            zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
            zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            // Глубина хода, а не его начало. Подвод подачей начинается снаружи заготовки
            // («G0 X265», затем «G1 X249»), и по начальной точке заготовка выходила
            // Ø270 вместо Ø249 — по такой подсказке наладчик переписал WORKPIECE
            // на несуществующий диаметр. Металл снимается там, куда ход доходит.
            double deepest = Math.min(Math.abs(move.startX()), Math.abs(move.endX()));
            maxAbsX = Math.max(maxAbsX, deepest);
        }
        if (!Double.isFinite(zMin) || !Double.isFinite(zMax) || maxAbsX <= 0.0) {
            return null;
        }
        return new WorkpieceDefinition(
                WorkpieceDefinition.Shape.CYLINDER,
                maxAbsX * 1.02,
                Math.max(1.0, zMax - zMin),
                zMin,
                zMax,
                0);
    }

    /** Пересчитывает проблемы применённой программы и обновляет плашку (для 2D — без диалогов). */
    private void refreshProgramDiagnostics(String programText, int moveCount) {
        try {
            this.programDiagnostics = this.analyzeProgramDiagnostics(
                    programText, moveCount, this.buildSimulationContourMoveDataList());
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Диагностика программы не выполнена", exception);
            this.programDiagnostics = new ArrayList<>();
        }
        this.updateDiagnosticBadge();
    }

    /** Показывает худшую проблему у заголовка «Программа» — красным или жёлтым. */
    private void updateDiagnosticBadge() {
        if (this.programDiagnosticBadge == null) {
            return;
        }
        ProgramDiagnostics.Diagnostic worst = ProgramDiagnostics.worst(this.programDiagnostics);
        if (worst == null) {
            this.programDiagnosticBadge.setVisible(false);
            this.programDiagnosticBadge.setManaged(false);
            this.programDiagnosticBadge.setTooltip(null);
            return;
        }
        String suffix = this.programDiagnostics.size() > 1
                ? "  (+" + (this.programDiagnostics.size() - 1) + ")"
                : "";
        this.programDiagnosticBadge.setText(worst.code() + " · " + worst.title() + suffix);
        this.programDiagnosticBadge.getStyleClass().removeAll("diagnostic-error", "diagnostic-warning");
        this.programDiagnosticBadge.getStyleClass().add(
                worst.severity() == ProgramDiagnostics.Severity.ERROR
                        ? "diagnostic-error" : "diagnostic-warning");
        this.programDiagnosticBadge.setTooltip(ToolIconFactory.fastTooltip(
                new Tooltip(worst.details() + I18n.text("app.032"))));
        this.programDiagnosticBadge.setOnMouseClicked(event -> {
            if (!this.programDiagnostics.isEmpty()) {
                this.showDiagnosticsDialog(this.programDiagnostics, false);
            }
        });
        this.programDiagnosticBadge.setVisible(true);
        this.programDiagnosticBadge.setManaged(true);
    }

    /**
     * Диалог перед 3D-симуляцией. Возвращает true, если запускать можно.
     * Красная проблема дальше не пускает; жёлтую можно проигнорировать.
     */
    private boolean resolveDiagnosticsBeforeSimulation(List<ProgramDiagnostics.Diagnostic> diagnostics) {
        return this.showDiagnosticsDialog(diagnostics, true);
    }

    /**
     * Общий диалог проблем. При {@code forSimulation} у жёлтых есть «Игнорировать и продолжить».
     * Возвращает true, только если пользователь решил продолжить запуск.
     */
    private boolean showDiagnosticsDialog(
            List<ProgramDiagnostics.Diagnostic> diagnostics, boolean forSimulation) {
        boolean hasErrors = ProgramDiagnostics.hasErrors(diagnostics);
        ProgramDiagnostics.Diagnostic primary = ProgramDiagnostics.worst(diagnostics);
        if (primary == null) {
            return true;
        }
        Alert alert = new Alert(hasErrors ? Alert.AlertType.ERROR : Alert.AlertType.WARNING);
        alert.setTitle(hasErrors ? I18n.text("app.033") : I18n.text("app.034"));
        alert.setHeaderText(primary.code() + " · " + primary.title());

        StringBuilder body = new StringBuilder(primary.details());
        if (diagnostics.size() > 1) {
            body.append(I18n.text("app.035"));
            for (ProgramDiagnostics.Diagnostic diagnostic : diagnostics) {
                if (diagnostic != primary) {
                    body.append("\n  • ")
                            .append(diagnostic.severity() == ProgramDiagnostics.Severity.ERROR ? "[!] " : "[~] ")
                            .append(diagnostic.code()).append(" — ").append(diagnostic.title());
                }
            }
        }
        alert.setContentText(body.toString());

        ButtonType autoFix = primary.autoFixLabel() != null
                ? new ButtonType(I18n.text("app.036"), ButtonBar.ButtonData.OTHER) : null;
        ButtonType manualFix = new ButtonType(I18n.text("app.037"), ButtonBar.ButtonData.OTHER);
        ButtonType ignore = (!hasErrors && forSimulation)
                ? new ButtonType(I18n.text("app.038"), ButtonBar.ButtonData.OTHER) : null;
        ButtonType close = new ButtonType(forSimulation ? I18n.text("app.039") : I18n.text("app.040"),
                ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().clear();
        if (autoFix != null) {
            alert.getButtonTypes().add(autoFix);
        }
        alert.getButtonTypes().add(manualFix);
        if (ignore != null) {
            alert.getButtonTypes().add(ignore);
        }
        alert.getButtonTypes().add(close);
        // Своя иконка: стандартная в тёмной теме выглядит как крестик на белом квадрате.
        alert.setGraphic(hasErrors ? DialogIcons.error(46.0) : DialogIcons.warning(46.0));
        this.applyDialogTheme(alert);
        alert.getDialogPane().setMinWidth(560.0);
        // Окно диалога приходило пустым белым прямоугольником: содержимое собрано,
        // но окно успевало показаться до раскладки. Считаем стили и раскладку заранее
        // и разрешаем менять размер — штатный обход для диалогов JavaFX на Windows.
        alert.setResizable(true);
        alert.getDialogPane().applyCss();
        alert.getDialogPane().layout();

        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == close) {
            return false;
        }
        if (autoFix != null && answer.get() == autoFix) {
            this.applyDiagnosticAutoFix(primary, forSimulation);
            return false; // после правки повторяем запуск заново
        }
        if (answer.get() == manualFix) {
            this.goToDiagnosticLine(primary);
            return false;
        }
        return ignore != null && answer.get() == ignore;
    }

    /** Применяет автоисправление: правку текста программы либо добор данных в настройки. */
    private void applyDiagnosticAutoFix(ProgramDiagnostics.Diagnostic diagnostic, boolean retrySimulation) {
        // Правка настроек открывает окно настроек: добавленные строки надо дозаполнить
        // (радиус и длины инструмента, X/Z точки смещения). Сразу перезапускать 3D поверх
        // этого окна нельзя — счёт пойдёт по нулевым данным, и результат обманет.
        boolean settingsFix = !diagnostic.hasTextAutoFix()
                && (ProgramDiagnostics.CODE_TOOLS_MISSING.equals(diagnostic.code())
                        || ProgramDiagnostics.CODE_TOOL_EDGES_MISSING.equals(diagnostic.code())
                        || ProgramDiagnostics.CODE_WORK_OFFSETS_MISSING.equals(diagnostic.code())
                        || ProgramDiagnostics.CODE_TWO_ENDS_NO_OFFSET.equals(diagnostic.code()));
        try {
            if (diagnostic.hasTextAutoFix()) {
                String current = this.programEditor != null
                        ? this.programEditor.getText() : this.appliedProgramText;
                String fixed = diagnostic.applyAutoFix(current == null ? "" : current);
                if (this.programEditor != null) {
                    this.programEditor.setText(fixed);
                }
                this.programEditorEditingActive = true;
                this.applyProgramEditorChanges();
                this.updateProgressDisplay(0, I18n.text("app.041") + diagnostic.code() + " · " + diagnostic.title());
            } else if (ProgramDiagnostics.CODE_TOOLS_MISSING.equals(diagnostic.code())) {
                int added = this.addMissingToolsToLibrary();
                this.updateProgressDisplay(0, added > 0
                        ? I18n.text("app.042") + added
                                + I18n.text("app.043")
                        : I18n.text("app.044"));
                this.refreshDiagnosticsAfterSettingsFix();
            } else if (ProgramDiagnostics.CODE_TOOL_EDGES_MISSING.equals(diagnostic.code())) {
                int added = this.addMissingToolEdgesToLibrary();
                this.updateProgressDisplay(0, added > 0
                        ? I18n.text("app.045") + added
                                + I18n.text("app.046")
                        : I18n.text("app.044"));
                this.refreshDiagnosticsAfterSettingsFix();
            } else if (ProgramDiagnostics.CODE_WORK_OFFSETS_MISSING.equals(diagnostic.code())) {
                int added = this.addMissingWorkOffsetsToSettings();
                this.updateProgressDisplay(0, added > 0
                        ? I18n.text("app.047") + added
                                + I18n.text("app.048")
                        : I18n.text("app.044"));
                this.refreshDiagnosticsAfterSettingsFix();
            } else if (ProgramDiagnostics.CODE_TWO_ENDS_NO_OFFSET.equals(diagnostic.code())) {
                String done = this.applyTwoEndOffsetFix(diagnostic);
                this.updateProgressDisplay(0, done);
                this.refreshDiagnosticsAfterSettingsFix();
            } else {
                this.goToDiagnosticLine(diagnostic);
                return;
            }
            if (retrySimulation && !settingsFix) {
                Platform.runLater(this::onSimulation3d);
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Автоисправление не удалось", exception);
            this.showInfoDialog(I18n.text("app.049"),
                    I18n.text("app.050")
                            + (exception.getMessage() != null
                            ? exception.getMessage() : exception.getClass().getSimpleName()));
        }
    }

    /**
     * Добавляет в библиотеку номера инструментов, которые вызывает программа, но которых там нет.
     * Радиус и длины остаются нулевыми — их technolog уточняет вручную.
     */
    private int addMissingToolsToLibrary() {
        String programText = this.programEditor != null
                ? this.programEditor.getText() : this.appliedProgramText;
        if (programText == null) {
            return 0;
        }
        java.util.Set<Integer> used =
                new java.util.LinkedHashSet<>(GCodeProgramParser.parseToolNumberByLine(programText).values());
        used.remove(0);
        List<CncToolDefinition> library = this.currentToolLibrary();
        java.util.Set<Integer> known = new java.util.HashSet<>();
        for (CncToolDefinition tool : library) {
            known.add(tool.getToolNumber());
        }
        int added = 0;
        Integer firstAdded = null;
        for (Integer number : used) {
            if (known.contains(number)) {
                continue;
            }
            CncToolDefinition fresh = new CncToolDefinition();
            fresh.setToolNumber(number);
            fresh.setLocation(number);
            library.add(fresh);
            if (firstAdded == null) {
                firstAdded = number;
            }
            ++added;
        }
        if (added > 0) {
            this.displaySavedToolLibrary(library);
            ToolLibraryStore.save(library);
            this.revealSettingsAfterAutoFix(firstAdded, null);
        }
        return added;
    }

    /**
     * Заводит недостающие кромки D для инструментов, которые уже есть в библиотеке.
     *
     * <p>Размеры копируются с первой кромки того же номера T: у одной пластины кромки
     * обычно отличаются только вылетом, и поправить одно число проще, чем заполнять
     * строку с нуля. Радиус и длины всё равно стоит уточнить вручную.
     *
     * @return сколько кромок добавлено
     */
    private int addMissingToolEdgesToLibrary() {
        String programText = this.programEditor != null
                ? this.programEditor.getText() : this.appliedProgramText;
        if (programText == null || programText.isBlank()) {
            return 0;
        }
        List<CncToolDefinition> library = this.currentToolLibrary();
        if (library == null || library.isEmpty()) {
            return 0;
        }
        List<GCodeMoveData> moves = this.buildSimulationContourMoveDataList();
        Map<Integer, Integer> toolByLine = GCodeProgramParser.parseToolNumberByLine(programText);
        Map<Integer, Integer> dByLine = LatheCompensationProcessor.parseDNumberByLine(programText);
        java.util.LinkedHashMap<Integer, java.util.LinkedHashSet<Integer>> needed =
                new java.util.LinkedHashMap<>();
        for (GCodeMoveData move : moves) {
            if (move == null || move.rapid()) {
                continue;
            }
            int toolNumber = toolByLine.getOrDefault(move.sourceLine(), 0);
            if (toolNumber <= 0) {
                continue;
            }
            int edgeNumber = dByLine.getOrDefault(move.sourceLine(), 1);
            if (this.hasExactSimulationTool(library, toolNumber, edgeNumber)) {
                continue;
            }
            needed.computeIfAbsent(toolNumber, key -> new java.util.LinkedHashSet<>()).add(edgeNumber);
        }
        int added = 0;
        Integer firstTool = null;
        for (Map.Entry<Integer, java.util.LinkedHashSet<Integer>> entry : needed.entrySet()) {
            CncToolDefinition sample = null;
            for (CncToolDefinition tool : library) {
                if (tool.getToolNumber() == entry.getKey()) {
                    sample = tool;
                    break;
                }
            }
            if (sample == null) {
                continue; // номера T нет вовсе — это добирает E004
            }
            int nextLocation = library.stream().mapToInt(CncToolDefinition::getLocation).max().orElse(0);
            for (Integer edge : entry.getValue()) {
                CncToolDefinition fresh = this.copyToolWithEdge(sample, edge, ++nextLocation);
                library.add(fresh);
                if (firstTool == null) {
                    firstTool = entry.getKey();
                }
                ++added;
            }
        }
        if (added > 0) {
            this.displaySavedToolLibrary(library);
            ToolLibraryStore.save(library);
            this.revealSettingsAfterAutoFix(firstTool, null);
        }
        return added;
    }

    /** Копия инструмента с другой кромкой: геометрию сохраняем, ячейку берём свободную. */
    private CncToolDefinition copyToolWithEdge(CncToolDefinition sample, int edgeNumber, int location) {
        CncToolDefinition copy = new CncToolDefinition();
        copy.setLocation(location);
        copy.setName(sample.getName());
        copy.setType(sample.getType());
        copy.setToolNumber(sample.getToolNumber());
        copy.setEdgeNumber(edgeNumber);
        copy.setLengthX(sample.getLengthX());
        copy.setLengthZ(sample.getLengthZ());
        copy.setRadius(sample.getRadius());
        copy.setTypeCode(sample.getTypeCode());
        copy.setTypeIdentifier(sample.getTypeIdentifier());
        copy.setToolPosition(sample.getToolPosition());
        copy.setCutDirection(sample.getCutDirection());
        copy.setHolderAngleDeg(sample.getHolderAngleDeg());
        copy.setInsertAngleDeg(sample.getInsertAngleDeg());
        copy.setPlateLength(sample.getPlateLength());
        copy.setCutWidth(sample.getCutWidth());
        copy.setModelId(sample.getModelId());
        return copy;
    }

    /**
     * Заводит в настройках точки смещения, которые вызывает программа, но которых там нет.
     *
     * <p>G54…G73 задаются счётчиком подряд, поэтому счётчик поднимается до нужного номера;
     * расширенные G505…G599 добавляются отдельными кодами. Значения X/Z остаются нулевыми —
     * их вписывает наладчик, а мы лишь показываем строки в окне настроек.
     *
     * @return сколько точек добавлено
     */
    private int addMissingWorkOffsetsToSettings() {
        String programText = this.programEditor != null
                ? this.programEditor.getText() : this.appliedProgramText;
        if (programText == null || programText.isBlank()) {
            return 0;
        }
        this.ensureSettingsPanel();
        java.util.Set<Integer> configured = this.configuredWorkOffsetCodes();
        java.util.TreeSet<Integer> missing =
                new java.util.TreeSet<>(ProgramDiagnostics.usedWorkOffsetCodes(programText));
        missing.removeAll(configured);
        if (missing.isEmpty()) {
            return 0;
        }
        int count = this.workOffsetCountField != null
                ? this.parseWorkOffsetCount(this.workOffsetCountField.getText())
                : UserSettings.getWorkOffsetCount();
        java.util.TreeSet<Integer> extras = new java.util.TreeSet<>();
        for (Integer code : UserSettings.getWorkOffsetExtraCodes()) {
            if (code != null && WorkOffsetStore.isSettableCode(code)) {
                extras.add(code);
            }
        }
        for (Integer code : missing) {
            if (code > 73) {
                extras.add(code);
            }
        }
        count = workOffsetCountAfterAdding(count, missing);
        UserSettings.setWorkOffsetCount(count);
        UserSettings.setWorkOffsetExtraCodes(extras);
        for (Integer code : missing) {
            if (!UserSettings.hasWorkOffsetMm(code)) {
                UserSettings.setWorkOffsetMm(code, 0.0, 0.0);
            }
        }
        UserSettings.flush();
        if (this.workOffsetCountField != null) {
            this.workOffsetCountField.setText(String.valueOf(count));
        }
        WorkOffsetStore.loadFromUserSettings();
        this.loadWorkOffsetsFromUserSettings();
        this.rebuildWorkOffsetRows(true);
        this.syncWorkOffsetsToStore();
        this.redrawCanvas();
        this.revealSettingsAfterAutoFix(null, missing.first());
        return missing.size();
    }

    /**
     * Каким станет счётчик точек G54…, если завести все недостающие коды.
     * G54…G73 идут в настройках подряд, поэтому счётчик тянется до самого дальнего номера.
     */
    static int workOffsetCountAfterAdding(int currentCount, java.util.Collection<Integer> missing) {
        int count = currentCount;
        if (missing != null) {
            for (Integer code : missing) {
                if (code != null && code >= 54 && code <= 73) {
                    count = Math.max(count, code - 53);
                }
            }
        }
        return Math.max(0, Math.min(20, count));
    }

    /**
     * Показывает результат правки настроек: открывает окно настроек (или поднимает уже
     * открытое), обновляет таблицу инструментов и строки смещений, подсвечивает добавленное.
     * Без этого правка «в настройки» оставалась невидимой.
     */
    private void revealSettingsAfterAutoFix(Integer toolNumber, Integer offsetCode) {
        this.ensureSettingsPanel();
        this.displaySavedToolLibrary(ToolLibraryStore.load());
        this.rebuildWorkOffsetRows(true);
        if (this.settingsStage != null && this.settingsStage.isShowing()) {
            // Окно уже открыто: правка прошла мимо снимка «Отмены» — обновляем снимок,
            // иначе закрытие окна молча откатило бы автоисправление.
            this.settingsDraftBaseline = this.captureSettingsDraft();
            this.settingsStage.toFront();
        } else {
            // Сюда попадаем сразу после закрытия диалога проблем. Новое окно, открытое
            // в этот момент, на Windows иногда остаётся непрорисованным, поэтому
            // отдаём его следующему такту.
            Platform.runLater(this::openSettingsWindow);
        }
        Platform.runLater(() -> {
            if (this.toolsTableView != null && toolNumber != null) {
                for (CncToolDefinition tool : this.toolsTableView.getItems()) {
                    if (tool.getToolNumber() == toolNumber) {
                        this.toolsTableView.getSelectionModel().select(tool);
                        this.toolsTableView.scrollTo(tool);
                        break;
                    }
                }
            }
            this.highlightWorkOffsetRow(offsetCode);
        });
    }

    /** Подсвечивает строку смещения на несколько секунд, чтобы её было видно среди прочих. */
    private void highlightWorkOffsetRow(Integer offsetCode) {
        if (offsetCode == null || this.workOffsetsBox == null) {
            return;
        }
        for (Node node : this.workOffsetsBox.getChildren()) {
            if (!(node instanceof HBox row) || !offsetCode.equals(row.getUserData())) {
                continue;
            }
            if (!row.getStyleClass().contains("settings-just-added")) {
                row.getStyleClass().add("settings-just-added");
            }
            javafx.animation.PauseTransition hold =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(6.0));
            hold.setOnFinished(event -> row.getStyleClass().remove("settings-just-added"));
            hold.play();
            if (this.settingsPanelRoot != null
                    && this.settingsPanelRoot.lookup("#workOffsetsScroll") instanceof ScrollPane scroll) {
                scroll.setVvalue(1.0);
            }
            return;
        }
    }

    /** Пересчитывает плашку после правки, которая изменила настройки, а не текст программы. */
    /**
     * Вписывает Z второго нуля, чтобы половины детали встали на свои места.
     *
     * <p>Значение берётся из подписи автоисправления: там уже посчитано −длина
     * заготовки. Точка появляется в настройках, и её видно — наладчик может уточнить.
     */
    private String applyTwoEndOffsetFix(ProgramDiagnostics.Diagnostic diagnostic) {
        String programText = this.resolveProgramTextForGraph();
        java.util.List<Integer> used = new ArrayList<>();
        for (Integer code : ProgramDiagnostics.usedWorkOffsetCodes(programText)) {
            if (code != null && WorkOffsetStore.isSettableCode(code)) {
                used.add(code);
            }
        }
        double blankLength = this.declaredBlankLength(programText);
        if (used.size() < 2 || blankLength <= 0.0) {
            return I18n.text("app.044");
        }
        int second = used.get(1);
        for (int i = 1; i < used.size(); i++) {
            if (Math.abs(WorkOffsetStore.get(used.get(i)).zMm()) < 0.001) {
                second = used.get(i);
                break;
            }
        }
        int first = used.get(0);
        UserSettings.setWorkOffsetMm(first, WorkOffsetStore.get(first).xMm(), WorkOffsetStore.get(first).zMm());
        UserSettings.setWorkOffsetMm(second, WorkOffsetStore.get(second).xMm(), -blankLength);
        WorkOffsetStore.set(second, WorkOffsetStore.get(second).xMm(), -blankLength);
        int neededCount = Math.max(UserSettings.getWorkOffsetCount(),
                Math.max(first, second) - 54 + 1);
        UserSettings.setWorkOffsetCount(Math.max(1, Math.min(46, neededCount)));
        UserSettings.flush();
        this.refreshGraphManualOffsetsSnapshotFromSavedSettings();
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.redrawCanvas();
        return String.format(Locale.US, I18n.text("app.twoends.done"), second, -blankLength);
    }

    private void refreshDiagnosticsAfterSettingsFix() {
        String programText = this.programEditor != null
                ? this.programEditor.getText() : this.appliedProgramText;
        if (programText == null) {
            programText = "";
        }
        int moveCount;
        try {
            moveCount = this.getCachedToolMoves().size();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Не удалось пересчитать ходы после правки настроек", exception);
            moveCount = 0;
        }
        this.refreshProgramDiagnostics(programText, moveCount);
    }

    /** «Исправить вручную»: ставит курсор на проблемную строку либо открывает настройки. */
    private void goToDiagnosticLine(ProgramDiagnostics.Diagnostic diagnostic) {
        if (ProgramDiagnostics.CODE_TOOLS_MISSING.equals(diagnostic.code())
                || ProgramDiagnostics.CODE_TOOL_EDGES_MISSING.equals(diagnostic.code())
                || ProgramDiagnostics.CODE_WORK_OFFSETS_MISSING.equals(diagnostic.code())
                || "E003".equals(diagnostic.code())) {
            this.openSettingsWindow();
            return;
        }
        if (diagnostic.line() > 0 && this.programEditor != null) {
            this.programEditorEditingActive = true;
            this.programEditor.requestFocus();
            this.programEditor.selectLine(diagnostic.line());
            this.updateProgressDisplay(0, diagnostic.code() + I18n.text("app.051") + diagnostic.line());
        } else if (this.programEditor != null) {
            this.programEditor.requestFocus();
            this.updateProgressDisplay(0, diagnostic.code() + " · " + diagnostic.title());
        }
    }

    /**
     * Проблемы инструментов, из-за которых 3D-срез не построится.
     *
     * <p>Пустую библиотеку ловит E003, полностью отсутствующий номер T — E004,
     * поэтому здесь остаётся то, что они не видят: вызвана кромка D, которой нет
     * у заведённого инструмента, и рабочие ходы без активного T.
     */
    /**
     * Программа точит с двух концов, а Z второго нуля не задан.
     *
     * <p>Признак простой и измеримый: программа переключается между двумя нулевыми
     * точками, а разобранная длина детали больше объявленной заготовки. Значит половины
     * лежат от одного нуля, одна на другой. Для оси 2180 мм это видно сразу: разбор
     * даёт 2473 мм.
     *
     * <p>Проверка живёт здесь, а не в {@link ProgramDiagnostics}: нужны и разбор
     * траектории, и настройки смещений, а они есть только у контроллера.
     */
    private ProgramDiagnostics.Diagnostic twoEndProgramProblem(String programText) {
        if (programText == null || programText.isBlank()) {
            return null;
        }
        java.util.List<Integer> used = new ArrayList<>();
        for (Integer code : ProgramDiagnostics.usedWorkOffsetCodes(programText)) {
            if (code != null && WorkOffsetStore.isSettableCode(code)) {
                used.add(code);
            }
        }
        if (used.size() < 2) {
            return null;
        }
        double blankLength = this.declaredBlankLength(programText);
        if (blankLength <= 0.0) {
            return null;
        }
        // Длина по «сырым» координатам: без смещений, то есть как половины лежат сейчас.
        double zMin = Double.POSITIVE_INFINITY;
        double zMax = Double.NEGATIVE_INFINITY;
        for (ToolMove move : this.parseGCodeMovesRaw(programText)) {
            if (move.rapid) {
                continue;
            }
            zMin = Math.min(zMin, Math.min(move.startZ, move.endZ));
            zMax = Math.max(zMax, Math.max(move.startZ, move.endZ));
        }
        if (!Double.isFinite(zMin) || !Double.isFinite(zMax)) {
            return null;
        }
        double rawSpan = zMax - zMin;
        if (rawSpan <= blankLength * 1.05) {
            return null;
        }
        // Какому нулю не хватает Z: берём первый из использованных, у которого он нулевой.
        Integer missing = null;
        for (int i = 1; i < used.size(); i++) {
            int code = used.get(i);
            if (Math.abs(WorkOffsetStore.get(code).zMm()) < 0.001) {
                missing = code;
                break;
            }
        }
        if (missing == null) {
            return null;
        }
        double suggested = -blankLength;
        StringBuilder codes = new StringBuilder();
        for (int code : used) {
            if (codes.length() > 0) {
                codes.append(", ");
            }
            codes.append('G').append(code);
        }
        return ProgramDiagnostics.twoEndsNoOffset(
                codes.toString(), rawSpan, blankLength, missing, suggested);
    }

    /** Длина заготовки, объявленной в программе; 0, если её там нет. */
    private double declaredBlankLength(String programText) {
        java.util.Optional<WorkpieceDefinition> declared = GCodeProgramParser.findWorkpiece(programText);
        if (declared.isEmpty() || !declared.get().isValid()) {
            return 0.0;
        }
        WorkpieceDefinition workpiece = declared.get();
        double byRange = Math.abs(workpiece.getZMax() - workpiece.getZMin());
        return byRange > 0.001 ? byRange : workpiece.getLengthMm();
    }

    private List<ProgramDiagnostics.Diagnostic> toolSimulationProblems(
            String programText, List<GCodeMoveData> moves) {
        List<ProgramDiagnostics.Diagnostic> found = new ArrayList<>();
        List<CncToolDefinition> tools = this.simulationToolLibrary(programText);
        if (tools == null || tools.isEmpty() || moves == null || moves.isEmpty()) {
            return found;
        }
        Map<Integer, Integer> toolByLine = GCodeProgramParser.parseToolNumberByLine(programText);
        Map<Integer, Integer> dByLine = LatheCompensationProcessor.parseDNumberByLine(programText);
        java.util.Set<Integer> knownNumbers =
                LatheCompensationProcessor.addressableToolNumbers(tools);
        java.util.LinkedHashSet<String> pairs = new java.util.LinkedHashSet<>();
        List<String> where = new ArrayList<>();
        boolean anyToolCall = false;
        boolean hasCutMoveWithTool = false;
        int firstLine = 0;
        for (GCodeMoveData move : moves) {
            if (move == null || move.rapid()) {
                continue;
            }
            int toolNumber = toolByLine.getOrDefault(move.sourceLine(), 0);
            if (toolNumber <= 0) {
                continue;
            }
            anyToolCall = true;
            hasCutMoveWithTool = true;
            if (!knownNumbers.contains(toolNumber)) {
                continue; // это E004 — там добирается сам номер инструмента
            }
            int edgeNumber = dByLine.getOrDefault(move.sourceLine(), 1);
            if (edgeNumber == 0) {
                // D0 у Siemens — «коррекция выключена», а не отсутствующая кромка.
                continue;
            }
            if (this.hasExactSimulationTool(tools, toolNumber, edgeNumber)) {
                continue;
            }
            String label = "T" + toolNumber + " D" + edgeNumber;
            if (pairs.add(label)) {
                if (where.size() < 8) {
                    where.add(this.lineLabelForToolError(move.sourceLine()) + ": " + label);
                }
                if (firstLine == 0) {
                    firstLine = move.sourceLine();
                }
            }
        }
        if (!anyToolCall) {
            for (Integer number : toolByLine.values()) {
                if (number != null && number > 0) {
                    anyToolCall = true;
                    break;
                }
            }
            if (anyToolCall && !hasCutMoveWithTool) {
                found.add(ProgramDiagnostics.noActiveToolMoves());
            }
            return found;
        }
        if (!pairs.isEmpty()) {
            found.add(ProgramDiagnostics.toolEdgesMissing(new ArrayList<>(pairs), where, firstLine));
        }
        return found;
    }

    private boolean hasExactSimulationTool(List<CncToolDefinition> tools, int toolNumber, int edgeNumber) {
        if (LatheCompensationProcessor.resolveTool(tools, toolNumber, edgeNumber) != null) {
            return true;
        }
        for (CncToolDefinition tool : tools) {
            if (tool.getToolNumber() == toolNumber && tool.getEdgeNumber() == edgeNumber) {
                return true;
            }
        }
        return false;
    }

    private String lineLabelForToolError(int sourceLine) {
        String label = this.formatNBlockLabel(this.getProgramLineText(sourceLine));
        if (label == null || label.isBlank()) {
            return I18n.text("app.052") + sourceLine;
        }
        return label;
    }

    private SimulationContext buildSimulationContext(List<GCodeMoveData> movesFromCaller) {
        SimulationContext current = this.buildActiveSideContext(movesFromCaller);
        if (this.sharedTurnoverStock == null || !current.getMachineConfiguration().isVerticalLathe()) return current;
        if (this.activeSide == 1) return current.withWorkpiece(this.sharedTurnoverStock);
        SimulationContext first = this.buildStoredSideContext(1);
        return first == null ? current : current.afterTurnover(first.withWorkpiece(this.sharedTurnoverStock), this.turnoverZSum);
    }

    /** The 3D result covers both setups, independently of which side is open in the editor. */
    private SimulationContext buildCompleteSimulationContext(List<GCodeMoveData> movesFromCaller) {
        SimulationContext current = this.buildSimulationContext(movesFromCaller);
        if (this.sharedTurnoverStock == null || !current.getMachineConfiguration().isVerticalLathe()
                || this.activeSide == 2) return this.resolveCompleteStock(current);
        SimulationContext second = this.buildStoredSideContext(2);
        return this.resolveCompleteStock(second == null ? current : second.afterTurnover(current, this.turnoverZSum));
    }

    private SimulationContext resolveCompleteStock(SimulationContext context) {
        var result = com.sergey.pisarev.service.ProgramBoreResolver.resolve(context);
        if (this.sharedTurnoverStock != null && UserSettings.isApproximatePreformEnabled()
                && result.getMachineConfiguration().isVerticalLathe())
            result = com.sergey.pisarev.service.ApproximatePreform.apply(result,
                    UserSettings.getTurnoverValue("preform.allowance",
                            com.sergey.pisarev.service.ApproximatePreform.suggestedAllowance(result)));
        return result;
    }

    private SimulationContext buildActiveSideContext(List<GCodeMoveData> movesFromCaller) {
        if (this.useBothSimulationChannels()) {
            SimulationContext left = this.buildSingleChannelContext(this.leftProgramEditor.getText(), List.of());
            SimulationContext right = this.buildSingleChannelContext(this.rightProgramEditor.getText(), List.of());
            return com.sergey.pisarev.service.MultiChannelSimulation.merge(left, right);
        }
        return this.buildSingleChannelContext(this.resolveProgramTextForGraph(), movesFromCaller);
    }

    private SimulationContext buildStoredSideContext(int side) {
        String left = UserSettings.getChannelSideProgram(2, side);
        String right = UserSettings.getChannelSideProgram(1, side);
        if (this.bothChannelsActive && !left.isBlank() && !right.isBlank())
            return com.sergey.pisarev.service.MultiChannelSimulation.merge(
                    this.buildSingleChannelContext(left, List.of()), this.buildSingleChannelContext(right, List.of()));
        if (this.bothChannelsActive) {
            String available = left.isBlank() ? right : left;
            return available.isBlank() ? null : this.buildSingleChannelContext(available, List.of());
        }
        String selected = this.rightPaneActive ? right : left;
        return selected.isBlank() ? null : this.buildSingleChannelContext(selected, List.of());
    }

    /** A shared blank and a setup datum are explicit inputs, never a wheel-shaped preset. */
    private boolean configureTurnoverBefore3d() {
        if (!this.buildSavedMachineConfiguration().isVerticalLathe()) return true;
        boolean hasOther = !UserSettings.getChannelSideProgram(1, other(this.activeSide)).isBlank()
                || !UserSettings.getChannelSideProgram(2, other(this.activeSide)).isBlank();
        if (!hasOther) { this.sharedTurnoverStock = null; return true; }
        if (this.buildStoredSideContext(other(this.activeSide)) == null) {
            this.showInfoDialog(I18n.text("turnover.two.title"), I18n.text("turnover.two.body"));
            return false;
        }
        SimulationContext first = this.activeSide == 1 ? this.buildActiveSideContext(List.of()) : this.buildStoredSideContext(1);
        if (first == null || first.getWorkpiece() == null || !first.getWorkpiece().isValid()) {
            this.sharedTurnoverStock = null;
            this.showInfoDialog(I18n.text("turnover.first.title"), I18n.text("turnover.first.body"));
            return false;
        }
        var stock = first.getWorkpiece();
        Double height = this.parameterVariables().values().get("WHEEL_HEIGHT");
        double suggestedSum = height != null && Double.isFinite(height) ? height : Double.NaN;
        double sum = UserSettings.getTurnoverValue("zSum", suggestedSum);
        double zMin = Double.isFinite(sum) ? Math.min(stock.getZMin(), sum-stock.getZMax()) : stock.getZMin();
        double zMax = Double.isFinite(sum) ? Math.max(stock.getZMax(), sum-stock.getZMin()) : stock.getZMax();
        var use = new CheckBox(I18n.text("turnover.use"));
        use.setSelected(true);
        var datum = new TextField(Double.isFinite(sum) ? Double.toString(sum) : "");
        var diameter = new TextField(Double.toString(UserSettings.getTurnoverValue("diameter", stock.getDiameterMm())));
        var origin = new TextField(Double.toString(UserSettings.getTurnoverValue("zMin", zMin)));
        var length = new TextField(Double.toString(UserSettings.getTurnoverValue("length", zMax-zMin)));
        var bore = new TextField(Double.toString(UserSettings.getTurnoverValue("initialBoreDiameter", stock.getInitialBoreDiameterMm())));
        var preform = new CheckBox(I18n.text("turnover.preform"));
        preform.setId("approximatePreform"); preform.setSelected(UserSettings.isApproximatePreformEnabled());
        var otherSetup=this.buildStoredSideContext(other(this.activeSide));
        double suggestedAllowance=Math.max(com.sergey.pisarev.service.ApproximatePreform.suggestedAllowance(first),
                com.sergey.pisarev.service.ApproximatePreform.suggestedAllowance(otherSetup));
        var allowance = new TextField(Double.toString(UserSettings.getTurnoverValue("preform.allowance",suggestedAllowance)));
        allowance.setId("preformAllowance");
        var fields = new javafx.scene.layout.GridPane(); fields.setHgap(12); fields.setVgap(10);
        fields.addRow(0, new Label(I18n.text("turnover.datum")), datum);
        fields.addRow(1, new Label(I18n.text("turnover.diameter")), diameter);
        fields.addRow(2, new Label(I18n.text("turnover.origin")), origin);
        fields.addRow(3, new Label(I18n.text("turnover.length")), length);
        fields.addRow(4, new Label(I18n.text("turnover.bore")), bore);
        fields.add(preform,0,5,2,1);
        fields.addRow(6,new Label(I18n.text("turnover.allowance")),allowance);
        var hint = new Label(I18n.text("turnover.hint"));
        hint.setWrapText(true); hint.setMaxWidth(560);
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.text("turnover.title"));
        alert.setHeaderText(I18n.format(this.activeSide == 2 ? "turnover.header.second" : "turnover.header.first",
                this.activeSide));
        alert.setGraphic(null);
        var run = new ButtonType(I18n.text("turnover.build"), ButtonBar.ButtonData.OK_DONE);
        // Стандартная ButtonType.CANCEL подписана на языке JVM («Cancel»), а не интерфейса.
        alert.getButtonTypes().setAll(run, new ButtonType(I18n.text("app.039"), ButtonBar.ButtonData.CANCEL_CLOSE));
        alert.getDialogPane().setContent(new javafx.scene.layout.VBox(14, use, fields, hint));
        this.applyDialogStyles(alert); this.setDialogIcon(alert);
        Runnable validate = () -> {
            fields.setDisable(!use.isSelected());
            double h = setupNumber(datum), d = setupNumber(diameter), z = setupNumber(origin), l = setupNumber(length);
            double b = setupNumber(bore), a = setupNumber(allowance);
            allowance.setDisable(!preform.isSelected());
            alert.getDialogPane().lookupButton(run).setDisable(use.isSelected()
                    && (!Double.isFinite(h) || !Double.isFinite(d) || !Double.isFinite(z) || !Double.isFinite(l)
                    || !Double.isFinite(b) || d<=0 || l<=0 || b<0 || b>=d
                    || (preform.isSelected() && (!Double.isFinite(a) || a<=0))));
        };
        for (var field : List.of(datum, diameter, origin, length, bore, allowance)) field.textProperty().addListener((o,a,b) -> validate.run());
        preform.selectedProperty().addListener((o,a,b) -> validate.run());
        use.selectedProperty().addListener((o,a,b) -> validate.run()); validate.run();
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != run) return false;
        if (!use.isSelected()) { this.sharedTurnoverStock = null; return true; }
        this.turnoverZSum = setupNumber(datum);
        double d = setupNumber(diameter), z = setupNumber(origin), l = setupNumber(length);
        this.sharedTurnoverStock = new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER, d, l, z, z+l, 0)
                .withInitialBoreDiameter(setupNumber(bore));
        UserSettings.setTurnoverValues(this.turnoverZSum, d, z, l);
        UserSettings.setTurnoverBoreDiameter(setupNumber(bore));
        UserSettings.setApproximatePreform(preform.isSelected(),preform.isSelected()?setupNumber(allowance):0);
        return true;
    }

    private static double setupNumber(TextField field) {
        try { return Double.parseDouble(field.getText().trim().replace(',', '.')); }
        catch (NumberFormatException ex) { return Double.NaN; }
    }

    private boolean useBothSimulationChannels() {
        return this.paneHasCode(this.leftProgramEditor) && this.paneHasCode(this.rightProgramEditor)
                && this.bothChannelsActive;
    }

    private SimulationContext buildSingleChannelContext(String text, List<GCodeMoveData> movesFromCaller) {
        List<GCodeMoveData> contourMoves = this.buildSimulationContourMoveDataList(text);
        final List<GCodeMoveData> contourForMesh = contourMoves.isEmpty()
                ? (movesFromCaller != null ? movesFromCaller : List.of())
                : contourMoves;
        final List<GCodeMoveData> moves = contourForMesh;
        MachineConfiguration baseConfiguration = this.buildSavedMachineConfiguration();
        final String programText = com.sergey.pisarev.service.SinumerikCycleTimeEstimator.resolveMotionExpressions(
                LatheCompensationProcessor.resolveOffnExpressions(text, this.parameterVariables().values()),
                this.parameterVariables().values());
        java.util.Optional<WorkpieceDefinition> parsedWorkpiece = GCodeProgramParser.findWorkpiece(programText);
        WorkpieceDefinition workpiece = parsedWorkpiece.orElseGet(
                () -> GCodeProgramParser.inferWorkpiece(programText, moves, baseConfiguration));
        boolean workpieceFromProgram = parsedWorkpiece.isPresent();
        if (workpiece != null && workpiece.isValid()) {
            workpiece = this.workpieceForGraphDisplay(workpiece, programText, moves);
        }
        if (workpiece != null && workpiece.isValid() && !workpieceFromProgram) {
            workpiece = this.expandWorkpieceToContainMoves(workpiece, moves);
        } else if ((workpiece == null || !workpiece.isValid())
                && baseConfiguration.isUseBlankDiameter()
                && baseConfiguration.getBlankDiameterMm() > 0.0) {
            // Галочка включена: диаметр из настроек + границы Z по ходам программы
            // (прежняя рабочая логика; G-code не меняется).
            double diameter = baseConfiguration.getBlankDiameterMm();
            double zMin = Double.POSITIVE_INFINITY;
            double zMax = Double.NEGATIVE_INFINITY;
            // Только рабочие ходы: отводы уходят далеко от детали (на программе колеса
            // это Z=335 при детали шириной 178), и заготовка вытягивалась вдвое длиннее
            // себя — рядом с деталью висел необработанный цилиндр.
            for (GCodeMoveData move : moves) {
                if (move == null || move.rapid()) {
                    continue;
                }
                zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
                zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
            }
            if (!Double.isFinite(zMin)) {
                for (GCodeMoveData move : moves) {
                    if (move == null) {
                        continue;
                    }
                    zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
                    zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
                }
            }
            if (!Double.isFinite(zMin)) {
                zMin = 0.0;
                zMax = Math.max(10.0, diameter);
            }
            if (zMax <= zMin) {
                zMax = zMin + Math.max(10.0, diameter);
            }
            workpiece = new WorkpieceDefinition(
                    WorkpieceDefinition.Shape.CYLINDER,
                    diameter,
                    zMax - zMin,
                    zMin,
                    zMax,
                    0);
            workpieceFromProgram = false;
        }
        List<CncToolDefinition> tools = this.simulationToolLibrary(programText);
        List<GCodeMoveData> toolMoves = GCodeProgramParser.enrichMovesWithTools(programText, moves);
        List<GCodeMoveData> contourEnriched = GCodeProgramParser.enrichMovesWithTools(programText, contourForMesh);
        MachineConfiguration configuration = baseConfiguration;
        if (workpiece != null || GCodeProgramParser.looksLikeLatheProgram(contourEnriched)) {
            java.util.HashSet<Character> axes = new java.util.HashSet<>();
            axes.add('X');
            axes.add('Z');
            double blankDiameter = workpiece != null
                    ? workpiece.getDiameterMm()
                    : baseConfiguration.getBlankDiameterMm();
            double cupRadiusMm = baseConfiguration.getCupRadiusMm();
            boolean useCupRadius = baseConfiguration.isUseCupRadius();
            java.util.OptionalDouble programCup = GCodeProgramParser.findCupRadiusMm(programText);
            if (programCup.isPresent()) {
                cupRadiusMm = programCup.getAsDouble();
                useCupRadius = true;
            } else if (baseConfiguration.isUseCupRadius() && baseConfiguration.getCupRadiusMm() > 0.0) {
                cupRadiusMm = baseConfiguration.getCupRadiusMm();
                useCupRadius = true;
            }
            MachineConfiguration.MachineType machineType = baseConfiguration.getMachineType();
            MachineConfiguration.ControlSystem controlSystem = baseConfiguration.getControlSystem();
            if (controlSystem == MachineConfiguration.ControlSystem.SIEMENS_MILLING) {
                controlSystem = MachineConfiguration.ControlSystem.SIEMENS_TURNING;
            }
            configuration = new MachineConfiguration(
                    controlSystem,
                    machineType,
                    axes,
                    blankDiameter,
                    true,
                    cupRadiusMm,
                    useCupRadius);
        }
        this.syncWorkOffsetsToStore();
        Map<Integer, WorkOffsetValues> offsetMap = new HashMap<>();
        int offsetCount = UserSettings.getWorkOffsetCount();
        if (offsetCount <= 0) {
            offsetCount = 3;
        }
        for (int i = 0; i < offsetCount; i++) {
            int code = 54 + i;
            offsetMap.put(code, WorkOffsetStore.get(code));
        }
        ProgramWorkOffsetScan offsetScan = GCodeProgramParser.scanWorkOffsets(programText);
        int activeOffset = offsetScan.activeCode();
        if (activeOffset == 500) {
            activeOffset = 54;
        } else if (activeOffset < 54) {
            activeOffset = this.activeWorkOffsetFromProgram;
            if (activeOffset == 500) {
                activeOffset = 54;
            }
        }
        return new SimulationContext(
                toolMoves.isEmpty() ? contourEnriched : toolMoves,
                contourEnriched,
                configuration,
                workpiece,
                tools,
                UserSettings.getFeedOverridePercent(),
                UserSettings.getSpindleOverridePercent(),
                programText,
                workpieceFromProgram,
                activeOffset,
                offsetMap);
    }

    private void navigateProgramFromSimulation(int sourceLine) {
        if (this.programEditor == null) {
            return;
        }
        if (sourceLine <= 0) {
            this.clearGraphEditorSelection();
            this.clearProgramHighlight();
            this.redrawCanvas();
            return;
        }
        this.handleGraphPointClickFromLine(sourceLine);
    }

    private void navigateSimulationChannelLine(int sourceLine) {
        if (this.useBothSimulationChannels()) {
            int offset = com.sergey.pisarev.service.MultiChannelSimulation.secondChannelLineOffset(
                    this.leftProgramEditor.getText());
            boolean right = sourceLine > offset;
            this.setActivePane(right);
            this.setBothChannels(true);
            sourceLine = right ? sourceLine - offset : sourceLine;
        }
        this.navigateProgramFromSimulation(sourceLine);
    }

    private void handleEditorCaretLine(int sourceLine) {
        if (this.programEditor == null || sourceLine <= 0) {
            return;
        }
        this.simulation3dWindow.syncEditorLine(sourceLine);
        this.highlightedProgramLine = sourceLine;
        List<ToolMove> moves = this.getActiveToolMoves();
        int moveIndex = this.findLastMoveIndexForSourceLine(moves, sourceLine);
        if (moveIndex >= 0) {
            this.highlightedProgramMove = moves.get(moveIndex);
        } else {
            this.highlightedProgramMove = null;
            if (!this.executionRunning) {
                this.currentExecutionIndex = -1;
            }
        }
        this.syncProgramHighlight();
        if (!this.executionRunning) {
            this.redrawCanvas();
        }
    }

    private void handleEditorLineClick(int line) {
        if (this.programEditor == null || line <= 0) {
            return;
        }
        this.handleEditorCaretLine(line);
        if (!this.canSelectGraphPoint()) {
            this.clearGraphEditorSelection();
            this.redrawCanvas();
            return;
        }
        List<ToolMove> moves = this.getActiveToolMoves();
        int moveIndex = this.findLastMoveIndexForSourceLine(moves, line);
        if (moveIndex < 0) {
            this.redrawCanvas();
            return;
        }
        this.focusGraphOnProgramPoint(line, moves.get(moveIndex), moveIndex);
    }

    private void clearGraphEditorSelection() {
        this.graphEditorSelectionIndex = -1;
    }

    private void handleGraphPointClickFromLine(int sourceLine) {
        String text = this.programEditor.getText();
        List<ToolMove> list = this.parseGCodeMoves(text.isBlank() ? this.appliedProgramText : text);
        if (list.isEmpty()) {
            this.selectProgramLineExact(sourceLine);
            return;
        }
        int moveIndex = this.findLastMoveIndexForSourceLine(list, sourceLine);
        if (moveIndex >= 0 && moveIndex < list.size()) {
            this.highlightedProgramLine = sourceLine;
            if (this.canSelectGraphPoint()) {
                this.graphEditorSelectionIndex = moveIndex;
                this.syncEditorHighlightFromGraph(sourceLine);
            }
            this.highlightProgramMove(list.get(moveIndex));
        } else {
            this.highlightedProgramMove = null;
            this.highlightedProgramLine = sourceLine;
            this.syncProgramHighlight();
        }
        this.selectProgramLineExact(sourceLine);
        if (this.programEditor != null) {
            this.programEditor.showLine(sourceLine);
        }
    }

    /**
     * Keep WORKPIECE dimensions from the program, but move its Z window into the active
     * program frame when Siemens extended frames such as G507 put cutting moves elsewhere.
     */
    private WorkpieceDefinition workpieceForGraphDisplay(WorkpieceDefinition workpiece, String programText) {
        String text = programText != null ? programText : this.resolveProgramTextForGraph();
        ArrayList<GCodeMoveData> moves = new ArrayList<>();
        for (ToolMove move : this.parseGCodeMoves(text)) {
            moves.add(new GCodeMoveData(
                    move.startX,
                    move.startZ,
                    move.endX,
                    move.endZ,
                    move.rapid,
                    move.sourceLine,
                    move.arcSegment,
                    0,
                    1,
                    move.arcEnd));
        }
        return this.workpieceForGraphDisplay(workpiece, text, moves);
    }

    private WorkpieceDefinition workpieceForGraphDisplay(
            WorkpieceDefinition workpiece,
            String programText,
            List<GCodeMoveData> moves
    ) {
        if (workpiece == null || !workpiece.isValid() || moves == null || moves.isEmpty()) {
            return workpiece;
        }
        // A declared blank is material, not a camera bounding box. Repositioning
        // it to the cutting range removes unmachined stock at the ends. Keep its
        // coordinates; diagnostics already report cuts outside WORKPIECE.
        //
        // Исключение — запись WORKPIECE(...,D,L): она задаёт только диаметр и длину,
        // положения вдоль Z в ней нет вовсе. Оставлять такой пруток там, куда его
        // положил разбор, значит ставить его наугад: у вала, который точат с двух
        // сторон, обработка уходила за заготовку на длину второй стороны. Двигаем
        // только такие, привязанные — не трогаем.
        if (workpiece.isZAnchored() && GCodeProgramParser.findWorkpiece(programText).isPresent()) {
            return workpiece;
        }
        if (this.workpieceCoversCuttingMoves(workpiece, moves)) {
            return workpiece;
        }
        double length = Math.max(workpiece.getLengthMm(), workpiece.getZMax() - workpiece.getZMin());
        if (!Double.isFinite(length) || length <= 0.0) {
            return workpiece;
        }
        ZBounds programBounds = this.moveZBounds(moves, false);
        ZBounds cuttingBounds = this.moveZBounds(moves, true);
        if (programBounds == null && cuttingBounds == null) {
            return workpiece;
        }
        if (programBounds == null) {
            programBounds = cuttingBounds;
        }
        if (cuttingBounds == null) {
            cuttingBounds = programBounds;
        }
        double zMin = this.bestProgramWorkpieceZMin(length, programBounds, cuttingBounds);
        double zMax = zMin + length;
        // Если объявленной длины не хватает на всё резание, тянем заготовку до него:
        // иначе инструмент режет воздух там, где металл обязан быть, и 3D показывает
        // срез «где-то посередине». О расхождении с программой предупреждает W106.
        if (Double.isFinite(cuttingBounds.min) && Double.isFinite(cuttingBounds.max)) {
            zMin = Math.min(zMin, cuttingBounds.min);
            zMax = Math.max(zMax, cuttingBounds.max);
        }
        return workpiece.withZRange(zMin, zMax, zMax - zMin);
    }

    /**
     * Заготовка накрывает всё резание по Z.
     *
     * <p>Раньше здесь проверялось «хоть один ход задевает заготовку», и этого хватало,
     * чтобы объявить всё в порядке. Заготовка, записанная как WORKPIECE(...,D,L) — а эта
     * форма всегда читается как Z 0…L, — у детали, которая точится от Z−1751, задевала
     * резание только своим краем: проверка молчала, заготовка оставалась на месте, и в 3D
     * срез получался «где-то посередине». Теперь требуем, чтобы резание целиком лежало
     * внутри заготовки.
     */
    private boolean workpieceCoversCuttingMoves(WorkpieceDefinition workpiece, List<GCodeMoveData> moves) {
        boolean hasCuttingMove = false;
        double zLo = Double.POSITIVE_INFINITY;
        double zHi = Double.NEGATIVE_INFINITY;
        for (GCodeMoveData move : moves) {
            if (move == null || move.rapid()) {
                continue;
            }
            hasCuttingMove = true;
            zLo = Math.min(zLo, Math.min(move.startZ(), move.endZ()));
            zHi = Math.max(zHi, Math.max(move.startZ(), move.endZ()));
        }
        if (!hasCuttingMove) {
            return true;
        }
        return zLo >= workpiece.getZMin() - 0.5 && zHi <= workpiece.getZMax() + 0.5;
    }

    private ZBounds moveZBounds(List<GCodeMoveData> moves, boolean cuttingOnly) {
        ZBounds bounds = new ZBounds();
        for (GCodeMoveData move : moves) {
            if (move == null || (cuttingOnly && move.rapid())) {
                continue;
            }
            bounds.add(move.startZ());
            bounds.add(move.endZ());
        }
        return bounds.isValid() ? bounds : null;
    }

    private double bestProgramWorkpieceZMin(double length, ZBounds programBounds, ZBounds cuttingBounds) {
        double[] candidates = new double[]{
                programBounds.min,
                programBounds.max - length,
                cuttingBounds.min,
                cuttingBounds.max - length,
                (cuttingBounds.min + cuttingBounds.max - length) * 0.5
        };
        double bestZMin = programBounds.min;
        double bestScore = Double.POSITIVE_INFINITY;
        for (double candidate : candidates) {
            if (!Double.isFinite(candidate)) {
                continue;
            }
            double score = this.workpieceWindowScore(candidate, length, programBounds, cuttingBounds);
            if (score < bestScore) {
                bestScore = score;
                bestZMin = candidate;
            }
        }
        return bestZMin;
    }

    private double workpieceWindowScore(double zMin, double length, ZBounds programBounds, ZBounds cuttingBounds) {
        double zMax = zMin + length;
        double score = this.boundsOutsideWindow(cuttingBounds, zMin, zMax) * 10000.0;
        score += this.boundsOutsideWindow(programBounds, zMin, zMax) * 2.0;
        score += Math.min(Math.abs(zMin - programBounds.min), Math.abs(zMax - programBounds.max)) * 0.01;
        score += Math.abs(zMin - programBounds.min) * 0.001;
        return score;
    }

    private double boundsOutsideWindow(ZBounds bounds, double zMin, double zMax) {
        double outside = 0.0;
        if (bounds.min < zMin) {
            outside += zMin - bounds.min;
        }
        if (bounds.max > zMax) {
            outside += bounds.max - zMax;
        }
        return outside;
    }

    private static final class ZBounds {
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        private void add(double z) {
            if (!Double.isFinite(z)) {
                return;
            }
            this.min = Math.min(this.min, z);
            this.max = Math.max(this.max, z);
        }

        private boolean isValid() {
            return Double.isFinite(this.min) && Double.isFinite(this.max);
        }
    }

    /** Расширить Z-диапазон WORKPIECE, если траектория выходит за границы (диаметр из УП). */
    private WorkpieceDefinition expandWorkpieceToContainMoves(WorkpieceDefinition workpiece, List<GCodeMoveData> moves) {
        if (workpiece == null || !workpiece.isValid() || moves == null || moves.isEmpty()) {
            return workpiece;
        }
        double zMin = workpiece.getZMin();
        double zMax = workpiece.getZMax();
        // Растягиваем только под рабочие ходы: отводы и смена инструмента уходят от
        // детали далеко (Z=335 при колесе шириной 178 мм), и заготовка вырастала в
        // необработанный цилиндр рядом с деталью.
        for (GCodeMoveData move : moves) {
            if (move == null || move.rapid()) {
                continue;
            }
            zMin = Math.min(zMin, Math.min(move.startZ(), move.endZ()));
            zMax = Math.max(zMax, Math.max(move.startZ(), move.endZ()));
        }
        if (Math.abs(zMin - workpiece.getZMin()) < 1.0E-6 && Math.abs(zMax - workpiece.getZMax()) < 1.0E-6) {
            return workpiece;
        }
        return workpiece.withZRange(zMin, zMax, Math.max(workpiece.getLengthMm(), zMax - zMin));
    }

    /**
     * Ходы для графика и 3D: активный канал или оба сразу.
     *
     * <p>Оба канала складываются в один список и попадают в одну систему координат:
     * они и на станке работают одну деталь с одной стороны, канал 2 по осям U/W,
     * канал 1 по X/Z. Связующего хода между программами не появляется: ходы хранятся
     * отрезками, а не как одна непрерывная ломаная.
     */
    private List<ToolMove> parseMovesForView(String text) {
        if (!this.useBothSimulationChannels()) {
            return this.parseGCodeMoves(text);
        }
        ArrayList<ToolMove> merged = new ArrayList<>();
        merged.addAll(this.parseGCodeMoves(this.leftProgramEditor.getText()));
        merged.addAll(this.parseGCodeMoves(this.rightProgramEditor.getText()));
        return merged;
    }

    private List<GCodeMoveData> buildContourMoveDataList() {
        String text = this.resolveProgramTextForGraph();
        ArrayList<GCodeMoveData> result = new ArrayList<>();
        // Со смещениями нулевой точки, как и график. Раньше здесь был «сырой» разбор без
        // них, и получалось расхождение: 2D показывал деталь собранной, а проверка,
        // расчёт заготовки и 3D считали по несобранным координатам. Для программы,
        // которая точит с двух концов (G54 на один конец, G55 на другой), это и есть
        // разница между осью 2180 мм и «валом» 2622 мм.
        for (ToolMove move : this.parseMovesForView(text)) {
            result.add(new GCodeMoveData(
                    move.startX,
                    move.startZ,
                    move.endX,
                    move.endZ,
                    move.rapid,
                    move.sourceLine,
                    move.arcSegment,
                    0,
                    1,
                    move.arcEnd));
        }
        return SiemensCycleExpander.expand(text, result);
    }

    private List<GCodeMoveData> buildSimulationContourMoveDataList() {
        return this.buildSimulationContourMoveDataList(this.resolveProgramTextForGraph());
    }

    private List<GCodeMoveData> buildSimulationContourMoveDataList(String text) {
        ArrayList<GCodeMoveData> result = new ArrayList<>();
        for (ToolMove move : this.parseGCodeMoves(text)) {
            result.add(new GCodeMoveData(
                    move.startX,
                    move.startZ,
                    move.endX,
                    move.endZ,
                    move.rapid,
                    move.sourceLine,
                    move.arcSegment,
                    0,
                    1,
                    move.arcEnd));
        }
        List<GCodeMoveData> expanded = SiemensCycleExpander.expand(text, result);
        List<GCodeMoveData> filtered = this.filterSimulationPartFrameMoves(text, expanded);
        // Preserve NC millimetres, including uncut stock and out-of-stock moves.
        // Fit only the camera; stretching the path here distorted every radius/arc
        // along Z and made the first cutting point become the left stock face.
        return filtered;
    }

    private List<GCodeMoveData> filterSimulationPartFrameMoves(String programText, List<GCodeMoveData> moves) {
        if (programText == null || programText.isBlank() || moves == null || moves.isEmpty()) {
            return moves == null ? List.of() : moves;
        }
        PartFrameLines frameLines = this.collectPartFrameLines(programText);
        if (!frameLines.shouldFilter()) {
            return moves;
        }
        ArrayList<GCodeMoveData> filtered = new ArrayList<>();
        for (GCodeMoveData move : moves) {
            if (move != null && frameLines.partLines.contains(move.sourceLine())) {
                filtered.add(move);
            }
        }
        if (filtered.isEmpty()) {
            return moves;
        }
        return this.dropLeadingSimulationPositioningMoves(filtered);
    }

    private List<GCodeMoveData> dropLeadingSimulationPositioningMoves(List<GCodeMoveData> moves) {
        int firstFeed = -1;
        for (int i = 0; i < moves.size(); i++) {
            GCodeMoveData move = moves.get(i);
            if (move != null && !move.rapid()) {
                firstFeed = i;
                break;
            }
        }
        if (firstFeed <= 0) {
            return moves;
        }
        return new ArrayList<>(moves.subList(firstFeed, moves.size()));
    }

    private PartFrameLines collectPartFrameLines(String programText) {
        PartFrameLines result = new PartFrameLines();
        boolean partFrameActive = false;
        boolean toolSelected = false;
        String[] lines = programText.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = GCodeProgramParser.stripFrameNumberForParse(
                    GCodeProgramParser.stripCommentsForParse(lines[i])).toUpperCase(Locale.US);
            Integer fixture = this.readWorkOffsetCode(line);
            if (fixture != null) {
                if (this.isMachineFrameCode(fixture)) {
                    result.sawMachineFrame = true;
                    partFrameActive = false;
                } else if (this.isPartFrameCode(fixture)) {
                    result.sawPartFrame = true;
                    partFrameActive = true;
                }
            }
            if (this.lineHasToolSelection(line)) {
                toolSelected = true;
            }
            if (partFrameActive && toolSelected) {
                result.partLines.add(i + 1);
            }
        }
        return result;
    }

    private boolean lineHasToolSelection(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        for (String token : line.trim().split("\\s+")) {
            if (token.matches("T\\d+") || token.matches("T=.+")) {
                return true;
            }
        }
        return false;
    }

    private boolean isMachineFrameCode(int code) {
        return code == 500 || code == 53;
    }

    private boolean isPartFrameCode(int code) {
        return (code >= 54 && code <= 73) || (code >= 501 && code <= 599);
    }

    private static final class PartFrameLines {
        private final java.util.Set<Integer> partLines = new HashSet<>();
        private boolean sawMachineFrame;
        private boolean sawPartFrame;

        private boolean shouldFilter() {
            return this.sawMachineFrame && this.sawPartFrame && !this.partLines.isEmpty();
        }
    }

    private List<GCodeMoveData> buildGCodeMoveDataList() {
        String text = this.programEditor != null ? this.programEditor.getText() : this.appliedProgramText;
        if (text == null || text.isBlank()) {
            text = this.appliedProgramText;
        }
        List<GCodeMoveData> expanded = this.buildContourMoveDataList();
        List<CncToolDefinition> tools = this.simulationToolLibrary(text);
        return LatheCompensationProcessor.apply(text, expanded, tools);
    }

    private void syncAxisModeForProgram(String programText) {
        if (programText == null || programText.isBlank()) {
            return;
        }
        List<GCodeMoveData> probe = this.buildContourMoveDataList();
        if (GCodeProgramParser.findWorkpiece(programText).isPresent()
                || GCodeProgramParser.looksLikeLatheProgram(probe)) {
            if (this.axisMode != AxisMode.XZ) {
                this.setAxisMode(AxisMode.XZ);
            }
        }
    }

    /** Настройки станка/чашки/заготовки — только после «Сохранить» (UserSettings). */
    private MachineConfiguration buildSavedMachineConfiguration() {
        MachineConfiguration.ControlSystem control = this.parseControlSystem(UserSettings.getControlSystem());
        MachineConfiguration.MachineType machine = this.parseMachineType(UserSettings.getMachineType());
        java.util.HashSet<Character> axes = new java.util.HashSet<>();
        axes.add('X');
        axes.add('Z');
        if (machine == MachineConfiguration.MachineType.HAAS_CM1
                || control == MachineConfiguration.ControlSystem.SIEMENS_MILLING) {
            axes.add('Y');
        }
        return new MachineConfiguration(
                control,
                machine,
                axes,
                UserSettings.getBlankDiameterMm(),
                UserSettings.isUseBlankDiameter(),
                UserSettings.getCupRadiusMm(),
                UserSettings.isUseCupRadius());
    }

    private MachineConfiguration buildMachineConfiguration() {
        this.ensureSettingsPanel();
        MachineConfiguration.ControlSystem control = this.readSelectedControlSystem();
        MachineConfiguration.MachineType machine = this.readSelectedMachineType();
        java.util.HashSet<Character> axes = new java.util.HashSet<>();
        axes.add('X');
        axes.add('Z');
        if (machine == MachineConfiguration.MachineType.HAAS_CM1
                || control == MachineConfiguration.ControlSystem.SIEMENS_MILLING) {
            axes.add('Y');
        }
        double blankDiameter = 0.0;
        boolean useBlank = this.checkBoxUseBlankDiameter != null && this.checkBoxUseBlankDiameter.isSelected();
        if (this.turningBlankDiameter != null) {
            OptionalDouble parsed = MainController.parsePositiveNumber(this.turningBlankDiameter.getText());
            if (parsed.isPresent()) {
                blankDiameter = parsed.getAsDouble();
            }
        }
        double cupRadius = 0.0;
        boolean useCup = this.checkBoxUseCupRadius != null && this.checkBoxUseCupRadius.isSelected();
        if (this.cupRadius != null) {
            OptionalDouble cupParsed = MainController.parsePositiveNumber(this.cupRadius.getText());
            if (cupParsed.isPresent()) {
                cupRadius = cupParsed.getAsDouble();
            }
        }
        return new MachineConfiguration(control, machine, axes, blankDiameter, useBlank, cupRadius, useCup);
    }

    @FXML
    private void onSettings() {
        this.openSettingsWindow();
    }

    private void ensureSettingsPanel() {
        if (this.settingsPanelRoot != null) {
            return;
        }
        URL url = MainController.class.getResource("/settings-panel.fxml");
        if (url != null) {
            try {
                this.settingsPanelRoot = new FXMLLoader(url, I18n.bundle()).load();
                this.bindSettingsPanelFields();
            }
            catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Failed to load settings-panel.fxml", exception);
            }
        }
        if (this.settingsPanelRoot == null) {
            this.settingsPanelRoot = this.buildSettingsPanel();
            this.bindSettingsPanelFields();
        }
    }

    private void bindSettingsPanelFields() {
        if (this.settingsPanelRoot == null) {
            return;
        }
        this.turningBlankDiameter = (TextField)this.settingsPanelRoot.lookup("#turningBlankDiameter");
        this.checkBoxUseBlankDiameter = (CheckBox)this.settingsPanelRoot.lookup("#checkBoxUseBlankDiameter");
        this.cupRadius = (TextField)this.settingsPanelRoot.lookup("#cupRadius");
        this.checkBoxUseCupRadius = (CheckBox)this.settingsPanelRoot.lookup("#checkBoxUseCupRadius");
        this.checkBoxEquidistant = (CheckBox)this.settingsPanelRoot.lookup("#checkBoxEquidistant");
        this.equidistantRadiusSourceComboBox =
                (ComboBox<String>)this.settingsPanelRoot.lookup("#equidistantRadiusSourceComboBox");
        this.equidistantRadius = (TextField)this.settingsPanelRoot.lookup("#equidistantRadius");
        this.equidistantAllowance = (TextField)this.settingsPanelRoot.lookup("#equidistantAllowance");
        this.markSettingsCombo(this.equidistantRadiusSourceComboBox);
        this.applySettingsShortFieldWidth(this.equidistantRadius);
        this.applySettingsShortFieldWidth(this.equidistantAllowance);
        this.setupEquidistantSettings();
        this.labelCupOuterDiameter = (Label)this.settingsPanelRoot.lookup("#labelCupOuterDiameter");
        this.workOffsetCountField = (TextField)this.settingsPanelRoot.lookup("#workOffsetCountField");
        this.applySettingsShortFieldWidth(this.turningBlankDiameter);
        this.applySettingsShortFieldWidth(this.cupRadius);
        this.applySettingsShortFieldWidth(this.workOffsetCountField);
        this.activeWorkOffsetLabel = (Label)this.settingsPanelRoot.lookup("#activeWorkOffsetLabel");
        this.workOffsetsBox = (VBox)this.settingsPanelRoot.lookup("#workOffsetsBox");
        if (this.workOffsetsBox == null) {
            // Список смещений лежит внутри ScrollPane. Пока панель не в сцене, скин
            // ScrollPane не создан, его содержимое ещё не является дочерним узлом —
            // и lookup его не находит. Поэтому берём контент у самого ScrollPane.
            Node offsetsScroll = this.settingsPanelRoot.lookup("#workOffsetsScroll");
            if (offsetsScroll instanceof ScrollPane scrollPane
                    && scrollPane.getContent() instanceof VBox offsetsBox) {
                this.workOffsetsBox = offsetsBox;
            }
        }
        this.controlSystemComboBox = (ComboBox<String>)this.settingsPanelRoot.lookup("#controlSystemComboBox");
        this.machineTypeComboBox = (ComboBox<String>)this.settingsPanelRoot.lookup("#machineTypeComboBox");
        this.markSettingsCombo(this.controlSystemComboBox);
        this.markSettingsCombo(this.machineTypeComboBox);
        this.axisLabelX = (Label)this.settingsPanelRoot.lookup("#axisLabelX");
        this.axisLabelY = (Label)this.settingsPanelRoot.lookup("#axisLabelY");
        this.axisLabelZ = (Label)this.settingsPanelRoot.lookup("#axisLabelZ");
        this.axisLabelA = (Label)this.settingsPanelRoot.lookup("#axisLabelA");
        this.axisLabelB = (Label)this.settingsPanelRoot.lookup("#axisLabelB");
        this.axisLabelC = (Label)this.settingsPanelRoot.lookup("#axisLabelC");
        this.setupMachineSettingsUi();
        this.setupToolsAndOverrideSettingsUi();
        this.setupGeometryCalculations();
        this.setupWorkOffsets();
        this.showDataFolderLocation();
        this.installSettingsCardsResponsiveLayout();
    }

    /** Показывает папку данных и предупреждает, если на носитель нельзя записать. */
    private void showDataFolderLocation() {
        if (this.settingsPanelRoot == null) {
            return;
        }
        Node node = this.settingsPanelRoot.lookup("#dataFolderLabel");
        if (!(node instanceof Label label)) {
            return;
        }
        label.setText(PortableStorage.describeStorage());
        label.getStyleClass().removeAll("diagnostic-badge", "diagnostic-warning");
        if (PortableStorage.mode() == PortableStorage.StorageMode.READ_ONLY) {
            label.getStyleClass().addAll("diagnostic-badge", "diagnostic-warning");
        }
    }

    /**
     * Раскладывает карточки настроек по числу колонок, которое реально помещается по ширине.
     * Нужно из-за масштабирования Windows: при 150% логических пикселей меньше, и жёсткие
     * четыре колонки просто обрезались бы по правому краю окна.
     */
    private void installSettingsCardsResponsiveLayout() {
        if (this.settingsPanelRoot == null) {
            return;
        }
        Node gridNode = this.settingsPanelRoot.lookup("#settingsCardsGrid");
        if (!(gridNode instanceof GridPane grid)) {
            return;
        }
        List<Node> cards = new ArrayList<>(grid.getChildren());
        if (cards.isEmpty()) {
            return;
        }
        this.settingsCardColumns = -1;
        grid.widthProperty().addListener(
                (observable, oldValue, newValue) -> this.applySettingsCardColumns(grid, cards));
        this.applySettingsCardColumns(grid, cards);
    }

    private void applySettingsCardColumns(GridPane grid, List<Node> cards) {
        double width = grid.getWidth();
        if (width <= 0.0) {
            return;
        }
        double gap = grid.getHgap();
        int columns = (int) Math.floor((width + gap) / (SETTINGS_CARD_MIN_WIDTH + gap));
        columns = Math.max(1, Math.min(cards.size(), columns));
        if (columns == this.settingsCardColumns) {
            return;
        }
        this.settingsCardColumns = columns;
        grid.getColumnConstraints().clear();
        for (int i = 0; i < columns; ++i) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / columns);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }
        for (int i = 0; i < cards.size(); ++i) {
            GridPane.setColumnIndex(cards.get(i), i % columns);
            GridPane.setRowIndex(cards.get(i), i / columns);
        }
    }

    private void applySettingsShortFieldWidth(TextField field) {
        if (field == null) {
            return;
        }
        field.setMinWidth(120.0);
        field.setPrefWidth(SETTINGS_SHORT_FIELD_WIDTH);
        field.setMaxWidth(SETTINGS_SHORT_FIELD_WIDTH);
    }

    private void markSettingsCombo(ComboBox<String> comboBox) {
        if (comboBox != null && !comboBox.getStyleClass().contains("settings-combo")) {
            comboBox.getStyleClass().add("settings-combo");
        }
        if (comboBox != null) {
            comboBox.setButtonCell(this.createSettingsComboCell());
            comboBox.setCellFactory(listView -> this.createSettingsComboCell());
        }
    }

    private ListCell<String> createSettingsComboCell() {
        return new ListCell<>() {
            {
                if (!this.getStyleClass().contains("settings-combo-cell")) {
                    this.getStyleClass().add("settings-combo-cell");
                }
                this.hoverProperty().addListener((observable, was, now) -> this.applySettingsComboCellStyle());
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                this.setText(empty || item == null ? null : item);
                this.applySettingsComboCellStyle();
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                this.applySettingsComboCellStyle();
            }

            private void applySettingsComboCellStyle() {
                // Выбранный пункт цветом не выделяем (выбор виден в самом поле);
                // лёгкая подсветка только под курсором.
                String textColor = MainController.this.darkTheme ? "#f8fafc" : "#0f172a";
                String background = this.isHover() && !this.isEmpty()
                        ? (MainController.this.darkTheme ? "#243049" : "#e8f0fb")
                        : "transparent";
                this.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: " + background + ";");
            }
        };
    }

    private Parent buildSettingsPanel() {
        VBox box = new VBox(10.0);
        box.getStyleClass().add("sidebar-card");
        box.setPadding(new Insets(10.0));
        Label titleMachine = new Label(I18n.text("app.053"));
        titleMachine.getStyleClass().add("section-title");
        Label controlLabel = new Label(I18n.text("app.054"));
        this.controlSystemComboBox = new ComboBox<>();
        this.markSettingsCombo(this.controlSystemComboBox);
        Label machineLabel = new Label(I18n.text("app.055"));
        this.machineTypeComboBox = new ComboBox<>();
        this.markSettingsCombo(this.machineTypeComboBox);
        Label axesLabel = new Label(I18n.text("app.056"));
        HBox axesRow = new HBox(8.0);
        axesRow.getStyleClass().add("machine-axes-row");
        this.axisLabelX = new Label("X");
        this.axisLabelY = new Label("Y");
        this.axisLabelZ = new Label("Z");
        this.axisLabelA = new Label("A");
        this.axisLabelB = new Label("B");
        this.axisLabelC = new Label("C");
        for (Label axisLabel : new Label[]{this.axisLabelX, this.axisLabelY, this.axisLabelZ, this.axisLabelA, this.axisLabelB, this.axisLabelC}) {
            axisLabel.getStyleClass().add("machine-axis-label");
        }
        axesRow.getChildren().addAll(this.axisLabelX, this.axisLabelY, this.axisLabelZ, this.axisLabelA, this.axisLabelB, this.axisLabelC);
        Label titleTurning = new Label(I18n.text("app.057"));
        titleTurning.getStyleClass().add("section-title");
        HBox blankRow = new HBox(8.0);
        Label blankLabel = new Label(I18n.text("app.058"));
        HBox.setHgrow(blankLabel, Priority.ALWAYS);
        this.checkBoxUseBlankDiameter = new CheckBox(I18n.text("app.059"));
        blankRow.getChildren().addAll(blankLabel, this.checkBoxUseBlankDiameter);
        this.turningBlankDiameter = new TextField();
        this.turningBlankDiameter.setPromptText(I18n.text("app.060"));
        Label titleCup = new Label(I18n.text("app.061"));
        titleCup.getStyleClass().add("section-title");
        HBox cupRow = new HBox(8.0);
        Label cupLabel = new Label(I18n.text("app.062"));
        cupLabel.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(
                I18n.text("app.063"))));
        HBox.setHgrow(cupLabel, Priority.ALWAYS);
        this.checkBoxUseCupRadius = new CheckBox(I18n.text("app.059"));
        cupRow.getChildren().addAll(cupLabel, this.checkBoxUseCupRadius);
        this.cupRadius = new TextField();
        this.cupRadius.setPromptText(I18n.text("app.064"));
        Label cupDiameterLabel = new Label(I18n.text("app.065"));
        this.labelCupOuterDiameter = new Label("\u2014");
        this.labelCupOuterDiameter.getStyleClass().add("result-label");
        Label titleOffsets = new Label(I18n.text("app.066"));
        titleOffsets.getStyleClass().add("section-title");
        Label countLabel = new Label(I18n.text("app.067"));
        this.workOffsetCountField = new TextField();
        this.workOffsetCountField.setPromptText(I18n.text("app.068"));
        this.workOffsetsBox = new VBox(6.0);
        box.getChildren().addAll(
                titleMachine,
                controlLabel,
                this.controlSystemComboBox,
                machineLabel,
                this.machineTypeComboBox,
                axesLabel,
                axesRow,
                new Separator(),
                titleTurning,
                blankRow,
                this.turningBlankDiameter,
                new Separator(),
                titleCup,
                cupRow,
                this.cupRadius,
                cupDiameterLabel,
                this.labelCupOuterDiameter,
                new Separator(),
                titleOffsets,
                countLabel,
                this.workOffsetCountField,
                this.workOffsetsBox);
        Label titleTools = new Label(I18n.text("app.069"));
        titleTools.getStyleClass().add("section-title");
        Label toolsHint = new Label(I18n.text("app.070"));
        toolsHint.getStyleClass().add("muted-label");
        VBox toolsTableHost = new VBox();
        toolsTableHost.setId("toolsTableHost");
        toolsTableHost.setMinHeight(320.0);
        toolsTableHost.setPrefHeight(360.0);
        HBox toolsButtons = new HBox(8.0);
        this.addToolButton = new Button(I18n.text("app.071"));
        this.addToolButton.getStyleClass().addAll("secondary-button", "settings-tool-action-button", "settings-tool-add-button");
        this.pickToolTypeButton = new Button(I18n.text("app.072"));
        this.pickToolTypeButton.getStyleClass().addAll("secondary-button", "settings-tool-action-button", "settings-tool-edit-button");
        this.removeToolButton = new Button(I18n.text("app.073"));
        this.removeToolButton.getStyleClass().addAll("secondary-button", "settings-tool-action-button", "settings-tool-delete-button");
        this.addToolButton.setMinWidth(106.0);
        this.pickToolTypeButton.setMinWidth(116.0);
        this.removeToolButton.setMinWidth(96.0);
        toolsButtons.getChildren().addAll(this.addToolButton, this.pickToolTypeButton, this.removeToolButton);
        HBox overrideDialsRow = new HBox(16.0);
        overrideDialsRow.setId("overrideDialsRow");
        overrideDialsRow.setAlignment(Pos.TOP_CENTER);
        box.getChildren().addAll(
                new Separator(),
                titleTools,
                toolsHint,
                toolsTableHost,
                toolsButtons,
                new Separator(),
                overrideDialsRow);
        this.setupMachineSettingsUi();
        this.setupToolsAndOverrideSettingsUi();
        return box;
    }

    private void setupToolsAndOverrideSettingsUi() {
        if (this.toolsSettingsInitialized) {
            return;
        }
        VBox toolsHost = this.settingsPanelRoot != null
                ? (VBox)this.settingsPanelRoot.lookup("#toolsTableHost")
                : null;
        HBox overrideRow = this.settingsPanelRoot != null
                ? (HBox)this.settingsPanelRoot.lookup("#overrideDialsRow")
                : null;
        if (toolsHost == null || overrideRow == null) {
            return;
        }
        if (this.addToolButton == null) {
            this.addToolButton = (Button)this.settingsPanelRoot.lookup("#addToolButton");
        }
        if (this.removeToolButton == null) {
            this.removeToolButton = (Button)this.settingsPanelRoot.lookup("#removeToolButton");
        }
        if (this.pickToolTypeButton == null) {
            this.pickToolTypeButton = (Button)this.settingsPanelRoot.lookup("#pickToolTypeButton");
        }
        this.toolsMachine = this.readSelectedMachineType();
        this.toolsObservableList = FXCollections.observableArrayList(ToolLibraryStore.load(this.toolsMachine));
        this.toolsObservableList.forEach(this::localizeGeneratedToolName);
        this.toolsObservableList.addListener((javafx.collections.ListChangeListener<CncToolDefinition>)
                change -> this.updateToolsTableHeight());
        this.toolsTableView = this.createToolsTableView();
        this.toolsMachineLabel = new Label();
        this.toolsMachineLabel.setId("toolsMachineLabel");
        this.toolsMachineLabel.getStyleClass().add("settings-hint");
        this.updateToolsMachineLabel();
        toolsHost.getChildren().setAll(this.toolsMachineLabel, this.toolsTableView);
        // Карточка инструментов тянется на всю высоту окна; таблица должна тянуться
        // вместе с ней, иначе под строками оставалось пустое поле карточки.
        VBox.setVgrow(this.toolsTableView, Priority.ALWAYS);
        this.toolsTableView.setMaxHeight(Double.MAX_VALUE);
        this.updateToolsTableHeight();
        this.feedOverrideDial = new ThermoOverrideControl(
                I18n.text("app.074"),
                I18n.text("app.075"),
                FEED_OVERRIDE_STOPS,
                UserSettings.getFeedOverridePercent());
        this.spindleOverrideDial = new ThermoOverrideControl(
                I18n.text("app.076"),
                I18n.text("app.077"),
                SPINDLE_OVERRIDE_STOPS,
                UserSettings.getSpindleOverridePercent());
        overrideRow.getChildren().setAll(this.feedOverrideDial, this.spindleOverrideDial);
        // F/S override сохраняются в UserSettings только по кнопке «Сохранить».
        if (this.addToolButton != null) {
            if (!this.addToolButton.getStyleClass().contains("settings-tool-action-button")) {
                this.addToolButton.getStyleClass().addAll("settings-tool-action-button", "settings-tool-add-button");
            }
            this.addToolButton.setMinWidth(106.0);
            this.addToolButton.setOnAction(event -> this.addToolRow());
        }
        if (this.removeToolButton != null) {
            if (!this.removeToolButton.getStyleClass().contains("settings-tool-action-button")) {
                this.removeToolButton.getStyleClass().addAll("settings-tool-action-button", "settings-tool-delete-button");
            }
            this.removeToolButton.setMinWidth(96.0);
            this.removeToolButton.setOnAction(event -> this.removeSelectedToolRow());
        }
        if (this.pickToolTypeButton != null) {
            if (!this.pickToolTypeButton.getStyleClass().contains("settings-tool-action-button")) {
                this.pickToolTypeButton.getStyleClass().addAll("settings-tool-action-button", "settings-tool-edit-button");
            }
            this.pickToolTypeButton.setText(I18n.text("app.072"));
            this.pickToolTypeButton.setMinWidth(116.0);
            this.pickToolTypeButton.setOnAction(event -> this.pickToolTypeForSelection());
        }
        this.toolsSettingsInitialized = true;
    }

    private void pickToolTypeForSelection() {
        if (this.toolsTableView == null) {
            return;
        }
        CncToolDefinition selected = this.toolsTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Window owner = this.settingsStage != null ? this.settingsStage : null;
        ToolTypePickerDialog.show(owner, this.darkTheme).ifPresent(selection -> {
            ToolTypeEntry entry = selection.entry();
            selected.setTypeCode(entry.typeCode());
            selected.setTypeIdentifier(entry.identifier());
            selected.setType(entry.category().name().toLowerCase(Locale.US));
            selected.setName(ToolTypeCatalog.russianName(entry));
            selected.setToolPosition(selection.position());
            selected.setCutDirection(ToolCutDirection.BOTH);
            selected.setHolderAngleDeg(0.0);
            selected.setInsertAngleDeg(0.0);
            selected.setPlateLength(0.0);
            selected.setCutWidth(0.0);
            this.applyToolTypeDefaults(selected);
            this.toolsTableView.refresh();
        });
    }

    private TableView<CncToolDefinition> createToolsTableView() {
        TableView<CncToolDefinition> table = new TableView<>(this.toolsObservableList);
        table.setEditable(true);
        table.setFixedCellSize(54.0);
        table.setMinHeight(320.0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("tools-settings-table");
        TableColumn<CncToolDefinition, String> typeCol = new TableColumn<>(I18n.text("app.078"));
        typeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().displayType()));
        typeCol.setCellFactory(column -> this.toolTextCell());
        typeCol.setPrefWidth(210);
        TableColumn<CncToolDefinition, Integer> locCol = new TableColumn<>(I18n.text("app.079"));
        locCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getLocation()).asObject());
        locCol.setCellFactory(column -> this.editableToolCell(new IntegerStringConverter(),
                (tool, value) -> tool.setLocation(value != null ? value : 1)));
        locCol.setOnEditCommit(event -> {
            event.getRowValue().setLocation(event.getNewValue() != null ? event.getNewValue() : 1);
        });
        locCol.setPrefWidth(70);
        locCol.setMaxWidth(86);
        locCol.setStyle("-fx-alignment: CENTER;");
        TableColumn<CncToolDefinition, String> nameCol = new TableColumn<>(I18n.text("app.080"));
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));
        nameCol.setCellFactory(column -> this.editableToolCell(this.stringConverter(),
                (tool, value) -> tool.setName(value)));
        nameCol.setOnEditCommit(event -> {
            event.getRowValue().setName(event.getNewValue());
        });
        nameCol.setPrefWidth(170);
        TableColumn<CncToolDefinition, Integer> edgeCol = new TableColumn<>(I18n.text("app.081"));
        edgeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getEdgeNumber()).asObject());
        edgeCol.setCellFactory(column -> this.editableToolCell(new IntegerStringConverter(),
                (tool, value) -> tool.setEdgeNumber(value != null ? value : 1)));
        edgeCol.setOnEditCommit(event -> {
            event.getRowValue().setEdgeNumber(event.getNewValue() != null ? event.getNewValue() : 1);
        });
        edgeCol.setPrefWidth(78);
        edgeCol.setMaxWidth(94);
        edgeCol.setStyle("-fx-alignment: CENTER;");
        TableColumn<CncToolDefinition, Integer> posCol = new TableColumn<>(I18n.text("app.082"));
        posCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getToolPosition()).asObject());
        posCol.setCellFactory(column -> this.toolPositionCell());
        posCol.setPrefWidth(108);
        posCol.setMinWidth(96);
        posCol.setStyle("-fx-alignment: CENTER;");
        TableColumn<CncToolDefinition, Integer> toolCol = new TableColumn<>(I18n.text("app.083"));
        toolCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getToolNumber()).asObject());
        toolCol.setCellFactory(column -> this.editableToolCell(new IntegerStringConverter(),
                (tool, value) -> tool.setToolNumber(value != null ? value : 1)));
        toolCol.setOnEditCommit(event -> {
            event.getRowValue().setToolNumber(event.getNewValue() != null ? event.getNewValue() : 1);
        });
        toolCol.setPrefWidth(104);
        toolCol.setMaxWidth(124);
        toolCol.setStyle("-fx-alignment: CENTER;");
        TableColumn<CncToolDefinition, ToolCutDirection> directionCol = new TableColumn<>(I18n.text("app.084"));
        directionCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(
                data.getValue().getCutDirection() != null
                        ? data.getValue().getCutDirection()
                        : ToolCutDirection.BOTH));
        directionCol.setCellFactory(column -> this.toolDirectionCell());
        directionCol.setOnEditCommit(event -> {
            if (ToolTypeCatalog.supportsCutDirection(event.getRowValue())) {
                event.getRowValue().setCutDirection(event.getNewValue() != null
                        ? event.getNewValue()
                        : ToolCutDirection.BOTH);
            }
        });
        directionCol.setPrefWidth(150);
        TableColumn<CncToolDefinition, Double> holderAngleCol = new TableColumn<>(I18n.text("app.085"));
        holderAngleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getHolderAngleDeg()).asObject());
        holderAngleCol.setCellFactory(column -> this.editableToolCell(new DoubleStringConverter(),
                (tool, value) -> tool.setHolderAngleDeg(value != null ? value : 0.0),
                ToolTypeCatalog::supportsInsertGeometry));
        holderAngleCol.setOnEditCommit(event -> {
            if (ToolTypeCatalog.supportsInsertGeometry(event.getRowValue())) {
                event.getRowValue().setHolderAngleDeg(event.getNewValue() != null ? event.getNewValue() : 0.0);
            }
        });
        holderAngleCol.setPrefWidth(118);
        TableColumn<CncToolDefinition, Double> insertAngleCol = new TableColumn<>(I18n.text("app.086"));
        insertAngleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getInsertAngleDeg()).asObject());
        insertAngleCol.setCellFactory(column -> this.editableToolCell(new DoubleStringConverter(),
                (tool, value) -> tool.setInsertAngleDeg(value != null ? value : 0.0),
                ToolTypeCatalog::supportsInsertGeometry));
        insertAngleCol.setOnEditCommit(event -> {
            if (ToolTypeCatalog.supportsInsertGeometry(event.getRowValue())) {
                event.getRowValue().setInsertAngleDeg(event.getNewValue() != null ? event.getNewValue() : 0.0);
            }
        });
        insertAngleCol.setPrefWidth(110);
        TableColumn<CncToolDefinition, Double> plateLengthCol = new TableColumn<>(I18n.text("app.087"));
        plateLengthCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getPlateLength()).asObject());
        plateLengthCol.setCellFactory(column -> this.editableToolCell(new DoubleStringConverter(),
                (tool, value) -> tool.setPlateLength(value != null ? value : 0.0),
                ToolTypeCatalog::supportsPlateLength));
        plateLengthCol.setOnEditCommit(event -> {
            if (ToolTypeCatalog.supportsPlateLength(event.getRowValue())) {
                event.getRowValue().setPlateLength(event.getNewValue() != null ? event.getNewValue() : 0.0);
            }
        });
        plateLengthCol.setPrefWidth(128);
        TableColumn<CncToolDefinition, Double> cutWidthCol = new TableColumn<>(I18n.text("app.088"));
        cutWidthCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getCutWidth()).asObject());
        cutWidthCol.setCellFactory(column -> this.editableToolCell(new DoubleStringConverter(),
                (tool, value) -> tool.setCutWidth(value != null ? value : 0.0),
                ToolTypeCatalog::supportsCutWidth));
        cutWidthCol.setOnEditCommit(event -> {
            if (ToolTypeCatalog.supportsCutWidth(event.getRowValue())) {
                event.getRowValue().setCutWidth(event.getNewValue() != null ? event.getNewValue() : 0.0);
            }
        });
        cutWidthCol.setPrefWidth(88);
        TableColumn<CncToolDefinition, Double> radiusCol = new TableColumn<>(I18n.text("app.089"));
        radiusCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getRadius()).asObject());
        radiusCol.setCellFactory(column -> this.editableToolCell(new DoubleStringConverter(),
                (tool, value) -> tool.setRadius(value != null ? value : 0.0)));
        radiusCol.setOnEditCommit(event -> {
            event.getRowValue().setRadius(event.getNewValue() != null ? event.getNewValue() : 0.0);
        });
        radiusCol.setPrefWidth(90);
        TableColumn<CncToolDefinition, Double> lengthXCol = new TableColumn<>(I18n.text("app.090"));
        lengthXCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getLengthX()).asObject());
        lengthXCol.setCellFactory(column -> this.editableToolCell(new DoubleStringConverter(),
                (tool, value) -> tool.setLengthX(value != null ? value : 0.0)));
        lengthXCol.setOnEditCommit(event -> {
            event.getRowValue().setLengthX(event.getNewValue() != null ? event.getNewValue() : 0.0);
        });
        lengthXCol.setPrefWidth(96);
        TableColumn<CncToolDefinition, Double> lengthZCol = new TableColumn<>(I18n.text("app.091"));
        lengthZCol.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getLengthZ()).asObject());
        lengthZCol.setCellFactory(column -> this.editableToolCell(new DoubleStringConverter(),
                (tool, value) -> tool.setLengthZ(value != null ? value : 0.0)));
        lengthZCol.setOnEditCommit(event -> {
            event.getRowValue().setLengthZ(event.getNewValue() != null ? event.getNewValue() : 0.0);
        });
        lengthZCol.setPrefWidth(96);
        // Порядок как в списке инструментов SINUMERIK: ячейка, тип, название,
        // НОМЕР инструмента, затем кромка D. Раньше «Кромка» и «Инструмент» стояли
        // наоборот, и список, перенесённый со стойки колонка в колонку, получался
        // зеркальным: номер попадал в кромку, а кромка — в номер.
        TableColumn<CncToolDefinition,String> modelCol=new TableColumn<>(I18n.text("tool.model"));
        modelCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getModelId()));
        modelCol.setCellFactory(javafx.scene.control.cell.ComboBoxTableCell.forTableColumn(new javafx.util.StringConverter<String>() {
            public String toString(String value) { return I18n.text("tool.model."+(value==null?"auto":value)); }
            public String fromString(String value) { return value; }
        }, "auto","round_straight","round_cranked"));
        modelCol.setOnEditCommit(event -> event.getRowValue().setModelId(event.getNewValue()));
        modelCol.setPrefWidth(180);
        table.getColumns().addAll(locCol, typeCol, nameCol, toolCol, edgeCol, posCol,modelCol,
                directionCol, radiusCol, lengthXCol, lengthZCol,
                holderAngleCol, insertAngleCol, plateLengthCol, cutWidthCol);
        return table;
    }

    private void updateToolsTableHeight() {
        if (this.toolsTableView == null) {
            return;
        }
        int rows = this.toolsTableView.getItems() != null ? this.toolsTableView.getItems().size() : 0;
        double height = Math.min(700.0, Math.max(340.0, 58.0 + Math.max(4, rows) * 54.0));
        this.toolsTableView.setMinHeight(Math.min(340.0, height));
        this.toolsTableView.setPrefHeight(height);
        if (this.toolsTableView.getParent() instanceof Region region) {
            region.setMinHeight(Math.min(340.0, height));
            region.setPrefHeight(height);
        }
    }

    private void localizeGeneratedToolName(CncToolDefinition tool) {
        ToolTypeEntry entry = this.toolTypeEntry(tool);
        if (tool == null || entry == null) {
            return;
        }
        String name = tool.getName();
        // Стандартный центровочный из начальной библиотеки — своим именем, а не именем типа.
        if (name != null && isStarterName(name, "tool.default.centering",
                "\u0426\u0435\u043d\u0442\u0440\u0430\u043b\u044c\u043d\u044b\u0439")) {
            tool.setName(I18n.text("tool.default.centering"));
            return;
        }
        if (name == null
                || name.isBlank()
                || this.isGeneratedToolName(name, entry)
                || isStarterName(name, "tooltype.030", null) || isStarterName(name, "tooltype.031", null)
                || isStarterName(name, "tooltype.022", null) || isStarterName(name, "tooltype.033", null)) {
            tool.setName(ToolTypeCatalog.russianName(entry));
        }
    }

    /** Имя из начальной библиотеки на любом из языков (и прежнее вшитое русское). */
    private static boolean isStarterName(String name, String key, String legacy) {
        String trimmed = name.trim();
        if (legacy != null && legacy.equalsIgnoreCase(trimmed)) return true;
        for (String code : List.of(I18n.RUSSIAN, I18n.ENGLISH, I18n.UKRAINIAN)) {
            if (I18n.textIn(code, key).equalsIgnoreCase(trimmed)) return true;
        }
        return false;
    }

    private boolean isGeneratedToolName(String name, ToolTypeEntry entry) {
        if (name == null || entry == null) {
            return false;
        }
        String normalized = name.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.US);
        String generated = entry.identifier().replace('-', '_').replace(' ', '_').toUpperCase(Locale.US);
        return generated.equals(normalized)
                || entry.identifier().equalsIgnoreCase(name.trim())
                || normalized.contains("_TOOL")
                || normalized.contains("ROUGHING")
                || normalized.contains("FINISHING")
                || normalized.contains("BUTTON")
                || normalized.contains("CUTTER")
                || normalized.contains("DRILL");
    }

    private TableCell<CncToolDefinition, String> toolTextCell() {
        return new TableCell<>() {
            {
                this.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                CncToolDefinition row = this.getTableRow() != null ? this.getTableRow().getItem() : null;
                if (empty || row == null) {
                    this.setText(null);
                    this.setGraphic(null);
                    this.setTooltip(null);
                    return;
                }
                this.setText(item);
                this.setGraphic(null);
                this.setTooltip(MainController.this.toolTooltip(row));
            }
        };
    }

    private TableCell<CncToolDefinition, Integer> toolPositionCell() {
        return new TableCell<>() {
            {
                this.setOnMouseClicked(event -> {
                    if (event.getButton() != MouseButton.PRIMARY || this.isEmpty()) {
                        return;
                    }
                    CncToolDefinition row = this.getTableRow() != null ? this.getTableRow().getItem() : null;
                    if (row == null) {
                        return;
                    }
                    MainController.this.pickToolPosition(row).ifPresent(position -> {
                        row.setToolPosition(position);
                        if (MainController.this.toolsTableView != null) {
                            MainController.this.toolsTableView.refresh();
                        }
                    });
                    event.consume();
                });
            }

            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                this.getStyleClass().remove("tool-position-cell");
                CncToolDefinition row = this.getTableRow() != null ? this.getTableRow().getItem() : null;
                if (empty || row == null) {
                    this.setText(null);
                    this.setGraphic(null);
                    this.setTooltip(null);
                    this.setDisable(false);
                    return;
                }
                this.setText(null);
                this.getStyleClass().add("tool-position-cell");
                this.setAlignment(Pos.CENTER);
                this.setGraphic(ToolIconFactory.positionGraphic(
                        MainController.this.toolTypeEntry(row),
                        row.getToolPosition(),
                        40.0));
                this.setTooltip(MainController.this.toolTooltip(row));
                this.setDisable(false);
            }
        };
    }

    private Optional<Integer> pickToolPosition(CncToolDefinition tool) {
        if (tool == null) {
            return Optional.empty();
        }
        ToolTypeEntry entry = this.toolTypeEntry(tool);
        Stage stage = new Stage();
        if (this.settingsStage != null) {
            stage.initOwner(this.settingsStage);
        }
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.text("app.092"));
        AppIconHelper.applyToStage(stage, this.settingsStage);
        VBox root = new VBox(12.0);
        root.setPadding(new Insets(14.0));
        root.getStyleClass().add("tool-picker-root");
        if (this.darkTheme) {
            root.getStyleClass().add("dark-theme");
        }
        Label label = new Label(tool.displayType());
        label.getStyleClass().add("tool-picker-title");
        StackPane preview = new StackPane(ToolIconFactory.preview(entry, tool.getToolPosition()));
        preview.getStyleClass().add("tool-picker-preview");
        GridPane grid = new GridPane();
        grid.setHgap(8.0);
        grid.setVgap(8.0);
        grid.setAlignment(Pos.CENTER);
        HBox pageControls = new HBox(8.0);
        pageControls.getStyleClass().add("tool-position-page-controls");
        pageControls.setAlignment(Pos.CENTER);
        final Integer[] selected = {null};
        int variants = Math.max(1, entry.positionVariants());
        final int[] page = {Math.max(0, (Math.max(1, tool.getToolPosition()) - 1) / 4)};
        final Runnable[] refreshPositions = new Runnable[1];
        refreshPositions[0] = () -> {
            grid.getChildren().clear();
            pageControls.getChildren().clear();
            int pageSize = 4;
            int pageCount = Math.max(1, (variants + pageSize - 1) / pageSize);
            page[0] = Math.max(0, Math.min(page[0], pageCount - 1));
            int first = page[0] * pageSize + 1;
            int last = Math.min(variants, first + pageSize - 1);
            for (int position = first; position <= last; position++) {
                Button button = new Button();
                button.getStyleClass().add("secondary-button");
                button.getStyleClass().add("tool-position-button");
                button.setMinSize(92.0, 60.0);
                button.setPrefSize(92.0, 60.0);
                button.setGraphic(ToolIconFactory.positionGraphic(entry, position, 40.0));
                button.setTooltip(ToolIconFactory.tooltip(entry, position));
                if (position == tool.getToolPosition()) {
                    button.getStyleClass().add("simulation3d-mode-active");
                }
                int selectedPosition = position;
                button.setOnAction(event -> {
                    selected[0] = selectedPosition;
                    stage.close();
                });
                int index = position - first;
                grid.add(button, index % 2, index / 2);
            }
            if (pageCount > 1) {
                Button previous = new Button("\u25c0");
                previous.getStyleClass().add("secondary-button");
                previous.setDisable(page[0] == 0);
                Label pageLabel = new Label((page[0] + 1) + " / " + pageCount);
                pageLabel.getStyleClass().add("muted-label");
                Button next = new Button("\u25b6");
                next.getStyleClass().add("secondary-button");
                next.setDisable(page[0] >= pageCount - 1);
                previous.setOnAction(event -> {
                    page[0]--;
                    refreshPositions[0].run();
                });
                next.setOnAction(event -> {
                    page[0]++;
                    refreshPositions[0].run();
                });
                pageControls.getChildren().addAll(previous, pageLabel, next);
            }
        };
        refreshPositions[0].run();
        HBox buttons = new HBox(8.0);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button(I18n.text("app.039"));
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> stage.close());
        buttons.getChildren().add(cancel);
        root.getChildren().addAll(label, preview, grid, pageControls, buttons);
        Scene scene = new Scene(root, 330, 520);
        scene.getStylesheets().add(MainController.class.getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(330.0);
        stage.setMinHeight(520.0);
        stage.showAndWait();
        return Optional.ofNullable(selected[0]);
    }

    private ToolTypeEntry toolTypeEntry(CncToolDefinition tool) {
        ToolTypeEntry entry = tool != null ? ToolTypeCatalog.findByCode(tool.getTypeCode()) : null;
        if (entry != null) {
            return entry;
        }
        return ToolTypeCatalog.defaultTurning();
    }

    private Tooltip toolTooltip(CncToolDefinition tool) {
        return ToolIconFactory.tooltip(tool);
    }

    private <T> TableCell<CncToolDefinition, T> editableToolCell(
            StringConverter<T> converter,
            BiConsumer<CncToolDefinition, T> setter
    ) {
        return this.editableToolCell(converter, setter, tool -> true);
    }

    private <T> TableCell<CncToolDefinition, T> editableToolCell(
            StringConverter<T> converter,
            BiConsumer<CncToolDefinition, T> setter,
            Predicate<CncToolDefinition> editablePredicate
    ) {
        return new TableCell<>() {
            private final TextField editor = new TextField();

            {
                this.setAlignment(Pos.CENTER);
                this.editor.setAlignment(Pos.CENTER);
                this.editor.setOnAction(event -> this.commitEditor());
                this.editor.focusedProperty().addListener((observable, wasFocused, focused) -> {
                    if (!focused) {
                        this.commitEditor();
                    }
                });
                this.editor.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        this.cancelEdit();
                        event.consume();
                    }
                });
            }

            @Override
            public void startEdit() {
                if (this.isEmpty() || !this.isEditableRow()) {
                    return;
                }
                super.startEdit();
                this.showEditor();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                this.setText(this.format(this.getItem()));
                this.setGraphic(null);
                this.refreshTooltip();
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    this.setText(null);
                    this.setGraphic(null);
                    this.setTooltip(null);
                    this.setDisable(false);
                    return;
                }
                if (!this.isEditableRow()) {
                    this.setText("\u2014");
                    this.setGraphic(null);
                    this.refreshTooltip();
                    this.setDisable(true);
                    return;
                }
                this.setDisable(false);
                this.refreshTooltip();
                if (this.isEditing()) {
                    this.showEditor();
                } else {
                    this.setText(this.format(item));
                    this.setGraphic(null);
                }
            }

            private void showEditor() {
                this.editor.setText(this.format(this.getItem()));
                this.setText(null);
                this.setGraphic(this.editor);
                Platform.runLater(() -> {
                    this.editor.requestFocus();
                    this.editor.selectAll();
                });
            }

            private void commitEditor() {
                if (this.getTableRow() == null || this.getTableRow().getItem() == null || !this.isEditableRow()) {
                    return;
                }
                T value;
                try {
                    value = converter.fromString(this.editor.getText());
                } catch (RuntimeException exception) {
                    this.editor.setText(this.format(this.getItem()));
                    return;
                }
                CncToolDefinition row = this.getTableRow().getItem();
                setter.accept(row, value);
                if (this.isEditing()) {
                    this.commitEdit(value);
                } else {
                    this.updateItem(value, false);
                }
                if (this.getTableView() != null) {
                    this.getTableView().refresh();
                }
            }

            private String format(T value) {
                return value == null ? "" : converter.toString(value);
            }

            private boolean isEditableRow() {
                CncToolDefinition row = this.getTableRow() != null ? this.getTableRow().getItem() : null;
                return row != null && (editablePredicate == null || editablePredicate.test(row));
            }

            private void refreshTooltip() {
                CncToolDefinition row = this.getTableRow() != null ? this.getTableRow().getItem() : null;
                this.setTooltip(row != null ? MainController.this.toolTooltip(row) : null);
            }
        };
    }

    private TableCell<CncToolDefinition, ToolCutDirection> toolDirectionCell() {
        return new ComboBoxTableCell<CncToolDefinition, ToolCutDirection>(
                this.toolDirectionConverter(),
                ToolCutDirection.values()) {
            {
                this.setAlignment(Pos.CENTER);
            }

            @Override
            public void startEdit() {
                if (!this.supported()) {
                    return;
                }
                super.startEdit();
            }

            @Override
            public void updateItem(ToolCutDirection item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    this.setText(null);
                    this.setGraphic(null);
                    this.setTooltip(null);
                    this.setDisable(false);
                    return;
                }
                CncToolDefinition row = this.getTableRow() != null ? this.getTableRow().getItem() : null;
                this.setTooltip(row != null ? MainController.this.toolTooltip(row) : null);
                if (!this.supported()) {
                    this.setText("\u2014");
                    this.setGraphic(null);
                    this.setDisable(true);
                    return;
                }
                this.setDisable(false);
            }

            private boolean supported() {
                CncToolDefinition row = this.getTableRow() != null ? this.getTableRow().getItem() : null;
                return ToolTypeCatalog.supportsCutDirection(row);
            }
        };
    }

    private StringConverter<String> stringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value != null ? value : "";
            }

            @Override
            public String fromString(String value) {
                return value != null ? value : "";
            }
        };
    }

    private StringConverter<ToolCutDirection> toolDirectionConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(ToolCutDirection direction) {
                return direction != null ? direction.getLabel() : ToolCutDirection.BOTH.getLabel();
            }

            @Override
            public ToolCutDirection fromString(String value) {
                return ToolCutDirection.fromKey(value);
            }
        };
    }

    private void addToolRow() {
        if (this.toolsObservableList == null) {
            return;
        }
        Window owner = this.settingsStage != null ? this.settingsStage : null;
        java.util.Optional<ToolTypePickerDialog.ToolTypeSelection> picked = ToolTypePickerDialog.show(owner, this.darkTheme);
        if (picked.isEmpty()) {
            return;
        }
        ToolTypePickerDialog.ToolTypeSelection selection = picked.get();
        ToolTypeEntry entry = selection.entry();
        int nextLoc = this.toolsObservableList.stream().mapToInt(CncToolDefinition::getLocation).max().orElse(0) + 1;
        int nextTool = nextLoc;
        String displayName = ToolTypeCatalog.russianName(entry);
        CncToolDefinition row = new CncToolDefinition(
                nextLoc,
                displayName,
                entry.category().name().toLowerCase(Locale.US),
                nextTool,
                1,
                0.0,
                0.0,
                0.0,
                entry.typeCode(),
                entry.identifier(),
                selection.position(),
                ToolCutDirection.BOTH);
        this.applyToolTypeDefaults(row);
        this.toolsObservableList.add(row);
        if (this.toolsTableView != null) {
            this.toolsTableView.getSelectionModel().select(row);
            this.toolsTableView.scrollTo(row);
        }
    }

    private void applyToolTypeDefaults(CncToolDefinition tool) {
        if (tool == null) {
            return;
        }
        if (ToolTypeCatalog.supportsInsertGeometry(tool)) {
            tool.setHolderAngleDeg(93.0);
            tool.setInsertAngleDeg(55.0);
            tool.setPlateLength(11.0);
            if (tool.getRadius() <= 0.0) {
                tool.setRadius(tool.getTypeCode() == 550 ? 6.0 : 0.2);
            }
        }
        if (ToolTypeCatalog.supportsCutWidth(tool)) {
            tool.setCutWidth(tool.getTypeCode() == 530 ? 3.0 : 2.0);
        }
    }

    private void removeSelectedToolRow() {
        if (this.toolsTableView == null || this.toolsObservableList == null) {
            return;
        }
        CncToolDefinition selected = this.toolsTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            this.toolsObservableList.remove(selected);
        }
    }

    private void persistToolLibrary() {
        if (this.toolsObservableList != null) {
            if (this.toolsTableView != null) {
                this.toolsTableView.refresh();
            }
            this.toolLibraryDrafts.put(this.toolsMachine, new ArrayList<>(this.toolsObservableList));
            this.toolLibraryDrafts.forEach(ToolLibraryStore::save);
            this.toolLibraryDrafts.clear();
        }
    }

    /** Explicit library T/D geometry is authoritative; comments do not change it. */
    private List<CncToolDefinition> simulationToolLibrary(String programText) {
        // Comments cannot override explicitly configured T/D geometry.
        return this.currentToolLibrary();
    }

    private List<CncToolDefinition> currentToolLibrary() {
        // Machine configuration becomes active only on Save. A draft selection
        // in Settings must never feed wheel geometry to a running axle program.
        var activeMachine = ToolLibraryStore.savedMachineType();
        if (this.toolsObservableList != null && this.toolsMachine == activeMachine) {
            return new ArrayList<>(this.toolsObservableList);
        }
        var draft = this.toolLibraryDrafts.get(activeMachine);
        return draft != null ? new ArrayList<>(draft) : ToolLibraryStore.load(activeMachine);
    }

    private void switchToolLibrary(MachineConfiguration.MachineType machine) {
        if (this.toolsObservableList == null || machine == this.toolsMachine) return;
        this.toolLibraryDrafts.put(this.toolsMachine, new ArrayList<>(this.toolsObservableList));
        this.toolsMachine = machine;
        this.toolsObservableList.setAll(this.toolLibraryDrafts.computeIfAbsent(machine, ToolLibraryStore::load));
        this.toolsObservableList.forEach(this::localizeGeneratedToolName);
        if (this.toolsTableView != null) this.toolsTableView.getSelectionModel().clearSelection();
        this.updateToolsMachineLabel();
    }

    private void updateToolsMachineLabel() {
        if (this.toolsMachineLabel != null) {
            this.toolsMachineLabel.setText(I18n.text("tools.machine.library") + " " + this.toolsMachine.getDisplayName());
        }
    }

    private void displaySavedToolLibrary(List<CncToolDefinition> tools) {
        var machine = ToolLibraryStore.savedMachineType();
        if (this.toolsObservableList != null && this.toolsMachine == machine) {
            this.toolsObservableList.setAll(tools);
            this.toolsObservableList.forEach(this::localizeGeneratedToolName);
        }
        if (this.toolLibraryDrafts.containsKey(machine)) {
            this.toolLibraryDrafts.put(machine, new ArrayList<>(tools));
        }
    }

    private void resetMachineToolDrafts() {
        // Restore both the selector and its bank on Cancel/reopen, not just rows.
        this.toolLibraryDrafts.clear();
        this.toolsMachine = ToolLibraryStore.savedMachineType();
        if (this.machineTypeComboBox != null) this.machineTypeComboBox.setValue(this.toolsMachine.getDisplayName());
        if (this.controlSystemComboBox != null) {
            this.controlSystemComboBox.setValue(this.parseControlSystem(UserSettings.getControlSystem()).getDisplayName());
        }
        if (this.toolsObservableList != null) {
            // Каждая загрузка с диска — снова через перевод: иначе при открытии
            // настроек стандартные имена возвращались на языке, которым их записали.
            this.toolsObservableList.setAll(ToolLibraryStore.load(this.toolsMachine));
            this.toolsObservableList.forEach(this::localizeGeneratedToolName);
        }
        this.updateToolsMachineLabel();
    }

    private void setupMachineSettingsUi() {
        if (this.controlSystemComboBox == null || this.machineTypeComboBox == null) {
            return;
        }
        if (this.controlSystemComboBox.getItems().isEmpty()) {
            for (MachineConfiguration.ControlSystem controlSystem : MachineConfiguration.ControlSystem.values()) {
                this.controlSystemComboBox.getItems().add(controlSystem.getDisplayName());
            }
        }
        if (this.machineTypeComboBox.getItems().isEmpty()) {
            for (MachineConfiguration.MachineType machineType : MachineConfiguration.MachineType.values()) {
                this.machineTypeComboBox.getItems().add(machineType.getDisplayName());
            }
        }
        MachineConfiguration.ControlSystem savedControl = this.parseControlSystem(UserSettings.getControlSystem());
        MachineConfiguration.MachineType savedMachine = this.parseMachineType(UserSettings.getMachineType());
        this.controlSystemComboBox.setValue(savedControl.getDisplayName());
        this.machineTypeComboBox.setValue(savedMachine.getDisplayName());
        this.controlSystemComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            this.refreshMachineAxisLabels();
        });
        this.machineTypeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            this.refreshMachineAxisLabels();
            this.switchToolLibrary(this.readSelectedMachineType());
        });
        this.refreshMachineAxisLabels();
    }

    private void refreshMachineAxisLabels() {
        MachineConfiguration.ControlSystem control = this.readSelectedControlSystem();
        MachineConfiguration.MachineType machine = this.readSelectedMachineType();
        java.util.HashSet<Character> active = new java.util.HashSet<>();
        active.add('X');
        active.add('Z');
        if (machine == MachineConfiguration.MachineType.HAAS_CM1
                || control == MachineConfiguration.ControlSystem.SIEMENS_MILLING) {
            active.add('Y');
        }
        this.applyAxisLabelState(this.axisLabelX, 'X', active);
        this.applyAxisLabelState(this.axisLabelY, 'Y', active);
        this.applyAxisLabelState(this.axisLabelZ, 'Z', active);
        this.applyAxisLabelState(this.axisLabelA, 'A', active);
        this.applyAxisLabelState(this.axisLabelB, 'B', active);
        this.applyAxisLabelState(this.axisLabelC, 'C', active);
    }

    private void applyAxisLabelState(Label label, char axis, java.util.Set<Character> activeAxes) {
        if (label == null) {
            return;
        }
        if (!label.getStyleClass().contains("machine-axis-label")) {
            label.getStyleClass().add("machine-axis-label");
        }
        if (activeAxes.contains(axis)) {
            if (!label.getStyleClass().contains("machine-axis-active")) {
                label.getStyleClass().add("machine-axis-active");
            }
        } else {
            label.getStyleClass().remove("machine-axis-active");
        }
    }

    private MachineConfiguration.ControlSystem readSelectedControlSystem() {
        if (this.controlSystemComboBox == null || this.controlSystemComboBox.getValue() == null) {
            return MachineConfiguration.ControlSystem.SIEMENS_MILLING;
        }
        return this.parseControlSystemByDisplayName(this.controlSystemComboBox.getValue());
    }

    private MachineConfiguration.MachineType readSelectedMachineType() {
        if (this.machineTypeComboBox == null || this.machineTypeComboBox.getValue() == null) {
            return MachineConfiguration.MachineType.HAAS_CM1;
        }
        return this.parseMachineTypeByDisplayName(this.machineTypeComboBox.getValue());
    }

    private MachineConfiguration.ControlSystem parseControlSystem(String stored) {
        if (stored == null || stored.isBlank()) {
            return MachineConfiguration.ControlSystem.SIEMENS_MILLING;
        }
        try {
            return MachineConfiguration.ControlSystem.valueOf(stored);
        } catch (IllegalArgumentException exception) {
            return MachineConfiguration.ControlSystem.SIEMENS_MILLING;
        }
    }

    private MachineConfiguration.MachineType parseMachineType(String stored) {
        if (stored == null || stored.isBlank()) {
            return MachineConfiguration.MachineType.GENERIC_LATHE;
        }
        if ("DEMO_LATHE_840D".equals(stored)) {
            UserSettings.setMachineType(MachineConfiguration.MachineType.GENERIC_LATHE.name());
            return MachineConfiguration.MachineType.GENERIC_LATHE;
        }
        try {
            return MachineConfiguration.MachineType.valueOf(stored);
        } catch (IllegalArgumentException exception) {
            return MachineConfiguration.MachineType.GENERIC_LATHE;
        }
    }

    private MachineConfiguration.ControlSystem parseControlSystemByDisplayName(String displayName) {
        for (MachineConfiguration.ControlSystem controlSystem : MachineConfiguration.ControlSystem.values()) {
            if (controlSystem.getDisplayName().equalsIgnoreCase(displayName)) {
                return controlSystem;
            }
        }
        return MachineConfiguration.ControlSystem.SIEMENS_MILLING;
    }

    private MachineConfiguration.MachineType parseMachineTypeByDisplayName(String displayName) {
        for (MachineConfiguration.MachineType machineType : MachineConfiguration.MachineType.values()) {
            if (machineType.getDisplayName().equalsIgnoreCase(displayName)) {
                return machineType;
            }
        }
        return MachineConfiguration.MachineType.HAAS_CM1;
    }

    private void openSettingsWindow() {
        this.ensureSettingsPanel();
        WorkOffsetStore.loadFromUserSettings();
        this.loadGeometrySettingsFromPrefs();
        this.rebuildWorkOffsetRows(true);
        this.syncWorkOffsetsToStore();
        String programText = this.programEditor != null ? this.programEditor.getText() : this.appliedProgramText;
        if (programText != null && !programText.isBlank()) {
            this.syncWorkOffsetsFromProgram(programText);
        }
        Window owner = null;
        if (this.anchorPaneCanvas != null && this.anchorPaneCanvas.getScene() != null) {
            owner = this.anchorPaneCanvas.getScene().getWindow();
        }
        if (this.settingsStage != null && this.settingsStage.isShowing()) {
            if (programText != null && !programText.isBlank()) {
                this.syncWorkOffsetsFromProgram(programText);
            }
            this.settingsStage.toFront();
            return;
        }
        this.settingsPanelCloseHandled = false;
        this.resetMachineToolDrafts();
        this.settingsDraftBaseline = this.captureSettingsDraft();
        ScrollPane scrollPane = new ScrollPane(this.settingsPanelRoot);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("sidebar-scroll");
        BorderPane root = new BorderPane(scrollPane);
        root.getStyleClass().add("settings-window-root");
        HBox settingsFooter = new HBox(10.0);
        settingsFooter.getStyleClass().add("settings-window-footer");
        settingsFooter.setAlignment(Pos.CENTER_RIGHT);
        settingsFooter.setPadding(new Insets(10, 14, 12, 14));
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        Button settingsCancel = new Button(I18n.text("app.039"));
        settingsCancel.getStyleClass().add("secondary-button");
        settingsCancel.getStyleClass().add("settings-footer-button");
        Button settingsSave = new Button(I18n.text("app.093"));
        settingsSave.getStyleClass().add("primary-button");
        settingsSave.getStyleClass().add("settings-footer-button");
        settingsCancel.setMinSize(128.0, 42.0);
        settingsSave.setMinSize(128.0, 42.0);
        settingsFooter.getChildren().addAll(footerSpacer, settingsCancel, settingsSave);
        root.setBottom(settingsFooter);
        // Окно настроек не должно превышать видимую область экрана (маленькие разрешения).
        javafx.geometry.Rectangle2D settingsBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(
                root,
                Math.min(1500.0, settingsBounds.getWidth() * 0.92),
                Math.min(900.0, settingsBounds.getHeight() * 0.9));
        URL cssUrl = MainController.class.getResource("/app.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        if (this.darkTheme) {
            root.getStyleClass().add("dark-theme");
        }
        this.settingsStage = new Stage();
        this.settingsStage.setTitle(I18n.text("app.094"));
        if (owner != null) {
            this.settingsStage.initOwner(owner);
        }
        this.applySettingsStageIcon(owner);
        this.settingsStage.setScene(scene);
        this.settingsStage.setMinWidth(Math.min(1180.0, settingsBounds.getWidth() * 0.85));
        this.settingsStage.setMinHeight(Math.min(760.0, settingsBounds.getHeight() * 0.85));
        settingsCancel.setOnAction(event -> {
            this.settingsPanelCloseHandled = true;
            this.restoreSettingsDraft(this.settingsDraftBaseline);
            this.resetMachineToolDrafts();
            this.settingsStage.close();
        });
        settingsSave.setOnAction(event -> {
            this.settingsPanelCloseHandled = true;
            this.commitSettingsPanel();
            this.settingsStage.close();
        });
        this.settingsStage.setOnCloseRequest(windowEvent -> {
            if (!this.settingsPanelCloseHandled) {
                this.restoreSettingsDraft(this.settingsDraftBaseline);
                this.resetMachineToolDrafts();
            }
            this.settingsPanelCloseHandled = false;
            this.settingsStage = null;
        });
        this.settingsStage.setOnHidden(windowEvent -> {
            this.settingsPanelCloseHandled = false;
            this.settingsStage = null;
        });
        this.settingsStage.show();
    }

    private void commitSettingsPanel() {
        this.storeWorkOffsetFieldsInCache();
        if (this.workOffsetCountField != null) {
            UserSettings.setWorkOffsetCount(this.parseWorkOffsetCount(this.workOffsetCountField.getText()));
        }
        this.persistGeometrySettings();
        if (this.controlSystemComboBox != null && this.controlSystemComboBox.getValue() != null) {
            UserSettings.setControlSystem(
                    this.parseControlSystemByDisplayName(this.controlSystemComboBox.getValue()).name());
        }
        if (this.machineTypeComboBox != null && this.machineTypeComboBox.getValue() != null) {
            UserSettings.setMachineType(
                    this.parseMachineTypeByDisplayName(this.machineTypeComboBox.getValue()).name());
        }
        this.persistToolLibrary();
        if (this.feedOverrideDial != null) {
            UserSettings.setFeedOverridePercent(this.feedOverrideDial.getValue());
        }
        if (this.spindleOverrideDial != null) {
            UserSettings.setSpindleOverridePercent(this.spindleOverrideDial.getValue());
        }
        this.applyWorkOffsetChanges();
        this.refreshOpenSimulation3d();
    }

    private SettingsDraft captureSettingsDraft() {
        this.storeWorkOffsetFieldsInCache();
        Map<Integer, String[]> offsets = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> entry : this.workOffsetFieldCache.entrySet()) {
            String[] values = entry.getValue();
            offsets.put(
                    entry.getKey(),
                    new String[]{
                            values != null && values.length > 0 && values[0] != null ? values[0] : "",
                            values != null && values.length > 1 && values[1] != null ? values[1] : ""});
        }
        return new SettingsDraft(
                offsets,
                this.turningBlankDiameter != null ? this.turningBlankDiameter.getText() : "",
                this.checkBoxUseBlankDiameter != null && this.checkBoxUseBlankDiameter.isSelected(),
                this.cupRadius != null ? this.cupRadius.getText() : "",
                this.checkBoxUseCupRadius != null && this.checkBoxUseCupRadius.isSelected(),
                this.workOffsetCountField != null ? this.workOffsetCountField.getText() : "",
                this.checkBoxEquidistant == null || this.checkBoxEquidistant.isSelected(),
                this.equidistantRadiusSourceComboBox != null
                        ? this.equidistantRadiusSourceComboBox.getValue() : null,
                this.equidistantRadius != null ? this.equidistantRadius.getText() : "",
                this.equidistantAllowance != null ? this.equidistantAllowance.getText() : "");
    }

    private void restoreSettingsDraft(SettingsDraft draft) {
        if (draft == null) {
            return;
        }
        this.workOffsetFieldCache.clear();
        this.workOffsetFieldCache.putAll(draft.workOffsets());
        if (this.workOffsetCountField != null) {
            this.workOffsetCountField.setText(draft.workOffsetCountText());
        }
        this.rebuildWorkOffsetRows(true);
        WorkOffsetStore.loadFromUserSettings();
        if (this.turningBlankDiameter != null) {
            this.turningBlankDiameter.setText(draft.blankDiameterText());
        }
        if (this.checkBoxUseBlankDiameter != null) {
            this.checkBoxUseBlankDiameter.setSelected(draft.useBlankDiameter());
        }
        if (this.cupRadius != null) {
            this.cupRadius.setText(draft.cupRadiusText());
        }
        if (this.checkBoxUseCupRadius != null) {
            this.checkBoxUseCupRadius.setSelected(draft.useCupRadius());
        }
        if (this.checkBoxEquidistant != null) {
            this.checkBoxEquidistant.setSelected(draft.equidistantEnabled());
        }
        if (this.equidistantRadiusSourceComboBox != null && draft.equidistantRadiusSource() != null) {
            this.equidistantRadiusSourceComboBox.setValue(draft.equidistantRadiusSource());
        }
        if (this.equidistantRadius != null) {
            this.equidistantRadius.setText(draft.equidistantRadiusText());
        }
        if (this.equidistantAllowance != null) {
            this.equidistantAllowance.setText(draft.equidistantAllowanceText());
        }
        // Эквидистанта применяется сразу, поэтому «Отмена» должна вернуть и сохранённые значения.
        this.persistEquidistantSettings();
    }

    private void applySettingsStageIcon(Window owner) {
        if (this.settingsStage == null) {
            return;
        }
        if (owner instanceof Stage ownerStage && !ownerStage.getIcons().isEmpty()) {
            this.settingsStage.getIcons().setAll(ownerStage.getIcons());
            return;
        }
        try {
            File iconFile = new File("icon.ico");
            if (iconFile.isFile()) {
                this.settingsStage.getIcons().setAll(new Image(iconFile.toURI().toString()));
            }
        }
        catch (Exception exception) {
            LOGGER.fine("Settings window icon not set: " + exception.getMessage());
        }
    }

    @FXML
    private void onExpandGraph() {
        this.openGraphFullScreen();
    }

    @FXML
    private void onSingleBlock() {
        this.handleSingleBlock();
    }

    @FXML
    private void onFrameUp() {
        this.stepFrame(-1);
    }

    @FXML
    private void onFrameDown() {
        this.stepFrame(1);
    }

    @FXML
    private void onFitView() {
        this.centerViewOnOrigin();
        this.redrawCanvas();
    }

    private void openGraphFullScreen() {
        if (this.drawingCanvas == null) {
            return;
        }
        Stage stage = new Stage();
        Canvas canvas = new Canvas(1280.0, 720.0);
        StackPane stackPane = new StackPane(canvas);
        Scene scene = new Scene(stackPane, 1280.0, 720.0);
        canvas.widthProperty().bind(stackPane.widthProperty());
        canvas.heightProperty().bind(stackPane.heightProperty());
        Runnable runnable = () -> this.drawToolpathPreview(canvas, canvas.getWidth(), canvas.getHeight(), this.parseMovesForView(this.appliedProgramText));
        canvas.widthProperty().addListener((observableValue, number, number2) -> runnable.run());
        canvas.heightProperty().addListener((observableValue, number, number2) -> runnable.run());
        scene.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setTitle(I18n.text("app.095"));
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();
        Platform.runLater(runnable);
    }

    private boolean geometrySettingsListenersReady;

    private void setupGeometryCalculations() {
        if (this.geometrySettingsListenersReady) {
            return;
        }
        if (this.turningBlankDiameter == null && this.cupRadius == null) {
            return;
        }
        this.geometrySettingsListenersReady = true;
        if (this.turningBlankDiameter != null) {
            this.turningBlankDiameter.textProperty().addListener((observableValue, string, string2) -> {
                MainController.parsePositiveNumber(string2)
                        .ifPresent(value -> {
                            if (this.checkBoxUseBlankDiameter != null) {
                                this.checkBoxUseBlankDiameter.setSelected(true);
                            }
                        });
                this.refreshTurningAndCupLabels();
                this.redrawCanvas();
            });
        }
        if (this.cupRadius != null) {
            this.cupRadius.textProperty().addListener((observableValue, string, string2) -> {
                MainController.parsePositiveNumber(string2)
                        .ifPresent(value -> {
                            if (this.checkBoxUseCupRadius != null) {
                                this.checkBoxUseCupRadius.setSelected(true);
                            }
                        });
                this.refreshTurningAndCupLabels();
                this.redrawCanvas();
            });
        }
        if (this.checkBoxUseBlankDiameter != null) {
            this.checkBoxUseBlankDiameter.setOnAction(actionEvent -> this.redrawCanvas());
        }
        if (this.checkBoxUseCupRadius != null) {
            this.checkBoxUseCupRadius.setOnAction(actionEvent -> {
                this.refreshTurningAndCupLabels();
                this.redrawCanvas();
            });
        }
        this.loadGeometrySettingsFromPrefs();
        this.refreshTurningAndCupLabels();
    }

    private void loadGeometrySettingsFromPrefs() {
        if (this.turningBlankDiameter != null && UserSettings.getBlankDiameterMm() > 0.0) {
            this.turningBlankDiameter.setText(String.format(Locale.US, "%.1f", UserSettings.getBlankDiameterMm()));
        }
        if (this.checkBoxUseBlankDiameter != null) {
            this.checkBoxUseBlankDiameter.setSelected(UserSettings.isUseBlankDiameter());
        }
        if (this.cupRadius != null && UserSettings.getCupRadiusMm() > 0.0) {
            this.cupRadius.setText(String.format(Locale.US, "%.1f", UserSettings.getCupRadiusMm()));
        }
        if (this.checkBoxUseCupRadius != null) {
            this.checkBoxUseCupRadius.setSelected(UserSettings.isUseCupRadius());
        }
        this.loadEquidistantSettingsFromPrefs();
        if (this.workOffsetCountField != null) {
            this.workOffsetCountField.setText(String.valueOf(UserSettings.getWorkOffsetCount()));
        }
        this.loadWorkOffsetsFromUserSettings();
    }

    private void loadWorkOffsetsFromUserSettings() {
        int count = UserSettings.getWorkOffsetCount();
        for (int i = 0; i < count; i++) {
            this.loadWorkOffsetFieldToCache(54 + i);
        }
        for (Integer code : WorkOffsetStore.extendedCodes()) {
            this.loadWorkOffsetFieldToCache(code);
        }
    }

    private void loadWorkOffsetFieldToCache(int code) {
        WorkOffsetValues values = WorkOffsetStore.get(code);
        this.workOffsetFieldCache.put(
                code,
                new String[]{
                        this.formatWorkOffsetField(values.xMm()),
                        this.formatWorkOffsetField(values.zMm())});
    }

    private void persistWorkOffsetsToUserSettings() {
        WorkOffsetStore.persistToUserSettings();
    }

    private void syncTurningSettingsFromGCode(String programText) {
        if (programText == null || programText.isBlank()) {
            return;
        }
        GCodeProgramParser.findWorkpiece(programText).ifPresent(workpiece -> {
            if (this.turningBlankDiameter != null) {
                this.turningBlankDiameter.setText(String.format(Locale.US, "%.1f", workpiece.getDiameterMm()));
            }
            if (this.checkBoxUseBlankDiameter != null) {
                this.checkBoxUseBlankDiameter.setSelected(true);
            }
        });
        GCodeProgramParser.findCupRadiusMm(programText).ifPresent(cupMm -> {
            if (this.cupRadius != null) {
                this.cupRadius.setText(String.format(Locale.US, "%.1f", cupMm));
            }
            if (this.checkBoxUseCupRadius != null) {
                this.checkBoxUseCupRadius.setSelected(true);
            }
        });
        this.refreshTurningAndCupLabels();
    }

    private void persistGeometrySettings() {
        if (this.turningBlankDiameter != null) {
            MainController.parsePositiveNumber(this.turningBlankDiameter.getText())
                    .ifPresent(UserSettings::setBlankDiameterMm);
        }
        if (this.checkBoxUseBlankDiameter != null) {
            UserSettings.setUseBlankDiameter(this.checkBoxUseBlankDiameter.isSelected());
        }
        if (this.cupRadius != null) {
            MainController.parsePositiveNumber(this.cupRadius.getText())
                    .ifPresent(UserSettings::setCupRadiusMm);
        }
        if (this.checkBoxUseCupRadius != null) {
            UserSettings.setUseCupRadius(this.checkBoxUseCupRadius.isSelected());
        }
        this.persistEquidistantSettings();
    }

    // ------------------------------------------------------------------
    // Эквидистанта: коррекция на радиус вершины резца (G41/G42)
    // ------------------------------------------------------------------

    /** Варианты источника радиуса вершины — порядок совпадает с логикой чтения. */
    private static final String EQUIDISTANT_FROM_LIBRARY = I18n.text("app.096");
    private static final String EQUIDISTANT_MANUAL = I18n.text("app.097");

    private void setupEquidistantSettings() {
        if (this.equidistantRadiusSourceComboBox != null
                && this.equidistantRadiusSourceComboBox.getItems().isEmpty()) {
            this.equidistantRadiusSourceComboBox.getItems()
                    .setAll(EQUIDISTANT_FROM_LIBRARY, EQUIDISTANT_MANUAL);
        }
        if (this.equidistantRadius != null) {
            this.equidistantRadius.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(
                    I18n.text("app.098")
                            + I18n.text("app.099")
                            + I18n.text("app.100"))));
        }
        if (this.equidistantAllowance != null) {
            this.equidistantAllowance.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(
                    I18n.text("app.101")
                            + I18n.text("app.102")
                            + I18n.text("app.103"))));
        }
        if (this.checkBoxEquidistant != null) {
            this.checkBoxEquidistant.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(
                    I18n.text("app.104")
                            + I18n.text("app.105"))));
            this.checkBoxEquidistant.setOnAction(event -> this.onEquidistantSettingsChanged());
        }
        if (this.equidistantRadiusSourceComboBox != null) {
            this.equidistantRadiusSourceComboBox.setOnAction(event -> this.onEquidistantSettingsChanged());
        }
        // Числа применяем по уходу фокуса и по Enter: 3D-окно берёт их из настроек,
        // а не из полей, поэтому ждать «Сохранить» здесь было бы неудобно.
        this.applyEquidistantFieldOnCommit(this.equidistantRadius);
        this.applyEquidistantFieldOnCommit(this.equidistantAllowance);
    }

    private void applyEquidistantFieldOnCommit(TextField field) {
        if (field == null) {
            return;
        }
        field.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) {
                this.onEquidistantSettingsChanged();
            }
        });
        field.setOnAction(event -> this.onEquidistantSettingsChanged());
    }

    private void onEquidistantSettingsChanged() {
        this.persistEquidistantSettings();
        this.redrawCanvas();
        this.refreshOpenSimulation3d();
    }

    private void loadEquidistantSettingsFromPrefs() {
        if (this.checkBoxEquidistant != null) {
            this.checkBoxEquidistant.setSelected(UserSettings.isEquidistantEnabled());
        }
        if (this.equidistantRadiusSourceComboBox != null) {
            this.equidistantRadiusSourceComboBox.setValue(
                    UserSettings.isEquidistantRadiusFromLibrary()
                            ? EQUIDISTANT_FROM_LIBRARY : EQUIDISTANT_MANUAL);
        }
        if (this.equidistantRadius != null && UserSettings.getEquidistantRadiusMm() > 0.0) {
            this.equidistantRadius.setText(
                    String.format(Locale.US, "%.2f", UserSettings.getEquidistantRadiusMm()));
        }
        if (this.equidistantAllowance != null
                && Math.abs(UserSettings.getEquidistantAllowanceMm()) > 1.0E-9) {
            this.equidistantAllowance.setText(
                    String.format(Locale.US, "%.2f", UserSettings.getEquidistantAllowanceMm()));
        }
    }

    private void persistEquidistantSettings() {
        if (this.checkBoxEquidistant != null) {
            UserSettings.setEquidistantEnabled(this.checkBoxEquidistant.isSelected());
        }
        if (this.equidistantRadiusSourceComboBox != null
                && this.equidistantRadiusSourceComboBox.getValue() != null) {
            UserSettings.setEquidistantRadiusFromLibrary(
                    !EQUIDISTANT_MANUAL.equals(this.equidistantRadiusSourceComboBox.getValue()));
        }
        if (this.equidistantRadius != null) {
            // Пустое поле — это «радиуса вручную нет», а не ошибка: сбрасываем в 0.
            UserSettings.setEquidistantRadiusMm(
                    MainController.parsePositiveNumber(this.equidistantRadius.getText()).orElse(0.0));
        }
        if (this.equidistantAllowance != null) {
            UserSettings.setEquidistantAllowanceMm(
                    MainController.parseSignedNumber(this.equidistantAllowance.getText()).orElse(0.0));
        }
    }


    private void onMachineSettingsChanged() {
        this.redrawCanvas();
        this.refreshOpenSimulation3d();
    }

    private void refreshOpenSimulation3d() {
        List<GCodeMoveData> moves = this.buildSimulationContourMoveDataList();
        if (moves.isEmpty()) {
            return;
        }
        SimulationContext context = this.buildCompleteSimulationContext(moves);
        this.simulation3dWindow.refreshIfOpen(context, this.darkTheme);
    }

    private void refreshTurningAndCupLabels() {
        this.refreshCupLabels();
    }

    private void refreshCupLabels() {
        if (this.labelCupOuterDiameter == null) {
            return;
        }
        MainController.clearWarningStyle(this.labelCupOuterDiameter);
        OptionalDouble optionalDouble = MainController.parsePositiveNumber(this.cupRadius != null ? this.cupRadius.getText() : "");
        if (optionalDouble.isEmpty()) {
            this.labelCupOuterDiameter.setText("\u2014");
            return;
        }
        double d = optionalDouble.getAsDouble() * 2.0;
        this.labelCupOuterDiameter.setText(String.format(Locale.US, "%.3f", d));
    }

    private void setupWorkOffsets() {
        if (this.workOffsetsUiReady || this.workOffsetCountField == null || this.workOffsetsBox == null) {
            return;
        }
        this.workOffsetsUiReady = true;
        this.workOffsetCountField.textProperty().addListener((observableValue, string, string2) -> {
            this.rebuildWorkOffsetRows();
        });
        this.bindWorkOffsetField(this.workOffsetCountField);
        WorkOffsetStore.loadFromUserSettings();
        this.loadWorkOffsetsFromUserSettings();
        this.rebuildWorkOffsetRows(true);
        this.syncWorkOffsetsToStore();
    }

    private void rebuildWorkOffsetRows() {
        this.rebuildWorkOffsetRows(false);
    }

    private void rebuildWorkOffsetRows(boolean keepCachedValues) {
        if (this.workOffsetCountField == null || this.workOffsetsBox == null) {
            return;
        }
        if (!keepCachedValues) {
            this.storeWorkOffsetFieldsInCache();
        }
        this.workOffsetsBox.getChildren().clear();
        int n = this.parseWorkOffsetCount(this.workOffsetCountField.getText());
        for (int i = 0; i < n; ++i) {
            int n2 = 54 + i;
            this.workOffsetsBox.getChildren().add(this.createWorkOffsetRow(n2, this.workOffsetFieldCache.get(n2)));
        }
        // Расширенные смещения Siemens (G505–G599), которые реально используются
        // программой или уже сохранены, — отдельными строками после G54….
        for (Integer code : this.extendedWorkOffsetCodesForUi()) {
            this.workOffsetsBox.getChildren().add(
                    this.createWorkOffsetRow(code, this.workOffsetFieldCache.get(code)));
        }
        if (!keepCachedValues) {
            this.redrawCanvas();
        }
    }

    /** Коды G505–G599 для строк настроек: из текущей программы + сохранённые. */
    private java.util.SortedSet<Integer> extendedWorkOffsetCodesForUi() {
        java.util.TreeSet<Integer> codes = new java.util.TreeSet<>(WorkOffsetStore.extendedCodes());
        String programText = this.resolveProgramTextForGraph();
        if (programText != null && !programText.isBlank()) {
            for (String rawLine : programText.split("\\R")) {
                String line = GCodeProgramParser.stripFrameNumberForParse(
                        GCodeProgramParser.stripCommentsForParse(rawLine)).toUpperCase(Locale.US);
                if (line.isBlank()) {
                    continue;
                }
                Integer code = this.readWorkOffsetCode(line);
                if (code != null && code >= 505 && WorkOffsetStore.isSettableCode(code)) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    private void storeWorkOffsetFieldsInCache() {
        if (this.workOffsetsBox == null) {
            return;
        }
        for (Node node : this.workOffsetsBox.getChildren()) {
            if (!(node instanceof HBox hBox) || !(hBox.getUserData() instanceof Integer n)) {
                continue;
            }
            String string = "";
            String string2 = "";
            if (hBox.getChildren().size() > 1 && hBox.getChildren().get(1) instanceof TextField textField) {
                string = textField.getText();
            }
            if (hBox.getChildren().size() > 2 && hBox.getChildren().get(2) instanceof TextField textField) {
                string2 = textField.getText();
            }
            this.workOffsetFieldCache.put(n, new String[]{string, string2});
        }
    }

    private Node createWorkOffsetRow(int n, String[] stringArray) {
        HBox hBox = new HBox(6.0);
        hBox.setUserData(n);
        hBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label label = new Label("G" + n);
        // Единая ширина подписи для всех строк (G54 и G507) — поля X/Z в одну колонку.
        label.setMinWidth(44.0);
        label.setPrefWidth(44.0);
        TextField textField = new TextField();
        textField.setPromptText("X");
        textField.setPrefWidth(78.0);
        TextField textField2 = new TextField();
        textField2.setPromptText(String.valueOf(this.verticalProgramAxis()));
        textField2.setPrefWidth(78.0);
        if (stringArray != null) {
            WorkOffset offset = this.workOffsetFromCachedFields(n, stringArray);
            textField.setText(this.formatWorkOffsetField(offset.x));
            textField2.setText(this.formatWorkOffsetField(offset.z));
        }
        this.bindWorkOffsetField(textField);
        this.bindWorkOffsetField(textField2);
        hBox.getChildren().addAll(label, textField, textField2);
        return hBox;
    }

    private void bindWorkOffsetField(TextField textField) {
        if (textField == this.workOffsetCountField) {
            return;
        }
        textField.focusedProperty().addListener((observableValue, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused && this.settingsStage != null && this.settingsStage.isShowing()) {
                this.storeWorkOffsetFieldsInCache();
            }
        });
    }

    /** Пересчёт графика после «Сохранить» в настройках (смещения G54…). */
    private void applyWorkOffsetChanges() {
        this.storeWorkOffsetFieldsInCache();
        if (this.workOffsetCountField != null) {
            int count = this.parseWorkOffsetCount(this.workOffsetCountField.getText());
            UserSettings.setWorkOffsetCount(Math.max(0, Math.min(20, count)));
        }
        this.syncWorkOffsetsToStore();
        this.persistWorkOffsetsToUserSettings();
        this.refreshGraphManualOffsetsSnapshot();
        this.commitProgramTextFromEditor();
        String programText = this.appliedProgramText != null ? this.appliedProgramText : "";
        this.refreshActiveWorkOffsetFromProgram(programText);
        this.executionMoves = List.of();
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.fullPreviewVisible = true;
        this.toolpathGraphVisible = true;
        this.trajectoryPreviewComplete = true;
        this.pathDisplayActive = false;
        this.currentExecutionIndex = -1;
        WorkOffset active = this.graphManualOffsetsSnapshot.getOrDefault(
                this.resolveWorkOffsetCodeForGraph(),
                new WorkOffset(0.0, 0.0));
        if (programText.isBlank()) {
            this.updateProgressDisplay(
                    0,
                    String.format(
                            Locale.US,
                            I18n.text("app.106"),
                            this.resolveWorkOffsetCodeForGraph(),
                            active.x,
                            active.z));
        } else {
            List<ToolMove> raw = this.parseGCodeMovesRaw(programText);
            List<ToolMove> moves = this.applyManualWorkOffsetsToMoves(raw, programText);
            this.cachedToolMoves = moves;
            this.cachedToolMovesSource = this.toolMovesCacheKey();
            this.rebuildMoveSourceLineIndex(moves);
            int shiftedSegments = this.countShiftedMoves(raw, moves);
            this.updateProgressDisplay(
                    this.progressPercent(moves),
                    String.format(
                            Locale.US,
                            I18n.text("app.107"),
                            this.resolveWorkOffsetCodeForGraph(),
                            active.x,
                            active.z,
                            shiftedSegments));
        }
        this.drawCanvasNow();
        this.applyCanvasTransformations();
    }

    private void commitProgramTextFromEditor() {
        String live = this.resolveProgramTextForGraph();
        if (!live.equals(this.appliedProgramText)) {
            this.appliedProgramText = live;
            this.saveLastProgramText(live);
            this.cachedToolMovesSource = "";
            this.previewLayoutValid = false;
        }
    }

    private String resolveProgramTextForGraph() {
        if (this.programEditor != null) {
            String live = this.programEditor.getText();
            if (live != null && !live.isBlank()) {
                return live;
            }
        }
        return this.appliedProgramText != null ? this.appliedProgramText : "";
    }

    private int countShiftedMoves(List<ToolMove> raw, List<ToolMove> shifted) {
        if (raw == null || shifted == null || raw.size() != shifted.size()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < raw.size(); i++) {
            ToolMove a = raw.get(i);
            ToolMove b = shifted.get(i);
            if (Math.abs(a.endX - b.endX) > 1.0E-6 || Math.abs(a.endZ - b.endZ) > 1.0E-6) {
                count++;
            }
        }
        return count;
    }

    private int parseWorkOffsetCount(String string) {
        int saved = UserSettings.getWorkOffsetCount();
        int fallback = Math.max(0, Math.min(20, saved));
        if (string == null || string.trim().isEmpty()) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(string.trim());
            return Math.max(0, Math.min(20, n));
        }
        catch (NumberFormatException numberFormatException) {
            return fallback;
        }
    }

    private void refreshGraphManualOffsetsSnapshot() {
        this.storeWorkOffsetFieldsInCache();
        this.syncWorkOffsetsToStore();
        this.graphManualOffsetsSnapshot.clear();
        this.graphManualOffsetsSnapshot.putAll(this.readWorkOffsetsForParse());
    }

    private void refreshGraphManualOffsetsSnapshotFromSavedSettings() {
        WorkOffsetStore.applySinuTrainDemoLathePresetsIfUnset();
        WorkOffsetStore.loadFromUserSettings();
        this.loadWorkOffsetsFromUserSettings();
        if (this.workOffsetsBox != null
                && (this.settingsStage == null || !this.settingsStage.isShowing())) {
            this.rebuildWorkOffsetRows(true);
        }
        this.graphManualOffsetsSnapshot.clear();
        int count = UserSettings.getWorkOffsetCount();
        for (int i = 0; i < count; i++) {
            int code = 54 + i;
            WorkOffsetValues values = WorkOffsetStore.get(code);
            this.graphManualOffsetsSnapshot.put(code, new WorkOffset(values.xMm(), values.zMm()));
        }
        for (Integer code : WorkOffsetStore.extendedCodes()) {
            WorkOffsetValues values = WorkOffsetStore.get(code);
            this.graphManualOffsetsSnapshot.put(code, new WorkOffset(values.xMm(), values.zMm()));
        }
    }

    private Map<Integer, WorkOffsetValues> manualOffsetValuesForGraph() {
        Map<Integer, WorkOffsetValues> values = new LinkedHashMap<>();
        Map<Integer, WorkOffset> source = this.graphManualOffsetsSnapshot.isEmpty()
                ? this.readWorkOffsetsForParse()
                : this.graphManualOffsetsSnapshot;
        for (Map.Entry<Integer, WorkOffset> entry : source.entrySet()) {
            int code = entry.getKey();
            if (WorkOffsetStore.isSettableCode(code)) {
                WorkOffset offset = entry.getValue();
                values.put(code, new WorkOffsetValues(offset.x, offset.z));
            }
        }
        return values;
    }

    private Map<Integer, WorkOffset> readWorkOffsets() {
        this.syncWorkOffsetsToStore();
        return this.readWorkOffsetsForParse();
    }

    /** Карта G54… для парсера (после syncWorkOffsetsToStore). */
    private Map<Integer, WorkOffset> readWorkOffsetsForParse() {
        Map<Integer, WorkOffset> map = new HashMap<>();
        int count = UserSettings.getWorkOffsetCount();
        if (this.workOffsetCountField != null) {
            int fromField = this.parseWorkOffsetCount(this.workOffsetCountField.getText());
            count = fromField;
        }
        for (int i = 0; i < count; i++) {
            int code = 54 + i;
            WorkOffsetValues values = WorkOffsetStore.get(code);
            map.put(code, new WorkOffset(values.xMm(), values.zMm()));
        }
        for (Integer code : WorkOffsetStore.extendedCodes()) {
            WorkOffsetValues values = WorkOffsetStore.get(code);
            map.put(code, new WorkOffset(values.xMm(), values.zMm()));
        }
        for (Map.Entry<Integer, String[]> entry : this.workOffsetFieldCache.entrySet()) {
            int code = entry.getKey();
            boolean inCountRange = code >= 54 && code <= 73 && code < 54 + count;
            boolean extended = code >= 505 && WorkOffsetStore.isSettableCode(code);
            if (inCountRange || extended) {
                map.put(code, this.workOffsetFromCachedFields(code, entry.getValue()));
            }
        }
        this.ensureSettingsPanel();
        if (this.workOffsetsBox != null) {
            for (Node node : this.workOffsetsBox.getChildren()) {
                if (!(node instanceof HBox hBox) || !(hBox.getUserData() instanceof Integer n)) {
                    continue;
                }
                boolean inCountRange = n >= 54 && n < 54 + count;
                boolean extended = n >= 505 && WorkOffsetStore.isSettableCode(n);
                if (inCountRange || extended) {
                    map.put(n, this.readWorkOffsetFromRow(hBox));
                }
            }
        }
        return map;
    }

    private WorkOffset readWorkOffsetFromRow(HBox hBox) {
        double x = 0.0;
        double z = 0.0;
        if (hBox.getChildren().size() > 1 && hBox.getChildren().get(1) instanceof TextField xField) {
            OptionalDouble parsedX = MainController.parseSignedNumber(xField.getText());
            if (parsedX.isPresent()) {
                x = parsedX.getAsDouble();
            }
        }
        if (hBox.getChildren().size() > 2 && hBox.getChildren().get(2) instanceof TextField zField) {
            OptionalDouble parsedZ = MainController.parseSignedNumber(zField.getText());
            if (parsedZ.isPresent()) {
                z = parsedZ.getAsDouble();
            }
        }
        return this.normalizeWorkOffset(
                hBox.getUserData() instanceof Integer code ? code : -1,
                x,
                z);
    }

    /** UI/кэш → {@link WorkOffsetStore} (источник для парсера и prefs). */
    private void syncWorkOffsetsToStore() {
        this.storeWorkOffsetFieldsInCache();
        int count = UserSettings.getWorkOffsetCount();
        if (this.workOffsetCountField != null) {
            int fromField = this.parseWorkOffsetCount(this.workOffsetCountField.getText());
            count = fromField;
        }
        // Расширенные коды (G505–G599) собираем до очистки хранилища.
        java.util.TreeSet<Integer> extendedCodes = new java.util.TreeSet<>(WorkOffsetStore.extendedCodes());
        for (Integer cachedCode : this.workOffsetFieldCache.keySet()) {
            if (cachedCode != null && cachedCode >= 505 && WorkOffsetStore.isSettableCode(cachedCode)) {
                extendedCodes.add(cachedCode);
            }
        }
        if (this.workOffsetsBox != null) {
            for (Node node : this.workOffsetsBox.getChildren()) {
                if (node instanceof HBox hBox
                        && hBox.getUserData() instanceof Integer rowCode
                        && rowCode >= 505
                        && WorkOffsetStore.isSettableCode(rowCode)) {
                    extendedCodes.add(rowCode);
                }
            }
        }
        WorkOffsetStore.clear();
        for (int i = 0; i < count; i++) {
            this.syncSingleWorkOffsetToStore(54 + i);
        }
        for (Integer code : extendedCodes) {
            this.syncSingleWorkOffsetToStore(code);
        }
    }

    private void syncSingleWorkOffsetToStore(int code) {
        String[] cached = this.workOffsetFieldCache.get(code);
        WorkOffset offset = this.workOffsetFromCachedFields(code, cached);
        boolean hasSource = cached != null;
        if (this.workOffsetsBox != null) {
            for (Node node : this.workOffsetsBox.getChildren()) {
                if (node instanceof HBox hBox
                        && hBox.getUserData() instanceof Integer rowCode
                        && rowCode == code) {
                    offset = this.readWorkOffsetFromRow(hBox);
                    hasSource = true;
                    break;
                }
            }
        }
        if (!hasSource) {
            // Ни строки, ни кэша (панель не строилась): значение остаётся в prefs,
            // get() возьмёт его оттуда — нулями не затираем.
            return;
        }
        WorkOffsetStore.set(code, offset.x, offset.z);
    }

    private WorkOffset workOffsetFromCachedFields(int code, String[] fields) {
        double x = 0.0;
        double z = 0.0;
        if (fields != null) {
            if (fields.length > 0 && fields[0] != null) {
                OptionalDouble parsedX = MainController.parseSignedNumber(fields[0]);
                if (parsedX.isPresent()) {
                    x = parsedX.getAsDouble();
                }
            }
            if (fields.length > 1 && fields[1] != null) {
                OptionalDouble parsedZ = MainController.parseSignedNumber(fields[1]);
                if (parsedZ.isPresent()) {
                    z = parsedZ.getAsDouble();
                }
            }
        }
        return this.normalizeWorkOffset(code, x, z);
    }

    private WorkOffset normalizeWorkOffset(int code, double x, double z) {
        return new WorkOffset(x, z);
    }

    private static OptionalDouble parsePositiveNumber(String string) {
        if (string == null) {
            return OptionalDouble.empty();
        }
        String string2 = string.trim().replace(',', '.');
        if (string2.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            double d = Double.parseDouble(string2);
            if (!Double.isFinite(d) || d < 0.0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(d);
        }
        catch (NumberFormatException numberFormatException) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble parseSignedNumber(String string) {
        if (string == null) {
            return OptionalDouble.empty();
        }
        String string2 = string.trim().replace(',', '.');
        if (string2.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            double d = Double.parseDouble(string2);
            if (!Double.isFinite(d)) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(d);
        }
        catch (NumberFormatException numberFormatException) {
            return OptionalDouble.empty();
        }
    }

    private static void applyWarningStyle(Label ... labelArray) {
        for (Label label : labelArray) {
            if (label == null) continue;
            label.setStyle("-fx-text-fill: #b00020; -fx-font-family: monospace; -fx-font-size: 13px;");
        }
    }

    private static void clearWarningStyle(Label ... labelArray) {
        for (Label label : labelArray) {
            if (label == null) continue;
            label.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        }
    }

    private void setupPerformanceOptimizations() {
        Platform.runLater(() -> {
            if (this.paneCanvas.getScene() != null) {
                this.paneCanvas.getScene().getRoot().setCache(true);
                this.paneCanvas.getScene().getRoot().setCacheHint(CacheHint.SPEED);
            }
        });
    }

    @FXML
    private void menuSaveProgram() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.text("app.108"));
        FileChooser.ExtensionFilter mpfFilter = new FileChooser.ExtensionFilter(
                I18n.text("app.109"),
                "*.mpf", "*.MPF");
        fileChooser.getExtensionFilters().setAll(mpfFilter);
        fileChooser.setSelectedExtensionFilter(mpfFilter);
        fileChooser.setInitialFileName("program.mpf");
        if (this.lastSaveDirectory != null) {
            fileChooser.setInitialDirectory(this.lastSaveDirectory);
        } else {
            // Портативный режим: по умолчанию сохраняем на носитель рядом с программой,
            // а не на рабочий стол чужого компьютера. Рабочий стол — запасной вариант.
            File portableDir = PortableStorage.dataDir().getParent() != null
                    ? PortableStorage.dataDir().getParent().toFile()
                    : null;
            File desktopDir = new File(System.getProperty("user.home") + File.separator + "Desktop");
            if (portableDir != null && portableDir.isDirectory()) {
                fileChooser.setInitialDirectory(portableDir);
            } else if (desktopDir.isDirectory()) {
                fileChooser.setInitialDirectory(desktopDir);
            }
        }
        File file = fileChooser.showSaveDialog(this.anchorPaneCanvas.getScene().getWindow());
        if (file != null) {
            if (file.exists()) {
                Alert overwriteAlert = new Alert(Alert.AlertType.CONFIRMATION);
                overwriteAlert.setTitle(I18n.text("app.110"));
                overwriteAlert.setHeaderText(I18n.text("app.111") + file.getName() + I18n.text("app.112"));
                overwriteAlert.setContentText(I18n.text("app.113"));
                if (overwriteAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
            }
            this.lastSaveDirectory = file.getParentFile();
            this.saveProgramAsync(this.ensureMpfFile(file));
        }
    }

    private void recordProgramHistoryIfChanged(String previous, String incoming) {
        this.programEditHistory.record(previous != null ? previous : "", incoming != null ? incoming : "");
    }

    @FXML
    private void onProgramEditHistory() {
        Window owner = null;
        if (this.anchorPaneCanvas != null && this.anchorPaneCanvas.getScene() != null) {
            owner = this.anchorPaneCanvas.getScene().getWindow();
        } else if (this.programEditor != null && this.programEditor.getCodeArea().getScene() != null) {
            owner = this.programEditor.getCodeArea().getScene().getWindow();
        }
        if (owner == null) {
            return;
        }
        if (this.programHistoryStage != null && this.programHistoryStage.isShowing()) {
            this.refreshProgramHistoryView();
            this.programHistoryStage.toFront();
            return;
        }
        this.historyScrollBarsLinked = false;
        this.programHistoryListView = new ListView<>();
        this.programHistoryListView.setFocusTraversable(false);
        this.programHistoryListView.getStyleClass().add("history-list-view");
        this.programHistoryListView.setCellFactory(listView -> this.createHistoryCell());
        this.programHistoryClearButton = this.createHistoryClearButton();
        this.historyDeleteIcon = this.loadHistoryIconByName("delete");
        this.historyBackIcon = this.loadHistoryIconByName("back");
        this.programHistorySearchQuery = "";
        this.programHistorySearchButton = this.createHistorySearchButton();
        this.programHistorySearchField = this.createHistorySearchField();
        this.programHistorySearchStatus = new Label();
        this.programHistorySearchStatus.getStyleClass().add("history-search-status");
        this.programHistorySearchStatus.setVisible(false);
        this.programHistorySearchStatus.setManaged(false);
        HBox searchGroup = new HBox(8.0, this.programHistorySearchButton, this.programHistorySearchField,
                this.programHistorySearchStatus);
        searchGroup.setAlignment(Pos.CENTER_LEFT);
        Region historyToolbarSpacer = new Region();
        HBox.setHgrow(historyToolbarSpacer, Priority.ALWAYS);
        this.programHistoryToolbar = new HBox(12.0, searchGroup, historyToolbarSpacer, this.programHistoryClearButton);
        this.programHistoryToolbar.setAlignment(Pos.CENTER_LEFT);
        // Кнопки тулбара выровнены с чипами строк: слева красный чип начинается после
        // стрелки отката (22px + 6px), справа зелёный чип заканчивается перед
        // крестиком удаления (22px + 6px) + отступ строки 10px.
        this.programHistoryToolbar.setStyle("-fx-padding: 0 38 0 28;");
        this.programHistoryToolbar.setMaxWidth(Double.MAX_VALUE);
        this.programHistoryToolbar.getStyleClass().add("history-toolbar");
        this.programHistoryToolbar.setPickOnBounds(true);
        this.programHistoryExternalScrollBar = new ScrollBar();
        this.programHistoryExternalScrollBar.setOrientation(Orientation.VERTICAL);
        this.programHistoryExternalScrollBar.getStyleClass().add("history-external-scroll");
        this.programHistoryExternalScrollBar.setFocusTraversable(false);
        GridPane historyBody = new GridPane();
        historyBody.getStyleClass().add("history-window-body");
        historyBody.setVgap(0.0);
        ColumnConstraints contentColumn = new ColumnConstraints();
        contentColumn.setHgrow(Priority.ALWAYS);
        contentColumn.setMinWidth(0.0);
        ColumnConstraints scrollColumn = new ColumnConstraints();
        scrollColumn.setHgrow(Priority.NEVER);
        this.historyScrollColumn = scrollColumn;
        scrollColumn.setMinWidth(HISTORY_SCROLLBAR_WIDTH);
        scrollColumn.setPrefWidth(HISTORY_SCROLLBAR_WIDTH);
        scrollColumn.setMaxWidth(HISTORY_SCROLLBAR_WIDTH);
        historyBody.getColumnConstraints().addAll(contentColumn, scrollColumn);
        RowConstraints toolbarRow = new RowConstraints();
        toolbarRow.setVgrow(Priority.NEVER);
        toolbarRow.setMinHeight(72.0);
        toolbarRow.setPrefHeight(72.0);
        RowConstraints listRow = new RowConstraints();
        listRow.setVgrow(Priority.ALWAYS);
        listRow.setMinHeight(0.0);
        historyBody.getRowConstraints().addAll(toolbarRow, listRow);
        historyBody.add(this.programHistoryToolbar, 0, 0, 1, 1);
        historyBody.add(this.programHistoryListView, 0, 1);
        historyBody.add(this.programHistoryExternalScrollBar, 1, 0);
        GridPane.setHgrow(this.programHistoryListView, Priority.ALWAYS);
        GridPane.setVgrow(this.programHistoryListView, Priority.ALWAYS);
        GridPane.setHgrow(this.programHistoryToolbar, Priority.ALWAYS);
        GridPane.setRowSpan(this.programHistoryExternalScrollBar, 2);
        GridPane.setVgrow(this.programHistoryExternalScrollBar, Priority.ALWAYS);
        this.programHistoryListView.skinProperty().addListener((observable, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(this::linkHistoryScrollBars);
            }
        });
        BorderPane historyRoot = new BorderPane();
        historyRoot.setCenter(historyBody);
        historyRoot.getStyleClass().add("history-window-root");
        javafx.geometry.Rectangle2D historyBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(
                historyRoot,
                Math.min(HISTORY_WINDOW_WIDTH, historyBounds.getWidth() * 0.92),
                Math.min(HISTORY_WINDOW_HEIGHT, historyBounds.getHeight() * 0.9));
        URL url = MainController.class.getResource("/app.css");
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
        if (this.darkTheme) {
            historyRoot.getStyleClass().add("dark-theme");
        }
        this.applyHistorySceneFill(scene);
        this.programHistoryStage = new Stage();
        this.programHistoryStage.setTitle(I18n.text("app.114"));
        this.programHistoryStage.initOwner(owner);
        this.programHistoryStage.setScene(scene);
        this.applyHistoryStageIcon(owner);
        this.refreshProgramHistoryView();
        this.refreshHistoryClearButtonTheme();
        this.programHistoryStage.show();
    }

    private void applyHistoryStageIcon(Window owner) {
        if (this.programHistoryStage == null) {
            return;
        }
        if (owner instanceof Stage ownerStage && !ownerStage.getIcons().isEmpty()) {
            this.programHistoryStage.getIcons().setAll(ownerStage.getIcons());
            return;
        }
        String[] candidates = new String[]{"icon.ico", "launch/icon.ico", "launch/CNC_Modeling/icon.ico"};
        for (String candidate : candidates) {
            File iconFile = new File(candidate);
            if (!iconFile.isFile()) {
                continue;
            }
            try {
                this.programHistoryStage.getIcons().setAll(new Image(iconFile.toURI().toString()));
                return;
            }
            catch (RuntimeException runtimeException) {
            }
        }
    }

    private void refreshProgramHistoryView() {
        if (this.programHistoryListView == null) {
            return;
        }
        String text = this.programEditHistory.renderNewestFirst();
        String[] lines = text.split("\\R", -1);
        long entryTimestamp = -1L;
        ObservableList<HistoryRow> rows = FXCollections.observableArrayList();
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("!! ")) {
                rows.add(HistoryRow.info(line.substring(3)));
                continue;
            }
            if (line.equals(I18n.text("history.empty"))) {
                continue;
            }
            if (!line.contains(" \u2192 ") && !line.startsWith("!! ")) {
                long tsHeader = this.parseHistoryTimestamp(line);
                if (tsHeader > 0L) {
                    entryTimestamp = tsHeader;
                    rows.add(HistoryRow.header(HISTORY_HEADER_DISPLAY.format(Instant.ofEpochMilli(tsHeader)), tsHeader));
                    continue;
                }
            }
            String[] pair = line.split(" \\u2192 ", 2);
            String oldLine = pair.length > 0 ? pair[0] : "";
            String newLine = pair.length > 1 ? pair[1] : "";
            if (("…".equals(oldLine) || "...".equals(oldLine)) && (newLine.startsWith("и ещё ") || newLine.startsWith("и еще "))) {
                continue;
            }
            rows.add(HistoryRow.change(entryTimestamp, oldLine, newLine));
        }
        if (rows.isEmpty()) {
            rows.add(HistoryRow.info(I18n.text("app.115")));
        }
        this.programHistoryAllRows = rows;
        this.applyHistorySearchFilter();
    }

    /** \u041a\u043d\u043e\u043f\u043a\u0430-\u043b\u0443\u043f\u0430: \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442/\u043f\u0440\u044f\u0447\u0435\u0442 \u043f\u043e\u043b\u0435 \u043f\u043e\u0438\u0441\u043a\u0430 \u043f\u043e \u0438\u0441\u0442\u043e\u0440\u0438\u0438. */
    private Button createHistorySearchButton() {
        Button button = new Button();
        button.getStyleClass().addAll("secondary-button", "history-search-button");
        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent("M9.5 2a7.5 7.5 0 0 1 5.93 12.09l4.24 4.24-1.34 1.34-4.24-4.24"
                + "A7.5 7.5 0 1 1 9.5 2zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z");
        icon.getStyleClass().add("history-search-icon");
        button.setGraphic(icon);
        button.setTooltip(ToolIconFactory.fastTooltip(new Tooltip(I18n.text("app.116"))));
        button.setOnAction(event -> this.toggleHistorySearch());
        return button;
    }

    private TextField createHistorySearchField() {
        TextField field = new TextField();
        field.getStyleClass().add("history-search-field");
        field.setPromptText(I18n.text("app.117"));
        field.setPrefWidth(300.0);
        field.setVisible(false);
        field.setManaged(false);
        field.textProperty().addListener((observable, oldText, newText) -> {
            this.programHistorySearchQuery = newText == null ? "" : newText;
            this.applyHistorySearchFilter();
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                this.toggleHistorySearch();
                event.consume();
            }
        });
        return field;
    }

    private void toggleHistorySearch() {
        if (this.programHistorySearchField == null) {
            return;
        }
        boolean show = !this.programHistorySearchField.isVisible();
        this.programHistorySearchField.setVisible(show);
        this.programHistorySearchField.setManaged(show);
        if (this.programHistorySearchButton != null) {
            if (show) {
                if (!this.programHistorySearchButton.getStyleClass().contains("history-search-button-active")) {
                    this.programHistorySearchButton.getStyleClass().add("history-search-button-active");
                }
            } else {
                this.programHistorySearchButton.getStyleClass().remove("history-search-button-active");
                this.updateHistorySearchStatus("");
            }
        }
        if (show) {
            this.programHistorySearchField.requestFocus();
        } else {
            this.programHistorySearchField.clear();
            this.programHistorySearchQuery = "";
            this.applyHistorySearchFilter();
        }
    }

    /**
     * \u0424\u0438\u043b\u044c\u0442\u0440 \u0438\u0441\u0442\u043e\u0440\u0438\u0438: \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u044e\u0442\u0441\u044f \u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u044f, \u0432 \u0441\u0442\u0430\u0440\u043e\u0439 \u0438\u043b\u0438 \u043d\u043e\u0432\u043e\u0439 \u0441\u0442\u0440\u043e\u043a\u0435 \u043a\u043e\u0442\u043e\u0440\u044b\u0445
     * \u0435\u0441\u0442\u044c \u0442\u0435\u043a\u0441\u0442 \u0437\u0430\u043f\u0440\u043e\u0441\u0430; \u0441\u043e\u0432\u043f\u0430\u0434\u0435\u043d\u0438\u0435 \u043f\u043e \u0437\u0430\u0433\u043e\u043b\u043e\u0432\u043a\u0443 (\u0434\u0430\u0442\u0435) \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0432\u0435\u0441\u044c \u0431\u043b\u043e\u043a.
     */
    private void applyHistorySearchFilter() {
        if (this.programHistoryListView == null) {
            return;
        }
        String query = this.programHistorySearchQuery == null
                ? ""
                : this.programHistorySearchQuery.trim().toLowerCase(Locale.ROOT);
        ObservableList<HistoryRow> source = this.programHistoryAllRows;
        ObservableList<HistoryRow> shown;
        this.updateHistorySearchStatus("");
        if (query.isEmpty()) {
            shown = source;
        } else {
            shown = FXCollections.observableArrayList();
            HistoryRow pendingHeader = null;
            boolean headerEmitted = false;
            boolean headerMatches = false;
            for (HistoryRow row : source) {
                if (row.type == HistoryRowType.HEADER) {
                    pendingHeader = row;
                    headerEmitted = false;
                    headerMatches = row.text != null
                            && row.text.toLowerCase(Locale.ROOT).contains(query);
                    if (headerMatches) {
                        shown.add(row);
                        headerEmitted = true;
                    }
                } else if (row.type == HistoryRowType.CHANGE) {
                    boolean match = headerMatches
                            || (row.oldLine != null && row.oldLine.toLowerCase(Locale.ROOT).contains(query))
                            || (row.newLine != null && row.newLine.toLowerCase(Locale.ROOT).contains(query));
                    if (match) {
                        if (pendingHeader != null && !headerEmitted) {
                            shown.add(pendingHeader);
                            headerEmitted = true;
                        }
                        shown.add(row);
                    }
                }
            }
            if (shown.isEmpty()) {
                this.updateHistorySearchStatus(
                        I18n.text("app.118")
                                + this.programHistorySearchQuery.trim() + "\u00bb");
            }
        }
        this.syncHistoryWindowChrome(shown);
        this.programHistoryRows = shown;
        this.programHistoryListView.setItems(shown);
        this.scrollHistoryListToTop();
    }

    private void updateHistorySearchStatus(String text) {
        if (this.programHistorySearchStatus == null) {
            return;
        }
        boolean show = text != null && !text.isBlank();
        this.programHistorySearchStatus.setText(show ? text : "");
        this.programHistorySearchStatus.setVisible(show);
        this.programHistorySearchStatus.setManaged(show);
    }

    private void scrollHistoryListToTop() {
        if (this.programHistoryListView == null) {
            return;
        }
        Platform.runLater(() -> {
            if (this.programHistoryListView == null) {
                return;
            }
            this.linkHistoryScrollBars();
            this.programHistoryListView.scrollTo(0);
            this.resetHistoryScrollPosition();
            Platform.runLater(() -> {
                if (this.programHistoryListView == null) {
                    return;
                }
                this.linkHistoryScrollBars();
                this.programHistoryListView.scrollTo(0);
                this.resetHistoryScrollPosition();
            });
        });
    }

    private void resetHistoryScrollPosition() {
        Node scrollBarNode = this.programHistoryListView.lookup(".scroll-bar:vertical");
        if (scrollBarNode instanceof ScrollBar internalScrollBar) {
            internalScrollBar.setValue(0.0);
        }
        if (this.programHistoryExternalScrollBar != null) {
            this.programHistoryExternalScrollBar.setValue(0.0);
        }
    }

    private void linkHistoryScrollBars() {
        if (this.programHistoryListView == null || this.programHistoryExternalScrollBar == null) {
            return;
        }
        this.programHistoryListView.applyCss();
        this.programHistoryListView.layout();
        Node corner = this.programHistoryListView.lookup(".corner");
        if (corner instanceof Region region) {
            region.setVisible(false);
            region.setManaged(false);
            region.setPrefSize(0.0, 0.0);
            region.setMinSize(0.0, 0.0);
            region.setMaxSize(0.0, 0.0);
        }
        Node scrollBarNode = this.programHistoryListView.lookup(".scroll-bar:vertical");
        if (!(scrollBarNode instanceof ScrollBar internalScrollBar)) {
            return;
        }
        internalScrollBar.setVisible(false);
        internalScrollBar.setManaged(false);
        internalScrollBar.setPrefWidth(0.0);
        internalScrollBar.setMinWidth(0.0);
        internalScrollBar.setMaxWidth(0.0);
        if (!this.historyScrollBarsLinked) {
            this.programHistoryExternalScrollBar.minProperty().bind(internalScrollBar.minProperty());
            this.programHistoryExternalScrollBar.maxProperty().bind(internalScrollBar.maxProperty());
            this.programHistoryExternalScrollBar.visibleAmountProperty().bind(internalScrollBar.visibleAmountProperty());
            this.programHistoryExternalScrollBar.valueProperty().bindBidirectional(internalScrollBar.valueProperty());
            this.programHistoryExternalScrollBar.unitIncrementProperty().bind(internalScrollBar.unitIncrementProperty());
            this.programHistoryExternalScrollBar.blockIncrementProperty().bind(internalScrollBar.blockIncrementProperty());
            this.historyScrollBarsLinked = true;
        }
        this.programHistoryExternalScrollBar.setPadding(Insets.EMPTY);
        this.programHistoryExternalScrollBar.setMinHeight(0.0);
        for (String selector : new String[]{".increment-button", ".decrement-button"}) {
            Node part = this.programHistoryExternalScrollBar.lookup(selector);
            if (part instanceof Region region) {
                region.setVisible(false);
                region.setManaged(false);
                region.setPadding(Insets.EMPTY);
                region.setMinHeight(0.0);
                region.setPrefHeight(0.0);
                region.setMaxHeight(0.0);
            }
        }
        Node track = this.programHistoryExternalScrollBar.lookup(".track");
        if (track instanceof Region trackRegion) {
            trackRegion.setPadding(Insets.EMPTY);
            trackRegion.setMinHeight(0.0);
        }
        this.updateHistoryScrollbarVisibility(this.historyHasScrollableEntries);
    }

    private boolean isHistoryScrollNeeded() {
        if (this.programHistoryListView == null || !this.historyHasScrollableEntries) {
            return false;
        }
        ObservableList<HistoryRow> items = this.programHistoryListView.getItems();
        int changeRows = 0;
        for (HistoryRow row : items) {
            if (row.type == HistoryRowType.CHANGE) {
                ++changeRows;
            }
        }
        if (changeRows <= 1) {
            return false;
        }
        this.programHistoryListView.applyCss();
        this.programHistoryListView.layout();
        double viewportHeight = this.programHistoryListView.getHeight();
        if (viewportHeight <= 1.0) {
            return false;
        }
        double estimatedHeight = 0.0;
        for (HistoryRow row : items) {
            if (row.type == HistoryRowType.CHANGE) {
                estimatedHeight += 76.0;
            } else if (row.type == HistoryRowType.HEADER) {
                estimatedHeight += 30.0;
            } else {
                estimatedHeight += 24.0;
            }
        }
        return estimatedHeight > viewportHeight + 12.0;
    }

    private void updateHistoryScrollbarVisibility(boolean allowScroll) {
        if (this.programHistoryExternalScrollBar == null) {
            return;
        }
        boolean show = allowScroll && this.isHistoryScrollNeeded();
        this.programHistoryExternalScrollBar.setVisible(show);
        this.programHistoryExternalScrollBar.setManaged(show);
        double columnWidth = show ? HISTORY_SCROLLBAR_WIDTH : 0.0;
        if (this.historyScrollColumn != null) {
            this.historyScrollColumn.setMinWidth(columnWidth);
            this.historyScrollColumn.setPrefWidth(columnWidth);
            this.historyScrollColumn.setMaxWidth(columnWidth);
        }
        if (!show && this.programHistoryListView != null) {
            this.programHistoryListView.setPadding(new Insets(0.0, 8.0, 8.0, 24.0));
        }
    }

    private void syncHistoryWindowChrome(ObservableList<HistoryRow> rows) {
        if (this.programHistoryListView == null) {
            return;
        }
        boolean hasEntries = false;
        for (HistoryRow row : rows) {
            if (row.type == HistoryRowType.HEADER || row.type == HistoryRowType.CHANGE) {
                hasEntries = true;
                break;
            }
        }
        boolean hasAnyEntries = hasEntries;
        if (!hasAnyEntries && this.programHistoryAllRows != null) {
            for (HistoryRow row : this.programHistoryAllRows) {
                if (row.type == HistoryRowType.HEADER || row.type == HistoryRowType.CHANGE) {
                    hasAnyEntries = true;
                    break;
                }
            }
        }
        if (this.programHistoryToolbar != null) {
            this.programHistoryToolbar.setVisible(hasAnyEntries);
            this.programHistoryToolbar.setManaged(hasAnyEntries);
        }
        if (this.programHistoryClearButton != null) {
            this.programHistoryClearButton.setVisible(hasAnyEntries);
            this.programHistoryClearButton.setManaged(hasAnyEntries);
        }
        this.historyHasScrollableEntries = hasEntries;
        this.programHistoryListView.setPadding(new Insets(0.0, 8.0, 8.0, 24.0));
        GridPane.setMargin(this.programHistoryListView, Insets.EMPTY);
        this.updateHistoryScrollbarVisibility(false);
        if (hasEntries) {
            Platform.runLater(() -> {
                this.updateHistoryScrollbarVisibility(true);
                Platform.runLater(() -> this.updateHistoryScrollbarVisibility(true));
            });
        }
    }

    private Button createHistoryClearButton() {
        Button button = new Button(I18n.text("app.119"));
        button.getStyleClass().add("button-36");
        button.setFocusTraversable(false);
        button.setOnAction(actionEvent -> this.onClearProgramHistory());
        button.setStyle(null);
        return button;
    }

    private void refreshHistoryClearButtonTheme() {
        if (this.programHistoryClearButton == null) {
            return;
        }
        this.programHistoryClearButton.setStyle(null);
        this.programHistoryClearButton.applyCss();
        if (this.programHistoryStage != null && this.programHistoryStage.getScene() != null) {
            this.applyHistorySceneFill(this.programHistoryStage.getScene());
        }
    }

    private void applyHistorySceneFill(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.setFill(Color.web(this.darkTheme ? "#0b0b0b" : "#f8fafc"));
    }

    private ListCell<HistoryRow> createHistoryCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(HistoryRow item, boolean empty) {
                super.updateItem(item, empty);
                this.getStyleClass().remove("history-cell-header");
                if (empty || item == null) {
                    this.setText(null);
                    this.setGraphic(null);
                    return;
                }
                if (item.type == HistoryRowType.HEADER) {
                    this.getStyleClass().add("history-cell-header");
                    Label ts = new Label(item.text);
                    ts.getStyleClass().add("history-header-label");
                    Button revertBlockButton = MainController.this.createHistoryActionButton(
                            MainController.this.historyBackIcon, "\u21a9");
                    revertBlockButton.setOnAction(actionEvent -> revertHistoryBlock(item));
                    Region headerIndent = new Region();
                    headerIndent.setMinWidth(28.0);
                    headerIndent.setPrefWidth(28.0);
                    headerIndent.setMaxWidth(28.0);
                    HBox headerRow = new HBox(6.0, headerIndent, ts, revertBlockButton);
                    headerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    headerRow.setPadding(Insets.EMPTY);
                    this.setGraphic(headerRow);
                    this.setText(null);
                    return;
                }
                if (item.type == HistoryRowType.INFO) {
                    Label info = new Label(item.text);
                    info.getStyleClass().add("history-info-row");
                    Region infoIndent = new Region();
                    infoIndent.setMinWidth(28.0);
                    infoIndent.setPrefWidth(28.0);
                    infoIndent.setMaxWidth(28.0);
                    HBox infoRow = new HBox(6.0, infoIndent, info);
                    infoRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    this.setGraphic(infoRow);
                    this.setText(null);
                    return;
                }
                this.setGraphic(buildHistoryChangeRow(item));
                this.setText(null);
            }
        };
    }

    private HBox buildHistoryChangeRow(HistoryRow item) {
        long timestamp = item.timestamp;
        String oldLine = item.oldLine;
        String newLine = item.newLine;
        String oldValue = oldLine.isBlank() ? I18n.text("app.120") : oldLine;
        String newValue = newLine.isBlank() ? I18n.text("app.121") : newLine;
        Button revertButton = this.createHistoryActionButton(this.historyBackIcon, "↩");
        revertButton.setOnAction(actionEvent -> this.revertHistoryEntry(timestamp, oldLine, newLine));
        Label oldLabel = new Label(I18n.text("app.122") + oldValue);
        oldLabel.setWrapText(true);
        oldLabel.getStyleClass().add("history-old-chip");
        Label newLabel = new Label(I18n.text("app.123") + newValue);
        newLabel.setWrapText(true);
        newLabel.getStyleClass().add("history-new-chip");
        if (!newLine.isBlank() && !I18n.text("app.121").equals(newValue)) {
            newLabel.setOnMouseClicked(mouseEvent -> this.navigateToHistoryLine(newLine));
            newLabel.getStyleClass().add("history-new-chip-clickable");
        }
        Label arrow = new Label("\u2192");
        arrow.getStyleClass().add("history-arrow");
        Button deleteButton = this.createHistoryActionButton(this.historyDeleteIcon, "✕");
        deleteButton.setOnAction(actionEvent -> this.deleteHistoryEntry(timestamp, oldLine, newLine));
        HBox rowBox = new HBox(6.0, revertButton, oldLabel, arrow, newLabel, deleteButton);
        rowBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        rowBox.setPadding(new Insets(0.0, 10.0, 0.0, 0.0));
        HBox.setHgrow(oldLabel, Priority.ALWAYS);
        HBox.setHgrow(newLabel, Priority.ALWAYS);
        oldLabel.setMaxWidth(Double.MAX_VALUE);
        newLabel.setMaxWidth(Double.MAX_VALUE);
        return rowBox;
    }

    private Image loadHistoryIconByName(String baseName) {
        String[] candidates = new String[]{
                "/history-icons/" + baseName + ".png",
                "/history-icons/" + baseName + "_icon.png",
                "/history-icons/" + baseName + ".ico",
                "/history-icons/" + baseName + "_icon.ico"
        };
        for (String resourcePath : candidates) {
            try {
                URL url = MainController.class.getResource(resourcePath);
                if (url == null) {
                    continue;
                }
                Image icon = new Image(url.toExternalForm(), 14.0, 14.0, true, true);
                if (!icon.isError() && icon.getWidth() > 0.0) {
                    return icon;
                }
            }
            catch (RuntimeException runtimeException) {
            }
        }
        return null;
    }

    private Button createHistoryActionButton(Image icon, String fallback) {
        Button button = new Button();
        button.setPrefWidth(22.0);
        button.setMinWidth(22.0);
        button.setPrefHeight(22.0);
        button.setMinHeight(22.0);
        button.setMaxWidth(22.0);
        button.setMaxHeight(22.0);
        button.setPadding(Insets.EMPTY);
        button.setFocusTraversable(false);
        button.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-cursor: hand;");
        if (icon != null && !icon.isError() && icon.getWidth() > 0.0) {
            ImageView view = new ImageView(icon);
            view.setFitWidth(15.0);
            view.setFitHeight(15.0);
            if (this.darkTheme) {
                ColorAdjust bright = new ColorAdjust();
                bright.setBrightness(0.35);
                view.setEffect(bright);
            } else {
                view.setEffect(null);
            }
            button.setGraphic(view);
        } else {
            button.setText(fallback);
            String textColor = this.darkTheme ? "#e2e8f0" : "#0f172a";
            button.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-cursor: hand; -fx-font-size: 13px; -fx-font-weight: 900; -fx-text-fill: " + textColor + ";");
        }
        return button;
    }

    private long parseHistoryTimestamp(String line) {
        if (line == null) {
            return -1L;
        }
        String value = line.replaceFirst("^[=\\-\\s]+", "").trim();
        try {
            return java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        catch (RuntimeException runtimeException) {
            return -1L;
        }
    }

    private String decodeHistoryDisplayLine(String displayValue, boolean isOldSide) {
        if (I18n.text("app.120").equals(displayValue)) {
            return "";
        }
        if (I18n.text("app.121").equals(displayValue)) {
            return isOldSide ? displayValue : "";
        }
        return displayValue;
    }

    private void deleteHistoryEntry(long timestamp, String oldLine, String newLine) {
        String oldRaw = this.decodeHistoryDisplayLine(oldLine, true);
        String newRaw = this.decodeHistoryDisplayLine(newLine, false);
        boolean removed = this.programEditHistory.deleteChange(timestamp, oldRaw, newRaw);
        if (!removed) {
            this.showInfoDialog(I18n.text("app.114"), I18n.text("app.124"));
            return;
        }
        this.refreshProgramHistoryView();
    }

    private void revertHistoryEntry(long timestamp, String oldLine, String newLine) {
        if (this.programEditor == null) {
            this.showInfoDialog(I18n.text("app.114"), I18n.text("app.125"));
            return;
        }
        String current = this.programEditor.getText();
        // По снимку «до правки» полностью удалённая строка ВСТАВЛЯЕТСЯ на своё место
        // (сдвигая существующий текст вниз), модификация откатывается на месте.
        String snapshot = this.programEditHistory.oldSnapshotForSecond(timestamp);
        String reverted = this.reverseChanges(
                current, snapshot, java.util.List.of(HistoryRow.change(timestamp, oldLine, newLine)));
        if (reverted == null || Objects.equals(current, reverted)) {
            this.showInfoDialog(I18n.text("app.114"), I18n.text("app.126"));
            return;
        }
        this.programEditor.setText(reverted);
        this.recordProgramHistoryIfChanged(current, reverted);
        this.appliedProgramText = reverted;
        this.saveLastProgramText(reverted);
        this.cachedToolMovesSource = "";
        this.refreshProgramHistoryView();
    }

    /**
     * Применяет ОБРАТНУЮ правку для набора изменений: удалённые строки возвращаются
     * на исходное место по снимку «до правки» (рядом с уцелевшим соседом), изменения
     * откатываются на месте, вставки убираются. Так и одна строка, и целый блок
     * восстанавливаются ТАМ ЖЕ, где были. Возвращает null, если ничего не применилось.
     */
    private String reverseChanges(String current, String snapshot, java.util.List<HistoryRow> changes) {
        String separator = current.indexOf("\r\n") >= 0 ? "\r\n" : "\n";
        java.util.List<String> cur =
                new java.util.ArrayList<>(java.util.Arrays.asList(current.split("\\R", -1)));
        java.util.List<String> snap = snapshot == null
                ? null
                : java.util.Arrays.asList(snapshot.split("\\R", -1));
        boolean changed = false;
        for (HistoryRow row : changes) {
            boolean deleted = I18n.text("app.121").equals(row.newLine);
            boolean oldEmpty = I18n.text("app.120").equals(row.oldLine);
            String oldRaw = this.decodeHistoryDisplayLine(row.oldLine, true);
            String newRaw = this.decodeHistoryDisplayLine(row.newLine, false);
            if (deleted) {
                this.insertDeletedLine(cur, snap, oldRaw);
                changed = true;
            } else if (oldEmpty) {
                int idx = this.indexOfProgramLine(cur, newRaw, 0);
                if (idx >= 0) {
                    cur.remove(idx);
                    changed = true;
                }
            } else {
                int idx = this.indexOfProgramLine(cur, newRaw, 0);
                if (idx >= 0) {
                    cur.set(idx, oldRaw);
                    changed = true;
                }
            }
        }
        return changed ? String.join(separator, cur) : null;
    }

    /** Возврат удалённой строки на её место по снимку (рядом с уцелевшим соседом), иначе в конец. */
    private void insertDeletedLine(java.util.List<String> cur, java.util.List<String> snap, String oldRaw) {
        if (snap != null) {
            // Поиск с допуском по пробелам: снимок хранит «сырые» строки, а oldRaw —
            // обрезанная из истории; точный indexOf промахивался и кидал строку наверх.
            int i = this.indexOfProgramLine(snap, oldRaw, 0);
            if (i >= 0) {
                for (int k = i - 1; k >= 0; --k) {
                    int c = this.indexOfProgramLine(cur, snap.get(k), 0);
                    if (c >= 0) {
                        cur.add(c + 1, oldRaw);
                        return;
                    }
                }
                cur.add(0, oldRaw);
                return;
            }
        }
        cur.add(oldRaw);
    }

    /** Поиск строки программы (с допуском по ведущим/хвостовым пробелам), начиная с from. */
    private int indexOfProgramLine(java.util.List<String> lines, String target, int from) {
        for (int i = Math.max(0, from); i < lines.size(); ++i) {
            String line = lines.get(i);
            if (line.equals(target) || line.trim().equals(target == null ? "" : target.trim())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Откат блока без снимка (старые записи): разворачиваем дифф ПО ПОРЯДКУ —
     * изменения/вставки ищем по «новой» строке-якорю и переносим неизменённые строки
     * до неё, удалённые возвращаем по ходу. Удалённый сверху блок встаёт сверху.
     */
    private String reversePatchBlock(String current, java.util.List<HistoryRow> blockChanges) {
        String separator = current.indexOf("\r\n") >= 0 ? "\r\n" : "\n";
        java.util.List<String> newLines =
                new java.util.ArrayList<>(java.util.Arrays.asList(current.split("\\R", -1)));
        java.util.List<String> oldLines = new java.util.ArrayList<>();
        int pointer = 0;
        boolean changed = false;
        for (HistoryRow row : blockChanges) {
            boolean deleted = I18n.text("app.121").equals(row.newLine);
            boolean oldEmpty = I18n.text("app.120").equals(row.oldLine);
            String oldRaw = this.decodeHistoryDisplayLine(row.oldLine, true);
            String newRaw = this.decodeHistoryDisplayLine(row.newLine, false);
            if (deleted) {
                oldLines.add(oldRaw);
                changed = true;
                continue;
            }
            int idx = this.indexOfProgramLine(newLines, newRaw, pointer);
            if (idx < 0) {
                if (!oldEmpty) {
                    oldLines.add(oldRaw);
                    changed = true;
                }
                continue;
            }
            for (int k = pointer; k < idx; ++k) {
                oldLines.add(newLines.get(k));
            }
            if (!oldEmpty) {
                oldLines.add(oldRaw);
            }
            changed = true;
            pointer = idx + 1;
        }
        for (int k = pointer; k < newLines.size(); ++k) {
            oldLines.add(newLines.get(k));
        }
        return changed ? String.join(separator, oldLines) : null;
    }

    private void revertHistoryBlock(HistoryRow headerRow) {
        if (this.programEditor == null || headerRow == null) {
            return;
        }
        // Откат работает по полному списку, а не по отфильтрованному поиском.
        ObservableList<HistoryRow> allRows =
                this.programHistoryAllRows != null && !this.programHistoryAllRows.isEmpty()
                        ? this.programHistoryAllRows
                        : this.programHistoryRows;
        if (allRows == null || allRows.isEmpty()) {
            return;
        }
        // Собираем ВСЕ изменения блока по метке времени заголовка: каждая строка
        // изменения хранит timestamp своего блока. Это надёжнее обхода по позиции
        // (не зависит от поиска/пересоздания объектов списка) — раньше из-за этого
        // блочная кнопка могла не вернуть ничего.
        ArrayList<HistoryRow> blockChanges = new ArrayList<>();
        for (HistoryRow row : allRows) {
            if (row.type == HistoryRowType.CHANGE && row.timestamp == headerRow.timestamp) {
                blockChanges.add(row);
            }
        }
        if (blockChanges.isEmpty()) {
            this.showInfoDialog(I18n.text("app.114"), I18n.text("app.127"));
            return;
        }
        String current = this.programEditor.getText();
        // Есть снимок «до правки» (новые записи) — точное восстановление НА МЕСТЕ.
        // Нет снимка (старые записи) — разворачиваем дифф по порядку (реверс-патч),
        // чтобы блок хотя бы шёл в исходном порядке/месте, а не как попало.
        String snapshot = this.programEditHistory.oldSnapshotForSecond(headerRow.timestamp);
        String reverted = snapshot != null
                ? this.reverseChanges(current, snapshot, blockChanges)
                : this.reversePatchBlock(current, blockChanges);
        if (reverted == null || Objects.equals(current, reverted)) {
            this.showInfoDialog(I18n.text("app.114"), I18n.text("app.128"));
            return;
        }
        this.programEditor.setText(reverted);
        this.recordProgramHistoryIfChanged(current, reverted);
        this.appliedProgramText = reverted;
        this.saveLastProgramText(reverted);
        this.cachedToolMovesSource = "";
        this.syncProgramEditorVisuals();
        this.redrawCanvas();
        this.refreshProgramHistoryView();
    }

    private void onClearProgramHistory() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.text("app.129"));
        alert.setHeaderText(I18n.text("app.130"));
        alert.setContentText(I18n.text("app.131"));
        if (this.programHistoryStage != null) {
            alert.initOwner(this.programHistoryStage);
        }
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        this.programEditHistory.clear();
        this.refreshProgramHistoryView();
    }

    private void navigateToHistoryLine(String targetLine) {
        if (this.programEditor == null || targetLine == null || targetLine.isBlank()) {
            this.showInfoDialog(I18n.text("app.114"), I18n.text("app.132"));
            return;
        }
        String text = this.programEditor.getText();
        int lineNumber = 1;
        int scanFrom = 0;
        while (scanFrom <= text.length()) {
            int idx = text.indexOf(targetLine, scanFrom);
            if (idx < 0) {
                break;
            }
            boolean leftBoundary = idx == 0 || text.charAt(idx - 1) == '\n' || text.charAt(idx - 1) == '\r';
            int end = idx + targetLine.length();
            boolean rightBoundary = end == text.length() || text.charAt(end) == '\n' || text.charAt(end) == '\r';
            if (leftBoundary && rightBoundary) {
                lineNumber = 1;
                for (int i = 0; i < idx; ++i) {
                    if (text.charAt(i) == '\n') {
                        ++lineNumber;
                    }
                }
                this.programEditor.showLine(lineNumber);
                this.programEditor.revealAndSelectRange(idx, end);
                this.programEditor.requestFocus();
                this.updateProgressDisplay(this.progressPercent(this.parseGCodeMoves(text)), String.format(Locale.US, I18n.text("app.133"), lineNumber));
                return;
            }
            scanFrom = idx + Math.max(1, targetLine.length());
        }
        this.showInfoDialog(I18n.text("app.114"), I18n.text("app.132"));
    }

    @FXML
    private void menuOpenProgram() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.text("app.134"));
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(I18n.text("app.135"), new String[]{"*.gcode", "*.nc", "*.txt"}), new FileChooser.ExtensionFilter(I18n.text("app.136"), new String[]{"*.*"}));
        File file = fileChooser.showOpenDialog(this.anchorPaneCanvas.getScene().getWindow());
        if (file != null) {
            this.loadProgramAsync(file);
        }
    }

    @FXML
    private void menuClearProgram() {
        if (this.programEditor != null) {
            String string = this.appliedProgramText != null ? this.appliedProgramText : "";
            this.programEditor.clear();
            this.recordProgramHistoryIfChanged(string, "");
            this.appliedProgramText = "";
            this.saveLastProgramText("");
            this.cachedToolMovesSource = "";
            this.updateProgressDisplay(0, I18n.text("app.137"));
            this.resetCanvasView();
            this.redrawCanvas();
        }
    }

    @FXML
    private void menuConvertAviaProgram() {
        if (this.programEditor == null || this.programEditor.getText().isBlank()) {
            this.showInfoDialog(I18n.text("app.138"), I18n.text("app.139"));
            return;
        }
        String oldText = this.programEditor.getText();
        String newText = this.convertAviaProgram(oldText);
        this.programEditor.setText(newText);
        this.recordProgramHistoryIfChanged(oldText, newText);
        this.appliedProgramText = newText;
        this.saveLastProgramText(newText);
        this.cachedToolMovesSource = "";
        this.updateProgressDisplay(0, I18n.text("app.140"));
        this.redrawCanvas();
    }

    @FXML
    private void menuRenameFrameNumbers() {
        if (this.programEditor == null || this.programEditor.getText().isBlank()) {
            this.showInfoDialog(I18n.text("app.141"), I18n.text("app.139"));
            return;
        }
        TextInputDialog textInputDialog = new TextInputDialog("10");
        textInputDialog.setTitle(I18n.text("app.141"));
        textInputDialog.setHeaderText(I18n.text("app.142"));
        textInputDialog.setContentText("N");
        String string = textInputDialog.showAndWait().orElse(null);
        if (string == null) {
            return;
        }
        TextInputDialog textInputDialog2 = new TextInputDialog("10");
        textInputDialog2.setTitle(I18n.text("app.141"));
        textInputDialog2.setHeaderText(I18n.text("app.143"));
        textInputDialog2.setContentText(I18n.text("app.144"));
        String string2 = textInputDialog2.showAndWait().orElse(null);
        if (string2 == null) {
            return;
        }
        try {
            int n = Integer.parseInt(string.trim());
            int n2 = Integer.parseInt(string2.trim());
            String oldText = this.programEditor.getText();
            String newText = this.renameFrameNumbers(oldText, n, n2);
            this.programEditor.setText(newText);
            this.recordProgramHistoryIfChanged(oldText, newText);
            this.appliedProgramText = newText;
            this.saveLastProgramText(newText);
            this.cachedToolMovesSource = "";
            this.updateProgressDisplay(0, I18n.text("app.145"));
        }
        catch (NumberFormatException numberFormatException) {
            this.showErrorDialog(I18n.text("app.141"), I18n.text("app.146"));
        }
    }

    @FXML
    private void menuPrint() {
        this.printCanvas();
    }

    @FXML
    private void menuQuit() {
        this.cleanup();
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void menuAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.text("app.147"));
        alert.setHeaderText(null);
        alert.setGraphic(null);
        // Первая строка текста — имя программы, остальные — подробности.
        String[] lines = I18n.text("app.148").split("\n");
        ImageView logo = new ImageView(new Image(this.getClass().getResourceAsStream("/icon_512.png")));
        logo.setFitWidth(76.0);
        logo.setFitHeight(76.0);
        logo.setSmooth(true);
        Label name = new Label(lines[0]);
        name.getStyleClass().add("about-name");
        Label version = new Label(aiAppVersion());
        version.getStyleClass().add("about-version");
        HBox title = new HBox(10.0, name, version);
        title.setAlignment(Pos.BASELINE_LEFT);
        VBox text = new VBox(6.0, title);
        for (int i = 1; i < lines.length; i++) {
            Label line = new Label(lines[i]);
            line.setWrapText(true);
            line.getStyleClass().add(i == lines.length - 1 ? "about-description" : "about-line");
            text.getChildren().add(line);
        }
        HBox content = new HBox(20.0, logo, text);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("about-content");
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().getStyleClass().add("about-dialog");
        alert.getDialogPane().setMinWidth(440.0);
        this.applyDialogStyles(alert);
        this.setDialogIcon(alert);
        alert.showAndWait();
    }

    @FXML
    private void menuThemeLight() {
        if (this.suppressThemeMenuHandler) {
            return;
        }
        this.setDarkTheme(false);
    }

    @FXML
    private void menuThemeDark() {
        if (this.suppressThemeMenuHandler) {
            return;
        }
        this.setDarkTheme(true);
    }

    @FXML
    private void menuLanguageAuto() {
        this.chooseLanguage(I18n.AUTO);
    }

    @FXML
    private void menuLanguageRu() {
        this.chooseLanguage(I18n.RUSSIAN);
    }

    @FXML
    private void menuLanguageEn() {
        this.chooseLanguage(I18n.ENGLISH);
    }

    @FXML
    private void menuLanguageUk() {
        this.chooseLanguage(I18n.UKRAINIAN);
    }

    /**
     * Меню языка: пункт «как в системе» подписывается тем языком, который программа
     * определила по Windows, а галочка ставится на текущем выборе.
     */
    private void setupLanguageMenu() {
        if (this.menuLanguageAuto != null) {
            this.menuLanguageAuto.setText(I18n.text("language.auto")
                    + " (" + I18n.displayName(I18n.systemLanguage()) + ")");
        }
        this.updateLanguageMenuSelection();
    }

    private void updateLanguageMenuSelection() {
        String saved = I18n.preference();
        RadioMenuItem target;
        if (I18n.RUSSIAN.equals(saved)) {
            target = this.menuLanguageRu;
        } else if (I18n.ENGLISH.equals(saved)) {
            target = this.menuLanguageEn;
        } else if (I18n.UKRAINIAN.equals(saved)) {
            target = this.menuLanguageUk;
        } else {
            target = this.menuLanguageAuto;
        }
        this.suppressLanguageMenuHandler = true;
        try {
            if (this.languageToggleGroup != null && target != null) {
                this.languageToggleGroup.selectToggle(target);
            } else if (target != null) {
                target.setSelected(true);
            }
        } finally {
            this.suppressLanguageMenuHandler = false;
        }
    }

    /**
     * Запоминает выбранный язык. Надписи меняются со следующего запуска: часть их
     * создаётся один раз при построении окна, и подмена на ходу дала бы смесь языков.
     */
    private void chooseLanguage(String code) {
        if (this.suppressLanguageMenuHandler) {
            return;
        }
        String previous = I18n.preference();
        if (previous.equals(code)) {
            return;
        }
        I18n.setPreference(code);
        this.updateLanguageMenuSelection();
        String shown = code.isEmpty() ? I18n.systemLanguage() : code;
        this.showInfoDialog(I18n.textIn(shown, "language.restart.title"),
                String.format(I18n.textIn(shown, "language.restart.body"), I18n.displayName(shown)));
    }

    @FXML
    private void menuAxisStandard() {
        this.setAxisMode(AxisMode.STANDARD);
    }

    @FXML
    private void menuAxisXY() {
        this.setAxisMode(AxisMode.XY);
    }

    @FXML
    private void menuAxisXZ() {
        this.setAxisMode(AxisMode.XZ);
    }

    private void setAxisMode(AxisMode axisMode) {
        if (this.axisMode == axisMode) {
            return;
        }
        this.axisMode = axisMode;
        this.previewLayoutValid = false;
        this.cachedToolMovesSource = "";
        this.rebuildWorkOffsetRows();
        this.redrawCanvas();
    }

    private void applySavedTheme() {
        this.applyDarkTheme(UserSettings.isDarkTheme(), false);
    }

    private void updateThemeMenuSelection(boolean dark) {
        this.suppressThemeMenuHandler = true;
        try {
            RadioMenuItem target = dark ? this.menuThemeDark : this.menuThemeLight;
            if (this.themeToggleGroup != null && target != null) {
                this.themeToggleGroup.selectToggle(target);
            } else if (this.menuThemeDark != null && this.menuThemeLight != null) {
                this.menuThemeDark.setSelected(dark);
                this.menuThemeLight.setSelected(!dark);
            }
        } finally {
            this.suppressThemeMenuHandler = false;
        }
    }

    private void setDarkTheme(boolean bl) {
        this.applyDarkTheme(bl, true);
    }

    private void applyDarkTheme(boolean bl, boolean persist) {
        this.darkTheme = bl;
        if (persist) {
            UserSettings.setDarkTheme(bl);
        }
        this.updateThemeMenuSelection(bl);
        // Все открытые окна разом. Раньше класс ставился на корень сцены главного
        // окна (обёртку масштаба), а при запуске он висит на корне разметки внутри
        // неё: переключение туда-обратно оставляло половину интерфейса в старой
        // теме до перезапуска. Остальные окна (3D, МСП, параметры) не менялись вовсе.
        for (javafx.stage.Window window : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
            if (window instanceof javafx.stage.PopupWindow || window.getScene() == null
                    || window.getScene().getRoot() == null) {
                continue;
            }
            this.applyThemeToScene(window.getScene(), bl);
        }
        this.redrawCanvas();
        if (this.programHistoryListView != null) {
            this.programHistoryListView.refresh();
        }
        this.refreshHistoryClearButtonTheme();
        this.applyStatusPanelMainButtonFonts();
        this.applyStatusCenterButtonStyle();
        this.applyProgramToolbarStyles();
        this.applyProgramSectionHeadingFonts();
        this.applySimActionButtonStyles();
        this.refreshSettingsComboTheme();
        if (this.programHistoryStage != null && this.programHistoryStage.isShowing()) {
            Platform.runLater(() -> this.updateHistoryScrollbarVisibility(this.historyHasScrollableEntries));
        }
    }

    /**
     * Ячейки выпадающих списков настроек красятся инлайном по текущей теме —
     * при переключении темы пересоздаём их, иначе остаются цвета старой темы
     * (белые пункты на белом / тёмные на тёмном).
     */
    private void refreshSettingsComboTheme() {
        for (ComboBox<String> comboBox : java.util.Arrays.asList(
                this.controlSystemComboBox, this.machineTypeComboBox)) {
            if (comboBox == null) {
                continue;
            }
            // Пересоздаём buttonCell и фабрику ячеек: новые ячейки сразу читают
            // актуальную тему; значение не трогаем, чтобы не дёргать слушатели.
            this.markSettingsCombo(comboBox);
        }
    }

    private Parent getMainWindowRoot() {
        if (this.anchorPaneCanvas != null && this.anchorPaneCanvas.getScene() != null) {
            return this.anchorPaneCanvas.getScene().getRoot();
        }
        Scene scene = this.getMainScene();
        return scene != null ? scene.getRoot() : null;
    }

    /**
     * Тема для одного окна: класс dark-theme ровно там, где его ставит запуск.
     *
     * <p>Сначала класс снимается со всех узлов сцены, потом ставится на корень
     * содержимого — у главного окна это разметка внутри обёртки масштаба, у
     * остальных корень сцены. Так не остаётся «застрявших» тёмных веток.
     */
    private void applyThemeToScene(Scene scene, boolean dark) {
        Parent root = scene.getRoot();
        this.clearDarkThemeDeep(root);
        Parent target = root;
        if (root.getStyleClass().contains("ui-scale-root") && !root.getChildrenUnmodifiable().isEmpty()
                && root.getChildrenUnmodifiable().get(0) instanceof javafx.scene.Group group
                && !group.getChildren().isEmpty() && group.getChildren().get(0) instanceof Parent content) {
            target = content;
            scene.setFill(dark ? Color.web("#0b1220") : Color.web("#f8fafc"));
        }
        this.applyDarkThemeStyleClass(target, dark);
    }

    private void clearDarkThemeDeep(Node node) {
        node.getStyleClass().removeAll("dark-theme");
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                this.clearDarkThemeDeep(child);
            }
        }
    }

    private void applyDarkThemeStyleClass(Node node, boolean dark) {
        if (node == null) {
            return;
        }
        if (dark) {
            if (!node.getStyleClass().contains("dark-theme")) {
                node.getStyleClass().add("dark-theme");
            }
            return;
        }
        node.getStyleClass().removeAll("dark-theme");
    }

    private void handleStart() {
        this.startFullExecution();
    }

    private void handleReset() {
        this.executionRunning = false;
        this.cyclePaused = true;
        this.fullPreviewVisible = false;
        this.toolpathGraphVisible = false;
        this.currentExecutionIndex = -1;
        this.selectedStartIndex = -1;
        this.selectedMarkerLine = -1;
        this.previewFromProgramHead = false;
        this.pathDisplayActive = false;
        this.trajectoryPreviewComplete = false;
        this.launchFocusPending = false;
        this.programEditorEditingActive = false;
        this.stopSpaceSingleBlockRepeat();
        this.executionMoves = List.of();
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.clearGraphEditorSelection();
        this.clearProgramHighlight();
        this.syncProgramEditorVisuals();
        this.updateProgressDisplay(0, I18n.text("app.149"));
        this.updateButtonStates(false);
        this.redrawCanvas();
    }

    private void handleCycleStart() {
        if (!this.executionRunning) {
            this.toggleCycleExecution();
        }
    }

    private void handleCyclePause() {
        if (this.executionRunning) {
            this.cyclePaused = !this.cyclePaused;
            this.updateButtonStates(true);
            this.updateProgressDisplay(this.progressPercent(this.parseGCodeMoves(this.programEditor != null ? this.programEditor.getText() : "")), this.cyclePaused ? I18n.text("app.150") : I18n.text("app.151"));
            this.redrawCanvas();
        }
    }

    private void handleSingleBlock() {
        this.programEditorEditingActive = false;
        this.executeSingleBlock();
    }

    private void updateButtonStates(boolean bl) {
        Platform.runLater(() -> {
            this.buttonStart.setDisable(bl);
            this.buttonReset.setDisable(false);
            this.buttonCycleStart.setDisable(this.executionRunning);
            if (this.buttonCyclePause != null) {
                this.buttonCyclePause.setDisable(!this.executionRunning);
                this.buttonCyclePause.setText(this.cyclePaused ? "\u25b6" : "\u23f8");
            }
            this.buttonSingleBlock.setDisable(this.executionRunning && !this.cyclePaused);
        });
    }

    private void startFullExecution() {
        List<ToolMove> list = this.refreshExecutionMoves();
        if (list.isEmpty()) {
            this.updateProgressDisplay(0, I18n.text("app.002"));
            return;
        }
        this.executionRunning = false;
        this.cyclePaused = true;
        this.fullPreviewVisible = false;
        int markerIndex = this.resolveMarkerMoveIndex(list);
        this.previewFromProgramHead = markerIndex < 0;
        int startLine = markerIndex >= 0 ? list.get(markerIndex).sourceLine : 1;
        int endIndex = this.mandatoryStopEndIndex(list, startLine);
        this.beginPathDisplay(list, endIndex);
        if (markerIndex >= 0) {
            this.pathDisplayStartIndex = markerIndex;
            this.currentExecutionIndex = endIndex;
        }
        this.trajectoryPreviewComplete = true;
        // СТАРТ должен ещё и корректно центрировать вид (как кнопка «Центрировать»):
        // показываем всю траекторию по центру, а не «прилипаем» к точке инструмента.
        this.centerViewOnOrigin();
        this.syncProgramEditorVisuals();
        String string = this.programStartMarkerLine > 0 ? String.format(Locale.US, I18n.text("app.152"), this.programStartMarkerLine) : I18n.text("app.153");
        this.updateProgressDisplay(100, string);
        this.redrawCanvas();
        this.notify3dMachiningProgress();
    }

    private int mandatoryStopEndIndex(List<ToolMove> moves, int startSourceLine) {
        if (moves == null || moves.isEmpty()) {
            return -1;
        }
        int stopLine = this.firstMandatoryStopLineAfter(this.resolveProgramTextForGraph(), startSourceLine);
        if (stopLine <= 0) {
            return moves.size() - 1;
        }
        int endIndex = -1;
        for (int i = 0; i < moves.size(); i++) {
            if (moves.get(i).sourceLine < stopLine) {
                endIndex = i;
            } else {
                break;
            }
        }
        return endIndex >= 0 ? endIndex : moves.size() - 1;
    }

    private int firstMandatoryStopLineAfter(String programText, int startSourceLine) {
        if (programText == null || programText.isBlank()) {
            return -1;
        }
        int lineNumber = 1;
        int scanStart = Math.max(1, startSourceLine);
        for (String rawLine : programText.split("\\R")) {
            if (lineNumber >= scanStart && this.containsMandatoryStop(rawLine)) {
                return lineNumber;
            }
            lineNumber++;
        }
        return -1;
    }

    private boolean containsMandatoryStop(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return false;
        }
        String line = GCodeProgramParser.stripFrameNumberForParse(
                GCodeProgramParser.stripCommentsForParse(rawLine)).toUpperCase(Locale.US);
        return !line.isBlank() && MANDATORY_STOP_PATTERN.matcher(line).find();
    }

    // Кэш для уведомлений 3D во время цикла: контекст строится один раз на программу,
    // иначе каждый тик анимации (до 10/с на скорости X10) полностью перепарсивал
    // программу в FX-потоке и 2D-график начинал лагать.
    private SimulationContext machining3dNotifyContext;
    private String machining3dNotifyProgram;
    private long machining3dNotifyLastNanos;
    private int machining3dNotifyLastIndex = Integer.MIN_VALUE;

    private void notify3dMachiningProgress() {
        this.notify3dMachiningProgress(false);
    }

    private void notify3dMachiningProgress(boolean force) {
        try {
            String program = this.resolveProgramTextForGraph();
            SimulationContext context = this.machining3dNotifyContext;
            // Во время цикла программа не меняется — используем кэш;
            // вне цикла собираем контекст заново (настройки могли измениться).
            if (context == null || !this.executionRunning || !program.equals(this.machining3dNotifyProgram)) {
                context = this.buildSimulationContext(List.of());
                this.machining3dNotifyContext = context;
                this.machining3dNotifyProgram = program;
            }
            int index = this.simulationContourIndexForContext(context);
            long now = System.nanoTime();
            if (!force && this.executionRunning) {
                if (index == this.machining3dNotifyLastIndex) {
                    return;
                }
                // Открытое 3D-окно перестраивает срез на каждое уведомление —
                // во время цикла шлём не чаще ~4 раз в секунду.
                if (now - this.machining3dNotifyLastNanos < 250_000_000L) {
                    return;
                }
            }
            this.machining3dNotifyLastNanos = now;
            this.machining3dNotifyLastIndex = index;
            this.simulation3dWindow.updateMachiningProgress(context, this.darkTheme, index);
        }
        catch (RuntimeException ignored) {
            // 3D окно закрыто
        }
    }

    private int mapExecutionIndexToContourIndex(SimulationContext context) {
        if (context == null || context.getContourMoves().isEmpty()) {
            return -1;
        }
        List<ToolMove> toolMoves = this.movesForDrawing();
        int line = this.sourceLineForSimulationProgress(toolMoves);
        int byLine = this.mapSourceLineToContourIndex(context.getContourMoves(), line);
        if (byLine >= 0) {
            return byLine;
        }
        if (this.currentExecutionIndex < 0) {
            return -1;
        }
        return Math.min(this.currentExecutionIndex, context.getContourMoves().size() - 1);
    }

    private int simulationContourIndexForContext(SimulationContext context) {
        int index = this.mapExecutionIndexToContourIndex(context);
        if (index >= 0) {
            return index;
        }
        int stopLine = this.firstMandatoryStopLineAfter(this.resolveProgramTextForGraph(), 1);
        return stopLine > 0 ? this.mapSourceLineToContourIndex(context.getContourMoves(), stopLine) : -1;
    }

    private int sourceLineForSimulationProgress(List<ToolMove> toolMoves) {
        if (toolMoves != null && !toolMoves.isEmpty()) {
            if (this.currentExecutionIndex >= 0 && this.currentExecutionIndex < toolMoves.size()) {
                return toolMoves.get(this.currentExecutionIndex).sourceLine;
            }
            if (this.graphEditorSelectionIndex >= 0 && this.graphEditorSelectionIndex < toolMoves.size()) {
                return toolMoves.get(this.graphEditorSelectionIndex).sourceLine;
            }
        }
        if (this.highlightedProgramLine > 0) {
            return this.highlightedProgramLine;
        }
        if (this.selectedMarkerLine > 0) {
            return this.selectedMarkerLine;
        }
        return -1;
    }

    private int mapSourceLineToContourIndex(List<GCodeMoveData> contour, int sourceLine) {
        if (sourceLine <= 0 || contour == null || contour.isEmpty()) {
            return -1;
        }
        int previous = -1;
        for (int i = 0; i < contour.size(); i++) {
            int line = contour.get(i).sourceLine();
            if (line == sourceLine) {
                previous = i;
            } else if (line < sourceLine) {
                previous = i;
            } else if (line > sourceLine) {
                break;
            }
        }
        return previous;
    }

    private void toggleCycleExecution() {
        List<ToolMove> list = this.refreshExecutionMoves();
        if (list.isEmpty()) {
            this.updateProgressDisplay(0, I18n.text("app.002"));
            return;
        }
        if (!this.hasFrameMoves(list)) {
            this.updateProgressDisplay(0, I18n.text("app.003"));
            return;
        }
        if (this.executionRunning) {
            this.cyclePaused = !this.cyclePaused;
            this.updateButtonStates(true);
            this.updateProgressDisplay(this.progressPercent(list), this.cyclePaused ? I18n.text("app.154") : I18n.text("app.151"));
            this.redrawCanvas();
            return;
        }
        if (this.trajectoryPreviewComplete) {
            this.trajectoryPreviewComplete = false;
        }
        boolean blRestartFromProgramHead = this.previewFromProgramHead && this.programStartMarkerLine <= 0;
        this.fullPreviewVisible = false;
        this.previewFromProgramHead = false;
        int nStart = this.resolveCycleStartMoveIndex(list, blRestartFromProgramHead);
        if (nStart < 0) {
            this.updateProgressDisplay(0, I18n.text("app.155"));
            return;
        }
        this.executionRunning = true;
        this.cyclePaused = false;
        this.lastCycleSyncedEditorLine = -1;
        this.cycleTickUiInFlight.set(false);
        this.beginPathDisplay(list, nStart);
        this.markLaunchFocusPending();
        this.updateButtonStates(true);
        this.updateProgressDisplay(this.progressPercent(list), this.describeCycleStart(list, nStart));
        Platform.runLater(() -> this.syncProgramSelectionToCurrentFrame(list));
        this.drawCanvasNow();
        this.notify3dMachiningProgress();
        final List<ToolMove> cycleMoves = this.executionMoves;
        final int nPathStart = this.pathDisplayStartIndex;
        final int cycleEndIndex = this.mandatoryStopEndIndex(
                cycleMoves,
                nStart >= 0 && nStart < cycleMoves.size() ? cycleMoves.get(nStart).sourceLine : 1);
        CompletableFuture.runAsync(() -> {
            while (this.currentExecutionIndex < cycleMoves.size()
                    && this.currentExecutionIndex <= cycleEndIndex
                    && this.executionRunning) {
                if (this.cyclePaused) {
                    this.sleepQuietly(50L);
                    continue;
                }
                int i = this.currentExecutionIndex;
                if (i < nPathStart) {
                    this.currentExecutionIndex = nPathStart;
                    continue;
                }
                if (i < 0 || i >= cycleMoves.size()) {
                    break;
                }
                ToolMove toolMove = cycleMoves.get(i);
                this.selectedMarkerLine = this.markerLineForMove(toolMove);
                // Защита от «снежного кома»: новый UI-кадр ставится в очередь только
                // когда предыдущий дорисован. На большой скорости лишние кадры
                // пропускаются, а не копятся в очереди FX-потока (лаги исчезают,
                // скорость анимации не меняется).
                if (this.cycleTickUiInFlight.compareAndSet(false, true)) {
                    final int tickIndex = i;
                    Platform.runLater(() -> {
                        try {
                            this.syncProgramSelectionToCurrentFrame(cycleMoves);
                            this.drawCanvasNow();
                            this.notify3dMachiningProgress();
                            if (this.progressExecution != null) {
                                int percent = this.progressPercentForMove(cycleMoves, tickIndex);
                                this.progressExecution.setProgress(Math.max(0.0, Math.min(1.0, percent / 100.0)));
                                if (this.labelExecutionProgress != null) {
                                    this.labelExecutionProgress.setText(percent + "%");
                                }
                                if (this.labelExecutionStatus != null && tickIndex < cycleMoves.size()) {
                                    this.labelExecutionStatus.setText(
                                            this.formatExecutionStatus(cycleMoves, cycleMoves.get(tickIndex), tickIndex));
                                }
                            }
                        } finally {
                            this.cycleTickUiInFlight.set(false);
                        }
                    });
                }
                this.sleepQuietly(this.getDrawDelayMillis());
                this.currentExecutionIndex = this.nextMoveIndex(cycleMoves, this.currentExecutionIndex, 1);
            }
            this.executionRunning = false;
            if (cycleEndIndex >= 0 && this.currentExecutionIndex > cycleEndIndex) {
                this.currentExecutionIndex = cycleEndIndex;
            }
            if (this.currentExecutionIndex >= cycleMoves.size()) {
                this.currentExecutionIndex = cycleMoves.size() - 1;
            }
            if (this.currentExecutionIndex >= cycleMoves.size() - 1 || this.currentExecutionIndex == cycleEndIndex) {
                this.updateProgressDisplay(100, I18n.text("app.004"));
            }
            this.updateButtonStates(false);
            Platform.runLater(() -> {
                this.drawCanvasNow();
                // \u0424\u0438\u043d\u0430\u043b\u044c\u043d\u043e\u0435 \u0441\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435 \u0432 3D \u2014 \u043c\u0438\u043c\u043e \u0442\u0440\u043e\u0442\u0442\u043b\u0438\u043d\u0433\u0430 \u0446\u0438\u043a\u043b\u0430.
                this.notify3dMachiningProgress(true);
            });
        }, EXECUTOR);
    }

    private List<ToolMove> refreshExecutionMoves() {
        this.syncAppliedProgramFromEditor();
        this.executionMoves = List.copyOf(this.getCachedToolMoves());
        return this.executionMoves;
    }

    private List<ToolMove> movesForDrawing() {
        if (this.fullPreviewVisible || this.trajectoryPreviewComplete) {
            return this.getCachedToolMoves();
        }
        return this.executionMoves.isEmpty() ? this.refreshExecutionMoves() : this.executionMoves;
    }

    private void beginPathDisplay(List<ToolMove> list, int n) {
        if (list != null && !list.isEmpty()) {
            this.executionMoves = List.copyOf(list);
        }
        this.toolpathGraphVisible = true;
        this.pathDisplayStartIndex = this.resolvePathStartIndex(list);
        this.selectedStartIndex = this.pathDisplayStartIndex;
        this.pathDisplayActive = true;
        this.currentExecutionIndex = Math.max(this.pathDisplayStartIndex, Math.min(n, list.size() - 1));
    }

    private void markLaunchFocusPending() {
        this.launchFocusPending = true;
    }

    private void centerViewOnOrigin() {
        this.launchFocusPending = false;
        this.currentZoom = 1.0;
        this.translateX = 0.0;
        this.translateY = 0.0;
        this.updateZoomDisplay();
        this.previewLayoutValid = false;
    }

    private int resolvePathStartIndex(List<ToolMove> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int n = this.resolveMarkerMoveIndex(list);
        if (n >= 0) {
            return n;
        }
        if (this.selectedStartIndex >= 0 && this.selectedStartIndex < list.size()) {
            return this.selectedStartIndex;
        }
        return this.firstFrameIndex(list, 0);
    }

    private boolean shouldShowToolpath() {
        if (this.getActiveToolMoves().isEmpty()) {
            return false;
        }
        return this.fullPreviewVisible || this.toolpathGraphVisible;
    }

    private List<ToolMove> buildDisplayMoves(List<ToolMove> list) {
        if (!this.shouldShowToolpath() || list == null || list.isEmpty()) {
            this.visibleMovesFromIndex = 0;
            return List.of();
        }
        if (this.fullPreviewVisible || this.trajectoryPreviewComplete) {
            int from = Math.max(0, Math.min(this.pathDisplayStartIndex, list.size() - 1));
            this.visibleMovesFromIndex = from;
            return new ArrayList<>(list.subList(from, list.size()));
        }
        if (!this.pathDisplayActive || this.currentExecutionIndex < 0) {
            this.visibleMovesFromIndex = 0;
            return List.of();
        }
        int from = Math.min(this.pathDisplayStartIndex, this.currentExecutionIndex);
        int to = Math.max(this.pathDisplayStartIndex, this.currentExecutionIndex);
        from = Math.max(0, Math.min(from, list.size() - 1));
        to = Math.max(from, Math.min(to, list.size() - 1));
        this.visibleMovesFromIndex = from;
        return new ArrayList<>(list.subList(from, to + 1));
    }

    private int resolveMarkerMoveIndex(List<ToolMove> list) {
        if (list == null || list.isEmpty() || this.programStartMarkerLine <= 0) {
            return -1;
        }
        int n = this.findMoveIndexForMarkerLine(list, this.programStartMarkerLine);
        if (n >= 0) {
            return n;
        }
        int n2 = this.resolveProgramMarkerLine(this.programStartMarkerLine, list);
        if (n2 > 0) {
            int n3 = this.findMoveIndexForMarkerLine(list, n2);
            if (n3 >= 0) {
                return n3;
            }
        }
        if (this.pathDisplayStartIndex >= 0 && this.pathDisplayStartIndex < list.size()) {
            return this.pathDisplayStartIndex;
        }
        if (this.selectedStartIndex >= 0 && this.selectedStartIndex < list.size()) {
            return this.selectedStartIndex;
        }
        return -1;
    }

    private int resolveCycleStartMoveIndex(List<ToolMove> list, boolean blFromProgramHead) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        if (blFromProgramHead) {
            return this.firstFrameIndex(list, 0);
        }
        int n = this.resolveMarkerMoveIndex(list);
        if (n >= 0) {
            return n;
        }
        if (this.programStartMarkerLine > 0) {
            return -1;
        }
        if (this.currentExecutionIndex >= 0 && this.currentExecutionIndex < list.size()) {
            return this.currentExecutionIndex;
        }
        if (this.selectedStartIndex >= 0 && this.selectedStartIndex < list.size()) {
            return this.selectedStartIndex;
        }
        return this.firstFrameIndex(list, 0);
    }

    private String describeCycleStart(List<ToolMove> list, int n) {
        if (list == null || list.isEmpty() || n < 0 || n >= list.size()) {
            return I18n.text("app.156");
        }
        ToolMove toolMove = list.get(n);
        if (this.programStartMarkerLine > 0) {
            int n2 = this.findMoveIndexForMarkerLine(list, this.programStartMarkerLine);
            if (n2 == n) {
                return String.format(Locale.US, I18n.text("app.157"), this.programStartMarkerLine, this.formatNBlockLabel(this.getProgramLineText(toolMove.sourceLine)));
            }
        }
        return String.format(Locale.US, I18n.text("app.158"), this.formatExecutionStatus(list, toolMove, n));
    }

    private void executeSingleBlock() {
        List<ToolMove> list = this.refreshExecutionMoves();
        if (list.isEmpty()) {
            this.updateProgressDisplay(0, I18n.text("app.002"));
            return;
        }
        if (!this.hasFrameMoves(list)) {
            this.updateProgressDisplay(0, I18n.text("app.003"));
            return;
        }
        this.fullPreviewVisible = false;
        this.previewFromProgramHead = false;
        if (this.currentExecutionIndex < 0) {
            this.currentExecutionIndex = this.resolvePathStartIndex(list);
        } else {
            this.currentExecutionIndex = this.nextFrameIndex(list, this.currentExecutionIndex, 1);
        }
        if (this.currentExecutionIndex >= list.size()) {
            this.currentExecutionIndex = -1;
            this.pathDisplayActive = false;
            this.executionRunning = false;
            this.cyclePaused = true;
            this.updateProgressDisplay(100, I18n.text("app.004"));
            this.updateButtonStates(false);
            this.redrawCanvas();
            return;
        }
        ToolMove toolMove = list.get(this.currentExecutionIndex);
        this.selectedMarkerLine = this.markerLineForMove(toolMove);
        this.executionRunning = false;
        this.cyclePaused = true;
        boolean blWasInactive = !this.pathDisplayActive;
        this.trajectoryPreviewComplete = false;
        this.beginPathDisplay(list, this.currentExecutionIndex);
        if (blWasInactive) {
            this.markLaunchFocusPending();
        }
        this.updateProgressDisplay(this.progressPercentForMove(list, this.currentExecutionIndex), this.formatExecutionStatus(list, toolMove, this.currentExecutionIndex));
        this.syncProgramSelectionToCurrentFrame(list);
        this.updateButtonStates(false);
        this.redrawCanvas();
        this.notify3dMachiningProgress();
    }

    private void stepFrame(int n) {
        List<ToolMove> list = this.refreshExecutionMoves();
        if (list.isEmpty()) {
            this.updateProgressDisplay(0, I18n.text("app.002"));
            return;
        }
        this.fullPreviewVisible = false;
        this.previewFromProgramHead = false;
        if (this.currentExecutionIndex < 0) {
            this.currentExecutionIndex = this.resolvePathStartIndex(list);
        } else {
            this.currentExecutionIndex = this.nextFrameIndex(list, this.currentExecutionIndex, n);
        }
        if (this.currentExecutionIndex < 0) {
            this.currentExecutionIndex = this.firstFrameIndex(list, 0);
        }
        if (this.currentExecutionIndex >= list.size()) {
            this.currentExecutionIndex = this.lastFrameIndex(list);
        }
        ToolMove toolMove = list.get(this.currentExecutionIndex);
        this.selectedMarkerLine = this.markerLineForMove(toolMove);
        this.beginPathDisplay(list, this.currentExecutionIndex);
        this.updateProgressDisplay(this.progressPercentForMove(list, this.currentExecutionIndex), this.formatExecutionStatus(list, toolMove, this.currentExecutionIndex));
        this.syncProgramSelectionToCurrentFrame(list);
        this.redrawCanvas();
        this.notify3dMachiningProgress();
    }

    private int nextMoveIndex(List<ToolMove> list, int n, int n2) {
        if (list == null || list.isEmpty()) {
            return -1;
        }
        if (n < 0) {
            return n2 < 0 ? -1 : 0;
        }
        int n3 = n + n2;
        if (n3 < 0) {
            return -1;
        }
        if (n3 >= list.size()) {
            return list.size();
        }
        return n3;
    }

    private int nextFrameIndex(List<ToolMove> list, int n, int n2) {
        if (list == null || list.isEmpty()) {
            return -1;
        }
        if (n < 0) {
            return n2 < 0 ? this.lastFrameIndex(list) : this.firstFrameIndex(list, 0);
        }
        int n3 = Math.max(0, Math.min(list.size() - 1, n));
        int n4 = list.get(n3).sourceLine;
        int n5 = n3;
        do {
            n5 += n2 < 0 ? -1 : 1;
            if (n5 < 0 || n5 >= list.size()) {
                return n5;
            }
        } while (list.get(n5).sourceLine == n4 || !this.isFrameMove(list.get(n5)) || this.markerLineForMove(list.get(n5)) <= 0);
        return n5;
    }

    private boolean hasFrameMoves(List<ToolMove> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        // Если есть хоть какие-то ToolMove — даём стартовать цикл,
        // даже без N-кадров (работает по sourceLine)
        return true;
    }

    private int firstFrameIndex(List<ToolMove> list, int n) {
        if (list == null || list.isEmpty()) {
            return -1;
        }
        for (int i = Math.max(0, n); i < list.size(); ++i) {
            if (this.isFrameMove(list.get(i))) {
                return i;
            }
        }
        // Если нет N-кадров — стартуем с первого хода
        return 0;
    }

    private int lastFrameIndex(List<ToolMove> list) {
        if (list == null || list.isEmpty()) {
            return -1;
        }
        for (int i = list.size() - 1; i >= 0; --i) {
            if (this.isFrameMove(list.get(i))) {
                return i;
            }
        }
        return list.size() - 1;
    }

    private boolean isFrameMove(ToolMove toolMove) {
        return toolMove != null && this.isProgramFrameLine(this.getProgramLineText(toolMove.sourceLine));
    }

    private String getProgramLineText(int n) {
        if (this.programEditor == null || n <= 0) {
            return "";
        }
        return this.programEditor.getLineText(n);
    }

    private boolean isProgramFrameLine(String string) {
        if (string == null) {
            return false;
        }
        String string2 = this.normalizeProgramLineForParse(string);
        return Pattern.compile("^N\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(string2).find();
    }

    private String normalizeProgramLineForParse(String string) {
        String text = this.stripGCodeComment(string).trim();
        while (text.startsWith(";")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    private void syncProgramSelectionToCurrentFrame(List<ToolMove> list) {
        if (this.programEditor == null || list == null || this.currentExecutionIndex < 0 || this.currentExecutionIndex >= list.size()) {
            return;
        }
        ToolMove toolMove = list.get(this.currentExecutionIndex);
        this.selectedMarkerLine = this.markerLineForMove(toolMove);
        this.highlightedProgramLine = toolMove.sourceLine;
        this.highlightedProgramMove = toolMove;
        // Во время цикла дуги дробятся на десятки ходов одной строки — повторная
        // подсветка той же строки редактора стоит дорого и не нужна.
        if (this.executionRunning && toolMove.sourceLine == this.lastCycleSyncedEditorLine) {
            return;
        }
        this.lastCycleSyncedEditorLine = toolMove.sourceLine;
        this.syncEditorHighlightFromGraph(toolMove.sourceLine);
        this.selectProgramLineExact(toolMove.sourceLine);
    }

    private void selectProgramLine(int n) {
        this.selectProgramLine(n, true);
    }

    private void selectProgramLineExact(int n) {
        this.selectProgramLine(n, false);
    }

    private void selectProgramLine(int n, boolean resolveMarkerLine) {
        if (this.programEditor == null || n <= 0) {
            return;
        }
        if (resolveMarkerLine) {
            n = this.resolveProgramMarkerLine(n);
            if (n <= 0) {
                return;
            }
        }
        String string = this.programEditor.getText();
        int n2 = 1;
        int n3 = 0;
        for (int i = 0; i < string.length() && n2 < n; ++i) {
            if (string.charAt(i) == '\n') {
                ++n2;
                n3 = i + 1;
            }
        }
        int n4 = n3;
        while (n4 < string.length() && string.charAt(n4) != '\n' && string.charAt(n4) != '\r') {
            ++n4;
        }
        if (resolveMarkerLine) {
            this.scrollProgramToLine(n);
        } else {
            this.scrollProgramToLineExact(n);
        }
    }

    private void scrollProgramToLineExact(int n) {
        if (this.programEditor != null && n > 0) {
            this.programEditor.showLine(n);
        }
    }

    private void scrollProgramToLine(int n) {
        if (this.programEditor == null || n <= 0) {
            return;
        }
        n = this.resolveProgramMarkerLine(n);
        this.scrollProgramToLineExact(n);
    }

    private void applyProgramFontSize(int n) {
        if (this.programEditor != null) {
            this.programEditor.applyFontSize(n);
            this.syncProgramEditorVisuals();
        }
    }

    private void installProgramFontSizeCloseHandler() {
        Platform.runLater(() -> {
            Scene scene = this.getMainScene();
            if (scene == null) {
                return;
            }
            Runnable persistOnClose = this::persistUserSettings;
            scene.windowProperty().addListener((observable, oldWindow, newWindow) -> {
                if (newWindow instanceof Stage stage) {
                    stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> persistOnClose.run());
                }
            });
            if (scene.getWindow() instanceof Stage stage) {
                stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> persistOnClose.run());
            }
        });
    }

    private void persistUserSettings() {
        UserSettings.setDarkTheme(this.darkTheme);
        if (this.programEditor != null) {
            UserSettings.setProgramFontSize(this.programEditor.getFontSizePx());
            this.saveLastProgramText(this.programEditor.getText());
        } else if (this.programFontSizeComboBox != null && this.programFontSizeComboBox.getValue() != null) {
            UserSettings.setProgramFontSize(this.programFontSizeComboBox.getValue());
        }
    }

    private void restoreLastProgramText() {
        if (this.programEditor == null) {
            return;
        }
        String text = UserSettings.getLastProgramText();
        if (text == null || text.isBlank()) {
            return;
        }
        this.applyingProgramText = true;
        this.programEditor.setText(text);
        this.applyingProgramText = false;
        this.appliedProgramText = text;
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.programEditorEditingActive = false;
        this.syncTurningSettingsFromGCode(text);
        this.syncAxisModeForProgram(text);
        this.syncWorkOffsetsFromProgram(text);
        this.syncProgramEditorVisuals();
    }

    private void saveLastProgramText(String text) {
        // Каждое окно кода хранит свою программу: правое — канал 1, левое — канал 2.
        // Иначе переключение канала записывало бы чужой текст в файл соседнего.
        if (this.rightPaneActive) {
            UserSettings.setRightProgramText(text);
            return;
        }
        UserSettings.setLastProgramText(text);
    }

    private void syncProgramEditorVisuals() {
        if (this.programEditor == null) {
            return;
        }
        this.programEditor.setStartMarkerLine(this.programStartMarkerLine);
        this.syncProgramHighlight();
    }

    private void syncProgramHighlight() {
        if (this.programEditor == null) {
            return;
        }
        if (this.highlightedProgramLine <= 0 && (this.highlightedProgramMove == null || this.highlightedProgramMove.sourceLine <= 0)) {
            this.programEditor.clearHighlight();
            return;
        }
        int n = this.highlightedProgramLine > 0
                ? this.highlightedProgramLine
                : (this.highlightedProgramMove != null && this.highlightedProgramMove.sourceLine > 0
                        ? this.highlightedProgramMove.sourceLine
                        : -1);
        if (n <= 0) {
            this.programEditor.clearHighlight();
            return;
        }
        this.programEditor.setHighlightLine(n, this.highlightStyleForMove(this.highlightedProgramMove));
    }

    private String highlightStyleForMove(ToolMove toolMove) {
        if (this.graphEditorSelectionIndex >= 0
                && this.highlightedProgramMove != null
                && toolMove == this.highlightedProgramMove) {
            return "program-line-graph-select";
        }
        if (this.executionRunning) {
            return "program-line-exec";
        }
        if (this.pathDisplayActive && !this.trajectoryPreviewComplete && this.currentExecutionIndex >= 0) {
            return "program-line-exec";
        }
        if (toolMove != null && toolMove.arcSegment) {
            return "program-line-graph-arc";
        }
        if (toolMove != null && toolMove.rapid) {
            return "program-line-graph-rapid";
        }
        return "program-line-graph";
    }

    private void syncEditorHighlightFromGraph(int sourceLine) {
        if (this.programEditor == null || sourceLine <= 0) {
            return;
        }
        this.programEditor.setHighlightLine(sourceLine, "program-line-graph-select");
        this.programEditor.showLine(sourceLine);
    }

    private int markerLineForMove(ToolMove toolMove) {
        if (toolMove == null || toolMove.sourceLine <= 0) {
            return -1;
        }
        return toolMove.sourceLine;
    }

    private int markerLineForMoveIndex(List<ToolMove> list, int n) {
        if (list == null || n < 0 || n >= list.size()) {
            return -1;
        }
        return this.markerLineForMove(list.get(n));
    }

    private void handleGraphPointClick(double d, double d2) {
        if (this.programEditor == null || this.drawingCanvas == null) {
            return;
        }
        Point2D point2D = this.canvasGroup.sceneToLocal(this.paneCanvas.localToScene(d, d2));
        this.hoverMouseX = point2D.getX();
        this.hoverMouseY = point2D.getY();
        List<ToolMove> list = this.getActiveToolMoves();
        if (list.isEmpty()) {
            return;
        }
        if (!this.previewLayoutValid) {
            double d3 = Math.max(1.0, this.drawingCanvas.getWidth());
            double d4 = Math.max(1.0, this.drawingCanvas.getHeight());
            if (!this.computeProgramBoundsLayout(d3, d4)) {
                return;
            }
        }
        int pickLimit = this.graphSelectableMoveLimit(list);
        HoverPoint hoverPoint = this.findHoverPoint(
                list,
                this.previewLayoutOriginX,
                this.previewLayoutOriginY,
                this.previewLayoutScale,
                this.previewLayoutMinZ,
                this.previewLayoutMaxX,
                pickLimit);
        if (hoverPoint == null || hoverPoint.sourceLine <= 0) {
            return;
        }
        int n = hoverPoint.sourceLine;
        int n2 = hoverPoint.moveIndex >= 0
                ? hoverPoint.moveIndex
                : this.findLastMoveIndexForSourceLine(list, n);
        boolean allowGreenSelection = this.canSelectGraphPoint();
        ToolMove toolMove = n2 >= 0 && n2 < list.size() ? list.get(n2) : null;
        if (allowGreenSelection && n2 >= 0 && n2 == this.graphEditorSelectionIndex) {
            this.clearGraphEditorSelection();
            this.clearProgramHighlight();
            this.redrawCanvas();
            return;
        }
        if (toolMove != null && n2 <= pickLimit) {
            this.highlightedProgramLine = n;
            this.highlightedProgramMove = toolMove;
            if (allowGreenSelection) {
                this.graphEditorSelectionIndex = n2;
                this.panGraphToToolMove(toolMove);
            } else {
                this.clearGraphEditorSelection();
            }
            this.syncProgramHighlight();
        } else {
            this.highlightedProgramLine = n;
            this.highlightedProgramMove = null;
            this.clearGraphEditorSelection();
            this.syncProgramHighlight();
        }
        this.selectProgramLineExact(n);
        this.syncEditorHighlightFromGraph(n);
        this.redrawCanvas();
    }

    private void highlightProgramLineFromGraph(int n) {
        if (this.programEditor == null || n <= 0) {
            this.clearProgramHighlight();
            return;
        }
        List<ToolMove> list = this.getActiveToolMoves();
        int n2 = this.findMoveIndexForExactLine(list, n);
        if (n2 >= 0 && n2 < list.size() && n2 <= this.graphSelectableMoveLimit(list)) {
            this.highlightedProgramLine = n;
            if (this.canSelectGraphPoint()) {
                this.graphEditorSelectionIndex = n2;
            } else {
                this.clearGraphEditorSelection();
            }
            this.highlightProgramMove(list.get(n2));
            return;
        }
        this.highlightedProgramLine = n;
        this.highlightedProgramMove = null;
        this.syncProgramHighlight();
    }

    private void highlightProgramMove(ToolMove toolMove) {
        if (this.programEditor == null || toolMove == null) {
            this.clearProgramHighlight();
            return;
        }
        int n = toolMove.sourceLine > 0 ? toolMove.sourceLine : this.markerLineForMove(toolMove);
        if (n <= 0) {
            this.clearProgramHighlight();
            return;
        }
        this.highlightedProgramLine = n;
        this.highlightedProgramMove = toolMove;
        this.syncProgramHighlight();
    }

    private void clearProgramHighlight() {
        this.highlightedProgramLine = -1;
        this.highlightedProgramMove = null;
        this.syncProgramHighlight();
    }

    private int findMoveIndexForCaretLine(List<ToolMove> list) {
        int n = this.getCaretLineNumber();
        return this.findMoveIndexForLine(list, n);
    }

    private int findMoveIndexForLine(List<ToolMove> list, int n) {
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i).sourceLine >= n) {
                return i;
            }
        }
        return Math.max(0, list.size() - 1);
    }

    private int findMoveIndexForExactLine(List<ToolMove> list, int n) {
        if (list == null || list.isEmpty() || n <= 0) {
            return -1;
        }
        int last = -1;
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i).sourceLine == n) {
                last = i;
            }
        }
        return last;
    }

    private static int binarySearchFirstSourceLine(int[] sourceLines, int targetLine) {
        int lo = 0;
        int hi = sourceLines.length - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sourceLines[mid] == targetLine) {
                found = mid;
                hi = mid - 1;
            } else if (sourceLines[mid] < targetLine) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /** Выбор точки на графике (в т.ч. дуги G02/G03 во время цикла). */
    private boolean canSelectGraphPoint() {
        return this.graphInteractionEnabled() && !this.getActiveToolMoves().isEmpty();
    }

    /** Последний ход строки (дуги G02/G03 дают несколько сегментов с одним sourceLine). */
    private int findLastMoveIndexForSourceLine(List<ToolMove> list, int sourceLine) {
        int first = this.findMoveIndexForExactLine(list, sourceLine);
        if (first < 0) {
            return -1;
        }
        int last = first;
        while (last + 1 < list.size() && list.get(last + 1).sourceLine == sourceLine) {
            ++last;
        }
        return last;
    }

    private void rebuildMoveSourceLineIndex(List<ToolMove> moves) {
        if (moves == null || moves.isEmpty()) {
            this.cachedMoveSourceLines = new int[0];
            return;
        }
        this.cachedMoveSourceLines = new int[moves.size()];
        for (int i = 0; i < moves.size(); ++i) {
            this.cachedMoveSourceLines[i] = moves.get(i).sourceLine;
        }
    }

    private int findMoveIndexForMarkerLine(List<ToolMove> list, int n) {
        int n2 = this.findMoveIndexForExactLine(list, n);
        if (n2 >= 0) {
            return n2;
        }
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i).sourceLine == n) {
                return i;
            }
        }
        return this.findMoveIndexForLine(list, n);
    }

    private int getCaretLineNumber() {
        if (this.programEditor == null) {
            return 1;
        }
        String string = this.programEditor.getText();
        int n = Math.max(0, Math.min(this.programEditor.getCaretPosition(), string.length()));
        int n2 = 1;
        for (int i = 0; i < n; ++i) {
            if (string.charAt(i) == '\n') {
                ++n2;
            }
        }
        return n2;
    }

    private int getProgramLineCount() {
        if (this.programEditor == null) {
            return 1;
        }
        return this.programEditor.getLineCount();
    }

    private int progressPercent(List<ToolMove> list) {
        if (list == null || list.isEmpty() || this.currentExecutionIndex < 0) {
            return 0;
        }
        return this.progressPercentForMove(list, this.currentExecutionIndex);
    }

    private int progressPercentForMove(List<ToolMove> list, int n) {
        if (list == null || list.isEmpty() || n < 0) {
            return 0;
        }
        int n2 = this.countDistinctFrameLines(list);
        if (n2 <= 0) {
            return (int)Math.round((double)(n + 1) * 100.0 / (double)list.size());
        }
        return (int)Math.round((double)this.frameOrdinalAt(list, n) * 100.0 / (double)n2);
    }

    private String formatExecutionStatus(List<ToolMove> list, ToolMove toolMove, int n) {
        if (toolMove == null || list == null || list.isEmpty()) {
            return "";
        }
        String string = this.formatNBlockLabel(this.getProgramLineText(toolMove.sourceLine));
        char c = this.verticalProgramAxis();
        return String.format(Locale.US, I18n.text("app.159"), string, n + 1, list.size(), toolMove.endX, Character.valueOf(c), toolMove.endZ);
    }

    private String formatNBlockLabel(String string) {
        Integer n = this.extractBlockNumber(string);
        return n != null ? "N" + n : I18n.text("app.160");
    }

    private Integer extractBlockNumber(String string) {
        if (string == null) {
            return null;
        }
        String string2 = this.stripGCodeComment(string).trim();
        Matcher matcher = Pattern.compile("\\bN(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(string2);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        }
        catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private int countDistinctFrameLines(List<ToolMove> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        HashSet<Integer> hashSet = new HashSet<Integer>();
        for (ToolMove toolMove : list) {
            if (!this.isFrameMove(toolMove)) {
                continue;
            }
            hashSet.add(toolMove.sourceLine);
        }
        return hashSet.size();
    }

    private int frameOrdinalAt(List<ToolMove> list, int n) {
        if (list == null || list.isEmpty() || n < 0) {
            return 0;
        }
        int n2 = Math.min(n, list.size() - 1);
        int n3 = 0;
        int n4 = -1;
        for (int i = 0; i <= n2; ++i) {
            ToolMove toolMove = list.get(i);
            if (!this.isFrameMove(toolMove) || toolMove.sourceLine == n4) {
                continue;
            }
            ++n3;
            n4 = toolMove.sourceLine;
        }
        return Math.max(n3, 1);
    }

    private long getDrawDelayMillis() {
        double d = this.sliderDrawSpeed == null ? 5.0 : this.sliderDrawSpeed.getValue();
        long baseDelay = Math.max(25L, Math.round(1000.0 - Math.max(1.0, Math.min(10.0, d)) * 90.0));
        int feedOverride = UserSettings.getFeedOverridePercent();
        if (feedOverride <= 0) {
            return 60000L;
        }
        return Math.max(25L, Math.round(baseDelay * 100.0 / feedOverride));
    }

    private void sleepQuietly(long l) {
        try {
            Thread.sleep(l);
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepWhileSpaceHeld(long l) {
        long l2 = System.currentTimeMillis() + l;
        while (System.currentTimeMillis() < l2 && this.spaceSingleBlockRepeatRunning.get()) {
            long l3 = l2 - System.currentTimeMillis();
            if (l3 <= 0L) {
                break;
            }
            this.sleepQuietly(Math.min(SPACE_HOLD_SLEEP_POLL_MS, l3));
        }
    }

    private void updateProgressDisplay(int n, String string) {
        Platform.runLater(() -> {
            if (this.progressExecution != null) {
                this.progressExecution.setProgress(Math.max(0.0, Math.min(1.0, (double)n / 100.0)));
            }
            if (this.labelExecutionProgress != null) {
                this.labelExecutionProgress.setText(n + "%");
            }
            if (this.labelExecutionStatus != null) {
                this.labelExecutionStatus.setText(string);
            }
        });
    }

    private void updateExecutionStatusText(String string) {
        Platform.runLater(() -> {
            if (this.labelExecutionStatus != null) {
                this.labelExecutionStatus.setText(string);
            }
        });
    }

    private void enableToolRadiusCompensation() {
        this.updateExecutionStatusText(I18n.text("app.161"));
        this.redrawCanvas();
    }

    private void disableToolRadiusCompensation() {
        this.updateExecutionStatusText(I18n.text("app.162"));
        this.redrawCanvas();
    }

    private void updateZoomDisplay() {
        this.textZooming.setText(String.format("%.0f%%", this.currentZoom * 100.0));
    }

    private void resetCanvasView() {
        this.centerViewOnOrigin();
        this.redrawCanvas();
    }

    private void redrawCanvas() {
        this.requestCanvasRedraw();
    }

    private void redrawCanvasForInteraction() {
        this.requestCanvasRedraw();
    }

    private void requestCanvasRedraw() {
        if (!this.canvasRedrawRequested.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(this::flushCanvasRedraw);
    }

    private void flushCanvasRedraw() {
        if (!this.canvasRedrawRequested.getAndSet(false)) {
            return;
        }
        if (!this.canvasRedrawInFlight.compareAndSet(false, true)) {
            this.canvasRedrawRequested.set(true);
            return;
        }
        Platform.runLater(() -> {
            try {
                this.drawCanvasNow();
                this.applyCanvasTransformations();
            }
            catch (RuntimeException runtimeException) {
                LOGGER.warning("Canvas redraw failed: " + runtimeException.getMessage());
            }
            finally {
                this.canvasRedrawInFlight.set(false);
                if (this.canvasRedrawRequested.get()) {
                    Platform.runLater(this::flushCanvasRedraw);
                }
            }
        });
    }

    private void drawCanvasNow() {
        if (this.drawingCanvas == null) {
            return;
        }
        double d = Math.max(1.0, this.drawingCanvas.getWidth());
        double d2 = Math.max(1.0, this.drawingCanvas.getHeight());
        List<ToolMove> list = this.movesForDrawing();
        this.drawToolpathPreview(this.drawingCanvas, d, d2, list);
    }

    private void syncAppliedProgramFromEditor() {
        if (this.programEditor == null) {
            return;
        }
        String string = this.programEditor.getText();
        if (!string.equals(this.appliedProgramText)) {
            this.recordProgramHistoryIfChanged(this.appliedProgramText, string);
            this.appliedProgramText = string;
            this.saveLastProgramText(string);
            this.cachedToolMovesSource = "";
            this.previewLayoutValid = false;
            this.syncWorkOffsetsFromProgram(string);
            this.refreshProgramHistoryView();
        }
    }

    /**
     * Из УП — только какая G54/G55… активна в конце (для подписи).
     * Количество строк в таблице не уменьшаем; при необходимости только добавляем строки.
     */
    private void refreshActiveWorkOffsetFromProgram(String programText) {
        ProgramWorkOffsetScan scan = GCodeProgramParser.scanWorkOffsets(programText);
        int active = scan.activeCode();
        if (active != this.activeWorkOffsetFromProgram) {
            this.previewLayoutValid = false;
        }
        this.activeWorkOffsetFromProgram = active;
        this.updateActiveWorkOffsetLabel();
        if (!scan.hasOffsets()) {
            return;
        }
        // G-code only chooses the active offset. The saved row count stays under user control.
    }

    private void syncWorkOffsetsFromProgram(String programText) {
        this.refreshActiveWorkOffsetFromProgram(programText);
    }

    /** Полный пересчёт траектории после «Сохранить»: УП с нуля + ручные смещения по G54…. */
    private List<ToolMove> reparseToolMovesFromProgram(String programText) {
        this.cachedToolMovesSource = "";
        List<ToolMove> moves = this.parseMovesForView(programText != null ? programText : "");
        this.cachedToolMoves = moves;
        this.cachedToolMovesSource = this.toolMovesCacheKey();
        this.rebuildMoveSourceLineIndex(moves);
        return moves;
    }

    private String formatWorkOffsetField(double valueMm) {
        if (!Double.isFinite(valueMm) || Math.abs(valueMm) < 1.0E-6) {
            return "";
        }
        return String.format(Locale.US, "%.3f", valueMm);
    }

    private void updateActiveWorkOffsetLabel() {
        if (this.activeWorkOffsetLabel == null) {
            return;
        }
        if (this.activeWorkOffsetFromProgram >= 54) {
            StringBuilder text = new StringBuilder(
                    I18n.text("app.163")
                            + this.activeWorkOffsetFromProgram
                            + I18n.text("app.164"));
            WorkOffset manual = this.readWorkOffsets().get(this.activeWorkOffsetFromProgram);
            if (manual != null && (Math.abs(manual.x) > 1.0E-6 || Math.abs(manual.z) > 1.0E-6)) {
                text.append("  X=").append(String.format(Locale.US, "%.3f", manual.x));
                text.append("  Z=").append(String.format(Locale.US, "%.3f", manual.z));
            }
            this.activeWorkOffsetLabel.setText(text.toString());
        } else if (this.activeWorkOffsetFromProgram == 500) {
            StringBuilder text = new StringBuilder(
                    I18n.text("app.165"));
            WorkOffset manual = this.readWorkOffsets().get(54);
            if (manual != null && (Math.abs(manual.x) > 1.0E-6 || Math.abs(manual.z) > 1.0E-6)) {
                text.append("  X=").append(String.format(Locale.US, "%.3f", manual.x));
                text.append("  Z=").append(String.format(Locale.US, "%.3f", manual.z));
            }
            this.activeWorkOffsetLabel.setText(text.toString());
        } else {
            this.activeWorkOffsetLabel.setText(
                    I18n.text("app.166"));
        }
    }

    private List<ToolMove> getActiveToolMoves() {
        this.syncAppliedProgramFromEditor();
        return this.getCachedToolMoves();
    }

    /**
     * Ходы для графика: на карусели каналы стоят по разные стороны оси.
     *
     * <p>Суппорты такого станка работают с двух сторон детали, и 3D их так и
     * показывает: канал 1 справа от оси вращения, канал 2 слева. На графике оба
     * канала ложились на одну сторону, и по картинке нельзя было понять, какой
     * суппорт где. Знак радиуса и есть сторона — станок так же позволяет
     * суппорту переходить через ось.
     *
     * <p>Зеркалится только рисунок: расчёт заготовки, проверка и 3D берут ходы
     * своим путём ({@link #buildContourMoveDataList}) и знака не видят.
     */
    private List<ToolMove> parseMovesForGraph(String text) {
        if (!this.carouselGraph()) {
            return this.parseMovesForView(text);
        }
        if (this.useBothSimulationChannels()) {
            var merged = new ArrayList<ToolMove>();
            merged.addAll(this.toGraphSide(this.parseGCodeMoves(this.leftProgramEditor.getText()),
                    this.graphSupportSide(this.leftProgramEditor.getText())));
            merged.addAll(this.toGraphSide(this.parseGCodeMoves(this.rightProgramEditor.getText()),
                    this.graphSupportSide(this.rightProgramEditor.getText())));
            return merged;
        }
        // Канал работает со своей стороны оси и когда он на экране один: иначе
        // ступица канала 2 оказывалась слева, хотя на станке она у оси справа.
        return this.toGraphSide(this.parseGCodeMoves(text), this.graphSupportSide(text));
    }

    /**
     * С какой стороны оси вращения работает этот канал: -1 слева, +1 справа.
     *
     * <p>Номер суппорта берётся из шапки программы (";Channel : 2 (left)") — тем
     * же способом, что и в 3D. Если шапки нет, сторону выдают оси: канал на U/W
     * — это левый суппорт такого станка.
     */
    private double graphSupportSide(String programText) {
        String program = programText == null ? "" : programText;
        if (com.sergey.pisarev.service.MultiChannelSimulation.supportNumber(program, 0) == 2) {
            return -1.0;
        }
        return ProgramFileRole.detectAxisLetters(program)[0] == 'U' ? -1.0 : 1.0;
    }

    /** Кладёт ходы на свою сторону оси: знак радиуса и есть сторона. */
    private List<ToolMove> toGraphSide(List<ToolMove> moves, double side) {
        if (side >= 0) {
            return moves;
        }
        var mirrored = new ArrayList<ToolMove>(moves.size());
        for (ToolMove move : moves) {
            mirrored.add(new ToolMove(-move.startX, move.startZ, -move.endX, move.endZ,
                    move.rapid, move.sourceLine, move.arcSegment, move.arcEnd));
        }
        return mirrored;
    }

    private List<ToolMove> getCachedToolMoves() {
        String string = this.toolMovesCacheKey();
        if (string.equals(this.cachedToolMovesSource)) {
            return this.cachedToolMoves;
        }
        this.syncAppliedProgramFromEditor();
        String string2 = this.appliedProgramText != null ? this.appliedProgramText : "";
        this.cachedToolMoves = this.parseMovesForGraph(string2);
        this.cachedToolMovesSource = string;
        this.rebuildMoveSourceLineIndex(this.cachedToolMoves);
        return this.cachedToolMoves;
    }

    private String toolMovesCacheKey() {
        // Сторона канала зависит от станка, поэтому тип станка входит в ключ:
        // без него после переключения на карусель остался бы старый рисунок.
        String string = this.appliedProgramText != null ? this.appliedProgramText : "";
        // Режим «оба канала» и текст второго окна — часть ключа: без них включение
        // режима не пересчитывало траекторию, и на экране оставалась прежняя.
        String other = this.useBothSimulationChannels() && this.rightProgramEditor != null
                ? this.rightProgramEditor.getText() + "\u0001" + (this.leftProgramEditor != null
                        ? this.leftProgramEditor.getText() : "")
                : "";
        return string
                + "\u0001"
                + this.axisMode.name()
                + "\u0001"
                + this.workOffsetsFingerprint()
                + "\u0001"
                + this.activeWorkOffsetFromProgram
                + "\u0001"
                + this.toolpathGraphVisible
                + "\u0001"
                + this.bothChannelsActive
                + "\u0001"
                + this.verticalLatheGraph
                + "\u0001"
                + other;
    }

    private String workOffsetsFingerprint() {
        if (!this.graphManualOffsetsSnapshot.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<Integer, WorkOffset> entry : this.graphManualOffsetsSnapshot.entrySet()) {
                builder.append(entry.getKey())
                        .append(':')
                        .append(entry.getValue().x)
                        .append(',')
                        .append(entry.getValue().z)
                        .append(';');
            }
            return builder.append('\u0004').append(this.activeWorkOffsetFromProgram).toString();
        }
        this.syncWorkOffsetsToStore();
        return WorkOffsetStore.fingerprint() + '\u0004' + this.activeWorkOffsetFromProgram;
    }

    private void drawToolpathPreview(Canvas canvas, double d, double d2, List<ToolMove> list) {
        double d3;
        double d4;
        double d5;
        double d6;
        double d7;
        GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
        this.refreshGraphMachineOrientation();
        graphicsContext.clearRect(0.0, 0.0, d, d2);
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#000000" : "#f8fafc")));
        graphicsContext.fillRect(0.0, 0.0, d, d2);
        this.drawGrid(graphicsContext, d, d2);
        if (list.isEmpty()) {
            this.drawOriginCanvasChrome(graphicsContext, d, d2);
            this.drawEmptyProgramHint(graphicsContext);
            return;
        }
        if (!this.shouldShowToolpath()) {
            this.drawOriginCanvasChrome(graphicsContext, d, d2);
            this.activeHoverPoint = canvas == this.drawingCanvas ? null : this.activeHoverPoint;
            return;
        }
        List<ToolMove> list2 = this.buildDisplayMoves(list);
        List<ToolMove> list3 = list;
        if (list2.isEmpty()) {
            this.drawOriginCanvasChrome(graphicsContext, d, d2);
            return;
        }
        int nVisibleFrom = this.visibleMovesFromIndex;
        if (!this.computeProgramBoundsLayout(d, d2, list2)) {
            this.previewLayoutValid = false;
            this.drawOriginCanvasChrome(graphicsContext, d, d2);
            this.drawEmptyProgramHint(graphicsContext);
            return;
        }
        if (this.launchFocusPending) {
            this.panToCurrentToolPoint(list3, d, d2);
            this.launchFocusPending = false;
        }
        double d15 = this.previewLayoutOriginX;
        double d16 = this.previewLayoutOriginY;
        double d14 = this.previewLayoutScale;
        double d10 = this.previewLayoutMinZ;
        double d9 = this.previewLayoutMaxX;
        double d8 = this.previewLayoutMinX;
        double d11 = this.previewLayoutMaxZ;
        double d13 = Math.max(1.0, d11 - d10);
        double d7span = Math.max(1.0, d9 - d8);
        double d17 = this.toScreenX(d15, 0.0, 0.0, d10, d14);
        double d18 = this.toScreenY(d16, 0.0, 0.0, d9, d14);
        this.drawProgramValueGrid(graphicsContext);
        // В токарном виде рамку габаритов не рисуем: её нижняя грань ложилась ровно
        // на ось вращения и читалась как лишняя линия детали. Низ вида и так помечен
        // самой осью, а размах виден по шкале.
        if (this.axisMode != AxisMode.XZ) {
            this.drawWorkBounds(graphicsContext, d15, d16, d13 * d14, d7span * d14);
        }
        this.drawAxes(graphicsContext, d, d2, d17, d18);
        list = list2;
        list3 = list;
        if (this.checkBoxToolRadius != null && this.checkBoxToolRadius.isSelected() && this.graphInteractionEnabled()) {
            for (ToolMove toolMove : list) {
                d6 = this.toScreenX(d15, toolMove.startX, toolMove.startZ, d10, d14);
                d5 = this.toScreenY(d16, toolMove.startX, toolMove.startZ, d9, d14);
                d4 = this.toScreenX(d15, toolMove.endX, toolMove.endZ, d10, d14);
                d3 = this.toScreenY(d16, toolMove.endX, toolMove.endZ, d9, d14);
                if (!this.segmentIntersectsCanvas(d6, d5, d4, d3, d, d2)) {
                    continue;
                }
                graphicsContext.setLineDashes(null);
                graphicsContext.setStroke((Paint)Color.rgb((int)59, (int)130, (int)246, (double)0.18));
                graphicsContext.setLineWidth(14.0);
                graphicsContext.strokeLine(d6, d5, d4, d3);
            }
        }
        for (int i = 0; i < list.size(); ++i) {
            boolean bl;
            ToolMove toolMove;
            toolMove = list.get(i);
            d6 = this.toScreenX(d15, toolMove.startX, toolMove.startZ, d10, d14);
            d5 = this.toScreenY(d16, toolMove.startX, toolMove.startZ, d9, d14);
            d4 = this.toScreenX(d15, toolMove.endX, toolMove.endZ, d10, d14);
            d3 = this.toScreenY(d16, toolMove.endX, toolMove.endZ, d9, d14);
            boolean bl2 = bl = nVisibleFrom + i == this.currentExecutionIndex;
            // Сегменты вне видимой канвы не рисуем — на тяжёлых программах (тысячи ходов)
            // это и есть основная стоимость панорамы/зума при близком масштабе.
            if (!bl && !this.segmentIntersectsCanvas(d6, d5, d4, d3, d, d2)) {
                continue;
            }
            // Раскраска как в SINUMERIK Operate: быстрый ход — красный, рабочая подача —
            // зелёная, и прямая с дугой одного цвета. Наладчик читает график станка
            // каждый день, и обратные цвета сбивают с толку сильнее любой мелкой ошибки.
            graphicsContext.setStroke((Paint)(bl
                    ? Color.web((String)"#f59e0b")
                    : (toolMove.rapid ? Color.web((String)SINUMERIK_RAPID_COLOR)
                            : Color.web((String)SINUMERIK_FEED_COLOR))));
            graphicsContext.setLineWidth(bl ? 4.4 : (toolMove.rapid ? 1.4 : 2.2));
            graphicsContext.setLineDashes(null);
            graphicsContext.strokeLine(d6, d5, d4, d3);
        }
        graphicsContext.setLineDashes(null);
        if (this.shouldShowManualGraphSelection(list3)) {
            ToolMove selected = list3.get(this.graphEditorSelectionIndex);
            double sx0 = this.toScreenX(d15, selected.startX, selected.startZ, d10, d14);
            double sy0 = this.toScreenY(d16, selected.startX, selected.startZ, d9, d14);
            double d19 = this.toScreenX(d15, selected.endX, selected.endZ, d10, d14);
            double d20 = this.toScreenY(d16, selected.endX, selected.endZ, d9, d14);
            graphicsContext.setStroke((Paint)Color.web("#86efac", 0.85));
            graphicsContext.setLineWidth(3.0);
            graphicsContext.setLineDashes(null);
            graphicsContext.strokeLine(sx0, sy0, d19, d20);
            this.drawEditorSelectionMarker(graphicsContext, d19, d20);
        }
        if (this.pathDisplayActive
                && this.currentExecutionIndex >= 0
                && this.currentExecutionIndex < list3.size()
                && this.currentExecutionIndex != this.graphEditorSelectionIndex) {
            ToolMove toolMove = list3.get(this.currentExecutionIndex);
            double sx0 = this.toScreenX(d15, toolMove.startX, toolMove.startZ, d10, d14);
            double sy0 = this.toScreenY(d16, toolMove.startX, toolMove.startZ, d9, d14);
            double d19 = this.toScreenX(d15, toolMove.endX, toolMove.endZ, d10, d14);
            double d20 = this.toScreenY(d16, toolMove.endX, toolMove.endZ, d9, d14);
            graphicsContext.setStroke((Paint)Color.web("#f59e0b"));
            graphicsContext.setLineWidth(5.0);
            graphicsContext.setLineDashes(null);
            graphicsContext.strokeLine(sx0, sy0, d19, d20);
            this.drawToolMarker(graphicsContext, d19, d20);
        }
        if (this.graphInteractionEnabled()) {
            for (ToolMove toolMove : list) {
                if (toolMove.arcSegment && !toolMove.arcEnd) continue;
                double npx = this.toScreenX(d15, toolMove.endX, toolMove.endZ, d10, d14);
                double npy = this.toScreenY(d16, toolMove.endX, toolMove.endZ, d9, d14);
                if (npx < -4.0 || npx > d + 4.0 || npy < -4.0 || npy > d2 + 4.0) {
                    continue;
                }
                this.drawNodePoint(graphicsContext, npx, npy);
            }
        }
        ToolMove toolMove = list.get(0);
        ToolMove toolMove2 = list.get(list.size() - 1);
        d6 = this.toScreenX(d15, toolMove.startX, toolMove.startZ, d10, d14);
        d5 = this.toScreenY(d16, toolMove.startX, toolMove.startZ, d9, d14);
        d4 = this.toScreenX(d15, toolMove2.endX, toolMove2.endZ, d10, d14);
        d3 = this.toScreenY(d16, toolMove2.endX, toolMove2.endZ, d9, d14);
        this.drawPoint(graphicsContext, d6, d5, Color.web((String)"#22c55e"));
        this.drawPoint(graphicsContext, d4, d3, this.previewFromProgramHead && this.currentExecutionIndex >= list3.size() - 1 ? Color.web((String)"#ef4444") : Color.web((String)"#f59e0b"));
        if (this.graphInteractionEnabled()) {
            int pickLimit = this.graphSelectableMoveLimit(list3);
            HoverPoint hoverPoint = this.findHoverPoint(list, d15, d16, d14, d10, d9, pickLimit);
            this.activeHoverPoint = canvas == this.drawingCanvas ? hoverPoint : this.activeHoverPoint;
            if (hoverPoint != null) {
                this.drawCoordinateTag(
                        graphicsContext,
                        hoverPoint.screenX,
                        hoverPoint.screenY,
                        hoverPoint.modelX,
                        hoverPoint.modelZ,
                        hoverPoint.title,
                        d,
                        d2,
                        hoverPoint.sourceLine);
            }
        } else {
            this.activeHoverPoint = null;
        }
        this.drawLegend(graphicsContext, list.size(), d10, d11, d8, d9, d);
    }

    private boolean isHoveringPoint(double d, double d2) {
        if (!Double.isFinite(this.hoverMouseX) || !Double.isFinite(this.hoverMouseY)) {
            return false;
        }
        double d3 = this.hoverMouseX - d;
        double d4 = this.hoverMouseY - d2;
        return Math.hypot(d3, d4) <= 28.0;
    }

    private int graphSelectableMoveLimit(List<ToolMove> moves) {
        if (moves == null || moves.isEmpty()) {
            return -1;
        }
        if (this.trajectoryPreviewComplete || !this.pathDisplayActive) {
            return moves.size() - 1;
        }
        if (this.currentExecutionIndex < 0) {
            return -1;
        }
        return Math.min(this.currentExecutionIndex, moves.size() - 1);
    }

    private HoverPoint findHoverPoint(
            List<ToolMove> list,
            double d,
            double d2,
            double d3,
            double d4,
            double d5,
            int maxMoveIndexInclusive
    ) {
        HoverPoint endpoint = this.findHoverPointOnEndpoints(
                list, d, d2, d3, d4, d5, 14.0, maxMoveIndexInclusive);
        if (endpoint != null) {
            return endpoint;
        }
        return this.findHoverPointOnSegments(
                list, d, d2, d3, d4, d5, 22.0, maxMoveIndexInclusive);
    }

    private HoverPoint findHoverPointOnEndpoints(
            List<ToolMove> moves,
            double originX,
            double originY,
            double scale,
            double minZ,
            double maxR,
            double pickRadiusPx,
            int maxMoveIndexInclusive
    ) {
        if (!Double.isFinite(this.hoverMouseX) || !Double.isFinite(this.hoverMouseY)) {
            return null;
        }
        if (maxMoveIndexInclusive < 0) {
            return null;
        }
        HoverPoint best = null;
        double bestDist = pickRadiusPx;
        int last = Math.min(maxMoveIndexInclusive, moves.size() - 1);
        for (int i = 0; i <= last; i++) {
            ToolMove move = moves.get(i);
            double ex = this.toScreenX(originX, move.endX, move.endZ, minZ, scale);
            double ey = this.toScreenY(originY, move.endX, move.endZ, maxR, scale);
            double dist = Math.hypot(this.hoverMouseX - ex, this.hoverMouseY - ey);
            if (dist <= bestDist) {
                bestDist = dist;
                best = new HoverPoint(
                        move.rapid ? "G00" : (move.arcSegment ? "G02/G03" : "G01"),
                        move.endX,
                        move.endZ,
                        ex,
                        ey,
                        move.sourceLine,
                        i);
            }
        }
        return best;
    }

    private HoverPoint createHoverPoint(String string, double d, double d2, int n, double d3, double d4, double d5, double d6, double d7) {
        double d8 = this.toScreenX(d3, d, d2, d6, d5);
        double d9 = this.toScreenY(d4, d, d2, d7, d5);
        return new HoverPoint(string, d, d2, d8, d9, n);
    }

    private double distanceToHover(HoverPoint hoverPoint) {
        if (!Double.isFinite(this.hoverMouseX) || !Double.isFinite(this.hoverMouseY)) {
            return Double.MAX_VALUE;
        }
        return Math.hypot(this.hoverMouseX - hoverPoint.screenX, this.hoverMouseY - hoverPoint.screenY);
    }

    private void drawSinuTrainToolpathPreview(
            GraphicsContext graphicsContext,
            double width,
            double height,
            List<ToolMove> moves,
            List<ToolMove> executionMoves
    ) {
        SinuTrainCanvasPainter.fillBackground(graphicsContext, width, height, this.darkTheme);
        if (!this.computeProgramBoundsLayout(width, height)) {
            this.previewLayoutValid = false;
            return;
        }
        if (this.launchFocusPending) {
            this.panToCurrentToolPoint(executionMoves, width, height);
            this.launchFocusPending = false;
        }
        double originX = this.previewLayoutOriginX;
        double originY = this.previewLayoutOriginY;
        double scale = this.previewLayoutScale;
        double minZ = this.previewLayoutMinZ;
        double maxZ = this.previewLayoutMaxZ;
        double minR = this.previewLayoutMinX;
        double maxR = this.previewLayoutMaxX;
        SinuTrainCanvasPainter.drawPlotPanel(
                graphicsContext, originX, originY, minZ, maxZ, minR, maxR, scale, this.darkTheme);
        SinuTrainCanvasPainter.drawValueGrid(
                graphicsContext, originX, originY, minZ, maxZ, minR, maxR, scale, this.darkTheme);
        this.displayWorkpieceWithOverride(GCodeProgramParser.findWorkpiece(this.appliedProgramText))
                .map(wp -> this.workpieceForGraphDisplay(wp, this.appliedProgramText))
                .ifPresent(wp -> SinuTrainCanvasPainter.drawStock(
                        graphicsContext, wp, originX, originY, minZ, maxZ, minR, maxR, scale));
        SinuTrainCanvasPainter.drawReferenceAxes(
                graphicsContext, originX, originY, minZ, maxZ, minR, maxR, scale);
        ArrayList<GCodeMoveData> path = new ArrayList<>(moves.size());
        for (ToolMove move : moves) {
            path.add(new GCodeMoveData(
                    move.startX,
                    move.startZ,
                    move.endX,
                    move.endZ,
                    move.rapid,
                    move.sourceLine,
                    move.arcSegment,
                    0,
                    1,
                    move.arcEnd));
        }
        SinuTrainCanvasPainter.drawToolpath(
                graphicsContext, path, originX, originY, minZ, maxZ, minR, maxR, scale);
        if (this.currentExecutionIndex >= 0 && this.currentExecutionIndex < executionMoves.size()) {
            ToolMove active = executionMoves.get(this.currentExecutionIndex);
            double sx0 = originX + (active.startZ - minZ) * scale;
            double sy0 = originY + (maxR - LatheMeshBuilder.toRadius(active.startX)) * scale;
            double sx = originX + (active.endZ - minZ) * scale;
            double sy = originY + (maxR - LatheMeshBuilder.toRadius(active.endX)) * scale;
            graphicsContext.setStroke(Color.web("#f59e0b"));
            graphicsContext.setLineWidth(3.2);
            graphicsContext.setLineDashes(null);
            graphicsContext.strokeLine(sx0, sy0, sx, sy);
            this.drawToolMarker(graphicsContext, sx, sy);
        }
        if (this.graphInteractionEnabled()) {
            for (ToolMove move : moves) {
                if (move.arcSegment && !move.arcEnd) {
                    continue;
                }
                double px = originX + (move.endZ - minZ) * scale;
                double py = originY + (maxR - LatheMeshBuilder.toRadius(move.endX)) * scale;
                this.drawNodePoint(graphicsContext, px, py);
            }
            HoverPoint hoverPoint = this.findHoverPointSinuTrain(moves, originX, originY, scale, minZ, maxR);
            this.activeHoverPoint = hoverPoint;
            if (hoverPoint != null) {
                this.drawCoordinateTag(
                        graphicsContext,
                        hoverPoint.screenX,
                        hoverPoint.screenY,
                        hoverPoint.modelX,
                        hoverPoint.modelZ,
                        hoverPoint.title,
                        width,
                        height,
                        hoverPoint.sourceLine);
            }
        } else {
            this.activeHoverPoint = null;
        }
        double displayX = this.activeHoverPoint != null ? this.activeHoverPoint.modelX : 0.0;
        double displayZ = this.activeHoverPoint != null ? this.activeHoverPoint.modelZ : 0.0;
        String status = this.executionRunning ? I18n.text("app.167") : I18n.text("app.168");
        String currentBlock = this.resolveSinuTrainStatusBlock(executionMoves);
        SinuTrainCanvasPainter.drawStatusBar(
                graphicsContext,
                width,
                height,
                displayX,
                displayZ,
                this.activeWorkOffsetFromProgram,
                UserSettings.getFeedOverridePercent(),
                UserSettings.getSpindleOverridePercent(),
                this.displayWorkpieceWithOverride(GCodeProgramParser.findWorkpiece(this.appliedProgramText)),
                status,
                currentBlock,
                this.darkTheme);
    }

    /** Explicit NC stock dimensions are authoritative, as in the 3D context. */
    private Optional<WorkpieceDefinition> displayWorkpieceWithOverride(Optional<WorkpieceDefinition> parsed) {
        return parsed;
    }

    private String resolveSinuTrainStatusBlock(List<ToolMove> executionMoves) {
        if (this.activeHoverPoint != null && this.activeHoverPoint.sourceLine > 0) {
            return this.getProgramLineText(this.activeHoverPoint.sourceLine);
        }
        if (this.currentExecutionIndex >= 0 && this.currentExecutionIndex < executionMoves.size()) {
            int line = executionMoves.get(this.currentExecutionIndex).sourceLine;
            if (line > 0) {
                return this.getProgramLineText(line);
            }
        }
        if (this.highlightedProgramLine > 0) {
            return this.getProgramLineText(this.highlightedProgramLine);
        }
        return "";
    }

    private HoverPoint findHoverPointSinuTrain(
            List<ToolMove> moves,
            double originX,
            double originY,
            double scale,
            double minZ,
            double maxR
    ) {
        int limit = this.graphSelectableMoveLimit(moves);
        return this.findHoverPointOnSegments(moves, originX, originY, scale, minZ, maxR, 14.0, limit);
    }

    private HoverPoint findHoverPointOnSegments(
            List<ToolMove> moves,
            double originX,
            double originY,
            double scale,
            double minZ,
            double maxR,
            double pickRadiusPx,
            int maxMoveIndexInclusive
    ) {
        if (!Double.isFinite(this.hoverMouseX) || !Double.isFinite(this.hoverMouseY) || moves.isEmpty()) {
            return null;
        }
        if (maxMoveIndexInclusive < 0) {
            return null;
        }
        HoverPoint best = null;
        double bestDist = pickRadiusPx;
        int last = Math.min(maxMoveIndexInclusive, moves.size() - 1);
        for (int i = 0; i <= last; i++) {
            ToolMove move = moves.get(i);
            double sx = this.toScreenX(originX, move.startX, move.startZ, minZ, scale);
            double sy = this.toScreenY(originY, move.startX, move.startZ, maxR, scale);
            double ex = this.toScreenX(originX, move.endX, move.endZ, minZ, scale);
            double ey = this.toScreenY(originY, move.endX, move.endZ, maxR, scale);
            double dist = this.distancePointToSegment(this.hoverMouseX, this.hoverMouseY, sx, sy, ex, ey);
            if (dist <= bestDist) {
                bestDist = dist;
                double t = this.segmentProjectionT(this.hoverMouseX, this.hoverMouseY, sx, sy, ex, ey);
                double modelX = move.startX + (move.endX - move.startX) * t;
                double modelZ = move.startZ + (move.endZ - move.startZ) * t;
                double px = sx + (ex - sx) * t;
                double py = sy + (ey - sy) * t;
                String title = move.arcSegment ? "G02/G03" : (move.rapid ? "G00" : "G01");
                best = new HoverPoint(title, modelX, modelZ, px, py, move.sourceLine, i);
            }
        }
        return best;
    }

    private double distancePointToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double t = this.segmentProjectionT(px, py, x1, y1, x2, y2);
        double cx = x1 + (x2 - x1) * t;
        double cy = y1 + (y2 - y1) * t;
        return Math.hypot(px - cx, py - cy);
    }

    private double segmentProjectionT(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1.0e-9) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, ((px - x1) * dx + (py - y1) * dy) / lenSq));
    }

    private void drawGrid(GraphicsContext graphicsContext, double d, double d2) {
        double d3;
        double d4 = 20.0;
        double d5 = 100.0;
        graphicsContext.setLineWidth(1.0);
        for (d3 = 0.0; d3 <= d; d3 += d4) {
            graphicsContext.setStroke((Paint)(this.isGridMajor(d3, d5) ? Color.web((String)(this.darkTheme ? "#1f2937" : "#dbe4ef")) : Color.web((String)(this.darkTheme ? "#111827" : "#edf2f7"))));
            graphicsContext.strokeLine(d3, 0.0, d3, d2);
        }
        for (d3 = 0.0; d3 <= d2; d3 += d4) {
            graphicsContext.setStroke((Paint)(this.isGridMajor(d3, d5) ? Color.web((String)(this.darkTheme ? "#1f2937" : "#dbe4ef")) : Color.web((String)(this.darkTheme ? "#111827" : "#edf2f7"))));
            graphicsContext.strokeLine(0.0, d3, d, d3);
        }
    }

    private boolean isGridMajor(double d, double d2) {
        return Math.abs(d % d2) < 0.001;
    }

    private void drawWorkBounds(GraphicsContext graphicsContext, double d, double d2, double d3, double d4) {
        graphicsContext.setStroke((Paint)Color.web((String)(this.darkTheme ? "#475569" : "#cbd5e1")));
        graphicsContext.setLineWidth(1.2);
        graphicsContext.setLineDashes(new double[]{4.0, 5.0});
        graphicsContext.strokeRoundRect(d, d2, d3, d4, 8.0, 8.0);
        graphicsContext.setLineDashes(null);
    }

    private void drawAxes(GraphicsContext graphicsContext, double d, double d2, double d3, double d4) {
        graphicsContext.setStroke((Paint)Color.web((String)(this.darkTheme ? "#64748b" : "#475569")));
        graphicsContext.setLineWidth(1.8);
        graphicsContext.strokeLine(0.0, d4, d, d4);
        graphicsContext.strokeLine(d3, 0.0, d3, d2);
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#ffffff" : "#475569")));
        if (this.carouselGraph()) {
            // Буквы стоят у своих шкал и видны при любом сдвиге вида: продольная
            // ось слева, где её числа, радиальная — внизу справа, где её числа.
            graphicsContext.fillText(this.verticalAxisLabel(), 12.0, 18.0);
            graphicsContext.fillText(this.horizontalAxisLabel(), d - 26.0, d2 - 26.0);
            return;
        }
        graphicsContext.fillText(this.horizontalAxisLabel(), d - 24.0, Math.max(18.0, d4 - 8.0));
        graphicsContext.fillText(this.verticalAxisLabel(), Math.min(d - 24.0, d3 + 8.0), 18.0);
    }

    // Подписи осей выводим из тех же правил, что и отрисовка (pathHorizontal/pathVertical).
    // Раньше в режиме XZ обе оси подписывались как «X», а «Z» не появлялась вовсе,
    // хотя по горизонтали рисуется именно Z.
    private String horizontalAxisLabel() {
        if (this.carouselGraph()) {
            // По горизонтали радиус: подпись — буква радиальной оси программы.
            char letter = this.radialProgramAxisLetter();
            return this.programRadiusMode ? String.valueOf(letter) : letter + "\u00D8";
        }
        if (this.axisMode == AxisMode.XY) {
            return "X";
        }
        return "Z";
    }

    private String verticalAxisLabel() {
        if (this.carouselGraph()) {
            // По вертикали ось детали: у канала на U/W это W.
            return String.valueOf(this.axialProgramAxisLetter());
        }
        if (this.axisMode == AxisMode.XY) {
            return "Y";
        }
        if (this.axisMode == AxisMode.XZ) {
            // Числа на шкале — те же, что в программе: диаметр при DIAMON, радиус при DIAMOF.
            return this.programRadiusMode ? "X" : "X\u00D8";
        }
        // STANDARD: X откладывается как есть. Значок диаметра уместен только на токарной
        // обработке (DIAMON); на фрезерной стойке X — обычная координата, и «XØ» там сбивает.
        return this.diameterProgrammingLikely() ? "X\u00D8" : "X";
    }

    /**
     * Число, которое оператор видит на вертикальной оси, из внутреннего X.
     *
     * <p>Внутри X всегда диаметр (см. parseGCodeMovesRaw), но на шкале должны стоять
     * ровно те числа, что технолог написал в программе. При DIAMOF это радиус, поэтому
     * делим пополам — иначе оператор ищет на графике X=680, а видит 1360.
     */
    private double verticalDisplayValue(double internalX) {
        return this.programRadiusMode ? internalX * 0.5 : internalX;
    }

    /** Число для подписи из значения пути: у диаметральной оси радиус переводится в диаметр. */
    private double axisAnnotationValue(double pathValue, boolean diameterAxis) {
        return diameterAxis ? this.verticalDisplayValue(Math.abs(pathValue) * 2.0) : pathValue;
    }

    /** Число на шкале: у диаметральной оси учитывается DIAMOF, у остальных — как есть. */
    private double axisDisplayValue(double value, boolean diameterAxis) {
        return diameterAxis ? this.verticalDisplayValue(Math.abs(value)) : value;
    }

    /** Программируется ли X диаметром: это токарное понятие, на фрезерной стойке его нет. */
    private boolean diameterProgrammingLikely() {
        return this.parseControlSystem(UserSettings.getControlSystem())
                != MachineConfiguration.ControlSystem.SIEMENS_MILLING;
    }

    private void drawPoint(GraphicsContext graphicsContext, double d, double d2, Color color) {
        graphicsContext.setFill((Paint)color);
        graphicsContext.fillOval(d - 5.0, d2 - 5.0, 10.0, 10.0);
        graphicsContext.setStroke((Paint)Color.WHITE);
        graphicsContext.setLineWidth(1.5);
        graphicsContext.strokeOval(d - 5.0, d2 - 5.0, 10.0, 10.0);
    }

    private void drawNodePoint(GraphicsContext graphicsContext, double d, double d2) {
        // Тёмный сланец на чёрном фоне не виден вовсе: в тёмной теме берём светлый тон.
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#cbd5e1" : "#334155")));
        graphicsContext.fillOval(d - 2.4, d2 - 2.4, 4.8, 4.8);
        graphicsContext.setStroke((Paint)Color.web((String)(this.darkTheme ? "#0f172a" : "#f8fafc"), 0.9));
        graphicsContext.setLineWidth(0.8);
        graphicsContext.setLineDashes(null);
        graphicsContext.strokeOval(d - 2.4, d2 - 2.4, 4.8, 4.8);
    }

    /** Пересекает ли отрезок видимую область канвы (грубая проверка по bbox для отсечения). */
    private boolean segmentIntersectsCanvas(double x0, double y0, double x1, double y1, double width, double height) {
        double margin = 8.0;
        if (Math.max(x0, x1) < -margin || Math.min(x0, x1) > width + margin) {
            return false;
        }
        if (Math.max(y0, y1) < -margin || Math.min(y0, y1) > height + margin) {
            return false;
        }
        return true;
    }

    private void drawToolMarker(GraphicsContext graphicsContext, double d, double d2) {
        graphicsContext.setFill((Paint)Color.rgb((int)245, (int)158, (int)11, (double)0.22));
        graphicsContext.fillOval(d - 13.0, d2 - 13.0, 26.0, 26.0);
        graphicsContext.setStroke((Paint)Color.web((String)"#f59e0b"));
        graphicsContext.setLineWidth(2.2);
        graphicsContext.strokeOval(d - 9.0, d2 - 9.0, 18.0, 18.0);
        graphicsContext.setFill((Paint)Color.web((String)"#f59e0b"));
        graphicsContext.fillOval(d - 4.0, d2 - 4.0, 8.0, 8.0);
    }

    private boolean shouldShowManualGraphSelection(List<ToolMove> moves) {
        if (!this.canSelectGraphPoint()
                || this.graphEditorSelectionIndex < 0
                || moves == null
                || this.graphEditorSelectionIndex >= moves.size()) {
            return false;
        }
        if (!this.pathDisplayActive || this.currentExecutionIndex < 0) {
            return true;
        }
        return this.graphEditorSelectionIndex != this.currentExecutionIndex;
    }

    private void drawEditorSelectionMarker(GraphicsContext graphicsContext, double d, double d2) {
        graphicsContext.setFill((Paint)Color.rgb(134, 239, 172, 0.35));
        graphicsContext.fillOval(d - 14.0, d2 - 14.0, 28.0, 28.0);
        graphicsContext.setStroke((Paint)Color.web("#4ade80"));
        graphicsContext.setLineWidth(2.0);
        graphicsContext.strokeOval(d - 10.0, d2 - 10.0, 20.0, 20.0);
        graphicsContext.setStroke((Paint)Color.web("#f8fafc"));
        graphicsContext.setLineWidth(1.2);
        graphicsContext.strokeOval(d - 5.5, d2 - 5.5, 11.0, 11.0);
        graphicsContext.setFill((Paint)Color.web("#f87171"));
        graphicsContext.fillOval(d - 3.5, d2 - 3.5, 7.0, 7.0);
        graphicsContext.setLineWidth(1.0);
        graphicsContext.setStroke((Paint)Color.web("#7dd3fc", 0.9));
        graphicsContext.strokeLine(d - 16.0, d2, d + 16.0, d2);
        graphicsContext.strokeLine(d, d2 - 16.0, d, d2 + 16.0);
    }

    private double measureCanvasTextWidth(GraphicsContext graphicsContext, String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        Text measure = new Text(text);
        measure.setFont(graphicsContext.getFont());
        return measure.getLayoutBounds().getWidth();
    }

    private void drawCoordinateTag(
            GraphicsContext graphicsContext,
            double d,
            double d2,
            double d3,
            double d4,
            String string,
            double d5,
            double d6,
            int sourceLine
    ) {
        String framePrefix = this.formatFramePrefixForLine(sourceLine);
        String titleLine = framePrefix.isEmpty() ? string : framePrefix + string;
        // На карусели подписи те же буквы, что в программе, а диаметр — без знака:
        // минус на графике означает сторону оси, а не размер.
        String string2 = this.carouselGraph()
                ? String.format(Locale.US, "%s %.3f  %s %.3f", this.radialProgramAxisLetter(),
                        Math.abs(d3), this.axialProgramAxisLetter(), d4)
                : String.format(Locale.US, "X %.3f  %s %.3f", d3, this.verticalProgramAxis(), d4);
        double contentWidth = Math.max(
                this.measureCanvasTextWidth(graphicsContext, titleLine),
                this.measureCanvasTextWidth(graphicsContext, string2));
        double d7 = Math.max(COORD_TAG_MIN_WIDTH, contentWidth + COORD_TAG_PAD_X * 2.0);
        double d8 = COORD_TAG_HEIGHT;
        double d9 = d + 12.0;
        double d10 = d2 - 46.0;
        if (d9 + d7 > d5 - 10.0) {
            d9 = d - d7 - 12.0;
        }
        if (d10 < 10.0) {
            d10 = d2 + 14.0;
        }
        if (d10 + d8 > d6 - 10.0) {
            d10 = d2 - d8 - 14.0;
        }
        graphicsContext.setFill((Paint)Color.rgb((int)15, (int)23, (int)42, (double)0.88));
        graphicsContext.fillRoundRect(d9, d10, d7, d8, 8.0, 8.0);
        graphicsContext.setFill((Paint)Color.WHITE);
        graphicsContext.fillText(titleLine, d9 + COORD_TAG_PAD_X, d10 + 13.0);
        graphicsContext.setFill((Paint)Color.web((String)"#cbd5e1"));
        graphicsContext.fillText(string2, d9 + COORD_TAG_PAD_X, d10 + 27.0);
    }

    private String formatFramePrefixForLine(int sourceLine) {
        if (sourceLine <= 0) {
            return "";
        }
        String text = this.normalizeProgramLineForParse(this.getProgramLineText(sourceLine));
        java.util.regex.Matcher matcher = Pattern.compile("^N(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            return "N" + matcher.group(1) + " · ";
        }
        return "";
    }

    private boolean isClickNearGraphSelection(double paneX, double paneY) {
        if (this.graphEditorSelectionIndex < 0 || this.drawingCanvas == null) {
            return false;
        }
        List<ToolMove> moves = this.getActiveToolMoves();
        if (this.graphEditorSelectionIndex >= moves.size()) {
            return false;
        }
        if (!this.previewLayoutValid && !this.computeProgramBoundsLayout(
                this.drawingCanvas.getWidth(),
                this.drawingCanvas.getHeight())) {
            return false;
        }
        ToolMove move = moves.get(this.graphEditorSelectionIndex);
        double sx = this.toScreenX(
                this.previewLayoutOriginX,
                move.endX,
                move.endZ,
                this.previewLayoutMinZ,
                this.previewLayoutScale);
        double sy = this.toScreenY(
                this.previewLayoutOriginY,
                move.endX,
                move.endZ,
                this.previewLayoutMaxX,
                this.previewLayoutScale);
        Point2D local = this.canvasGroup.sceneToLocal(this.paneCanvas.localToScene(paneX, paneY));
        return Math.hypot(local.getX() - sx, local.getY() - sy) <= 32.0;
    }

    private void drawLegend(
            GraphicsContext graphicsContext, int n, double d, double d2, double d3, double d4,
            double canvasWidth) {
        // Легенда прижата к правому краю: слева она закрывала подписи вертикальной шкалы.
        // На узкой канве не уезжаем за левый край.
        double legendWidth = 280.0;
        double d5 = Math.max(14.0, canvasWidth - legendWidth);
        double d6 = 14.0;
        // Легенда следует теме: белая подложка на тёмном графике слепила.
        graphicsContext.setFill((Paint)(this.darkTheme
                ? Color.rgb(17, 24, 39, 0.92)
                : Color.rgb(255, 255, 255, 0.92)));
        graphicsContext.fillRoundRect(d5 - 8.0, d6 - 8.0, 280.0, 140.0, 12.0, 12.0);
        graphicsContext.setStroke((Paint)Color.web((String)(this.darkTheme ? "#3f4a5a" : "#cbd5e1")));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.strokeRoundRect(d5 - 8.0, d6 - 8.0, 280.0, 122.0, 12.0, 12.0);
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#f8fafc" : "#0f172a")));
        int n2 = this.countDistinctFrameLines(this.parseGCodeMoves(this.appliedProgramText));
        graphicsContext.fillText(String.format(Locale.US, I18n.text("app.169"), n, n2), d5, d6 + 8.0);
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#94a3b8" : "#64748b")));
        // Границы приходят в единицах пути (радиус у диаметральной оси). Показываем
        // их в тех же числах, что и шкала: иначе легенда говорила «520», а сетка «1040».
        graphicsContext.fillText(
                String.format(Locale.US, "%s %.3f .. %.3f", this.horizontalAxisLabel(),
                        this.axisAnnotationValue(d, this.diameterAxisHorizontal()),
                        this.axisAnnotationValue(d2, this.diameterAxisHorizontal())),
                d5, d6 + 28.0);
        graphicsContext.fillText(
                String.format(Locale.US, "%s %.3f .. %.3f", this.verticalAxisLabel(),
                        this.axisAnnotationValue(d3, this.diameterAxisVertical()),
                        this.axisAnnotationValue(d4, this.diameterAxisVertical())),
                d5, d6 + 44.0);
        // Легенда повторяет раскраску стойки: красный — быстрый ход, зелёный — подача.
        graphicsContext.setLineDashes(null);
        graphicsContext.setLineWidth(2.0);
        graphicsContext.setStroke((Paint)Color.web((String)SINUMERIK_RAPID_COLOR));
        graphicsContext.strokeLine(d5, d6 + 64.0, d5 + 30.0, d6 + 64.0);
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#cbd5e1" : "#475569")));
        graphicsContext.fillText(I18n.text("app.170"), d5 + 38.0, d6 + 68.0);
        graphicsContext.setStroke((Paint)Color.web((String)SINUMERIK_FEED_COLOR));
        graphicsContext.strokeLine(d5, d6 + 82.0, d5 + 30.0, d6 + 82.0);
        graphicsContext.fillText(I18n.text("app.171"), d5 + 38.0, d6 + 86.0);
        graphicsContext.setStroke((Paint)Color.web((String)"#f59e0b"));
        graphicsContext.setLineWidth(3.0);
        graphicsContext.strokeLine(d5, d6 + 100.0, d5 + 30.0, d6 + 100.0);
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#cbd5e1" : "#475569")));
        graphicsContext.fillText(I18n.text("app.172"), d5 + 38.0, d6 + 104.0);
    }

    private void drawOriginCanvasChrome(GraphicsContext graphicsContext, double d, double d2) {
        if (!this.computeProgramBoundsLayout(d, d2)) {
            this.drawAxes(graphicsContext, d, d2, d / 2.0, d2 / 2.0);
            return;
        }
        this.drawPreviewChrome(graphicsContext, d, d2);
    }

    private void drawEmptyProgramHint(GraphicsContext graphicsContext) {
        String program = this.appliedProgramText != null ? this.appliedProgramText : "";
        if (!program.isBlank()) {
            // Текст есть, а перемещений нет: строить нечего, и это ошибка, а не
            // подсказка новичку. Слова и код те же, что у проверки программы (E001),
            // иначе на графике и в плашке проблем говорилось бы разное.
            graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#fca5a5" : "#b91c1c")));
            graphicsContext.fillText("E001 — " + I18n.text("diag.003"), 14.0, 22.0);
            graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#fecaca" : "#7f1d1d")));
            graphicsContext.fillText(I18n.text("diag.004").trim(), 14.0, 42.0);
            return;
        }
        graphicsContext.setFill((Paint)Color.web((String)(this.darkTheme ? "#e2e8f0" : "#0f172a")));
        graphicsContext.fillText(I18n.text("app.173"), 14.0, 22.0);
        graphicsContext.setFill((Paint)Color.web((String)"#64748b"));
        graphicsContext.fillText(I18n.text("app.174"), 14.0, 42.0);
    }

    private List<ToolMove> parseGCodeMoves(String string) {
        List<ToolMove> rawMoves = this.parseGCodeMovesRaw(string);
        return this.applyManualWorkOffsetsToMoves(rawMoves, string);
    }

    private List<ToolMove> applyManualWorkOffsetsToMoves(List<ToolMove> rawMoves, String programText) {
        if (rawMoves == null || rawMoves.isEmpty()) {
            return rawMoves == null ? List.of() : rawMoves;
        }
        Map<Integer, WorkOffsetValues> manual = this.manualOffsetValuesForGraph();
        if (manual.isEmpty()) {
            return rawMoves;
        }
        TreeMap<Integer, WorkOffsetValues> shiftAfterLine = GraphWorkOffsetTransform.buildShiftAfterSourceLine(
                programText,
                manual,
                this::readWorkOffsetCode);
        if (shiftAfterLine.isEmpty()) {
            return rawMoves;
        }
        ArrayList<ToolMove> shifted = new ArrayList<>(rawMoves.size());
        // Начало хода сдвигается смещением ПРЕДЫДУЩЕГО хода, а конец — своим.
        // При переключении нуля инструмент не двигается: меняется только координата
        // одной и той же точки. Если сдвигать оба конца одним смещением, первый ход
        // после G54→G55 получает начало, которого не было: на программе оси так
        // появлялась линия до Z=-4080 при детали 2180 мм.
        WorkOffsetValues previousShift = null;
        for (ToolMove move : rawMoves) {
            WorkOffsetValues shift = GraphWorkOffsetTransform.shiftForSourceLine(shiftAfterLine, move.sourceLine);
            WorkOffsetValues startShift = previousShift == null ? shift : previousShift;
            previousShift = shift;
            if (shift.isZero() && startShift.isZero()) {
                shifted.add(move);
                continue;
            }
            shifted.add(new ToolMove(
                    move.startX + startShift.xMm(),
                    move.startZ + startShift.zMm(),
                    move.endX + shift.xMm(),
                    move.endZ + shift.zMm(),
                    move.rapid,
                    move.sourceLine,
                    move.arcSegment,
                    move.arcEnd));
        }
        return shifted;
    }

    /** Координаты программы (WCS) без ручного смещения из настроек. */
    private List<ToolMove> parseGCodeMovesRaw(String string) {
        ArrayList<ToolMove> arrayList = new ArrayList<>();
        Map<String, Double> rVariables = new TreeMap<>();
        // Размеры двухканальной программы живут в отдельной программе параметров:
        // без неё «X=TREAD_DIAM+30» читается как X=30, и деталь выходит нулевой.
        rVariables.putAll(this.parameterVariables().values());
        // Канал 2 такого станка ходит по осям U/W, канал 1 — по X/Z.
        char[] channelAxes = ProgramFileRole.detectAxisLetters(string);
        char horizontalAxis = channelAxes[0];
        char verticalAxis = horizontalAxis == 'U' ? 'W' : this.verticalProgramAxis();
        // Пропуск участка по GOTOF: пока имя метки заполнено, кадры не исполняются.
        String skipUntilLabel = null;
        double posX = 0.0;
        double posZ = 0.0;
        double g92ShiftX = 0.0;
        double g92ShiftZ = 0.0;
        // G58/G59 — программируемое смещение нуля (грубое и точное), а не ход.
        // Раньше такой кадр считался перемещением: «G58 X=0 Z=...» ставил инструмент
        // в ноль, и от него через всю деталь шла линия, которой в программе нет.
        double frameCoarseX = 0.0;
        double frameCoarseZ = 0.0;
        double frameFineX = 0.0;
        double frameFineZ = 0.0;
        int motionMode = 1;
        boolean absoluteMode = true;
        int lineNumber = 1;
        if (string == null || string.trim().isEmpty()) {
            return arrayList;
        }
        // Внимание: X в ToolMove хранится КАК В ПРОГРАММЕ (диаметральное значение, стандарт DIAMON).
        // Преобразование в радиус выполняется в LatheMeshBuilder.toRadius() в местах рендера/просчёта,
        // поэтому делить X на 2 здесь НЕЛЬЗЯ — будет двойное деление.
        String[] programLines = string.split("\\R");
        // Дуги/RND/ANG строятся как радиусная контурная геометрия (X-диаметр делится на 2)
        // ТОЛЬКО когда X на графике показан как радиус — т.е. в режиме осей X-Z
        // (pathVertical -> toRadius). В режимах X-Y и STANDARD X рисуется как есть
        // (pathHorizontal/Vertical = X), поэтому делить на 2 НЕЛЬЗЯ — иначе радиусы дуг
        // выходили растянутыми вдвое («неправильные радиуса в осях X-Y»).
        boolean latheDiameterGeometryMode = this.axisMode == AxisMode.XZ;
        // Siemens DIAMOF: X в кадре задан радиусом. Весь остальной код (2D, 3D, меш,
        // расчёт заготовки) держит X диаметром, поэтому приводим к диаметру здесь —
        // один раз, на входе. Иначе деталь строится вдвое меньше настоящей.
        boolean radiusMode = false;
        // Инструмент стоит там, где его оставила предыдущая программа. Первый G0 —
        // это подвод из неизвестной точки, и рисовать его как ход из нуля нельзя:
        // через всю деталь ложится линия, которой в программе нет.
        boolean toolPositionKnown = false;
        boolean reentryXUnknown = false, reentryZUnknown = false;
        var activeToolsByLine = GCodeProgramParser.parseToolNumberByLine(string);
        var activeEdgesByLine = LatheCompensationProcessor.parseDNumberByLine(string);
        var parserTools = this.simulationToolLibrary(string);
        // У многих Siemens-программ R-коррекции вынесены в таблицу/комментарий в конце файла
        // (например: ";R-KI R20 0.026;R30 0.034"). Для симуляции используем их как стартовые значения.
        for (String rawLine : programLines) {
            GCodeExpressionEvaluator.collectRDefinitions(rawLine, rVariables);
            MachineParameterProgram.collectAssignments(rawLine, rVariables, null, false);
        }
        Map<Integer, Double> rndByLine = new HashMap<>();
        for (String rawLine : programLines) {
            var activeTool = LatheCompensationProcessor.resolveTool(parserTools,
                    activeToolsByLine.getOrDefault(lineNumber,0),activeEdgesByLine.getOrDefault(lineNumber,1));
            rVariables.remove("$P_TOOLR");
            if (activeTool != null) rVariables.put("$P_TOOLR",activeTool.getRadius());
            GCodeExpressionEvaluator.collectRDefinitions(rawLine, rVariables);
            MachineParameterProgram.collectAssignments(rawLine, rVariables, null, false);
            int sourceLine = lineNumber;
            String line = GCodeProgramParser.stripFrameNumberForParse(
                    GCodeProgramParser.stripCommentsForParse(rawLine)).toUpperCase(Locale.US);
            // GOTOF перепрыгивает участок программы. Раньше такие строки считались
            // неисполняемыми и пропускались молча, а перепрыгнутые проходы всё равно
            // попадали в траекторию: на программе колеса рисовались торцовки ступицы,
            // которых на станке не будет.
            if (skipUntilLabel != null) {
                if (skipUntilLabel.equals(this.programLabelOnLine(line))) {
                    skipUntilLabel = null;
                }
                ++lineNumber;
                continue;
            }
            String jumpTarget = this.forwardJumpTarget(line);
            if (jumpTarget != null) {
                skipUntilLabel = jumpTarget;
                ++lineNumber;
                continue;
            }
            if (!line.isBlank()) {
                Integer fixtureOnLine = this.readWorkOffsetCode(line);
                if (fixtureOnLine != null) {
                    this.activeWorkOffsetFromProgram = fixtureOnLine;
                }
            }
            // Конец программы проверяем ДО фильтра неисполняемых строк: у кадра M30
            // нет ни движения, ни осей, поэтому фильтр отбрасывал его раньше времени,
            // и разбор продолжался дальше по файлу.
            if (GCodeProgramParser.isProgramEndLine(line)) {
                break;
            }
            // Переключение DIAMON/DIAMOF читаем ДО фильтра неисполняемых строк:
            // в кадре «DIAMOF» нет ни движения, ни осей, и фильтр его отбрасывал.
            Boolean diameterSwitch = GCodeProgramParser.readDiameterModeSwitch(line);
            if (diameterSwitch != null) {
                radiusMode = !diameterSwitch;
            }
            if (line.isBlank() || GCodeProgramParser.isNonExecutableProgramLine(rawLine)) {
                ++lineNumber;
                continue;
            }
            if (this.lineContainsCode(line, "G90")) {
                absoluteMode = true;
            }
            if (this.lineContainsCode(line, "G91")) {
                absoluteMode = false;
            }
            // A machine-frame retract cannot be connected to the preceding WCS
            // point without machine zero/kinematic data. Keep each affected axis
            // unknown until a subsequent absolute work-coordinate block restores it.
            if (this.lineContainsCode(line,"G153") || this.lineContainsCode(line,"SUPA")) {
                if (this.indexOfAxisToken(line,horizontalAxis)>=0) reentryXUnknown=true;
                if (this.indexOfAxisToken(line,verticalAxis)>=0) reentryZUnknown=true;
                if (reentryXUnknown||reentryZUnknown) {
                    toolPositionKnown=false;
                    Integer retractMode=this.readGCodeMotion(line);
                    if(retractMode!=null) motionMode=retractMode;
                    ++lineNumber;
                    continue;
                }
            }
            Integer fixtureCode = this.readWorkOffsetCode(line);
            AxisTerm xTerm = this.readAxisTerm(line, horizontalAxis, rVariables);
            Double xValue = xTerm == null ? null : xTerm.value();
            if (xValue != null && radiusMode) {
                // Только X: I (центр дуги) и CR у Siemens всегда радиусные величины.
                xValue = xValue * 2.0;
            }
            AxisTerm zTerm = this.readAxisTerm(line, verticalAxis, rVariables);
            Double zValue = zTerm == null ? null : zTerm.value();
            if (fixtureCode != null) {
                this.activeWorkOffsetFromProgram = fixtureCode;
            }
            if (this.lineContainsCode(line, "G92")) {
                if (xValue != null) {
                    g92ShiftX += posX - xValue;
                }
                if (zValue != null) {
                    g92ShiftZ += posZ - zValue;
                }
            }
            boolean coarseFrame = this.lineContainsCode(line, "G58");
            boolean fineFrame = this.lineContainsCode(line, "G59");
            if (coarseFrame || fineFrame) {
                double newX = xValue != null ? xValue : (coarseFrame ? frameCoarseX : frameFineX);
                double newZ = zValue != null ? zValue : (coarseFrame ? frameCoarseZ : frameFineZ);
                if (coarseFrame) {
                    frameCoarseX = newX;
                    frameCoarseZ = newZ;
                } else {
                    frameFineX = newX;
                    frameFineZ = newZ;
                }
                ++lineNumber;
                continue;
            }
            Integer motionOnLine = this.readGCodeMotion(line);
            if (motionOnLine != null && motionOnLine >= 0 && motionOnLine <= 3) {
                motionMode = motionOnLine;
            }
            Double arcRadius = this.readNamedValue(line, "CR", rVariables);
            // Центр дуги может задаваться смещениями от начальной точки: I по X и K по Z
            // (в осях X-Y второй координатой служит J). Раньше такие дуги распознавались
            // только по CR, а с I/K рисовались прямой линией.
            Double arcOffsetI = this.readAxisValue(line, 'I', rVariables);
            Double arcOffsetSecond = this.readAxisValue(
                    line, this.axisMode == AxisMode.XY ? 'J' : 'K', rVariables);
            Double lineAngle = this.readNamedValue(line, "ANG", rVariables);
            Double roundRadius = this.readNamedValue(line, "RND", rVariables);
            if (roundRadius != null && roundRadius > 0.0) {
                rndByLine.put(sourceLine, roundRadius);
            }
            boolean hasCoordinates = xValue != null || zValue != null;
            // «G54 X20 Z-10» у Siemens означает выбрать смещение И поехать по модальному
            // режиму, а не записать значения в таблицу. Раньше такой кадр считался
            // «только смещением», и ход из траектории пропадал. Значения таблицы
            // задаются в настройках вручную, из программы они не читаются.
            int effectiveMotion = motionOnLine != null ? motionOnLine : motionMode;
            boolean isMove = hasCoordinates
                    && effectiveMotion >= 0
                    && effectiveMotion <= 3;
            if (!isMove) {
                ++lineNumber;
                continue;
            }
            double targetX = posX;
            double targetZ = posZ;
            // IC(...) — размер от текущей точки, даже когда действует G90.
            double frameShiftX = frameCoarseX + frameFineX;
            double frameShiftZ = frameCoarseZ + frameFineZ;
            if (xValue != null) {
                if (xTerm.incremental()) {
                    targetX = posX + xValue;
                } else if (absoluteMode) {
                    targetX = xValue + g92ShiftX + frameShiftX;
                } else {
                    targetX = posX + xValue + g92ShiftX;
                }
            }
            if (zValue != null) {
                if (zTerm.incremental()) {
                    targetZ = posZ + zValue;
                } else if (absoluteMode) {
                    targetZ = zValue + g92ShiftZ + frameShiftZ;
                } else {
                    targetZ = posZ + zValue + g92ShiftZ;
                }
            }
            if (effectiveMotion == 1 && lineAngle != null && (xValue == null || zValue == null)) {
                double[] angTarget = this.applySiemensAngTarget(
                        posX,
                        posZ,
                        targetX,
                        targetZ,
                        xValue != null,
                        zValue != null,
                        lineAngle,
                        latheDiameterGeometryMode);
                targetX = angTarget[0];
                targetZ = angTarget[1];
            }
            if (reentryXUnknown || reentryZUnknown) {
                if (xValue!=null && absoluteMode && !xTerm.incremental()) reentryXUnknown=false;
                if (zValue!=null && absoluteMode && !zTerm.incremental()) reentryZUnknown=false;
                posX=targetX; posZ=targetZ;
                toolPositionKnown=!(reentryXUnknown||reentryZUnknown);
                ++lineNumber;
                continue;
            }
            Double effectiveArcRadius = null;
            if (arcRadius != null) {
                effectiveArcRadius = Math.abs(arcRadius);
            } else if (arcOffsetI != null || arcOffsetSecond != null) {
                double offsetI = arcOffsetI != null ? arcOffsetI : 0.0;
                double offsetK = arcOffsetSecond != null ? arcOffsetSecond : 0.0;
                double fromCenter = Math.hypot(offsetI, offsetK);
                if (fromCenter > 1.0E-9) {
                    effectiveArcRadius = fromCenter;
                }
            }
            if ((effectiveMotion == 2 || effectiveMotion == 3) && effectiveArcRadius != null) {
                // В осях X-Y экранные оси (X-гориз., Y-верт.) поменяны местами относительно
                // того, как addArcMoves считает дугу (Z-гориз., X-верт.), поэтому направление
                // G2/G3 на экране переворачивается. Компенсируем XOR-ом по режиму X-Y.
                boolean clockwise = (effectiveMotion == 2) != (this.axisMode == AxisMode.XY);
                this.addArcMoves(
                        arrayList,
                        posX,
                        posZ,
                        targetX,
                        targetZ,
                        effectiveArcRadius,
                        clockwise,
                        sourceLine,
                        latheDiameterGeometryMode);
            } else if (!toolPositionKnown && effectiveMotion == 0) {
                // Первый кадр программы — быстрый подвод: откуда он идёт, программа не
                // говорит. Просто встаём в точку, ход не рисуем.
                posX = targetX;
                posZ = targetZ;
                toolPositionKnown = true;
                ++lineNumber;
                continue;
            } else {
                arrayList.add(new ToolMove(posX, posZ, targetX, targetZ, effectiveMotion == 0, sourceLine));
            }
            toolPositionKnown = true;
            posX = targetX;
            posZ = targetZ;
            ++lineNumber;
        }
        this.programRadiusMode = radiusMode;
        Platform.runLater(this::updateActiveWorkOffsetLabel);
        return this.applySiemensRounding(arrayList, rndByLine, latheDiameterGeometryMode);
    }

    private int resolveWorkOffsetCodeForGraph() {
        if (this.activeWorkOffsetFromProgram >= 54) {
            return this.activeWorkOffsetFromProgram;
        }
        if (this.activeWorkOffsetFromProgram == 500) {
            return 54;
        }
        for (int code = 54; code <= 73; code++) {
            WorkOffset offset = this.readWorkOffsets().get(code);
            if (offset != null && (Math.abs(offset.x) > 1.0E-6 || Math.abs(offset.z) > 1.0E-6)) {
                return code;
            }
        }
        return 54;
    }

    /**
     * Siemens RND: скругление угла между текущим линейным кадром и следующим линейным кадром.
     * ToolMove хранит X как диаметр; геометрия угла считается в радиусной плоскости токарки.
     */
    private List<ToolMove> applySiemensRounding(
            List<ToolMove> moves,
            Map<Integer, Double> rndByLine,
            boolean latheDiameterGeometryMode
    ) {
        if (moves == null || moves.size() < 2 || rndByLine == null || rndByLine.isEmpty()) {
            return moves == null ? List.of() : moves;
        }
        ArrayList<ToolMove> result = new ArrayList<>(moves);
        for (int i = 0; i < result.size() - 1; i++) {
            ToolMove first = result.get(i);
            Double rnd = rndByLine.get(first.sourceLine);
            if (rnd == null || rnd <= 0.0) {
                continue;
            }
            ToolMove second = result.get(i + 1);
            List<ToolMove> rounded = this.buildSiemensRndCorner(first, second, rnd, latheDiameterGeometryMode);
            if (rounded.isEmpty()) {
                continue;
            }
            result.set(i, rounded.get(0));
            result.set(i + 1, rounded.get(rounded.size() - 1));
            if (rounded.size() > 2) {
                result.addAll(i + 1, rounded.subList(1, rounded.size() - 1));
            }
        }
        return result;
    }

    private List<ToolMove> buildSiemensRndCorner(
            ToolMove first,
            ToolMove second,
            double rndRadius,
            boolean latheDiameterGeometryMode
    ) {
        if (first == null || second == null || first.rapid || second.rapid
                || first.arcSegment || second.arcSegment) {
            return List.of();
        }
        double xScale = latheDiameterGeometryMode ? 0.5 : 1.0;
        double firstStartX = first.startX * xScale;
        double firstEndX = first.endX * xScale;
        double secondStartX = second.startX * xScale;
        double secondEndX = second.endX * xScale;
        double gap = Math.hypot(secondStartX - firstEndX, second.startZ - first.endZ);
        if (gap > 0.02) {
            return List.of();
        }
        double len1 = Math.hypot(firstEndX - firstStartX, first.endZ - first.startZ);
        double len2 = Math.hypot(secondEndX - secondStartX, second.endZ - second.startZ);
        if (len1 < 0.05 || len2 < 0.05) {
            return List.of();
        }
        double ux1 = (firstEndX - firstStartX) / len1;
        double uz1 = (first.endZ - first.startZ) / len1;
        double ux2 = (secondEndX - secondStartX) / len2;
        double uz2 = (second.endZ - second.startZ) / len2;
        double dot = Math.max(-1.0, Math.min(1.0, ux1 * ux2 + uz1 * uz2));
        double turn = Math.acos(dot);
        if (turn < Math.toRadians(2.0) || Math.abs(Math.PI - turn) < Math.toRadians(2.0)) {
            return List.of();
        }
        double tangent = rndRadius / Math.tan(turn / 2.0);
        double maxTangent = Math.min(len1, len2) * 0.45;
        if (tangent > maxTangent) {
            tangent = maxTangent;
        }
        if (tangent < 0.02) {
            return List.of();
        }
        double effectiveRadius = tangent * Math.tan(turn / 2.0);
        double cornerX = firstEndX;
        double cornerZ = first.endZ;
        double p1x = cornerX - ux1 * tangent;
        double p1z = cornerZ - uz1 * tangent;
        double p2x = cornerX + ux2 * tangent;
        double p2z = cornerZ + uz2 * tangent;
        double bisX = -ux1 + ux2;
        double bisZ = -uz1 + uz2;
        double bisLen = Math.hypot(bisX, bisZ);
        if (bisLen < 1.0e-6) {
            return List.of();
        }
        bisX /= bisLen;
        bisZ /= bisLen;
        double centerDistance = effectiveRadius / Math.sin(turn / 2.0);
        double cx = cornerX + bisX * centerDistance;
        double cz = cornerZ + bisZ * centerDistance;
        double startAngle = Math.atan2(p1z - cz, p1x - cx);
        double endAngle = Math.atan2(p2z - cz, p2x - cx);
        double sweep = endAngle - startAngle;
        double cross = ux1 * uz2 - uz1 * ux2;
        if (cross > 0.0 && sweep < 0.0) {
            sweep += Math.PI * 2.0;
        } else if (cross < 0.0 && sweep > 0.0) {
            sweep -= Math.PI * 2.0;
        }
        if (Math.abs(sweep) > Math.PI) {
            sweep += sweep > 0.0 ? -Math.PI * 2.0 : Math.PI * 2.0;
        }
        int segments = Math.max(4, Math.min(48, (int)Math.ceil(Math.abs(sweep) * effectiveRadius / 0.75)));
        ArrayList<ToolMove> rounded = new ArrayList<>();
        rounded.add(new ToolMove(
                first.startX,
                first.startZ,
                p1x / xScale,
                p1z,
                false,
                first.sourceLine,
                false,
                false));
        double prevX = p1x / xScale;
        double prevZ = p1z;
        for (int s = 1; s <= segments; s++) {
            double angle = startAngle + sweep * (double)s / (double)segments;
            double x = cx + Math.cos(angle) * effectiveRadius;
            double z = cz + Math.sin(angle) * effectiveRadius;
            double nextX = x / xScale;
            rounded.add(new ToolMove(prevX, prevZ, nextX, z, false, first.sourceLine, true, s == segments));
            prevX = nextX;
            prevZ = z;
        }
        rounded.add(new ToolMove(
                p2x / xScale,
                p2z,
                second.endX,
                second.endZ,
                false,
                second.sourceLine,
                false,
                false));
        return rounded;
    }

    private boolean lineContainsCode(String line, String code) {
        if (line == null || code == null || code.isBlank()) {
            return false;
        }
        for (String token : line.trim().split("\\s+")) {
            if (token.equals(code) || token.equals(code + ".0")) {
                return true;
            }
            if (token.startsWith(code) && token.length() > code.length() && Character.isDigit(token.charAt(code.length()))) {
                continue;
            }
            if (token.matches("G\\d+")) {
                try {
                    int value = Integer.parseInt(token.substring(1));
                    int expected = Integer.parseInt(code.substring(1));
                    if (value == expected) {
                        return true;
                    }
                }
                catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private void focusGraphOnProgramPoint(int line, ToolMove move, int moveIndex) {
        if (this.drawingCanvas == null || move == null || moveIndex < 0 || !this.canSelectGraphPoint()) {
            return;
        }
        this.graphEditorSelectionIndex = moveIndex;
        this.highlightedProgramLine = line;
        this.highlightedProgramMove = move;
        this.syncProgramHighlight();
        this.selectProgramLineExact(line);
        this.syncEditorHighlightFromGraph(line);
        this.panGraphToToolMove(move);
        this.redrawCanvas();
    }

    private void panGraphToToolMove(ToolMove move) {
        if (this.drawingCanvas == null || move == null) {
            return;
        }
        double width = Math.max(1.0, this.drawingCanvas.getWidth());
        double height = Math.max(1.0, this.drawingCanvas.getHeight());
        if (!this.computeProgramBoundsLayout(width, height)) {
            return;
        }
        double screenX = this.toScreenX(
                this.previewLayoutOriginX,
                move.endX,
                move.endZ,
                this.previewLayoutMinZ,
                this.previewLayoutScale);
        double screenY = this.toScreenY(
                this.previewLayoutOriginY,
                move.endX,
                move.endZ,
                this.previewLayoutMaxX,
                this.previewLayoutScale);
        this.translateX += width / 2.0 - screenX;
        this.translateY += height / 2.0 - screenY;
        this.applyPreviewLayoutBounds(
                this.previewLayoutMinX,
                this.previewLayoutMaxX,
                this.previewLayoutMinZ,
                this.previewLayoutMaxZ,
                width,
                height);
    }

    /**
     * Карусельный станок: ось детали стоит вертикально.
     *
     * <p>На таком станке деталь лежит на планшайбе, и стойка рисует её так же:
     * продольная ось (Z, а у канала на U/W — W) идёт по вертикали, радиус — вправо.
     * В горизонтальной токарке наоборот, поэтому один и тот же график нельзя
     * рисовать обоим станкам.
     */
    private boolean carouselGraph() {
        return this.axisMode == AxisMode.XZ && this.verticalLatheGraph;
    }

    /** Откладывается ли X (диаметр) по вертикальной оси: это горизонтальная токарка. */
    private boolean diameterAxisVertical() {
        return this.axisMode == AxisMode.XZ && !this.carouselGraph();
    }

    /** Откладывается ли X (диаметр) по горизонтальной оси: это карусельный станок. */
    private boolean diameterAxisHorizontal() {
        return this.carouselGraph();
    }

    /** Тип станка для графика читается из настроек и запоминается на отрисовку. */
    private void refreshGraphMachineOrientation() {
        this.verticalLatheGraph = this.buildSavedMachineConfiguration().isVerticalLathe();
    }

    /** Буква радиальной оси активной программы: у канала на U/W это U. */
    private char radialProgramAxisLetter() {
        String program = this.appliedProgramText != null ? this.appliedProgramText : "";
        return ProgramFileRole.detectAxisLetters(program)[0];
    }

    /** Буква продольной оси: у канала на U/W это W, иначе Z (или Y в режиме X-Y). */
    private char axialProgramAxisLetter() {
        return this.radialProgramAxisLetter() == 'U' ? 'W' : this.verticalProgramAxis();
    }

    private double pathHorizontal(double d, double d2) {
        if (this.carouselGraph()) {
            // Знак сохраняем: он говорит, с какой стороны оси идёт этот суппорт.
            return d * 0.5;
        }
        if (this.axisMode == AxisMode.STANDARD) {
            return d2;
        }
        if (this.axisMode == AxisMode.XZ) {
            return d2;
        }
        return d;
    }

    private double pathVertical(double d, double d2) {
        if (this.carouselGraph()) {
            return d2;
        }
        if (this.axisMode == AxisMode.XZ) {
            return LatheMeshBuilder.toRadius(d);
        }
        if (this.axisMode == AxisMode.XY) {
            return d2;
        }
        return d;
    }

    private double toScreenX(double d, double d2, double d3, double d4, double d5) {
        return d + (this.pathHorizontal(d2, d3) - d4) * d5;
    }

    private double toScreenY(double d, double d2, double d3, double d4, double d5) {
        return d + (d4 - this.pathVertical(d2, d3)) * d5;
    }

    private char verticalProgramAxis() {
        return this.axisMode == AxisMode.XY ? 'Y' : 'Z';
    }

    private void addArcMoves(
            List<ToolMove> list,
            double d,
            double d2,
            double d3,
            double d4,
            double d5,
            boolean bl,
            int n2,
            boolean latheDiameterArcMode
    ) {
        double xScale = latheDiameterArcMode ? 0.5 : 1.0;
        double d6 = d2;
        double d7 = d * xScale;
        double d8 = d4;
        double d9 = d3 * xScale;
        double d10 = d8 - d6;
        double d11 = d9 - d7;
        double d12 = Math.hypot(d10, d11);
        if (d5 <= 0.0 || d12 <= 0.000001 || d12 > d5 * 2.0 + 0.0001) {
            list.add(new ToolMove(d, d2, d3, d4, false, n2));
            return;
        }
        double d13 = (d6 + d8) / 2.0;
        double d14 = (d7 + d9) / 2.0;
        double d15 = Math.sqrt(Math.max(0.0, d5 * d5 - d12 * d12 / 4.0));
        double d16 = -d11 / d12;
        double d17 = d10 / d12;
        double d18 = d13 + d16 * d15;
        double d19 = d14 + d17 * d15;
        double d20 = d13 - d16 * d15;
        double d21 = d14 - d17 * d15;
        double[] dArray = this.selectArcCenter(d6, d7, d8, d9, d18, d19, d20, d21, bl);
        double d22 = dArray[0];
        double d23 = dArray[1];
        double d24 = Math.atan2(d7 - d23, d6 - d22);
        double d25 = Math.atan2(d9 - d23, d8 - d22);
        double d26 = d25 - d24;
        if (bl && d26 > 0.0) {
            d26 -= Math.PI * 2.0;
        } else if (!bl && d26 < 0.0) {
            d26 += Math.PI * 2.0;
        }
        int n = Math.max(8, Math.min(96, (int)Math.ceil(Math.abs(d26) * d5 / 2.0)));
        double d27 = d;
        double d28 = d2;
        for (int i = 1; i <= n; ++i) {
            double d29 = d24 + d26 * (double)i / (double)n;
            double d30 = d23 + Math.sin(d29) * d5;
            double d31 = d22 + Math.cos(d29) * d5;
            double nextX = d30 / xScale;
            list.add(new ToolMove(d27, d28, nextX, d31, false, n2, true, i == n));
            d27 = nextX;
            d28 = d31;
        }
    }

    private double[] selectArcCenter(double d, double d2, double d3, double d4, double d5, double d6, double d7, double d8, boolean bl) {
        double d9 = this.arcSweep(d, d2, d3, d4, d5, d6, bl);
        double d10 = this.arcSweep(d, d2, d3, d4, d7, d8, bl);
        return Math.abs(d9) <= Math.abs(d10) ? new double[]{d5, d6} : new double[]{d7, d8};
    }

    private double arcSweep(double d, double d2, double d3, double d4, double d5, double d6, boolean bl) {
        double d7 = Math.atan2(d2 - d6, d - d5);
        double d8 = Math.atan2(d4 - d6, d3 - d5);
        double d9 = d8 - d7;
        if (bl && d9 > 0.0) {
            d9 -= Math.PI * 2.0;
        } else if (!bl && d9 < 0.0) {
            d9 += Math.PI * 2.0;
        }
        return d9;
    }

    private String stripGCodeComment(String string) {
        if (string == null) {
            return "";
        }
        String trimmed = string.trim();
        Object object = trimmed;
        if (trimmed.startsWith(";N") || trimmed.startsWith(";n")) {
            object = trimmed.substring(1).trim();
        } else {
            int n = trimmed.indexOf(';');
            if (n >= 0) {
                object = trimmed.substring(0, n).trim();
            }
        }
        while (((String)object).contains("(") && ((String)object).contains(")") && ((String)object).indexOf(40) < ((String)object).indexOf(41)) {
            int n2 = ((String)object).indexOf(40);
            int n3 = ((String)object).indexOf(41, n2);
            object = ((String)object).substring(0, n2) + ((String)object).substring(n3 + 1);
        }
        return ((String)object).trim();
    }

    private Integer readGCodeMotion(String string) {
        String[] stringArray;
        for (String string2 : stringArray = string.trim().split("\\s+")) {
            if (string2.equals("G0") || string2.equals("G00")) {
                return 0;
            }
            if (string2.equals("G1") || string2.equals("G01")) {
                return 1;
            }
            if (string2.equals("G2") || string2.equals("G02")) {
                return 2;
            }
            if (!string2.equals("G3") && !string2.equals("G03")) continue;
            return 3;
        }
        return null;
    }

    private Integer readWorkOffsetCode(String string) {
        Integer last = null;
        for (String token : string.trim().split("\\s+")) {
            if (!token.matches("G\\d+")) {
                continue;
            }
            try {
                int n = Integer.parseInt(token.substring(1));
                if (this.isMachineFrameCode(n) || this.isPartFrameCode(n)) {
                    last = n;
                }
            }
            catch (NumberFormatException ignored) {
                return last;
            }
        }
        return last;
    }

    private Double readAxisValue(String string, char c) {
        return this.readAxisValue(string, c, Map.of());
    }

    private Double readAxisValue(String string, char c, Map<String, Double> rVariables) {
        AxisTerm term = this.readAxisTerm(string, c, rVariables);
        return term == null ? null : term.value();
    }

    /** Программа параметров из окна «Параметры»: разбирается один раз на текст. */
    private MachineParameterProgram.Variables parameterVariables() {
        String text = UserSettings.getParameterProgramText();
        if (text == null || text.isBlank()) {
            this.parameterProgramCacheKey = null;
            this.parameterProgramCache = MachineParameterProgram.Variables.empty();
            return this.parameterProgramCache;
        }
        if (!text.equals(this.parameterProgramCacheKey) || this.parameterProgramCache == null) {
            this.parameterProgramCacheKey = text;
            this.parameterProgramCache = MachineParameterProgram.read(text);
        }
        return this.parameterProgramCache;
    }

    /** Метка перехода: «TORCOVKA_STUPICY_2:». Иначе {@code null}. */
    private String programLabelOnLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon != trimmed.length() - 1) {
            return null;
        }
        String name = trimmed.substring(0, colon).trim();
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            return null;
        }
        for (int i = 1; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return null;
            }
        }
        return name;
    }

    /**
     * Безусловный переход вперёд: «GOTOF END_KANAVKA».
     *
     * <p>Условный переход ({@code IF ... GOTOF}) не трогаем: какая ветка выполнится,
     * зависит от станка, и угадывать хуже, чем показать программу целиком.
     */
    private String forwardJumpTarget(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.regionMatches(true, 0, "GOTOF", 0, 5)) {
            return null;
        }
        String target = trimmed.substring(5).trim();
        int space = target.indexOf(' ');
        if (space > 0) {
            target = target.substring(0, space);
        }
        return target.isEmpty() ? null : target;
    }

    /**
     * Значение оси в кадре и признак инкрементного размера.
     *
     * <p>{@code IC(...)} у Siemens означает «от текущей точки». Раньше такой кадр
     * читался как абсолютный, и {@code X=IC($P_TOOLR*(-1))} уводил инструмент к нулю
     * через всю деталь.
     */
    private record AxisTerm(double value, boolean incremental) {
    }

    private AxisTerm readAxisTerm(String string, char c, Map<String, Double> rVariables) {
        int start = GCodeProgramParser.indexOfAxisToken(string, c);
        if (start < 0) {
            return null;
        }
        int index = start + 1;
        // Имена (X=TREAD_DIAM+30) допустимы только в форме со знаком равенства: без него
        // «X100Z-50» прочиталось бы как одно выражение и ход бы потерялся.
        boolean named = index < string.length() && string.charAt(index) == '=';
        if (named) {
            index++;
        }
        int end = index;
        while (end < string.length()) {
            char ch = string.charAt(end);
            // Операторы и скобки — часть выражения (X=R1*2, X=(R1+5)/2).
            // Без них чтение обрывалось перед «*», и множитель молча терялся.
            if (Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.' || ch == ','
                    || ch == '*' || ch == '/' || ch == '(' || ch == ')'
                    || ch == 'R' || ch == 'r') {
                end++;
                continue;
            }
            if (named && (Character.isLetter(ch) || ch == '_' || ch == '$'
                    || ch == '[' || ch == ']')) {
                end++;
                continue;
            }
            break;
        }
        if (end == index) {
            return null;
        }
        String expression = string.substring(index, end);
        boolean incremental = expression.length() > 3
                && expression.regionMatches(true, 0, "IC(", 0, 3);
        java.util.Set<String> unknown = new java.util.LinkedHashSet<>();
        Double value = GCodeExpressionEvaluator.evaluate(expression, rVariables, unknown);
        if (value == null) {
            return null;
        }
        // Координата от неизвестной машинной переменной — не координата. Позиции
        // смены инструмента заданы данными станка (N_GANTRYPOS_X), их у нас
        // нет; посчитав их нулём, график тянул красную линию через всю деталь в ноль.
        // R-параметры оставляем как были: неизвестный R по-прежнему считается нулём.
        for (String name : unknown) {
            if (!name.matches("R\\d+")) {
                return null;
            }
        }
        return new AxisTerm(value, incremental);
    }

    private int indexOfAxisToken(String string, char c) {
        for (int i = 0; i < string.length(); ++i) {
            if (string.charAt(i) != c) continue;
            if (i > 0 && Character.isLetterOrDigit(string.charAt(i - 1))) {
                continue;
            }
            if (i + 1 < string.length() && (string.charAt(i + 1) == '=' || string.charAt(i + 1) == '+' || string.charAt(i + 1) == '-' || string.charAt(i + 1) == '.' || Character.isDigit(string.charAt(i + 1)))) {
                return i;
            }
        }
        return -1;
    }

    private Double readNamedValue(String string, String string2) {
        return this.readNamedValue(string, string2, Map.of());
    }

    private Double readNamedValue(String string, String name, Map<String, Double> rVariables) {
        int index = string.indexOf(name + "=");
        if (index < 0) {
            return null;
        }
        int start = index + name.length() + 1;
        int end = start;
        while (end < string.length()) {
            char ch = string.charAt(end);
            // Операторы и скобки — часть выражения (X=R1*2, X=(R1+5)/2).
            // Без них чтение обрывалось перед «*», и множитель молча терялся.
            // Имена — тоже: OFFN=ALLOWANCE_A+ALLOWANCE_B.
            if (Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.' || ch == ','
                    || ch == '*' || ch == '/' || ch == '(' || ch == ')'
                    || Character.isLetter(ch) || ch == '_' || ch == '$'
                    || ch == '[' || ch == ']') {
                end++;
                continue;
            }
            break;
        }
        return GCodeExpressionEvaluator.evaluate(string.substring(start, end), rVariables);
    }

    /**
     * Siemens ANG для линейного кадра с одной заданной координатой.
     * ToolMove хранит X как диаметр; угол токарки считаем в радиусной плоскости Z/X.
     * Это приближение нужно, чтобы фаски/конусы ANG=45/135 не превращались в прямые ступени.
     */
    private double[] applySiemensAngTarget(
            double startX,
            double startZ,
            double targetX,
            double targetZ,
            boolean hasX,
            boolean hasZ,
            double angleDeg,
            boolean latheDiameterGeometryMode
    ) {
        if (hasX == hasZ) {
            return new double[]{targetX, targetZ};
        }
        double radians = Math.toRadians(angleDeg);
        double tan = Math.tan(radians);
        if (!Double.isFinite(tan) || Math.abs(tan) < 1.0e-6) {
            return new double[]{targetX, targetZ};
        }
        double xScale = latheDiameterGeometryMode ? 0.5 : 1.0;
        double startGeometryX = startX * xScale;
        double targetGeometryX = targetX * xScale;
        if (hasX) {
            double dx = targetGeometryX - startGeometryX;
            double dz = dx / tan;
            return new double[]{targetX, startZ + dz};
        }
        double dz = targetZ - startZ;
        double dx = dz * tan;
        return new double[]{(startGeometryX + dx) / xScale, targetZ};
    }

    private void applyCanvasTransformations() {
        if (this.canvasGroup != null) {
            this.canvasGroup.setScaleX(1.0);
            this.canvasGroup.setScaleY(1.0);
            this.canvasGroup.setTranslateX(0.0);
            this.canvasGroup.setTranslateY(0.0);
        }
    }

    private File ensureMpfFile(File file) {
        String name = file.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(MPF_EXTENSION)) {
            return file;
        }
        return new File(file.getParentFile(), name + MPF_EXTENSION);
    }

    private void saveProgramAsync(File file) {
        CompletableFuture.runAsync(() -> {
            try {
                String string = this.programEditor != null ? this.programEditor.getText() : "";
                ProgramTextFile.write(file.toPath(), string, this.programFileCharset);
                Platform.runLater(() -> this.showInfoDialog(I18n.text("app.175"), I18n.text("app.176") + file.getName()));
            }
            catch (IOException iOException) {
                Platform.runLater(() -> this.showErrorDialog(I18n.text("app.177"), I18n.text("app.178") + iOException.getMessage()));
            }
        }, EXECUTOR);
    }

    private void loadProgramAsync(File file) {
        CompletableFuture.runAsync(() -> {
            try {
                ProgramTextFile.Content content = ProgramTextFile.read(file.toPath());
                String string = content.text();
                this.programFileCharset = content.charset();
                Platform.runLater(() -> {
                    this.applyingProgramText = true;
                    this.programEditor.setText(string);
                    this.applyingProgramText = false;
                    this.recordProgramHistoryIfChanged(this.appliedProgramText, string);
                    this.appliedProgramText = string;
                    this.saveLastProgramText(string);
                    this.syncTurningSettingsFromGCode(string);
                    this.syncAxisModeForProgram(string);
                    this.syncWorkOffsetsFromProgram(string);
                    this.programEditorEditingActive = false;
                    this.fullPreviewVisible = false;
                    this.toolpathGraphVisible = false;
                    this.trajectoryPreviewComplete = false;
                    this.programStartMarkerLine = -1;
                    this.selectedStartIndex = -1;
                    this.selectedMarkerLine = -1;
                    this.currentExecutionIndex = -1;
                    this.clearProgramHighlight();
                    this.syncProgramEditorVisuals();
                    this.textFrame.setText("Loaded: " + file.getName());
                    this.updateProgressDisplay(
                            0,
                            I18n.text("app.179")
                                    + file.getName()
                                    + I18n.text("app.180"));
                    this.resetCanvasView();
                    this.redrawCanvas();
                });
            }
            catch (IOException iOException) {
                Platform.runLater(() -> this.showErrorDialog("Load Error", "Could not load program: " + iOException.getMessage()));
            }
        }, EXECUTOR);
    }

    private String renameFrameNumbers(String string, int n, int n2) {
        String[] stringArray;
        StringBuilder stringBuilder = new StringBuilder();
        int n3 = n;
        for (String string2 : stringArray = string.split("\\R", -1)) {
            String trimmed = string2.trim();
            if (trimmed.startsWith(";") || trimmed.startsWith("(")) {
                stringBuilder.append(string2);
                stringBuilder.append(System.lineSeparator());
                continue;
            }
            String string3 = string2.replaceFirst("^\\s*N\\d+\\s*", "");
            if (string3.isBlank()) {
                stringBuilder.append(string2);
            } else {
                stringBuilder.append("N").append(n3).append(" ").append(string3);
                n3 += n2;
            }
            stringBuilder.append(System.lineSeparator());
        }
        return stringBuilder.toString();
    }

    private String convertAviaProgram(String string) {
        StringBuilder stringBuilder = new StringBuilder();
        String[] stringArray = string.split("\\R", -1);
        Pattern pattern = Pattern.compile("([XZ])\\s*([-+]?\\d+(?:[.,]\\d+)?)", 2);
        for (String string2 : stringArray) {
            String string3 = string2.trim();
            if (string3.isEmpty()) {
                stringBuilder.append(System.lineSeparator());
                continue;
            }
            string3 = string3.replace(',', '.').toUpperCase(Locale.US);
            string3 = string3.replaceAll("\\s+", " ");
            Matcher matcher = pattern.matcher(string3);
            StringBuffer stringBuffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(stringBuffer, matcher.group(1) + matcher.group(2));
            }
            matcher.appendTail(stringBuffer);
            stringBuilder.append(stringBuffer).append(System.lineSeparator());
        }
        return stringBuilder.toString();
    }

    private void printCanvas() {
        if (this.drawingCanvas == null) {
            this.showErrorDialog(I18n.text("app.181"), I18n.text("app.182"));
            return;
        }
        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            this.showErrorDialog(I18n.text("app.181"), I18n.text("app.183"));
            return;
        }
        boolean bl = printerJob.showPrintDialog(this.anchorPaneCanvas.getScene().getWindow());
        if (bl) {
            boolean bl2 = printerJob.printPage((Node)this.drawingCanvas);
            if (bl2) {
                printerJob.endJob();
                this.showInfoDialog(I18n.text("app.184"), I18n.text("app.185"));
            } else {
                this.showErrorDialog(I18n.text("app.181"), I18n.text("app.186"));
            }
        }
    }

    @FXML
    private void handleDragProgram(DragEvent dragEvent) {
        if (dragEvent.getDragboard().hasFiles()) {
            File file = (File)dragEvent.getDragboard().getFiles().get(0);
            CompletableFuture.runAsync(() -> {
                try {
                    ProgramTextFile.Content content = ProgramTextFile.read(file.toPath());
                    String string = content.text();
                    this.programFileCharset = content.charset();
                    Platform.runLater(() -> {
                        this.applyingProgramText = true;
                        this.programEditor.setText(string);
                        this.applyingProgramText = false;
                        this.recordProgramHistoryIfChanged(this.appliedProgramText, string);
                        this.appliedProgramText = string;
                        this.saveLastProgramText(string);
                        this.syncTurningSettingsFromGCode(string);
                        this.syncAxisModeForProgram(string);
                        this.syncWorkOffsetsFromProgram(string);
                        this.programEditorEditingActive = false;
                        this.fullPreviewVisible = false;
                        this.toolpathGraphVisible = false;
                        this.trajectoryPreviewComplete = false;
                        this.programStartMarkerLine = -1;
                        this.selectedStartIndex = -1;
                        this.selectedMarkerLine = -1;
                        this.currentExecutionIndex = -1;
                        this.clearProgramHighlight();
                        this.syncProgramEditorVisuals();
                        this.updateProgressDisplay(0, I18n.text("app.179") + file.getName() + I18n.text("app.187"));
                        this.redrawCanvas();
                    });
                }
                catch (IOException iOException) {
                    Platform.runLater(() -> this.showErrorDialog(I18n.text("app.188"), I18n.text("app.189") + iOException.getMessage()));
                }
            }, EXECUTOR);
            dragEvent.setDropCompleted(true);
        } else {
            dragEvent.setDropCompleted(false);
        }
        dragEvent.consume();
    }

    @FXML
    private void handleDragOverProgram(DragEvent dragEvent) {
        if (dragEvent.getDragboard().hasFiles()) {
            dragEvent.acceptTransferModes(new TransferMode[]{TransferMode.COPY});
        }
        dragEvent.consume();
    }

    @FXML
    private void onMouseClickedProgram(MouseEvent mouseEvent) {
        if (this.programEditor != null) {
            this.programEditor.requestFocus();
        }
    }

    private void selectProgramLineAsStart(int n) {
        if (this.programEditor == null) {
            return;
        }
        String cur = this.programEditor.getText();
        String prev = this.appliedProgramText != null ? this.appliedProgramText : "";
        this.recordProgramHistoryIfChanged(prev, cur);
        this.appliedProgramText = cur;
        this.saveLastProgramText(cur);
        List<ToolMove> list = this.parseGCodeMoves(this.appliedProgramText);
        if (!this.isValidStartMarkerCandidate(n, list)) {
            String string = this.explainMarkerRejectionForLine(n);
            this.updateProgressDisplay(this.progressPercent(list), string != null ? string : String.format(Locale.US, I18n.text("app.190"), n));
            return;
        }
        int n2 = list.isEmpty() ? -1 : this.findMoveIndexForMarkerLine(list, n);
        this.selectedStartIndex = n2;
        this.selectedMarkerLine = n;
        this.programStartMarkerLine = n;
        this.fullPreviewVisible = false;
        this.previewFromProgramHead = false;
        this.pathDisplayActive = false;
        this.pathDisplayStartIndex = n2 >= 0 ? n2 : this.firstFrameIndex(list, 0);
        this.currentExecutionIndex = -1;
        this.executionMoves = List.copyOf(list);
        this.cachedToolMoves = list;
        this.cachedToolMovesSource = this.toolMovesCacheKey();
        this.launchFocusPending = false;
        this.syncProgramEditorVisuals();
        this.updateProgressDisplay(0, String.format(Locale.US, I18n.text("app.191"), n));
        this.redrawCanvas();
    }

    private void clearProgramStartMarker() {
        List<ToolMove> list = this.parseGCodeMoves(this.programEditor != null ? this.programEditor.getText() : "");
        this.selectedStartIndex = -1;
        this.selectedMarkerLine = -1;
        this.programStartMarkerLine = -1;
        this.launchFocusPending = false;
        this.syncProgramEditorVisuals();
        this.updateProgressDisplay(this.progressPercent(list), this.currentExecutionIndex >= 0 ? I18n.text("app.192") : I18n.text("app.193"));
    }

    private void remapProgramStartMarker(List<ToolMove> list) {
        if (this.programStartMarkerLine <= 0) {
            this.selectedStartIndex = -1;
            return;
        }
        int n = this.resolveProgramMarkerLine(this.programStartMarkerLine, list);
        if (n <= 0) {
            this.programStartMarkerLine = -1;
            this.selectedStartIndex = -1;
            this.selectedMarkerLine = -1;
            return;
        }
        this.programStartMarkerLine = n;
        this.selectedMarkerLine = n;
        this.selectedStartIndex = list.isEmpty() ? -1 : this.findMoveIndexForMarkerLine(list, n);
    }

    private int resolveProgramMarkerLine(int n) {
        List<ToolMove> list = this.programEditor != null ? this.parseGCodeMoves(this.programEditor.getText()) : List.of();
        return this.resolveProgramMarkerLine(n, list);
    }

    private boolean isProgramMarkerableLine(String string) {
        if (string == null) {
            return false;
        }
        String normalized = this.normalizeProgramLineForParse(string);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            return false;
        }
        return this.isProgramFrameLine(normalized);
    }

    private int resolveProgramMarkerLine(int n, List<ToolMove> list) {
        if (this.programEditor == null || n <= 0) {
            return -1;
        }
        if (this.isValidStartMarkerCandidate(n, list)) {
            return n;
        }
        int n2 = this.getProgramLineCount();
        for (int i = 1; i <= n2; ++i) {
            int n3 = n + i;
            if (n3 <= n2 && this.isValidStartMarkerCandidate(n3, list)) {
                return n3;
            }
            int n4 = n - i;
            if (n4 >= 1 && this.isValidStartMarkerCandidate(n4, list)) {
                return n4;
            }
        }
        return -1;
    }

    private boolean isValidStartMarkerCandidate(int n, List<ToolMove> list) {
        if (!this.isProgramMarkerableLine(this.getProgramLineText(n))) {
            return false;
        }
        return list == null || list.isEmpty() || this.findMoveIndexForMarkerLine(list, n) >= 0;
    }

    private String explainMarkerRejectionForLine(int n) {
        if (this.programEditor == null || n <= 0) {
            return I18n.text("app.194");
        }
        String string = this.getProgramLineText(n);
        String string2 = string.trim();
        if (string2.isEmpty()) {
            return String.format(Locale.US, I18n.text("app.195"), n);
        }
        if (string2.startsWith(";")) {
            return String.format(Locale.US, I18n.text("app.196"), n);
        }
        if (string2.startsWith("(") && string2.endsWith(")")) {
            return String.format(Locale.US, I18n.text("app.197"), n);
        }
        String string3 = this.stripGCodeComment(string).trim();
        boolean bl = string.indexOf(59) >= 0;
        boolean bl2 = string.contains("(") && string.contains(")");
        if (string3.isEmpty()) {
            if (bl) {
                return String.format(Locale.US, I18n.text("app.198"), n);
            }
            if (bl2) {
                return String.format(Locale.US, I18n.text("app.199"), n);
            }
            return String.format(Locale.US, I18n.text("app.195"), n);
        }
        if (!this.isProgramFrameLine(string)) {
            return String.format(Locale.US, I18n.text("app.200"), n);
        }
        return String.format(Locale.US, I18n.text("app.201"), n);
    }

    private void drawPreviewChrome(GraphicsContext graphicsContext, double d, double d2) {
        double d3 = this.toScreenX(this.previewLayoutOriginX, 0.0, 0.0, this.previewLayoutMinZ, this.previewLayoutScale);
        double d4 = this.toScreenY(this.previewLayoutOriginY, 0.0, 0.0, this.previewLayoutMaxX, this.previewLayoutScale);
        double d5 = Math.max(1.0, this.previewLayoutMaxZ - this.previewLayoutMinZ);
        double d6 = Math.max(1.0, this.previewLayoutMaxX - this.previewLayoutMinX);
        this.drawWorkBounds(graphicsContext, this.previewLayoutOriginX, this.previewLayoutOriginY, d5 * this.previewLayoutScale, d6 * this.previewLayoutScale);
        this.drawAxes(graphicsContext, d, d2, d3, d4);
    }

    private double originViewHalfSpanMm() {
        List<ToolMove> list = this.getActiveToolMoves();
        if (list.isEmpty()) {
            return DEFAULT_ORIGIN_VIEW_HALF_SPAN_MM;
        }
        double d = MIN_ORIGIN_VIEW_HALF_SPAN_MM;
        for (ToolMove toolMove : list) {
            d = Math.max(d, Math.abs(this.pathHorizontal(toolMove.endX, toolMove.endZ)));
            d = Math.max(d, Math.abs(this.pathVertical(toolMove.endX, toolMove.endZ)));
            if (toolMove.rapid) {
                continue;
            }
            d = Math.max(d, Math.abs(this.pathHorizontal(toolMove.startX, toolMove.startZ)));
            d = Math.max(d, Math.abs(this.pathVertical(toolMove.startX, toolMove.startZ)));
        }
        return Math.max(MIN_ORIGIN_VIEW_HALF_SPAN_MM, d * 1.12);
    }

    private boolean computeProgramBoundsLayout(double canvasWidth, double canvasHeight) {
        return this.computeProgramBoundsLayout(canvasWidth, canvasHeight, this.getActiveToolMoves());
    }

    private boolean computeProgramBoundsLayout(double canvasWidth, double canvasHeight, List<ToolMove> moves) {
        this.previewLayoutValid = false;
        if (moves == null || moves.isEmpty()) {
            return false;
        }
        double minHorizontal = Double.POSITIVE_INFINITY;
        double maxHorizontal = Double.NEGATIVE_INFINITY;
        double minVertical = Double.POSITIVE_INFINITY;
        double maxVertical = Double.NEGATIVE_INFINITY;
        for (ToolMove move : moves) {
            for (double[] point : new double[][]{
                    {move.startX, move.startZ},
                    {move.endX, move.endZ}
            }) {
                double horizontal = this.pathHorizontal(point[0], point[1]);
                double vertical = this.pathVertical(point[0], point[1]);
                minHorizontal = Math.min(minHorizontal, horizontal);
                maxHorizontal = Math.max(maxHorizontal, horizontal);
                minVertical = Math.min(minVertical, vertical);
                maxVertical = Math.max(maxVertical, vertical);
            }
        }
        String programText = this.appliedProgramText != null ? this.appliedProgramText : "";
        Optional<WorkpieceDefinition> workpiece = GCodeProgramParser.findWorkpiece(programText);
        if (workpiece.isPresent() && workpiece.get().isValid()) {
            WorkpieceDefinition blank = this.workpieceForGraphDisplay(workpiece.get(), programText);
            if (this.carouselGraph()) {
                // Карусель: ось детали по вертикали, радиус в стороны. Когда каналы
                // разнесены, ось вращения идёт посередине вида, иначе — по левому краю.
                minVertical = Math.min(minVertical, blank.getZMin());
                maxVertical = Math.max(maxVertical, blank.getZMax());
                double radius = Math.max(Math.max(maxHorizontal, -minHorizontal),
                        blank.getStockRadiusMm() * 1.05);
                // Показываем только занятые стороны: пустая половина вида съедала
                // масштаб, и деталь выходила вдвое мельче.
                boolean leftSide = minHorizontal < -1.0e-9;
                boolean rightSide = maxHorizontal > 1.0e-9;
                minHorizontal = leftSide ? -radius : 0.0;
                maxHorizontal = rightSide ? radius : 0.0;
            } else {
                minHorizontal = Math.min(minHorizontal, blank.getZMin());
                maxHorizontal = Math.max(maxHorizontal, blank.getZMax());
                if (this.axisMode == AxisMode.XZ) {
                    // Низ вида — ось вращения. По вертикали здесь откладывается toRadius(X),
                    // а он не бывает отрицательным: половина поля под осью всегда пустая,
                    // и раньше она съедала половину масштаба — деталь выходила вдвое мельче.
                    minVertical = 0.0;
                    maxVertical = Math.max(maxVertical, blank.getStockRadiusMm() * 1.05);
                } else {
                    minVertical = Math.min(minVertical, 0.0);
                    maxVertical = Math.max(maxVertical, blank.getDiameterMm());
                }
            }
        } else if (this.carouselGraph()) {
            double radius = 50.0;
            boolean leftSide = minHorizontal < -1.0e-9;
            boolean rightSide = maxHorizontal > 1.0e-9;
            for (ToolMove move : moves) {
                radius = Math.max(radius, LatheMeshBuilder.toRadius(
                        Math.max(Math.abs(move.startX), Math.abs(move.endX))));
            }
            radius *= 1.05;
            minHorizontal = leftSide ? -radius : 0.0;
            maxHorizontal = rightSide ? radius : 0.0;
        } else if (this.axisMode == AxisMode.XZ) {
            double top = 50.0;
            for (ToolMove move : moves) {
                top = Math.max(top, LatheMeshBuilder.toRadius(Math.max(move.startX, move.endX)));
            }
            minVertical = 0.0;
            maxVertical = top * 1.05;
        }
        double padHorizontal = Math.max(40.0, (maxHorizontal - minHorizontal) * 0.06);
        double padVertical = Math.max(20.0, (maxVertical - minVertical) * 0.08);
        return this.applyPreviewLayoutBounds(
                minVertical - padVertical,
                maxVertical + padVertical,
                minHorizontal - padHorizontal,
                maxHorizontal + padHorizontal,
                canvasWidth,
                canvasHeight);
    }

    private void drawProgramValueGrid(GraphicsContext graphicsContext) {
        if (!this.previewLayoutValid) {
            return;
        }
        double canvasWidth = Math.max(1.0, this.drawingCanvas != null ? this.drawingCanvas.getWidth() : this.previewLayoutWidth);
        double canvasHeight = Math.max(1.0, this.drawingCanvas != null ? this.drawingCanvas.getHeight() : this.previewLayoutHeight);
        double[] visibleHorizontal = this.visiblePreviewHorizontalRange(canvasWidth);
        double[] visibleVertical = this.visiblePreviewVerticalRange(canvasHeight);
        double horizontalSpan = Math.max(GRAPH_GRID_MIN_STEP, visibleHorizontal[1] - visibleHorizontal[0]);
        double verticalSpan = Math.max(GRAPH_GRID_MIN_STEP, visibleVertical[1] - visibleVertical[0]);
        double hPixelsPerUnit = this.horizontalAxisPixelsPerUnit();
        double stepHorizontal = this.adaptGraphGridStep(
                this.niceGraphGridStep(horizontalSpan / 22.0), hPixelsPerUnit);
        // Вертикальная ось считается в единицах ПОДПИСИ (XØ-диаметр для токарки) — тех же,
        // что у X-траектории. Шаг и видимый диапазон в этих единицах, плотность пикселей
        // через verticalAxisPixelsPerUnit(): из-за этого шкала не «съезжает» и покрывает
        // весь видимый диапазон.
        double vPixelsPerUnit = this.verticalAxisPixelsPerUnit();
        double stepVertical = this.adaptGraphGridStep(
                this.niceGraphGridStep(verticalSpan / 22.0), vPixelsPerUnit);
        double horizontalLabelStep = this.adaptGraphLabelStep(stepHorizontal, hPixelsPerUnit);
        double verticalLabelStep = this.adaptGraphLabelStep(
                stepVertical, vPixelsPerUnit, GRAPH_GRID_VERTICAL_LABEL_SPACING_PX);
        Color minorGrid = Color.web(this.darkTheme ? "#1e293b" : "#e2e8f0");
        Color majorGrid = Color.web(this.darkTheme ? "#475569" : "#94a3b8");
        Color labelColor = Color.web(this.darkTheme ? "#f8fafc" : "#1e293b");
        graphicsContext.setLineWidth(1.0);
        graphicsContext.setFont(Font.font("Segoe UI", GRAPH_GRID_FONT));
        double zStart = Math.floor(visibleHorizontal[0] / stepHorizontal) * stepHorizontal;
        double zEnd = Math.ceil(visibleHorizontal[1] / stepHorizontal) * stepHorizontal;
        double lastZLabelX = Double.NaN;
        double labelBottom = Math.min(canvasHeight - 8.0, Math.max(18.0, canvasHeight - GRAPH_GRID_FONT * 0.45));
        for (double z = zStart; z <= zEnd + stepHorizontal * 0.5; z += stepHorizontal) {
            double x = this.horizontalValueToScreenX(z);
            boolean major = this.isGraphMajorGridLine(z, stepHorizontal);
            graphicsContext.setStroke((Paint)(major ? majorGrid : minorGrid));
            graphicsContext.strokeLine(x, 0.0, x, canvasHeight);
            if (this.isGraphLabelLine(z, horizontalLabelStep)
                    && this.shouldDrawGraphGridLabel(x, lastZLabelX)) {
                lastZLabelX = x;
                graphicsContext.setFill((Paint)labelColor);
                graphicsContext.fillText(
                        this.formatGraphGridValue(
                                this.axisDisplayValue(z, this.diameterAxisHorizontal()), horizontalLabelStep),
                        x + 4.0,
                        labelBottom);
                // Слева от оси диаметры те же, что справа: знак — это сторона, а не размер.
            }
        }
        double xStart = Math.floor(visibleVertical[0] / stepVertical) * stepVertical;
        double xEnd = Math.ceil(visibleVertical[1] / stepVertical) * stepVertical;
        for (double[] line : this.collectVerticalGridLines(
                xStart, xEnd, stepVertical, verticalLabelStep, canvasHeight)) {
            double y = line[1];
            graphicsContext.setStroke((Paint)(line[2] != 0.0 ? majorGrid : minorGrid));
            graphicsContext.strokeLine(0.0, y, canvasWidth, y);
            if (line[3] != 0.0) {
                graphicsContext.setFill((Paint)labelColor);
                // Подпись строго у своей линии (как в SinuTrainSideViewPainter).
                graphicsContext.fillText(
                        this.formatGraphGridValue(
                                this.axisDisplayValue(line[0], this.diameterAxisVertical()), verticalLabelStep),
                        12.0,
                        y + GRAPH_GRID_FONT * 0.12);
            }
        }
    }

    /**
     * Вертикальные линии сетки графика: {значение-оси, screenY, major(0/1), label(0/1)}.
     * value — значение вертикальной оси (XØ-диаметр для токарки): передаётся прямо в
     * {@link #toScreenY}, поэтому линия ложится ровно там же, где X-траектория, и шкала
     * не «съезжает». Линии за пределами канвы пропускаются, подпись — у самой линии.
     * Вынесено отдельно для модульной проверки.
     */
    private List<double[]> collectVerticalGridLines(
            double xStart,
            double xEnd,
            double stepVertical,
            double verticalLabelStep,
            double canvasHeight
    ) {
        ArrayList<double[]> out = new ArrayList<>();
        double lastLabelY = Double.NaN;
        for (double value = xStart; value <= xEnd + stepVertical * 0.5; value += stepVertical) {
            double y = this.verticalValueToScreenY(value);
            if (y < -2.0 || y > canvasHeight + 2.0) {
                continue;
            }
            boolean major = this.isGraphMajorGridLine(value, stepVertical);
            boolean showLabel = (!this.diameterAxisVertical() || value >= -verticalLabelStep * 0.25)
                    && this.isGraphLabelLine(value, verticalLabelStep)
                    && this.shouldDrawGraphGridLabel(y, lastLabelY, GRAPH_GRID_VERTICAL_LABEL_SPACING_PX);
            if (showLabel) {
                lastLabelY = y;
            }
            out.add(new double[]{value, y, major ? 1.0 : 0.0, showLabel ? 1.0 : 0.0});
        }
        return out;
    }

    private double[] visiblePreviewHorizontalRange(double canvasWidth) {
        double z0 = this.screenToPreviewHorizontal(0.0);
        double z1 = this.screenToPreviewHorizontal(canvasWidth);
        double min = Math.min(z0, z1);
        double max = Math.max(z0, z1);
        double pad = Math.max(GRAPH_GRID_MIN_STEP * 4.0, (max - min) * 0.04);
        return new double[]{min - pad, max + pad};
    }

    private double[] visiblePreviewVerticalRange(double canvasHeight) {
        double v0 = this.screenToPreviewVertical(0.0);
        double v1 = this.screenToPreviewVertical(canvasHeight);
        double min = Math.min(v0, v1);
        double max = Math.max(v0, v1);
        double pad = Math.max(GRAPH_GRID_MIN_STEP * 4.0, (max - min) * 0.05);
        return new double[]{min - pad, max + pad};
    }

    private double screenToPreviewHorizontal(double screenX) {
        double pathValue = this.previewLayoutMinZ
                + (screenX - this.previewLayoutOriginX) / Math.max(1.0e-9, this.previewLayoutScale);
        // У карусельного станка по горизонтали отложен радиус, а шкала — в диаметрах.
        return this.diameterAxisHorizontal() ? pathValue * 2.0 : pathValue;
    }

    /**
     * Значение вертикальной оси (XØ-диаметр для токарки) в точке screenY — точная инверсия
     * {@link #toScreenY}. previewLayoutMaxX и pathVertical работают в радиусе, поэтому для
     * XZ значение оси = радиус * 2 (диаметр). Иначе шкала считалась бы вдвое меньше и «съезжала».
     */
    private double screenToPreviewVertical(double screenY) {
        double pathValue = this.previewLayoutMaxX - (screenY - this.previewLayoutOriginY) / Math.max(1.0e-9, this.previewLayoutScale);
        return this.diameterAxisVertical() ? pathValue * 2.0 : pathValue;
    }

    /** Экранный X для значения горизонтальной оси сетки (обратное к screenToPreviewHorizontal,
     *  не зависит от слотов pathHorizontal — иначе в режиме X-Y все линии схлопывались в одну). */
    private double horizontalValueToScreenX(double value) {
        double geom = this.diameterAxisHorizontal() ? value * 0.5 : value;
        return this.previewLayoutOriginX + (geom - this.previewLayoutMinZ) * this.previewLayoutScale;
    }

    /** Экранных пикселей на единицу горизонтальной оси (для диаметра: scale/2). */
    private double horizontalAxisPixelsPerUnit() {
        return this.diameterAxisHorizontal() ? this.previewLayoutScale * 0.5 : this.previewLayoutScale;
    }

    /** Экранный Y для значения вертикальной оси сетки (обратное к screenToPreviewVertical). */
    private double verticalValueToScreenY(double value) {
        double geom = this.diameterAxisVertical() ? Math.abs(value) * 0.5 : value;
        return this.previewLayoutOriginY + (this.previewLayoutMaxX - geom) * this.previewLayoutScale;
    }

    /** Экранных пикселей на единицу вертикальной оси (для XZ диаметр: scale/2). */
    private double verticalAxisPixelsPerUnit() {
        return this.diameterAxisVertical() ? this.previewLayoutScale * 0.5 : this.previewLayoutScale;
    }

    private double adaptGraphGridStep(double step, double scale) {
        double candidate = Math.max(GRAPH_GRID_MIN_STEP, step);
        for (int attempt = 0; attempt < 16; attempt++) {
            if (Math.abs(candidate * scale) >= GRAPH_GRID_MIN_SPACING_PX) {
                return candidate;
            }
            candidate = this.niceGraphGridStep(candidate * 2.01);
        }
        return candidate;
    }

    private double adaptGraphLabelStep(double step, double scale) {
        return this.adaptGraphLabelStep(step, scale, GRAPH_GRID_MIN_LABEL_SPACING_PX);
    }

    private double adaptGraphLabelStep(double step, double scale, double minSpacingPx) {
        double candidate = Math.max(GRAPH_GRID_MIN_STEP, step);
        for (int attempt = 0; attempt < 16; attempt++) {
            if (Math.abs(candidate * scale) >= minSpacingPx) {
                return candidate;
            }
            candidate = this.niceGraphGridStep(candidate * 2.01);
        }
        return candidate;
    }

    private double niceGraphGridStep(double rawStep) {
        double raw = Math.max(GRAPH_GRID_MIN_STEP, Math.abs(rawStep));
        double exponent = Math.floor(Math.log10(raw));
        double magnitude = Math.pow(10.0, exponent);
        double fraction = raw / magnitude;
        double niceFraction;
        if (fraction <= 1.0) {
            niceFraction = 1.0;
        } else if (fraction <= 2.0) {
            niceFraction = 2.0;
        } else if (fraction <= 5.0) {
            niceFraction = 5.0;
        } else {
            niceFraction = 10.0;
        }
        return Math.max(GRAPH_GRID_MIN_STEP, niceFraction * magnitude);
    }

    private boolean isGraphMajorGridLine(double value, double step) {
        if (!Double.isFinite(value) || !Double.isFinite(step) || step <= 0.0) {
            return false;
        }
        double majorStep = step * 5.0;
        if (majorStep >= 500.0) {
            return true;
        }
        double normalized = value / majorStep;
        return Math.abs(normalized - Math.rint(normalized)) < 1.0e-6;
    }

    private boolean isGraphLabelLine(double value, double step) {
        if (!Double.isFinite(value) || !Double.isFinite(step) || step <= 0.0) {
            return false;
        }
        double normalized = value / step;
        return Math.abs(normalized - Math.rint(normalized)) < 1.0e-6;
    }

    private boolean shouldDrawGraphGridLabel(double coordinate, double lastCoordinate) {
        return this.shouldDrawGraphGridLabel(coordinate, lastCoordinate, GRAPH_GRID_MIN_LABEL_SPACING_PX);
    }

    private boolean shouldDrawGraphGridLabel(double coordinate, double lastCoordinate, double minSpacingPx) {
        if (!Double.isFinite(lastCoordinate)) {
            return true;
        }
        return Math.abs(coordinate - lastCoordinate) >= minSpacingPx;
    }

    private String formatGraphGridValue(double value, double step) {
        double normalized = Math.abs(value) < Math.max(1.0e-9, step) * 0.25 ? 0.0 : value;
        if (step < 0.0099) {
            return String.format(Locale.US, "%.3f", normalized);
        }
        if (step < 0.099) {
            return String.format(Locale.US, "%.2f", normalized);
        }
        if (step < 0.999) {
            return String.format(Locale.US, "%.1f", normalized);
        }
        return String.format(Locale.US, "%.0f", normalized);
    }

    private void panToCurrentToolPoint(List<ToolMove> list, double d, double d2) {
        if (list == null || list.isEmpty() || this.currentExecutionIndex < 0 || this.currentExecutionIndex >= list.size()) {
            return;
        }
        ToolMove toolMove = list.get(this.currentExecutionIndex);
        double d3 = this.toScreenX(this.previewLayoutOriginX, toolMove.endX, toolMove.endZ, this.previewLayoutMinZ, this.previewLayoutScale);
        double d4 = this.toScreenY(this.previewLayoutOriginY, toolMove.endX, toolMove.endZ, this.previewLayoutMaxX, this.previewLayoutScale);
        this.translateX += d / 2.0 - d3;
        this.translateY += d2 / 2.0 - d4;
        this.applyPreviewLayoutBounds(this.previewLayoutMinX, this.previewLayoutMaxX, this.previewLayoutMinZ, this.previewLayoutMaxZ, d, d2);
    }

    private double clampPreviewZoom(double zoom, double baseScale) {
        double maxZoom = MAX_CANVAS_ZOOM;
        if (Double.isFinite(baseScale) && baseScale > 1.0e-9) {
            maxZoom = Math.max(maxZoom, MAX_CANVAS_EFFECTIVE_SCALE / baseScale);
        }
        return Math.max(MIN_CANVAS_ZOOM, Math.min(maxZoom, zoom));
    }

    private boolean applyPreviewLayoutBounds(double d3, double d4, double d5, double d6, double d, double d2) {
        double marginSide = 88.0;
        double marginTop = 88.0;
        double marginBottom = 88.0;
        double d8 = Math.max(1.0, d6 - d5);
        double d9 = Math.max(1.0, d4 - d3);
        double plotW = Math.max(1.0, d - marginSide * 2.0);
        double plotH = Math.max(1.0, d2 - marginTop - marginBottom);
        double d10 = Math.min(plotW / d8, plotH / d9);
        if (!Double.isFinite(d10) || d10 <= 0.0) {
            d10 = 1.0;
        }
        this.currentZoom = this.clampPreviewZoom(this.currentZoom, d10);
        d10 *= this.currentZoom;
        this.previewLayoutOriginX = marginSide + (plotW - d8 * d10) / 2.0 + this.translateX;
        this.previewLayoutOriginY = marginTop + (plotH - d9 * d10) / 2.0 + this.translateY;
        this.previewLayoutScale = d10;
        this.previewLayoutMinX = d3;
        this.previewLayoutMinZ = d5;
        this.previewLayoutMaxX = d4;
        this.previewLayoutMaxZ = d6;
        this.previewLayoutWidth = d;
        this.previewLayoutHeight = d2;
        this.previewLayoutValid = true;
        return true;
    }

    private void showInfoDialog(String string, String string2) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(string);
        alert.setHeaderText(null);
        alert.setContentText(string2);
        alert.getDialogPane().getStyleClass().add("about-dialog");
        alert.setGraphic(null);
        this.applyDialogStyles(alert);
        this.setDialogIcon(alert);
        alert.showAndWait();
    }

    private void showErrorDialog(String string, String string2) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setGraphic(DialogIcons.cross(46.0));
        alert.setTitle(string);
        alert.setHeaderText(null);
        alert.setContentText(string2);
        this.applyDialogStyles(alert);
        this.setDialogIcon(alert);
        alert.showAndWait();
    }

    private void applyDialogStyles(Alert alert) {
        // Раньше здесь подключался только app.css: окна были светлыми и в тёмной
        // теме и открывались где попало, а не над главным окном.
        this.applyDialogTheme(alert);
    }

    private void setDialogIcon(Alert alert) {
        Stage stage = (Stage)alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(this.getClass().getResourceAsStream("/icon_16.png")));
    }

    public void cleanup() {
        this.stopSpaceSingleBlockRepeat();
        if (this.programEditor != null) {
            this.programEditor.dispose();
        }
        if (EXECUTOR != null && !EXECUTOR.isShutdown()) {
            EXECUTOR.shutdownNow();
        }
        com.sergey.pisarev.service.PrintService.shutdown();
    }

    private record SettingsDraft(
            Map<Integer, String[]> workOffsets,
            String blankDiameterText,
            boolean useBlankDiameter,
            String cupRadiusText,
            boolean useCupRadius,
            String workOffsetCountText,
            boolean equidistantEnabled,
            String equidistantRadiusSource,
            String equidistantRadiusText,
            String equidistantAllowanceText
    ) {
    }

    private enum HistoryRowType {
        HEADER,
        CHANGE,
        INFO
    }

    private static final class HistoryRow {
        private final HistoryRowType type;
        private final long timestamp;
        private final String oldLine;
        private final String newLine;
        private final String text;

        private HistoryRow(HistoryRowType type, long timestamp, String oldLine, String newLine, String text) {
            this.type = type;
            this.timestamp = timestamp;
            this.oldLine = oldLine;
            this.newLine = newLine;
            this.text = text;
        }

        private static HistoryRow header(String text, long timestamp) {
            return new HistoryRow(HistoryRowType.HEADER, timestamp, "", "", text);
        }

        private static HistoryRow change(long timestamp, String oldLine, String newLine) {
            return new HistoryRow(HistoryRowType.CHANGE, timestamp, oldLine, newLine, "");
        }

        private static HistoryRow info(String text) {
            return new HistoryRow(HistoryRowType.INFO, -1L, "", "", text);
        }
    }

    private static final class ToolMove {
        private final double startX;
        private final double startZ;
        private final double endX;
        private final double endZ;
        private final boolean rapid;
        private final int sourceLine;
        private final boolean arcSegment;
        private final boolean arcEnd;

        private ToolMove(double d, double d2, double d3, double d4, boolean bl, int n) {
            this(d, d2, d3, d4, bl, n, false, false);
        }

        private ToolMove(double d, double d2, double d3, double d4, boolean bl, int n, boolean bl2, boolean bl3) {
            this.startX = d;
            this.startZ = d2;
            this.endX = d3;
            this.endZ = d4;
            this.rapid = bl;
            this.sourceLine = n;
            this.arcSegment = bl2;
            this.arcEnd = bl3;
        }
    }

    private static final class WorkOffset {
        private final double x;
        private final double z;

        private WorkOffset(double d, double d2) {
            this.x = d;
            this.z = d2;
        }
    }

    private enum AxisMode {
        STANDARD,
        XY,
        XZ
    }

    private static class HoverPoint {
        private final String title;
        private final double modelX;
        private final double modelZ;
        private final double screenX;
        private final double screenY;
        private final int sourceLine;
        private final int moveIndex;

        private HoverPoint(String string, double d, double d2, double d3, double d4, int n) {
            this(string, d, d2, d3, d4, n, -1);
        }

        private HoverPoint(String string, double d, double d2, double d3, double d4, int n, int moveIndex) {
            this.title = string;
            this.modelX = d;
            this.modelZ = d2;
            this.screenX = d3;
            this.screenY = d4;
            this.sourceLine = n;
            this.moveIndex = moveIndex;
        }
    }

    // ------------------------------------------------------------------
    // Два канала: слева канал 2, справа канал 1
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Масштаб окна
    // ------------------------------------------------------------------

    /** Крупнее на шаг: кнопка «+» в строке меню. */
    @FXML
    private void onUiScaleIn() {
        UiScale.step(1);
        this.refreshUiScaleLabel();
    }

    /** Мельче на шаг: кнопка «−». */
    @FXML
    private void onUiScaleOut() {
        UiScale.step(-1);
        this.refreshUiScaleLabel();
    }

    /** Обратно в 100 %. */
    @FXML
    private void onUiScaleReset() {
        UiScale.reset();
        this.refreshUiScaleLabel();
    }

    private void refreshUiScaleLabel() {
        if (this.uiScaleLabel != null) {
            this.uiScaleLabel.setText(UiScale.label());
        }
    }

    /** Кнопка развёртывания правого блока кода. */
    @FXML
    private void menuToggleRightPane() {
        this.setRightPaneVisible(!this.rightPaneVisible);
    }

    private void setRightPaneVisible(boolean visible) {
        this.rightPaneVisible = visible;
        UserSettings.setRightPaneVisible(visible);
        if (!visible && this.rightPaneActive) {
            // Свернули окно исполняемого канала — работаем снова левым.
            this.setActivePane(false);
        }
        this.applyRightPaneLayout();
        this.moveSidebarDivider(visible);
    }

    /**
     * Доли окон.
     *
     * <p>Третье окно встаёт справа, а график остаётся посередине — как на стойке:
     * слева программа канала 2, в середине картинка, справа программа канала 1.
     */
    private void moveSidebarDivider(boolean expanded) {
        if (this.splitPane == null || this.splitPane.getDividers().isEmpty()) {
            return;
        }
        // Доли ставим после раскладки: заданные до неё JavaFX не применяет.
        //
        // Левую границу не трогаем: ширину левого окна держит панель поиска и кнопок
        // под ним, меньше своей ширины она не станет, и доля всё равно не послушается.
        // Правое окно задаём в пикселях от ширины: всё остальное достаётся графику.
        Runnable apply = () -> {
            if (expanded && this.splitPane.getDividers().size() >= 2) {
                double width = this.splitPane.getWidth();
                double share = width > 0.0 ? Math.min(0.4, 340.0 / width) : 0.24;
                double first = this.splitPane.getDividerPositions()[0];
                this.splitPane.setDividerPositions(first, 1.0 - share);
            } else if (!expanded) {
                this.splitPane.setDividerPositions(this.sidebarDividerBeforeExpand);
            }
        };
        apply.run();
        Platform.runLater(apply);
    }

    private void applyRightPaneLayout() {
        if (this.programEditorBox == null || this.leftProgramEditor == null) {
            return;
        }
        // Левое окно кода всегда одно: второе окно — отдельная область справа.
        Node left = this.leftProgramEditor.getNode();
        HBox.setHgrow(left, Priority.ALWAYS);
        this.programEditorBox.getChildren().setAll(left);
        this.installLeftPaneTitle(this.rightPaneVisible);
        this.installRightPaneArea(this.rightPaneVisible);
        if (this.rightPaneToggleButton != null) {
            this.rightPaneToggleButton.setText(I18n.text(
                    this.rightPaneVisible ? "panel.channel.collapse" : "panel.channel.expand"));
        }
        this.updateChannelPaneTitles();
    }

    /** Подпись канала над левым окном: нужна только когда каналов два. */
    private void installLeftPaneTitle(boolean show) {
        if (!(this.programEditorBox.getParent() instanceof VBox stack)) {
            return;
        }
        if (this.leftPaneTitleLabel != null) {
            stack.getChildren().remove(this.leftPaneTitleLabel);
        }
        if (!show) {
            this.leftPaneTitleLabel = null;
            return;
        }
        Label title = this.channelTitleLabel(false);
        this.leftPaneTitleLabel = title;
        stack.getChildren().add(0, title);
    }

    /** Правое окно кода — третья область разделителя, за графиком. */
    private void installRightPaneArea(boolean show) {
        if (this.splitPane == null) {
            return;
        }
        if (this.rightPaneArea != null) {
            this.splitPane.getItems().remove(this.rightPaneArea);
        }
        if (!show) {
            this.rightPaneArea = null;
            this.rightPaneTitleLabel = null;
            return;
        }
        Label title = this.channelTitleLabel(true);
        this.rightPaneTitleLabel = title;
        Node editorNode = this.ensureRightEditor().getNode();
        VBox.setVgrow(editorNode, Priority.ALWAYS);
        VBox area = new VBox(0.0, title, editorNode);
        area.getStyleClass().addAll("sidebar", "channel-pane");
        area.setMinWidth(0.0);
        area.setPrefWidth(320.0);
        // Окно кода не растягивается вместе с окном программы: шире становится график.
        SplitPane.setResizableWithParent(area, Boolean.FALSE);
        this.rightPaneArea = area;
        this.splitPane.getItems().add(area);
    }

    /** Подпись канала: по щелчку канал становится исполняемым. */
    private Label channelTitleLabel(boolean right) {
        Label title = new Label();
        title.getStyleClass().add("channel-pane-title");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setOnMouseClicked(event -> this.setActivePane(right));
        return title;
    }

    private void updateChannelPaneTitles() {
        boolean leftRunning = this.bothChannelsActive || !this.rightPaneActive;
        boolean rightRunning = this.bothChannelsActive || this.rightPaneActive;
        this.paneTitle(this.leftPaneTitleLabel, I18n.text("panel.channel.two"), leftRunning);
        this.paneTitle(this.rightPaneTitleLabel, I18n.text("panel.channel.one"), rightRunning);
    }

    private void paneTitle(Label label, String name, boolean running) {
        if (label == null) {
            return;
        }
        label.setText(name + (running ? "  " + I18n.text("panel.channel.active") : ""));
        label.getStyleClass().remove("channel-pane-title-active");
        if (running) {
            label.getStyleClass().add("channel-pane-title-active");
        }
    }

    private ProgramGcodeEditor ensureRightEditor() {
        if (this.rightProgramEditor != null) {
            return this.rightProgramEditor;
        }
        ProgramGcodeEditor editor = new ProgramGcodeEditor();
        editor.applyFontSize(UserSettings.getProgramFontSize());
        editor.setText(UserSettings.getRightProgramText());
        editor.setOnTextChange(() -> {
            if (this.applyingProgramText) {
                return;
            }
            UserSettings.setRightProgramText(editor.getText());
            if (this.rightPaneActive) {
                this.programEditorEditingActive = true;
            }
        });
        this.rightProgramEditor = editor;
        return editor;
    }

    /** Переключает исполняемый канал: весь расчёт идёт по активному окну. */
    private void setActivePane(boolean right) {
        if (right && this.rightProgramEditor == null && !this.rightPaneVisible) {
            return;
        }
        ProgramGcodeEditor target = right ? this.ensureRightEditor() : this.leftProgramEditor;
        if (target == null || (this.rightPaneActive == right && this.programEditor == target)) {
            this.updateChannelPaneTitles();
            return;
        }
        this.rightPaneActive = right;
        this.bothChannelsActive = false;
        this.programEditor = target;
        this.programEditorEditingActive = false;
        String text = target.getText();
        this.appliedProgramText = text == null ? "" : text;
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.fullPreviewVisible = false;
        this.toolpathGraphVisible = false;
        this.currentExecutionIndex = -1;
        this.selectedStartIndex = -1;
        this.syncAxisModeForProgram(this.appliedProgramText);
        this.syncWorkOffsetsFromProgram(this.appliedProgramText);
        this.clearProgramHighlight();
        this.syncProgramEditorVisuals();
        this.updateChannelPaneTitles();
        this.resetCanvasView();
        this.redrawCanvas();
    }

    private boolean paneHasCode(ProgramGcodeEditor editor) {
        if (editor == null) {
            return false;
        }
        String text = editor.getText();
        if (text == null || text.isBlank()) {
            return false;
        }
        // Один комментарий кодом не считаем: иначе шапка файла заставит спрашивать канал.
        for (String line : text.split("\\R")) {
            if (!GCodeProgramParser.isNonExecutableProgramLine(line)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Запуск с выбором канала.
     *
     * <p>Код в одном окне — запускается он. Код в обоих — спрашиваем, какой канал
     * гнать: одновременный пуск двух каналов появится позже, и молча выбрать за
     * человека нельзя, это разные головки и разные стороны детали.
     */
    /** Что делать при пуске: гнать активный канал, переключиться на один, или спросить. */
    enum ChannelChoice {
        RUN_ACTIVE,
        USE_LEFT,
        USE_RIGHT,
        ASK
    }

    /**
     * Решение без диалога — его и проверяем в тестах.
     *
     * <p>Код в одном окне: гоним его, спрашивать нечего. Код в обоих: спрашиваем, потому
     * что это разные головки и разные стороны детали, и выбрать за человека нельзя.
     */
    ChannelChoice channelChoiceForRun() {
        if (!this.rightPaneVisible) {
            return ChannelChoice.RUN_ACTIVE;
        }
        boolean leftHasCode = this.paneHasCode(this.leftProgramEditor);
        boolean rightHasCode = this.paneHasCode(this.rightProgramEditor);
        if (!leftHasCode && !rightHasCode) {
            return ChannelChoice.RUN_ACTIVE;
        }
        if (leftHasCode && rightHasCode) {
            return ChannelChoice.ASK;
        }
        return rightHasCode ? ChannelChoice.USE_RIGHT : ChannelChoice.USE_LEFT;
    }

    private boolean chooseChannelBeforeRun() {
        ChannelChoice choice = this.channelChoiceForRun();
        if (choice == ChannelChoice.RUN_ACTIVE) {
            return true;
        }
        if (choice != ChannelChoice.ASK) {
            this.setActivePane(choice == ChannelChoice.USE_RIGHT);
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.text("channel.choice.title"));
        alert.setHeaderText(I18n.text("channel.choice.header"));
        // Длинная подсказка в стандартной строке обрезалась многоточием.
        Label hint = new Label(I18n.text("channel.choice.hint"));
        hint.setWrapText(true);
        hint.setPrefWidth(770.0);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        hint.getStyleClass().add("dialog-hint");
        alert.getDialogPane().setContent(hint);
        alert.getDialogPane().getStyleClass().add("choice-dialog");
        alert.getDialogPane().setPrefWidth(820.0);
        alert.setGraphic(null);
        ButtonType two = new ButtonType(I18n.text("channel.choice.two"), ButtonBar.ButtonData.OK_DONE);
        ButtonType one = new ButtonType(I18n.text("channel.choice.one"), ButtonBar.ButtonData.OTHER);
        ButtonType both = new ButtonType(I18n.text("channel.choice.both"), ButtonBar.ButtonData.APPLY);
        ButtonType cancel = new ButtonType(I18n.text("channel.choice.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(two, one, both, cancel);
        // ButtonBar равняет кнопки по ширине, и подписи резались многоточием:
        // каждая кнопка берёт ширину по своему тексту.
        for (ButtonType type : alert.getButtonTypes()) {
            javafx.scene.Node button = alert.getDialogPane().lookupButton(type);
            if (button != null) ButtonBar.setButtonUniformSize(button, false);
        }
        this.applyDialogStyles(alert);
        this.setDialogIcon(alert);
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == cancel) {
            return false;
        }
        if (answer.get() == both) {
            this.setBothChannels(true);
            return true;
        }
        this.setBothChannels(false);
        this.setActivePane(answer.get() == one);
        return true;
    }

    /** Включает или выключает показ обоих каналов на одной плоскости. */
    void setBothChannels(boolean both) {
        if (this.bothChannelsActive == both) {
            return;
        }
        this.bothChannelsActive = both;
        this.cachedToolMovesSource = "";
        this.cachedToolMoves = List.of();
        this.previewLayoutValid = false;
        this.trajectoryPreviewComplete = false;
        this.currentExecutionIndex = -1;
        this.selectedStartIndex = -1;
        // Показываем сразу: режим меняет саму траекторию, а не подпись.
        this.fullPreviewVisible = true;
        this.toolpathGraphVisible = true;
        this.pathDisplayActive = true;
        this.executionMoves = this.parseMovesForView(
                this.appliedProgramText != null ? this.appliedProgramText : "");
        this.updateChannelPaneTitles();
        this.resetCanvasView();
        this.redrawCanvas();
    }

    // ------------------------------------------------------------------
    // Меню «Параметры» и набор файлов двухканальной программы
    // ------------------------------------------------------------------

    @FXML
    private void menuOpenParameterProgram() {
        ParameterProgramWindow.show(this.aiOwnerWindow(), this.darkTheme, this::onParameterProgramSaved);
    }

    private void onParameterProgramSaved(String text) {
        // Размеры поменялись — прежняя траектория и открытая 3D-модель уже не те.
        this.parameterProgramCacheKey = null;
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.redrawCanvas();
        this.refreshOpenSimulation3d();
    }

    /**
     * Открыть набор файлов двухканальной программы.
     *
     * <p>Такая программа лежит пятью файлами: параметры и два канала по две стороны.
     * Раскладывать их руками незачем — в каждом файле написано, чей он.
     */
    @FXML
    private void menuOpenProgramSet() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("fileset.title"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.text("fileset.filter"),
                        "*.spf", "*.SPF", "*.mpf", "*.MPF", "*.nc", "*.gcode", "*.txt"),
                new FileChooser.ExtensionFilter(I18n.text("app.136"), "*.*"));
        Window owner = this.aiOwnerWindow();
        List<File> files = chooser.showOpenMultipleDialog(owner);
        if (files == null || files.isEmpty()) {
            return;
        }
        this.showInfoDialog(I18n.text("fileset.done"), this.applyProgramSet(files));
    }

    /**
     * Раскладывает набор по окнам и возвращает отчёт, что куда попало.
     *
     * <p>Отчёт возвращается, а не показывается: так раскладку можно проверить тестом,
     * не упираясь в диалог, который ждёт человека.
     */
    String applyProgramSet(List<File> files) {
        Map<String, String> byRole = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        String parameterText = null;
        for (File file : files) {
            String text;
            try {
                text = ProgramTextFile.read(file.toPath()).text();
            } catch (IOException exception) {
                failed.add(file.getName());
                continue;
            }
            ProgramFileRole.Role role = ProgramFileRole.detect(file.getName(), text);
            if (role.isParameter()) {
                parameterText = text;
                report.add(file.getName() + " → " + I18n.text("fileset.role.parameters"));
                continue;
            }
            int channel = role.channel() > 0 ? role.channel() : 1;
            int side = role.side() > 0 ? role.side() : 1;
            byRole.put(channel + "/" + side, text);
            // Все четыре программы остаются в наборе: в окнах видно одну сторону.
            UserSettings.setChannelSideProgram(channel, side, text);
            report.add(file.getName() + " → " + role.shortLabel());
        }
        if (parameterText != null) {
            UserSettings.setParameterProgramText(parameterText);
            ParameterProgramWindow.setProgramText(parameterText);
            this.parameterProgramCacheKey = null;
        }
        // Сторона 1 — то, с чего начинают: её и показываем. Сторона 2 лежит в наборе
        // и приходит в окна переключателем сторон, когда деталь перевернут.
        int side = byRole.containsKey("2/1") || byRole.containsKey("1/1") ? 1 : 2;
        String channelTwo = firstNonNull(byRole.get("2/" + side), byRole.get("2/" + other(side)));
        String channelOne = firstNonNull(byRole.get("1/" + side), byRole.get("1/" + other(side)));
        if (channelOne != null || channelTwo != null) {
            this.setRightPaneVisible(true);
        }
        this.activeSide = side;
        UserSettings.setActiveSide(side);
        if (channelTwo != null) {
            this.applyTextToPane(false, channelTwo);
        }
        if (channelOne != null) {
            this.applyTextToPane(true, channelOne);
        }
        this.setActivePane(channelTwo == null && channelOne != null);
        this.updateSideToggle();
        StringBuilder message = new StringBuilder(String.join(System.lineSeparator(), report));
        if (!failed.isEmpty()) {
            message.append(System.lineSeparator())
                    .append(I18n.format("fileset.failed", String.join(", ", failed)));
        }
        return message.toString();
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private static int other(int side) {
        return side == 1 ? 2 : 1;
    }

    /**
     * Переключение стороны детали: обе головки переходят вместе.
     *
     * <p>На станке так и есть: сначала сторона 1 в двух каналах, потом деталь
     * переворачивают и идёт сторона 2. Порознь стороны не гоняют.
     */
    @FXML
    private void menuToggleSide() {
        this.switchSide(other(this.activeSide));
    }

    private void switchSide(int side) {
        String left = UserSettings.getChannelSideProgram(2, side);
        String right = UserSettings.getChannelSideProgram(1, side);
        if ((left == null || left.isBlank()) && (right == null || right.isBlank())) {
            this.showInfoDialog(I18n.text("side.missing.title"),
                    I18n.format("side.missing.body", side));
            return;
        }
        // Правки текущей стороны не теряем: сохраняем их в набор перед переходом.
        if (this.leftProgramEditor != null) {
            UserSettings.setChannelSideProgram(2, this.activeSide, this.leftProgramEditor.getText());
        }
        if (this.rightProgramEditor != null) {
            UserSettings.setChannelSideProgram(1, this.activeSide, this.rightProgramEditor.getText());
        }
        this.activeSide = side;
        UserSettings.setActiveSide(side);
        if (left != null && !left.isBlank()) {
            this.applyTextToPane(false, left);
        }
        if (right != null && !right.isBlank()) {
            this.setRightPaneVisible(true);
            this.applyTextToPane(true, right);
        }
        this.setActivePane(this.rightPaneActive && right != null && !right.isBlank());
        this.appliedProgramText = this.programEditor == null ? "" : this.programEditor.getText();
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.syncAxisModeForProgram(this.appliedProgramText);
        this.redrawCanvas();
        this.updateSideToggle();
    }

    /** Кнопка стороны видна, когда в наборе есть вторая сторона. */
    private void updateSideToggle() {
        if (this.sideToggleButton == null) {
            return;
        }
        boolean hasOther = !UserSettings.getChannelSideProgram(2, other(this.activeSide)).isBlank()
                || !UserSettings.getChannelSideProgram(1, other(this.activeSide)).isBlank();
        this.sideToggleButton.setVisible(hasOther);
        this.sideToggleButton.setManaged(hasOther);
        this.sideToggleButton.setText(I18n.format("panel.side", this.activeSide));
    }

    /** Кладёт текст в окно и сохраняет его файл. */
    private void applyTextToPane(boolean right, String text) {
        ProgramGcodeEditor editor = right ? this.ensureRightEditor() : this.leftProgramEditor;
        if (editor == null) {
            return;
        }
        this.applyingProgramText = true;
        editor.setText(text);
        this.applyingProgramText = false;
        if (right) {
            UserSettings.setRightProgramText(text);
        } else {
            UserSettings.setLastProgramText(text);
            this.recordProgramHistoryIfChanged(this.appliedProgramText, text);
        }
    }

    // ------------------------------------------------------------------
    // Меню «МСП»
    // ------------------------------------------------------------------

    @FXML
    private void menuMcpConsole() {
        McpConsoleWindow.show(this.aiOwnerWindow(), this.aiAccess(), aiAppVersion(), this.darkTheme);
    }

    /**
     * Поднимает сервер MCP при запуске, если человек поставил галочку в окне «МСП».
     *
     * <p>Молча: окно об этом не сообщает, но строка появляется в журнале MCP и в
     * intraspect-start.log — по ним потом видно, что сервер поднимался сам.
     */
    private void aiStartMcpIfAsked() {
        if (!UserSettings.isMcpAutoStart() || McpServer.isRunning()) {
            return;
        }
        try {
            McpServer.Access access = "FULL".equals(UserSettings.getMcpAccess())
                    ? McpServer.Access.FULL : McpServer.Access.READ_ONLY;
            McpServer server = McpServer.start(this.aiAccess(), UserSettings.getMcpPort(),
                    access, aiAppVersion());
            com.sergey.pisarev.util.StartupLog.step("MCP server started on port " + server.port()
                    + ", access " + access);
        } catch (Exception failure) {
            McpServer.note(I18n.text("mcp.log.startfailed") + " " + failure.getMessage());
            com.sergey.pisarev.util.StartupLog.failure("MCP server did not start", failure);
        }
    }

    private javafx.stage.Window aiOwnerWindow() {
        if (this.anchorPaneCanvas != null && this.anchorPaneCanvas.getScene() != null) {
            return this.anchorPaneCanvas.getScene().getWindow();
        }
        return null;
    }

    private static String aiAppVersion() {
        String version = System.getProperty("jpackage.app-version");
        return version == null || version.isBlank() ? "dev" : version;
    }

    // ------------------------------------------------------------------
    // Мост для сервера MCP
    //
    // Всё, что нейросеть делает с программой, проходит через эти методы и остаётся
    // видимым человеку: текст ложится в редактор, инструмент — в таблицу, настройка —
    // в своё поле. Скрытых правок нет: наладчик должен видеть, что изменила модель.
    // Методы вызываются только из потока JavaFX (за этим следит AiAppAccess).
    // ------------------------------------------------------------------

    /** Доступ к программе для ИИ; создаётся при первом обращении. */
    AiAppAccess aiAccess() {
        if (this.aiAppAccess == null) {
            this.aiAppAccess = new AiAppAccess(this);
        }
        return this.aiAppAccess;
    }

    String aiProgramText() {
        String text = this.programEditor != null ? this.programEditor.getText() : this.appliedProgramText;
        return text == null ? "" : text;
    }

    /** Кладёт программу в редактор так же, как открытие файла: с историей и перерисовкой. */
    void aiSetProgramText(String text) {
        if (this.programEditor == null) {
            this.appliedProgramText = text;
            return;
        }
        String previous = this.aiProgramText();
        this.programEditHistory.record(previous, text);
        this.applyingProgramText = true;
        this.programEditor.setText(text);
        this.applyingProgramText = false;
        this.appliedProgramText = text;
        this.cachedToolMovesSource = "";
        this.previewLayoutValid = false;
        this.programEditorEditingActive = false;
        this.syncTurningSettingsFromGCode(text);
        this.syncAxisModeForProgram(text);
        this.syncWorkOffsetsFromProgram(text);
        this.syncProgramEditorVisuals();
        this.saveLastProgramText(text);
        List<GCodeMoveData> moves = this.buildContourMoveDataList();
        this.refreshProgramDiagnostics(text, moves.size());
        this.redrawCanvas();
    }

    List<ProgramDiagnostics.Diagnostic> aiCheckProgram() {
        String text = this.aiProgramText();
        List<GCodeMoveData> moves = this.buildContourMoveDataList();
        return this.analyzeProgramDiagnostics(text, moves.size(), moves);
    }

    List<GCodeMoveData> aiContourMoves() {
        return this.buildContourMoveDataList();
    }

    SimulationContext aiSimulationContext() {
        return this.buildSimulationContext(this.buildGCodeMoveDataList());
    }

    Simulation3dWindow aiSimulationWindow() { return this.simulation3dWindow; }

    Map<String,Object> aiControlSimulation(Map<String,Object> args) {
        if ("open".equals(args.get("action"))) {
            if (!Boolean.TRUE.equals(this.simulation3dWindow.liveState().get("open"))) {
                this.syncAppliedProgramFromEditor();
                if(this.paneHasCode(this.leftProgramEditor)&&this.paneHasCode(this.rightProgramEditor)) this.setBothChannels(true);
                if (this.buildSavedMachineConfiguration().isVerticalLathe() && this.sharedTurnoverStock == null) {
                    double d=UserSettings.getTurnoverValue("diameter",Double.NaN),
                            z=UserSettings.getTurnoverValue("zMin",Double.NaN),
                            l=UserSettings.getTurnoverValue("length",Double.NaN),
                            sum=UserSettings.getTurnoverValue("zSum",Double.NaN);
                    if (Double.isFinite(d+z+l+sum)&&d>0&&l>0) {
                        this.sharedTurnoverStock=new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,d,l,z,z+l,0)
                                .withInitialBoreDiameter(UserSettings.getTurnoverValue("initialBoreDiameter",0));
                        this.turnoverZSum=sum;
                    }
                }
                var context = this.buildCompleteSimulationContext(this.buildSimulationContourMoveDataList());
                var owner = this.anchorPaneCanvas.getScene().getWindow();
                this.simulation3dWindow.open(owner,context,this.darkTheme,null,true);
            }
            return this.simulation3dWindow.liveState();
        }
        return this.simulation3dWindow.control(args);
    }

    List<CncToolDefinition> aiToolLibrary() {
        return this.currentToolLibrary();
    }

    /** Записывает библиотеку и сразу показывает её в таблице настроек. */
    void aiSetToolLibrary(List<CncToolDefinition> tools) {
        ToolLibraryStore.save(new ArrayList<>(tools));
        this.displaySavedToolLibrary(tools);
        if (this.toolsTableView != null) {
            this.toolsTableView.refresh();
        }
        this.previewLayoutValid = false;
        this.redrawCanvas();
    }

    /** Перечитывает настройки в поля панели — после правки через MCP. */
    void aiRefreshSettingsUi() {
        this.resetMachineToolDrafts();
        this.loadGeometrySettingsFromPrefs();
        this.refreshTurningAndCupLabels();
        // Смещения нулевой точки живут в двух местах: в настройках и в WorkOffsetStore,
        // из которого их читает разбор программы. Без этой синхронизации правка через
        // MCP попадала в поля панели, но график и 3D её не видели — поймано на программе
        // оси с двумя нулями: половины так и лежали одна на другой.
        this.refreshGraphManualOffsetsSnapshotFromSavedSettings();
        this.cachedToolMovesSource = "";
        if (this.feedOverrideDial != null) {
            this.feedOverrideDial.setValue(UserSettings.getFeedOverridePercent());
        }
        if (this.spindleOverrideDial != null) {
            this.spindleOverrideDial.setValue(UserSettings.getSpindleOverridePercent());
        }
        this.previewLayoutValid = false;
        this.redrawCanvas();
    }

    boolean aiDarkTheme() {
        return this.darkTheme;
    }

    com.sergey.pisarev.model.MachineConfiguration aiMachineConfiguration() {
        return this.buildSavedMachineConfiguration();
    }

    String aiAxisMode() {
        return String.valueOf(this.axisMode);
    }

    /** Строка о работе нейросети в статусе окна — чтобы человек видел, что что-то происходит. */
    void aiStatus(String line) {
        if (this.labelExecutionStatus != null && line != null && !line.isBlank()) {
            this.labelExecutionStatus.setText(line);
        }
    }

    /** PNG двумерного графика: тот же рисовальщик, что и в окне, но в отдельный холст. */
    byte[] aiRenderGraphPng(int width, int height) {
        int w = Math.max(320, Math.min(2400, width));
        int h = Math.max(200, Math.min(1600, height));
        String text = this.aiProgramText();
        List<ToolMove> moves = this.parseMovesForView(text);
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(w, h);
        // Холст без сцены рисуется, но снимок берётся уже готовой картинкой.
        new javafx.scene.Scene(new javafx.scene.Group(canvas), w, h);
        boolean savedFullPreview = this.fullPreviewVisible;
        boolean savedComplete = this.trajectoryPreviewComplete;
        this.fullPreviewVisible = true;
        this.trajectoryPreviewComplete = true;
        try {
            this.drawToolpathPreview(canvas, w, h, moves);
            javafx.scene.image.WritableImage image = canvas.snapshot(null, null);
            return com.sergey.pisarev.ai.PngWriter.encode(image);
        } finally {
            this.fullPreviewVisible = savedFullPreview;
            this.trajectoryPreviewComplete = savedComplete;
        }
    }
}
