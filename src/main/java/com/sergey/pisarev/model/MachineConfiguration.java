package com.sergey.pisarev.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Параметры системы ЧПУ и станка для симуляции.
 */
public final class MachineConfiguration {
    public enum ControlSystem {
        SIEMENS_MILLING("Siemens Milling"),
        SIEMENS_TURNING("Siemens Turning"),
        FANUC("Fanuc");

        private final String displayName;

        ControlSystem(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    public enum MachineType {
        HAAS_CM1("Haas CM-1"),
        GENERIC_LATHE("Generic Lathe"),
        GENERIC_MILL("Generic Mill"),
        DMG_MORI_LATHE("DMG MORI (Siemens)"),
        /**
         * Карусельный (вертикальный) станок: деталь лежит на столе, ось вертикальная.
         *
         * <p>Так работают колёсные станки Hegenscheidt: колесо кладут на планшайбу,
         * инструмент подходит сверху. У обычного токарного ось горизонтальная, и модель
         * надо показывать иначе — иначе колесо стоит на ребре, как вал.
         */
        VERTICAL_LATHE("Carousel (vertical lathe)");

        private final String displayName;

        MachineType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    private final ControlSystem controlSystem;
    private final MachineType machineType;
    private final Set<Character> activeAxes;
    private final double blankDiameterMm;
    private final boolean useBlankDiameter;
    private final double cupRadiusMm;
    private final boolean useCupRadius;

    public MachineConfiguration(
            ControlSystem controlSystem,
            MachineType machineType,
            Set<Character> activeAxes,
            double blankDiameterMm,
            boolean useBlankDiameter
    ) {
        this(controlSystem, machineType, activeAxes, blankDiameterMm, useBlankDiameter, 0.0, false);
    }

    public MachineConfiguration(
            ControlSystem controlSystem,
            MachineType machineType,
            Set<Character> activeAxes,
            double blankDiameterMm,
            boolean useBlankDiameter,
            double cupRadiusMm,
            boolean useCupRadius
    ) {
        this.controlSystem = controlSystem;
        this.machineType = machineType;
        this.activeAxes = Set.copyOf(activeAxes);
        this.blankDiameterMm = blankDiameterMm;
        this.useBlankDiameter = useBlankDiameter;
        this.cupRadiusMm = Math.max(0.0, cupRadiusMm);
        this.useCupRadius = useCupRadius;
    }

    public ControlSystem getControlSystem() {
        return this.controlSystem;
    }

    public MachineType getMachineType() {
        return this.machineType;
    }

    public Set<Character> getActiveAxes() {
        return Set.copyOf(this.activeAxes);
    }

    public boolean isAxisActive(char axis) {
        return this.activeAxes.contains(Character.toUpperCase(axis));
    }

    public double getBlankDiameterMm() {
        return this.blankDiameterMm;
    }

    public boolean isUseBlankDiameter() {
        return this.useBlankDiameter;
    }

    public double getCupRadiusMm() {
        return this.cupRadiusMm;
    }

    public boolean isUseCupRadius() {
        return this.useCupRadius;
    }

    public boolean isLatheMode() {
        return this.controlSystem == ControlSystem.SIEMENS_TURNING
                || this.machineType == MachineType.GENERIC_LATHE
                || this.machineType == MachineType.VERTICAL_LATHE;
    }

    /** Карусельный станок: деталь лежит, ось вертикальная. */
    public boolean isVerticalLathe() {
        return this.machineType == MachineType.VERTICAL_LATHE;
    }

    public boolean isMillingMode() {
        return !this.isLatheMode();
    }
}
