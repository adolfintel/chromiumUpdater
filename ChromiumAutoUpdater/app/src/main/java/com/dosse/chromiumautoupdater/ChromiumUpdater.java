package com.dosse.chromiumautoupdater;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.os.StrictMode;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import static com.dosse.chromiumautoupdater.Utils.log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The actual Updater Service.
 *
 * When the service is started, it creates a new thread which checks periodically for updates.
 * Every 10 seconds, it checks the timestamp and if it's been long enough since the last update was installed (or an update was forced), it will download and install the latest APK.
 *
 */

public class ChromiumUpdater extends Service {
    private static final String TAG = "Chromium Updater Svc";

    private static UpdateThread t=null;
    private static boolean forcedUpdateRequested=false;
    private static boolean busy=false; //if for obscure reasons the service is started multiple times, this is used to avoid overlapping
    public static boolean isBusy(){return busy;}

    public static boolean cancelRequested=false; //set to true by Starter class when a CANCEL_ACTION event is received (cancel button on notification)
    public static NotificationManager notifMan=null; //just for convenience, we make this public so that the Starter class can cancel notifications directly without waiting for this thread

    private static final long MIN_FREE_SPACE=300*1024*1024;

    /**
     * The actual thread which checks, downloads and installs updates
     */
    private class UpdateThread extends Thread{
        public void run(){
            long lastUpdate=0;
            //read last update timestamp from file
            try{
                DataInputStream fis=new DataInputStream(getApplicationContext().openFileInput("lastUpdate"));
                lastUpdate=fis.readLong();
                fis.close();
            }catch(Throwable e){}
            for(;;) {
                if(!busy){
                    log("THREAD ALIVE");
                    cancelRequested=false;
                    SharedPreferences prefs=getSharedPreferences("chromiumUpdater",MODE_PRIVATE);
                    //how often do we need to download updates?
                    long updateEvery=Integer.parseInt(prefs.getString("updateEvery","7"))*86400000L; //days -> milliseconds
                    if(lastUpdate>Utils.getTimestamp()){log("lastUpdate is in the future. Discarding."); lastUpdate=0;} //if the timestamp was invalid, lastUpdate will be 0 and chromium will be downloaded asap
                    log("Timestamp: "+Utils.getTimestamp()+" Last update: "+lastUpdate+" Next update: "+(lastUpdate+updateEvery));
                    if(forcedUpdateRequested||(prefs.getBoolean("autoSwitch",true)&&Utils.getTimestamp()-lastUpdate>=updateEvery)){ //time to update
                        if(forcedUpdateRequested){
                            log("Forced update requested");
                        }
                        log("Updating Chromium");
                        lastUpdate=downloadUpdate(); //downloadUpdate will download and install the latest build and return either the current timestamp (success) or 0 (error, try again asap)
                        //save timestamp (or 0) to file
                        try{
                            DataOutputStream fos=new DataOutputStream(getApplicationContext().openFileOutput("lastUpdate", Context.MODE_PRIVATE));
                            fos.writeLong(lastUpdate);
                            fos.close();
                        }catch(Throwable e){}
                        if(forcedUpdateRequested){
                            forcedUpdateRequested=false;
                        }
                    }else log("No update necessary");
                }else log("Updater is busy, skipping tick");
                //wait 10 seconds
                try{sleep(10000);}catch(Throwable e){}
            }
        }

        /**
         * ignorable exceptions will not be shown even when notify errors is enabled
         */
        private class IgnorableException extends Exception {

            public IgnorableException(String message) {
                super(message);
            }
        }

