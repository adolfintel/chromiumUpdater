package com.dosse.chromiumautoupdater;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class ChromiumAlreadyInstalledActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chromium_already_installed);
        //event listener for uninstall button
        Button b=(Button)(findViewById(R.id.button3));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", "org.chromium.chrome", null);
                intent.setData(uri);
                startActivity(intent);
                finish();
            }
        });
        //event listener for ignore button
        b=(Button)(findViewById(R.id.button4));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent();
                Intent i = new Intent(ChromiumAlreadyInstalledActivity.this, MainActivity.class);
                startActivity(i);
                finish();
            }
        });
    }
}
