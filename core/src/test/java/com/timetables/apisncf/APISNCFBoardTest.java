package com.timetables.apisncf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Offline tests of the board logic: a fake APISNCF replaces the network call
 * (callApi) with canned Navitia JSON, so no API key and no Internet are needed.
 */
class APISNCFBoardTest {

    /** An APISNCF that answers from memory and counts how often it is "called". */
    static class FakeApi extends APISNCF {
        final AtomicInteger placesCalls = new AtomicInteger();
        final AtomicInteger vehicleJourneyCalls = new AtomicInteger();

        FakeApi() {
            super("test-key");
        }

        @Override
        protected String callApi(String url) {
            if (url.contains("/places")) {
                placesCalls.incrementAndGet();
                return "{\"places\":[{\"embedded_type\":\"stop_area\","
                        + "\"id\":\"stop_area:SNCF:87723197\","
                        + "\"stop_area\":{\"name\":\"Lyon Part Dieu\"}}]}";
            }
            if (url.contains("/arrivals")) {
                return "{\"arrivals\":["
                        + arrival("TGV INOUI", "6609", "Annecy (Annecy)",
                                "20261024T115600", "20261024T115600", "vehicle_journey:A") + ","
                        + arrival("REGIONAURA", "886271", "Lyon Perrache (Lyon)",
                                "20261024T115900", "20261024T120400", "vehicle_journey:B") + ","
                        + arrival("Bus", "1", "x",
                                "20261024T115900", "20261024T115900", "vehicle_journey:C") + ","
                        + arrival("TER", "77", "Dijon (Dijon)",
                                "20261024T121000", null, "vehicle_journey:D") + ","
                        + arrival("TER", "78", "Dijon (Dijon)",
                                "20261024T122000", "20261024T122000", "vehicle_journey:E")
                        + "]}";
            }
            if (url.contains("/departures")) {
                return "{\"departures\":[{\"display_informations\":{"
                        + "\"commercial_mode\":\"TGV INOUI\",\"headsign\":\"1\","
                        + "\"direction\":\"Mulhouse (Mulhouse)\"},"
                        + "\"stop_date_time\":{\"base_departure_date_time\":\"20261024T120000\","
                        + "\"departure_date_time\":\"20261024T120000\"}}]}";
            }
            if (url.contains("/vehicle_journeys/")) {
                vehicleJourneyCalls.incrementAndGet();
                String id = url.substring(url.indexOf("/vehicle_journeys/") + "/vehicle_journeys/".length());
                if (id.endsWith("E")) {
                    throw new RuntimeException("HTTP 404"); // simulates a failing lookup
                }
                String first = id.endsWith("A") ? "Paris - Gare de Lyon - Hall 1 & 2 (Paris)"
                        : id.endsWith("B") ? "Firminy (Firminy)"
                        : "Ailleurs (X)";
                return "{\"vehicle_journeys\":[{\"stop_times\":["
                        + "{\"stop_point\":{\"name\":\"n\",\"label\":\"" + first + "\"}},"
                        + "{\"stop_point\":{\"label\":\"Lyon Part Dieu (Lyon)\"}}]}]}";
            }
            throw new IllegalStateException("unexpected URL " + url);
        }

        private static String arrival(String mode, String headsign, String direction,
                String base, String real, String vehicleJourney) {
            return "{\"route\":{\"name\":\"x\"},\"stop_point\":{\"name\":\"s\"},"
                    + "\"display_informations\":{\"commercial_mode\":\"" + mode
                    + "\",\"headsign\":\"" + headsign + "\",\"direction\":\"" + direction + "\"},"
                    + "\"stop_date_time\":{\"base_arrival_date_time\":\"" + base + "\""
                    + (real == null ? "" : ",\"arrival_date_time\":\"" + real + "\"") + "},"
                    + "\"links\":[{\"type\":\"disruption\",\"id\":\"d1\"},"
                    + "{\"type\":\"vehicle_journey\",\"id\":\"" + vehicleJourney + "\"}]}";
        }
    }

    private final PlatformProvider platforms = new OfficialPlatformProvider();

