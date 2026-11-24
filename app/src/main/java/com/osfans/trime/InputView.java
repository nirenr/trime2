/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.androlua.LuaUtil;
import com.osfans.trime.core.Rime;
import com.osfans.trime.editor.LuaPatchManager;
import com.osfans.trime.keyboard.AbsKeyboardView;
import com.osfans.trime.keyboard.FlexboxKeyboardView;
import com.osfans.trime.keyboard.KeyboardView;
import com.osfans.trime.keyboard.ModifierState;
import com.osfans.trime.keyboard.RowKeyboardView;
import com.osfans.trime.keyboard.SymbolsKeyboardView;
import com.osfans.trime.theme.ThemeManager;
import com.osfans.trime.util.Function;

import androidx.annotation.NonNull;

import org.luaj.Globals;
import org.luaj.LuaValue;
import org.luaj.lib.ResourceFinder;
import org.luaj.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class InputView extends FrameLayout implements ResourceFinder {

    private View mKeyboardView;
    private Globals globals;
    private View oldView;

    public InputView(@NonNull Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        String id = Rime.getRimeStatus().getSchemaId();
        if (TextUtils.isEmpty(id))
            id = Function.getPref(context).getString("select_schema_id", "");
        setKeyboard(id);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setKeyboardView(KeyboardView keyboardView) {
        if (keyboardView.equals(mKeyboardView))
            return;
        if (keyboardView.isLock()) {
            mViewCache.put(".default", keyboardView);
        }
        oldView = mKeyboardView;
        mKeyboardView = keyboardView;
        ViewParent parent = keyboardView.getParent();
        if (parent != null && parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(keyboardView);
        }
        Rime.setRimeOption("ascii_mode", keyboardView.isAsciiMode());
        setShifted(false);
        ModifierState.setShifted(false);
        ModifierState.setShiftLock(false);
        addView(keyboardView, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (oldView != null) {
            // 旧键盘淡出
            oldView.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withLayer() // 动画期间使用硬件层加速
                    .withEndAction(() -> {
                        removeView(oldView);
                        oldView.setAlpha(1.0f); // 恢复状态以备下次复用
                    })
                    .start();
        }
    }

    private void setKeyboardView(View keyboardView) {
        oldView = mKeyboardView;
        mKeyboardView = keyboardView;
        ViewParent parent = keyboardView.getParent();
        if (parent != null && parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(keyboardView);
        }
        addView(keyboardView, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (oldView != null) {
            // 旧键盘淡出
            oldView.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withLayer() // 动画期间使用硬件层加速
                    .withEndAction(() -> {
                        removeView(oldView);
                        oldView.setAlpha(1.0f); // 恢复状态以备下次复用
                    })
                    .start();
        }
    }

    private String mCurrentSchemaId;

    // 2. 视图缓存：SchemaId -> KeyboardView 实例
    private final Map<String, KeyboardView> mViewCache = new HashMap<>();
    private final Map<String, SymbolsKeyboardView> mSymbolsViewCache = new HashMap<>();

    public void setKeyboard(String id) {
        Log.w("TAG", "setKeyboard:s " + id);
        if (".last".equals(id)) {
            if (oldView != null)
                setKeyboardView(oldView);
            return;
        }
        if (id == null || id.equals(mCurrentSchemaId)) return;
        mCurrentSchemaId = id;
        id = getKeyboardId(id);
        Log.w("TAG", "setKeyboard:e " + id);
        // 获取或创建 KeyboardView
        KeyboardView targetView = mViewCache.get(id);
        if (targetView == null) {
            // 初始化 Lua 环境
            Globals globals = JsePlatform.standardGlobals();
            globals.finder = this;
            String path = id + ".lua";
            File base = new File(path);
            if (!base.isFile()) base = new File(findFile(path));
            LuaValue func = globals.loadfilex(base.isFile() ? base.getAbsolutePath() : path);
            try {
                if (func.isfunction()) {
                    LuaValue ret = func.call();
                    try { LuaPatchManager.applyFile(globals, globals, base); }
                    catch (Exception e) { ThemeManager.sendMsg("键盘补丁: " + e); }
                    if (ret.isuserdata(View.class)) {
                        setKeyboardView(ret.touserdata(View.class));
                        return;
                    }
                } else {
                    ThemeManager.sendMsg("setKeyboard " + func.tojstring());
                }
            } catch (Exception e) {
                ThemeManager.sendMsg("setKeyboard " + e);
            }
            //Log.w("TAG", "setKeyboard: "+globals.checktable().dump().tojstring() );
            // 创建并存入缓存
            if (globals.get("rows").istable()) {
                targetView = new RowKeyboardView(getContext(), globals);
            } else if (globals.get("flex_box").istable()) {
                targetView = new FlexboxKeyboardView(getContext(), globals);
            } else if (globals.get("keys").istable()) {
                targetView = new AbsKeyboardView(getContext(), globals);
            } else if (globals.get("key_maps").istable()) {
                SymbolsKeyboardView symbolsView = mSymbolsViewCache.get(id);
                if (symbolsView == null) {
                    symbolsView = new SymbolsKeyboardView(getContext(), globals);
                    mSymbolsViewCache.put(id, symbolsView);
                }
                TrimeService.getInstance().showCustomView(symbolsView);
                mCurrentSchemaId = null;
                return;
            } else {
                func = globals.loadfilex("themes/default/keyboards/qwerty36.lua");
                if (func.isfunction())
                    func.call();
                targetView = new RowKeyboardView(getContext(), globals);
            }
            mViewCache.put(id, targetView);
        }
        setKeyboardView(targetView);
    }

    private String getKeyboardId(String id) {
        if (id.isEmpty()) {
            String k = Config.getKeyboard(".default");
            if (!TextUtils.isEmpty(k)) {
                if (mViewCache.containsKey(id))
                    return id;
                if (new File(findFile(id + ".lua")).exists())
                    return id;
            }
            id = Rime.getRimeStatus().getSchemaId();
            if (TextUtils.isEmpty(id))
                id = Function.getPref(getContext()).getString("select_schema_id", "");
        }
        Log.w("TAG", "setKeyboard:2 " + id);
        if (mViewCache.containsKey(id))
            return id;
        if (new File(findFile(id + ".lua")).exists())
            return id;

        id = ThemeManager.getKeyboard(id);
        Log.w("TAG", "setKeyboard:4 " + id);
        if (mViewCache.containsKey(id))
            return id;
        if (new File(findFile(id + ".lua")).exists())
            return id;

        id = Rime.getRimeStatus().getSchemaId();
        if (TextUtils.isEmpty(id))
            id = Function.getPref(getContext()).getString("select_schema_id", "");
        Log.w("TAG", "setKeyboard:5 " + id);
        if (mViewCache.containsKey(id))
            return id;
        if (new File(findFile(id + ".lua")).exists())
            return id;

        id = ThemeManager.getKeyboard(id);
        Log.w("TAG", "setKeyboard:6 " + id);
        if (mViewCache.containsKey(id))
            return id;
        if (new File(findFile(id + ".lua")).exists())
            return id;

        return "qwerty36";
    }

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
        if (!name.endsWith(".lua"))
            return null;
        try {
            return getContext().getAssets().open("themes/default/keyboards/" + name);
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        try {
            //name = name.replace(".lua", "");
            //if (!name.startsWith("themes/default/") && !TextUtils.isEmpty(name)) {
            //    //Toast.makeText(getContext(), "未找到 " + name, Toast.LENGTH_SHORT).show();
            //    return null;
            //}
            return getContext().getAssets().open(name);
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

    public void invalidateComposingKeys() {
        if (mKeyboardView instanceof KeyboardView)
            ((KeyboardView) mKeyboardView).invalidateComposingKeys();
    }

    public void invalidateAllKeys() {
        if (mKeyboardView instanceof KeyboardView)
            ((KeyboardView) mKeyboardView).invalidateAllKeys();
        //for (KeyboardView value : mViewCache.values()) {
        //    value.invalidateAllKeys();
        //}
    }

    public boolean isShifted() {
        return ModifierState.isShifted();
    }

    public void setShifted(boolean shifted) {
        if (mKeyboardView instanceof KeyboardView)
            ((KeyboardView) mKeyboardView).setShifted(shifted);
    }

    public void setAsciiMode(boolean asciiMode) {
        if (mKeyboardView instanceof KeyboardView)
            ((KeyboardView) mKeyboardView).setAsciiMode(asciiMode);
    }

    public View getKeyboardView() {
        return mKeyboardView;
    }
}
