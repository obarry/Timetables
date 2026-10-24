package com.timetables.apisncf;

/**
 * The public SNCF API (Navitia, api.sncf.com) does not provide platform/track
 * information on the /departures endpoint (verified: the stop_point field
 * contains nothing of the kind, whatever the transport mode). This
 * implementation therefore always returns "--".
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