    @Test
    void arrivalsBoardDropsBusRows() throws Exception {
        List<BoardEntry> rows = new FakeApi().fetchBoard(BoardType.ARRIVALS, "lyon part dieu", 100, platforms);

        assertEquals(4, rows.size());
    }

    @Test
    void arrivalOriginIsTheFirstStopOfTheVehicleJourney() throws Exception {
        List<BoardEntry> rows = new FakeApi().fetchBoard(BoardType.ARRIVALS, "lyon part dieu", 100, platforms);

        assertEquals("Paris - Gare de Lyon - Hall 1 & 2 (Paris)", rows.get(0).otherStation());
        // "Firminy (Firminy)" is cleaned into "Firminy"
        assertEquals("Firminy", rows.get(1).otherStation());
    }

    @Test
    void delayedTrainShowsItsDelay() throws Exception {
        List<BoardEntry> rows = new FakeApi().fetchBoard(BoardType.ARRIVALS, "lyon part dieu", 100, platforms);

        assertTrue(rows.get(1).retarde());
        assertEquals("Retardé +5 min", rows.get(1).statut());
    }

    @Test
    void trainWithoutRealTimeDataIsShownAsCancelled() throws Exception {
        List<BoardEntry> rows = new FakeApi().fetchBoard(BoardType.ARRIVALS, "lyon part dieu", 100, platforms);

        assertTrue(rows.get(2).supprime());
        assertEquals("--:--", rows.get(2).heure());
    }

    @Test
    void failedOriginLookupShowsADash() throws Exception {
        List<BoardEntry> rows = new FakeApi().fetchBoard(BoardType.ARRIVALS, "lyon part dieu", 100, platforms);

        assertEquals("--", rows.get(3).otherStation());
    }

    @Test
    void secondRefreshOnlyRetriesTheFailedLookups() throws Exception {
        FakeApi api = new FakeApi();

        api.fetchBoard(BoardType.ARRIVALS, "lyon part dieu", 100, platforms);
        assertEquals(4, api.vehicleJourneyCalls.get()); // A, B, D, E (the bus is filtered out)

        api.fetchBoard(BoardType.ARRIVALS, "Lyon Part Dieu ", 100, platforms);
        // A, B and D come from the cache; only E (which failed) is asked again.
        assertEquals(5, api.vehicleJourneyCalls.get());
    }

    @Test
    void stationIsLookedUpOnlyOnce() throws Exception {
        FakeApi api = new FakeApi();

        api.fetchBoard(BoardType.ARRIVALS, "lyon part dieu", 100, platforms);
        api.fetchBoard(BoardType.DEPARTURES, "Lyon Part Dieu ", 100, platforms);

        assertEquals(1, api.placesCalls.get());
    }

    @Test
    void departureShowsTheDestinationAndTheTime() throws Exception {
        List<BoardEntry> rows = new FakeApi().fetchBoard(BoardType.DEPARTURES, "lyon part dieu", 100, platforms);

        assertEquals(1, rows.size());
        assertEquals("Mulhouse", rows.get(0).otherStation());
        assertEquals("12:00", rows.get(0).heure());
        assertFalse(rows.get(0).retarde());
    }

    @Test
    void cleanStationLabelRemovesARedundantCity() {
        assertEquals("Mulhouse", APISNCF.cleanStationLabel("Mulhouse (Mulhouse)"));
        assertEquals("Lyon Part Dieu (Lyon)", APISNCF.cleanStationLabel("Lyon Part Dieu (Lyon)"));
        assertEquals("Dijon", APISNCF.cleanStationLabel("Dijon"));
    }

    @Test
    void sncfDatetimeIsFormattedForHumans() {
        assertEquals("24/10/2026 11:56", APISNCF.formatSncfDatetime("20261024T115600"));
    }

    @Test
    void boardTypesDescribeTheirNavitiaEndpoint() {
        assertEquals("departures", BoardType.DEPARTURES.apiPath());
        assertEquals("arrival_date_time", BoardType.ARRIVALS.timeField());
        assertEquals("Provenance", BoardType.ARRIVALS.otherStationLabel());
    }
}
