/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.icu.util.Calendar;
import android.icu.util.ULocale;
import android.net.Uri;
import android.os.Build.*;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.androlua.LuaApplication;
import com.osfans.trime.BuildConfig;
import com.osfans.trime.Config;
import com.osfans.trime.TrimeService;
import com.osfans.trime.VivoGpt;
import com.osfans.trime.core.DataManager;
import com.osfans.trime.core.Rime;
import com.osfans.trime.dialog.DeployDialog;

import org.luaj.LuaTable;

import java.io.File;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.noties.markwon.Markwon;

/**
 * 實現打開指定程序、打開 輸入法全局設置對話框等功能
 */
public class Function {
    private static String TAG = Function.class.getSimpleName();
    private static SparseArray<String> sApplicationLaunchKeyCategories;
    // 2. 创建 Markwon 实例（传入 Context）
    private static final Markwon markwon = Markwon.create(LuaApplication.getInstance());

    static {
        sApplicationLaunchKeyCategories = new SparseArray<String>();
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_EXPLORER, "android.intent.category.APP_BROWSER");
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_ENVELOPE, "android.intent.category.APP_EMAIL");
        sApplicationLaunchKeyCategories.append(207, "android.intent.category.APP_CONTACTS");
        sApplicationLaunchKeyCategories.append(208, "android.intent.category.APP_CALENDAR");
        sApplicationLaunchKeyCategories.append(209, "android.intent.category.APP_EMAIL");
        sApplicationLaunchKeyCategories.append(210, "android.intent.category.APP_CALCULATOR");
    }

    @TargetApi(VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public static boolean openCategory(Context context, int keyCode) {
        String category = sApplicationLaunchKeyCategories.get(keyCode);
        if (category != null) {
            Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            try {
                context.startActivity(intent);
            } catch (Exception ex) {
                Log.e(TAG, "Start Activity Exception" + ex);
            }
            return true;
        }
        return false;
    }

    private static void startIntent(Context context, String arg) {
        Intent intent;
        try {
            if (arg.indexOf(':') >= 0) {
                // The argument is a URI.  Fully parse it, and use that result
                // to fill in any data not specified so far.
                intent = Intent.parseUri(arg, Intent.URI_INTENT_SCHEME);
            } else if (arg.indexOf('/') >= 0) {
                // The argument is a component name.  Build an Intent to launch
                // it.
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(ComponentName.unflattenFromString(arg));
            } else {
                // Assume the argument is a package name.
                intent = context.getPackageManager().getLaunchIntentForPackage(arg);
            }
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            context.startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Start Activity Exception" + ex);
        }
    }

    private static void startIntent(Context context, String action, String arg) {
        action = "android.intent.action." + action.toUpperCase(Locale.getDefault());
        try {
            Intent intent = new Intent(action);
            switch (action) {
                case Intent.ACTION_WEB_SEARCH:
                case Intent.ACTION_SEARCH:
                    if (arg.startsWith("http")) { //web_search無法直接打開網址
                        startIntent(context, arg);
                        return;
                    }
                    intent.putExtra(SearchManager.QUERY, arg);
                    break;
                case Intent.ACTION_SEND: //分享文本
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, arg);
                    break;
                default:
                    if (!TextUtils.isEmpty(arg)) intent.setData(Uri.parse(arg));
                    break;
            }
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            context.startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Start Activity Exception" + ex);
        }
    }


    public static String getDate(String option) {
        String s = "";
        String locale = "";
        if (option.contains("@")) {
            String[] ss = option.split(" ", 2);
            if (ss.length == 2 && ss[0].contains("@")) {
                locale = ss[0];
                option = ss[1];
            } else if (ss.length == 1) {
                locale = ss[0];
                option = "";
            }
        }
        if (VERSION.SDK_INT >= VERSION_CODES.N && !TextUtils.isEmpty(locale)) {
            ULocale ul = new ULocale(locale);
            Calendar cc = Calendar.getInstance(ul);
            android.icu.text.DateFormat df;
            if (TextUtils.isEmpty(option)) {
                df = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, ul);
            } else {
                df = new android.icu.text.SimpleDateFormat(option, ul);
            }
            s = df.format(cc, new StringBuffer(256), new FieldPosition(0)).toString();
        } else {
            s = new SimpleDateFormat(option, Locale.getDefault()).format(new Date()); //時間
        }
        return s;
    }

    public static boolean setAll(SharedPreferences preferences, Map<String, Object> map) {
        Set<Map.Entry<String, Object>> sets = map.entrySet();
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : sets) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            if (newValue instanceof String)
                editor.putString(key, (String) newValue);
            else if (newValue instanceof Integer)
                editor.putInt(key, (Integer) newValue);
            else if (newValue instanceof Long)
                editor.putLong(key, (Long) newValue);
            else if (newValue instanceof Float)
                editor.putFloat(key, (Float) newValue);
            else if (newValue instanceof Set)
                editor.putStringSet(key, (Set<String>) newValue);
            else if (newValue instanceof Boolean)
                editor.putBoolean(key, (Boolean) newValue);
        }
        return editor.commit();
    }

    public static String getVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAppAvailable(Context context, String app) {
        final PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
        if (pinfo != null) {
            for (int i = 0; i < pinfo.size(); i++) {
                String pn = pinfo.get(i).packageName;
                if (pn.equals(app)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static SharedPreferences getPref(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isDiffVer(Context context) {
        String version = getVersion(context);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String pref_ver = pref.getString("version_name", "");
        boolean isDiff = !version.contentEquals(pref_ver);
        if (isDiff) {
            SharedPreferences.Editor edit = pref.edit();
            edit.putString("version_name", version);
            edit.apply();
        }
        return isDiff;
    }

    public static void printStackTrace(String text) {
        if (!BuildConfig.DEBUG)
            return;
        try {
            throw new RuntimeException(text + "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getUserDataDir(Context context) {
        return DataManager.getUserDataDir().getAbsolutePath();
    }

    public static String handle(TrimeService context, String command, Object... option) {
        String s = null;
        if (command == null)
            return s;
        String path = Config.getScriptsPath(command);
        Log.w(TAG, "handle: "+path );
        if (new File(path).exists()) {
            Object ret = context.doFile(path, option);
            if (ret == null)
                return null;
            if (ret instanceof LuaTable) {
                context.setCandidates(new ArrayList<String>((Collection<? extends String>) ((LuaTable) ret).checktable().values()));
                return null;
            }
            return ret.toString();
        }
        return handle(context, command, "");
    }

    public static String handle(TrimeService context, String command, String option) {
        String s = null;
        if (command == null)
            return s;
        String path = Config.getScriptsPath(command);
        Log.w(TAG, "handle: "+path );
        if (new File(path).exists()) {
            Object ret = context.doFile(path, option);
            if (ret == null)
                return null;
            if (ret instanceof LuaTable) {
                //context.setCandidates(new ArrayList<String>(((LuaTable) ret).values()));
                return null;
            }
            return ret.toString();
        }
        switch (command) {
             case "gpt": {
                if (TextUtils.isEmpty(option)) {
                    Toast.makeText(context, "输入内容不能为空，请输入一些文字后重试", Toast.LENGTH_SHORT).show();
                    return null;
                }
                Toast.makeText(context, "正在生成，请稍后...", Toast.LENGTH_SHORT).show();
                 // 获取当前服务所在的屏幕或默认屏幕
                 DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                 Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

// 为特定屏幕创建 Context
                 Context displayContext = context.createDisplayContext(defaultDisplay);
                 EditText tv = new EditText(displayContext);
                if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
                    tv.setTextColor(0xffffffff);
                } else {
                    tv.setTextColor(0xff000000);
                }
                AlertDialog dlg = context.showWidthDialog(new AlertDialog.Builder(displayContext, Config.getDialogTheme())
                        .setTitle(option)
                        .setView(tv)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (!option.startsWith("根据以下内容续写"))
                                    context.getCurrentInputConnection().deleteSurroundingText(option.length(), 0);
                                context.commitText(tv.getText());
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton("重新生成", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                handle(context, command, option);
                            }
                        }).create());
                dlg.getButton(DialogInterface.BUTTON1).setEnabled(false);
                VivoGpt.gpt(option, new HttpUtil.HttpCallback() {
                    @Override
                    public void onDone(HttpUtil.HttpResult result) {
                        context.getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                if (result == null) {
                                    dlg.getButton(DialogInterface.BUTTON1).setEnabled(true);
                                    markwon.setMarkdown(tv, tv.getText().toString());
                                    return;
                                }
                                tv.append(result.text);
                            }
                        });
                    }
                });
                break;
            }
            case "gpt1": {
                if (TextUtils.isEmpty(option)) {
                    Toast.makeText(context, "输入内容不能为空，请输入一些文字后重试", Toast.LENGTH_SHORT).show();
                    return null;
                }
                final ProgressDialog mProgressDialog = new ProgressDialog(context);
                mProgressDialog.setMessage("正在生成，请稍后...");
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton("后台运行", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                /*Window window = mProgressDialog.getWindow();
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.type = TrimeService.getDialogType();
                window.setAttributes(lp);
                window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                mProgressDialog.show();*/
                TrimeService.getInstance().showWidthDialog(mProgressDialog);
                Toast.makeText(context, "正在生成，请稍后...", Toast.LENGTH_SHORT).show();

                VivoGpt.gpt1(option, new HttpUtil.HttpCallback() {
                    @Override
                    public void onDone(HttpUtil.HttpResult result) {
                        context.getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                mProgressDialog.dismiss();
                                DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                                Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

// 为特定屏幕创建 Context
                                Context displayContext = context.createDisplayContext(defaultDisplay);
                                EditText tv = new EditText(displayContext);
                                if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
                                    tv.setTextColor(0xffffffff);
                                } else {
                                    tv.setTextColor(0xff000000);
                                }
                                markwon.setMarkdown(tv, result.text);
                                //tv.setText(result.text);
                                tv.setShowSoftInputOnFocus(false);
                                context.showWidthDialog(new AlertDialog.Builder(displayContext, Config.getDialogTheme())
                                        .setTitle(option)
                                        .setView(tv)
                                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (!option.startsWith("根据以下内容续写"))
                                                    context.getCurrentInputConnection().deleteSurroundingText(option.length(), 0);
                                                context.commitText(tv.getText());
                                            }
                                        })
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setNeutralButton("重新生成", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                handle(context, command, option);
                                            }
                                        }).create());
                            }
                        });
                    }
                });
                break;
            }
            case "gpt2": {
                if (TextUtils.isEmpty(option)) {
                    Toast.makeText(context, "输入内容不能为空，请输入一些文字后重试", Toast.LENGTH_SHORT).show();
                    return null;
                }
                final ProgressDialog mProgressDialog = new ProgressDialog(context);
                mProgressDialog.setMessage("正在生成，请稍后...");
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton("后台运行", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                /*Window window = mProgressDialog.getWindow();
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.type = Trime.getDialogType();
                window.setAttributes(lp);
                window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                mProgressDialog.show();*/
                TrimeService.getInstance().showWidthDialog(mProgressDialog);
                Toast.makeText(context, "正在生成，请稍后...", Toast.LENGTH_SHORT).show();
                VivoGpt.gpt1(option, new HttpUtil.HttpCallback() {
                    @Override
                    public void onDone(HttpUtil.HttpResult result) {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                            context.commitText(result.text);
                            return;
                        }
                        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

// 为特定屏幕创建 Context
                        Context displayContext = context.createDisplayContext(defaultDisplay);
                        EditText tv = new EditText(displayContext);
                        if ((LuaApplication.getInstance().getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
                            tv.setTextColor(0xffffffff);
                        } else {
                            tv.setTextColor(0xff000000);
                        }
                        //tv.setText(result.text);
                        markwon.setMarkdown(tv, result.text);
                        tv.setShowSoftInputOnFocus(false);

                        context.showWidthDialog(new AlertDialog.Builder(displayContext, Config.getDialogTheme())
                                .setTitle(option)
                                .setView(tv)
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        context.commitText(result.text);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .setNeutralButton("重新生成", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        handle(context, command, option);
                                    }
                                }).create());
                    }
                });
                break;
            }
            case "gpt3": {
                if (TextUtils.isEmpty(option)) {
                    Toast.makeText(context, "输入内容不能为空，请输入一些文字后重试", Toast.LENGTH_SHORT).show();
                    return null;
                }
                Toast.makeText(context, "正在生成，请稍后...", Toast.LENGTH_SHORT).show();
                VivoGpt.gpt(option, new HttpUtil.HttpCallback() {
                    @Override
                    public void onDone(HttpUtil.HttpResult result) {
                        context.getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                if (result == null) {
                                    Toast.makeText(context, "生成完成。", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (!context.isInputViewShown())
                                    return;
                                context.commitText(result.text);
                            }
                        });
                    }
                });
                break;
            }
            case "date":
                s = getDate(option);
                break;
            case "run":
                if (option.startsWith("http")) {
                    try {
                        context.startActivity(new Intent(Intent.ACTION_VIEW).addFlags(FLAG_ACTIVITY_NEW_TASK).setData(Uri.parse(option)));
                    }catch (Exception e){
                        Log.w(TAG, "handle: "+option );
                        e.printStackTrace();
                        Toast.makeText(context,e.toString(),Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                startIntent(context, option); //啓動程序
                break;
            case "deploy":
                new DeployDialog(context).show(context.getToken());
                break;
            case "sync":
                Rime.syncRimeUserData();
                break;
            case "broadcast":
                context.sendBroadcast(new Intent(option)); //廣播
                break;
            case "add_phrase":
                TrimeService.getInstance().addPhrase(option); //新建短语
                Toast.makeText(context,"已添加到短语 "+option,Toast.LENGTH_SHORT).show();
                break;
            case "commit":
                s = option;
                break;
            default:
                startIntent(context, command, option); //其他intent
                break;
        }
        return s;
    }

    public static void showPrefDialog(Context trimeService) {

    }

    public static void saveString(Context context, String id, String s) {
        getPref(context).edit().putString(id,s).apply();
    }

    public static String loadString(Context context, String id, String def) {
        return getPref(context).getString(id,def);
    }

    public static void saveBoolean(Context context, String id, boolean s) {
        getPref(context).edit().putBoolean(id,s).apply();
    }
    public static boolean loadBoolean(Context context, String id, boolean def) {
        return getPref(context).getBoolean(id,def);
    }
}
