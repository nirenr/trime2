package com.androlua;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

import com.osfans.trime.BuildConfig;
import com.osfans.trime.Config;
import com.osfans.trime.core.DataManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class Welcome extends LuaActivity {

    private boolean isUpdata;

    private LuaApplication app;

    private String localDir;

    private long mLastTime;

    private long mOldLastTime;

    private ProgressDialog pd;

    private boolean isVersionChanged;

    private String mVersionName;

    private String mOldVersionName;

    private ArrayList<String> permissions;

    @CallLuaFunction
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView view = new TextView(this);
        view.setText(new String(new char[]{'P', 'o', 'w', 'e', 'r', 'e', 'd', ' ', 'b', 'y', ' ', 'L', 'u', 'a', 'J', '+', '+'}));
        view.setTextColor(0xff888888);
        view.setGravity(Gravity.TOP);
        setContentView(view);
        SharedPreferences info = getSharedPreferences("appInfo", 0);
        boolean first = info.getBoolean("init", false);
        if (!first) {
            Class<?>[] cls = new Class[]{
                    LuaNotificationListenerService.class,
                    LuaAccessibilityService.class,
                    LuaWallpaperService.class,
                    ImportProject.class,
                    LuaAppWidgetProvider.class,
            };
            for (Class<?> c : cls) {
                ComponentName networkActivity = new ComponentName(getApplicationContext(), c);
                PackageManager packageManager = getApplicationContext().getPackageManager();
                int i = packageManager.getComponentEnabledSetting(networkActivity);
                Log.d("luaj", "getComponentEnabledSetting: " + i+" : "+c);
                packageManager.setComponentEnabledSetting(networkActivity, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            info.edit().putBoolean("init", true).apply();
        }


        if (checkResource("welcome.lua")) {
            checkInfo();
            runFunc("welcome", mVersionName, mOldVersionName);
            return;
        }
        app = (LuaApplication) getApplication();
        localDir = app.getLuaDir();
        /*try {
            if (new File(app.getLuaPath("setup.png")).exists())
                getWindow().setBackgroundDrawable(new LuaBitmapDrawable(app, app.getLuaPath("setup.png"), getResources().getDrawable(R.mipmap.ic_launcher)));
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        if (checkInfo()) {
            if (checkPermission())
                return;
            new UpdateTask().execute();
        } else {
            startActivity();
        }
    }

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                permissions = new ArrayList<String>();
                String[] ps2 = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
                for (String p : ps2) {
                    try {
                        checkPermission(p);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (!permissions.isEmpty()) {
                    String[] ps = new String[permissions.size()];
                    permissions.toArray(ps);
                    requestPermissions(ps,
                            0);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public Object doFile(String path, Object... arg) {
        if (checkResource(path))
            super.doFile(path, arg);
        return null;
    }

    @Override
    public String getLuaPath() {
        return "welcome.lua";
    }

    @Override
    protected void initENV() {

    }

    private void checkPermission(String permission) {
        if (checkCallingOrSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (checkResource("welcome.lua")) {
            return;
        }
        new UpdateTask().execute();
    }

    public void startActivity() {
        try {
            InputStream f = getAssets().open("main.lua");
            if (f != null) {
                Intent intent = new Intent(Welcome.this, Main.class);
                if (isVersionChanged) {
                    intent.putExtra("isVersionChanged", isVersionChanged);
                    intent.putExtra("newVersionName", mVersionName);
                    intent.putExtra("oldVersionName", mOldVersionName);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return;
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
            Intent intent = new Intent(Welcome.this, LuaEditorActivity.class);
            if (isVersionChanged) {
                ComponentName networkActivity = new ComponentName(getApplicationContext(), Main.class);
                PackageManager packageManager = getApplicationContext().getPackageManager();
                int i = packageManager.getComponentEnabledSetting(networkActivity);
                Log.d("luaj", "Main: " + i);
                packageManager.setComponentEnabledSetting(networkActivity, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

                intent.putExtra("isVersionChanged", isVersionChanged);
                intent.putExtra("newVersionName", mVersionName);
                intent.putExtra("oldVersionName", mOldVersionName);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        //overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out                                                                                                                 );
        finish();
    }

    public boolean checkInfo() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(this.getPackageName(), 0);
            long lastTime = packageInfo.lastUpdateTime;
            String versionName = packageInfo.versionName;
            SharedPreferences info = getSharedPreferences("appInfo", 0);
            String oldVersionName = info.getString("versionName", "");
            mVersionName = versionName;
            mOldVersionName = oldVersionName;
            if (!versionName.equals(oldVersionName)) {
                SharedPreferences.Editor edit = info.edit();
                edit.putString("versionName", versionName);
                edit.apply();
                isVersionChanged = true;
            }
            long oldLastTime = info.getLong("lastUpdateTime", 0);
            if (oldLastTime != lastTime) {
                SharedPreferences.Editor edit = info.edit();
                edit.putLong("lastUpdateTime", lastTime);
                edit.apply();
                isUpdata = true;
                mLastTime = lastTime;
                mOldLastTime = oldLastTime;
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }


    @SuppressLint("StaticFieldLeak")
    private class UpdateTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String[] p1) {
            // TODO: Implement this method
            onUpdate(mLastTime, mOldLastTime);
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            startActivity();
        }

        private void onUpdate(long lastTime, long oldLastTime) {

            try {
                //LuaUtil.rmDir(new File(localDir),".lua");
                //LuaUtil.rmDir(new File(luaMdDir),".lua");
                DataManager.sync();
                if(!new File(Config.getDataDir()).exists())
                    unApk("assets/themes", Config.getThemeDir());
                //unZipAssets("main.alp", extDir);
            } catch (IOException e) {
                sendMsg(e.getMessage());
            }
        }
    }

    public void unApk(String dir, String extDir) throws IOException {
        int i = dir.length() + 1;
        ZipFile zip = new ZipFile(getApplicationInfo().publicSourceDir);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.indexOf(dir) != 0)
                continue;
            String path = name.substring(i);
            if (entry.isDirectory()) {
                File f = new File(extDir + File.separator + path);
                if (!f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.mkdirs();
                }
            } else {
                String fname = extDir + File.separator + path;
                File ff = new File(fname);
                File temp = new File(fname).getParentFile();
                if (!temp.exists()) {
                    if (!temp.mkdirs()) {
                        throw new RuntimeException("create file " + temp.getName() + " fail");
                    }
                }
                try {
                    if (ff.exists() && entry.getSize() == ff.length() && LuaUtil.getFileMD5(zip.getInputStream(entry)).equals(LuaUtil.getFileMD5(ff)))
                        continue;
                } catch (NullPointerException ignored) {
                }
                FileOutputStream out = new FileOutputStream(extDir + File.separator + path);
                InputStream in = zip.getInputStream(entry);
                byte[] buf = new byte[40960];
                int count = 0;
                while ((count = in.read(buf)) != -1) {
                    out.write(buf, 0, count);
                }
                out.close();
                in.close();
            }
        }
        zip.close();
    }

}
