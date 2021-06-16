package com.chiligarlic.ratatxt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppController appCtl = AppController.getInstance();

        appCtl.addToHistory("SmsBroadcastReceiver onReceive");
        appCtl.incrementCounter(AppController.COUNT_SMS_RECEIVED);

        if (intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
            String smsSender = "";
            StringBuilder smsBody = new StringBuilder();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                for (SmsMessage msg : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    smsSender = msg.getDisplayOriginatingAddress();
                    smsBody.append(msg.getMessageBody());
                }
            } else {
                Bundle smsBundle = intent.getExtras();
                if (smsBundle != null) {
                    Object[] pdus = (Object[]) smsBundle.get("pdus");
                    if (pdus == null) {
                        // Display some error to the user
                        appCtl.addToHistory("SmsBroadcastReceiver SmsBundle had no pdus key");
                        return;
                    }
                    SmsMessage[] messages = new SmsMessage[pdus.length];
                    for (int i = 0; i < messages.length; i++) {
                        messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                        smsBody.append(messages[i].getMessageBody());
                    }

                    smsSender = messages[0].getOriginatingAddress();
                }
            }

            SmsReceiver.push(smsSender, smsBody.toString());
        }
    }
}
