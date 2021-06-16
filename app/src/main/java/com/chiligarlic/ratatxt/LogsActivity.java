package com.chiligarlic.ratatxt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class LogsActivity extends AppCompatActivity {
    ListView simpleList;
    ArrayAdapter<String> arrayAdapter;
    ArrayList<String> logList = new ArrayList<>();
    SharedPreferences prefs;

    Set<String> textSet = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console);

        simpleList = findViewById(R.id.list_view);
        arrayAdapter = new ArrayAdapter<>(this, R.layout.listview, R.id.list_item, logList);
        simpleList.setAdapter(arrayAdapter);

        // Check for local storage values.
        prefs = getSharedPreferences(AppController.SHARED_PREF_KEY, Context.MODE_PRIVATE);

        textSet = prefs.getStringSet(AppController.HISTORY, new HashSet<>());
        if (textSet != null) {
            TreeSet<String> treeSet = new TreeSet<>(textSet);

            for(String str : treeSet.descendingSet()) {
                addToHistory(str);
            }
        }
    }

    public void addToHistory(String text){
        System.out.println("LOG: " + text);

        logList.add(text);
        arrayAdapter.notifyDataSetChanged();
    }

    public void clearLogs(View view) {
        logList.clear();
        arrayAdapter.notifyDataSetChanged();
        AppController.getInstance().clearHistory();
    }
}
