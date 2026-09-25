package com.sergey.pisarev.util;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Иконка приложения для дочерних окон.
 */
public final class AppIconHelper {
    private static final Logger LOGGER = Logger.getLogger(AppIconHelper.class.getName());

    private AppIconHelper() {
    }

    public static void applyToStage(Stage stage, Window owner) {
        if (stage == null) {
            return;
        }
        if (owner instanceof Stage ownerStage && !ownerStage.getIcons().isEmpty()) {
            stage.getIcons().setAll(ownerStage.getIcons());
            return;
        }
        Image resourceIcon = loadResourceIcon();
        if (resourceIcon != null) {
            stage.getIcons().setAll(resourceIcon);
            return;
        }
        try {
            File iconFile = new File("icon.ico");
            if (iconFile.isFile()) {
                stage.getIcons().setAll(new Image(iconFile.toURI().toString()));
            }
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Stage icon not set", exception);
        }
    }

    private static Image loadResourceIcon() {
        java.net.URL url = AppIconHelper.class.getResource("/icon.ico");
        if (url == null) {
            url = AppIconHelper.class.getResource("/app-icon.png");
        }
        return url != null ? new Image(url.toExternalForm()) : null;
    }
}
