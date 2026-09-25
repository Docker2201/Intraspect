package com.sergey.pisarev.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Журнал правок текста программы: построчный дифф, сохранение между запусками.
 */
public final class ProgramEditHistoryStore {
    private static final Logger LOGGER = Logger.getLogger(ProgramEditHistoryStore.class.getName());
    /** История правок хранится рядом с программой (на флешке), а не в профиле пользователя. */
    private static final String STORAGE_FILE = "program_edit_history.log";
    private static final Path STORAGE = PortableStorage.file(STORAGE_FILE);

    private static final long MAX_FILE_BYTES = 300L * 1024L * 1024L;
    private static final long MAX_DP_CELLS = 1_200_000L;
    private static final Base64.Encoder B64ENC = Base64.getEncoder();
    private static final Base64.Decoder B64DEC = Base64.getDecoder();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Object FILE_LOCK = new Object();
    private static final String NOTICE_PREFIX = "#NOTICE|trimmed|";
    // Текст предупреждения берётся на языке интерфейса при чтении (history.notice.trimmed).
    /** Метка последнего токена записи — полный снимок текста ДО правки (для точного отката). */
    private static final String SNAPSHOT_MARK = "@";

    public void record(String oldFull, String newFull) {
        if (Objects.equals(oldFull, newFull)) {
            return;
        }
        String[] a = lines(oldFull);
        String[] b = lines(newFull);
        List<String[]> changes = diffLines(a, b);
        if (changes.isEmpty()) {
            return;
        }
        long ts = System.currentTimeMillis();
        try {
            Files.createDirectories(STORAGE.getParent());
            String line = encodeEntry(ts, changes, oldFull);
            synchronized (FILE_LOCK) {
                Files.writeString(STORAGE, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                trimFileIfNeeded();
            }
        }
        catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Не удалось записать историю правок", ex);
        }
    }

