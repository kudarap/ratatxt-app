package com.chiligarlic.ratatxt;

import java.util.Date;

public class SmsReceiver {
    /**
     * Push inbox message to API when SmsReceiver is running.
     *
     * @param address sender number.
     * @param text message body.
     */
    public static void push(String address, String text) {
        AppController appCtl = AppController.getInstance();
        // sms receiver is not running msg will be ignored
        if (!appCtl.isReceiverRunning()) {
            return;
        }

        Ratatxt.Message msg = new Ratatxt.Message();
        msg.setDeviceId(appCtl.getDevice().getId());
        msg.setAddress(address);
        msg.setText(text);

        System.out.printf("SmsReceiver push %s %s %s\n", msg.getDeviceId(), address, text);
        appCtl.getRatatxt().pushInbox(text, address, new Date().getTime(), data -> {
            appCtl.incrementCounter(AppController.COUNT_INBOX);
            appCtl.addToHistory("inbox push success! " + Ratatxt.Message.parse(data).getId());
        }, errResp -> {
            appCtl.addToHistory("inbox push failed! " + errResp.getMessage());
        });
    }
}
