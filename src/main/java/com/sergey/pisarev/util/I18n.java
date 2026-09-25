package com.sergey.pisarev.util;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Язык интерфейса: русский, английский, украинский.
 *
 * <p>Язык выбирается один раз при запуске и до конца работы не меняется: так надпись,
 * прочитанная в конструкторе перечисления или в статическом поле, не расходится с той,
 * что видна в меню. Поэтому смена языка в меню просит перезапустить программу.
 *
 * <p>Порядок выбора: сохранённый выбор пользователя → язык Windows → английский.
 * Английский именно как последний вариант: на станке с чешской или польской Windows
 * лучше показать понятный английский, чем русский, которого оператор может не знать.
 *
 * <p>Файлы лежат в {@code resources/i18n} и читаются как UTF-8: {@code ResourceBundle}
 * по умолчанию читает .properties в ISO-8859-1, и без явной кодировки текст рассыпался бы.
 *
 * <p>Ни один метод не бросает исключение: пропавший ключ показывается как есть, чтобы
 * программа продолжала работать даже с неполным переводом.
 */
public final class I18n {
    /** Языки, для которых есть перевод. */
    public static final String RUSSIAN = "ru";
    public static final String ENGLISH = "en";
    public static final String UKRAINIAN = "uk";
    /** Пустая строка в настройках значит «как в системе Windows». */
    public static final String AUTO = "";

    private static final String BASE = "i18n.messages";
    private static final String FALLBACK = ENGLISH;

    private static volatile ResourceBundle bundle;
    private static volatile String language;

    private I18n() {
    }

    /** Код языка, на котором сейчас показан интерфейс: ru, en или uk. */
    public static synchronized String language() {
        ensureLoaded();
        return language;
    }

    /** Выбор пользователя: код языка или {@link #AUTO}, если язык берётся из Windows. */
    public static String preference() {
        try {
            return normalizePreference(UserSettings.getInterfaceLanguage());
        } catch (Throwable ignored) {
            return AUTO;
        }
    }

    /**
     * Запоминает выбор пользователя. Действует со следующего запуска — уже созданные
     * надписи не переписываются, чтобы в окне не смешались два языка.
     */
    public static void setPreference(String code) {
        try {
            UserSettings.setInterfaceLanguage(normalizePreference(code));
        } catch (Throwable ignored) {
            // Настройки могут быть недоступны для записи (флешка «только чтение»).
        }
    }

    /** Язык, который выбрала бы программа сама, по языку Windows. */
    public static String systemLanguage() {
        try {
            return supported(Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
            return FALLBACK;
        }
    }

    /** Набор строк для {@code FXMLLoader}: подписи в FXML пишутся как {@code %ключ}. */
    public static synchronized ResourceBundle bundle() {
        ensureLoaded();
        return bundle;
    }

    /** Текст по ключу. Если ключа нет ни в одном файле, вернётся сам ключ. */
    public static String text(String key) {
        if (key == null) {
            return "";
        }
        try {
            return bundle().getString(key);
        } catch (RuntimeException ignored) {
            return key;
        }
    }

    /** Текст с подстановкой: те же {@code %s} и {@code %d}, что и в {@link String#format}. */
    public static String format(String key, Object... args) {
        String pattern = text(key);
        try {
            return String.format(Locale.US, pattern, args);
        } catch (RuntimeException ignored) {
            // Перевод с испорченным %-заполнителем не должен ронять окно.
            return pattern;
        }
    }

    /**
     * Текст на другом языке, а не на текущем.
     *
     * <p>Нужен ровно для одного случая: сообщение «язык переключён» показывается уже
     * на выбранном языке, иначе непонятно, сработало ли переключение.
     */
    public static String textIn(String code, String key) {
        ResourceBundle other = tryLoad(supported(code));
        if (other == null) {
            return text(key);
        }
        try {
            return other.getString(key);
        } catch (RuntimeException ignored) {
            return text(key);
        }
    }

    /** Название языка так, как оно пишется на самом этом языке. */
    public static String displayName(String code) {
        switch (supported(code)) {
            case RUSSIAN:
                return "Русский";
            case UKRAINIAN:
                return "Українська";
            default:
                return "English";
        }
    }

    private static void ensureLoaded() {
        if (bundle != null) {
            return;
        }
        String chosen = preference();
        if (chosen.isEmpty()) {
            chosen = systemLanguage();
        }
        language = chosen;
        bundle = load(chosen);
    }

    private static ResourceBundle load(String code) {
        ResourceBundle loaded = tryLoad(code);
        if (loaded == null && !FALLBACK.equals(code)) {
            loaded = tryLoad(FALLBACK);
            language = FALLBACK;
        }
        if (loaded == null) {
            // Совсем без файлов: пустой набор, ключи покажутся как есть.
            loaded = new ResourceBundle() {
                @Override
                protected Object handleGetObject(String key) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getKeys() {
                    return java.util.Collections.emptyEnumeration();
                }
            };
        }
        return loaded;
    }

    private static ResourceBundle tryLoad(String code) {
        try {
            // Начиная с Java 9 .properties читаются как UTF-8, отдельный Control не нужен.
            return ResourceBundle.getBundle(BASE, Locale.forLanguageTag(code));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalizePreference(String code) {
        if (code == null) {
            return AUTO;
        }
        String trimmed = code.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return AUTO;
        }
        return supported(trimmed);
    }

    /** Любой другой язык Windows сводится к английскому. */
    private static String supported(String code) {
        if (code == null) {
            return FALLBACK;
        }
        String lower = code.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith(RUSSIAN)) {
            return RUSSIAN;
        }
        if (lower.startsWith(UKRAINIAN)) {
            return UKRAINIAN;
        }
        return FALLBACK;
    }
}
