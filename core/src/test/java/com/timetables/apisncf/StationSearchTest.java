package com.timetables.apisncf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

/** Offline tests of the station autocompletion (canned Navitia /places answers). */
class StationSearchTest {

    /** Answers /places and /departures from memory and remembers the requested URLs. */
    static class FakeTransport implements HttpTransport {
        final List<String> urls = new CopyOnWriteArrayList<>();

        @Override
        public Response get(String url, Map<String, String> headers, Duration timeout) {
            urls.add(url);
            if (url.contains("/places")) {
                return new Response(200, "{\"places\":["
                        + "{\"id\":\"stop_area:SNCF:87723197\",\"name\":\"Lyon Part Dieu (Lyon)\","
                        + "\"embedded_type\":\"stop_area\",\"stop_area\":{\"name\":\"Lyon Part Dieu\"}},"
                        + "{\"id\":\"admin:fr:69123\",\"name\":\"Lyon (69000)\","
                        + "\"embedded_type\":\"administrative_region\"},"
                        + "{\"id\":\"stop_area:SNCF:87722025\",\"name\":\"Lyon Perrache (Lyon)\","
                        + "\"embedded_type\":\"stop_area\",\"stop_area\":{\"name\":\"Lyon Perrache\"}},"
                        + "{\"id\":\"stop_area:SNCF:87000001\",\"name\":\"Mulhouse (Mulhouse)\","
                        + "\"embedded_type\":\"stop_area\"}"
                        + "]}");
            }
            if (url.contains("/departures")) {
                return new Response(200, "{\"departures\":[{\"display_informations\":{"
                        + "\"commercial_mode\":\"TGV INOUI\",\"headsign\":\"1\","
                        + "\"direction\":\"Mulhouse (Mulhouse)\"},"
                        + "\"stop_date_time\":{\"base_departure_date_time\":\"20261024T120000\","
                        + "\"departure_date_time\":\"20261024T120000\"}}]}");
            }
            return new Response(404, "not found");
        }

        List<String> placesUrls() {
            List<String> result = new ArrayList<>();
            for (String url : urls) {
                if (url.contains("/places")) {
                    result.add(url);
                }
            }
            return result;
        }
    }

    private static APISNCF api(FakeTransport transport) {
        return new APISNCF("test-key", transport);
    }

    @Test
    void suggestionsAreTheStationsOnly() throws Exception {
        List<Station> stations = api(new FakeTransport()).searchStations("lyon", 8);

        assertEquals(3, stations.size()); // the administrative region is dropped
        assertEquals("stop_area:SNCF:87723197", stations.get(0).id());
        assertEquals("Lyon Part Dieu (Lyon)", stations.get(0).name());
        assertEquals("Lyon Perrache (Lyon)", stations.get(1).name());
    }

    @Test
    void redundantCityIsRemovedFromTheSuggestion() throws Exception {
        List<Station> stations = api(new FakeTransport()).searchStations("mulhouse", 8);

        assertEquals("Mulhouse", stations.get(2).name());
    }

    @Test
    void resultsAreLimitedToTheRequestedCount() throws Exception {
        List<Station> stations = api(new FakeTransport()).searchStations("lyon", 2);

        assertEquals(2, stations.size());
    }

    @Test
    void tooShortQueryMakesNoNetworkCall() throws Exception {
        FakeTransport transport = new FakeTransport();
        APISNCF api = api(transport);

        assertTrue(api.searchStations("", 8).isEmpty());
        assertTrue(api.searchStations(" l ", 8).isEmpty());
        assertTrue(api.searchStations(null, 8).isEmpty());
        assertTrue(transport.urls.isEmpty());
    }

    @Test
    void requestAsksForStationsOnlyAndEncodesTheQuery() throws Exception {
        FakeTransport transport = new FakeTransport();

        api(transport).searchStations("Saint-Étienne & co", 5);

        String url = transport.placesUrls().get(0);
        assertTrue(url.contains("type%5B%5D=stop_area"), url);
        assertTrue(url.contains("count=5"), url);
        assertTrue(url.contains("q=Saint-%C3%89tienne%20%26%20co"), url);
        assertFalse(url.contains(" "), url);
    }

    @Test
    void sameQueryIsAskedOnlyOnce() throws Exception {
        FakeTransport transport = new FakeTransport();
        APISNCF api = api(transport);

        api.searchStations("lyon", 8);
        api.searchStations("LYON ", 8);

        assertEquals(1, transport.placesUrls().size());
    }

    @Test
    void boardOfAChosenStationSkipsTheNameSearch() throws Exception {
        FakeTransport transport = new FakeTransport();
        Station chosen = new Station("stop_area:SNCF:87723197", "Lyon Part Dieu (Lyon)");

        List<BoardEntry> rows = api(transport).fetchBoard(BoardType.DEPARTURES, chosen, 100,
                new OfficialPlatformProvider());

        assertEquals(1, rows.size());
        assertTrue(transport.placesUrls().isEmpty());
        assertTrue(transport.urls.get(0).contains("/stop_areas/stop_area:SNCF:87723197/departures"));
    }

    @Test
    void stationIsDisplayedByItsName() {
        assertEquals("Lyon Part Dieu (Lyon)", new Station("x", "Lyon Part Dieu (Lyon)").toString());
    }
}
