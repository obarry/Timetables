package com.timetables.apisncf;

/** Thrown when no station matches the entered name. */
public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(String stationName) {
        super("Aucune gare trouvée pour « " + stationName + " ».");
    }
}
