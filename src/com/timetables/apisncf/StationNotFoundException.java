package com.timetables.apisncf;

/** Levée quand aucune gare ne correspond au nom saisi. */
public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(String stationName) {
        super("Aucune gare trouvée pour « " + stationName + " ».");
    }
}
