package com.timetables.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JPanel;

import com.timetables.apisncf.Departure;

/**
 * Recrée l'ambiance visuelle des tableaux d'affichage bleus SNCF (bandes
 * alternées, heure en jaune, destination en blanc, filigrane "départs") sans
 * reproduire le logo SNCF lui-même. Ne fait aucun appel réseau : reçoit une
 * liste de Departure déjà calculée et se contente de la dessiner.
 */
public class DepartureBoardPanel extends JPanel {

    // Couleurs inspirées des vrais panneaux (bandes bleues alternées, jaune, blanc).
    private static final Color BLUE_LIGHT = new Color(27, 74, 158);
    private static final Color BLUE_DARK = new Color(10, 24, 61);
    private static final Color HEADER_BLUE = new Color(6, 15, 40);
    private static final Color YELLOW = new Color(255, 205, 0);
    private static final Color WHITE = Color.WHITE;
    private static final Color LATE_ORANGE = new Color(255, 150, 40);
    private static final Color CANCELLED_RED = new Color(255, 90, 90);
    private static final Color WATERMARK = new Color(255, 255, 255, 55);

    private static final int ROW_HEIGHT = 46;
    private static final int HEADER_HEIGHT = 56;
    private static final int TIME_COL_WIDTH = 100;
    private static final int BADGE_COL_WIDTH = 90;
    private static final int PLATFORM_COL_WIDTH = 64;
    private static final int PADDING = 14;

    private String stationName = "";
    private String lastUpdate = "";
    private List<Departure> departures = List.of();
    private String message = "Saisissez une gare puis cliquez sur Afficher.";

    public DepartureBoardPanel() {
        setBackground(BLUE_DARK);
        setPreferredSize(new Dimension(900, HEADER_HEIGHT + ROW_HEIGHT * 12));
    }

    public void setDepartures(String stationName, List<Departure> departures) {
        this.stationName = stationName;
        this.departures = departures;
        this.lastUpdate = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        this.message = departures.isEmpty()
                ? "Aucun TER / TGV / Intercités / RER / Transilien trouvé pour cette gare pour le moment."
                : null;
        setPreferredSize(new Dimension(getPreferredSize().width, HEADER_HEIGHT + ROW_HEIGHT * Math.max(departures.size(), 1)));
        revalidate();
        repaint();
    }

    public void setMessage(String message) {
        this.departures = List.of();
        this.message = message;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        paintHeader(g2, width);

        if (message != null) {
            paintWatermark(g2, width, height);
            g2.setColor(WHITE);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g2.drawString(message, PADDING, HEADER_HEIGHT + 30);
            return;
        }

        // Ordre de dessin important pour l'effet filigrane : d'abord les bandes
        // de fond, puis le filigrane par-dessus (semi-transparent), puis le
        // texte/les badges par-dessus le tout (sinon les bandes opaques des
        // lignes masquent complètement le filigrane).
        int y = HEADER_HEIGHT;
        for (int i = 0; i < departures.size(); i++) {
            paintRowBackground(g2, y, width, i % 2 == 0);
            y += ROW_HEIGHT;
        }

        paintWatermark(g2, width, height);

        y = HEADER_HEIGHT;
        for (Departure d : departures) {
            paintRowContent(g2, d, y, width);
            y += ROW_HEIGHT;
        }
    }

    private void paintHeader(Graphics2D g2, int width) {
        g2.setColor(HEADER_BLUE);
        g2.fillRect(0, 0, width, HEADER_HEIGHT);

        g2.setColor(WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        String title = stationName.isBlank() ? "TABLEAU DES DÉPARTS" : stationName.toUpperCase();
        g2.drawString(title, PADDING, 36);

        if (!lastUpdate.isBlank()) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(new Color(200, 210, 230));
            String maj = "Mis à jour à " + lastUpdate;
            int textWidth = g2.getFontMetrics().stringWidth(maj);
            g2.drawString(maj, width - textWidth - PADDING, 34);
        }
    }

