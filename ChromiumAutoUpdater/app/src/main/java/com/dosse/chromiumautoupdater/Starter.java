package com.dosse.chromiumautoupdater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;

/**
 * Created by Federico on 2016-10-26.
 */

public class Starter extends BroadcastReceiver {
    private static final String TAG = "Chromium Updater";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        Intent startServiceIntent = new Intent(context, ChromiumUpdater.class);
        context.startService(startServiceIntent);
    }
}
