package com.timetables.android;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import com.timetables.apisncf.BoardEntry;
import com.timetables.theme.BoardTheme;

/**
 * Shows the rows of a board (departures or arrivals) in a RecyclerView. Makes no network
 * call: it receives an already computed list of BoardEntry, like BoardPanel on the desktop.
 */
final class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.Holder> {

    private List<BoardEntry> entries = Collections.emptyList();
    private BoardTheme theme = BoardTheme.DEPARTURES;

    void submit(List<BoardEntry> newEntries, BoardTheme newTheme) {
        entries = newEntries;
        theme = newTheme;
        notifyDataSetChanged(); // the list is small (at most 15 rows)
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_board_row, parent, false);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BoardEntry entry = entries.get(position);

        // Alternating bands, like the real boards.
        holder.itemView.setBackgroundColor(position % 2 == 0 ? theme.rowLight() : theme.rowDark());

        holder.train.setText(entry.train());
        holder.status.setText(entry.statut());
        holder.time.setText(entry.heure());
        holder.station.setText(entry.otherStation());

        int statusColor = entry.supprime() ? BoardTheme.CANCELLED_RED
                : entry.retarde() ? BoardTheme.LATE_ORANGE
                : BoardTheme.WHITE;
        holder.status.setTextColor(statusColor);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView train;
        final TextView status;
        final TextView time;
        final TextView station;

        Holder(@NonNull View itemView) {
            super(itemView);
            train = itemView.findViewById(R.id.train);
            status = itemView.findViewById(R.id.status);
            time = itemView.findViewById(R.id.time);
            station = itemView.findViewById(R.id.station);
        }
    }
}
