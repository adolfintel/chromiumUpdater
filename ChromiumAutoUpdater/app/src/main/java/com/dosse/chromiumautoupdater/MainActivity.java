package com.dosse.chromiumautoupdater;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity{


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(!Utils.isRooted()){
            noRootMessage();
        }else{
            try{
                Process p=Runtime.getRuntime().exec("su");
                OutputStream os=p.getOutputStream();
                os.write("touch /test.dat\n".getBytes("ASCII")); os.flush();
                Thread.sleep(100);
                if(new File("/test.dat").exists()){
                    os.write("rm /test.dat\n".getBytes("ASCII")); os.flush();
                    os.write("exit\n".getBytes("ASCII")); os.flush(); os.close(); p.waitFor();
                }else{
                    noRootMessage();
                }
            }catch(Throwable e){noRootMessage();}
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

    private void noRootMessage(){
        new AlertDialog.Builder(getApplicationContext()).setTitle(getString(R.string.noroot_title)).setMessage(getString(R.string.noroot_text)).setNeutralButton(getString(R.string.noroot_exit), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                System.exit(0);
            }
        });
    }

}
