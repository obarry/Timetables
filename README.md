# Timetables

Recrée en Java les tableaux d'affichage SNCF (départs en bleu, arrivées en vert) à partir de
l'API publique SNCF (Navitia). Projet à but ludique, didactique (utilisation d'une API REST
publique) et utile (usage personnel pour voyager en train).

État actuel : proof of concept en mode console, focalisé sur le tableau des départs.
L'architecture, le design graphique et le portage mobile viendront dans des phases suivantes.

## Configuration

1. Obtenir une clé API gratuite sur https://numerique.sncf.com/startup/api/token-developpeur/
2. Créer le fichier `resources/Timetables.properties` (non versionné, voir `.gitignore`) avec :
   ```
   API_KEY=votre_cle_api_sncf
   ```

## Lancer le tableau des départs (mode console)

Depuis la racine du projet, avec le JDK et le jar `org.json` sur le classpath (chemin à adapter) :

```
javac -d bin -cp "C:/Users/olivi/Development/JSON/org.json-20120521.jar" -sourcepath src src/com/timetables/TrainDepartures.java
java -cp "bin;C:/Users/olivi/Development/JSON/org.json-20120521.jar" com.timetables.TrainDepartures
```

Ou directement depuis Eclipse : clic droit sur `TrainDepartures.java` > Run As > Java Application.

Le programme demande un nom de gare, puis affiche les prochains départs TER / TGV / Intercités
(heure, train, destination, voie, statut).
