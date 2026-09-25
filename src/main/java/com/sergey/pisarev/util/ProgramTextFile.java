package com.sergey.pisarev.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Чтение и запись файлов G-code с сохранением кодировки документа.
 *
 * <p>Раньше сохранение шло в CP1251, а открытие — только в UTF-8, поэтому программа
 * не могла прочитать собственный файл с русским комментарием. Теперь кодировка
 * определяется при чтении и используется при сохранении, так что цикл
 * «открыть → сохранить → открыть» не портит текст.
 *
 * <p>Определение: сначала BOM, затем строгая проверка на UTF-8; если байты под UTF-8
 * не подходят — это однобайтовая кириллица, читаем как CP1251.
 */
public final class ProgramTextFile {

    /** Кодировка по умолчанию для программ, не открытых из файла (как было раньше). */
    public static final Charset DEFAULT_CHARSET = Charset.forName("CP1251");

    /** Текст файла вместе с кодировкой, в которой он был записан. */
    public record Content(String text, Charset charset) {
    }

    private ProgramTextFile() {
    }

    public static Content read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);

        if (startsWith(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new Content(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, (byte) 0xFF, (byte) 0xFE)) {
            return new Content(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE),
                    StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, (byte) 0xFE, (byte) 0xFF)) {
            return new Content(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE),
                    StandardCharsets.UTF_16BE);
        }

        CharsetDecoder strictUtf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            String text = strictUtf8.decode(ByteBuffer.wrap(bytes)).toString();
            return new Content(text, StandardCharsets.UTF_8);
        } catch (CharacterCodingException notUtf8) {
            return new Content(new String(bytes, DEFAULT_CHARSET), DEFAULT_CHARSET);
        }
    }

    /** Запись в заданной кодировке; null — кодировка по умолчанию. */
    public static void write(Path path, String text, Charset charset) throws IOException {
        Charset effective = charset != null ? charset : DEFAULT_CHARSET;
        Files.write(path, (text == null ? "" : text).getBytes(effective));
    }

    private static boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; ++i) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
