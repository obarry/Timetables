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
 * Source NON OFFICIELLE et NON DOCUMENTÉE : un endpoint interne autrefois
 * utilisé par l'application mobile Transilien (sncf.mobi). Ce n'est PAS une
 * API publique de la SNCF :
 *  - aucune garantie de disponibilité (des retours de 2016 indiquaient déjà
 *    qu'il ne répondait plus systématiquement) ;
 *  - aucune stabilité de format garantie (le JSON ci-dessous est une
 *    hypothèse à confirmer/corriger avec de vraies réponses) ;
 *  - limité au Transilien/RER (Île-de-France) : ne fonctionne pas pour les
 *    TGV/Intercités grandes lignes.
 *
 * CONSTAT (test réel, 20/09/2026, gare de Paris - Gare de Lyon, code TR3 PAA) :
 * java.net.ConnectException sur toutes les requêtes. L'endpoint est mort,
 * comme le laissait présager le retour de 2016. Code conservé tel quel (il se
 * dégrade proprement vers "--", sans jamais planter le tableau des départs)
 * au cas où une autre gare ou un autre moment donnerait un résultat différent,
 * mais ne pas s'attendre à ce que cette source fonctionne en l'état.
 *
 * Utilisé uniquement à titre exploratoire pour ce proof of concept, en toute
 * connaissance de ces risques (décision explicite du porteur du projet).
 */
public class TransilienUnofficialPlatformProvider implements PlatformProvider {

    private static final String ENDPOINT = "http://sncf.mobi/infotrafic/iphoneapp/transilien/?gare=";

    private final Map<String, String> stationToTr3Code; // nom de gare normalisé -> code TR3
    private final Map<String, JSONObject> responseCache = new HashMap<>(); // code TR3 -> réponse brute (succès uniquement)
    private final Set<String> attemptedTr3Codes = new HashSet<>(); // code TR3 déjà tenté (succès ou échec) durant l'exécution
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

    // Un seul appel réseau par code TR3 pour toute l'exécution, y compris en
    // cas d'échec (Map.computeIfAbsent ne mémorise pas les résultats null,
    // ce qui provoquait une nouvelle tentative de connexion à chaque train).
    private JSONObject getOrFetch(String tr3) {
        if (responseCache.containsKey(tr3)) {
            return responseCache.get(tr3);
        }
        if (attemptedTr3Codes.contains(tr3)) {
            return null; // déjà tenté et échoué durant cette exécution
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
                return null; // format inattendu (pas un objet JSON)
            }
            return new JSONObject(body);
        } catch (Exception e) {
            if (debug) {
                System.out.println("[Transilien non-officiel] Endpoint injoignable pour " + tr3 + " : " + e);
            }
            return null;
        }
    }

    // Extraction volontairement défensive : le format exact de cette API
    // non-documentée n'est pas connu avec certitude (aucune doc officielle).
    // On tente plusieurs noms de champs plausibles et on retombe sur "--"
    // si rien ne correspond, plutôt que de planter.
    private String extractPlatform(JSONObject response, String trainNumber) {
        JSONArray trains = null;
        try {
            if (response.has("train")) {
                trains = response.getJSONArray("train");
            } else if (response.has("trains")) {
                trains = response.getJSONArray("trains");
            }
        } catch (Exception ignored) {
            // la clé existe mais n'est pas un tableau : format différent de ce qu'on attendait
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
     * Construit la table de correspondance "nom de gare" -> "code TR3" depuis
     * la valeur de configuration TRANSILIEN_CODES, au format :
     *   Nom Gare 1:XXX,Nom Gare 2:YYY
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
