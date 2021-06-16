package com.chiligarlic.ratatxt;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        // Action set device address using select box
        // Action set default sms app (for receiving sms)
        // Action logout

        // Display api version
        // Display api URL
        // Display user info
        System.out.println("SettingsActivity created!");
    }
}
