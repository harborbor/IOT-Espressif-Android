package com.espressif.iot.ui.settings;

import java.io.InputStream;
import java.util.Locale;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.webkit.WebView;

import com.espressif.iot.R;
import com.espressif.iot.base.api.EspBaseApiUtil;
import com.espressif.iot.base.api.EspBaseApiUtil.ProgressUpdateListener;
import com.espressif.iot.base.application.EspApplication;
import com.espressif.iot.type.upgrade.EspUpgradeApkResult;
import com.espressif.iot.ui.configure.DeviceConfigureActivity;
import com.espressif.iot.user.IEspUser;
import com.espressif.iot.user.builder.BEspUser;
import com.espressif.iot.util.EspStrings;

public class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener
{
    private static final String KEY_ACCOUNT_AUTO_LOGIN = "account_auto_login";
    private static final String KEY_AUTO_REFRESH_DEVICE = "device_auto_refresh";
    private static final String KEY_AUTO_CONFIGURE_DEVICE = "device_auto_configure";
    private static final String KEY_VERSION_NAME = "version_name";
    private static final String KEY_VERSION_UPGRADE = "version_upgrade";
    private static final String KEY_VERSION_LOG = "version_log";
    
    private static final String DEFAULT_VERSION_LOG_URL = "file:///android_asset/html/en_us/update.html";
    /**
     * The url for WebView
     */
    private static final String VERSION_LOG_URL = "file:///android_asset/html/%locale/update.html";
    /**
     * The path for AssetManager
     */
    private static final String VERSION_LOG_PATH = "html/%locale/update.html";
    
    private CheckBoxPreference mAutoLoginPre;
    private ListPreference mAutoRefreshDevicePre;
    private ListPreference mAutoConfigureDevicePre;
    private Preference mVersionNamePre;
    private Preference mVersionUpgradePre;
    private Preference mVersionLogPre;
    
    private IEspUser mUser;
    
    private UpgradeApkTask mUpgradeApkTask;
    
