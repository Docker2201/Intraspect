package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.*;
import com.sergey.pisarev.service.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercises the real controller path with isolated settings. */
public final class SimulationGeometryRegressionTest {
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory(Path.of(args[0]), "controller-");
        System.setProperty("user.home", folder.resolve("home").toString());
        System.setProperty("chekator.data.dir", folder.resolve("data").toString());
        // Match the supplied example's settings; isolate NC coordinates from tool compensation.
        com.sergey.pisarev.util.UserSettings.setEquidistantEnabled(false);
        javafx.application.Platform.startup(() -> {});
        var done = new CompletableFuture<Void>();
        javafx.application.Platform.runLater(() -> {
            try { run(); done.complete(null); }
            catch (Throwable t) { done.completeExceptionally(t); }
        });
        int code = 0;
        try { done.get(30, TimeUnit.SECONDS); }
        catch (Exception e) { e.printStackTrace(); code = 1; }
        finally { javafx.application.Platform.exit(); }
        System.exit(code);
    }

    private static void run() throws Exception {
        String text = "WORKPIECE(,,,\"CYLINDER\",100,200,-200,-200,0)\n"
                + "G0 X60 Z-100\nG1 X40 Z-50\nG1 Z20\nM30";
        var controller = new MainController();
        var applied = MainController.class.getDeclaredField("appliedProgramText");
        applied.setAccessible(true); applied.set(controller, text);
        var method = MainController.class.getDeclaredMethod("buildSimulationContext", List.class);
        method.setAccessible(true);
        var context = (SimulationContext) method.invoke(controller, List.of());
        var wp = context.getWorkpiece();
        check("declared blank retains Z -200..0", wp.getZMin() == -200 && wp.getZMax() == 0);
        var cut = context.getContourMoves().stream().filter(m -> !m.rapid()).toList();
        check("first cutting point keeps NC coordinate", cut.get(0).startZ() == -100);
        check("out-of-stock target keeps NC coordinate", cut.get(cut.size()-1).endZ() == 20);
        var profile = LatheStockRemovalSimulator.buildRevolveProfile(context);
        check("uncut left part survives", profile.get(0)[0] == -200 && profile.get(0)[1] == 50);
        for (boolean rapid : new boolean[]{false,true}) {
            var dark = SimulationGcodeOverlay.pointColor(true, rapid);
            var light = SimulationGcodeOverlay.pointColor(false, rapid);
            check("dark-theme point is light (rapid="+rapid+")", dark.getBrightness() > .9);
            check("light-theme point is dark (rapid="+rapid+")", light.getBrightness() < .6);
        }
        try (var source = SimulationGeometryRegressionTest.class.getResourceAsStream("/sample-shaft.nc")) {
            check("complete NC fixture present", source != null);
            applied.set(controller, new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        var sync = MainController.class.getDeclaredMethod("syncAxisModeForProgram", String.class);
        sync.setAccessible(true); sync.invoke(controller, (String) applied.get(controller));
        var fullContext = (SimulationContext) method.invoke(controller, List.of());
        var fullStock = fullContext.getWorkpiece();
        check("fixture blank is diameter 120 and length 1200",fullStock.getDiameterMm()==120
                && fullStock.getZMin()==-600 && fullStock.getZMax()==600);
        var fullProfile=LatheStockRemovalSimulator.buildRevolveProfile(fullContext);
        var radii=new TreeMap<Double,Double>();
        for(var point:fullProfile) radii.put(point[0],point[1]);
        check("both stock ends survive",radii.get(-600.0)==60 && radii.get(600.0)==60);
        var center=radii.floorEntry(0.0);
        check("narrow feature lies in the middle of the stock",center != null && center.getValue()<55);
        double minZ=fullContext.getContourMoves().stream().filter(m->!m.rapid())
                .mapToDouble(m->Math.min(m.startZ(),m.endZ())).min().orElseThrow();
        double maxZ=fullContext.getContourMoves().stream().filter(m->!m.rapid())
                .mapToDouble(m->Math.max(m.startZ(),m.endZ())).max().orElseThrow();
        check("full program coordinates are not scaled or recentered ("+minZ+".."+maxZ+")",
                minZ==-480 && maxZ==120);
        verifyCarousel(controller, method, applied);
        verifyParameterBore();
    }

    private static void verifyParameterBore() throws Exception {
        var controller=new MainController();
        String program="WORKPIECE(,,,\"CYLINDER\",0,60,0,0,100)\nDIAMOF\nT73\nL6\nT88\n"
                + ";Boring\nG0 G40 X=BORE_DIAM-2 Z65\nG1 Z-5\nG0 X=IC(-3)\nZ65\n"
                + "X=BORE_DIAM-GLOBAL_ALLOWANCE\nG1 Z-5\nG0 X=IC(-3)\nZ65\nM30";
        var applied=MainController.class.getDeclaredField("appliedProgramText");applied.setAccessible(true);applied.set(controller,program);
        var sync=MainController.class.getDeclaredMethod("syncAxisModeForProgram",String.class);sync.setAccessible(true);sync.invoke(controller,program);
        var build=MainController.class.getDeclaredMethod("buildCompleteSimulationContext",List.class);build.setAccessible(true);
        for (double diameter:new double[]{48,64,52}) {
            // Change the shared parameter program without recreating the controller.
            // This catches stale parameter values and a cached automatic raw bore.
            com.sergey.pisarev.util.UserSettings.setParameterProgramText("BASE="+diameter+"\nBORE_DIAM=BASE/2\nGLOBAL_ALLOWANCE=0.5");
            var context=(SimulationContext)build.invoke(controller,List.of());
            check("DIAMOF parameter expression controls the automatic bore "+diameter,
                    context.getWorkpiece().isBoreFromProgram()&&context.getWorkpiece().getInitialBoreDiameterMm()==diameter-4);
            var section=AxialStockSection.build(context,context.getContourMoves());
            check("parameter edit updates the final hole including allowance "+diameter,
                    !AxialStockSection.contains(section,30,diameter*.5-.51)
                    &&AxialStockSection.contains(section,30,diameter*.5-.49));
        }
        // The exact same dimensions expressed in diameter mode must not double the bore.
        com.sergey.pisarev.util.UserSettings.setParameterProgramText("BORE_DIAM=52\nGLOBAL_ALLOWANCE=1");
        program=program.replace("DIAMOF","DIAMON").replace("BORE_DIAM-2","BORE_DIAM-4");
        applied.set(controller,program);sync.invoke(controller,program);
        var diamon=(SimulationContext)build.invoke(controller,List.of());
        var section=AxialStockSection.build(diamon,diamon.getContourMoves());
        check("DIAMON parameter is a diameter rather than a radius",!AxialStockSection.contains(section,30,25.49)
                &&AxialStockSection.contains(section,30,25.51));
    }

    private static void verifyCarousel(MainController controller, Method build, Field applied) throws Exception {
        com.sergey.pisarev.util.UserSettings.setMachineType("VERTICAL_LATHE");
        com.sergey.pisarev.util.UserSettings.setUseBlankDiameter(true);
        com.sergey.pisarev.util.UserSettings.setBlankDiameterMm(840);
        String blank = "WORKPIECE(,,,\"CYLINDER\",192,460,-460,-460,840)\n";
        String twoZeros = blank + "DIAMON\nG54\nG0 X800 Z0\nG1 Z10\nG55\nG1 Z20\nM30";
        applied.set(controller, twoZeros);
        var stock = ((SimulationContext) build.invoke(controller, List.of())).getWorkpiece();
        check("920 mm axial stock is not halved by two work offsets", stock.getLengthMm() == 920
                && stock.getZMin() == -460 && stock.getZMax() == 460);

        var tools = List.of(new CncToolDefinition(1,"left","turning",1,1,0,0,0,510,"Finish",3),
                new CncToolDefinition(2,"right","turning",2,1,0,0,0,510,"Finish",4));
        var library = MainController.class.getDeclaredField("toolsObservableList");
        var libraryMachine = MainController.class.getDeclaredField("toolsMachine");
        libraryMachine.setAccessible(true); libraryMachine.set(controller, MachineConfiguration.MachineType.VERTICAL_LATHE);
        library.setAccessible(true);
        library.set(controller, javafx.collections.FXCollections.observableArrayList(tools));
        var left = new ProgramGcodeEditor();
        var right = new ProgramGcodeEditor();
        left.setText(blank + "DIAMON\nT1 D1\nG0 X600 Z-400\nG41 G1 Z-100\nM30");
        right.setText(blank + "DIAMON\nT2 D1\nG0 X700 Z400\nG42 G1 Z100\nM30");
        for (var entry : Map.of("leftProgramEditor", left, "rightProgramEditor", right,
                "programEditor", left).entrySet()) {
            var field = MainController.class.getDeclaredField(entry.getKey());
            field.setAccessible(true); field.set(controller, entry.getValue());
        }
        controller.setBothChannels(false);
        var single = (SimulationContext) build.invoke(controller, List.of());
        check("vertical mode honors one selected channel", single.getContourMoves().stream().filter(m -> !m.rapid()).count() == 1);
        var bothField = MainController.class.getDeclaredField("bothChannelsActive");
        bothField.setAccessible(true); bothField.set(controller,true);
        var paneVisible = MainController.class.getDeclaredField("rightPaneVisible");
        paneVisible.setAccessible(true); paneVisible.set(controller,true);
        check("Start still asks when both channels were previously selected",controller.channelChoiceForRun()==MainController.ChannelChoice.ASK);
        var choose = MainController.class.getDeclaredMethod("chooseChannelBeforeRun"); choose.setAccessible(true);
        javafx.application.Platform.runLater(() -> {
            for (var window : List.copyOf(javafx.stage.Window.getWindows())) {
                if (window.getScene()!=null && window.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                    for(var button:pane.getButtonTypes()) if(button.getButtonData()==javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)
                        ((javafx.scene.control.Button)pane.lookupButton(button)).fire();
                }
            }
        });
        check("Start opens the choice dialog and honors Cancel",!(boolean)choose.invoke(controller));
        var merged = (SimulationContext) build.invoke(controller, List.of());
        var cuts = merged.getContourMoves().stream().filter(m -> !m.rapid()).toList();
        check("3D includes both explicitly selected channels", cuts.size() == 2);
        check("channels retain independent tools", cuts.get(0).toolNumber() == 1 && cuts.get(1).toolNumber() == 2);
        var envelope = new LatheCuttingEnvelope(merged);
        var modes = LatheCompensationProcessor.scanCompensationByLine(merged.getProgramText());
        check("second channel retains G42 rather than first channel G41",
                modes.get(cuts.get(1).sourceLine()) == LatheCompensationProcessor.CompensationMode.G42);
        var profile = LatheStockRemovalSimulator.buildRevolveProfile(merged);
        check("left channel cuts shared stock", AxialStockSection.contains(profile,-200,299)
                && !AxialStockSection.contains(profile,-200,301));
        check("right channel cuts shared stock", AxialStockSection.contains(profile,200,349)
                && !AxialStockSection.contains(profile,200,351));
        check("uncut middle and both ends survive", AxialStockSection.contains(profile,0,419)
                && AxialStockSection.contains(profile,-459,419) && AxialStockSection.contains(profile,459,419));
        right.setText(right.getText().replace("460,-460,-460", "954,34,34"));
        left.setText(left.getText().replace("460,-460,-460", "954,34,34"));
        var changed = (SimulationContext) build.invoke(controller, List.of());
        check("live WORKPIECE edits replace cached stock bounds", changed.getWorkpiece().getZMin() == 34
                && changed.getWorkpiece().getZMax() == 954 && changed.getWorkpiece().getLengthMm() == 920);
        com.sergey.pisarev.util.UserSettings.setChannelSideProgram(1,1,right.getText());
        com.sergey.pisarev.util.UserSettings.setChannelSideProgram(2,1,left.getText());
        var side = MainController.class.getDeclaredField("activeSide");side.setAccessible(true);side.set(controller,2);
        var configure = MainController.class.getDeclaredMethod("configureTurnoverBefore3d");configure.setAccessible(true);
        javafx.application.Platform.runLater(() -> {
            for(var window:List.copyOf(javafx.stage.Window.getWindows())) {
                if(window.getScene()!=null && window.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane
                        && pane.getContent() instanceof javafx.scene.layout.VBox box) {
                    var grid=(javafx.scene.layout.GridPane)box.getChildren().get(1);
                    String[] values={"30","100","-100","920","20"};int index=0;
                    for(var node:grid.getChildren()) if(node instanceof javafx.scene.control.TextField field && !"preformAllowance".equals(field.getId()))
                        field.setText(values[index++]);
                    var forging=(javafx.scene.control.CheckBox)grid.lookup("#approximatePreform");
                    var allowance=(javafx.scene.control.TextField)grid.lookup("#preformAllowance");
                    for(var button:pane.getButtonTypes()) if(button.getButtonData()==javafx.scene.control.ButtonBar.ButtonData.OK_DONE) {
                        var run=(javafx.scene.control.Button)pane.lookupButton(button);
                        forging.setSelected(true);allowance.setText("");
                        check("preform requires a positive allowance",run.isDisabled());
                        allowance.setText("4");check("preform accepts an editable allowance",!run.isDisabled());
                        forging.setSelected(false);run.fire();
                    }
                }
            }
        });
        check("turnover dialog accepts an arbitrary axial thickness",(boolean)configure.invoke(controller));
        var turnover=(SimulationContext)build.invoke(controller,List.of());
        check("side two receives side one's shared stock",turnover.getPrecedingSetup()!=null
                && turnover.getPrecedingSetup().getContourMoves().stream().filter(m->!m.rapid()).count()==2);
        check("920 mm survives a datum-changing flip",turnover.getWorkpiece().getLengthMm()==920
                && turnover.getWorkpiece().getZMin()==-790&&turnover.getWorkpiece().getZMax()==130);
        check("initial bore survives the dialog, settings and turnover",turnover.getWorkpiece().getInitialBoreDiameterMm()==20
                &&turnover.getPrecedingSetup().getWorkpiece().getInitialBoreDiameterMm()==20
                &&com.sergey.pisarev.util.UserSettings.getTurnoverValue("initialBoreDiameter",0)==20);
        com.sergey.pisarev.util.UserSettings.setChannelSideProgram(1,2,right.getText().replace("Z100", "Z120"));
        com.sergey.pisarev.util.UserSettings.setChannelSideProgram(2,2,left.getText().replace("Z-100", "Z-120"));
        side.set(controller,1);
        var completeBuild=MainController.class.getDeclaredMethod("buildCompleteSimulationContext",List.class);
        completeBuild.setAccessible(true);
        var complete=(SimulationContext)completeBuild.invoke(controller,List.of());
        check("opening 3D from side one includes the actual side-two programs",complete.getPrecedingSetup()!=null
                && complete.getProgramText().contains("Z120") && complete.getProgramText().contains("Z-120"));
        check("first-side unsaved editor coordinates survive in the complete result",
                complete.getPrecedingSetup().getProgramText().contains("Z-100"));
        bothField.set(controller,false);
        var oneChannel=(SimulationContext)completeBuild.invoke(controller,List.of());
        check("channel selection applies to both setups",oneChannel.getContourMoves().stream().filter(m->!m.rapid()).count()==1
                && oneChannel.getPrecedingSetup().getContourMoves().stream().filter(m->!m.rapid()).count()==1);
        var rotation = com.sergey.pisarev.util.MachineOrientation.modelRotation(true);
        check("vertical machine +Z points up", rotation.transform(0,1,0).getY() < -.999);
        var horizontal = com.sergey.pisarev.util.MachineOrientation.modelRotation(false);
        check("horizontal lathe orientation preserved", horizontal.transform(0,1,0).getX() < -.999);
    }

    private static double radiusAt(List<double[]> profile, double z) {
        for (int i=1;i<profile.size();i++) {
            double[] a=profile.get(i-1), b=profile.get(i);
            if (z >= a[0] && z <= b[0]) return a[1]+(b[1]-a[1])*(z-a[0])/(b[0]-a[0]);
        }
        throw new AssertionError("No profile at " + z);
    }
    private static void check(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }
}
