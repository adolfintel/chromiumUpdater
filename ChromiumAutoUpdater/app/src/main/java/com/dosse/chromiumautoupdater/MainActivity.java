package com.dosse.chromiumautoupdater;

import android.Manifest;
import android.content.Intent;
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
