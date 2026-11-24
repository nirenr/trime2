/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;

import com.osfans.trime.BuildConfig;
import com.osfans.trime.Config;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.theme.ThemeManager;

import org.luaj.Globals;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.ResourceFinder;
import org.luaj.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class KeyboardDialog implements ResourceFinder {
    private final AlertDialog mDialog;
    private IBinder mWindowToken;

    @Override
    public InputStream findResource(String name) {
        if (TextUtils.isEmpty(name))
            return null;
        try {
            if (new File(name).exists())
                return new FileInputStream(name);
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        try {
            return new FileInputStream(new File(Config.getKeyboardDir(), name));
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        return null;
    }

    @Override
    public String findFile(String filename) {
        if (TextUtils.isEmpty(filename))
            return null;
        if (filename.startsWith("/"))
            return filename;
        return new File(Config.getKeyboardDir(), filename).getAbsolutePath();
    }

    /**
     * 内部辅助类：用于绑定样式ID和解析出的显示名称
     */
    private static class KeyboardItem {
        String id;
        String displayName;

        KeyboardItem(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }

    public KeyboardDialog(Context context) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context, ThemeManager.getDialogTheme())
                        .setTitle("选择默认键盘")
                        //setPositiveButton(
                        //       "主题",
                        //       (dialog, id) -> new ThemeDialog(context).show(mWindowToken))
                        .setNegativeButton(android.R.string.cancel, null);

        if (Rime.getCurrentRimeSchema().equals(".default")) {
            builder.setMessage("请先正确配置");
        } else {
            // 1. 获取当前选中的 ID 和所有样式文件夹名
            String currentKeyboardId = Config.getKeyboard();
            String[] rawKeyboardIds = Config.getKeyboards();

            // 2. 准备 Lua 环境
            Globals luaGlobals = JsePlatform.standardGlobals();
            luaGlobals.finder = this;

            // 3. 遍历并解析每个样式的显示名称
            List<KeyboardItem> KeyboardItems = new ArrayList<>();
            for (String id : rawKeyboardIds) {
                id=id.replace(".lua","");
                String displayName = getKeyboardNameFromLua(luaGlobals, id);
                if(TextUtils.isEmpty(displayName))
                    continue;
                KeyboardItems.add(new KeyboardItem(id, displayName));
            }

            // 4. 【核心】按显示名称进行本地化排序
            Collections.sort(KeyboardItems, new Comparator<KeyboardItem>() {
                private final OptionsDialog.LocaleComparator localeComp = new OptionsDialog.LocaleComparator();

                @Override
                public int compare(KeyboardItem s1, KeyboardItem s2) {
                    // 1. 首先比较显示名称
                    int nameComparison = localeComp.compare(s1.displayName, s2.displayName);
                    // 2. 如果显示名称相同，则按 ID 字符串排序
                    if (nameComparison == 0) {
                        // ID 通常是英文/数字文件夹名，直接用 String 的 compareTo 即可
                        return localeComp.compare(s1.id, s2.id);
                    }

                    return nameComparison;
                }
            });
            KeyboardItems.add(0,new KeyboardItem("","自动匹配"));
            // 5. 拆分排序后的数据供 Dialog 使用
            int size = KeyboardItems.size();
            String[] sortedNames = new String[size];
            String[] sortedIds = new String[size];
            int currentSelectedIndex = 0;

            for (int i = 0; i < size; i++) {
                KeyboardItem item = KeyboardItems.get(i);
                sortedNames[i] = item.displayName;
                sortedIds[i] = item.id;
                // 重新匹配当前选中的索引位置
                if (item.id.equals(currentKeyboardId)) {
                    currentSelectedIndex = i;
                }
            }

            builder.setSingleChoiceItems(
                    sortedNames,
                    currentSelectedIndex,
                    (dialog, index) -> {
                        dialog.dismiss();
                        String selectedId = sortedIds[index];
                        Config.setKeyboard(selectedId);
                        TrimeService trime = TrimeService.getInstance();
                        if (trime != null) {
                            trime.setKeyboard(selectedId);
                        }
                    });
        }
        mDialog = builder.create();
    }

    /**
     * 私有工具方法：从 main.lua 中提取 name 变量
     */
    private String getKeyboardNameFromLua(Globals globals, String keyboardId) {
        LuaTable env = new LuaTable();
        LuaTable mt = new LuaTable();
        mt.set("__index", globals);
        env.setmetatable(mt);

        try {
            String path = Config.getKeyboardPath(keyboardId);
            LuaValue chunk = globals.loadfilex(path, env);
            if (chunk.isfunction()) {
                chunk.call();
                com.osfans.trime.editor.LuaPatchManager.applyFile(globals,env,new java.io.File(path));
                LuaValue nameValue = env.get("name");
                if(!env.get("lock").toboolean())
                    return null;
                if (nameValue.isstring()) {
                    return nameValue.tojstring()+ " (" + keyboardId + ")"; // 仅返回名称，ID 在 UI 组装处处理
                }
            }
        } catch (Exception e) {
            return keyboardId + " (" + e + ")";
        }
        return keyboardId;
    }

    public void show() {
        if (mDialog == null) return;
        mDialog.show();
    }

    public void show(IBinder token) {
        if (mDialog == null) return;
        mWindowToken = token;
        if (mWindowToken == null) {
            show();
            return;
        }
        Window win = mDialog.getWindow();
        if (win != null) {
            WindowManager.LayoutParams attr = win.getAttributes();
            attr.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            attr.token = token;
            win.setAttributes(attr);
        }
        mDialog.show();
    }
}

