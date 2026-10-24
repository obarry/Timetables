# Timetables

A Java recreation of the SNCF station display boards (departures in blue, arrivals in green),
built on the public SNCF API (Navitia). A project that is playful (recreating the atmosphere of
a station), didactic (learning to use a public REST API) and useful (personal use when travelling
by train).

Current state: proof of concept with departures and arrivals boards, in console and graphical (Swing)
versions. Architecture, graphical design and the mobile port will come in later phases.

## Configuration

1. Get a free API key at https://numerique.sncf.com/startup/api/token-developpeur/
2. Create the file `resources/Timetables.properties` (not versioned, see `.gitignore`) with:
   ```
   API_KEY=your_sncf_api_key
   ```

## Running the application

Two boards to choose from: departures (blue) and arrivals (green).

From the project root, with the JDK and the `org.json` jar on the classpath (adapt the path):

```
javac -d bin -cp "C:/Users/olivi/Development/JSON/org.json-20120521.jar" -sourcepath src src/com/timetables/TimetablesApp.java src/com/timetables/TimetablesConsole.java
```

Graphical version (Swing): pick "Départs" or "Arrivées", type a station name, then click "Afficher".
The board refreshes every 30 seconds.

```
java -cp "bin;C:/Users/olivi/Development/JSON/org.json-20120521.jar" com.timetables.TimetablesApp
```

Console version: asks for a station name, then (D)épart or (A)rrivées.

```
java -cp "bin;C:/Users/olivi/Development/JSON/org.json-20120521.jar" com.timetables.TimetablesConsole
```

Or straight from Eclipse: right-click `TimetablesApp.java` (or `TimetablesConsole.java`) > Run As > Java Application.

The boards show the next TER / TGV / Intercités / RER / Transilien trains (time, train,
destination or origin, platform, status). The user interface texts are in French.

The origin of arriving trains is not provided directly by the API: it is deduced from the
train's first stop (one call per train, cached). The platform is not provided by the official
API either, so it shows "--" (see `resources/Timetables.properties` for the experimental
alternative).
