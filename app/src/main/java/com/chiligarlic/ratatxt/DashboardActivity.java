package com.chiligarlic.ratatxt;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class DashboardActivity extends AppCompatActivity {
    private static final int SENDER_PERMISSIONS_GRANTED = 1;
    private static final String[] SENDER_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
    };
    private static final int RECEIVER_PERMISSIONS_GRANTED = 2;
    private static final String[] RECEIVER_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
    };

    private AppController appCtl;
    private Ratatxt.Device[] mDeviceArray;
    private Button mDeviceSelectorButton;
    private Switch mReceiverSwitch;
    private Switch mSenderSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // getSupportActionBar().setTitle("Dashboard");

        appCtl = AppController.getInstance();

        // Check for saved access token.
        if (!appCtl.isAuthenticated()) {
            // Proceed to LoginActivity.
            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);
            finish();
            System.out.println("Login required, starting LoginActivity.");
            return;
        }

        // Check for saved device.
        Ratatxt.Device device = appCtl.getDevice();
        // Start settings screen to set device from device list pulled from API.
        mDeviceSelectorButton = findViewById(R.id.button_device);
        if (device.getId() == null) {
            showDeviceSelectorDialog(mDeviceSelectorButton);
        } else {
            mDeviceSelectorButton.setText(device.getLabel());
        }

        // Handle SMS receiver service state.
        mReceiverSwitch = findViewById(R.id.switch_receiver);
        mReceiverSwitch.setOnCheckedChangeListener(toggleSmsReceiver());
        if (appCtl.isReceiverRunning()) {
            mReceiverSwitch.setChecked(true);
        }

        // Handle action start/stop service state.
        mSenderSwitch = findViewById(R.id.switch_sender);
        mSenderSwitch.setOnCheckedChangeListener(toggleSmsSender());
        if (appCtl.isSenderRunning()) {
            mSenderSwitch.setChecked(true);
        }

        // Set dashboard counters.
        setCounters();

        System.out.println("DashboardActivity created!");
    }

    private void setCounters() {
        SharedPreferences prefs = getSharedPreferences(AppController.SHARED_PREF_KEY,
                Context.MODE_PRIVATE);

        TextView outbox = findViewById(R.id.outbox_received);
        outbox.setText(String.valueOf(prefs.getInt(AppController.COUNT_OUTBOX, 0)));

        TextView sent = findViewById(R.id.sms_sent);
        sent.setText(String.valueOf(prefs.getInt(AppController.COUNT_SMS_SENT, 0)));

        TextView failed = findViewById(R.id.sms_failed);
        failed.setText(String.valueOf(prefs.getInt(AppController.COUNT_SMS_FAILED, 0)));

        TextView received = findViewById(R.id.sms_received);
        received.setText(String.valueOf(prefs.getInt(AppController.COUNT_SMS_RECEIVED, 0)));

        TextView inbox = findViewById(R.id.inbox_pushed);
        inbox.setText(String.valueOf(prefs.getInt(AppController.COUNT_INBOX, 0)));
    }

    public void refreshCounters(View view) {
        setCounters();
        Toast.makeText(getApplicationContext(), "Counters updated", Toast.LENGTH_SHORT).show();
    }

    public void showDeviceSelectorDialog(View view) {
        mDeviceSelectorButton.setEnabled(false);

        final AlertDialog.Builder builderSingle = new AlertDialog.Builder(
                DashboardActivity.this);

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                DashboardActivity.this, android.R.layout.select_dialog_item);

        builderSingle.setTitle("Select Device");
        builderSingle.setAdapter(arrayAdapter, (dialog, which) -> {
            Ratatxt.Device device = mDeviceArray[which];
            // Set device address
            appCtl.saveDevice(device);
            mDeviceSelectorButton.setText(device.getLabel());
        });
        // builderSingle.setCancelable(false);

         appCtl.getRatatxt().getDevices(data -> {
             System.out.println("devices " + data.toString());
             runOnUiThread(() -> {
                mDeviceArray = Ratatxt.Device.parse(data);
                for (Ratatxt.Device device: mDeviceArray) {
                    arrayAdapter.add(device.getLabel());
                }

                if (mDeviceArray.length == 0) {
                    builderSingle.setMessage("Please create a device from Console or API");
                }

                mDeviceSelectorButton.setEnabled(true);
                builderSingle.show();
            });
        }, errResp -> {
            runOnUiThread(() -> {
                mDeviceSelectorButton.setEnabled(true);
                builderSingle.setMessage(errResp.getMessage());
            });
        });
    }

    private CompoundButton.OnCheckedChangeListener toggleSmsReceiver() {
        final AppController app = AppController.getInstance();
        return (buttonView, isChecked) -> {
            if (!isDeviceReady()) {
                // Set back toggle off when not set.
                mReceiverSwitch.setChecked(false);
                return;
            }

            if (isChecked) {
                // check and request for permissions to receive SMS.
                if (!checkPermissions(RECEIVER_PERMISSIONS)) {
                    ActivityCompat.requestPermissions(DashboardActivity.this,
                            RECEIVER_PERMISSIONS, RECEIVER_PERMISSIONS_GRANTED);
                    mReceiverSwitch.setChecked(false);
                    return;
                }

                app.startSmsReceiver();
            } else {
                app.stopSmsReceiver();
            }

            shouldDeviceSelectorEnabled();
        };
    }

    private CompoundButton.OnCheckedChangeListener toggleSmsSender() {
        final AppController app = AppController.getInstance();
        return (buttonView, isChecked) -> {
            if (!isDeviceReady()) {
                // Set back toggle off when not set.
                mSenderSwitch.setChecked(false);
                return;
            }

            if (isChecked) {
                // check and request for permissions to send SMS.
                if (!checkPermissions(SENDER_PERMISSIONS)) {
                    ActivityCompat.requestPermissions(DashboardActivity.this,
                            SENDER_PERMISSIONS, SENDER_PERMISSIONS_GRANTED);
                    mSenderSwitch.setChecked(false);
                    return;
                }

                // Permission has already been granted.
                app.startSmsSender();
            } else {
                app.stopSmsSender();
            }

            shouldDeviceSelectorEnabled();
        };
    }

    private boolean checkPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(DashboardActivity.this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    private Boolean isDeviceReady() {
        AppController app = AppController.getInstance();

        // Show Device selector when its null.
        if (app.getDevice().getId() == null) {
            showDeviceSelectorDialog(mDeviceSelectorButton);
            mDeviceSelectorButton.setEnabled(true);
            return false;
        }

        return true;
    }

    private void shouldDeviceSelectorEnabled() {
        AppController app = AppController.getInstance();

        // Unlock device selector when Sms receiver and sender is not running.
        boolean isLock = app.isReceiverRunning() || app.isSenderRunning();
        mDeviceSelectorButton.setEnabled(!isLock);
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("DashboardActivity resumed!");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // AppController.getInstance().stopSmsSender();
        System.out.println("DashboardActivity stopped!");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        // noinspection SimplifiableIfStatement
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_settings:
                intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_logs:
                intent = new Intent(getApplicationContext(), LogsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_logout:
                AppController.getInstance().shutdown();
                intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
