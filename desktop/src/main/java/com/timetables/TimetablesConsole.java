package com.timetables;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.BoardEntry;
import com.timetables.apisncf.BoardType;
import com.timetables.apisncf.PlatformProvider;
import com.timetables.apisncf.StationNotFoundException;

/**
 * Console version: departures or arrivals board of a station.
 */
public class TimetablesConsole {

    public static void main(String[] args) {

        // Force standard output to UTF-8: avoids garbled accents/symbols
        // on Windows consoles (cmd.exe / PowerShell).
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Properties config = AppConfig.load();
        if (config == null) {
            System.out.println("Fichier resources/Timetables.properties introuvable.");
            System.out.println("Créez-le avec au moins la ligne :");
            System.out.println("    API_KEY=votre_cle_api_sncf");
            return;
        }

        String apiKey = config.getProperty("API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Clé API SNCF manquante ou vide dans resources/Timetables.properties.");
            System.out.println("Ajoutez la ligne : API_KEY=votre_cle_api_sncf");
            return;
        }

        PlatformProvider platformProvider = AppConfig.buildPlatformProvider(config);
        System.out.println("Source quai/voie : " + platformProvider.name());

        String station;
        BoardType type;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Nom de la gare : ");
            station = scanner.nextLine().trim();

            System.out.print("Tableau : (D)épart ou (A)rrivées ? [D] ");
            String choice = scanner.nextLine().trim().toLowerCase();
            type = choice.startsWith("a") ? BoardType.ARRIVALS : BoardType.DEPARTURES;
        }

        if (station.isEmpty()) {
            System.out.println("Aucune gare saisie.");
            return;
        }

        APISNCF api = AppConfig.buildApi(apiKey);
        try {
            printBoard(api, type, station, 100, platformProvider);
        } catch (Exception e) {
            System.out.println("Impossible de récupérer le tableau : " + e.getMessage());
        }
    }

    // Console presentation of a board (the core only fetches the data).
    private static void printBoard(APISNCF api, BoardType type, String station, int nbTrains,
            PlatformProvider platformProvider) throws Exception {

        List<BoardEntry> entries;
        try {
            entries = api.fetchBoard(type, station, nbTrains, platformProvider);
        } catch (StationNotFoundException e) {
            System.out.println(e.getMessage());
            return;
        }

        if (entries.isEmpty()) {
            System.out.println("Aucun TER / TGV / Intercités / RER / Transilien trouvé pour cette gare pour le moment.");
            return;
        }

        String maj = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println();
        System.out.println("=== " + type.title() + " — " + station.toUpperCase() + " (maj " + maj + ") ===");
        System.out.printf("%-10s %-20s %-45s %-10s %-15s%n", "Heure", "Train", type.otherStationLabel(), "Quai", "Statut"); // Header row
        System.out.println("--------------------------------------------------------------------------------------------------------"); // Separator row

        for (BoardEntry e : entries) {
            System.out.printf("%-10s %-20s %-45s %-10s %-15s%n",
                    e.heure(), e.train(), e.otherStation(), e.quai(), e.statut());
        }
    }
}
