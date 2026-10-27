package com.timetables.android;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.timetables.apisncf.APISNCF;
import com.timetables.apisncf.BoardEntry;
import com.timetables.apisncf.BoardType;
import com.timetables.apisncf.OfficialPlatformProvider;
import com.timetables.apisncf.PlatformProvider;
import com.timetables.apisncf.Station;
import com.timetables.apisncf.StationNotFoundException;
import com.timetables.theme.BoardTheme;

/**
 * Departures / arrivals board of a station. The data comes from the core module
 * (APISNCF), the look from BoardTheme; this class only wires them to the screen.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Timetables";
    private static final long REFRESH_INTERVAL_MS = 30_000;
    private static final int MAX_TRAINS = 100;
    // Autocompletion: wait for a pause in the typing before asking the API.
    private static final long SEARCH_DELAY_MS = 300;
    private static final int MAX_SUGGESTIONS = 8;
    private static final String STATE_STATION = "station";
    private static final String STATE_STATION_ID = "stationId";
    private static final String STATE_TYPE = "type";

    // Network calls run on one background thread, never on the UI thread.
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Incremented at each request: an answer that is no longer the latest is ignored,
    // so a slow response never overwrites a newer one (same idea as the Swing version).
    private final AtomicInteger requestSeq = new AtomicInteger();
    // Same idea for the suggestions, on their own thread so they never wait for a board.
    private final ExecutorService suggestionNetwork = Executors.newSingleThreadExecutor();
    private final AtomicInteger suggestionSeq = new AtomicInteger();
    private final PlatformProvider platforms = new OfficialPlatformProvider();
    private final BoardAdapter adapter = new BoardAdapter();
    private APISNCF api;

    private BoardType type = BoardType.DEPARTURES;
    // The board being shown: its name, and its id when it was picked from the suggestions
    // (null when the user typed a name and pressed "show": it is then searched by name).
    private String station = "";
    private Station shownStation;

    // What the station field currently holds, when it comes from a suggestion.
    private Station choice;
    private StationSuggestAdapter suggestions;

    private View root;
    private View headerBand;
    private TextView titleView;
    private TextView updatedView;
    private TextView messageView;
    private AutoCompleteTextView stationInput;
    private Button departuresButton;
    private Button arrivalsButton;

    private final Runnable searchSuggestions = this::searchSuggestions;

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        headerBand = findViewById(R.id.headerBand);
        titleView = findViewById(R.id.titleView);
        updatedView = findViewById(R.id.updatedView);
        messageView = findViewById(R.id.messageView);
        stationInput = findViewById(R.id.stationInput);
        departuresButton = findViewById(R.id.departuresButton);
        arrivalsButton = findViewById(R.id.arrivalsButton);
        Button showButton = findViewById(R.id.showButton);
        RecyclerView list = findViewById(R.id.list);

        // The app draws under the system bars (Android 15+): keep the content clear of them.
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        // Light icons in the status bar: the board is dark.
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        // The key comes from local.properties or resources/Timetables.properties (see build.gradle.kts).
        api = new APISNCF(BuildConfig.SNCF_API_KEY, new AndroidHttpTransport(),
                message -> Log.d(TAG, message));

        showButton.setOnClickListener(v -> show());
        stationInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                show();
                return true;
            }
            return false;
        });
        // Suggestions: ranked by the API, shown on a dark background like the board.
        suggestions = new StationSuggestAdapter(this);
        stationInput.setAdapter(suggestions);
        stationInput.setDropDownBackgroundDrawable(new ColorDrawable(0xFF060F28));
        stationInput.setOnItemClickListener((parent, view, position, id) -> {
            choice = suggestions.getItem(position);
            show();
        });
        departuresButton.setOnClickListener(v -> setType(BoardType.DEPARTURES));
        arrivalsButton.setOnClickListener(v -> setType(BoardType.ARRIVALS));

        if (savedInstanceState != null) {
            station = savedInstanceState.getString(STATE_STATION, "");
            String savedType = savedInstanceState.getString(STATE_TYPE, BoardType.DEPARTURES.name());
            type = BoardType.valueOf(savedType);
            String savedId = savedInstanceState.getString(STATE_STATION_ID);
            if (savedId != null && !station.isEmpty()) {
                shownStation = new Station(savedId, station);
                choice = shownStation;
            }
            stationInput.setText(station, false);
        }
        applyType();

        // Added last, so that restoring the text above does not start a search.
        stationInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                onStationTextChanged(text.toString().trim());
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_STATION, station);
        outState.putString(STATE_STATION_ID, shownStation == null ? null : shownStation.id());
        outState.putString(STATE_TYPE, type.name());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!station.isEmpty()) {
            refresh(true);
            scheduleRefresh();
        }
    }

    @Override
    protected void onStop() {
        mainHandler.removeCallbacks(autoRefresh);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        network.shutdownNow();
        suggestionNetwork.shutdownNow();
        super.onDestroy();
    }

    /** "Afficher" button: takes the typed station and starts showing its board. */
    private void show() {
        String typed = stationInput.getText().toString().trim();
        if (typed.isEmpty()) {
            showMessage(getString(R.string.prompt));
            return;
        }
        // A station picked in the list is used as is; typed text is searched by name.
        if (choice != null && !choice.name().equals(typed)) {
            choice = null;
        }
        shownStation = choice;
        station = typed;
        cancelSuggestions();
        hideKeyboard();
        applyType();
        refresh(true);
        scheduleRefresh();
    }

    /** Departures / arrivals buttons. */
    private void setType(BoardType newType) {
        type = newType;
        requestSeq.incrementAndGet(); // drop any answer still in flight for the other board
        applyType();
        if (station.isEmpty()) {
            return;
        }
        refresh(true);
        scheduleRefresh();
    }

    /** Colors, title and buttons for the current board; clears the rows. */
    private void applyType() {
        BoardTheme theme = BoardTheme.of(type);
        root.setBackgroundColor(theme.rowDark());
        headerBand.setBackgroundColor(theme.header());
        titleView.setText(station.isEmpty() ? type.title() : boardTitle(type, station));
        updatedView.setText("");
        departuresButton.setAlpha(type == BoardType.DEPARTURES ? 1f : 0.55f);
        arrivalsButton.setAlpha(type == BoardType.ARRIVALS ? 1f : 0.55f);
        adapter.submit(Collections.<BoardEntry>emptyList(), theme);
        if (station.isEmpty()) {
            showMessage(getString(R.string.prompt));
        } else {
            messageView.setVisibility(View.GONE);
        }
    }

    private void scheduleRefresh() {
        mainHandler.removeCallbacks(autoRefresh);
        mainHandler.postDelayed(autoRefresh, REFRESH_INTERVAL_MS);
    }

    /**
     * Fetches the board on the background thread, then shows the result on the UI thread.
     *
     * @param userInitiated true when the user asked for it: the rows are cleared and a
     *                      loading message is shown. An automatic refresh keeps the rows on screen.
     */
    private void refresh(boolean userInitiated) {
        final int seq = requestSeq.incrementAndGet();
        final BoardType requestedType = type;
        final String requestedStation = station;
        final Station requestedChoice = shownStation;

        if (userInitiated) {
            adapter.submit(Collections.<BoardEntry>emptyList(), BoardTheme.of(requestedType));
            updatedView.setText("");
            showMessage(getString(R.string.loading));
        }

        network.execute(() -> {
            try {
                List<BoardEntry> rows = requestedChoice != null
                        ? api.fetchBoard(requestedType, requestedChoice, MAX_TRAINS, platforms)
                        : api.fetchBoard(requestedType, requestedStation, MAX_TRAINS, platforms);
                runIfCurrent(seq, () -> showRows(requestedType, requestedStation, rows));
            } catch (StationNotFoundException e) {
                runIfCurrent(seq, () -> showMessage(e.getMessage()));
            } catch (Exception e) {
                Log.w(TAG, "Board request failed", e);
                runIfCurrent(seq, () -> showMessage(getString(R.string.error_fetch, e.getMessage())));
            }
        });
    }

    /** Runs an action on the UI thread, unless a newer request has been made since. */
    private void runIfCurrent(int seq, Runnable action) {
        runOnUiThread(() -> {
            if (seq == requestSeq.get() && !isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    private void showRows(BoardType shownType, String shownStation, List<BoardEntry> rows) {
        titleView.setText(boardTitle(shownType, shownStation));
        updatedView.setText(getString(R.string.updated_at,
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
        adapter.submit(rows, BoardTheme.of(shownType));
        if (rows.isEmpty()) {
            showMessage(getString(R.string.no_train));
        } else {
            messageView.setVisibility(View.GONE);
        }
    }

    private void showMessage(String text) {
        messageView.setText(text);
        messageView.setVisibility(View.VISIBLE);
    }

    private static String boardTitle(BoardType boardType, String stationName) {
        return stationName.toUpperCase(Locale.FRANCE) + " — " + boardType.label().toUpperCase(Locale.FRANCE);
    }

    /** Called at each change of the station field. */
    private void onStationTextChanged(String typed) {
        if (choice != null && choice.name().equals(typed)) {
            return; // the text was just set from a suggestion: nothing to search
        }
        choice = null;
        // Keep the current list on screen while typing; only drop searches now out of date.
        mainHandler.removeCallbacks(searchSuggestions);
        suggestionSeq.incrementAndGet();
        if (typed.length() >= APISNCF.MIN_QUERY_LENGTH) {
            mainHandler.postDelayed(searchSuggestions, SEARCH_DELAY_MS);
        } else {
            cancelSuggestions();
        }
    }

    /** Forgets the pending and in-flight searches and hides the list. */
    private void cancelSuggestions() {
        mainHandler.removeCallbacks(searchSuggestions);
        suggestionSeq.incrementAndGet();
        suggestions.submit(Collections.<Station>emptyList());
        stationInput.dismissDropDown();
    }

    /** Asks the API for the stations matching the text typed so far (background thread). */
    private void searchSuggestions() {
        final String typed = stationInput.getText().toString().trim();
        final int seq = suggestionSeq.incrementAndGet();
        suggestionNetwork.execute(() -> {
            try {
                List<Station> found = api.searchStations(typed, MAX_SUGGESTIONS);
                runOnUiThread(() -> {
                    if (seq == suggestionSeq.get() && !isFinishing() && !isDestroyed()) {
                        showSuggestions(found);
                    }
                });
            } catch (Exception e) {
                // Not worth a message: the user can still type the full name and press "show".
                Log.d(TAG, "Station search failed: " + e.getMessage());
            }
        });
    }

    private void showSuggestions(List<Station> found) {
        suggestions.submit(found);
        if (found.isEmpty()) {
            stationInput.dismissDropDown();
        } else if (stationInput.hasFocus()) {
            stationInput.showDropDown();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(stationInput.getWindowToken(), 0);
        }
    }
}
