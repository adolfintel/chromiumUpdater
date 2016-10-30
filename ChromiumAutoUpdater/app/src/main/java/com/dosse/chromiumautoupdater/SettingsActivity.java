package com.dosse.chromiumautoupdater;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.widget.Toast;

/**
 * Settings activity
 */
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getPreferenceManager().setSharedPreferencesName("chromiumUpdater");
        addPreferencesFromResource(R.xml.pref_general);
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
        //github link listener
        ((Preference)findPreference("github")).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github))));
                return true;
            }
        });
        //hide from launcher listener. a file is created to disable the option disabled if the app was already hidden.
        Preference hideApp=(Preference)findPreference("hideApp");
        try{
            getApplicationContext().openFileInput("hidden").close();
            hideApp.setEnabled(false);
        }catch (Throwable t){}
        hideApp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(new ComponentName(SettingsActivity.this,MainActivity.class), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Toast.makeText(getApplicationContext(),getString(R.string.hide_clicked),Toast.LENGTH_LONG).show();
                try{getApplicationContext().openFileOutput("hidden",MODE_PRIVATE).close();}catch(Throwable t){}
                finish();
                return true;
            }
        });
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
        if(key.equalsIgnoreCase("updateEvery")){
            //update frequency must be a positive integer
            if(Integer.parseInt(sharedPreferences.getString("updateEvery","7"))<1){
                SharedPreferences.Editor e=sharedPreferences.edit();
                e.putString("updateEvery","1");
                e.commit();
            }
        }
    }
}