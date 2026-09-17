package com.timetables.apisncf;

/**
 * Fournit (si possible) le numéro de quai/voie d'un train.
 *
 * L'API officielle SNCF ne fournit pas cette info (vérifié) ; une implémentation
 * alternative peut tenter de la récupérer via une source non-officielle.
 */
public interface PlatformProvider {

    /** Nom de la source, pour affichage à l'utilisateur. */
    String name();

    /**
     * Retourne le quai/voie pour ce train dans cette gare, ou "--" si inconnu
     * ou indisponible. Ne doit jamais lever d'exception : toute erreur réseau
     * ou de parsing doit être absorbée et donner "--".
     */
    String getPlatform(String stationName, String trainNumber);
}
