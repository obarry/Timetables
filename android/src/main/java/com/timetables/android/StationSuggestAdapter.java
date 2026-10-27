package com.timetables.android;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import com.timetables.apisncf.Station;

/**
 * Suggestions shown under the station field.
 *
 * The filtering is NOT done here: the list comes already filtered and ranked by the SNCF API
 * (APISNCF.searchStations). The Filter below only tells the AutoCompleteTextView to show
 * whatever list we currently hold, instead of filtering it again by prefix.
 */
final class StationSuggestAdapter extends BaseAdapter implements Filterable {

    private final LayoutInflater inflater;
    private volatile List<Station> stations = Collections.emptyList();

    StationSuggestAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    /** Replaces the suggestions (UI thread). */
    void submit(List<Station> newStations) {
        stations = newStations;
        if (newStations.isEmpty()) {
            notifyDataSetInvalidated();
        } else {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return stations.size();
    }

    @Override
    public Station getItem(int position) {
        return stations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) convertView;
        if (view == null) {
            view = (TextView) inflater.inflate(R.layout.item_station_suggestion, parent, false);
        }
        view.setText(getItem(position).name());
        return view;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<Station> current = stations;
                FilterResults results = new FilterResults();
                results.values = current;
                results.count = current.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return ((Station) resultValue).name();
            }
        };
    }
}