    private SharedPreferences mShared;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.settings);
        
        mUser = BEspUser.getBuilder().getInstance();
        mShared = getActivity().getSharedPreferences(EspStrings.Key.SETTINGS_NAME, Context.MODE_PRIVATE);
        
        // About Account
        Preference mAccountCatgory = findPreference("esp_settings_account_category");
        String userEmail = mUser.getUserEmail();
        mAccountCatgory.setTitle(getString(R.string.esp_settings_account, userEmail));
        
        mAutoLoginPre = (CheckBoxPreference)findPreference(KEY_ACCOUNT_AUTO_LOGIN);
        mAutoLoginPre.setChecked(mUser.isAutoLogin());
        mAutoLoginPre.setOnPreferenceChangeListener(this);
        
        // About Device
        mAutoRefreshDevicePre = (ListPreference)findPreference(KEY_AUTO_REFRESH_DEVICE);
        String autoRefreshTime = "" + mShared.getLong(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_REFRESH, 0);
        mAutoRefreshDevicePre.setValue(autoRefreshTime);
        mAutoRefreshDevicePre.setSummary(mAutoRefreshDevicePre.getEntry());
        mAutoRefreshDevicePre.setOnPreferenceChangeListener(this);
        
        mAutoConfigureDevicePre = (ListPreference)findPreference(KEY_AUTO_CONFIGURE_DEVICE);
        int defaultAutoConfigureValue = DeviceConfigureActivity.DEFAULT_AUTO_CONFIGRUE_VALUE;
        String autoConfigureValue =
            "" + mShared.getInt(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_CONFIGURE, defaultAutoConfigureValue);
        mAutoConfigureDevicePre.setValue(autoConfigureValue);
        mAutoConfigureDevicePre.setSummary(mAutoConfigureDevicePre.getEntry());
        mAutoConfigureDevicePre.setOnPreferenceChangeListener(this);
        
        // About Version
        mVersionNamePre = findPreference(KEY_VERSION_NAME);
        String versionName = EspApplication.sharedInstance().getVersionName();
        mVersionNamePre.setSummary(versionName);
        
        mVersionUpgradePre = findPreference(KEY_VERSION_UPGRADE);
        if (mVersionUpgradePre != null && EspApplication.GOOGLE_PALY_VERSION)
        {
            getPreferenceScreen().removePreference(mVersionUpgradePre);
        }
        
        mVersionLogPre = findPreference(KEY_VERSION_LOG);
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        
        if (mUpgradeApkTask != null)
        {
            mUpgradeApkTask.cancel(true);
        }
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
    {
        if (preference == mVersionUpgradePre)
        {
            updateApk();
            return true;
        }
        else if (preference == mVersionLogPre)
        {
            showLogDialog();
            return true;
        }
        
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference == mAutoLoginPre)
        {
            boolean autoLogin = (Boolean)newValue;
            mUser.saveUserInfoInDB(false, autoLogin);
            return true;
        }
        else if (preference == mAutoRefreshDevicePre)
        {
            String time = newValue.toString();
            mAutoRefreshDevicePre.setValue(time);
            mAutoRefreshDevicePre.setSummary(mAutoRefreshDevicePre.getEntry());
            mShared.edit().putLong(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_REFRESH, Long.parseLong(time)).commit();
            return true;
        }
        else if (preference == mAutoConfigureDevicePre)
        {
            String value = newValue.toString();
            mAutoConfigureDevicePre.setValue(value);
            mAutoConfigureDevicePre.setSummary(mAutoConfigureDevicePre.getEntry());
            mShared.edit().putInt(EspStrings.Key.SETTINGS_KEY_DEVICE_AUTO_CONFIGURE, Integer.parseInt(value)).commit();
            return true;
        }
        
        return false;
    }
    
    private void updateApk()
    {
        ConnectivityManager cm = (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE)
        {
            // Show dialog to hint using mobile data now
            new AlertDialog.Builder(getActivity()).setTitle(R.string.esp_upgrade_apk_mobile_data_title)
                .setMessage(R.string.esp_upgrade_apk_mobile_data_msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        executeUpgradeApkTask();
                    }
                })
                .show();
        }
        else
        {
            executeUpgradeApkTask();
        }
    }
    
    private void executeUpgradeApkTask()
    {
        mUpgradeApkTask = new UpgradeApkTask();
        mUpgradeApkTask.execute();
    }
    
    private class UpgradeApkTask extends AsyncTask<Void, Integer, EspUpgradeApkResult>
    {
        @Override
        protected void onPreExecute()
        {
            mVersionUpgradePre.setEnabled(false);
        }
        
        @Override
        protected EspUpgradeApkResult doInBackground(Void... arg0)
        {
            return EspBaseApiUtil.upgradeApk(mUpdateListener);
        }
        
        @Override
        protected void onPostExecute(EspUpgradeApkResult result)
        {
            mVersionUpgradePre.setEnabled(true);
            
            switch (result)
            {
                case UPGRADE_COMPLETE:
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_complete);
                    break;
                case DOWNLOAD_FAILED:
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_download_failed);
                    break;
                case LOWER_VERSION:
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_lower_version);
                    break;
                case NOT_FOUND:
                    mVersionUpgradePre.setSummary(R.string.esp_upgrade_apk_status_not_found);
                    break;
            }
        }
        
        @Override
        protected void onProgressUpdate(Integer... values)
        {
            int percent = values[0];
            mVersionUpgradePre.setSummary(getString(R.string.esp_upgrade_apk_downloading, percent));
        }
        
        /**
         * Update download progress
         */
        private ProgressUpdateListener mUpdateListener = new ProgressUpdateListener()
        {
            
            @Override
            public void onProgress(double percent)
            {
                int per = (int)(percent * 100);
                publishProgress(per);
            }
            
        };
    }
    
    /**
     * Show update log
     */
    private void showLogDialog()
    {
        /*
         * check for the full language + country resource, if not there, check for the only language resource, if not
         * there again, use default(en_us) language log
         */
        boolean isLogFileExist;
        Locale locale = Locale.getDefault();
        String languageCode = locale.getLanguage().toLowerCase(Locale.US);
        String countryCode = locale.getCountry().toLowerCase(Locale.US);
        
        String folderName = languageCode + "_" + countryCode;
        String path = VERSION_LOG_PATH.replace("%locale", folderName);
        // check full language + country resource
        isLogFileExist = isAssetFileExist(path);
        
        if (!isLogFileExist)
        {
            folderName = languageCode;
            path = VERSION_LOG_PATH.replace("%locale", folderName);
            
            // check the only language resource
            isLogFileExist = isAssetFileExist(path);
        }
        
        String url;
        if (isLogFileExist)
        {
            url = VERSION_LOG_URL.replaceAll("%locale", folderName);
        }
        else
        {
            url = DEFAULT_VERSION_LOG_URL;
        }
        
        WebView webview = new WebView(getActivity());
        webview.loadUrl(url);
        
        new AlertDialog.Builder(getActivity()).setView(webview).show();
    }
    
    /**
     * Check whether the log file exist
     * @param path
     * @return
     */
    private boolean isAssetFileExist(String path)
    {
        boolean result = true;
        
        final AssetManager am = getActivity().getAssets();
        InputStream is = null;
        try
        {
            is = am.open(path);
        }
        catch (Exception ignored)
        {
            result = false;
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (Exception ignored)
                {
                }
            }
        }
        
        return result;
    }
}
