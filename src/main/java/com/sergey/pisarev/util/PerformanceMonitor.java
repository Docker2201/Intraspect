/*
 * Decompiled with CFR 0.152.
 */
package com.sergey.pisarev.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class PerformanceMonitor {
    private static final Logger LOGGER = Logger.getLogger(PerformanceMonitor.class.getName());
    private static final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<String, AtomicLong>();
    private static final Map<String, AtomicLong> operationTimes = new ConcurrentHashMap<String, AtomicLong>();
    private static final Map<String, Long> startTimes = new ConcurrentHashMap<String, Long>();
    private static volatile boolean enabled = true;
    private static volatile long startTime = System.currentTimeMillis();

    public static void startOperation(String string) {
        if (!enabled) {
            return;
        }
        startTimes.put(string, System.nanoTime());
    }

    public static void endOperation(String string2) {
        if (!enabled) {
            return;
        }
        Long l = startTimes.remove(string2);
        if (l != null) {
            long l2 = System.nanoTime() - l;
            operationCounts.computeIfAbsent(string2, string -> new AtomicLong(0L)).incrementAndGet();
            operationTimes.computeIfAbsent(string2, string -> new AtomicLong(0L)).addAndGet(l2);
        }
    }

    public static void recordOperation(String string2, long l) {
        if (!enabled) {
            return;
        }
        operationCounts.computeIfAbsent(string2, string -> new AtomicLong(0L)).incrementAndGet();
        operationTimes.computeIfAbsent(string2, string -> new AtomicLong(0L)).addAndGet(l);
    }

    public static long getOperationCount(String string) {
        AtomicLong atomicLong = operationCounts.get(string);
        return atomicLong != null ? atomicLong.get() : 0L;
    }

    public static long getOperationTime(String string) {
        AtomicLong atomicLong = operationTimes.get(string);
        return atomicLong != null ? atomicLong.get() : 0L;
    }

    public static double getAverageOperationTime(String string) {
        long l = PerformanceMonitor.getOperationCount(string);
        if (l == 0L) {
            return 0.0;
        }
        long l2 = PerformanceMonitor.getOperationTime(string);
        return (double)l2 / 1000000.0 / (double)l;
    }

    public static Map<String, OperationStats> getAllStats() {
        ConcurrentHashMap<String, OperationStats> concurrentHashMap = new ConcurrentHashMap<String, OperationStats>();
        for (String string : operationCounts.keySet()) {
            long l = PerformanceMonitor.getOperationCount(string);
            long l2 = PerformanceMonitor.getOperationTime(string);
            double d = PerformanceMonitor.getAverageOperationTime(string);
            concurrentHashMap.put(string, new OperationStats(l, l2, d));
        }
        return concurrentHashMap;
    }

    public static void printPerformanceReport() {
        if (!enabled) {
            return;
        }
        long l = System.currentTimeMillis() - startTime;
        Map<String, OperationStats> map = PerformanceMonitor.getAllStats();
        LOGGER.info("=== Performance Report ===");
        LOGGER.info("Uptime: " + PerformanceMonitor.formatDuration(l));
        LOGGER.info("Operations tracked: " + map.size());
        LOGGER.info("");
        map.entrySet().stream().sorted((entry, entry2) -> Long.compare(((OperationStats)entry2.getValue()).getTotalTime(), ((OperationStats)entry.getValue()).getTotalTime())).forEach(entry -> {
            OperationStats operationStats = (OperationStats)entry.getValue();
            LOGGER.info(String.format("%-30s | Count: %6d | Total: %8.2f ms | Avg: %6.2f ms", entry.getKey(), operationStats.getCount(), (double)operationStats.getTotalTime() / 1000000.0, operationStats.getAverageTime()));
        });
        LOGGER.info("==========================");
    }

    public static void clearStats() {
        operationCounts.clear();
        operationTimes.clear();
        startTimes.clear();
        startTime = System.currentTimeMillis();
    }

    public static void setEnabled(boolean bl) {
        enabled = bl;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static String formatDuration(long l) {
        long l2 = l / 1000L;
        long l3 = l2 / 60L;
        long l4 = l3 / 60L;
        long l5 = l4 / 24L;
        if (l5 > 0L) {
            return String.format("%dd %dh %dm %ds", l5, l4 % 24L, l3 % 60L, l2 % 60L);
        }
        if (l4 > 0L) {
            return String.format("%dh %dm %ds", l4, l3 % 60L, l2 % 60L);
        }
        if (l3 > 0L) {
            return String.format("%dm %ds", l3, l2 % 60L);
        }
        return String.format("%ds", l2);
    }

    public static OperationTimer time(String string) {
        return new OperationTimer(string);
    }

    public static class OperationStats {
        private final long count;
        private final long totalTime;
        private final double averageTime;

        public OperationStats(long l, long l2, double d) {
            this.count = l;
            this.totalTime = l2;
            this.averageTime = d;
        }

        public long getCount() {
            return this.count;
        }

        public long getTotalTime() {
            return this.totalTime;
        }

        public double getAverageTime() {
            return this.averageTime;
        }
    }

    public static class OperationTimer
    implements AutoCloseable {
        private final String operationName;

        public OperationTimer(String string) {
            this.operationName = string;
            PerformanceMonitor.startOperation(string);
        }

        @Override
        public void close() {
            PerformanceMonitor.endOperation(this.operationName);
        }
    }
}

