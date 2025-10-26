package com.timetables.apisncf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

public class APISNCF {
	
	protected String api_key;
	
	public APISNCF(String key) {
		api_key = key;
	}
	
    // Convertit un datetime SNCF (YYYYMMDDTHHMMSS) en format lisible
    public static String formatSncfDatetime(String dtStr) {
        DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        DateTimeFormatter outputFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        LocalDateTime dt = LocalDateTime.parse(dtStr, inputFmt);
        return dt.format(outputFmt);
    }

    // Effectue une requête GET sur l’API SNCF
    private String callApi(String urlStr) throws Exception {
    	String auth = Base64.getEncoder().encodeToString((api_key + ":").getBytes());
    	
    	//System.out.println("callAPI - Url: "+urlStr);
    	HttpClient client = HttpClient.newHttpClient();
    	HttpRequest request = HttpRequest.newBuilder(new URI(urlStr)).header("Authorization", "Basic " + auth).GET().build();
    	HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    	        
    	//System.out.println("Response Status: "+ response.statusCode());
    	//System.out.println("Response Body  : "+ response.body());
        return response.body();
    }

    // Recherche l’ID stop_area d’une gare à partir de son nom
    public String searchStationId(String stationName) throws Exception {
    	
        // Call SNCF API with URL /places
        String url = "https://api.sncf.com/v1/coverage/sncf/places?q=" + stationName.replace(" ", "%20");
        String body = callApi(url);
        
        JSONArray places = null;
        if (body.trim().startsWith("{")) {
        	JSONObject json = new JSONObject(body);
            places = json.getJSONArray("places");
       } else if (body.trim().startsWith("[")) {
        	places = new JSONArray(body);
        } else {
        	System.out.println("La reponse n'est pas du JSON valide: " + body);
        }

        for (int i = 0; i < places.length(); i++) {
            JSONObject place = places.getJSONObject(i);
            if (place.getString("embedded_type").equals("stop_area")) {
                JSONObject stopArea = place.getJSONObject("stop_area");
                String id = place.getString("id");
                System.out.println("Gare trouvée : " + stopArea.getString("name") + " / " + id);
                return id;
            }
        }
        return null;
    }

    // Récupère les trajets entre deux gares à une date donnée
    public void getTrains(String fromName, String toName, LocalDateTime dateTime) throws Exception {
        String fromStation = searchStationId(fromName);
        String toStation = searchStationId(toName);

        if (fromStation != null && toStation != null) {
            String dateStr = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
            
            // Call SNCF API with URL /journeys
            String url = "https://api.sncf.com/v1/coverage/sncf/journeys?from=" + fromStation + "&to=" + toStation + "&datetime=" + dateStr;
            String json = callApi(url);
            
            JSONObject obj = new JSONObject(json);
            JSONArray journeys = null;
            
            try {
            	journeys = obj.getJSONArray("journeys");
            }  catch (org.json.JSONException e) {
                System.out.println("⚠️ Aucun journey trouvé.");
                return;
            }

            if (journeys.isEmpty()) {
                System.out.println("⚠️ Aucun train trouvé.");
                return;
            }

            for (int i = 0; i < journeys.length(); i++) {
                JSONObject journey = journeys.getJSONObject(i);
                JSONArray sections = journey.getJSONArray("sections");

                String departure = formatSncfDatetime(sections.getJSONObject(0).getString("departure_date_time"));
                String arrival = formatSncfDatetime(sections.getJSONObject(sections.length() - 1).getString("arrival_date_time"));

                System.out.println("🚆 Départ : " + departure + " → Arrivée : " + arrival);
            }
        }
    }
    
