package com.sergey.pisarev.ai;

import java.util.List;
import java.util.Map;

/**
 * Что нейросеть может делать с программой через MCP.
 *
 * <p>Узкий интерфейс между сервером MCP и приложением: сервер ничего не знает про
 * JavaFX и про контроллер, приложение ничего не знает про JSON-RPC. Реализация обязана
 * выполнять любое изменение в потоке JavaFX и так, чтобы результат был **виден в окне**:
 * подставленная программа появляется в редакторе, новый инструмент — в таблице,
 * настройка — в своём поле. Скрытых правок быть не должно, иначе наладчик не поймёт,
 * что изменила нейросеть.
 */
public interface AppAccess {

    /** Проблема программы: то же, что показывает плашка проверки. */
    record Problem(String code, String severity, String title, String details, int line, String fix) {
    }

    /**
     * Картинка либо причина, по которой её нет.
     *
     * <p>Пустой массив вместо ответа не годится: нейросеть должна узнать, что модель
     * не построилась именно из-за отсутствующей заготовки или пустой траектории, —
     * иначе она будет слепо повторять один и тот же запрос.
     */
    record Picture(byte[] png, String problem) {

        public static Picture of(byte[] png) {
            return new Picture(png, null);
        }

        public static Picture failed(String problem) {
            return new Picture(new byte[0], problem);
        }

        public boolean ok() {
            return png != null && png.length > 0;
        }
    }

    /** Текст программы из редактора. */
    String programText();

    /**
     * Заменяет программу целиком.
     *
     * @param reason зачем — попадёт в журнал MCP и в историю правок
     * @return короткий итог: сколько строк и что нашла проверка
     */
    String setProgramText(String text, String reason);

    /** Проверка программы: красные ошибки и жёлтые предупреждения. */
    List<Problem> checkProgram();

    /** Настройки станка, заготовки, эквидистанты, смещений и коррекций. */
    Map<String, Object> settings();

    /**
     * Меняет настройки. Ключи — как в {@link #settings()}; неизвестные игнорируются.
     *
     * @return что реально изменилось, по-человечески
     */
    List<String> applySettings(Map<String, Object> values);

    /** Библиотека инструментов: ячейка, название, номер T, кромка D, радиус, длины. */
    List<Map<String, Object>> toolLibrary();

    /**
     * Добавляет инструмент или правит существующий.
     *
     * <p>Адрес — ячейка магазина и кромка (в одной ячейке живут D1, D2…), либо номер T
     * и кромка. Переданы только те поля, которые надо изменить; остальные остаются как
     * были — это правка, а не пересоздание.
     */
    String saveTool(Map<String, Object> tool);

    /** Убирает инструмент из ячейки; кромка 0 — убрать все кромки этой ячейки. */
    String removeTool(int location, int edge);

    /** Разобранная траектория: габариты, ходы, вызовы T. */
    Map<String, Object> toolpath(int maxMoves);

    /** PNG двумерного графика — то же, что человек видит в окне. */
    byte[] renderGraph(int width, int height);

    /** PNG трёхмерной модели после снятия материала либо причина, почему её нет. */
    Picture render3d(int width, int height);

    /** Live window state, not a reconstructed final result. */
    default Map<String, Object> simulationState() { return Map.of("open", false); }

    default Picture simulationView(int width, int height) {
        return Picture.failed("Окно 3D не открыто");
    }

    default Map<String, Object> controlSimulation(Map<String, Object> arguments) {
        throw new IllegalStateException("Управление 3D недоступно");
    }

    /** Строка в журнал MCP — что сделала нейросеть. */
    void log(String line);
}
