package com.timetables.apisncf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * UNOFFICIAL and UNDOCUMENTED source: an internal endpoint once
 * used by the Transilien mobile app (sncf.mobi). It is NOT a
 * public SNCF API:
 *  - no availability guarantee (2016 reports already said it
 *    no longer answered consistently);
 *  - no format stability guaranteed (the JSON below is an
 *    assumption to confirm/correct with real responses);
 *  - limited to Transilien/RER (Île-de-France): does not work for
 *    long-distance TGV/Intercités.
 *
 * FINDING (real test, 2026-09-20, Paris - Gare de Lyon station, TR3 code PAA):
 * java.net.ConnectException on every request. The endpoint is dead,
 * as the 2016 report suggested. Code kept as is (it degrades
 * cleanly to "--", never crashing the board)
 * in case another station or another time gives a different result,
 * but do not expect this source to work as is.
 *
 * Used only for exploration in this proof of concept, in full
 * knowledge of these risks (explicit decision of the project owner).
 */
public class TransilienUnofficialPlatformProvider implements PlatformProvider {

    private static final String ENDPOINT = "http://sncf.mobi/infotrafic/iphoneapp/transilien/?gare=";

    private final Map<String, String> stationToTr3Code; // normalized station name -> TR3 code
    private final Map<String, JSONObject> responseCache = new HashMap<>(); // TR3 code -> raw response (successes only)
    private final Set<String> attemptedTr3Codes = new HashSet<>(); // TR3 code already attempted (success or failure) during this run
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final boolean debug;

    public TransilienUnofficialPlatformProvider(Map<String, String> stationToTr3Code, boolean debug) {
        this.stationToTr3Code = stationToTr3Code;
        this.debug = debug;
    }

    @Override
    public String name() {
        return "Endpoint non-officiel Transilien (sncf.mobi) — expérimental, Transilien/RER uniquement";
    }

    @Override
    public String getPlatform(String stationName, String trainNumber) {
        String tr3 = stationToTr3Code.get(normalize(stationName));
        if (tr3 == null) {
            if (debug) {
                System.out.println("[Transilien non-officiel] Pas de code TR3 connu pour « " + stationName
                        + " » (ajoutez-le dans TRANSILIEN_CODES du fichier de config).");
            }
            return "--";
        }

        JSONObject response = getOrFetch(tr3);
        if (response == null) {
            return "--";
        }
        return extractPlatform(response, trainNumber);
    }

    // A single network call per TR3 code for the whole run, including on
    // failure (Map.computeIfAbsent does not remember null results,
    // which caused a new connection attempt for every train).
    private JSONObject getOrFetch(String tr3) {
        if (responseCache.containsKey(tr3)) {
            return responseCache.get(tr3);
        }
        if (attemptedTr3Codes.contains(tr3)) {
            return null; // already attempted and failed during this run
        }
        attemptedTr3Codes.add(tr3);

        JSONObject result = fetch(tr3);
        if (result != null) {
            responseCache.put(tr3, result);
        }
        return result;
    }

    private JSONObject fetch(String tr3) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + tr3))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (debug) {
                System.out.println("[Transilien non-officiel] GET " + ENDPOINT + tr3 + " -> HTTP " + response.statusCode());
                System.out.println("[Transilien non-officiel] Réponse brute : " + truncate(response.body(), 2000));
            }

            String body = response.body();
            if (response.statusCode() != 200 || body == null || body.trim().isEmpty()) {
                return null;
            }
            if (!body.trim().startsWith("{")) {
                return null; // unexpected format (not a JSON object)
            }
            return new JSONObject(body);
        } catch (Exception e) {
            if (debug) {
                System.out.println("[Transilien non-officiel] Endpoint injoignable pour " + tr3 + " : " + e);
            }
            return null;
        }
    }

    // Deliberately defensive extraction: the exact format of this
    // undocumented API is not known for sure (no official documentation).
    // We try several plausible field names and fall back to "--"
    // if nothing matches, rather than crashing.
    private String extractPlatform(JSONObject response, String trainNumber) {
        JSONArray trains = null;
        try {
            if (response.has("train")) {
                trains = response.getJSONArray("train");
            } else if (response.has("trains")) {
                trains = response.getJSONArray("trains");
            }
        } catch (Exception ignored) {
            // the key exists but is not an array: different format from what we expected
        }
        if (trains == null) {
            return "--";
        }

        for (int i = 0; i < trains.length(); i++) {
            JSONObject t;
            try {
                t = trains.getJSONObject(i);
            } catch (Exception e) {
                continue;
            }
            String num = firstNonEmpty(
                    t.optString("num", ""),
                    t.optString("numero", ""),
                    t.optString("miss", ""));
            if (num.equals(trainNumber)) {
                return firstNonEmpty(
                        t.optString("voie", ""),
                        t.optString("quai", ""),
                        t.optString("track", ""),
                        t.optString("platform", ""));
            }
        }
        return "--";
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "--";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return (s.length() <= max) ? s : s.substring(0, max) + "...";
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase();
    }

    /**
     * Builds the "station name" -> "TR3 code" lookup table from
     * the TRANSILIEN_CODES configuration value, in the format:
     *   Station Name 1:XXX,Station Name 2:YYY
     */
    public static Map<String, String> parseStationCodes(String configValue) {
        Map<String, String> map = new HashMap<>();
        if (configValue == null || configValue.isBlank()) {
            return map;
        }
        for (String entry : configValue.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                map.put(normalize(parts[0]), parts[1].trim());
            }
        }
        return map;
    }
}
