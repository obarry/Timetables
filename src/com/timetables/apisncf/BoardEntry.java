package com.timetables.apisncf;

/**
 * One row of a station display board, departure or arrival.
 *
 * @param heure        actual time shown (HH:mm), or "--:--" if cancelled
 * @param train        commercial mode + number (e.g. "TGV INOUI 6711")
 * @param otherStation destination for a departure, origin for an arrival
 * @param quai         platform/track, or "--" if unknown
 * @param statut       status text, in French as displayed (À l'heure, Retardé +N min, Supprimé)
 * @param retarde      true if the train is late
 * @param supprime     true if the train is cancelled
 */
public record BoardEntry(
        String heure,
        String train,
        String otherStation,
        String quai,
        String statut,
        boolean retarde,
        boolean supprime
) {
}
