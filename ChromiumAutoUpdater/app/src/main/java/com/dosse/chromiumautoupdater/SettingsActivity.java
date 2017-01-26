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
        //advanced settings listener
        ((Preference)findPreference("advancedSettings")).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(SettingsActivity.this,Settings2Activity.class));
                return true;
            }
        });
        //github link listener
        ((Preference)findPreference("github")).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github))));
                return true;
            }
        });
        //update now listener
        ((Preference)findPreference("forceUpdate")).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent startServiceIntent = new Intent(getApplicationContext(), ChromiumUpdater.class);
                startServiceIntent.putExtra("forced",true);
                startService(startServiceIntent);
                Toast.makeText(getApplicationContext(),getString(R.string.updateNow_clicked),Toast.LENGTH_LONG).show();
                return true;
            }
        });

        //hide app listener
        ((Preference)findPreference("hideApp")).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(new ComponentName(SettingsActivity.this,MainActivity.class), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Toast.makeText(getApplicationContext(),getString(R.string.hide_clicked),Toast.LENGTH_LONG).show();
                try{getApplicationContext().openFileOutput("hidden",MODE_PRIVATE).close();}catch(Throwable t){}
                enableDisableOptions();
                return true;
            }
        });

        //some options are only active if auto updates are active
        enableDisableOptions();

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
        enableDisableOptions(); //update grayed out options
        if(key.equalsIgnoreCase("updateEvery")){
            //update frequency must be a positive integer
            if(Integer.parseInt(sharedPreferences.getString("updateEvery","7"))<1){
                SharedPreferences.Editor e=sharedPreferences.edit();
                e.putString("updateEvery","1");
                e.commit();
            }
        }
    }

    private void enableDisableOptions(){
        if(getSharedPreferences("chromiumUpdater",MODE_PRIVATE).getBoolean("autoSwitch",true)){
            //when auto updates are enabled, updateEvery, noMobileConnections and hideApp can be changed
            findPreference("updateEvery").setEnabled(true);
            findPreference("noMobileConnections").setEnabled(true);
            findPreference("hideApp").setEnabled(true);
        }else{
            //otherwise they're grayed out
            findPreference("updateEvery").setEnabled(false);
            findPreference("noMobileConnections").setEnabled(false);
            findPreference("hideApp").setEnabled(false);
        }
        //if the application is hidden, hideApp is grayed out
        try{
            getApplicationContext().openFileInput("hidden").close();
            ((Preference)findPreference("hideApp")).setEnabled(false);
            ((Preference)findPreference("autoSwitch")).setEnabled(false);
        }catch (Throwable t){}
    }
}