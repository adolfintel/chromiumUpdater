package com.dosse.chromiumautoupdater;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.Preference;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * The welcome screen. It is used to start the service for the first time and to obtain necessary privileges.
 */
public class MainActivity extends AppCompatActivity{


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //check and obtain root privileges
        if(!Utils.isRooted()){
            noRootMessage();
        }else{
            try{
                Runtime.getRuntime().exec(new String[]{"su","-c","exit"}).waitFor();
                //we should probably add a check in a later version to make sure the user actually clicked yes to the prompt...
            }catch(Throwable e){noRootMessage();}
        }
        //check and obtain storage write permission
        while(!Utils.canWriteToSdcard(this)){ ActivityCompat.requestPermissions(this,new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },1); try{Thread.sleep(1000);}catch(Throwable e){}}
        //check if first time starting
        try{
            getApplicationContext().openFileInput("firstStart").close();
            //not the first time
        }catch (Throwable t){
            //fist time, show intro
            try{getApplicationContext().openFileOutput("firstStart",MODE_PRIVATE).close();}catch(Throwable t2){}
            Intent i = new Intent(MainActivity.this, IntroActivity.class);
            startActivity(i);
            finish();
            return;
        }
        //check if chromium is already installed
        try{
            //already checked, no need to show the warning
            getApplicationContext().openFileInput("TPChromiumChecked").close();
        }catch(Throwable t) {
            //not checked: if chromium is already installed, show warning
            try{getApplicationContext().openFileOutput("TPChromiumChecked",MODE_PRIVATE).close();}catch(Throwable t2){}
            if (Utils.isPackageInstalled(getApplicationContext(), "org.chromium.chrome")) {
                try{
                    //it was installed by an older version of this app, it's fine
                    getApplicationContext().openFileInput("lastUpdate").close();
                }catch(Throwable t2) {
                    //it was installed by the user or another app, show warning
                    Intent i = new Intent(MainActivity.this, ChromiumAlreadyInstalledActivity.class);
                    startActivity(i);
                    finish();
                    return;
                }
            }
        }
        //start updater service
        Intent startServiceIntent = new Intent(getApplicationContext(), ChromiumUpdater.class);
        getApplicationContext().startService(startServiceIntent);
        //event listener for settings button
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,SettingsActivity.class));
            }
        });
        //event listener for help button
        findViewById(R.id.button6).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.needHelp_url))));
            }
        });
    }

    private void noRootMessage(){
        ((TextView)(findViewById(R.id.textView))).setText(getString(R.string.noroot_title));
        ((TextView)(findViewById(R.id.textView4))).setText(getString(R.string.noroot_text));
        if(!Utils.thirdPartyAppsAllowed(getContentResolver())){
            Intent i=new Intent(MainActivity.this,EnableNonMarketAppsActivity.class);
            startActivity(i);
            finish();
        }
    }

}
