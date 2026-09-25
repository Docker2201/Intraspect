/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javafx.application.Application
 *  javafx.fxml.FXMLLoader
 *  javafx.scene.Parent
 *  javafx.scene.Scene
 *  javafx.scene.image.Image
 *  javafx.stage.Stage
 */
package com.sergey.pisarev;

import com.sergey.pisarev.util.I18n;
import com.sergey.pisarev.util.PortableStorage;
import com.sergey.pisarev.util.StartupLog;
import com.sergey.pisarev.util.UserSettings;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Launcher
extends Application {
    /** Имя программы: заголовок окна, журнал запуска, сообщения об ошибках. */
    public static final String APP_NAME = "Intraspect";

    public void start(Stage stage) throws Exception {
        StartupLog.step("JavaFX started, building the interface");
        StartupLog.storage(PortableStorage.describeStorageEnglish());
        FXMLLoader loader = new FXMLLoader(Launcher.class.getResource("/main.fxml"),
                I18n.bundle());
        StartupLog.step("interface language: " + I18n.language());
        Parent parent;
        try {
            parent = loader.load();
        }
        catch (Exception exception) {
            System.err.println(APP_NAME + " failed to load UI:");
            for (Throwable throwable = exception; throwable != null; throwable = throwable.getCause()) {
                throwable.printStackTrace();
            }
            StartupLog.failure("could not build the interface from main.fxml", exception);
            throw exception;
        }
        boolean darkTheme = UserSettings.isDarkTheme();
        if (darkTheme && !parent.getStyleClass().contains("dark-theme")) {
            parent.getStyleClass().add("dark-theme");
        }
        stage.setTitle(APP_NAME);
        stage.getIcons().add(new Image(Launcher.class.getResourceAsStream("/icon_16.png")));
        // Окно целиком масштабируется кнопками в верхней панели, поэтому сцена
        // строится на подложке: само содержимое лежит в ней и растягивается
        // преобразованием, а не пересборкой разметки.
        Scene scene = new Scene(com.sergey.pisarev.util.UiScale.wrap(parent));
        scene.setFill(darkTheme ? Color.web("#0b1220") : Color.web("#f8fafc"));
        scene.getStylesheets().add(Launcher.class.getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        // На маленьких экранах окно не должно занимать весь экран и не должно
        // вылезать за его пределы из-за фиксированных минимальных размеров.
        stage.setMinWidth(Math.min(1100.0, visualBounds.getWidth() * 0.9));
        stage.setMinHeight(Math.min(720.0, visualBounds.getHeight() * 0.9));
        boolean smallScreen = visualBounds.getWidth() < 1600.0 || visualBounds.getHeight() < 900.0;
        double width = smallScreen ? visualBounds.getWidth() * 0.92 : visualBounds.getWidth();
        double height = smallScreen ? visualBounds.getHeight() * 0.92 : visualBounds.getHeight();
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(visualBounds.getMinX() + (visualBounds.getWidth() - width) / 2.0);
        stage.setY(visualBounds.getMinY() + (visualBounds.getHeight() - height) / 2.0);
        stage.setOnCloseRequest(windowEvent -> {
            Platform.exit();
            System.exit(0);
        });
        stage.show();
        StartupLog.step("window shown - startup succeeded");
    }

    public void stop() {
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] stringArray) {
        // Журнал до всего остального: на части цеховых машин окно не появлялось,
        // и причину узнать было нечем — падение уходило в никуда.
        StartupLog.installGlobalHandler();
        StartupLog.begin(System.getProperty("jpackage.app-version"));
        System.setProperty("javafx.animation.pulse",
                System.getProperty("chekator.animation.pulse", "180"));
        System.setProperty("javafx.animation.fullspeed",
                System.getProperty("chekator.animation.fullspeed", "true"));
        System.setProperty("prism.vsync",
                System.getProperty("chekator.prism.vsync", "false"));
        System.setProperty("quantum.multithreaded",
                System.getProperty("chekator.quantum.multithreaded", "true"));
        try {
            StartupLog.step("handing control to JavaFX");
            Launcher.launch((String[])stringArray);
        } catch (Throwable error) {
            StartupLog.failure("JavaFX launch failed", error);
            throw error;
        }
    }
}