        /**
         * Downloads and installs the latest version of chromium from https://commondatastorage.googleapis.com/chromium-browser-snapshots/Android
         * @return timestamp (success) or 0 (error)
         */
        private long downloadUpdate(){
            long ret=Utils.getTimestamp();
            synchronized(ChromiumUpdater.this){
                busy=true;
                NotificationManager mNotifyManager=null;
                try {
                    //can we actually update chromium?
                    if(!Utils.isRooted()&&!Utils.thirdPartyAppsAllowed(getContentResolver())){
                        throw new Exception("No root and no third party apps allowed");
                    }
                    if(!Utils.canWriteToSdcard(ChromiumUpdater.this)){
                        throw new Exception("Cannot write to sdcard");
                    }
                    if(!Utils.isConnected(getApplicationContext())){
                        throw new IgnorableException("No Internet");
                    }
                    SharedPreferences prefs=getSharedPreferences("chromiumUpdater",MODE_PRIVATE);
                    if(!forcedUpdateRequested){
                        if(prefs.getBoolean("noMobileConnections",false)&&Utils.isMobileConnection(getApplicationContext())){
                            throw new IgnorableException("Avoiding mobile connection");
                        }
                    }
                    //ok, we can do it
                    //intent for cancel button on notification
                    Intent cancelReceive =new Intent();
                    cancelReceive.setAction("CANCEL_ACTION");
                    PendingIntent cancelIntent=PendingIntent.getBroadcast(getApplicationContext(),12345,cancelReceive,PendingIntent.FLAG_IMMUTABLE);
                    //create update notification with indeterminate progressbar
                    mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notifMan=mNotifyManager;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //on oreo and newer, create a notification channel
                        NotificationChannel channel = new NotificationChannel(TAG, TAG, NotificationManager.IMPORTANCE_LOW);
                        channel.enableVibration(false);
                        channel.enableLights(false);
                        channel.setShowBadge(true);
                        notifMan.createNotificationChannel(channel);
                    }
                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ChromiumUpdater.this,TAG);
                    mBuilder.setContentTitle(getString(R.string.notification_title)).setSmallIcon(R.drawable.notification).setContentText(getString(R.string.notification_starting)).setOngoing(true).setShowWhen(false);
                    mBuilder.setProgress(100, 0, true);
                    mBuilder.addAction(android.support.design.R.drawable.navigation_empty_icon,getString(R.string.notification_cancel),cancelIntent); //add cancel button
                    mNotifyManager.notify(1, mBuilder.build());
                    File sdcard=Environment.getExternalStorageDirectory();
                    if(cancelRequested){
                        throw new IgnorableException("Cancelled by user");
                    }
                    //get id of latest build
                    String latestVer=null;
                    URL u;
                    URLConnection c;
                    if(getSharedPreferences("chromiumUpdater",MODE_PRIVATE).getString("channel",getString(R.string.channelValue_latest)).equalsIgnoreCase(getString(R.string.channelValue_stable))){
                        u=new URL("https://omahaproxy.appspot.com/all?channel=stable&os=android");
                        c = u.openConnection();
                        c.connect();
                        BufferedReader br = new BufferedReader(new InputStreamReader(u.openStream()));
                        br.readLine(); //first line is useless to us
                        latestVer = br.readLine().split(",")[7];
                        br.close();
                    }else{
                        u = new URL("https://commondatastorage.googleapis.com/chromium-browser-snapshots/Android/LAST_CHANGE");
                        c = u.openConnection();
                        c.connect();
                        BufferedReader br = new BufferedReader(new InputStreamReader(u.openStream()));
                        latestVer = br.readLine();
                        br.close();
                    }
                    log("latest build " + latestVer);
                    if(cancelRequested){
                        throw new IgnorableException("Cancelled by user");
                    }
                    //check currently installed build
                    String currentVer="";
                    try{
                        BufferedReader fis=new BufferedReader(new InputStreamReader(getApplicationContext().openFileInput("currentBuild")));
                        currentVer=fis.readLine();
                        fis.close();
                    }catch(Throwable e){
                    }
                    if(cancelRequested){
                        throw new IgnorableException("Cancelled by user");
                    }
                    if(currentVer.trim().equals(latestVer.trim())&&!forcedUpdateRequested){
                        cancelRequested=true;
                        throw new IgnorableException("Already on latest build");
                    }
                    //check if we have enough space to update chromium
                    StatFs stat=new StatFs(sdcard.getAbsolutePath());
                    log("free space: "+stat.getAvailableBytes());
                    if(stat.getAvailableBytes()<MIN_FREE_SPACE){ //not enough space, show notification and cancel
                        mBuilder = new NotificationCompat.Builder(ChromiumUpdater.this,TAG);
                        mBuilder.setContentTitle(getString(R.string.notEnoughSpace)).setContentText(getString(R.string.notEnoughSpace_retry)).setSmallIcon(R.drawable.notification);
                        mBuilder.setAutoCancel(true);
                        mNotifyManager.notify(4,mBuilder.build());
                        cancelRequested=true;
                        throw new IgnorableException("Not enough free space");
                    }
                    InputStream in=null;
                    //fallback: make sure the build number actually exists. If it doesn't, find the first one that's older and exists
                    int changes=0; //counter of how many times we decremented the build counter
                    String originalBuildNumber=latestVer;
                    while(changes<100) {
                        try {
                            u = new URL("https://commondatastorage.googleapis.com/chromium-browser-snapshots/Android/" + latestVer + "/chrome-android.zip");
                            c = u.openConnection();
                            c.connect();
                            in = new BufferedInputStream(u.openStream());
                            latestVer=originalBuildNumber;
                            break;
                        } catch (FileNotFoundException e) {
                            latestVer=""+(Integer.parseInt(latestVer)-1);
                            log("Decrementing to "+latestVer+" because specified build number was not found");
                            changes++;
                        }
                        if(cancelRequested){
                            throw new IgnorableException("Cancelled by user");
                        }
                    }
                    if(changes>=100) throw new Exception("Build number error");
                    if(cancelRequested){
                        throw new IgnorableException("Cancelled by user");
                    }
                    //download zip of latest build to sdcard
                    int size=c.getContentLength(),downloaded=0;
                    FileOutputStream out = new FileOutputStream(new File(sdcard,"chromium.zip"));
                    log("download started");
                    mBuilder.setProgress(100,0,false).setContentText(getString(R.string.notification_downloading)); //set progress bar to 0% and show Downloading
                    mNotifyManager.notify(1, mBuilder.build());
                    long lastNotUpdate=System.nanoTime();
                    for (;;) {
                        if(cancelRequested){
                            throw new IgnorableException("Cancelled by user");
                        }
                        byte[] buff = new byte[262144];
                        try {
                            int l = in.read(buff);
                            if(l==-1) break;
                            out.write(buff, 0, l);
                            downloaded+=l;
                            if(System.nanoTime()-lastNotUpdate>1000000000L){ //every 1s update notification
                                lastNotUpdate=System.nanoTime();
                                log(downloaded+"/"+size+" downloaded");
                                mBuilder.setProgress(size,downloaded,false);
                                mNotifyManager.notify(1, mBuilder.build());
                            }
                        } catch (Exception e) {
                            if(!(e instanceof EOFException)) throw e;
                            break;
                        }
                    }
                    in.close();
                    out.flush();
                    out.close();
                    log("download complete");
                    if(cancelRequested){
                        throw new IgnorableException("Cancelled by user");
                    }
                    mBuilder.setProgress(100,0,true).setContentText(getString(R.string.notification_installing)); //set progress bar to indeterminate and show Installing update
                    mNotifyManager.notify(1, mBuilder.build());
                    //now we have to scan the zip file and extract the APK
                    log("extracting");
                    ZipInputStream zin = new ZipInputStream(new FileInputStream(new File(sdcard,"chromium.zip")));
                    ZipEntry z = null;
                    boolean foundApk=false;
                    while ((z = zin.getNextEntry()) != null) {
                        if (z.getName().contains("ChromePublic.apk")) {
                            log("found apk");
                            foundApk=true;
                            out = new FileOutputStream(new File(sdcard,"chromium.apk"));
                            for (;;) {
                                if(cancelRequested){
                                    throw new IgnorableException("Cancelled by user");
                                }
                                byte[] buff = new byte[262144];
                                try {
                                    int l = zin.read(buff);
                                    if(l==-1) break;
                                    out.write(buff, 0, l);
                                } catch (Exception e) {
                                    break;
                                }
                            }
                            log("apk extracted");
                            zin.closeEntry();
                            out.close();
                            break;
                        }
                    }
                    zin.close();
                    mBuilder.mActions.clear(); //remove cancel button
                    //now that we have the APK we can delete the zip file...
                    log("deleting zip");
                    new File(sdcard,"chromium.zip").delete();
                    if(!foundApk) throw new Exception("No apk");
                    //and now we can install the apk
                    String installMethod=prefs.getString("installMethod","auto");
                    log("install method pref: "+installMethod);
                    if(installMethod.equalsIgnoreCase("auto")) installMethod=Utils.isRooted()?"root":"noroot";
                    log("install method: "+installMethod);
                    if(installMethod.equalsIgnoreCase("root")) {
                        //root: install it silently using some root wizardry
                        log("installing apk - root");
                        String path = new File(sdcard, "chromium.apk").getAbsolutePath();
                        log(path);
                        Process p = Runtime.getRuntime().exec("su"); //create elevated shell
                        OutputStream os = p.getOutputStream();
                        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){
                            //after oreo, pm can no longer install shit from sdcard (deliberate sabotage of custom roms by google, no doubt), so we replace sdcard with /data/local/tmp and hope for the best
                            log("moving apk to /data/local/tmp for pie");
                            os.write(("mv "+path+" /data/local/tmp\n").getBytes("ASCII"));
                            os.flush();
                            path="/data/local/tmp/chromium.apk";
                        }
                        log("calling pm install");
                        os.write(("pm install -r " + path + "\n").getBytes("ASCII"));
                        os.flush(); //pm install -r chromium.apk
                        os.write("exit\n".getBytes("ASCII"));
                        os.flush();
                        os.close(); //close elevated shell
                        p.waitFor(); //wait for it to actually terminate
                        log("apk installed");
                        //chromium is now installed (no real way to be sure actually) and we can delete the APK
                        log("deleting apk");
                        new File(path).delete();
                        //show update done notification if enabled
                        if(prefs.getBoolean("notifyDone",false)) {
                            NotificationCompat.Builder mBuilder2 = new NotificationCompat.Builder(ChromiumUpdater.this,TAG);
                            Intent intent=null;
                            try{
                                intent = getPackageManager().getLaunchIntentForPackage("org.chromium.chrome");
                                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            }catch (Throwable t){}
                            mBuilder2.setContentIntent(PendingIntent.getActivity(ChromiumUpdater.this,0,intent,0)); //when tapped, launch chromium
                            mBuilder2.setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.notifyDone_notification)).setSmallIcon(R.drawable.notification).setAutoCancel(true);
                            mNotifyManager.notify(2, mBuilder2.build());
                        }
                    }else{
                        //no root: show update ready notification
                        log("installing apk - no root");
                        try{
                            //now what the fuck is this thing? well in Android 7 they blocked file:// URIs from being passed with intents. This is a workaround.
                            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                            StrictMode.setVmPolicy(builder.build());
                        }catch (Throwable t){log("err "+t);}
                        NotificationCompat.Builder mBuilder2 = new NotificationCompat.Builder(ChromiumUpdater.this,TAG);
                        mBuilder2.setContentTitle(getString(R.string.notification_noroot_ready)).setContentText(getString(R.string.notification_noroot_ready_text)).setSmallIcon(R.drawable.notification).setOngoing(true);
                        Intent intent=null;
                        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
                            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                            intent.setData(Uri.fromFile(new File(sdcard, "chromium.apk"))); //install apk when notification is clicked
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }else{
                            intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.fromFile(new File(sdcard, "chromium.apk")), "application/vnd.android.package-archive"); //install apk when notification is clicked
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        mBuilder2.setContentIntent(PendingIntent.getActivity(ChromiumUpdater.this,0,intent,0));
                        mBuilder2.setAutoCancel(true); //when clicked, automatically removes the notification
                        mNotifyManager.notify(2,mBuilder2.build());
                    }
                    try{
                        BufferedWriter fos=new BufferedWriter(new OutputStreamWriter(getApplicationContext().openFileOutput("currentBuild", Context.MODE_PRIVATE)));
                        fos.write(latestVer.trim());
                        fos.close();
                    }catch(Throwable e){}
                } catch (Throwable e) {
                    //something happened, return 0 (or timestamp if cancelled)
                    log("err " + e);
                    ret=0;
                    if(cancelRequested) ret=Utils.getTimestamp();
                    if(!(e instanceof IgnorableException) && getSharedPreferences("chromiumUpdater",MODE_PRIVATE).getBoolean("notifyErrors",false)){
                        try{
                            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ChromiumUpdater.this,TAG);
                            mBuilder.setContentTitle(getString(R.string.error)).setContentText(e.toString()).setSmallIcon(R.drawable.notification);
                            Intent notifyIntent = new Intent(getApplicationContext(), ShowErrorActivity.class);
                            notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            notifyIntent.putExtra("message",e.toString());
                            try{
                                String data="Stack trace:\n";
                                for(StackTraceElement x:e.getStackTrace()){
                                    data+=x.toString()+"\n";
                                }
                                notifyIntent.putExtra("data",data);
                            }catch(Throwable t){
                                notifyIntent.putExtra("data","");
                            }
                            PendingIntent notifyPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, notifyIntent, PendingIntent.FLAG_IMMUTABLE);
                            mBuilder.setContentIntent(notifyPendingIntent);
                            mBuilder.setAutoCancel(true);
                            mNotifyManager.notify(3,mBuilder.build());
                        }catch (Throwable t){log("I failed at failing: "+t);}
                    }
                }
                try{
                    mNotifyManager.cancel(1);
                }catch(Throwable t){}
                busy=false;
                cancelRequested=false;
            }
            return ret;
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static BroadcastReceiver broadcastReceiver;

    @Override
    public void onCreate() {
        try{
            unregisterReceiver(broadcastReceiver);
        }catch (Throwable t){}
        try {
            broadcastReceiver = new Starter();
            registerReceiver(broadcastReceiver, new IntentFilter("android.intent.action.TIME_TICK"));
        }catch(Throwable t){}
    }

    @Override
    public void onDestroy() {

    }

    /*
        called when the service is started. creates the UpdateThread if it's not already been created and tells android to restart the service if it dies (sticky)
     */
    public int onStartCommand (Intent intent, int flags, int startId){
        if(intent!=null&&intent.getBooleanExtra("forced",false)) forcedUpdateRequested=true;
        if(t==null||!t.isAlive()){t=new UpdateThread();t.start();}
        return START_STICKY;
    }

}
