package com.dosse.chromiumautoupdater;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity{


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(!Utils.isRooted()){
            new AlertDialog.Builder(getApplicationContext()).setTitle("Device not rooted").setMessage("This application requires a rooted device. You can find instruction online on how to root your device.\n\n--This is not an application error--").setNeutralButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    System.exit(0);
                }
            });
        }else{
            try{Runtime.getRuntime().exec("su -c exit").waitFor();}catch(Throwable e){}
        }
        while(!Utils.canWriteToSdcard(this)){ ActivityCompat.requestPermissions(this,new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },1); try{Thread.sleep(1000);}catch(Throwable e){}}
        Intent startServiceIntent = new Intent(getApplicationContext(), ChromiumUpdater.class);
        getApplicationContext().startService(startServiceIntent);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,SettingsActivity.class));
            }
        });
    }

}
