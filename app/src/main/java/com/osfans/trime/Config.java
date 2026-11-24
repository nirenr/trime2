/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;

import com.androlua.LuaApplication;
import com.osfans.trime.core.Rime;
import com.osfans.trime.theme.ThemeManager;
import com.osfans.trime.util.Function;

import java.io.File;
import java.io.FilenameFilter;

public class Config {

    private static String mGroup = null;
    private static String mTheme = null;
    private static String mStyle = null;
    private static String mKeyboard;


    public static boolean isSpeakKeyLabel() {
        return false;
    }


    public static String getDataDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "rime");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getGroup() {
        if (mGroup == null)
            mGroup = Function.loadString(LuaApplication.getInstance(), "select_schema_group", "default");
        return mGroup;
    }

    public static String getUserDataDir() {
        File dir = new File(getDataDir(), "schemas/" + getGroup());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getScriptsDir() {
        File dir = new File(getDataDir(), "scripts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getScriptsPath(String name) {
        File f = new File(getThemeDir(getTheme()), "scripts/" + name);
        if (f.exists())
            return f.getAbsolutePath();
        return new File(getScriptsDir(), name).getAbsolutePath();
    }

    public static String[] getGroups() {
        File dir = new File(getDataDir(), "schemas");
        String[] list = dir.list();
        if (list == null)
            list = new String[0];
        return list;
    }

    public static void setGroup(String s) {
        mGroup = s;
        Function.saveString(LuaApplication.getInstance(), "select_schema_group", s);
    }

    public static String getTheme() {
        if (mTheme == null)
            mTheme = Function.loadString(LuaApplication.getInstance(), "theme", "default");
        return mTheme;
    }

    public static void setTheme(String s) {
        mTheme = s;
        Function.saveString(LuaApplication.getInstance(), "theme", s);
    }

    public static String getThemeDir() {
        File dir = new File(getDataDir(), "themes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getThemeDir(String s) {
        File dir = new File(getThemeDir(), s);
        return dir.getAbsolutePath();
    }

    public static String getThemePath(String s) {
        File dir = new File(getThemeDir(getTheme()), s);
        return dir.getAbsolutePath();
    }

    public static String getThemePath(String d, String s) {
        File dir = new File(getThemeDir(d), s);
        return dir.getAbsolutePath();
    }

    public static String getKeyboardDir() {
        File dir = new File(getThemeDir(getTheme()), "keyboards");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String[] getStyles() {
        File dir = new File(getStyleDir());
        String[] list = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name + "/main.lua").exists();
            }
        });
        if (list == null)
            list = new String[0];

        return list;
    }

    public static String getStyleKey(String s) {
        if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
            return s + "_style_night";
        } else {
            return s + "_style";
        }
    }

    public static String getStyleKey() {
        if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
            return getTheme() + "_style_night";
        } else {
            return getTheme() + "_style";
        }
    }

    public static void setStyle(String s) {
        Function.saveString(LuaApplication.getInstance(), getStyleKey(), s);
    }

    public static String getStyle() {
        if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
            return Function.loadString(LuaApplication.getInstance(), getTheme() + "_style_night", Function.loadString(LuaApplication.getInstance(), getTheme() + "_style", "light"));
        } else {
            return Function.loadString(LuaApplication.getInstance(), getTheme() + "_style", "light");
        }
    }

    public static String getStyleDir() {
        File dir = new File(getThemeDir(getTheme()), "styles");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getStyleDir(String s) {
        File dir = new File(getStyleDir(), s);
        return dir.getAbsolutePath();
    }

    public static String getStylePath(String d, String s) {
        File dir = new File(getStyleDir(d), s);
        return dir.getAbsolutePath();
    }

    public static String getStylePath(String s) {
        File dir = new File(getStyleDir(getStyle()), s);
        return dir.getAbsolutePath();
    }
    public static int getDialogTheme(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return android.R.style.Theme_Material_Dialog;
        }
        if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
            return android.R.style.Theme_DeviceDefault_Dialog_Alert;
        } else {
            return android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        }
    }

    public static String[] getThemes() {
        File dir = new File(getThemeDir());
        String[] list = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name + "/main.lua").exists();
            }
        });
        if (list == null)
            list = new String[0];
        return list;
    }

    public static String getImagePath(String s) {
        return new File(getThemeDir(getTheme()), "images/" + s).getAbsolutePath();
    }

    public static String findImagePath(String s) {
        File f = new File(getStylePath(s));
        if (f.exists())
            return f.getAbsolutePath();
        f = new File(getThemePath("images/" + s));
        if (f.exists())
            return f.getAbsolutePath();
        f = new File(getDataDir(), "images/" + s);
        if (f.exists())
            return f.getAbsolutePath();
        return "";
    }

    public static String getSoundPath(String s) {
        return new File(getThemeDir(getTheme()), "sounds/" + s).getAbsolutePath();
    }

    public static String getFontPath(String s) {
        File f = new File(getStylePath(s));
        if (f.exists())
            return f.getAbsolutePath();
        f = new File(getThemePath("fonts/" + s));
        if (f.exists())
            return f.getAbsolutePath();
        f = new File(getDataDir(), "fonts/" + s);
        if (f.exists())
            return f.getAbsolutePath();
        return "null";
    }

    public static String getKeyboard() {
        mKeyboard = Function.loadString(LuaApplication.getInstance(), getTheme() + "_" + Rime.getCurrentRimeSchema() + "_keyboard", "");
        Log.w("config", "getKeyboard: " + Rime.getCurrentRimeSchema() + ";" + mKeyboard);
        setKeyboard(".default", mKeyboard);
        return mKeyboard;
    }

    public static String getKeyboard(String id) {
        //if (mKeyboard == null)
        return Function.loadString(LuaApplication.getInstance(), getTheme() + "_" + id + "_keyboard", "");
    }

    public static void setKeyboard(String s) {
        mKeyboard = s;
        Function.saveString(LuaApplication.getInstance(), getTheme() + "_" + Rime.getCurrentRimeSchema() + "_keyboard", s);
    }

    public static void setKeyboard(String id, String s) {
        Function.saveString(LuaApplication.getInstance(), getTheme() + "_" + id + "_keyboard", s);
    }

    public static String[] getKeyboards() {
        return new File(getKeyboardDir()).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".lua") && !com.osfans.trime.editor.LuaPatchManager.isPatch(name);
            }
        });
    }


    public static String getKeyboardPath(String keyboardId) {
        return new File(getKeyboardDir(), keyboardId + ".lua").getAbsolutePath();
    }

    public static int getSmallModeGravity() {
        return Function.getPref(LuaApplication.getInstance()).getInt("small_mode_gravity", Gravity.LEFT);
    }

    public static void setSmallModeGravity(int g) {
        Function.getPref(LuaApplication.getInstance()).edit().putInt("small_mode_gravity", g).commit();
    }

    public static boolean isSmallMode() {
        return Rime.getRimeOption("small_mode");
        //return Function.getPref(LuaApplication.getInstance()).getBoolean("small_mode", false)|| isFloatMode();
    }

    public static void setSmallMode(boolean b) {
        Function.getPref(LuaApplication.getInstance()).edit().putBoolean("small_mode", b).commit();
    }


    public static int getSmallModeWidth() {
        return Function.getPref(LuaApplication.getInstance()).getInt(getKey("small_mode_width"), Function.getPref(LuaApplication.getInstance()).getInt("small_mode_width", (int) (TrimeService.getInstance().getMaxWidth() * 0.8)));
    }

    public static void setSmallModeWidth(int w) {
        Function.getPref(LuaApplication.getInstance()).edit().putInt(getKey("small_mode_width"), w).commit();
    }

    public static void setFloatMode(boolean b) {
        Function.getPref(LuaApplication.getInstance()).edit().putBoolean("float_mode", b).commit();
    }

    public static boolean isFloatMode() {
        return Rime.getRimeOption("float_mode");
        //return Function.getPref(LuaApplication.getInstance()).getBoolean("float_mode", false);
    }

    public static void setFloatModeX(float x) {
        Function.getPref(LuaApplication.getInstance()).edit().putFloat(getKey("float_mode_x"), x).commit();
    }

    public static void setFloatModeY(float y) {
        Function.getPref(LuaApplication.getInstance()).edit().putFloat(getKey("float_mode_y"), y).commit();
    }

    public static float getFloatModeX() {
        return Function.getPref(LuaApplication.getInstance()).getFloat(getKey("float_mode_x"),  Function.getPref(LuaApplication.getInstance()).getFloat("float_mode_x", 0));
    }

    public static float getFloatModeY() {
        return Function.getPref(LuaApplication.getInstance()).getFloat(getKey("float_mode_y"), Function.getPref(LuaApplication.getInstance()).getFloat("float_mode_y", 0));
    }

    public static void setKeyboardHeightScale(float height) {
        height = getKeyboardHeightScale() * height;
        if (height < 0.5)
            height = 0.5f;
        else if (height > 2) {
            height = 2f;
        }
        Function.getPref(LuaApplication.getInstance()).edit().putFloat(getKey("keyboard_height"), height).commit();
    }

    public static float getKeyboardHeightScale() {
        int height = LuaApplication.getInstance().getResources().getDisplayMetrics().heightPixels;
        float scale = Function.getPref(LuaApplication.getInstance()).getFloat(getKey("keyboard_height"), Function.getPref(LuaApplication.getInstance()).getFloat("keyboard_height", 1f));
        int raw=ThemeManager.getRawContentHeight();
        if(raw*scale>height*0.95){
            return height*0.95f/raw;
        }
        return scale;
    }

    private static String getKey(String s) {
        StringBuilder buf = new StringBuilder(s);
        if (LuaApplication.getInstance().getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE)
            buf.append("_landscape");
        if (isFloatMode())
            buf.append("_float");
        else if (isSmallMode())
            buf.append("_small");
        return buf.toString();
    }
    private static boolean _hide_comment;
    public static void set_hide_comment(boolean b) {
        _hide_comment=b;
    }

    public static boolean is_hide_comment() {
        return _hide_comment;
    }

    private static boolean _hide_key_hint;
    public static void set_hide_key_hint(boolean b) {
        _hide_key_hint=b;
    }

    public static boolean is_hide_key_hint() {
        return _hide_key_hint;
    }

}
