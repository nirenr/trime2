/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.candidate;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.osfans.trime.Config;
import com.osfans.trime.Event;
import com.osfans.trime.Key;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeSchema;
import com.osfans.trime.keyboard.KeyView;
import com.osfans.trime.theme.KeyStyle;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;
import com.osfans.trime.util.Function;

import org.luaj.LuaTable;
import org.luaj.LuaValue;

import java.util.ArrayList;
import java.util.List;

public class ToolbarView extends LinearLayout implements View.OnClickListener {
    private final TrimeService mTrime;
    private final Style mToolbarStyle;
    private final KeyStyle mKeyStyle;
    private ArrayList<KeyView> mKeys = new ArrayList<>();
    private KeyView mHide;
    ;

    public ToolbarView(Context context) {
        super(context);
        mTrime = TrimeService.getInstance();
        mToolbarStyle = ThemeManager.getStyle().getStyle("toolbar");
        mKeyStyle = mToolbarStyle.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle("key"));
        setClipChildren(false);
        setClipToPadding(false);
        initView();
    }

    private void initView() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(HORIZONTAL);
        root.setBackground(mToolbarStyle.getBackground(0xffdddddd));
        int elevation = mToolbarStyle.getSize("elevation", 2);
        root.setElevation(elevation);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int dShadowColor = mToolbarStyle.getColor("shadow_color", 0);
            if (dShadowColor != 0) {
                root.setOutlineAmbientShadowColor(dShadowColor);
                root.setOutlineSpotShadowColor(dShadowColor);
            }
        }
        // 设置 CandidateView 自身的高度，防止输入法界面闪烁
        int height = ThemeManager.getCandidateHeight() - elevation;
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, 0, 0, elevation);
        addView(root, lp);

        HorizontalScrollView mListView = new HorizontalScrollView(getContext());
        mListView.setHorizontalScrollBarEnabled(false); // 禁止水平滚动条
        mListView.setVerticalScrollBarEnabled(false);
        LinearLayout itemsLayout = new LinearLayout(getContext());
        itemsLayout.setGravity(Gravity.CENTER);
        mListView.addView(itemsLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LuaValue hide = mToolbarStyle.get("hide");
        mHide = new KeyView(getContext(), hide.istable() ? mToolbarStyle.getKeyStyle("hide", mToolbarStyle.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle("key"))) : mToolbarStyle.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle("key")));
        if (hide.istable()) {
            mHide.setText(hide.get("text").optjstring("▽"));
        } else {
            mHide.setText(hide.optjstring("▽"));
        }
        mHide.setContentDescription("收起键盘");
        mHide.setOnClickListener(this);
        mHide.setMinimumWidth(height);

        root.addView(mListView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, 1));
        root.addView(mHide, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height));
        try {
            if (mToolbarStyle.get("schema_switches").optboolean(false) && !Rime.getCurrentRimeSchema().equals(".default")) {
                RimeSchema currentRimeSchema = new RimeSchema(Rime.getCurrentRimeSchema());
                List<RimeSchema.Switch> switches = currentRimeSchema.getSwitches();
                for (RimeSchema.Switch aSwitch : switches) {
                    if (aSwitch.getStates().isEmpty())
                        continue;
                    KeyView key = new KeyView(getContext(), mKeyStyle) {
                        @Override
                        public void invalidateKey() {
                            super.invalidateKey();
                            setText(aSwitch.getState());
                        }
                    };
                    key.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            aSwitch.toggleOption();
                        }
                    });
                    key.setText(aSwitch.getState());
                    itemsLayout.addView(key, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    key.setMinimumWidth(height);
                    mKeys.add(key);
                    Rime.setRimeOption(aSwitch.getName(), aSwitch.getReset() != 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        LuaValue keys = mToolbarStyle.get("keys").opttable(new LuaTable());
        int len = keys.length();
        for (int i = 0; i < len; i++) {
            LuaValue o = keys.get(i + 1);
            if (o.istable()) {
                LuaValue s = o.get("style");
                KeyStyle style = mKeyStyle;
                if (s.isstring()) {
                    style = ThemeManager.getStyle().getKeyStyle(s.tojstring(), mKeyStyle);
                }
                if (o.get("options").istable()) {
                    RimeSchema.Switch aSwitch = new RimeSchema.Switch(o.get("name").optjstring(""), o.get("options").opttable(new LuaTable()).stringValues(), o.get("reset").optint(0), o.get("states").opttable(new LuaTable()).stringValues());
                    KeyView key = new KeyView(getContext(), mKeyStyle) {
                        @Override
                        public void invalidateKey() {
                            super.invalidateKey();
                            setText(aSwitch.getState());
                        }
                    };
                    key.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            aSwitch.toggleOption();
                            switch (aSwitch.getName()) {
                                case "schema_group":
                                    String groupsId = aSwitch.getOption();
                                    Config.setGroup(groupsId);
                                    mTrime.restart();
                                    break;
                                case "schema_id":
                                    String selectedId = aSwitch.getOption();
                                    Rime.selectRimeSchema(selectedId); // 切换方案
                                    Function.saveString(mTrime, "select_schema_id", selectedId);
                                    break;
                                case "theme":
                                    mTrime.setTheme(aSwitch.getOption());
                                    break;
                                case "style":
                                    mTrime.setStyle(aSwitch.getOption());
                                    break;
                                case "keyboard":
                                    mTrime.setKeyboard(aSwitch.getOption());
                                    break;

                            }
                        }
                    });
                    key.setText(aSwitch.getState());
                    itemsLayout.addView(key, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    key.setMinimumWidth(height);
                    mKeys.add(key);
                } else {
                    KeyView key = new KeyView(getContext(), o.get("click").isnil() ? new Key(new Event(o)) : new Key(o), style);
                    itemsLayout.addView(key, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    key.setMinimumWidth(height);
                    mKeys.add(key);
                }
            } else if (o.isstring()) {
                KeyView key = new KeyView(getContext(), new Key(o.tojstring()), mKeyStyle);
                itemsLayout.addView(key, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                key.setMinimumWidth(height);
                mKeys.add(key);
            }
        }
    }

    @Override
    public void onClick(View v) {
        mTrime.requestHideSelf(0);
    }

    public void invalidateAllKeys() {
        for (KeyView key : mKeys) {
            key.invalidateKey();
        }
    }

    public void setSchema(String id) {
        removeAllViews();
        initView();
    }

    public KeyView getHide() {
        return mHide;
    }
}
