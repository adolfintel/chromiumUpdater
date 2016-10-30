package com.dosse.chromiumautoupdater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * This class merely receives events from the system (boot, etc.) and starts the service
 */
public class Starter extends BroadcastReceiver {
    private static final String TAG = "Chromium Updater";

    @Override
    public void onReceive(Context context, Intent intent) {
        Utils.log("onReceive");
        Intent startServiceIntent = new Intent(context, ChromiumUpdater.class);
        context.startService(startServiceIntent);
    }
}
