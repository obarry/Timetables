package com.timetables.apisncf;

/**
 * Une ligne du tableau des départs, déjà calculée (heure, retard, quai...),
 * prête à être affichée par n'importe quelle vue (console, graphique...).
 */
public record Departure(
        String heure,
        String train,
        String destination,
        String quai,
        String statut,
        boolean retarde,
        boolean supprime
) {
}
