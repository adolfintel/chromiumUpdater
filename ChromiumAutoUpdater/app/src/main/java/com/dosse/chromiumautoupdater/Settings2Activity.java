package com.dosse.chromiumautoupdater;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.widget.Toast;

/**
 * Settings activity
 */
public class Settings2Activity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getPreferenceManager().setSharedPreferencesName("chromiumUpdater");
        addPreferencesFromResource(R.xml.pref_advanced);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(
                this);
        try {
            PreferenceGroup p = ((PreferenceGroup) getPreferenceScreen());
            for (int i = 0; i < p.getPreferenceCount(); i++) {
                Preference pref = p.getPreference(i);
                if (pref instanceof ListPreference) {
                    ListPreference listPref = (ListPreference) pref;
                    pref.setSummary(listPref.getEntry());
                }
            }
        }catch (Throwable t){}

    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key==null) return;
        if(key.equalsIgnoreCase("installMethod")||key.equalsIgnoreCase("channel")){
            Preference pref = findPreference(key);
            if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                pref.setSummary(listPref.getEntry());
            }
        }
        if(key.equalsIgnoreCase("channel")){
            ChromiumUpdater.cancelRequested=true;
            while(ChromiumUpdater.isBusy()) try{Thread.sleep(100);}catch(Throwable t){}
            Intent startServiceIntent = new Intent(getApplicationContext(), ChromiumUpdater.class);
            startServiceIntent.putExtra("forced",true);
            startService(startServiceIntent);
            Toast.makeText(getApplicationContext(),getString(R.string.updateNow_clicked),Toast.LENGTH_LONG).show();
        }

    }
}