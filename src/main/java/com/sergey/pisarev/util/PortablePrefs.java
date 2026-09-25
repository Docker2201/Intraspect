package com.sergey.pisarev.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Замена {@link java.util.prefs.Preferences} с тем же набором методов, но хранящая
 * значения в обычном файле рядом с программой, а не в реестре Windows.
 *
 * <p>Так настройки уезжают вместе с флешкой и не оставляют следов на чужом компьютере.
 * Старые настройки в реестре Windows не читаются и не импортируются.
 */
public final class PortablePrefs {
    private static final Logger LOGGER = Logger.getLogger(PortablePrefs.class.getName());
    private final Path path;
    private final Properties values = new Properties();
    private volatile boolean dirty;

    private PortablePrefs(Path path) {
        this.path = path;
        this.load();
        Runtime.getRuntime().addShutdownHook(new Thread(this::flush, "chekator-prefs-flush"));
    }

    public static PortablePrefs open(String fileName) {
        return new PortablePrefs(PortableStorage.file(fileName));
    }

    public String get(String key, String def) {
        String value = this.values.getProperty(key);
        return value != null ? value : def;
    }

    public void put(String key, String value) {
        if (value == null) {
            this.values.remove(key);
        } else {
            this.values.setProperty(key, value);
        }
        this.dirty = true;
    }

    public int getInt(String key, int def) {
        try {
            String value = this.values.getProperty(key);
            return value != null ? Integer.parseInt(value.trim()) : def;
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    public void putInt(String key, int value) {
        this.put(key, Integer.toString(value));
    }

    public boolean getBoolean(String key, boolean def) {
        String value = this.values.getProperty(key);
        return value != null ? Boolean.parseBoolean(value.trim()) : def;
    }

    public void putBoolean(String key, boolean value) {
        this.put(key, Boolean.toString(value));
    }

    public double getDouble(String key, double def) {
        try {
            String value = this.values.getProperty(key);
            return value != null ? Double.parseDouble(value.trim()) : def;
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    public void putDouble(String key, double value) {
        this.put(key, Double.toString(value));
    }

    /** Записывает настройки на носитель (вызывается после каждого изменения). */
    public void flush() {
        if (!this.dirty) {
            return;
        }
        try {
            // Через временный файл: обрыв записи не должен оставить пустые настройки.
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            this.values.store(buffer, "Intraspect settings (портативные, хранятся рядом с программой)");
            PortableStorage.writeAtomically(this.path, buffer.toByteArray());
            this.dirty = false;
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Не удалось сохранить настройки в " + this.path, exception);
        }
    }

    private void load() {
        Path source = PortableStorage.readablePath(this.path);
        if (Files.isRegularFile(source)) {
            try (InputStream in = Files.newInputStream(source)) {
                this.values.load(in);
                return;
            } catch (IOException | RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Не удалось прочитать настройки " + this.path, exception);
            }
        }
    }
}
