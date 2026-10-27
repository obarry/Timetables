package com.timetables.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.Station;

/**
 * Autocompletion for a station text field: while the user types, a list of matching
 * stations (ranked by the SNCF API, see APISNCF.searchStations) drops down under the field.
 *
 * Everything here runs on the Swing event thread, except the API call itself, which runs
 * in a SwingWorker so that the window never freezes.
 */
public final class StationAutocomplete {

    private static final int DELAY_MS = 300;   // pause in the typing before asking the API
    private static final int MAX_SUGGESTIONS = 8;

    private final JTextField field;
    private final APISNCF api;
    private final DefaultListModel<Station> model = new DefaultListModel<>();
    private final JList<Station> list = new JList<>(model);
    private final JPopupMenu popup = new JPopupMenu();
    private final Timer debounce;

    // Incremented at each keystroke and each search: an answer that is no longer the latest
    // is ignored (same idea as the board requests).
    private int searchSeq;
    private boolean settingText;
    private Station picked;
    private Runnable onPicked = () -> { };

    public StationAutocomplete(JTextField field, APISNCF api) {
        this.field = field;
        this.api = api;

        // The list is dark like the board; the popup must never take the keyboard focus,
        // otherwise the user could not keep typing.
        list.setBackground(new Color(6, 15, 40));
        list.setForeground(Color.WHITE);
        list.setSelectionBackground(new Color(27, 74, 158));
        list.setSelectionForeground(Color.WHITE);
        list.setFont(field.getFont().deriveFont(Font.PLAIN, 14f));
        list.setFixedCellHeight(26);
        list.setFocusable(false);
        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(new Color(27, 74, 158)));
        popup.add(list);

        debounce = new Timer(DELAY_MS, e -> search());
        debounce.setRepeats(false);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                textChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                textChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                textChanged();
            }
        });

        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e);
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                hide();
            }
        });

        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                list.setSelectedIndex(list.locationToIndex(e.getPoint()));
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) {
                    pick(model.get(index));
                }
            }
        });
    }

    /** Called when the user picks a suggestion (the field already holds its name). */
    public void setOnPicked(Runnable onPicked) {
        this.onPicked = onPicked;
    }

    /**
     * The station picked from the list, as long as the field still holds its name;
     * null when the user typed something else.
     */
    public Station getPicked() {
        if (picked != null && picked.name().equals(field.getText().trim())) {
            return picked;
        }
        return null;
    }

    private void textChanged() {
        if (settingText) {
            return;
        }
        picked = null;
        searchSeq++; // any search in flight is now out of date
        if (field.getText().trim().length() >= APISNCF.MIN_QUERY_LENGTH) {
            debounce.restart();
        } else {
            debounce.stop();
            hide();
        }
    }

    private void search() {
        final String typed = field.getText().trim();
        final int seq = ++searchSeq;
        new SwingWorker<List<Station>, Void>() {
            @Override
            protected List<Station> doInBackground() throws Exception {
                return api.searchStations(typed, MAX_SUGGESTIONS);
            }

            @Override
            protected void done() {
                if (seq != searchSeq) {
                    return;
                }
                try {
                    show(get());
                } catch (Exception e) {
                    // Not worth a message: the user can still type the full name and press Enter.
                    hide();
                }
            }
        }.execute();
    }

    private void show(List<Station> stations) {
        model.clear();
        stations.forEach(model::addElement);
        if (stations.isEmpty() || !field.isShowing() || !field.hasFocus()) {
            hide();
            return;
        }
        list.clearSelection();
        popup.setPopupSize(field.getWidth(), list.getPreferredSize().height + 2);
        popup.show(field, 0, field.getHeight());
    }

    private void hide() {
        popup.setVisible(false);
    }

    private void pick(Station station) {
        settingText = true;
        try {
            field.setText(station.name());
        } finally {
            settingText = false;
        }
        picked = station;
        searchSeq++;
        debounce.stop();
        hide();
        onPicked.run();
    }

    private void handleKey(KeyEvent e) {
        boolean open = popup.isVisible();
        switch (e.getKeyCode()) {
            case KeyEvent.VK_DOWN -> {
                if (!open && model.getSize() > 0 && field.getText().trim().length() >= APISNCF.MIN_QUERY_LENGTH) {
                    show(java.util.Collections.list(model.elements()));
                } else if (open) {
                    list.setSelectedIndex(Math.min(list.getSelectedIndex() + 1, model.getSize() - 1));
                }
                e.consume();
            }
            case KeyEvent.VK_UP -> {
                if (open) {
                    list.setSelectedIndex(Math.max(list.getSelectedIndex() - 1, 0));
                    e.consume();
                }
            }
            case KeyEvent.VK_ESCAPE -> {
                if (open) {
                    hide();
                    e.consume();
                }
            }
            case KeyEvent.VK_ENTER -> {
                // With a row highlighted, Enter picks it; otherwise it behaves as usual
                // (the field's own action: show the board for the typed name).
                if (open && list.getSelectedIndex() >= 0) {
                    pick(model.get(list.getSelectedIndex()));
                    e.consume();
                } else {
                    hide();
                }
            }
            default -> { }
        }
    }
}
