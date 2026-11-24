/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;

import com.osfans.trime.Config;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.theme.ThemeManager;

import org.luaj.Globals;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.jse.JsePlatform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StyleDialog {
    private final AlertDialog mDialog;
    private IBinder mWindowToken;

    /**
     * 内部辅助类：用于绑定样式ID和解析出的显示名称
     */
    private static class StyleItem {
        String id;
        String displayName;

        StyleItem(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }

    public StyleDialog(Context context) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context, ThemeManager.getDialogTheme())
                        .setTitle("选择样式")
                        .setPositiveButton(
                                "主题",
                                (dialog, id) -> new ThemeDialog(context).show(mWindowToken))
                        .setNegativeButton(android.R.string.cancel, null);

        if (Rime.getCurrentRimeSchema().equals(".default")) {
            builder.setMessage("请先正确配置");
        } else {
            // 1. 获取当前选中的 ID 和所有样式文件夹名
            String currentStyleId = Config.getStyle();
            String[] rawStyleIds = Config.getStyles();

            // 2. 准备 Lua 环境
            Globals luaGlobals = JsePlatform.standardGlobals();
            luaGlobals.finder = ThemeManager.getFinder();

            // 3. 遍历并解析每个样式的显示名称
            List<StyleItem> styleItems = new ArrayList<>();
            for (String id : rawStyleIds) {
                String displayName = getStyleNameFromLua(luaGlobals, id);
                styleItems.add(new StyleItem(id, displayName));
            }

            // 4. 【核心】按显示名称进行本地化排序
            Collections.sort(styleItems, new Comparator<StyleItem>() {
                private final OptionsDialog.LocaleComparator localeComp = new OptionsDialog.LocaleComparator();

                @Override
                public int compare(StyleItem s1, StyleItem s2) {
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

            // 5. 拆分排序后的数据供 Dialog 使用
            int size = styleItems.size();
            String[] sortedNames = new String[size];
            String[] sortedIds = new String[size];
            int currentSelectedIndex = 0;

            for (int i = 0; i < size; i++) {
                StyleItem item = styleItems.get(i);
                sortedNames[i] = item.displayName;
                sortedIds[i] = item.id;
                // 重新匹配当前选中的索引位置
                if (item.id.equals(currentStyleId)) {
                    currentSelectedIndex = i;
                }
            }

            builder.setSingleChoiceItems(
                    sortedNames,
                    currentSelectedIndex,
                    (dialog, index) -> {
                        dialog.dismiss();
                        String selectedId = sortedIds[index];
                        Config.setStyle(selectedId);
                        TrimeService trime = TrimeService.getInstance();
                        if (trime != null) {
                            trime.setStyle(selectedId);
                        }
                    });
        }
        mDialog = builder.create();
    }

    /**
     * 私有工具方法：从 main.lua 中提取 name 变量
     */
    private String getStyleNameFromLua(Globals globals, String styleId) {
        LuaTable env = new LuaTable();
        LuaTable mt = new LuaTable();
        mt.set("__index", globals);
        env.setmetatable(mt);

        try {
            String path = Config.getStylePath(styleId, "main.lua");
            LuaValue chunk = globals.loadfilex(path, env);
            if (chunk.isfunction()) {
                chunk.call();
                com.osfans.trime.editor.LuaPatchManager.applyFile(globals,env,new java.io.File(path));
                LuaValue nameValue = env.get("name");
                if (nameValue.isstring()) {
                    return nameValue.tojstring()+ " (" + styleId + ")"; // 仅返回名称，ID 在 UI 组装处处理
                }
            }
        } catch (Exception e) {
            return styleId + " (" + e + ")";
        }
        return styleId;
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
