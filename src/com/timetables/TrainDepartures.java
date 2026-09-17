package com.timetables;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.OfficialPlatformProvider;
import com.timetables.apisncf.PlatformProvider;
import com.timetables.apisncf.TransilienUnofficialPlatformProvider;

public class TrainDepartures {

    public static void main(String[] args) {

        // Force la sortie standard en UTF-8 : évite les accents/symboles
        // mal affichés sur les consoles Windows (cmd.exe / PowerShell).
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Properties config = loadConfig();
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

        PlatformProvider platformProvider = buildPlatformProvider(config);
        System.out.println("Source quai/voie : " + platformProvider.name());

        String station;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Nom de la gare : ");
            station = scanner.nextLine().trim();
        }

        if (station.isEmpty()) {
            System.out.println("Aucune gare saisie.");
            return;
        }

        APISNCF api = new APISNCF(apiKey);
        try {
            api.getDepartures(station, 100, platformProvider);
        } catch (Exception e) {
            System.out.println("Impossible de récupérer les départs : " + e.getMessage());
        }
    }

    private static Properties loadConfig() {
        try (InputStream input = new FileInputStream("resources/Timetables.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            return prop;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Choisit la source de quai/voie selon la config (PLATFORM_SOURCE) :
     *   OFFICIAL              -> API officielle SNCF (quai toujours "--")
     *   TRANSILIEN_UNOFFICIEL -> endpoint non documenté, Transilien/RER uniquement,
     *                            expérimental (voir TransilienUnofficialPlatformProvider)
     */
    private static PlatformProvider buildPlatformProvider(Properties config) {
        String source = config.getProperty("PLATFORM_SOURCE", "OFFICIAL").trim().toUpperCase();

        if (source.equals("TRANSILIEN_UNOFFICIEL") || source.equals("TRANSILIEN_UNOFFICIAL")) {
            Map<String, String> codes = TransilienUnofficialPlatformProvider.parseStationCodes(
                    config.getProperty("TRANSILIEN_CODES", ""));
            boolean debug = Boolean.parseBoolean(config.getProperty("PLATFORM_DEBUG", "false"));
            return new TransilienUnofficialPlatformProvider(codes, debug);
        }

        return new OfficialPlatformProvider();
    }
}
