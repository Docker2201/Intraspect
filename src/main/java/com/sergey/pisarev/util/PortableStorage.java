package com.sergey.pisarev.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Куда программа складывает пользовательские данные.
 *
 * <p>Intraspect задуман портативным: настройки, история правок, инструменты и последняя
 * программа лежат в папке {@code data} рядом с exe — то есть на флешке. На компьютере
 * ничего не создаётся, поэтому программу можно запускать с носителя на чужой машине.
 *
 * <p>Готовая сборка всегда использует {@code <папка exe>/data}, независимо от
 * рабочего каталога, старых файлов на ПК и реестра. Если запись невозможна,
 * папка остаётся прежней: переключения в профиль Windows нет.
 * Переопределение {@code chekator.data.dir} доступно только при запуске из классов
 * для разработки и изолированных тестов.
 */
public final class PortableStorage {
    private static final Logger LOGGER = Logger.getLogger(PortableStorage.class.getName());
    /** Объявлено ДО DATA_DIR: resolveDataDir() выставляет режим по ходу выбора папки. */
    private static StorageMode mode = StorageMode.PORTABLE;
    private static final Path DATA_DIR = resolveDataDir();

    /** Откуда в итоге читаются и куда пишутся данные. */
    public enum StorageMode {
        /** Папка data рядом с программой — обычный режим работы с флешки. */
        PORTABLE,
        /** Путь задан вручную через -Dchekator.data.dir. */
        OVERRIDE,
        /** Папка рядом с программой недоступна для записи; внешней подмены нет. */
        READ_ONLY
    }

    public static StorageMode mode() {
        return mode;
    }

    /** Короткое описание для интерфейса: где лежат данные и всё ли в порядке. */
    public static String describeStorage() {
        return switch (mode) {
            case PORTABLE -> I18n.text("storage.001") + DATA_DIR;
            case OVERRIDE -> I18n.text("storage.002") + DATA_DIR;
            case READ_ONLY -> I18n.text("storage.003") + DATA_DIR + I18n.text("storage.004");
        };
    }

    /**
     * То же описание, но всегда по-английски: строка уходит в intraspect-start.log,
     * который читают на разных машинах и вставляют в заявки, поэтому язык там один.
     */
    public static String describeStorageEnglish() {
        return switch (mode) {
            case PORTABLE -> "next to the program: " + DATA_DIR;
            case OVERRIDE -> "set by hand: " + DATA_DIR;
            case READ_ONLY -> "not writable: " + DATA_DIR + " - changes will be lost";
        };
    }

    private PortableStorage() {
    }

    public static Path dataDir() {
        return DATA_DIR;
    }

    public static Path file(String name) {
        return DATA_DIR.resolve(name);
    }

    /**
     * Атомарная запись файла данных.
     *
     * <p>Пишем во временный файл рядом с целью, сбрасываем на носитель и только потом
     * подменяем цель. Прежняя версия остаётся как {@code .bak}. Прямая запись в целевой
     * файл при обрыве — вынули флешку, отключили питание — оставляла бы обрезанный или
     * пустой файл настроек, инструментов или программы.
     */
    public static void writeAtomically(Path target, byte[] content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        if (Files.exists(target)) {
            try {
                Files.copy(target, backupPath(target), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                LOGGER.log(Level.FINE, "Резервная копия не создана: " + target, exception);
            }
        }
        try {
            Files.move(temp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void writeAtomically(Path target, String text, Charset charset) throws IOException {
        writeAtomically(target, (text == null ? "" : text).getBytes(charset));
    }

    /** Резервная копия предыдущей версии файла. */
    public static Path backupPath(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak");
    }

    /**
     * Откуда читать: обычно сам файл, но если он пропал или пуст после сбоя записи —
     * последняя целая копия. Иначе одна неудачная запись стирала бы данные навсегда.
     */
    public static Path readablePath(Path target) {
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 0L) {
                return target;
            }
            Path backup = backupPath(target);
            if (Files.isRegularFile(backup) && Files.size(backup) > 0L) {
                LOGGER.log(Level.WARNING, "Файл {0} повреждён или пуст — читаем резервную копию", target);
                return backup;
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.FINE, "Не удалось выбрать источник чтения для " + target, exception);
        }
        return target;
    }

    private static Path resolveDataDir() {
        Path packaged = packagedAppDir();
        if (packaged != null) {
            return selectDirectory(packaged.resolve("data"), StorageMode.PORTABLE);
        }
        String override = System.getProperty("chekator.data.dir");
        if (override != null && !override.isBlank()) {
            return selectDirectory(Path.of(override.trim()), StorageMode.OVERRIDE);
        }
        return selectDirectory(Path.of(System.getProperty("user.dir", ".")).resolve("data"),
                StorageMode.PORTABLE);
    }

    private static Path selectDirectory(Path directory, StorageMode writableMode) {
        Path absolute = directory.toAbsolutePath().normalize();
        mode = ensureWritable(absolute) ? writableMode : StorageMode.READ_ONLY;
        if (mode == StorageMode.READ_ONLY) {
            LOGGER.log(Level.WARNING, "Папка данных недоступна для записи: {0}", absolute);
        }
        return absolute;
    }

    /** EXE/JAR определяет свою папку, даже если запущен ярлыком с другим рабочим каталогом. */
    private static Path packagedAppDir() {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            try {
                Path parent = Path.of(appPath).toAbsolutePath().normalize().getParent();
                if (parent != null) {
                    return parent;
                }
            } catch (RuntimeException ignored) {
                LOGGER.log(Level.WARNING, "Неверный путь запуска jpackage: {0}", appPath);
            }
        }
        try {
            Path source = Path.of(PortableStorage.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(source) && source.toString().endsWith(".jar")) {
                Path parent = source.getParent();
                // jpackage places the application JAR in <exe>/app.
                return parent.getFileName().toString().equals("app") ? parent.getParent() : parent;
            }
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Не удалось определить папку JAR", exception);
        }
        return null;
    }

    private static boolean ensureWritable(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".write-probe-", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }
}
