/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.theme;

import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;

import com.androlua.LuaApplication;
import com.androlua.LuaBitmap;
import com.androlua.LuaUtil;
import com.osfans.trime.BuildConfig;
import com.osfans.trime.Config;
import com.osfans.trime.Key;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeSchema;
import com.osfans.trime.enums.InlineModeType;
import com.osfans.trime.util.Function;

import org.luaj.Globals;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.ResourceFinder;
import org.luaj.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThemeManager {

    private static final int mCandidateHeight = 48;
    private static final int mKeyboardHeight = 240;

    private static Globals mGlobals;
    private static LuaTable mColor;
    private static Style mStyle;
    private static Vibrator vibrator;
    private static SoundPool mSoundPool;
    private static InlineModeType mInlineModeType=null;

    public static ResourceFinder getFinder() {
        return mResourceFinder;
    }
    public static Globals getGlobals() {
        return mGlobals;
    }

    public static void vibrate(VibrationEffect ve) {
        if (vibrator == null) {
            Context context = LuaApplication.getInstance();
            if (context != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    vibrator = vibratorManager.getDefaultVibrator();
                } else {
                    vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                }
            }
        }
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(ve);
            }
        }
    }

    private final static ResourceFinder mResourceFinder = new ResourceFinder() {
        @Override
        public InputStream findResource(String name) {
            if (TextUtils.isEmpty(name)) {
                try {
                    return LuaApplication.getInstance().getAssets().open("themes/default/main.lua");
                } catch (Exception ioe) {
                    ioe.printStackTrace();
                }
                return null;
            }
            try {
                if (name.startsWith("themes/default/"))
                    return LuaApplication.getInstance().getAssets().open(name);
            } catch (Exception ioe) {
                ioe.printStackTrace();
            }
            try {
                if (new File(name).exists())
                    return new FileInputStream(name);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                File f = new File(Config.getThemeDir(), Config.getTheme() + "/" + name);
                if (f.exists())
                    return new FileInputStream(f);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!name.endsWith(".lua"))
                return null;
            /*try {
                return LuaApplication.getInstance().getAssets().open("themes/default/"+name);
            } catch (Exception ioe) {
                ioe.printStackTrace();
            }*/
            return null;
        }

        @Override
        public String findFile(String filename) {
            if (TextUtils.isEmpty(filename))
                return null;
            if (filename.startsWith("/"))
                return filename;
            return new File(Config.getThemeDir(), filename).getAbsolutePath();
        }
    };

    public static void setTheme(String name) {
        mStyle = null;
        clearSound();
        Config.setTheme(name);
        mGlobals = JsePlatform.standardGlobals();
        mGlobals.finder = mResourceFinder;
        try {
            LuaValue func = mGlobals.loadfilex("main.lua");
            if (func.isfunction()) {
                func.call();
            } else {
                sendMsg("setTheme " + func.tojstring());
                func = mGlobals.loadfilex("themes/default/main.lua");
                if (func.isfunction())
                    func.call();
            }
        } catch (Exception e) {
            sendMsg("setTheme " + e.toString());
        }
        String styleName = Function.loadString(LuaApplication.getInstance(), Config.getStyleKey(name), mGlobals.get("style").optjstring("light"));
        setStyle(styleName);
        Key.presetKeys = getPresetKeys();
        //Style candidate = getStyle().getStyle("candidate");
        //Rime.setRimeOption("_hide_candidate",!candidate.get("show").optboolean(true));
        //Style comment = getStyle().getStyle("candidate").getStyle("comment");
        //Rime.setRimeOption("_hide_comment",!comment.get("show").optboolean(true));

    }

    public static void sendMsg(String s) {
        TrimeService trime = TrimeService.getInstance();
        if (trime != null)
            trime.sendMsg(s);
    }

    public static void setStyle(String name) {
        mStyle = null;
        mInlineModeType=null;
        clearSound();
        Config.setStyle(name);
        mColor = new LuaTable(mGlobals);
        LuaTable mt = new LuaTable(mGlobals);
        mt.set("__index", mGlobals);
        mColor.setmetatable(mt);
        String path = "styles/" + name + "/main.lua";
        try {
            LuaValue func = mGlobals.loadfilex(path, mColor);
            if (func.isfunction()) {
                func.call();
                return;
            } else {
                sendMsg("setStyle " + func.tojstring());
            }
        } catch (Exception e) {
            sendMsg("setStyle " + e);
        }
        try {
            LuaValue func = mGlobals.loadfilex("themes/default/styles/light/main.lua", mColor);
            if (func.isfunction()) {
                func.call();
            }
        } catch (Exception e) {
            Log.e("theme", "setStyle: " + e);
        }
    }

    public static Style getStyle() {
        if (mStyle == null)
            mStyle = new Style(mColor);
        return mStyle;
    }

    public static int getHeight() {
        if(Rime.getRimeOption("_hide_candidate"))
            return getStyle().getSize("height", mCandidateHeight + mKeyboardHeight) - getStyle().getStyle("keyboard").getSize("height", mKeyboardHeight) - getStyle().getStyle("candidate").getSize("height", mCandidateHeight) + getKeyboardHeight();
        return getStyle().getSize("height", mCandidateHeight + mKeyboardHeight) - getStyle().getStyle("keyboard").getSize("height", mKeyboardHeight) - getStyle().getStyle("candidate").getSize("height", mCandidateHeight) + getKeyboardHeight() + getCandidateHeight();
    }

    public static int getContentHeight() {
        if(Rime.getRimeOption("_hide_candidate"))
            return getKeyboardHeight();
        return getCandidateHeight() + getKeyboardHeight();
    }

    public static int getRawContentHeight() {
        if(Rime.getRimeOption("_hide_candidate"))
            return getStyle().getStyle("keyboard").getSize("height", mKeyboardHeight);
        return getStyle().getStyle("keyboard").getSize("height", mKeyboardHeight) + getStyle().getStyle("candidate").getSize("height", mCandidateHeight);
    }

    public static int getCandidateHeight() {
        return (int) (getStyle().getStyle("candidate").getSize("height", mCandidateHeight) * Math.min(1, Config.getKeyboardHeightScale()));
    }

    public static int getKeyboardHeight() {
        return (int) (getStyle().getStyle("keyboard").getSize("height", mKeyboardHeight) * Config.getKeyboardHeightScale());
    }
    public static InlineModeType getInlinePreedit() {
        if(mInlineModeType!=null)
            return mInlineModeType;
        switch (mStyle.getStyle("preedit").getString("inline", "none")) {
            case "preview":
            case "true":
                mInlineModeType = InlineModeType.INLINE_PREVIEW;
                break;
            case "preedit":
            case "composition":
                mInlineModeType =  InlineModeType.INLINE_COMPOSITION;
                break;
            case "input":
                mInlineModeType =  InlineModeType.INLINE_INPUT;
                break;
            default:
                mInlineModeType =  InlineModeType.INLINE_NONE;
                break;
        }
        return mInlineModeType;
    }

    private static final DisplayMetrics mDisplayMetrics = Resources.getSystem().getDisplayMetrics();

    public static int dp2px(float f) {
        return (int) (TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, f, mDisplayMetrics));
    }


    public static LuaValue getPresetKeys() {
        LuaTable keys = mGlobals.get("preset_keys").opttable(new LuaTable());
        LuaTable env = new LuaTable();
        try {
            env.setmetamethod("__index", mGlobals);
            LuaValue func = mGlobals.loadfilex("themes/default/main.lua", env);
            if (func.isfunction())
                func.call();
        } catch (Exception e) {
            e.printStackTrace();
            return keys;
        }
        if (env.rawget("preset_keys").istable())
            keys.setmetamethod("__index", env.get("preset_keys").opttable(new LuaTable()));
        return keys;
    }

    public static String getActionLabel(String key, String def) {
        LuaValue action = mGlobals.get("action_labels");
        if (!action.istable())
            return def;
        return action.get(key).optjstring(def);
    }

    public static int getDialogTheme() {
        if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
            return android.R.style.Theme_DeviceDefault_Dialog_Alert;
        } else {
            return android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        }
    }

    public static String getKeyboard(String id) {
        String k = Config.getKeyboard();
        Log.w("theme", "getKeyboard:Config " + id + ";" + k);
        if (!TextUtils.isEmpty(k))
            return k;
        LuaValue fun = mGlobals.get("get_keyboard");
        if (fun.isfunction()) {
            try {
                String alphabet = "abcdefghijklmnopqrstuvwxyz";
                try {
                    RimeSchema schema = new RimeSchema(id);
                    alphabet = schema.getAlphabet();
                } catch (Exception e) {
                    File f = new File(Config.getUserDataDir(), id + ".schema.yaml");
                    if (f.exists()) {
                        String input = new String(LuaUtil.readAll(f.getAbsolutePath()));
                        String regex = "alphabet:\\s*(.*)";
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(input);
                        if (matcher.find()) {
                            // matcher.group(1) 提取第一个括号内的内容
                            String result = matcher.group(1);
                            if (!TextUtils.isEmpty(result)) {
                                alphabet = result;
                                Log.w("theme", "getKeyboard:alphabet " + alphabet);
                            }
                        }
                    }

                }
                LuaValue ret = fun.call(LuaValue.valueOf(id), LuaValue.valueOf(alphabet));
                Log.w("theme", "getKeyboard:ret " + ret);
                if (ret.isstring())
                    id = ret.tojstring();
            } catch (Exception e) {
                if (BuildConfig.DEBUG)
                    e.printStackTrace();
            }
        }
        return id;
    }

    public static int loadSound(String soundPath) {
        Log.w("theme", "loadSound: " + soundPath);
        if (mSoundPool == null) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA) // 提示音类型
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mSoundPool = new SoundPool.Builder()
                    .setMaxStreams(5) // 允许同时播放 5 个声音（防止连打时截断）
                    .setAudioAttributes(attributes)
                    .build();
            mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    //if (status != 0) {
                    // status 为 0 表示成功，非 0 表示失败
                    Log.e("SoundHelper", "加载失败！ID: " + sampleId + " 状态码: " + status);
                    //}
                }
            });
        }
        // 检查文件是否存在
        java.io.File file = new java.io.File(soundPath);
        if (!file.exists()) {
            Log.e("SoundHelper", "文件根本不存在: " + soundPath);
            return -1;
        }
        return mSoundPool.load(soundPath, 1);
    }

    public static void play(int soundId) {
        if (mSoundPool != null && soundId > 0) {
            // 参数依次为：左声道、右声道、优先级、循环、速率
            mSoundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    public static void clearSound() {
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
    }

    public static Object callFunction(String s, Object... args) {
        if(mGlobals==null)
            return null;
        LuaValue f = mGlobals.get(s);
        if(f.isfunction()){
            return f.jcall(args);
        }
        return null;
    }
}
