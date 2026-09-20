package com.timetables;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.Departure;
import com.timetables.apisncf.PlatformProvider;
import com.timetables.apisncf.StationNotFoundException;
import com.timetables.ui.DepartureBoardPanel;

/**
 * Version graphique (Swing) du tableau des départs. Réutilise APISNCF /
 * PlatformProvider / AppConfig tels quels : seule la présentation change par
 * rapport à TrainDepartures (console).
 */
public class TrainDeparturesGraphical {

    private static final int REFRESH_SECONDS = 30;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TrainDeparturesGraphical::createAndShow);
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
        APISNCF api = new APISNCF(apiKey);

        JFrame frame = new JFrame("Tableau des départs SNCF");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(960, 640);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        DepartureBoardPanel boardPanel = new DepartureBoardPanel();

        JTextField stationField = new JTextField(24);
        JButton goButton = new JButton("Afficher");
        JLabel sourceLabel = new JLabel("Source quai/voie : " + platformProvider.name());
        sourceLabel.setForeground(new Color(210, 220, 235));
        sourceLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topBar.setBackground(new Color(6, 15, 40));
        JLabel gareLabel = new JLabel("Gare :");
        gareLabel.setForeground(Color.WHITE);
        topBar.add(gareLabel);
        topBar.add(stationField);
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
        refreshTimer.addActionListener(e -> refresh(api, platformProvider, stationField.getText().trim(), boardPanel));

        Runnable triggerSearch = () -> {
            String station = stationField.getText().trim();
            if (station.isEmpty()) {
                return;
            }
            refreshTimer.stop();
            refresh(api, platformProvider, station, boardPanel);
            refreshTimer.start();
        };

        goButton.addActionListener(e -> triggerSearch.run());
        stationField.addActionListener(e -> triggerSearch.run());
    }

    private static void showConfigError(String message) {
        JOptionPane.showMessageDialog(null, message, "Configuration manquante", JOptionPane.ERROR_MESSAGE);
    }

    /** Va chercher les départs en tâche de fond (SwingWorker) pour ne jamais geler l'interface. */
    private static void refresh(APISNCF api, PlatformProvider platformProvider, String station, DepartureBoardPanel boardPanel) {
        boardPanel.setMessage("Chargement des départs pour « " + station + " »...");

        new SwingWorker<List<Departure>, Void>() {
            private String errorMessage;

            @Override
            protected List<Departure> doInBackground() {
                try {
                    return api.fetchDepartures(station, 100, platformProvider);
                } catch (StationNotFoundException e) {
                    errorMessage = e.getMessage();
                } catch (Exception e) {
                    errorMessage = "Erreur : " + e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (errorMessage != null) {
                    boardPanel.setMessage(errorMessage);
                    return;
                }
                try {
                    boardPanel.setDepartures(station, get());
                } catch (Exception ex) {
                    boardPanel.setMessage("Erreur : " + ex.getMessage());
                }
            }
        }.execute();
    }
}
