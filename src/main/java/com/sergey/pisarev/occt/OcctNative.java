package com.sergey.pisarev.occt;

import com.sergey.pisarev.util.I18n;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * JNI-мост к Open CASCADE (chekator_occt.dll). Если библиотека не найдена — 3D строится на JavaFX.
 */
public final class OcctNative {
    /** 3D-меш через OCCT. Отключить: -Dchekator.occt.mesh=false */
    public static final boolean PREFER_OCCT_MESH =
            Boolean.parseBoolean(System.getProperty("chekator.occt.mesh", "true"));

    private static final String LIB_NAME = "chekator_occt";
    private static final String WINPATH_LIB_NAME = "chekator_occt_winpath";
    private static volatile boolean loadAttempted;
    private static volatile boolean loaded;
    private static volatile String loadError = "";
    private static volatile boolean lastMeshBuildUsedOcct;
    /** Причина последнего сбоя построения меша в OCCT (пусто — сбоя не было). */
    private static volatile String lastMeshErrorMessage = "";

    private OcctNative() {
    }

    public static boolean isAvailable() {
        ensureLoaded();
        return loaded;
    }

    public static String loadErrorMessage() {
        ensureLoaded();
        return loadError;
    }

    public static boolean lastMeshBuildUsedOcct() {
        return lastMeshBuildUsedOcct;
    }

    public static void markMeshBuildUsedOcct(boolean used) {
        lastMeshBuildUsedOcct = used;
    }

    /** Текст сбоя OCCT, пришедший из нативного кода. Раньше причина просто терялась. */
    public static String meshErrorMessage() {
        return lastMeshErrorMessage;
    }

    public static void recordMeshError(String message) {
        lastMeshErrorMessage = message != null ? message : "";
    }

    /**
     * @param zMm ось Z программы
     * @param rMm радиус готовой поверхности
     * @param deflectionMm шаг триангуляции OCCT (меньше — точнее)
     * @return handle &gt; 0 или 0 при ошибке
     */
    public static native long buildRevolvedSolid(double[] zMm, double[] rMm, double deflectionMm);

    public static native long buildRevolvedSection(double[] zMm, double[] rMm, int[] loops, double deflectionMm);

    public static native int meshVertexCount(long handle);

    public static native int meshTriangleCount(long handle);

    /** xyz interleaved, length = 3 * vertexCount */
    public static native float[] meshPositions(long handle);

    /** triangle indices, length = 3 * triangleCount */
    public static native int[] meshIndices(long handle);

    public static native void releaseMesh(long handle);

    public static void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        synchronized (OcctNative.class) {
            if (loadAttempted) {
                return;
            }
            loadAttempted = true;
            try {
                Path occtBin = resolveOcctBinDirectory();
                if (occtBin == null) {
                    throw new UnsatisfiedLinkError(
                            I18n.text("occt.error.folder"));
                }
                Path bridge = occtBin.resolve(LIB_NAME + ".dll");
                if (!Files.isRegularFile(bridge)) {
                    throw new UnsatisfiedLinkError(I18n.format("occt.error.file", bridge));
                }
                loadWindowsDllSearchPath(occtBin);
                System.load(bridge.toAbsolutePath().toString());
                loaded = true;
                loadError = "";
            } catch (UnsatisfiedLinkError | RuntimeException error) {
                loaded = false;
                loadError = error.getMessage() != null ? error.getMessage() : error.toString();
                System.err.println("OCCT: " + loadError);
            }
        }
    }

    private static void loadWindowsDllSearchPath(Path occtBin) {
        if (!isWindows()) {
            return;
        }
        Path winpath = occtBin.resolve(WINPATH_LIB_NAME + ".dll");
        if (Files.isRegularFile(winpath)) {
            System.load(winpath.toAbsolutePath().toString());
            return;
        }
        preloadCoreDependencies(occtBin);
    }

    private static void preloadCoreDependencies(Path occtBin) {
        for (String name : new String[]{"tbb12.dll", "jemalloc.dll", "TKernel.dll"}) {
            Path dependency = occtBin.resolve(name);
            if (!Files.isRegularFile(dependency)) {
                continue;
            }
            try {
                System.load(dependency.toAbsolutePath().toString());
            } catch (UnsatisfiedLinkError ignored) {
                // already loaded
            }
        }
    }

    private static Path resolveOcctBinDirectory() {
        String explicit = System.getenv("CHEKATOR_OCCT_BIN");
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit);
            if (isOcctRuntimeDirectory(path)) {
                return path.toAbsolutePath();
            }
        }
        Path nearExe = Path.of(System.getProperty("user.dir", "."), "app", "occt", "win64");
        if (isOcctRuntimeDirectory(nearExe)) {
            return nearExe.toAbsolutePath();
        }
        Path nearJar = findNearExecutable("app", "occt", "win64");
        if (nearJar != null) {
            return nearJar;
        }
        Path dev = Path.of("launch", "CNC_Modeling", "app", "occt", "win64");
        if (isOcctRuntimeDirectory(dev)) {
            return dev.toAbsolutePath();
        }
        Path staging = Path.of("launch", "staging", "occt", "win64");
        if (isOcctRuntimeDirectory(staging)) {
            return staging.toAbsolutePath();
        }
        return null;
    }

    private static boolean isOcctRuntimeDirectory(Path directory) {
        return Files.isDirectory(directory)
                && Files.isRegularFile(directory.resolve("TKernel.dll"))
                && Files.isRegularFile(directory.resolve(LIB_NAME + ".dll"));
    }

    private static Path findNearExecutable(String... parts) {
        Path codeSource = codeSourcePath();
        if (codeSource == null) {
            return null;
        }
        Path dir = Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
        for (int depth = 0; depth < 8 && dir != null; depth++) {
            Path candidate = dir;
            for (String part : parts) {
                candidate = candidate.resolve(part);
            }
            if (isOcctRuntimeDirectory(candidate)) {
                return candidate.toAbsolutePath();
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static Path codeSourcePath() {
        URL location = OcctNative.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            return null;
        }
        try {
            return Path.of(location.toURI());
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return Path.of(location.getPath());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.US).contains("win");
    }

    public static Optional<Path> defaultOcctDirectory() {
        return Optional.ofNullable(resolveOcctBinDirectory());
    }
}
