package com.dosse.chromiumautoupdater;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Calendar;

/**
 * This class contains utility methods
 */

public class Utils {

    /**
     * Checks if a package is installed
     * @param ctx application context
     * @param p package name (e.g. org.chromium.chrome)
     * @return true if installed, false otherwise
     */
    public static boolean isPackageInstalled(Context ctx, String p){
        try {
            ctx.getPackageManager().getPackageInfo(p, PackageManager.GET_META_DATA);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Check if the phone is rooted
     * @return true if su binary is present, false otherwise
     */
    public static boolean isRooted(){
        return rootCheck1()||rootCheck2();
    }

    private static boolean rootCheck1(){
        for (String s : System.getenv("PATH").split(System.getProperty("path.separator"))) {
            log("rc1 looking for su in "+s);
            if ( new File( s + (s.endsWith("/")?"":"/")+"su" ).exists() ) {
                log("found root");
                return true;
            }
        }
        return false;
    }

    private static boolean rootCheck2(){
        for (String s : new String[]{"/sbin/", "/system/bin/", "/system/xbin/", "/data/local/xbin/", "/data/local/bin/", "/system/sd/xbin/", "/system/bin/failsafe/", "/data/local/"}) {
            log("rc2 looking for su in "+s);
            if (new File(s + "su").exists()) {
                log("found root");
                return true;
            }
        }
        return false;
    }

    public static boolean thirdPartyAppsAllowed(ContentResolver cr){
        try {
            return Settings.Secure.getInt(cr, Settings.Secure.INSTALL_NON_MARKET_APPS)!=0;
        } catch (Throwable e) {
            return true;
        }
    }

    /**
     * Checks for storage write permissions
     * @param ctx context
     * @return true if permission is granted, false otherwise
     */
    public static boolean canWriteToSdcard(Context ctx){
        return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if an internet connection is available
     * @param ctx context
     * @return true if available, false otherwise
     */
    public static boolean isConnected(Context ctx){
        NetworkInfo info=((ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return info!=null&&info.isConnected();
    }

    /**
     * Checks if the main internet connection is mobile
     * @param ctx context
     * @return true if available, false if not mobile or not connected
     */
    public static boolean isMobileConnection(Context ctx){
        NetworkInfo info=((ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return info!=null&&info.getType()==ConnectivityManager.TYPE_MOBILE;
    }


    /**
     * Timestamp function in ms
     * @return utc time in ms
     */
    public static long getTimestamp(){
        return Calendar.getInstance().getTimeInMillis();
    }

    public static final boolean USE_LOG= BuildConfig.DEBUG; //logging is only active in debug builds
    public static final boolean USE_LOGFILE=false; //if active, log will be stored on /sdcard/chrupd.log

    /**
     * Log
     * @param s String
     */
    public static void log(String s){
        if(USE_LOG) Log.d("Chromium Updater",s);
        if(USE_LOGFILE){
            try {
                File sdcard = Environment.getExternalStorageDirectory();
                File logFile = new File(sdcard, "chrupd.log");
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true)));
                w.write(getTimestamp()+" - "+s+"\n");
                w.flush();
                w.close();
            }catch(Throwable t){
            }
        }
    }

}
