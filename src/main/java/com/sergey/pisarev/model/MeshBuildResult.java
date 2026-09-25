package com.sergey.pisarev.model;

import javafx.scene.shape.TriangleMesh;

/**
 * Результат построения 3D-меша с описанием ошибки при неудаче.
 */
public final class MeshBuildResult {
    private final boolean success;
    private final TriangleMesh mesh;
    private final String message;
    private final int cuttingMoves;
    private final int profilePoints;

    private MeshBuildResult(boolean success, TriangleMesh mesh, String message, int cuttingMoves, int profilePoints) {
        this.success = success;
        this.mesh = mesh;
        this.message = message;
        this.cuttingMoves = cuttingMoves;
        this.profilePoints = profilePoints;
    }

    public static MeshBuildResult failure(String message) {
        return new MeshBuildResult(false, null, message, 0, 0);
    }

    public static MeshBuildResult success(TriangleMesh mesh, String message, int cuttingMoves, int profilePoints) {
        return new MeshBuildResult(true, mesh, message, cuttingMoves, profilePoints);
    }

    public boolean isSuccess() {
        return this.success;
    }

    public TriangleMesh getMesh() {
        return this.mesh;
    }

    public String getMessage() {
        return this.message;
    }

    public int getCuttingMoves() {
        return this.cuttingMoves;
    }

    public int getProfilePoints() {
        return this.profilePoints;
    }
}
