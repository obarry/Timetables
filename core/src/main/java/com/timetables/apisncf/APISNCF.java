package com.timetables.apisncf;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class APISNCF {

    private static final String BASE_URL = "https://api.sncf.com/v1/coverage/sncf";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final DateTimeFormatter SNCF_DATETIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    // Maximum number of rows shown on a board
    private static final int MAX_ROWS = 15;

    // Looking up the origin station of arrivals: a few calls in parallel
    // (daemon threads, so they never keep the JVM from exiting).
    private static final int ORIGIN_LOOKUP_THREADS = 4;
    private static final ExecutorService ORIGIN_EXECUTOR = Executors.newFixedThreadPool(ORIGIN_LOOKUP_THREADS, r -> {
        Thread t = new Thread(r, "origin-lookup");
        t.setDaemon(true);
        return t;
    });

    // "id":"vehicle_journey:SNCF:2026-10-24:6609:1187:LongDistanceTrain"
    private static final Pattern VEHICLE_JOURNEY_ID = Pattern.compile("\"id\"\\s*:\\s*\"(vehicle_journey:[^\"]+)\"");

    // "Mulhouse (Mulhouse)" -> "Mulhouse"
    private static final Pattern REDUNDANT_CITY = Pattern.compile("^(.+) \\((.+)\\)$");

    private final String apiKey;
    private final HttpTransport transport;
    // Where diagnostic messages go (console, Android Logcat, nowhere...).
    private final Consumer<String> log;

    // Typed station name (normalized) -> stop_area id: avoids repeating the
    // /places search on every automatic refresh (API quota).
    private final Map<String, String> stationIdCache = new ConcurrentHashMap<>();

    // vehicle_journey id -> origin station. The id contains the day's date and
    // a train's origin never changes, so we only ask for it once.
    private final Map<String, String> originCache = new ConcurrentHashMap<>();

    /** Same as the full constructor, but diagnostic messages are dropped. */
    public APISNCF(String key, HttpTransport transport) {
        this(key, transport, message -> { });
    }

    /**
     * @param key       SNCF API key
     * @param transport how HTTP requests are made (depends on the platform)
     * @param log       receives diagnostic messages (one line each, never null)
     */
    public APISNCF(String key, HttpTransport transport, Consumer<String> log) {
        this.apiKey = key;
        this.transport = transport;
        this.log = log;
    }

    // Converts an SNCF datetime (YYYYMMDDTHHMMSS) to a readable format
    public static String formatSncfDatetime(String dtStr) {
        DateTimeFormatter outputFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        LocalDateTime dt = LocalDateTime.parse(dtStr, SNCF_DATETIME);
        return dt.format(outputFmt);
    }

    // Performs a GET request on the SNCF API (through the injected transport)
    private String callApi(String urlStr) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Clé API SNCF manquante. Renseignez API_KEY dans resources/Timetables.properties.");
        }

        String auth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));

        HttpTransport.Response response = transport.get(urlStr,
                Map.of("Authorization", "Basic " + auth), REQUEST_TIMEOUT);

        if (response.statusCode() != 200) {
            String detail = response.statusCode() == 401
                ? "clé API invalide, expirée, ou absente."
                : response.body();
            throw new RuntimeException("Erreur API SNCF (HTTP " + response.statusCode() + ") : " + detail);
        }

        return response.body();
    }

    // Looks up a station's stop_area ID from its name (result is cached)
    public String searchStationId(String stationName) throws Exception {

        String cacheKey = stationName.trim().toLowerCase();
        String cached = stationIdCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Call SNCF API with URL /places
        String url = BASE_URL + "/places?q=" + stationName.trim().replace(" ", "%20");
        String body = callApi(url);

        JSONArray places = null;
        if (body.trim().startsWith("{")) {
            JSONObject json = new JSONObject(body);
            places = json.has("places") ? json.getJSONArray("places") : null;
        } else if (body.trim().startsWith("[")) {
            places = new JSONArray(body);
        } else {
            log.accept("La reponse n'est pas du JSON valide: " + body);
        }

        if (places == null) {
            log.accept("Aucune gare trouvée pour « " + stationName + " ».");
            return null;
        }

        for (int i = 0; i < places.length(); i++) {
            JSONObject place = places.getJSONObject(i);
            if (place.getString("embedded_type").equals("stop_area")) {
                JSONObject stopArea = place.getJSONObject("stop_area");
                String id = place.getString("id");
                log.accept("Gare trouvée : " + stopArea.getString("name") + " / " + id);
                stationIdCache.put(cacheKey, id);
                return id;
            }
        }

        log.accept("Aucune gare trouvée pour « " + stationName + " ».");
        return null;
    }

    /**
     * Finds the journeys between two stations around a given date and time.
     *
     * @return the journeys found (empty if there is none)
     * @throws StationNotFoundException if one of the station names matches nothing.
     */
    public List<Journey> findJourneys(String fromName, String toName, LocalDateTime dateTime) throws Exception {
        String fromStation = searchStationId(fromName);
        if (fromStation == null) {
            throw new StationNotFoundException(fromName);
        }
        String toStation = searchStationId(toName);
        if (toStation == null) {
            throw new StationNotFoundException(toName);
        }

        String dateStr = dateTime.format(SNCF_DATETIME);

        // Call SNCF API with URL /journeys
        String url = BASE_URL + "/journeys?from=" + fromStation + "&to=" + toStation + "&datetime=" + dateStr;
        JSONObject obj = new JSONObject(callApi(url));

        List<Journey> result = new ArrayList<>();
        if (!obj.has("journeys")) {
            return result;
        }
        JSONArray journeys = obj.getJSONArray("journeys");
        for (int i = 0; i < journeys.length(); i++) {
            JSONArray sections = journeys.getJSONObject(i).getJSONArray("sections");
            LocalDateTime departure = LocalDateTime.parse(
                    sections.getJSONObject(0).getString("departure_date_time"), SNCF_DATETIME);
            LocalDateTime arrival = LocalDateTime.parse(
                    sections.getJSONObject(sections.length() - 1).getString("arrival_date_time"), SNCF_DATETIME);
            result.add(new Journey(departure, arrival));
        }
        return result;
    }

    /**
     * Fetches and computes the next departures or arrivals of a station, filtered
     * (regional trains, long-distance, RER/Transilien) and limited to MAX_ROWS rows,
     * without printing anything: presentation (console, graphical...) is left to
     * the caller. Diagnostic messages go to the injected log.
     *
     * For a departure, "otherStation" is the destination (provided directly
     * by the API). For an arrival, it is the origin, which the API does not give
     * directly: we deduce it from the first stop of the train's vehicle_journey
     * (one call per train, cached).
     *
     * @throws StationNotFoundException if the station name matches nothing.
     */
    public List<BoardEntry> fetchBoard(BoardType type, String station, int nb_trains, PlatformProvider platformProvider) throws Exception {

        String stationId = searchStationId(station);
        if (stationId == null) {
            throw new StationNotFoundException(station);
        }

        String json = callApi(BASE_URL + "/stop_areas/" + stationId + "/" + type.apiPath() + "?count=" + nb_trains);

        JSONObject obj = new JSONObject(json);
        if (!obj.has(type.apiPath())) {
            return new ArrayList<>();
        }
        JSONArray rawItems = obj.getJSONArray(type.apiPath());

        // Pass 1: filter + compute everything that comes straight from the response.
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < rawItems.length() && rows.size() < MAX_ROWS; i++) {
            JSONObject item = rawItems.getJSONObject(i);
            JSONObject info = item.getJSONObject("display_informations");

            // Read the commercial mode (TER, TGV, ...)
            String commercialMode = info.optString("commercial_mode", "").toUpperCase();

            // Filter: keep regional trains (all regions), long-distance, and RER/Transilien
            // (the latter are included because they are the only case where the unofficial
            // platform option has a chance of answering - see TransilienUnofficialPlatformProvider)
            boolean isTER = commercialMode.startsWith("REGION") || commercialMode.contains("TER");
            boolean isTGV = commercialMode.contains("TGV") || commercialMode.contains("OUIGO");
            boolean isIntercites = commercialMode.contains("INTERCITES") || commercialMode.contains("ICE");
            boolean isRERouTransilien = commercialMode.contains("RER") || commercialMode.contains("TRANSILIEN");

            if (!(isTER || isTGV || isIntercites || isRERouTransilien)) {
                continue; // skip the others (bus, tram, metro, etc.)
            }

            // Read the train
            String numTrain = info.optString("headsign", "N/A");
            String comMode = info.optString("commercial_mode", "");
            String train = comMode + " " + numTrain;

            JSONObject stopDateTime = item.getJSONObject("stop_date_time");

            // Platform: the official SNCF API does not provide it (verified); the
            // PlatformProvider encapsulates the source actually used (official,
            // always "--", or the unofficial Transilien option).
            String quai = platformProvider.getPlatform(station, numTrain);

            // Scheduled and actual times (field names depend on the board type)
            String baseDateTimeStr = stopDateTime.optString(type.baseTimeField(), "");
            String realDateTimeStr = stopDateTime.optString(type.timeField(), "");

            LocalDateTime baseTime = null;
            LocalDateTime realTime = null;
            if (!baseDateTimeStr.isEmpty()) {
                baseTime = LocalDateTime.parse(baseDateTimeStr, SNCF_DATETIME);
            }
            if (!realDateTimeStr.isEmpty()) {
                realTime = LocalDateTime.parse(realDateTimeStr, SNCF_DATETIME);
            }

            // Status
            String status;
            boolean retarde = false;
            boolean supprime = false;
            if (realTime == null) {
                status = "Supprimé";
                supprime = true;
            } else if (baseTime != null && realTime.isAfter(baseTime)) {
                long minutesLate = Duration.between(baseTime, realTime).toMinutes();
                status = "Retardé +" + minutesLate + " min";
                retarde = true;
            } else {
                status = "À l'heure";
            }

            String heureAffichee = (realTime != null)
                    ? realTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "--:--";

            String otherStation;
            String vehicleJourneyId = null;
            if (type == BoardType.DEPARTURES) {
                otherStation = cleanStationLabel(info.optString("direction", "N/A"));
            } else {
                otherStation = "--"; // filled in during pass 2
                vehicleJourneyId = findVehicleJourneyId(item);
            }

            rows.add(new Row(
                    new BoardEntry(heureAffichee, train, otherStation, quai, status, retarde, supprime),
                    vehicleJourneyId));
        }

        // Pass 2 (arrivals only): origin of each train.
        if (type == BoardType.ARRIVALS) {
            resolveOrigins(rows);
        }

        List<BoardEntry> result = new ArrayList<>();
        for (Row row : rows) {
            BoardEntry e = row.entry();
            if (type == BoardType.ARRIVALS && row.vehicleJourneyId() != null) {
                String origin = originCache.get(row.vehicleJourneyId());
                if (origin != null) {
                    e = new BoardEntry(e.heure(), e.train(), origin, e.quai(), e.statut(), e.retarde(), e.supprime());
                }
            }
            result.add(e);
        }
        return result;
    }

    // Intermediate row: the computed entry + the vehicle_journey id (arrivals).
    private record Row(BoardEntry entry, String vehicleJourneyId) {
    }

    /** Finds the vehicle_journey id of a departure/arrival item ("vehicle_journey" link). */
    private static String findVehicleJourneyId(JSONObject item) {
        try {
            if (item.has("links")) {
                JSONArray links = item.getJSONArray("links");
                for (int i = 0; i < links.length(); i++) {
                    JSONObject link = links.getJSONObject(i);
                    if (link.has("type") && link.has("id") && "vehicle_journey".equals(link.getString("type"))) {
                        return link.getString("id");
                    }
                }
            }
        } catch (org.json.JSONException e) {
            // fall back to the pattern search below
        }
        Matcher m = VEHICLE_JOURNEY_ID.matcher(item.toString());
        return m.find() ? m.group(1) : null;
    }

    /** Queries (in parallel) the vehicle_journeys not yet known, to fill originCache. */
    private void resolveOrigins(List<Row> rows) {
        Set<String> toFetch = new LinkedHashSet<>();
        for (Row row : rows) {
            String id = row.vehicleJourneyId();
            if (id != null && !originCache.containsKey(id)) {
                toFetch.add(id);
            }
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String id : toFetch) {
            futures.add(CompletableFuture.runAsync(() -> {
                String origin = fetchOrigin(id);
                if (origin != null) {
                    originCache.put(id, origin); // failures are not remembered: retried on the next refresh
                }
            }, ORIGIN_EXECUTOR));
        }
        for (CompletableFuture<Void> f : futures) {
            f.join();
        }
    }

    /** Origin station = first stop of the vehicle_journey. Never throws: returns null if unavailable. */
    private String fetchOrigin(String vehicleJourneyId) {
        try {
            String body = callApi(BASE_URL + "/vehicle_journeys/" + vehicleJourneyId.replace(" ", "%20"));
            JSONObject obj = new JSONObject(body);
            if (!obj.has("vehicle_journeys")) {
                return null;
            }
            JSONArray vjs = obj.getJSONArray("vehicle_journeys");
            if (vjs.length() == 0) {
                return null;
            }
            JSONObject vj = vjs.getJSONObject(0);
            if (!vj.has("stop_times")) {
                return null;
            }
            JSONArray stopTimes = vj.getJSONArray("stop_times");
            if (stopTimes.length() == 0) {
                return null;
            }
            JSONObject first = stopTimes.getJSONObject(0);
            if (!first.has("stop_point")) {
                return null;
            }
            JSONObject stopPoint = first.getJSONObject("stop_point");
            String label = stopPoint.has("label") ? stopPoint.getString("label")
                    : stopPoint.has("name") ? stopPoint.getString("name") : null;
            return label == null ? null : cleanStationLabel(label);
        } catch (Exception e) {
            log.accept("[Provenance] impossible de lire " + vehicleJourneyId + " : " + e.getMessage());
            return null;
        }
    }

    /** "Mulhouse (Mulhouse)" becomes "Mulhouse"; "Lyon Part Dieu (Lyon)" is left unchanged. */
    static String cleanStationLabel(String label) {
        Matcher m = REDUNDANT_CITY.matcher(label);
        if (m.matches() && m.group(1).trim().equalsIgnoreCase(m.group(2).trim())) {
            return m.group(1).trim();
        }
        return label;
    }

}
