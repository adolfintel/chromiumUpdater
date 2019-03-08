package com.dosse.chromiumautoupdater;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ShowErrorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_error);
        TextView t=(TextView)(findViewById(R.id.textView6));
        try{
            Intent i=getIntent();
            String message=i.getStringExtra("message"), data=i.getStringExtra("data");
            t.setText(message+"\n\n"+data);
        }catch(Throwable e){
            finish();
        }
        ((Button)(findViewById(R.id.report))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:"));
                    intent.putExtra(Intent.EXTRA_EMAIL, "info@fdossena.com");
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Chromium Auto Updater - Bug report");
                    intent.putExtra(Intent.EXTRA_TEXT, "\n\n\n\n\n\n---------------------------\nException:"+((TextView)(findViewById(R.id.textView6))).getText()+"\n\n"+"Device model: "+Build.MODEL+"\nSDK Version: "+Build.VERSION.SDK_INT+"\nApp Version: "+getPackageManager().getPackageInfo(getPackageName(), 0).versionCode);
                    startActivity(Intent.createChooser(intent, "Email"));
                }catch(Throwable t){}
            }
        });
    }
}
