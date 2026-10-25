package com.timetables.apisncf;

/**
 * The two kinds of station display boards: departures (blue) and arrivals
 * (green). Gathers everything that differs between the two on the data side (Navitia
 * endpoint, time field names) and on the vocabulary side (titles, label of the
 * "other station" column), so that the rest of the code can be shared.
 */
public enum BoardType {

    DEPARTURES("departures", "departure_date_time", "base_departure_date_time",
            "Départs", "TABLEAU DES DÉPARTS", "Destination"),

    ARRIVALS("arrivals", "arrival_date_time", "base_arrival_date_time",
            "Arrivées", "TABLEAU DES ARRIVÉES", "Provenance");

    private final String apiPath;
    private final String timeField;
    private final String baseTimeField;
    private final String label;
    private final String title;
    private final String otherStationLabel;

    BoardType(String apiPath, String timeField, String baseTimeField,
            String label, String title, String otherStationLabel) {
        this.apiPath = apiPath;
        this.timeField = timeField;
        this.baseTimeField = baseTimeField;
        this.label = label;
        this.title = title;
        this.otherStationLabel = otherStationLabel;
    }

    /** End of the Navitia URL (/stop_areas/{id}/<apiPath>) AND key of the JSON array in the response. */
    public String apiPath() {
        return apiPath;
    }

    /** stop_date_time field holding the actual (real-time) time. */
    public String timeField() {
        return timeField;
    }

    /** stop_date_time field holding the scheduled time (used to compute the delay). */
    public String baseTimeField() {
        return baseTimeField;
    }

    /** Short label for a button or window title: "Départs" / "Arrivées" (French, as displayed). */
    public String label() {
        return label;
    }

    /** Board title shown when no station has been chosen yet. */
    public String title() {
        return title;
    }

    /** "Destination" for departures, "Provenance" for arrivals (French, as displayed). */
    public String otherStationLabel() {
        return otherStationLabel;
    }
}
