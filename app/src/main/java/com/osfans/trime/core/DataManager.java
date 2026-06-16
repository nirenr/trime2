// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.androlua.LuaApplication;
import com.androlua.LuaUtil;
import com.osfans.trime.BuildConfig;
import com.osfans.trime.Config;
import com.osfans.trime.TrimeApplication;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class DataManager {

    private static final String DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml";
    private static final String DATA_CHECKSUMS_NAME = "checksums.json";

    private static final String SCHEMA_LIST_CUSTOM_PATCH =
            "patch:\n" +
                    "  schema_list:\n" +
                    "    - schema: luna_pinyin\n" +
                    "    - schema: luna_pinyin_simp";

    private static final ReentrantLock lock = new ReentrantLock();



    // 懒加载 DataDir
    private static final File dataDir;

    static {
        // 初始化 dataDir
        Context context = getAppContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dataDir = context.createDeviceProtectedStorageContext().getDataDir();
        } else {
            dataDir = new File(context.getApplicationInfo().dataDir);
        }
    }

    // 懒加载 AppPrefs
    private static volatile SharedPreferences prefsInstance;

    private DataManager() {
        // 防止实例化
    }

    // 辅助方法：获取 Context，对应 Kotlin 的 import com.osfans.trime.util.appContext
    private static Context getAppContext() {
        // 实际项目中请替换为真实的调用方式，例如 LuaApplication.getInstance() 或 UtilKt.getAppContext()
        return LuaApplication.getInstance();
    }

    private static SharedPreferences getPrefs() {
        if (prefsInstance == null) {
            synchronized (DataManager.class) {
                if (prefsInstance == null) {
                    prefsInstance = PreferenceManager.getDefaultSharedPreferences(getAppContext());
                }
            }
        }
        return prefsInstance;
    }

    public static File getDefaultDataDir() {
        return new File(Environment.getExternalStorageDirectory(), "rime");
    }

    public static File getSharedDataDir() {
        File dir = new File(getAppContext().getExternalFilesDir(null), "shared");
        dir.mkdirs();
        return dir;
    }

    public static File getUserDataDir() {
        File dir = new File(Config.getUserDataDir());
        dir.mkdirs();
        return dir;
    }

    public static File getPrebuiltDataDir() {
        return new File(getSharedDataDir(), "build");
    }

    public static File getStagingDir() {
        return new File(getUserDataDir(), "build");
    }

    /**
     * Return the absolute path of the compiled config file
     * based on given resource id.
     *
     * @param resourceId usually equals the config file name without the extension
     * @return the absolute path of the compiled config file
     */
    public static String resolveDeployedResourcePath(String resourceId) {
        File defaultPath = new File(getStagingDir(), resourceId + ".yaml");
        if (!defaultPath.exists()) {
            File fallbackPath = new File(getPrebuiltDataDir(), resourceId + ".yaml");
            if (fallbackPath.exists()) {
                return fallbackPath.getAbsolutePath();
            }
        }
        return defaultPath.getAbsolutePath();
    }

    public static void sync() {
        try {
            LuaApplication.getInstance().unApk("assets/shared",getSharedDataDir().getAbsolutePath());
            File f = new File(Config.getUserDataDir(), DEFAULT_CUSTOM_FILE_NAME);
            if(!f.exists()&&Config.getTheme().equals("default")){
                LuaUtil.save(f.getAbsolutePath(),
                        "      patch:\n" +
                        "        schema_list:\n" +
                        "          - schema: luna_pinyin\n"
                );
            }
            //if (BuildConfig.DEBUG || Config.getThemes().length==0) {
                LuaApplication.getInstance().unApk("assets/themes", Config.getThemeDir());
            LuaApplication.getInstance().unApk("assets/scripts", Config.getScriptsDir());
            //}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
