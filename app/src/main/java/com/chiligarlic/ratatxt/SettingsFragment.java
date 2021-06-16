package com.chiligarlic.ratatxt;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppController appCtl = AppController.getInstance();

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Update preference detail
        findPreference("pref_client_ver").setSummary(BuildConfig.VERSION_NAME);
        findPreference("pref_api_host").setSummary(appCtl.getApiUrl());
        findPreference("pref_device").setSummary(appCtl.getDevice().getLabel());
        findPreference("pref_device_topic").setSummary(appCtl.getRatatxt().getDeviceTopic());

        final Preference apiVer = findPreference("pref_api_ver");
        final Preference apiBuilt = findPreference("pref_api_built");

        // Request for version info
        appCtl.getRatatxt().getVersion(data -> {
            getActivity().runOnUiThread(() -> {
                Ratatxt.Version version = Ratatxt.Version.parse(data);
                apiVer.setSummary(version.getVersion().replaceFirst("v", ""));
                apiBuilt.setSummary(version.getBuilt());
            });
        }, errResponse -> {
            getActivity().runOnUiThread(() -> {
                apiVer.setSummary("ERR " + errResponse.getMessage());
            });
        });

        findPreference("pref_clear_counters").setOnPreferenceClickListener(preference -> {
            // open browser or intent here
            appCtl.clearCounters();
            Toast.makeText(appCtl.getBaseContext(), "Counters cleared", Toast.LENGTH_SHORT).show();
            return true;
        });
    }
}