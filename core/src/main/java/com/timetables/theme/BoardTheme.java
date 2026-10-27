package com.timetables.theme;

import com.timetables.apisncf.BoardType;

/**
 * Look of a board: blue for departures, green for arrivals. Only what differs between the
 * two boards lives here (band and header colors, watermark); each front-end does its own
 * drawing with these values.
 *
 * Colors are plain ARGB ints (0xAARRGGBB) rather than java.awt.Color, so that this class
 * can be shared by the desktop app and the Android app (which have no AWT).
 */
public enum BoardTheme {

    DEPARTURES(
            0xFF1B4A9E,   // light band
            0xFF0A183D,   // dark band
            0xFF060F28,   // header
            "départs"),

    ARRIVALS(
            0xFF187A54,   // light band
            0xFF083A28,   // dark band
            0xFF042218,   // header
            "arrivées");

    // Colors shared by both boards.
    public static final int YELLOW = 0xFFFFCD00;         // time
    public static final int WHITE = 0xFFFFFFFF;          // train and station names
    public static final int LATE_ORANGE = 0xFFFF9628;    // delayed train
    public static final int CANCELLED_RED = 0xFFFF5A5A;  // cancelled train

    private final int rowLight;
    private final int rowDark;
    private final int header;
    private final String watermark;

    BoardTheme(int rowLight, int rowDark, int header, String watermark) {
        this.rowLight = rowLight;
        this.rowDark = rowDark;
        this.header = header;
        this.watermark = watermark;
    }

    public static BoardTheme of(BoardType type) {
        return type == BoardType.ARRIVALS ? ARRIVALS : DEPARTURES;
    }

    /** Background of the odd rows (ARGB). */
    public int rowLight() {
        return rowLight;
    }

    /** Background of the even rows, also used behind the board (ARGB). */
    public int rowDark() {
        return rowDark;
    }

    /** Background of the title band (ARGB). */
    public int header() {
        return header;
    }

    /** Vertical watermark text on the right-hand side of the board. */
    public String watermark() {
        return watermark;
    }
}
