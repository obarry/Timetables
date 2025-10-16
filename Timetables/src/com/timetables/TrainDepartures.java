package com.timetables;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

import com.timetables.apisncf.APISNCF;

public class TrainDepartures {
    //private static final String API_KEY = "7465168f-a1ce-4ca4-a543-f385dbfc096d"; //  clé API SNCF

    public static void main(String[] args) throws Exception {
    	
    	String API_KEY = "";
    	
    	try (InputStream input = new FileInputStream("resources/Timetables.properties")) {
    	    Properties prop = new Properties();
    	    prop.load(input);
    	    API_KEY = prop.getProperty("API_KEY");
    	    System.out.println(API_KEY);
    	} catch (IOException ex) {
    	    ex.printStackTrace();
    	}

    	String station = null;

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Nom de la gare : ");
            station = scanner.nextLine();
        }
        
        //String stopArea = "stop_area:OCE:SA:8774700"; // Grenoble
        
        APISNCF api = new APISNCF(API_KEY);
        
        api.getDepartures(station, 100);

    }
}