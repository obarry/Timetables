package com.timetables;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * THROWAWAY PROBE (not meant to stay in the project): checks what
 * the SNCF API /arrivals endpoint really provides, in particular the
 * ORIGIN STATION (provenance) of the trains.
 *
 * Deliberately independent of org.json and of the rest of the project: it reads
 * resources/Timetables.properties (API_KEY) directly and prints the raw
 * JSON, pretty-printed. Run from the project root, like the other mains.
 *
 * Steps:
 *   1. station search (/places);
 *   2. dump of the first 2 arrivals (/stop_areas/{id}/arrivals?count=2);
 *   3. for the first 2 "vehicle_journey" links found, dump of the start of
 *      /vehicle_journeys/{id} (the 1st stop_time = the train's origin station).
 */
public class ArrivalsProbe {

    private static final String BASE = "https://api.sncf.com/v1/coverage/sncf";
    private static final int MAX_LINES_ARRIVALS = 260;
    private static final int LINES_AFTER_STOP_TIMES = 45;

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Properties config = new Properties();
        try (InputStream in = new FileInputStream("resources/Timetables.properties")) {
            config.load(in);
        } catch (Exception e) {
            System.out.println("Impossible de lire resources/Timetables.properties : " + e.getMessage());
            System.out.println("(lancer depuis la racine du projet)");
            return;
        }
        String key = config.getProperty("API_KEY", "").trim();
        if (key.isEmpty()) {
            System.out.println("API_KEY manquante dans resources/Timetables.properties");
            return;
        }
        String auth = Base64.getEncoder().encodeToString((key + ":").getBytes(StandardCharsets.UTF_8));

        String station;
        if (args.length > 0) {
            station = String.join(" ", args);
        } else {
            System.out.print("Nom de la gare : ");
            station = new Scanner(System.in).nextLine().trim();
        }

        // 1. Station
        String places = get(auth, BASE + "/places?q=" + URLEncoder.encode(station, StandardCharsets.UTF_8).replace("+", "%20"));
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"(stop_area:[^\"]+)\"").matcher(places);
        if (!m.find()) {
            System.out.println("Aucune gare trouvée. Réponse brute :\n" + places);
            return;
        }
        String stopAreaId = m.group(1);
        System.out.println("Gare : " + stopAreaId);

        // 2. Arrivals
        String arrivals = get(auth, BASE + "/stop_areas/" + stopAreaId + "/arrivals?count=2");
        // The start of an arrival (route, stop_point...) is very long: we directly
        // print the useful sections of the 1st arrival.
        List<String> arrLines = pretty(arrivals);
        int arrStart = indexOf(arrLines, "\"arrivals\"", 0);
        System.out.println("\n================ /arrivals?count=2 : sections utiles de la 1re arrivée ================");
        for (String section : new String[] { "\"display_informations\"", "\"stop_date_time\"" }) {
            int idx = indexOf(arrLines, section, Math.max(arrStart, 0));
            System.out.println("\n--- " + section + " ---");
            if (idx < 0) {
                System.out.println("[!] section introuvable");
            } else {
                printLines(arrLines, idx, section.equals("\"stop_date_time\"") ? 16 : 34);
            }
        }

        // 3. Linked vehicle journeys
        List<String> vjIds = new ArrayList<>();
        Matcher vm = Pattern.compile("\"id\"\\s*:\\s*\"(vehicle_journey:[^\"]+)\"").matcher(arrivals);
        while (vm.find() && vjIds.size() < 2) {
            if (!vjIds.contains(vm.group(1))) {
                vjIds.add(vm.group(1));
            }
        }
        if (vjIds.isEmpty()) {
            System.out.println("\n[!] Aucun lien vehicle_journey trouvé dans les arrivées.");
            return;
        }
        for (String vj : vjIds) {
            System.out.println("\n================ /vehicle_journeys/" + vj + " (début de stop_times) ================");
            try {
                List<String> lines = pretty(get(auth, BASE + "/vehicle_journeys/" + vj.replace(" ", "%20")));
                int idx = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains("\"stop_times\"")) {
                        idx = i;
                        break;
                    }
                }
                if (idx < 0) {
                    System.out.println("[!] pas de stop_times ; début de la réponse :");
                    printLines(lines, 0, 60);
                } else {
                    printLines(lines, idx, LINES_AFTER_STOP_TIMES);
                }
            } catch (Exception e) {
                System.out.println("[!] échec : " + e.getMessage());
            }
        }
        System.out.println("\n=== FIN DE LA SONDE — copiez toute la sortie ci-dessus ===");
    }

    private static String get(String auth, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(new URI(url)).header("Authorization", "Basic " + auth).GET().build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " sur " + url + " : " + resp.body());
        }
        return resp.body();
    }

    private static int indexOf(List<String> lines, String needle, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static void printLines(List<String> lines, int from, int count) {
        int end = Math.min(lines.size(), from + count);
        for (int i = from; i < end; i++) {
            System.out.println(lines.get(i));
        }
        if (end < lines.size()) {
            System.out.println("... (" + (lines.size() - end) + " lignes supplémentaires non affichées)");
        }
    }

    /** Minimal JSON pretty-printing (indentation), dependency-free. */
    static List<String> pretty(String json) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int indent = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                cur.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            switch (c) {
                case '"':
                    inStr = true;
                    cur.append(c);
                    break;
                case '{':
                case '[':
                    cur.append(c);
                    flush(out, cur, indent);
                    indent++;
                    break;
                case '}':
                case ']':
                    flush(out, cur, indent);
                    indent = Math.max(0, indent - 1);
                    cur.append(c);
                    break;
                case ',':
                    cur.append(c);
                    flush(out, cur, indent);
                    break;
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    break;
                default:
                    cur.append(c);
            }
        }
        flush(out, cur, indent);
        return out;
    }

    private static void flush(List<String> out, StringBuilder cur, int indent) {
        if (cur.length() > 0) {
            out.add("  ".repeat(indent) + cur);
            cur.setLength(0);
        }
    }
}
