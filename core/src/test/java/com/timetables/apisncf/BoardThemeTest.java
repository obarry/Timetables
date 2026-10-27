package com.timetables.apisncf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.timetables.theme.BoardTheme;

class BoardThemeTest {

    @Test
    void themeFollowsTheBoardType() {
        assertEquals(BoardTheme.DEPARTURES, BoardTheme.of(BoardType.DEPARTURES));
        assertEquals(BoardTheme.ARRIVALS, BoardTheme.of(BoardType.ARRIVALS));
    }

    @Test
    void colorsAreOpaqueArgbValues() {
        for (BoardTheme theme : BoardTheme.values()) {
            assertEquals(0xFF, theme.rowLight() >>> 24);
            assertEquals(0xFF, theme.rowDark() >>> 24);
            assertEquals(0xFF, theme.header() >>> 24);
        }
    }

    @Test
    void departuresAreBlueAndArrivalsAreGreen() {
        assertTrue(blue(BoardTheme.DEPARTURES.rowLight()) > green(BoardTheme.DEPARTURES.rowLight()));
        assertTrue(green(BoardTheme.ARRIVALS.rowLight()) > blue(BoardTheme.ARRIVALS.rowLight()));
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }
}
