package com.chiligarlic.ratatxt;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AppController extends Application {
    public static final String CHANNEL_ID = "com.chiligarlic.ratatxt.CHANNEL";

    public static final String SHARED_PREF_KEY = "com.chiligarlic.ratatxt.SHARED_PREF_KEY";
    public static final String API_URL = "com.chiligarlic.ratatxt.API_URL";
    public static final String API_USER = "com.chiligarlic.ratatxt.API_USERNAME";
    public static final String APPKEY_ID = "com.chiligarlic.ratatxt.APPKEY_ID";
    public static final String APPKEY_USER_ID = "com.chiligarlic.ratatxt.APPKEY_USER_ID";
    public static final String APPKEY_TOKEN = "com.chiligarlic.ratatxt.APPKEY_TOKEN";
    public static final String DEVICE_ID = "com.chiligarlic.ratatxt.DEVICE_ID";
    public static final String DEVICE_NAME = "com.chiligarlic.ratatxt.DEVICE_LABEL";
    public static final String DEVICE_ADDRESS = "com.chiligarlic.ratatxt.DEVICE_ADDRESS";
    public static final String SENDER_RUNNING = "com.chiligarlic.ratatxt.SENDER_RUNNING";
    public static final String RECEIVER_RUNNING = "com.chiligarlic.ratatxt.RECEIVER_RUNNING";
    public static final String HISTORY = "com.chiligarlic.ratatxt.HISTORY";

    private static AppController instance;
    private SharedPreferences prefs;
    private Ratatxt ratatxt;

    @Override
    public void onCreate() {
        super.onCreate();

        // Check for local storage values.
        prefs = getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);

        // Set saved Auth and Device address.
        ratatxt = Ratatxt.getInstance();
        ratatxt.setApiHost(prefs.getString(API_URL, null));
        ratatxt.setAppKey(getAppKey());
        ratatxt.setDevice(getDevice());

        createNotificationChannel();

        // TODO! app automatically receives messages to send without hitting start button.

        instance = this;
        System.out.println("AppController created!");
    }

    /**
     * Get AppController API instance.
     *
     * @return returns an AppController instance.
     */
    public static AppController getInstance() {
        return instance;
    }

    /**
     * Get Ratatxt API instance.
     *
     * @return returns a Ratatxt instance.
     */
    public Ratatxt getRatatxt() {
        return ratatxt;
    }

    /**
     * Save Auth data on local prefs and set on Ratatxt.
     *
     * @param url API url value.
     */
    public void saveApiSettings(String url, String username) {
        ratatxt.setApiHost(url);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(API_URL, url);
        editor.putString(API_USER, username);
        editor.apply();
    }

    public String getApiUrl() {
        return prefs.getString(API_URL, null);
    }

    public String getApiUser() {
        return prefs.getString(API_USER, null);
    }

    public String getBrokerUrl() {
        String host = getApiUrl().split(":")[1];
        return String.format("tcp:%s:1883", host);
    }

    /**
     * Save Auth data on local prefs and set on Ratatxt.
     *
     * @param appKey new AppKey value.
     */
    public void saveAppKey(Ratatxt.AppKey appKey) {
        ratatxt.setAppKey(appKey);

        // Save on local prefs
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(APPKEY_ID, appKey.getId());
        editor.putString(APPKEY_USER_ID, appKey.getUserId());
        editor.putString(APPKEY_TOKEN, appKey.getToken());
        editor.apply();
    }

    /**
     * Get saved Auth value from local prefs.
     *
     * @return returns Auth.
     */
    public Ratatxt.AppKey getAppKey() {
        Ratatxt.AppKey appKey = new Ratatxt.AppKey();
        appKey.setId(prefs.getString(APPKEY_ID, null));
        appKey.setUserId(prefs.getString(APPKEY_USER_ID, null));
        appKey.setToken(prefs.getString(APPKEY_TOKEN, null));
        return appKey;
    }

    /**
     * Save Device value on local prefs and set on Ratatxt.
     *
     * @param device new Device value.
     */
    public void saveDevice(Ratatxt.Device device) {
        // Set device address on Ratatxt
        ratatxt.setDevice(device);

        // Save on local prefs
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DEVICE_ID, device.getId());
        editor.putString(DEVICE_NAME, device.getName());
        editor.putString(DEVICE_ADDRESS, device.getAddress());
        editor.apply();
    }

    /**
     * Get saved Device address from local prefs.
     *
     * @return returns Device address.
     */
    public Ratatxt.Device getDevice() {
        Ratatxt.Device device = new Ratatxt.Device();
        device.setId(prefs.getString(DEVICE_ID, null));
        device.setName(prefs.getString(DEVICE_NAME, null));
        device.setAddress(prefs.getString(DEVICE_ADDRESS, null));
        return device;
    }

    /**
     * Check available Auth value store on local prefs.
     *
     * @return returns true if present else false.
     */
    public boolean isAuthenticated() {
        Ratatxt.AppKey appKey = getAppKey();
        return !(appKey.getToken() == null || appKey.getToken().isEmpty());
    }

    private void saveSenderState(boolean isRunning) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SENDER_RUNNING, isRunning);
        editor.apply();
    }

    public boolean isSenderRunning() {
        return prefs.getBoolean(SENDER_RUNNING, false);
    }

    public void startSmsSender() {
        if (isSenderRunning()) {
            System.out.println("SMS sender already running");
            return;
        }

        addToHistory("Start SMS sender");
        // TODO! check set device
        sendPendingOutbox();
        startMQTTReceiver();
        saveSenderState(true);
    }

    public void stopSmsSender() {
        if (!isSenderRunning()) {
            System.out.println("SMS sender is not running");
            return;
        }

        addToHistory("Stop SMS sender");
        stopMQTTReceiver();
        saveSenderState(false);
    }

    private void saveReceiverState(boolean isRunning) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(RECEIVER_RUNNING, isRunning);
        editor.apply();
    }

    public boolean isReceiverRunning() {
        return prefs.getBoolean(RECEIVER_RUNNING, false);
    }

    public void startSmsReceiver() {
        if (isReceiverRunning()) {
            System.out.println("SMS receiver is already running");
            return;
        }

        addToHistory("Start SMS receiver");
        saveReceiverState(true);
    }

    public void stopSmsReceiver() {
        if (!isReceiverRunning()) {
            System.out.println("SMS receiver is not running");
            return;
        }

        addToHistory("Stop SMS receiver");
        saveReceiverState(false);
    }

    public void shutdown() {
        stopSmsSender();
        stopSmsReceiver();
    }

    public void clearAuth() {
        ratatxt.deleteAppToken(getAppKey().getId(), data -> {
            addToHistory("shutdown: appkey token deleted");
            clearData();
        }, errResp -> {
            addToHistory("shutdown: appkey token deletion failed");
            clearData();
        });
    }

    private void clearData() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(APPKEY_ID);
        editor.remove(APPKEY_USER_ID);
        editor.remove(APPKEY_TOKEN);
        editor.remove(DEVICE_ID);
        editor.remove(DEVICE_NAME);
        editor.remove(DEVICE_ADDRESS);
        editor.apply();
    }

    private void startMQTTReceiver() {
        String topic = getRatatxt().getDeviceTopic();

        Intent serviceIntent = new Intent(getApplicationContext(), AppService.class);
        serviceIntent.putExtra("topic", topic);
        ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);

        addToHistory("MQTT subscribe to topic " + topic);
    }

    private void stopMQTTReceiver() {
        String topic = getRatatxt().getDeviceTopic();

        Intent serviceIntent = new Intent(getApplicationContext(), AppService.class);
        stopService(serviceIntent);

        addToHistory("MQTT un-subscribe to topic " + topic);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Rtx Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public void addToHistory(String text) {
        Log.d("Ratatxt", text);

        Set<String> textSet = prefs.getStringSet(AppController.HISTORY, new HashSet<>());
        if (textSet == null) {
            return;
        }

        String ts = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        textSet.add(ts+ ": " + text);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(AppController.HISTORY, textSet);
        editor.apply();
    }

    public void clearHistory() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(AppController.HISTORY, new HashSet<>());
        editor.apply();
    }

    public static final String COUNT_OUTBOX = "com.chiligarlic.ratatxt.COUNTER_OUTBOX";
    public static final String COUNT_SMS_SENT = "com.chiligarlic.ratatxt.COUNT_SMS_SENT";
    public static final String COUNT_SMS_FAILED = "com.chiligarlic.ratatxt.COUNT_SMS_FAILED";
    public static final String COUNT_INBOX = "com.chiligarlic.ratatxt.COUNT_INBOX";
    public static final String COUNT_SMS_RECEIVED = "com.chiligarlic.ratatxt.COUNT_SMS_RECEIVED";

    public void incrementCounter(String key) {
        int count = prefs.getInt(key, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(key, ++count);
        editor.apply();
    }

    public void clearCounters() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(COUNT_OUTBOX, 0);
        editor.putInt(COUNT_SMS_SENT, 0);
        editor.putInt(COUNT_SMS_FAILED, 0);
        editor.putInt(COUNT_INBOX, 0);
        editor.putInt(COUNT_SMS_RECEIVED, 0);
        editor.apply();
    }

    private void sendPendingOutbox() {
//        ArrayList<Ratatxt.Message> messages = getRatatxt().pullOutbox();
//        System.out.printf("Sending %d pending outbox\n", messages.size());
//        for (Ratatxt.Message msg : messages) {
//            SmsSender.send(msg);
//        }
    }
}
