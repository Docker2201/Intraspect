package com.sergey.pisarev.controller;

import com.sergey.pisarev.ai.McpServer;
import com.sergey.pisarev.ai.PngWriter;
import com.sergey.pisarev.util.I18n;
import com.sergey.pisarev.util.UserSettings;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Captures real JavaFX windows using a separate synthetic demo data directory. */
public final class DocumentationScreenshots {
    static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<T>(work); Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(), images=root.resolve("docs/images");
        Files.createDirectories(images);
        System.setProperty("chekator.data.dir",Files.createTempDirectory(root.resolve("out"),"docs-demo-").toString());
        UserSettings.setInterfaceLanguage("en"); UserSettings.setDarkTheme(true);
        UserSettings.setMcpAutoStart(false);
        Platform.startup(()->{});
        try {
            MainController controller=fx(()->{
                var loader=new FXMLLoader(MainController.class.getResource("/main.fxml"),I18n.bundle());
                Parent pane=loader.load(); pane.getStyleClass().add("dark-theme");
                var stage=new Stage(); var scene=new Scene(pane,1440,900);
                scene.getStylesheets().add(MainController.class.getResource("/app.css").toExternalForm());
                stage.setScene(scene);stage.setTitle("Intraspect — sample program");stage.show();
                return loader.getController();
            });
            var app=controller.aiAccess();
            app.saveTool(Map.of("location",1.0,"toolNumber",1.0,"edge",1.0,"name","Sample finishing tool","typeCode",510.0,"radiusMm",.4,"toolPosition",3.0));
            System.out.println(app.setProgramText(Files.readString(root.resolve("samples/first-part.nc")),"Sample program"));
            System.out.println("Diagnostics: "+app.checkProgram());
            fx(()->{var start=MainController.class.getDeclaredMethod("onStart");start.setAccessible(true);start.invoke(controller);return null;});
            Thread.sleep(1800);
            fx(()->{
                for(Window w:Window.getWindows()) if(w instanceof Stage s && s.getTitle().startsWith("Intraspect"))
                    Files.write(images.resolve("editor.png"),PngWriter.encode(s.getScene().snapshot(null)));
                return null;
            });
            app.controlSimulation(Map.of("action","open"));
            for(int i=0;i<60;i++) {
                Thread.sleep(300); var state=app.simulationState();
                if(Boolean.TRUE.equals(state.get("open")) && Boolean.FALSE.equals(state.get("loading")))break;
            }
            fx(()->{
                for(Window w:Window.getWindows()) if(w.getScene()!=null) {
                    var button=w.getScene().lookup("#view3dButton");
                    if(button instanceof javafx.scene.control.Button b)b.fire();
                }
                return null;
            });
            Thread.sleep(600);
            fx(()->{
                for(Window w:Window.getWindows()) if(w.getScene()!=null && w.getScene().lookup("#view3dButton")!=null) {
                    var pane=w.getScene().getRoot();
                    var bounds=pane.getLayoutBounds();
                    var parameters=new javafx.scene.SnapshotParameters();
                    // Capture inside the scene, excluding the thin white strip at its left edge.
                    parameters.setViewport(new javafx.geometry.Rectangle2D(2,0,
                            Math.floor(bounds.getWidth())-2,Math.floor(bounds.getHeight())));
                    Files.write(images.resolve("simulation.png"),PngWriter.encode(pane.snapshot(parameters,null)));
                    return null;
                }
                throw new IllegalStateException("Simulation window was not opened");
            });
            fx(()->{
                McpConsoleWindow.show(null,app,"4.10",true);
                return null;
            });
            Thread.sleep(700);
            fx(()->{
                for(Window w:Window.getWindows()) if(w instanceof Stage s && s.getTitle().equals(I18n.text("mcp.title")))
                    Files.write(images.resolve("mcp.png"),PngWriter.encode(s.getScene().snapshot(null)));
                return null;
            });
            System.out.println("Saved documentation screenshots to "+images);
        } finally {McpServer.stop();Platform.exit();}
    }
}
