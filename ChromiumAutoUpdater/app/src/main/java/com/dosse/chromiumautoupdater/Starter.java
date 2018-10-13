package com.dosse.chromiumautoupdater;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * This class merely receives events from the system (boot, etc.) and forwards them to the service
 */
public class Starter extends BroadcastReceiver {
    private static final String TAG = "Chromium Updater";

    @Override
    public void onReceive(Context context, Intent intent) {
        Utils.log("onReceive");
        if(intent.getAction().equals("CANCEL_ACTION")){
            ChromiumUpdater.cancelRequested=true;
            try{
                ChromiumUpdater.notifMan.cancel(1);
            }catch(Throwable t){}
        }else {
            try {
                Intent startServiceIntent = new Intent(context, ChromiumUpdater.class);
                context.startService(startServiceIntent);
            }catch(Throwable t){} //try-catch added to catch a random exception sometimes thrown when the app was updated
        }
    }
}