    // Ancre le filigrane près du haut du panneau (pas en bas) pour qu'il soit
    // entièrement visible sans avoir à faire défiler, même quand le tableau
    // contient beaucoup de lignes (panneau plus grand que la fenêtre visible).
    private static final int WATERMARK_FONT_SIZE = 64;
    private static final int WATERMARK_BOTTOM_ANCHOR = HEADER_HEIGHT + 320;

    private void paintWatermark(Graphics2D g2, int width, int height) {
        g2.setColor(WATERMARK);
        g2.setFont(new Font("SansSerif", Font.BOLD, WATERMARK_FONT_SIZE));
        int anchorY = Math.min(height - 12, WATERMARK_BOTTOM_ANCHOR);
        AffineTransform old = g2.getTransform();
        g2.translate(width - 44, anchorY);
        g2.rotate(-Math.PI / 2);
        g2.drawString("départs", 0, 0);
        g2.setTransform(old);
    }

    private void paintRowBackground(Graphics2D g2, int y, int width, boolean evenRow) {
        g2.setColor(evenRow ? BLUE_LIGHT : BLUE_DARK);
        g2.fillRect(0, y, width, ROW_HEIGHT);
    }

    private void paintRowContent(Graphics2D g2, Departure d, int y, int width) {
        Color statusColor = d.supprime() ? CANCELLED_RED : d.retarde() ? LATE_ORANGE : new Color(210, 220, 235);

        int x = PADDING;

        // Type de train (badge simple, sans logo) + statut en dessous
        String modeAbbrev = firstWord(d.train());
        g2.setColor(new Color(255, 255, 255, 235));
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString(modeAbbrev, x, y + 18);
        g2.setColor(statusColor);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.drawString(d.statut(), x, y + 34);

        // Heure (jaune, grand)
        x = PADDING + BADGE_COL_WIDTH;
        g2.setColor(YELLOW);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.drawString(d.heure(), x, y + 31);

        // Destination (blanc, grand, prend le reste de la largeur)
        x = PADDING + BADGE_COL_WIDTH + TIME_COL_WIDTH;
        int destinationWidth = width - x - PLATFORM_COL_WIDTH - PADDING;
        g2.setColor(WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        String destination = truncateToWidth(g2, d.destination(), destinationWidth);
        g2.drawString(destination, x, y + 30);

        // Quai (badge à droite, uniquement si connu)
        if (!"--".equals(d.quai())) {
            int badgeX = width - PLATFORM_COL_WIDTH - PADDING / 2;
            int badgeSize = 34;
            int badgeY = y + (ROW_HEIGHT - badgeSize) / 2;
            g2.setColor(new Color(255, 255, 255, 235));
            g2.fillRoundRect(badgeX, badgeY, badgeSize, badgeSize, 8, 8);
            g2.setColor(BLUE_DARK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            String quai = d.quai();
            int qw = g2.getFontMetrics().stringWidth(quai);
            g2.drawString(quai, badgeX + (badgeSize - qw) / 2, badgeY + badgeSize - 11);
        }

        // Séparateur discret entre les lignes
        g2.setColor(new Color(0, 0, 0, 40));
        g2.setStroke(new BasicStroke(1));
        g2.drawLine(0, y + ROW_HEIGHT - 1, width, y + ROW_HEIGHT - 1);
    }

    private static String firstWord(String train) {
        int space = train.indexOf(' ');
        return space > 0 ? train.substring(0, space) : train;
    }

    private static String truncateToWidth(Graphics2D g2, String text, int maxWidth) {
        if (g2.getFontMetrics().stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 1 && g2.getFontMetrics().stringWidth(sb + ellipsis) > maxWidth) {
            sb.setLength(sb.length() - 1);
        }
        return sb + ellipsis;
    }
}
