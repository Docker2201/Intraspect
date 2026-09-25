package com.sergey.pisarev.service;

import com.sergey.pisarev.model.GCodeMoveData;
import com.sergey.pisarev.model.MachineConfiguration;
import com.sergey.pisarev.model.SimulationContext;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SinumerikCycleTimeEstimator {
    private static final double RAPID_FEED_MM_PER_MIN = 12_000.0;
    private static final double DEFAULT_LATHE_FEED_MM_PER_REV = 0.25;
    private static final double DEFAULT_LINEAR_FEED_MM_PER_MIN = 100.0;
    private static final double DEFAULT_RPM = 500.0;
    private static final double TURRET_TOOL_CHANGE_SECONDS = 8.0;
    private static final double MILLING_TOOL_CHANGE_SECONDS = 10.0;
    private static final double SPINDLE_START_SECONDS = 4.0;
    private static final double SPINDLE_STOP_SECONDS = 1.5;
    private static final double COOLANT_SWITCH_SECONDS = 0.8;
    private static final double MACHINE_AUX_SECONDS = 1.2;
    private static final double CUT_BLOCK_SETTLE_SECONDS = 0.085;
    private static final double RAPID_BLOCK_SETTLE_SECONDS = 0.035;
    private static final String NUMBER = "[-+]?(?:\\d+(?:[\\.,]\\d*)?|[\\.,]\\d+)";
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\b(LIMS(?:\\[\\d+\\])?)\\s*=\\s*(" + NUMBER + ")"
                    + "|\\b(S(?:\\[\\d+\\]|\\d+)?)\\s*=\\s*(" + NUMBER + ")"
                    + "|\\b([GFS])\\s*=?\\s*(" + NUMBER + ")(?!\\s*=)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SET_MASTER_SPINDLE_PATTERN =
            Pattern.compile("\\bSETMS\\s*\\(\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVR_PATTERN =
            Pattern.compile("\\bOVR\\s*=\\s*(" + NUMBER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern M_CODE_PATTERN =
            Pattern.compile("\\bM\\s*(" + NUMBER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern T_CODE_PATTERN =
            Pattern.compile("\\bT\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern G4_PATTERN =
            Pattern.compile("\\bG\\s*0*4\\b", Pattern.CASE_INSENSITIVE);

    private SinumerikCycleTimeEstimator() {
    }

    /**
     * @param approximate часть ходов посчитана с дефолтной подачей/оборотами
     *                    (в программе не нашлось F или S) — время показываем с «~»
     */
    public record Estimate(double seconds, boolean known, boolean approximate) {
        public Estimate(double seconds, boolean known) {
            this(seconds, known, false);
        }

        public static Estimate unknown() {
            return new Estimate(0.0, false, false);
        }
    }

    private record ModalState(
            boolean feedPerRev,
            boolean constantSurfaceSpeed,
            double feed,
            boolean feedKnown,
            double spindle,
            boolean spindleKnown,
            double rpmLimit,
            int masterSpindle,
            double pathOverride
    ) {
    }

    /** Motion-only clock; PLC waits, acceleration and tool-change travel are not reconstructed. */
    public record Motion(double seconds, double signedRpm, boolean spindleCommanded, boolean approximate) { }

    /** Resolve F/S/LIMS expressions once, preserving source lines for all downstream lookup. */
    public static String resolveMotionExpressions(String text, java.util.Map<String, Double> seed) {
        if (text == null || text.isBlank()) return text;
        var variables = new java.util.TreeMap<String, Double>(seed);
        var pattern = Pattern.compile("\\b(?:F|S(?:\\[\\d+]|\\d+)?|LIMS(?:\\[\\d+])?)\\s*=\\s*"
                + "([^;]+?)(?=\\s+[A-Z_$][A-Z0-9_$]*(?:\\s*=|\\s+[0-9])|\\s+[FGMSTD][0-9]|;|$)", Pattern.CASE_INSENSITIVE);
        var result = new java.util.ArrayList<String>();
        for (String raw : text.split("\\R", -1)) {
            MachineParameterProgram.collectAssignments(raw, variables, null, false);
            GCodeExpressionEvaluator.collectRDefinitions(raw, variables);
            int semi=raw.indexOf(';'); String code=semi<0?raw:raw.substring(0,semi);
            var matcher=pattern.matcher(code); var out=new StringBuilder(); int previous=0;
            while (matcher.find()) {
                var unknown=new java.util.HashSet<String>();
                Double value=GCodeExpressionEvaluator.evaluate(matcher.group(1),variables,unknown);
                out.append(code,previous,matcher.start(1));
                out.append(value!=null && Double.isFinite(value) && unknown.isEmpty()?value.toString():matcher.group(1));
                previous=matcher.end(1);
            }
            out.append(code.substring(previous)); if(semi>=0)out.append(raw.substring(semi));
            result.add(out.toString());
        }
        return String.join("\n",result);
    }

    public static List<Motion> motionClock(SimulationContext context, List<GCodeMoveData> moves) {
        String[] lines = context.getProgramText().split("\\R", -1);
        int boundary = MultiChannelSimulation.channelBoundaryLine(context.getProgramText());
        ModalState[] states = {defaultState(context), defaultState(context)};
        int[] processed = {0, Math.min(lines.length, boundary)};
        int[] direction = {0, 0};
        boolean[] spindleCommanded = {false, false};
        var result = new java.util.ArrayList<Motion>(moves.size());
        for (var move : moves) {
            int channel = move.sourceLine() > boundary ? 1 : 0;
            while (processed[channel] < Math.min(move.sourceLine(), lines.length)) {
                String line = lines[processed[channel]++];
                states[channel] = applyLine(states[channel], line, move.startX());
                String code = GCodeProgramParser.stripCommentsForParse(line);
                Matcher m = M_CODE_PATTERN.matcher(code);
                while (m.find()) {
                    int value = (int) parseDouble(m.group(1));
                    if (value == 3 || value == 4 || value == 5) spindleCommanded[channel] = true;
                    if (value == 3) direction[channel] = 1;
                    else if (value == 4) direction[channel] = -1;
                    else if (value == 5 || value == 2 || value == 30) direction[channel] = 0;
                }
            }
            ModalState state = states[channel];
            double feed = move.rapid() ? RAPID_FEED_MM_PER_MIN * context.getFeedRateMultiplier()
                    : feedMmPerMinute(state, move, context.getFeedRateMultiplier(), context.getSpindleSpeedMultiplier());
            boolean approximate = !Double.isFinite(feed) || feed <= 1e-6;
            if (approximate) feed = fallbackFeedMmPerMinute(state, context.getFeedRateMultiplier(), context.getSpindleSpeedMultiplier());
            double rpm = state.spindleKnown() ? averageRpmForMove(state, move, context.getSpindleSpeedMultiplier()) : 0;
            result.add(new Motion(Math.max(1e-8, moveLengthMm(context, move) * 60 / Math.max(1e-6, feed)),
                    direction[channel] * rpm, spindleCommanded[channel], approximate));
        }
        return List.copyOf(result);
    }

    public static Estimate estimate(SimulationContext context, List<GCodeMoveData> moves) {
        if (context == null || moves == null || moves.isEmpty()) {
            return Estimate.unknown();
        }
        String marker="\n"+MultiChannelSimulation.CHANNEL_BOUNDARY+"\n";
        int boundary=context.getProgramText().indexOf(marker);
        if(boundary>=0) {
            String first=context.getProgramText().substring(0,boundary);
            String second=context.getProgramText().substring(boundary+marker.length());
            int offset=MultiChannelSimulation.secondChannelLineOffset(first);
            Estimate a=estimateChannel(context,moves,first,offset,false);
            Estimate b=estimateChannel(context,moves,second,offset,true);
            // PLC waits and channel synchronization are not available in this viewer.
            return new Estimate(Math.max(a.seconds(),b.seconds()),a.known()||b.known(),true);
        }
        double feedOverride = context.getFeedRateMultiplier();
        double spindleOverride = context.getSpindleSpeedMultiplier();
        if (feedOverride <= 0.0 || spindleOverride <= 0.0) {
            return Estimate.unknown();
        }

        String[] programLines = context.getProgramText().split("\\R", -1);
        ModalState state = defaultState(context);
        int processedLine = 0;
        int lastMotionSourceLine = -1;
        double seconds = programNonMotionSeconds(programLines);
        boolean known = true;
        for (GCodeMoveData move : moves) {
            int sourceLine = Math.max(0, move.sourceLine());
            int sourceLineIndex = Math.max(0, sourceLine - 1);
            while (processedLine <= sourceLineIndex && processedLine < programLines.length) {
                state = applyLine(state, programLines[processedLine], move.startX());
                processedLine++;
            }
            double lengthMm = moveLengthMm(context, move);
            if (lengthMm < 1.0e-6) {
                continue;
            }
            double feedMmPerMin = move.rapid()
                    ? RAPID_FEED_MM_PER_MIN * feedOverride
                    : feedMmPerMinute(state, move, feedOverride, spindleOverride);
            if (!Double.isFinite(feedMmPerMin) || feedMmPerMin <= 1.0e-6) {
                // В программе нет F или S для этого хода (например, строки удалены) —
                // считаем по консервативным дефолтам, а время помечаем «приблизительно»,
                // вместо того чтобы показывать прочерк.
                feedMmPerMin = fallbackFeedMmPerMinute(state, feedOverride, spindleOverride);
                known = false;
            }
            if (!Double.isFinite(feedMmPerMin) || feedMmPerMin <= 1.0e-6) {
                continue;
            }
            seconds += lengthMm / feedMmPerMin * 60.0;
            if (sourceLine != lastMotionSourceLine) {
                seconds += move.rapid() ? RAPID_BLOCK_SETTLE_SECONDS : CUT_BLOCK_SETTLE_SECONDS;
                lastMotionSourceLine = sourceLine;
            }
        }
        boolean usable = seconds > 0.0 && Double.isFinite(seconds);
        return new Estimate(seconds, usable, usable && !known);
    }

    private static Estimate estimateChannel(SimulationContext context,List<GCodeMoveData> moves,
                                             String text,int offset,boolean second) {
        var selected=new java.util.ArrayList<GCodeMoveData>();
        for(var m:moves) if((m.sourceLine()>offset)==second)
            selected.add(new GCodeMoveData(m.startX(),m.startZ(),m.endX(),m.endZ(),m.rapid(),
                    m.sourceLine()-(second?offset:0),m.arcSegment(),m.toolNumber(),m.edgeNumber(),m.arcEnd()));
        var channel=new SimulationContext(selected,context.getMachineConfiguration(),context.getWorkpiece(),
                context.getTools(),context.getFeedOverridePercent(),context.getSpindleOverridePercent(),text,true);
        return estimate(channel,selected);
    }

    /** Дефолтная скорость подачи, когда F/S в программе не заданы. */
    private static double fallbackFeedMmPerMinute(
            ModalState state,
            double feedOverride,
            double spindleOverride
    ) {
        double effectiveFeedOverride = Math.max(0.01, feedOverride * state.pathOverride());
        if (!state.feedPerRev()) {
            double feed = state.feedKnown() ? state.feed() : DEFAULT_LINEAR_FEED_MM_PER_MIN;
            return feed * effectiveFeedOverride;
        }
        double feedPerRev = state.feedKnown() ? state.feed() : DEFAULT_LATHE_FEED_MM_PER_REV;
        // При G96 S — это скорость резания (м/мин), без диаметра в обороты не перевести:
        // берём дефолтные обороты.
        double rpm = state.spindleKnown() && !state.constantSurfaceSpeed()
                ? Math.max(1.0, state.spindle() * Math.max(0.01, spindleOverride))
                : DEFAULT_RPM * Math.max(0.01, spindleOverride);
        if (state.rpmLimit() > 0.0) {
            rpm = Math.min(rpm, state.rpmLimit());
        }
        return feedPerRev * rpm * effectiveFeedOverride;
    }

    private static double programNonMotionSeconds(String[] programLines) {
        if (programLines == null || programLines.length == 0) {
            return 0.0;
        }
        double seconds = 0.0;
        int lastTool = -1;
        for (String rawLine : programLines) {
            String line = GCodeProgramParser.stripFrameNumberForParse(
                    GCodeProgramParser.stripCommentsForParse(rawLine)).toUpperCase(Locale.US);
            if (line.isBlank()) {
                continue;
            }
            Matcher toolMatcher = T_CODE_PATTERN.matcher(line);
            if (toolMatcher.find()) {
                int tool = parsePositiveInt(toolMatcher.group(1));
                if (tool > 0 && tool != lastTool) {
                    seconds += TURRET_TOOL_CHANGE_SECONDS;
                    lastTool = tool;
                }
            }
            if (lineContainsG4(line)) {
                seconds += dwellSeconds(line);
            }
            Matcher mMatcher = M_CODE_PATTERN.matcher(line);
            while (mMatcher.find()) {
                double rawCode = parseDouble(mMatcher.group(1));
                if (Double.isFinite(rawCode)) {
                    seconds += auxiliaryTimeForMCode((int) Math.round(rawCode));
                }
            }
        }
        return seconds;
    }

    private static double dwellSeconds(String line) {
        double fValue = wordValue(line, "F");
        if (Double.isFinite(fValue) && fValue > 0.0) {
            return fValue;
        }
        double sValue = wordValue(line, "S");
        if (Double.isFinite(sValue) && sValue > 0.0) {
            return sValue;
        }
        return 0.0;
    }

    private static double auxiliaryTimeForMCode(int code) {
        return switch (code) {
            case 3, 4 -> SPINDLE_START_SECONDS;
            case 5 -> SPINDLE_STOP_SECONDS;
            case 6 -> MILLING_TOOL_CHANGE_SECONDS;
            case 8, 9 -> COOLANT_SWITCH_SECONDS;
            case 15, 51, 52, 54 -> MACHINE_AUX_SECONDS;
            case 2, 30 -> 0.5;
            default -> 0.0;
        };
    }

    private static ModalState defaultState(SimulationContext context) {
        boolean lathe = context.getMachineConfiguration() == null
                || context.getMachineConfiguration().isLatheMode();
        return new ModalState(
                lathe,
                false,
                lathe ? DEFAULT_LATHE_FEED_MM_PER_REV : DEFAULT_LINEAR_FEED_MM_PER_MIN,
                false,
                DEFAULT_RPM,
                false,
                0.0,
                1,
                1.0);
    }

    private static ModalState applyLine(ModalState previous, String rawLine, double referenceDiameter) {
        String line = GCodeProgramParser.stripFrameNumberForParse(
                GCodeProgramParser.stripCommentsForParse(rawLine)).toUpperCase(Locale.US);
        if (line.isBlank()) {
            return previous;
        }

        boolean feedPerRev = previous.feedPerRev();
        boolean css = previous.constantSurfaceSpeed();
        double feed = previous.feed();
        boolean feedKnown = previous.feedKnown();
        double spindle = previous.spindle();
        boolean spindleKnown = previous.spindleKnown();
        double rpmLimit = previous.rpmLimit();
        int masterSpindle = previous.masterSpindle();
        double pathOverride = previous.pathOverride();
        boolean dwellLine = lineContainsG4(line);

        Matcher setMaster = SET_MASTER_SPINDLE_PATTERN.matcher(line);
        if (setMaster.find()) {
            try {
                masterSpindle = Math.max(1, Integer.parseInt(setMaster.group(1)));
            } catch (NumberFormatException ignored) {
                masterSpindle = previous.masterSpindle();
            }
        }

        Matcher ovrMatcher = OVR_PATTERN.matcher(line);
        if (ovrMatcher.find()) {
            double value = parseDouble(ovrMatcher.group(1));
            if (Double.isFinite(value)) {
                pathOverride = Math.max(0.0, value / 100.0);
            }
        }

        Matcher matcher = TOKEN_PATTERN.matcher(line);
        while (matcher.find()) {
            String word = firstNonNull(matcher.group(1), matcher.group(3), matcher.group(5));
            double value = parseDouble(firstNonNull(matcher.group(2), matcher.group(4), matcher.group(6)));
            if (word == null || !Double.isFinite(value)) {
                continue;
            }
            if (word.startsWith("LIMS")) {
                int spindleIndex = indexedWordNumber(word);
                if (spindleIndex <= 0 || spindleIndex == masterSpindle) {
                    rpmLimit = value > 0.0 ? value : 0.0;
                }
                continue;
            }
            if (word.startsWith("S")) {
                // G4 S specifies a dwell in spindle revolutions, not a new speed.
                if (dwellLine) continue;
                int spindleIndex = indexedWordNumber(word);
                if (spindleIndex <= 0 || spindleIndex == masterSpindle) {
                    spindle = Math.max(0.0, value);
                    spindleKnown = spindle > 0.0;
                }
                continue;
            }
            if ("F".equals(word)) {
                if (dwellLine) {
                    continue;
                }
                feed = Math.max(0.0, value);
                feedKnown = feed > 0.0;
                continue;
            }
            if (!"G".equals(word)) {
                continue;
            }
            int code = (int) Math.round(value);
            if (code == 94) {
                feedPerRev = false;
            } else if (code == 95) {
                feedPerRev = true;
            } else if (code == 96) {
                css = true;
                if (!feedPerRev && !lineContainsFeed(line)) {
                    feedKnown = false;
                }
                feedPerRev = true;
            } else if (code == 961) {
                css = true;
                feedPerRev = false;
            } else if (code == 962) {
                css = true;
            } else if (code == 97) {
                if (css && !lineContainsSpindle(line)) {
                    spindle = cssProgramRpm(spindle, referenceDiameter);
                    spindleKnown = true;
                }
                css = false;
                feedPerRev = true;
            } else if (code == 971) {
                if (css && !lineContainsSpindle(line)) {
                    spindle = cssProgramRpm(spindle, referenceDiameter);
                    spindleKnown = true;
                }
                css = false;
                feedPerRev = false;
            } else if (code == 972 || code == 973) {
                if (css && !lineContainsSpindle(line)) {
                    spindle = cssProgramRpm(spindle, referenceDiameter);
                    spindleKnown = true;
                }
                css = false;
            }
        }

        return new ModalState(feedPerRev, css, feed, feedKnown, spindle, spindleKnown, rpmLimit, masterSpindle, pathOverride);
    }

    private static double feedMmPerMinute(
            ModalState state,
            GCodeMoveData move,
            double feedOverride,
            double spindleOverride
    ) {
        if (!state.feedKnown()) {
            return Double.NaN;
        }
        double effectiveFeedOverride = feedOverride * state.pathOverride();
        if (effectiveFeedOverride <= 0.0) {
            return Double.NaN;
        }
        if (!state.feedPerRev()) {
            return state.feed() * effectiveFeedOverride;
        }
        if (!state.spindleKnown()) {
            return Double.NaN;
        }
        return state.feed() * averageRpmForMove(state, move, spindleOverride) * effectiveFeedOverride;
    }

    private static double averageRpmForMove(ModalState state, GCodeMoveData move, double spindleOverride) {
        if (!state.constantSurfaceSpeed()) {
            return limitedRpm(state, state.spindle(), spindleOverride);
        }
        double start = rpmForDiameter(state, move.startX(), spindleOverride);
        double mid = rpmForDiameter(state, (move.startX() + move.endX()) * 0.5, spindleOverride);
        double end = rpmForDiameter(state, move.endX(), spindleOverride);
        return Math.max(1.0, (start + 4.0 * mid + end) / 6.0);
    }

    private static double rpmForDiameter(ModalState state, double diameterMm, double spindleOverride) {
        return limitedRpm(state, cssProgramRpm(state.spindle(), diameterMm), spindleOverride);
    }

    private static double limitedRpm(ModalState state, double programmedRpm, double spindleOverride) {
        double rpm = programmedRpm;
        rpm *= spindleOverride;
        if (state.rpmLimit() > 0.0) {
            rpm = Math.min(rpm, state.rpmLimit());
        }
        return Math.max(1.0, rpm);
    }

    private static double cssProgramRpm(double cuttingSpeedMPerMin, double diameterMm) {
        double diameter = Math.max(0.1, Math.abs(diameterMm));
        return 1000.0 * Math.max(0.0, cuttingSpeedMPerMin) / (Math.PI * diameter);
    }

    private static double moveLengthMm(SimulationContext context, GCodeMoveData move) {
        MachineConfiguration configuration = context.getMachineConfiguration();
        boolean lathe = configuration == null || configuration.isLatheMode();
        double dx = lathe
                ? (move.endX() - move.startX()) * .5
                : move.endX() - move.startX();
        double dz = move.endZ() - move.startZ();
        return Math.hypot(dx, dz);
    }

    private static boolean lineContainsFeed(String line) {
        return Pattern.compile("\\bF\\s*=?\\s*" + NUMBER, Pattern.CASE_INSENSITIVE).matcher(line).find();
    }

    private static boolean lineContainsG4(String line) {
        return line != null && G4_PATTERN.matcher(line).find();
    }

    private static boolean lineContainsSpindle(String line) {
        return Pattern.compile("\\bS(?:\\[\\d+\\]|\\d+)?\\s*=?\\s*" + NUMBER, Pattern.CASE_INSENSITIVE)
                .matcher(line)
                .find();
    }

    private static double wordValue(String line, String word) {
        if (line == null || word == null || word.isBlank()) {
            return Double.NaN;
        }
        Matcher matcher = Pattern.compile(
                        "\\b" + Pattern.quote(word) + "\\s*=?\\s*(" + NUMBER + ")",
                        Pattern.CASE_INSENSITIVE)
                .matcher(line);
        if (!matcher.find()) {
            return Double.NaN;
        }
        return parseDouble(matcher.group(1));
    }

    private static int indexedWordNumber(String word) {
        Matcher bracket = Pattern.compile("\\[(\\d+)]").matcher(word);
        if (bracket.find()) {
            return parsePositiveInt(bracket.group(1));
        }
        Matcher suffix = Pattern.compile("^[A-Z]+(\\d+)$").matcher(word);
        if (suffix.find()) {
            return parsePositiveInt(suffix.group(1));
        }
        return -1;
    }

    private static int parsePositiveInt(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String firstNonNull(String first, String second, String third) {
        if (first != null) {
            return first;
        }
        return second != null ? second : third;
    }

    private static double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }
}
