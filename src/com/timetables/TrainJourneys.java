package com.timetables;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Scanner;

import com.timetables.apisncf.APISNCF;

public class TrainJourneys {
	

    //private static final String API_KEY = "7465168f-a1ce-4ca4-a543-f385dbfc096d"; // clé API SNCF ici

    public static void main(String[] args) {
    	
    	String API_KEY = "";
    	
    	try (InputStream input = new FileInputStream("resources/Timetables.properties")) {
    	    Properties prop = new Properties();
    	    prop.load(input);
    	    API_KEY = prop.getProperty("API_KEY");
    	    System.out.println(API_KEY);
    	} catch (IOException ex) {
    	    ex.printStackTrace();
    	}

    	APISNCF api = new APISNCF(API_KEY);
    	
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

            api.getTrains(from, to, dateTime);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
