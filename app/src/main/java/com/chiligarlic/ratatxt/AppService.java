package com.chiligarlic.ratatxt;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AppService extends Service {
    private final int QOS = 2;

    private AppController appCtl;
    private MqttAsyncClient mqttClient;
    private String topic;

    @Override
    public void onCreate() {
        super.onCreate();

        appCtl = AppController.getInstance();
        topic = appCtl.getRatatxt().getDeviceTopic();
        String broker = appCtl.getBrokerUrl();
        String clientId = appCtl.getDevice().getId();

        appCtl.addToHistory("MQTT Connecting to broker: "+ broker + " - " + clientId);

        try {
            MqttAsyncClient client = getMqttClient(broker, clientId);
            connectAndSub(client, topic);
            mqttClient = client;
        } catch (MqttException e) {
            e.printStackTrace();
        }

        appCtl.addToHistory("MQTT Service created!");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        appCtl.addToHistory("MQTT Service onStartCommand");

        // Notification setup.
        Intent notificationIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, AppController.CHANNEL_ID)
                .setContentTitle("Ratatxt running")
                .setContentText("Waiting to send and receive SMS")
                .setSmallIcon(R.drawable.ic_rat)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        //do heavy work on a background thread
        //stopSelf();
        return START_STICKY;
    }

    private MqttAsyncClient getMqttClient(String serverURI, String clientId) throws MqttException {
        // MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence();
        MemoryPersistence persistence = new MemoryPersistence();

        MqttAsyncClient client = new MqttAsyncClient(serverURI, clientId, persistence);
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    appCtl.addToHistory("MQTT Reconnected to : " + serverURI);
                    // No need to re-subscribe because Clean Session is false.
                } else {
                    appCtl.addToHistory("MQTT Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                appCtl.addToHistory("MQTT connectionLost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                Ratatxt.Message rMessage = Ratatxt.Message.parse(new JSONObject(payload));
                appCtl.addToHistory("MQTT Message arrived: " + rMessage.getId());
                appCtl.incrementCounter(AppController.COUNT_OUTBOX);
                SmsSender.send(rMessage);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                appCtl.addToHistory("MQTT deliveryComplete " + token.isComplete());
            }
        });


        return client;
    }

    private MqttConnectOptions getMqttConnectionOption() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setAutomaticReconnect(true);
        //mqttConnectOptions.setKeepAliveInterval(30);
        //mqttConnectOptions.setWill(TOPIC, "I am going offline".getBytes(), 1, true);
        //mqttConnectOptions.setUserName("username");
        //mqttConnectOptions.setPassword("password".toCharArray());
        return mqttConnectOptions;
    }

    private DisconnectedBufferOptions getDisconnectedBufferOptions() {
        DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
        disconnectedBufferOptions.setBufferEnabled(true);
        disconnectedBufferOptions.setBufferSize(100);
        disconnectedBufferOptions.setPersistBuffer(true);
        disconnectedBufferOptions.setDeleteOldestMessages(false);
        return disconnectedBufferOptions;
    }

    private void connectAndSub(MqttAsyncClient client, String topic) throws MqttException {
        appCtl.addToHistory("MQTT connect and sub");
        if (client == null) {
            appCtl.addToHistory("MQTT client is null could not connect");
            return;
        }

        client.connect(getMqttConnectionOption());
        client.setBufferOpts(getDisconnectedBufferOptions());
        appCtl.addToHistory("MQTT connected!");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        client.subscribe(topic, QOS);
        appCtl.addToHistory("MQTT subscribed topic:" + topic + " qos:" + QOS);
    }

    private void disconnectAndUnSub(MqttAsyncClient client, String topic) throws MqttException {
        appCtl.addToHistory("MQTT disconnect and unsub");
        if (client == null) {
            appCtl.addToHistory("MQTT client is null could not disconnect");
            return;
        }

        client.unsubscribe(topic);
        appCtl.addToHistory("MQTT unsubscribe topic:" + topic);

        client.disconnect();
        appCtl.addToHistory("MQTT disconnected!");
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            disconnectAndUnSub(mqttClient, topic);
        } catch (MqttException e) {
            appCtl.addToHistory("MQTT disconnection failed!");
            e.printStackTrace();
        }
        appCtl.addToHistory("MQTT disconnect success!");

        appCtl.addToHistory("MQTT Service destroyed!");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
