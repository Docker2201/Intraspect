package com.sergey.pisarev.util;

import com.sergey.pisarev.model.MachineConfiguration;

import java.util.Locale;

/** Mesh Y stores NC Z; orient that axis to match the configured lathe. */
public final class MachineOrientation {
    private MachineOrientation() {
    }

    /** Карусельный станок по сохранённой настройке. */
    public static boolean isVerticalLathe() {
        return isVerticalLathe(UserSettings.getMachineType());
    }

    public static boolean isVerticalLathe(String storedMachineType) {
        if (storedMachineType == null || storedMachineType.isBlank()) {
            return false;
        }
        String name = storedMachineType.trim().toUpperCase(Locale.US);
        return name.equals(MachineConfiguration.MachineType.VERTICAL_LATHE.name());
    }

    /** JavaFX Y points down; machine +Z must point up on a vertical lathe. */
    public static javafx.scene.transform.Rotate modelRotation(boolean vertical) {
        return new javafx.scene.transform.Rotate(vertical ? 180.0 : 90.0,
                vertical ? javafx.scene.transform.Rotate.X_AXIS : javafx.scene.transform.Rotate.Z_AXIS);
    }

}
