/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.keyboard;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.osfans.trime.Key;
import com.osfans.trime.TrimeService;
import com.osfans.trime.theme.ThemeManager;

import org.luaj.Globals;
import org.luaj.LuaTable;

public class RowKeyboardView extends KeyboardView implements View.OnClickListener, View.OnLongClickListener {

    private final Globals globals;
    private double mHeight;
    private double mWidth;
    private LuaTable mRows;
    private double mRowHeight;
    private double mTop;
    private double mKeyWidth;
    private LuaTable mLayout;
    private double mLeft;

    public RowKeyboardView(@NonNull Context context, Globals globals) {
        super(context, globals);
        this.globals = globals;
        String style = globals.get("style").optjstring("keyboard");
        long time=System.currentTimeMillis();
        setBackground(ThemeManager.getStyle().getStyle(style).getBackground(0xffdddddd));
        loadRows();
        Log.w("RowKeyboardView", "init time: "+(System.currentTimeMillis()-time) );
    }

    private void loadRows() {
        TrimeService mTrime= TrimeService.getInstance();
        mHeight = ThemeManager.getKeyboardHeight();
        mWidth = mTrime.getWidth();

        mLayout = globals.get("layout").opttable(null);
        mRows = globals.get("rows").checktable();
        int len = mRows.length();
        mRowHeight = 100.0 / Math.max(1, len);
        mRowHeight = globals.get("key_height").optdouble(mRowHeight);
        int firstKeyCount = len > 0 && mRows.get(1).istable() && mRows.get(1).get("keys").istable() ? mRows.get(1).get("keys").length() : 0;
        mKeyWidth = globals.get("key_width").optdouble(100.0 / Math.max(1, firstKeyCount));
        mTop=0;
        for (int i = 0; i < len; i++) {
            loadRow(mRows.get(i + 1).checktable());
        }
    }

    private void loadRow(LuaTable row) {
        double height = row.get("height").optdouble(mRowHeight);
        double width = row.get("width").optdouble(mKeyWidth);
        LuaTable keys = row.get("keys").checktable();
        int len = keys.length();
        mLeft=0;
        // Row style is a container background, independent of individual Key styles.
        if (row.get("style").type() == org.luaj.LuaValue.TSTRING) {
            View background = new View(getContext());
            background.setBackground(ThemeManager.getStyle().getStyle(row.get("style").tojstring()).getBackground(0));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int)mWidth, Math.max(0,(int)(mHeight*height/100)), Gravity.TOP|Gravity.LEFT);
            params.topMargin = (int)mTop;
            background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(background, 0, params);
        }
        for (int i = 0; i < len; i++) {
            loadKey(keys.get(i+1).checktable(),width,height);
        }
        mTop+=mHeight*height/100;
    }

    private void loadKey(LuaTable key, double width, double height) {
        //LuaTable layout=key.get("layout").opttable(mLayout);
        width = (mWidth*key.get("width").optdouble(width)/100);
        height = (mHeight*key.get("height").optdouble(height)/100);
        KeyView keyView = new KeyView(getContext(),new Key(key));

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams((int) width, (int) height, Gravity.TOP| Gravity.LEFT);
        layoutParams.leftMargin= (int) mLeft;
        layoutParams.topMargin= (int) mTop;
           mLeft+=width;
        addView(keyView,layoutParams);
    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public boolean onLongClick(View v) {
        return false;
    }
}
