package com.dosse.chromiumautoupdater;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.ActivityCompat;

import java.io.File;
import java.util.Calendar;

/**
 * Created by Federico on 2016-10-27.
 */

public class Utils {
    public static boolean isRooted(){
        for (String s : new String[]{"/sbin/", "/system/bin/", "/system/xbin/", "/data/local/xbin/", "/data/local/bin/", "/system/sd/xbin/", "/system/bin/failsafe/", "/data/local/"}) {
            if ( new File( s + "su" ).exists() ) {
                return true;
            }
        }
        return false;
    }

    public static boolean canWriteToSdcard(Context ctx){
        return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isConnected(Context ctx){
        NetworkInfo info=((ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return info!=null&&info.isConnected();
    }

    public static boolean isMobileConnection(Context ctx){
        NetworkInfo info=((ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return info!=null&&info.getType()==ConnectivityManager.TYPE_MOBILE;
    }

    public static long getTimestamp(){
        return Calendar.getInstance().getTimeInMillis();
    }

}