    public void getDepartures(String station, int nb_trains) throws Exception {

    	String json = callApi("https://api.sncf.com/v1/coverage/sncf/stop_areas/" + searchStationId(station) + "/departures?count="+nb_trains);
         
        JSONObject obj = new JSONObject(json);
        JSONArray departures = null;

        try {
        	departures = obj.getJSONArray("departures");
            System.out.println("Nombre de departures trouvés: "+departures.length());
        }  catch (org.json.JSONException e) {
            System.out.println("Aucun departure trouvé.");
            return;
        }

//    	System.out.printf("%-10s %-20s %-20s%n", "Heure", "Train", "Destination");
//    	System.out.println("------------------------------------------------------------");
        
    	System.out.printf("%-10s %-20s %-45s %-10s %-15s%n","Heure", "Train", "Destination", "Quai", "Statut"); // Ligne d'en-tête
    	System.out.println("--------------------------------------------------------------------------------------------------------"); // Ligne de séparation

    	int i_disp = 0;
    	for (int i = 0; i < departures.length() && i_disp <15; i++) {
    		JSONObject dep = departures.getJSONObject(i);
    		JSONObject info = dep.getJSONObject("display_informations");
    		//JSONObject line = dep.getJSONObject("route").getJSONObject("line");
    		    		
    		// Lire le commercial mode (TER, TGV, ...)
    	    String commercialMode = info.optString("commercial_mode", "").toUpperCase();

    	    // Filtre : on garde TER (toutes régions) et grandes lignes
    	    boolean isTER = commercialMode.startsWith("REGION");
    	    boolean isTGV = commercialMode.contains("TGV") || commercialMode.contains("OUIGO");
    	    boolean isIntercites = commercialMode.contains("INTERCITES") || commercialMode.contains("ICE");

    	    if (!(isTER || isTGV || isIntercites)) {
    	    	//System.out.println(commercialMode);
    	        continue; // on saute les autres (bus, tram, métro, etc.)
    	    }
    		
       		if (i_disp == 0) System.out.println(dep);
    	    i_disp++;

    	    
    		// Lire le train
    		String numTrain = info.optString("headsign", "N/A");
    		String comMode = info.optString("commercial_mode", "");
    		String train = comMode + " " + numTrain;
    		
    		
    		// Lire la destination lisible
    		String destination = info.optString("direction", "N/A");
    		
    	    JSONObject stopDateTime = dep.getJSONObject("stop_date_time");
    	    
    	    // Quai
    	    JSONObject stopPoint = null;
    	    if (stopDateTime.has("stop_point")) {
    	    	stopPoint = stopDateTime.getJSONObject("stop_point");
    	    }
    	    String quai = (stopPoint != null) ? stopPoint.optString("platform", "--") : "--";
    		
    	    // Heures prévue et réelle
    	    String baseDateTimeStr = stopDateTime.optString("base_departure_date_time", "");
    	    String realDateTimeStr = stopDateTime.optString("departure_date_time", "");

    	    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    	    LocalDateTime baseTime = null;
    	    LocalDateTime realTime = null;
    	    if (!baseDateTimeStr.isEmpty()) {
    	        baseTime = LocalDateTime.parse(baseDateTimeStr, fmt);
    	    }
    	    if (!realDateTimeStr.isEmpty()) {
    	        realTime = LocalDateTime.parse(realDateTimeStr, fmt);
    	    }

    	    // Statut
    	    String status;
    	    if (realTime == null) {
    	        status = "Supprimé";
    	    } else if (baseTime != null && realTime.isAfter(baseTime)) {
    	        long minutesLate = java.time.Duration.between(baseTime, realTime).toMinutes();
    	        status = "Retardé +" + minutesLate + " min";
    	    } else {
    	        status = "À l'heure";
    	    }
    	    
    	    String heureAffichee = (realTime != null)
    	            ? realTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    	            : "--:--";

    	    System.out.printf("%-10s %-20s %-45s %-10s %-15s%n",
    	            heureAffichee, train, destination, quai, status);


    		// Lire l'heure
//    		JSONObject sdt = dep.getJSONObject("stop_date_time");
//    		String dateTimeStr = sdt.getString("departure_date_time");

    		// Formatage heure (API donne 20250824T153000 → 15:30)
//    		LocalDateTime dt = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
//    		String heure = dt.format(DateTimeFormatter.ofPattern("HH:mm"));
//
//    		System.out.printf("%-10s %-20s %-20s%n", heure, train, destination);
    	}

    }

}
