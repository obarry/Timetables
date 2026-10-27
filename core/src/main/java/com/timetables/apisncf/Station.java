package com.timetables.apisncf;

/**
 * A station (Navitia "stop_area") as proposed by the search: its identifier, used to ask
 * for the board, and the name shown to the user, for example "Lyon Part Dieu (Lyon)".
 */
public record Station(String id, String name) {

    /** Lets list widgets (Android, Swing) display the station without any adapter code. */
    @Override
    public String toString() {
        return name;
    }
}
