package com.dosse.chromiumautoupdater;

import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class EnableNonMarketAppsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enable_non_market_apps);
        Button b=(Button)(findViewById(R.id.button2));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(Settings.ACTION_SECURITY_SETTINGS);
                startActivity(intent);
            }
        });
        checkTPAppsAllowed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkTPAppsAllowed();
    }

    private void checkTPAppsAllowed(){
        if(Utils.thirdPartyAppsAllowed(getContentResolver())){
            Intent i=new Intent(EnableNonMarketAppsActivity.this,MainActivity.class);
            startActivity(i);
            finish();
        }
    }
}
