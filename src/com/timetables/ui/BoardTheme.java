package com.timetables.ui;

import java.awt.Color;

import com.timetables.apisncf.BoardType;

/**
 * Look of a board: blue for departures, green for arrivals. Only
 * what differs between the two boards lives here (background colors, watermark);
 * the drawing itself is shared (BoardPanel).
 */
public enum BoardTheme {

    DEPARTURES(
            new Color(27, 74, 158),   // light band
            new Color(10, 24, 61),    // dark band
            new Color(6, 15, 40),     // header
            "départs"),

    ARRIVALS(
            new Color(24, 122, 84),   // light band
            new Color(8, 58, 40),     // dark band
            new Color(4, 34, 24),     // header
            "arrivées");

    private final Color rowLight;
    private final Color rowDark;
    private final Color header;
    private final String watermark;

    BoardTheme(Color rowLight, Color rowDark, Color header, String watermark) {
        this.rowLight = rowLight;
        this.rowDark = rowDark;
        this.header = header;
        this.watermark = watermark;
    }

    public static BoardTheme of(BoardType type) {
        return type == BoardType.ARRIVALS ? ARRIVALS : DEPARTURES;
    }

    public Color rowLight() {
        return rowLight;
    }

    public Color rowDark() {
        return rowDark;
    }

    public Color header() {
        return header;
    }

    /** Vertical watermark text on the right-hand side of the board. */
    public String watermark() {
        return watermark;
    }
}
