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
 * Chargement de resources/Timetables.properties, partagé par la version
 * console (TrainDepartures) et la version graphique (TrainDeparturesGraphical)
 * pour éviter de dupliquer cette logique dans les deux.
 */
public final class AppConfig {

    private AppConfig() {
    }

    public static Properties load() {
        try (InputStream input = new FileInputStream("resources/Timetables.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            return prop;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Choisit la source de quai/voie selon la config (PLATFORM_SOURCE) :
     *   OFFICIAL              -> API officielle SNCF (quai toujours "--")
     *   TRANSILIEN_UNOFFICIEL -> endpoint non documenté, Transilien/RER uniquement,
     *                            expérimental (voir TransilienUnofficialPlatformProvider)
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
