package com.timetables;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import com.timetables.apisncf.OfficialPlatformProvider;
import com.timetables.apisncf.PlatformProvider;
import com.timetables.apisncf.TransilienUnofficialPlatformProvider;

/**
 * Loads resources/Timetables.properties, shared by the console version
 * (TimetablesConsole) and the graphical version (TimetablesApp)
 * to avoid duplicating this logic in both.
 */
public final class AppConfig {

    private AppConfig() {
    }

    // The file sits at the root of the repository. Depending on how the program is
    // started, the working directory is that root (Gradle tasks, command line) or the
    // desktop/ module folder (IDE "Run As"), hence the two candidates.
    private static final String[] CONFIG_PATHS = {
            "resources/Timetables.properties",
            "../resources/Timetables.properties"
    };

    public static Properties load() {
        for (String path : CONFIG_PATHS) {
            try (InputStream input = new FileInputStream(path)) {
                Properties prop = new Properties();
                prop.load(input);
                return prop;
            } catch (IOException ex) {
                // not here: try the next candidate
            }
        }
        return null;
    }

    /**
     * Chooses the platform/track source according to the config (PLATFORM_SOURCE):
     *   OFFICIAL              -> official SNCF API (platform always "--")
     *   TRANSILIEN_UNOFFICIEL -> undocumented endpoint, Transilien/RER only,
     *                            experimental (see TransilienUnofficialPlatformProvider)
     */
    public static PlatformProvider buildPlatformProvider(Properties config) {
        String source = config.getProperty("PLATFORM_SOURCE", "OFFICIAL").trim().toUpperCase();

        if (source.equals("TRANSILIEN_UNOFFICIEL") || source.equals("TRANSILIEN_UNOFFICIAL")) {
            Map<String, String> codes = TransilienUnofficialPlatformProvider.parseStationCodes(
                    config.getProperty("TRANSILIEN_CODES", ""));
            boolean debug = Boolean.parseBoolean(config.getProperty("PLATFORM_DEBUG", "false"));
            return new TransilienUnofficialPlatformProvider(codes, debug);
        }

        return new OfficialPlatformProvider();
    }
}
