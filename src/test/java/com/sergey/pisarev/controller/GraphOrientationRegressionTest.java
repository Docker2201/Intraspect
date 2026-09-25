package com.sergey.pisarev.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Двумерный график: у карусельного станка оси стоят иначе, чем у токарного.
 *
 * <p>На карусели деталь лежит на планшайбе: её ось вертикальна, и стойка рисует
 * программу так же — продольная ось (Z, а у канала на U/W это W) вверх, радиус
 * вправо. У горизонтальной токарки наоборот. Раньше график был один на оба
 * станка, и на карусели деталь лежала на боку.
 */
public final class GraphOrientationRegressionTest {
    public static void main(String[] args) throws Exception {
        MainController controller = new MainController();
        set(controller, "axisMode", axisMode("XZ"));
        set(controller, "previewLayoutOriginX", 100.0);
        set(controller, "previewLayoutOriginY", 500.0);
        set(controller, "previewLayoutScale", 2.0);
        set(controller, "previewLayoutMinZ", 0.0);
        set(controller, "previewLayoutMaxX", 400.0);

        // --- горизонтальная токарка: Z вправо, радиус вверх ---
        set(controller, "verticalLatheGraph", false);
        near("токарка: по горизонтали Z", path(controller, "pathHorizontal", 200, -50), -50);
        near("токарка: по вертикали радиус", path(controller, "pathVertical", 200, -50), 100);
        check("токарка: рост Z уводит вправо",
                screenX(controller, 200, 10) > screenX(controller, 200, -10));
        check("токарка: рост диаметра уводит вверх",
                screenY(controller, 300, 0) < screenY(controller, 100, 0));
        set(controller, "appliedProgramText", "N10 G1 X=100 Z=50 F100\n");
        equals("токарка: по горизонтали подписана Z", label(controller, "horizontalAxisLabel"), "Z");
        equals("токарка: по вертикали подписан диаметр", label(controller, "verticalAxisLabel"), "XØ");

        // --- карусель: радиус вправо, ось детали вверх ---
        set(controller, "verticalLatheGraph", true);
        near("карусель: по горизонтали радиус", path(controller, "pathHorizontal", 200, -50), 100);
        near("карусель: по вертикали ось детали", path(controller, "pathVertical", 200, -50), -50);
        check("карусель: рост диаметра уводит вправо",
                screenX(controller, 300, 0) > screenX(controller, 100, 0));
        check("карусель: рост Z уводит вверх",
                screenY(controller, 200, 10) < screenY(controller, 200, -10));
        equals("карусель: по горизонтали подписан диаметр", label(controller, "horizontalAxisLabel"), "XØ");
        equals("карусель: по вертикали подписана Z", label(controller, "verticalAxisLabel"), "Z");

        // У канала на осях U/W подписи те же буквы, что в программе.
        set(controller, "appliedProgramText", "N10 G1 U=100 W=50 F100\n");
        equals("карусель: канал U/W подписан U", label(controller, "horizontalAxisLabel"), "UØ");
        equals("карусель: слева стоит W", label(controller, "verticalAxisLabel"), "W");

        // Каналы стоят по разные стороны оси вращения, как суппорты на станке:
        // знак радиуса и есть сторона, а на шкале с обеих сторон диаметры.
        near("карусель: канал слева уходит влево от оси", path(controller, "pathHorizontal", -200, 0), -100);
        check("карусель: левый канал левее оси",
                screenX(controller, -200, 0) < screenX(controller, 0, 0));
        check("карусель: правый канал правее оси",
                screenX(controller, 200, 0) > screenX(controller, 0, 0));
        near("карусель: стороны симметричны относительно оси",
                screenX(controller, 200, 0) - screenX(controller, 0, 0),
                screenX(controller, 0, 0) - screenX(controller, -200, 0));
        near("карусель: слева на шкале диаметр без знака",
                display(controller, -200), display(controller, 200));

        // Сторона берётся у самой программы, а не у числа открытых окон: канал 2
        // работает слева от оси и тогда, когда на экране он один.
        near("сторона по шапке: канал 2 слева",
                side(controller, ";Channel       : 2 (left)\nN10 G1 U=100 W=5 F100\n"), -1);
        near("сторона по шапке: канал 1 справа",
                side(controller, ";Channel       : 1 (right)\nN10 G1 X=100 Z=5 F100\n"), 1);
        near("без шапки сторону выдают оси U/W",
                side(controller, "N10 G1 U=100 W=5 F100\n"), -1);
        near("без шапки оси X/Z — правая сторона",
                side(controller, "N10 G1 X=100 Z=5 F100\n"), 1);

        // Шкала и траектория должны совпадать: перевод значения оси в экран и обратно.
        double diameter = 260.0;
        double screen = invokeDouble(controller, "horizontalValueToScreenX", diameter);
        near("карусель: шкала диаметров ложится на траекторию",
                invokeDouble(controller, "screenToPreviewHorizontal", screen), diameter);
        near("карусель: диаметр на шкале и радиус в пути — одна точка",
                screen, screenX(controller, diameter, 0));
        double height = 42.0;
        double screenY = invokeDouble(controller, "verticalValueToScreenY", height);
        near("карусель: шкала по оси детали совпадает с траекторией",
                screenY, screenY(controller, 200, height));

        System.out.println("Graph orientation regressions passed.");
    }

    private static double path(MainController controller, String name, double x, double z) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name, double.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, x, z);
    }

    private static double screenX(MainController controller, double x, double z) throws Exception {
        Method method = MainController.class.getDeclaredMethod("toScreenX",
                double.class, double.class, double.class, double.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, 100.0, x, z, 0.0, 2.0);
    }

    private static double screenY(MainController controller, double x, double z) throws Exception {
        Method method = MainController.class.getDeclaredMethod("toScreenY",
                double.class, double.class, double.class, double.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, 500.0, x, z, 400.0, 2.0);
    }

    /** Сторона оси вращения для программы: -1 слева, +1 справа. */
    private static double side(MainController controller, String program) throws Exception {
        Method method = MainController.class.getDeclaredMethod("graphSupportSide", String.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, program);
    }

    /** Число, которое встанет на шкале для этого значения диаметральной оси. */
    private static double display(MainController controller, double value) throws Exception {
        Method method = MainController.class.getDeclaredMethod("axisDisplayValue", double.class, boolean.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, value, true);
    }

    private static double invokeDouble(MainController controller, String name, double value) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name, double.class);
        method.setAccessible(true);
        return (double) method.invoke(controller, value);
    }

    private static String label(MainController controller, String name) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (String) method.invoke(controller);
    }

    private static Object axisMode(String name) throws Exception {
        Class<?> type = Class.forName("com.sergey.pisarev.controller.MainController$AxisMode");
        for (Object value : type.getEnumConstants()) {
            if (value.toString().equals(name)) return value;
        }
        throw new AssertionError("режим осей " + name + " не найден");
    }

    private static void set(MainController controller, String name, Object value) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static void near(String label, double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-6) {
            throw new AssertionError(label + ": " + actual + " вместо " + expected);
        }
        System.out.println("PASS " + label);
    }

    private static void equals(String label, String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": «" + actual + "» вместо «" + expected + "»");
        }
        System.out.println("PASS " + label);
    }

    private static void check(String label, boolean ok) {
        if (!ok) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
}
