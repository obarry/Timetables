package com.timetables.apisncf;

/**
 * Provides (when possible) the platform/track number of a train.
 *
 * The official SNCF API does not provide this information (verified); an alternative
 * implementation may try to get it from an unofficial source.
 */
public interface PlatformProvider {

    /** Name of the source, for display to the user. */
    String name();

    /**
     * Returns the platform/track for this train at this station, or "--" if unknown
     * or unavailable. Must never throw: any network or parsing error
     * must be absorbed and result in "--".
     */
    String getPlatform(String stationName, String trainNumber);
}
