package com.timetables;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.BoardEntry;
import com.timetables.apisncf.BoardType;
import com.timetables.apisncf.PlatformProvider;
import com.timetables.apisncf.Station;
import com.timetables.apisncf.StationNotFoundException;
import com.timetables.ui.BoardPanel;
import com.timetables.ui.StationAutocomplete;

/**
 * Graphical (Swing) application: departures board (blue) or arrivals board
 * (green) of a station, user's choice. Reuses APISNCF / PlatformProvider / AppConfig
 * as is: only the presentation differs from TimetablesConsole.
 */
public class TimetablesApp {

    private static final int REFRESH_SECONDS = 30;

    // Counter of requests started: a response that arrives after a more recent
    // request (other station, other board) was started is ignored.
    private static final AtomicInteger requestSeq = new AtomicInteger();

    /** The board being shown: the typed name, and the station when it was picked from the suggestions. */
    private record Target(String name, Station station) {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TimetablesApp::createAndShow);
    }

    private static void createAndShow() {
        Properties config = AppConfig.load();
        if (config == null) {
            showConfigError("Fichier resources/Timetables.properties introuvable.\n"
                    + "Créez-le avec au moins la ligne :\nAPI_KEY=votre_cle_api_sncf");
            return;
        }

        String apiKey = config.getProperty("API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            showConfigError("Clé API SNCF manquante ou vide dans resources/Timetables.properties.\n"
                    + "Ajoutez la ligne : API_KEY=votre_cle_api_sncf");
            return;
        }

        PlatformProvider platformProvider = AppConfig.buildPlatformProvider(config);
        APISNCF api = AppConfig.buildApi(apiKey);

        JFrame frame = new JFrame("Timetables — départs SNCF");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(960, 640);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        BoardPanel boardPanel = new BoardPanel();

        JTextField stationField = new JTextField(24);
        StationAutocomplete autocomplete = new StationAutocomplete(stationField, api);
        Target[] shown = new Target[1]; // what the automatic refresh reloads
        JButton goButton = new JButton("Afficher");
        JToggleButton departuresButton = new JToggleButton(BoardType.DEPARTURES.label(), true);
        JToggleButton arrivalsButton = new JToggleButton(BoardType.ARRIVALS.label());
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(departuresButton);
        modeGroup.add(arrivalsButton);

        JLabel sourceLabel = new JLabel("Source quai/voie : " + platformProvider.name());
        sourceLabel.setForeground(new Color(210, 220, 235));
        sourceLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topBar.setBackground(new Color(6, 15, 40));
        JLabel gareLabel = new JLabel("Gare :");
        gareLabel.setForeground(Color.WHITE);
        topBar.add(gareLabel);
        topBar.add(stationField);
        topBar.add(departuresButton);
        topBar.add(arrivalsButton);
        topBar.add(goButton);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(topBar, BorderLayout.NORTH);
        JPanel sourceBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sourceBar.setBackground(new Color(6, 15, 40));
        sourceBar.add(sourceLabel);
        topContainer.add(sourceBar, BorderLayout.SOUTH);

        frame.add(topContainer, BorderLayout.NORTH);
        frame.add(new JScrollPane(boardPanel), BorderLayout.CENTER);
        frame.setVisible(true);

        Timer refreshTimer = new Timer(REFRESH_SECONDS * 1000, null);
        refreshTimer.setRepeats(true);
        refreshTimer.addActionListener(e -> {
            Target target = shown[0];
            if (target != null) {
                refresh(api, platformProvider, boardPanel.getBoardType(), target, boardPanel, false);
            }
        });

        Runnable triggerSearch = () -> {
            String station = stationField.getText().trim();
            if (station.isEmpty()) {
                return;
            }
            // A station picked in the list is used as is; typed text is searched by name.
            shown[0] = new Target(station, autocomplete.getPicked());
            refreshTimer.stop();
            refresh(api, platformProvider, boardPanel.getBoardType(), shown[0], boardPanel, true);
            refreshTimer.start();
        };
        autocomplete.setOnPicked(triggerSearch);

        // Switching boards: new color right away, then reload
        // if a station has already been entered.
        departuresButton.addActionListener(e ->
                selectBoard(BoardType.DEPARTURES, boardPanel, frame, refreshTimer, stationField, triggerSearch));
        arrivalsButton.addActionListener(e ->
                selectBoard(BoardType.ARRIVALS, boardPanel, frame, refreshTimer, stationField, triggerSearch));

        goButton.addActionListener(e -> triggerSearch.run());
        stationField.addActionListener(e -> triggerSearch.run());
    }

    private static void selectBoard(BoardType type, BoardPanel boardPanel, JFrame frame, Timer refreshTimer,
            JTextField stationField, Runnable triggerSearch) {
        if (boardPanel.getBoardType() == type) {
            return;
        }
        requestSeq.incrementAndGet(); // invalidates any response still in flight for the old board
        refreshTimer.stop();
        boardPanel.setBoardType(type);
        frame.setTitle("Timetables — " + type.label().toLowerCase() + " SNCF");
        if (!stationField.getText().trim().isEmpty()) {
            triggerSearch.run();
        }
    }

    private static void showConfigError(String message) {
        JOptionPane.showMessageDialog(null, message, "Configuration manquante", JOptionPane.ERROR_MESSAGE);
    }

    /** Fetches the board in the background (SwingWorker) so the UI never freezes. */
    private static void refresh(APISNCF api, PlatformProvider platformProvider, BoardType type, Target target,
            BoardPanel boardPanel, boolean showLoading) {
        final String station = target.name();
        final int seq = requestSeq.incrementAndGet();
        if (showLoading) {
            // Not for the automatic refresh: keep the board displayed
            // until the new data arrives (otherwise it flickers every 30 s).
            boardPanel.setMessage("Chargement des " + type.label().toLowerCase() + " pour « " + station + " »...");
        }

        new SwingWorker<List<BoardEntry>, Void>() {
            private String errorMessage;

            @Override
            protected List<BoardEntry> doInBackground() {
                try {
                    return target.station() != null
                            ? api.fetchBoard(type, target.station(), 100, platformProvider)
                            : api.fetchBoard(type, station, 100, platformProvider);
                } catch (StationNotFoundException e) {
                    errorMessage = e.getMessage();
                } catch (Exception e) {
                    errorMessage = "Erreur : " + e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (seq != requestSeq.get()) {
                    return; // stale response (station or board changed in the meantime)
                }
                if (errorMessage != null) {
                    boardPanel.setMessage(errorMessage);
                    return;
                }
                try {
                    boardPanel.setEntries(station, get());
                } catch (Exception ex) {
                    boardPanel.setMessage("Erreur : " + ex.getMessage());
                }
            }
        }.execute();
    }
}
