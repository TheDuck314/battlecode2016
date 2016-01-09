package radar8;

import battlecode.common.*;

public class Debug extends Globals {
    private static String activeDebugSet;

    private static String[] currentIndicatorStrings = new String[GameConstants.NUMBER_OF_INDICATOR_STRINGS];

    public static void init(String debugSet) {
        activeDebugSet = debugSet;
    }

    public static void indicate(String debugSet, int indicator, String message) {
        if (debugSet == activeDebugSet) {
            rc.setIndicatorString(indicator, String.format("turn %d: %s", rc.getRoundNum(), message));
            currentIndicatorStrings[indicator] = message;
        }
    }

    public static void indicateAppend(String debugSet, int indicator, String message) {
        indicate(debugSet, indicator, currentIndicatorStrings[indicator] + message);
    }

    public static void clear(String debugSet) {
        for (int i = 0; i < GameConstants.NUMBER_OF_INDICATOR_STRINGS; i++) {
            indicate(debugSet, i, "");
        }
    }

    public static void debugBytecodes(String message) {
        System.out.println(String.format("turn: %d, bytecodes: %d: %s\n", rc.getRoundNum(), Clock.getBytecodeNum(), message));
    }

    static int timerStartRoundNum;
    static int timerStartBytecodeNum;

    public static void timerStart() {
        timerStartRoundNum = rc.getRoundNum();
        timerStartBytecodeNum = Clock.getBytecodeNum();
    }

    public static void timerEnd(String message) {
        int timerEndBytecodeNum = Clock.getBytecodeNum();
        int timerEndRoundNum = rc.getRoundNum();
        int bytecodeLimit = rc.getType().bytecodeLimit;

        int totalBytecodes = bytecodeLimit * (timerEndRoundNum - timerStartRoundNum) + (timerEndBytecodeNum - timerStartBytecodeNum);

        System.out.println(String.format("timed %s: took %d bytecodes ( = %f turns)\n", message, totalBytecodes, totalBytecodes / (double) bytecodeLimit));
    }

    public static void debug_bytecodes_init() {
        Debug.indicate("bytecodes", 0, "");
    }

    public static void debug_bytecodes(String message) {
        Debug.indicateAppend("bytecodes", 0, message + ": " + Clock.getBytecodeNum() + "; ");
    }
}


