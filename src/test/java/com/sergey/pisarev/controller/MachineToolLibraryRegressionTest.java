package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.model.MachineConfiguration.MachineType;
import com.sergey.pisarev.util.ToolLibraryStore;
import com.sergey.pisarev.util.UserSettings;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

/** Real settings selector, isolated portable files, same T/D on different machines. */
public final class MachineToolLibraryRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(args[0]), "machine-tools-");
        Path data = Files.createDirectories(root.resolve("data"));
        System.setProperty("chekator.data.dir", data.toString());
        String legacy = "[{\"toolNumber\":1,\"edgeNumber\":1,\"radius\":0.2,\"typeCode\":510}]";
        Files.writeString(data.resolve("tools.json"), legacy);
        UserSettings.setMachineType(MachineType.GENERIC_LATHE.name());
        var axle = ToolLibraryStore.load();
        check("legacy shaft tool retained", axle.get(0).getRadius() == .2);
        var wheel = ToolLibraryStore.load(MachineType.VERTICAL_LATHE);
        wheel.get(0).setRadius(16);
        wheel.get(0).setTypeCode(550);
        ToolLibraryStore.save(MachineType.VERTICAL_LATHE, wheel);
        check("wheel save cannot alter shaft", ToolLibraryStore.load().get(0).getRadius() == .2);
        check("legacy seed remains unchanged", Files.readString(data.resolve("tools.json")).equals(legacy));
        check("later machine migration uses original seed", ToolLibraryStore.load(MachineType.DMG_MORI_LATHE).get(0).getRadius() == .2);
        ToolLibraryStore.save(MachineType.GENERIC_MILL, List.of());
        check("intentionally empty bank stays empty", ToolLibraryStore.load(MachineType.GENERIC_MILL).isEmpty());
        Platform.startup(() -> {});
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try { verifySettings(); done.complete(null); }
            catch (Throwable e) { done.completeExceptionally(e); }
        });
        int code = 0;
        try { done.get(45, TimeUnit.SECONDS); }
        catch (Exception e) { e.printStackTrace(); code = 1; }
        finally { Platform.exit(); }
        System.exit(code);
    }

    @SuppressWarnings("unchecked")
    private static void verifySettings() throws Exception {
        var controller = new MainController();
        call(controller, "ensureSettingsPanel");
        var selector = (ComboBox<String>) field(controller, "machineTypeComboBox");
        var rows = (ObservableList<CncToolDefinition>) field(controller, "toolsObservableList");
        var label = (Label) field(controller, "toolsMachineLabel");
        check("shaft bank in initial table", rows.get(0).getRadius() == .2);
        rows.get(0).setRadius(.4);
        selector.setValue(MachineType.VERTICAL_LATHE.getDisplayName());
        check("selector loads wheel bank", rows.get(0).getRadius() == 16);
        check("visible bank label follows selector", label.getText().contains(MachineType.VERTICAL_LATHE.getDisplayName()));
        rows.get(0).setRadius(10);
        check("unsaved machine does not supply wheel tools to shaft simulation", controller.aiToolLibrary().get(0).getRadius() == .4);
        selector.setValue(MachineType.GENERIC_LATHE.getDisplayName());
        check("draft survives switching away and back", rows.get(0).getRadius() == .4);
        selector.setValue(MachineType.VERTICAL_LATHE.getDisplayName());
        check("wheel draft stays separate", rows.get(0).getRadius() == 10);
        call(controller, "resetMachineToolDrafts");
        check("cancel restores saved machine", selector.getValue().equals(MachineType.GENERIC_LATHE.getDisplayName()));
        check("cancel restores shaft dimensions", rows.get(0).getRadius() == .2);
        selector.setValue(MachineType.VERTICAL_LATHE.getDisplayName());
        check("cancel discards wheel draft too", rows.get(0).getRadius() == 16);
        rows.get(0).setRadius(12);
        selector.setValue(MachineType.GENERIC_LATHE.getDisplayName());
        rows.get(0).setRadius(.8);
        selector.setValue(MachineType.VERTICAL_LATHE.getDisplayName());
        call(controller, "commitSettingsPanel");
        check("save activates selected machine", ToolLibraryStore.savedMachineType() == MachineType.VERTICAL_LATHE);
        check("save persists wheel dimensions", ToolLibraryStore.load().get(0).getRadius() == 12);
        check("save persists shaft draft in its own file", ToolLibraryStore.load(MachineType.GENERIC_LATHE).get(0).getRadius() == .8);
        check("simulation follows saved wheel bank", controller.aiToolLibrary().get(0).getRadius() == 12);
        selector.setValue(MachineType.GENERIC_LATHE.getDisplayName());
        var update = ToolLibraryStore.load(MachineType.VERTICAL_LATHE);
        update.get(0).setRadius(20);
        controller.aiSetToolLibrary(update);
        check("MCP update targets active machine without overwriting another draft", rows.get(0).getRadius() == .8);
        check("MCP update reaches simulation bank", controller.aiToolLibrary().get(0).getRadius() == 20);
        System.out.println("Machine tool library regressions passed.");
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static Object call(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target);
    }
    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }
}
