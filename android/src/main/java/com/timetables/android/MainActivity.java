package com.timetables.android;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.timetables.apisncf.BoardType;

/**
 * First screen of the Android app. For now it only proves that this module can use the
 * pure-Java core module: it shows the board titles defined in core.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView hello = findViewById(R.id.hello);
        hello.setText(BoardType.DEPARTURES.title() + "\n" + BoardType.ARRIVALS.title());
    }
}
