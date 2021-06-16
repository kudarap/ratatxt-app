package com.chiligarlic.ratatxt;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsManager;

import java.util.ArrayList;

public class SmsSender {
    /**
     * Send SMS using default sim and update its state using API.
     *
     * @param message Message object.
     */
    public static void send(final Ratatxt.Message message) {
        final String messageId = message.getId();
        AppController appCtl = AppController.getInstance();
        Ratatxt ratatxt = appCtl.getRatatxt();

        System.out.printf("SmsSender sending %s\n", messageId);
        ratatxt.updateOutboxStatus(messageId, Ratatxt.MESSAGE_STATUS_OUTBOX_SENDING, data -> {
            System.out.println("outbox [SENDING] update success! " + Ratatxt.Message.parse(data).getId());
        }, errResp -> {
            System.out.println("outbox [SENDING] update s failed! " + errResp.getMessage());
        });

        String SENT = "SMS_SENT";
        String DELIVERED = "SMS_DELIVERED";
        Context context = appCtl.getApplicationContext();

        // Register for SMS send action
        PendingIntent sentPI = PendingIntent.getBroadcast(
                context, 0, new Intent(SENT), PendingIntent.FLAG_ONE_SHOT);

        // Register for Delivery event
        PendingIntent deliveryPI = PendingIntent.getBroadcast(
                context, 0, new Intent(DELIVERED), PendingIntent.FLAG_ONE_SHOT);

        // register callbacks receiver base in event
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = 0;
                String result = "Sending";

                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        status = Ratatxt.MESSAGE_STATUS_OUTBOX_SENT;
                        result = "Transmission successful";
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        status = Ratatxt.MESSAGE_STATUS_OUTBOX_FAILED;
                        result = "Transmission failed";
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        status = Ratatxt.MESSAGE_STATUS_OUTBOX_FAILED;
                        result = "Radio off";
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        status = Ratatxt.MESSAGE_STATUS_OUTBOX_FAILED;
                        result = "No PDU defined";
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        status = Ratatxt.MESSAGE_STATUS_OUTBOX_FAILED;
                        result = "No service";
                        break;
                }

                if (status == Ratatxt.MESSAGE_STATUS_OUTBOX_SENT) {
                    appCtl.incrementCounter(AppController.COUNT_SMS_SENT);
                } else {
                    appCtl.incrementCounter(AppController.COUNT_SMS_FAILED);
                }

                ratatxt.updateOutboxStatus(messageId, status, data -> {
                    System.out.println("outbox [SENT] update success! " + Ratatxt.Message.parse(data).getId());
                }, errResp -> {
                    System.out.println("outbox [SENT] update s failed! " + errResp.getMessage());
                });

                System.out.printf("SmsSender[SENT] %s status:%d [%s]\n", messageId, status, result);
                context.unregisterReceiver(this);
            }
        }, new IntentFilter(SENT));

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = 0;
                String result = "";

                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        status = Ratatxt.MESSAGE_STATUS_OUTBOX_SENT;
                        result = "SMS delivered";
                        break;
                    case Activity.RESULT_CANCELED:
                        status = Ratatxt.MESSAGE_STATUS_OUTBOX_FAILED;
                        result = "SMS not delivered";
                        break;
                }

                if (status == Ratatxt.MESSAGE_STATUS_OUTBOX_SENT) {
                    appCtl.incrementCounter(AppController.COUNT_SMS_SENT);
                } else {
                    appCtl.incrementCounter(AppController.COUNT_SMS_FAILED);
                }

                ratatxt.updateOutboxStatus(messageId, status, data -> {
                    System.out.println("outbox [DELIVERED] update success! " + Ratatxt.Message.parse(data).getId());
                }, errResp -> {
                    System.out.println("outbox [DELIVERED] update s failed! " + errResp.getMessage());
                });

                System.out.printf("SmsSender[DELIVERED] %s %s[%s]\n", messageId, status, result);
                context.unregisterReceiver(this);
            }
        }, new IntentFilter(DELIVERED));

        SmsManager sms = SmsManager.getDefault();
        // Supports long message.
        ArrayList<String> parts = sms.divideMessage(message.getText());
        ArrayList<PendingIntent> sentIntents = new ArrayList<>();
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            sentIntents.add(sentPI);
            deliveryIntents.add(deliveryPI);
        }

        // Note: Using this method requires that your app has the Manifest.permission.SEND_SMS
        // permission.

        // Note: Beginning with Android 4.4 (API level 19), if and only if an app is not selected as
        // the default SMS app, the system automatically writes messages sent using this method to
        // the SMS Provider (the default SMS app is always responsible for writing its sent messages
        // to the SMS Provider). For information about how to behave as the default SMS app, see
        // Telephony.
        sms.sendMultipartTextMessage(message.getAddress(), null, parts, sentIntents,
                deliveryIntents);
    }
}