    public String renderNewestFirst() {
        try {
            synchronized (FILE_LOCK) {
                if (!Files.isRegularFile(STORAGE)) {
                    return I18n.text("history.empty");
                }
                List<String> lines = Files.readAllLines(STORAGE, StandardCharsets.UTF_8);
                Collections.reverse(lines);
                StringBuilder sb = new StringBuilder();
                boolean hasTrimNotice = false;
                for (String raw : lines) {
                    if (raw.isBlank()) {
                        continue;
                    }
                    if (raw.startsWith(NOTICE_PREFIX)) {
                        hasTrimNotice = true;
                        continue;
                    }
                    appendDecodedEntry(sb, raw);
                    sb.append('\n');
                }
                if (hasTrimNotice) {
                    sb.insert(0, "!! " + I18n.text("history.notice.trimmed") + "\n\n");
                }
                if (sb.length() == 0) {
                    return I18n.text("history.empty");
                }
                return sb.toString();
            }
        }
        catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Не удалось прочитать историю правок", ex);
            return I18n.text("history.read.error");
        }
    }

    public void clear() {
        try {
            synchronized (FILE_LOCK) {
                if (Files.exists(STORAGE)) {
                    Files.delete(STORAGE);
                }
            }
        }
        catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Не удалось очистить историю правок", ex);
        }
    }

    public boolean deleteChange(long timestamp, String oldLine, String newLine) {
        try {
            synchronized (FILE_LOCK) {
                if (!Files.isRegularFile(STORAGE)) {
                    return false;
                }
                List<String> lines = Files.readAllLines(STORAGE, StandardCharsets.UTF_8);
                boolean changed = false;
                for (int i = 0; i < lines.size(); ++i) {
                    String raw = lines.get(i);
                    if (raw == null || raw.isBlank() || raw.startsWith(NOTICE_PREFIX)) {
                        continue;
                    }
                    String[] parts = raw.split("\\|", -1);
                    if (parts.length < 3) {
                        continue;
                    }
                    long ts = Long.parseLong(parts[0]);
                    if (ts / 1000L != timestamp / 1000L) {
                        continue;
                    }
                    int removeAt = -1;
                    for (int p = 1; p + 1 < parts.length; p += 2) {
                        String oldDecoded = decode(parts[p]);
                        String newDecoded = decode(parts[p + 1]);
                        if (Objects.equals(oldDecoded, oldLine) && Objects.equals(newDecoded, newLine)) {
                            removeAt = p;
                            break;
                        }
                    }
                    if (removeAt < 0) {
                        continue;
                    }
                    ArrayList<String[]> pairs = new ArrayList<>();
                    for (int p = 1; p + 1 < parts.length; p += 2) {
                        if (p == removeAt) {
                            continue;
                        }
                        pairs.add(new String[]{decode(parts[p]), decode(parts[p + 1])});
                    }
                    if (pairs.isEmpty()) {
                        lines.remove(i);
                    } else {
                        lines.set(i, encodeEntry(ts, pairs, snapshotFromParts(parts)).trim());
                    }
                    changed = true;
                    break;
                }
                if (!changed) {
                    return false;
                }
                Files.write(STORAGE, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                return true;
            }
        }
        catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Не удалось удалить элемент истории", ex);
            return false;
        }
    }

    private static void appendDecodedEntry(StringBuilder sb, String raw) {
        try {
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 1) {
                return;
            }
            long ts = Long.parseLong(parts[0]);
            sb.append("==== ").append(TIME_FMT.format(Instant.ofEpochMilli(ts))).append('\n');
            for (int i = 1; i + 1 < parts.length; i += 2) {
                String oldLine = decode(parts[i]);
                String newLine = decode(parts[i + 1]);
                sb.append(formatLine(oldLine, newLine)).append('\n');
            }
        }
        catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "Пропуск повреждённой строки журнала", ex);
        }
    }

    private static String decode(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return "";
        }
        return new String(B64DEC.decode(b64), StandardCharsets.UTF_8);
    }

    private static String formatLine(String oldLine, String newLine) {
        String o = oldLine.isEmpty() ? I18n.text("history.line.empty") : oldLine;
        String n = newLine.isEmpty() ? I18n.text("history.line.deleted") : newLine;
        return o + " \u2192 " + n;
    }

    private static String encodeEntry(long ts, List<String[]> changes) {
        return encodeEntry(ts, changes, null);
    }

    private static String encodeEntry(long ts, List<String[]> changes, String oldSnapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append(ts);
        for (String[] pair : changes) {
            sb.append('|')
                    .append(B64ENC.encodeToString(pair[0].getBytes(StandardCharsets.UTF_8)))
                    .append('|')
                    .append(B64ENC.encodeToString(pair[1].getBytes(StandardCharsets.UTF_8)));
        }
        // Снимок «до правки» добавляется ПОСЛЕДНИМ токеном с меткой — парные циклы
        // (i+=2) его не задевают, а откат блока берёт по нему точное состояние.
        if (oldSnapshot != null) {
            sb.append('|').append(SNAPSHOT_MARK)
                    .append(B64ENC.encodeToString(oldSnapshot.getBytes(StandardCharsets.UTF_8)));
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String snapshotFromParts(String[] parts) {
        if (parts == null || parts.length < 2) {
            return null;
        }
        String last = parts[parts.length - 1];
        if (last == null || !last.startsWith(SNAPSHOT_MARK)) {
            return null;
        }
        try {
            return new String(B64DEC.decode(last.substring(SNAPSHOT_MARK.length())), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Снимок текста программы ДО блока с указанной секундой времени (или null, если записи без снимка). */
    public String oldSnapshotForSecond(long timestampMillis) {
        try {
            synchronized (FILE_LOCK) {
                if (!Files.isRegularFile(STORAGE)) {
                    return null;
                }
                List<String> lines = Files.readAllLines(STORAGE, StandardCharsets.UTF_8);
                for (String raw : lines) {
                    if (raw == null || raw.isBlank() || raw.startsWith(NOTICE_PREFIX)) {
                        continue;
                    }
                    int bar = raw.indexOf('|');
                    String tsStr = bar < 0 ? raw : raw.substring(0, bar);
                    long ts;
                    try {
                        ts = Long.parseLong(tsStr.trim());
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (ts / 1000L == timestampMillis / 1000L) {
                        String snap = snapshotFromParts(raw.split("\\|", -1));
                        if (snap != null) {
                            return snap;
                        }
                    }
                }
                return null;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Не удалось прочитать снимок истории", ex);
            return null;
        }
    }

    private static void trimFileIfNeeded() throws IOException {
        if (!Files.isRegularFile(STORAGE)) {
            return;
        }
        if (Files.size(STORAGE) <= MAX_FILE_BYTES) {
            return;
        }
        List<String> lines = Files.readAllLines(STORAGE, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return;
        }
        long bytes = estimateUtf8Bytes(lines);
        while (bytes > MAX_FILE_BYTES && lines.size() > 1) {
            lines.remove(0);
            bytes = estimateUtf8Bytes(lines);
        }
        if (!lines.isEmpty() && lines.get(0).startsWith(NOTICE_PREFIX)) {
            lines.remove(0);
        }
        lines.add(0, NOTICE_PREFIX + System.currentTimeMillis());
        Files.write(STORAGE, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static long estimateUtf8Bytes(List<String> lines) {
        long total = 0L;
        for (String line : lines) {
            total += line.getBytes(StandardCharsets.UTF_8).length + 1L;
        }
        return total;
    }

    private static String[] lines(String s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }
        return s.split("\\R", -1);
    }

    private static List<String[]> diffLines(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        if (n == 0 && m == 0) {
            return Collections.emptyList();
        }
        if ((long) n * (long) m > MAX_DP_CELLS) {
            return Collections.singletonList(new String[]{"\u2026", I18n.format("history.too.large", n, m)});
        }
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; --i) {
            for (int j = m - 1; j >= 0; --j) {
                if (a[i].equals(b[j])) {
                    dp[i][j] = 1 + dp[i + 1][j + 1];
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        ArrayList<String[]> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                ++i;
                ++j;
                continue;
            }
            if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(new String[]{a[i++], ""});
            } else {
                out.add(new String[]{"", b[j++]});
            }
        }
        while (i < n) {
            out.add(new String[]{a[i++], ""});
        }
        while (j < m) {
            out.add(new String[]{"", b[j++]});
        }
        return mergeAdjacentChanges(out);
    }

    private static List<String[]> mergeAdjacentChanges(List<String[]> ops) {
        if (ops.isEmpty()) {
            return ops;
        }
        ArrayList<String[]> merged = new ArrayList<>(ops.size());
        int i = 0;
        while (i < ops.size()) {
            String[] cur = ops.get(i);
            if (i + 1 < ops.size()) {
                String[] next = ops.get(i + 1);
                boolean curDelete = !cur[0].isEmpty() && cur[1].isEmpty();
                boolean curInsert = cur[0].isEmpty() && !cur[1].isEmpty();
                boolean nextDelete = !next[0].isEmpty() && next[1].isEmpty();
                boolean nextInsert = next[0].isEmpty() && !next[1].isEmpty();
                if (curDelete && nextInsert) {
                    merged.add(new String[]{cur[0], next[1]});
                    i += 2;
                    continue;
                }
                if (curInsert && nextDelete) {
                    merged.add(new String[]{next[0], cur[1]});
                    i += 2;
                    continue;
                }
            }
            merged.add(cur);
            ++i;
        }
        return merged;
    }
}
