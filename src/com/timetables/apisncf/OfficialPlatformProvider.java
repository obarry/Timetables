package com.timetables.apisncf;

/**
 * L'API SNCF publique (Navitia, api.sncf.com) ne fournit pas l'info de
 * quai/voie sur l'endpoint /departures (vérifié : le champ stop_point ne
 * contient rien de tel, quel que soit le mode de transport). Cette
 * implémentation retourne donc toujours "--".
 */
public class OfficialPlatformProvider implements PlatformProvider {

    @Override
    public String name() {
        return "API officielle SNCF (quai non fourni par cette API)";
    }

    @Override
    public String getPlatform(String stationName, String trainNumber) {
        return "--";
    }
}
