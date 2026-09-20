package com.timetables;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Scanner;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.PlatformProvider;

public class TrainDepartures {

    public static void main(String[] args) {

        // Force la sortie standard en UTF-8 : évite les accents/symboles
        // mal affichés sur les consoles Windows (cmd.exe / PowerShell).
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
}
