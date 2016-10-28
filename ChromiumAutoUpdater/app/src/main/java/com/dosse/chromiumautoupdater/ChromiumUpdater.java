package com.dosse.chromiumautoupdater;

import android.Manifest;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
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
 * Created by Federico on 2016-10-26.
 */

public class ChromiumUpdater extends Service {
    private static final String TAG = "Chromium Updater Svc";

    private boolean busy=false;
    private long lastUpdate=0;

    private class UpdateThread extends Thread{
        public void run(){
            try{
                DataInputStream fis=new DataInputStream(getApplicationContext().openFileInput("lastUpdate"));
                lastUpdate=fis.readLong();
                if(lastUpdate>Utils.getTimestamp()) lastUpdate=0;
                fis.close();
            }catch(Throwable e){}
            for(;;) {
                if(!busy){
                    Log.d(TAG, "THREAD ALIVE");
                    SharedPreferences prefs=getSharedPreferences("chromiumUpdater",MODE_PRIVATE);
                    long updateEvery=Integer.parseInt(prefs.getString("updateEvery","7"))*86400000L;
                    Log.d(TAG,"Timestamp: "+Utils.getTimestamp()+" Last update: "+lastUpdate+" Next update: "+(lastUpdate+updateEvery));
                    if(Utils.getTimestamp()-lastUpdate>=updateEvery){
                        Log.d(TAG,"Updating Chromium");
                        lastUpdate=Utils.getTimestamp();
                        try{
                            DataOutputStream fos=new DataOutputStream(getApplicationContext().openFileOutput("lastUpdate", Context.MODE_PRIVATE));
                            fos.writeLong(lastUpdate);
                            fos.close();
                        }catch(Throwable e){}
                        downloadUpdate();
                    }else Log.d(TAG,"No update necessary");
                }else Log.d(TAG,"Updater is busy, skipping tick");
                try{sleep(30000);}catch(Throwable e){}
            }
        }

        private void downloadUpdate(){
            synchronized(ChromiumUpdater.this){
                busy=true;
                NotificationManager mNotifyManager=null;
                try {
                    if(!Utils.isRooted()){
                        throw new Exception("Device not rooted");
                    }
                    if(!Utils.canWriteToSdcard(ChromiumUpdater.this)){
                        throw new Exception("Cannot write to sdcard");
                    }
                    if(!Utils.isConnected(getApplicationContext())){
                        throw new Exception("No Internet");
                    }
                    SharedPreferences prefs=getSharedPreferences("chromiumUpdater",MODE_PRIVATE);
                    if(prefs.getBoolean("noMobileConnections",true)&&Utils.isMobileConnection(getApplicationContext())){
                        throw new Exception("Avoiding mobile connection");
                    }
                    try {
                        mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ChromiumUpdater.this);
                        mBuilder.setContentTitle(getString(R.string.notification)).setSmallIcon(R.drawable.notification);
                        mBuilder.setProgress(100, 0, true);
                        mNotifyManager.notify(1, mBuilder.build());
                    }catch(Throwable t){}
                    File sdcard=Environment.getExternalStorageDirectory();
                    URL u = new URL("https://commondatastorage.googleapis.com/chromium-browser-snapshots/Android/LAST_CHANGE");
                    URLConnection c = u.openConnection();
                    c.connect();
                    BufferedReader br = new BufferedReader(new InputStreamReader(u.openStream()));
                    String lastVer = br.readLine();
                    br.close();
                    Log.d(TAG, "last build " + lastVer);
                    u = new URL("https://commondatastorage.googleapis.com/chromium-browser-snapshots/Android/" + lastVer + "/chrome-android.zip");
                    c = u.openConnection();
                    c.connect();
                    InputStream in = new BufferedInputStream(u.openStream());
                    FileOutputStream out = new FileOutputStream(new File(sdcard,"chromium.zip"));
                    Log.d(TAG, "download started");
                    for (;;) {
                        byte[] buff = new byte[8192];
                        try {
                            int l = in.read(buff);
                            if(l==-1) break;
                            out.write(buff, 0, l);
                        } catch (Exception e) {
                            if(!(e instanceof EOFException)) throw e;
                            break;
                        }
                    }
                    in.close();
                    out.flush();
                    out.close();
                    Log.d(TAG, "download complete");
                    Log.d(TAG, "extracting");
                    ZipInputStream zin = new ZipInputStream(new FileInputStream(new File(sdcard,"chromium.zip")));
                    ZipEntry z = null;
                    boolean foundApk=false;
                    while ((z = zin.getNextEntry()) != null) {
                        if (z.getName().contains("ChromePublic.apk")) {
                            Log.d(TAG, "found apk");
                            foundApk=true;
                            out = new FileOutputStream(new File(sdcard,"chromium.apk"));
                            for (;;) {
                                byte[] buff = new byte[8192];
                                try {
                                    int l = zin.read(buff);
                                    if(l==-1) break;
                                    out.write(buff, 0, l);
                                } catch (Exception e) {
                                    break;
                                }
                            }
                            Log.d(TAG, "apk extracted");
                            zin.closeEntry();
                            out.close();
                            break;
                        }
                    }
                    zin.close();
                    Log.d(TAG, "deleting zip");
                    new File(sdcard,"chromium.zip").delete();
                    if(!foundApk) throw new Exception("No apk");
                    Log.d(TAG, "installing apk");
                    String path=new File(sdcard,"chromium.apk").getAbsolutePath();
                    Log.d(TAG, path);
                    Process p=Runtime.getRuntime().exec("su");
                    OutputStream os=p.getOutputStream();
                    os.write(("pm install -r "+path+"\n").getBytes("ASCII")); os.flush();
                    os.write("exit\n".getBytes("ASCII")); os.flush(); os.close();
                    p.waitFor();
                    Log.d(TAG, "apk installed");
                    Log.d(TAG, "deleting apk");
                    new File(path).delete();
                } catch (Throwable e) {
                    Log.d(TAG, "err " + e);
                    lastUpdate=0; //retry asap
                }
                try{
                    mNotifyManager.cancel(1);
                }catch (Throwable t){}
                busy=false;
            }
        }

    }



    private UpdateThread t=null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        registerReceiver(new Starter(),new IntentFilter("android.intent.action.TIME_TICK"));
    }

    @Override
    public void onDestroy() {

    }

    public int onStartCommand (Intent intent, int flags, int startId){
        if(t==null||!t.isAlive()){t=new UpdateThread();t.start();}
        return START_STICKY;
    }

}
