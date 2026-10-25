package com.timetables;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.Journey;
import com.timetables.apisncf.StationNotFoundException;

public class TrainJourneys {
	
    public static void main(String[] args) {
    	
    	Properties prop = AppConfig.load();
    	if (prop == null) {
    	    System.err.println("Cannot read resources/Timetables.properties");
    	    return;
    	}
    	// Never print the API key: it is a secret.
    	String API_KEY = prop.getProperty("API_KEY");

    	APISNCF api = AppConfig.buildApi(API_KEY);
    	
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Nom de la gare de départ : ");
            String from = scanner.nextLine();

            System.out.print("Nom de la gare d’arrivée : ");
            String to = scanner.nextLine();

            System.out.print("Date de départ (format JJ/MM/AAAA) : ");
            String dateStr = scanner.nextLine();

            System.out.print("Heure de départ (format HH:MM) : ");
            String timeStr = scanner.nextLine();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            LocalDateTime dateTime = LocalDateTime.parse(dateStr + " " + timeStr, formatter);

            DateTimeFormatter shown = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            List<Journey> journeys = api.findJourneys(from, to, dateTime);
            if (journeys.isEmpty()) {
                System.out.println("⚠️ Aucun train trouvé.");
            }
            for (Journey journey : journeys) {
                System.out.println("🚆 Départ : " + journey.departure().format(shown)
                        + " → Arrivée : " + journey.arrival().format(shown));
            }

        } catch (StationNotFoundException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
