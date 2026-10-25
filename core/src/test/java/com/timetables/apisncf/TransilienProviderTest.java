package com.timetables.apisncf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Offline tests of the (experimental) unofficial Transilien platform source. */
class TransilienProviderTest {

    private static final Map<String, String> CODES = Map.of("paris gare de lyon", "PAA");

    @Test
    void platformIsReadFromTheResponseAndLookedUpOnce() {
        AtomicInteger calls = new AtomicInteger();
        HttpTransport transport = (url, headers, timeout) -> {
            calls.incrementAndGet();
            return new HttpTransport.Response(200,
                    "{\"train\":[{\"num\":\"123\",\"voie\":\"B\"},{\"num\":\"456\",\"voie\":\"7\"}]}");
        };
        PlatformProvider provider = new TransilienUnofficialPlatformProvider(CODES, false, transport, m -> { });

        assertEquals("B", provider.getPlatform("Paris Gare de Lyon", "123"));
        assertEquals("7", provider.getPlatform(" paris gare de lyon ", "456"));
        assertEquals("--", provider.getPlatform("Paris Gare de Lyon", "999"));
        assertEquals(1, calls.get());
    }

    @Test
    void unreachableEndpointDegradesToADashAndIsTriedOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        HttpTransport transport = (url, headers, timeout) -> {
            calls.incrementAndGet();
            throw new java.net.ConnectException("down");
        };
        PlatformProvider provider = new TransilienUnofficialPlatformProvider(CODES, false, transport, m -> { });

        assertEquals("--", provider.getPlatform("Paris Gare de Lyon", "123"));
        assertEquals("--", provider.getPlatform("Paris Gare de Lyon", "456"));
        assertEquals(1, calls.get());
    }

    @Test
    void stationWithoutCodeIsReportedInDebugMode() {
        List<String> messages = new ArrayList<>();
        HttpTransport neverCalled = (url, headers, timeout) -> {
            throw new AssertionError("no request expected");
        };
        PlatformProvider provider = new TransilienUnofficialPlatformProvider(CODES, true, neverCalled, messages::add);

        assertEquals("--", provider.getPlatform("Lyon Part Dieu", "123"));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("Pas de code TR3"));
    }

    @Test
    void stationCodesAreParsedFromTheConfig() {
        Map<String, String> codes = TransilienUnofficialPlatformProvider
                .parseStationCodes("Paris Gare de Lyon:PAA, Melun : MLN ,broken,:X");

        assertEquals("PAA", codes.get("paris gare de lyon"));
        assertEquals("MLN", codes.get("melun"));
        assertEquals(2, codes.size());
    }
}
