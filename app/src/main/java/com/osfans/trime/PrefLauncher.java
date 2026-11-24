/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayListAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.androlua.LuaUtil;
import com.osfans.trime.core.DataManager;
import com.osfans.trime.dialog.DeployDialog;
import com.osfans.trime.dialog.KeyboardDialog;
import com.osfans.trime.dialog.OptionsDialog;
import com.osfans.trime.dialog.SchemaDialog;
import com.osfans.trime.dialog.SchemaGroupDialog;
import com.osfans.trime.dialog.StyleDialog;
import com.osfans.trime.dialog.ThemeDialog;
import com.osfans.trime.dialog.VoiceInputDialog;

public class PrefLauncher extends Activity implements AdapterView.OnItemClickListener {

    private static IBinder mToken;

    public static IBinder getToken() {
        return mToken;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(android.R.style.Theme_DeviceDefault_DayNight);
        } else {
            setTheme(android.R.style.Theme_DeviceDefault);
        }
        super.onCreate(savedInstanceState);
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        DataManager.sync();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        ArrayListAdapter<String> adapter = new ArrayListAdapter<>(this,new String[]{
                "切换输入法",
                "方案组",
                "管理方案",
                "输入方案",
                "键盘主题",
                "颜色样式",
                "默认键盘",
                "语音识别引擎",
                "主题编辑器", "配色编辑器", "键盘编辑器", "方案编辑器"
        });
        ListView mListView = new ListView(this);
        // 解决内容被导航栏遮挡的关键：应用 WindowInsets
         mListView.setAdapter(adapter);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // 修改后的布局结构逻辑
        LinearLayout root = new LinearLayout(this);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                // 获取系统状态栏和导航栏的 Insets，但不包含键盘 (ime)
                Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                // 获取键盘高度
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(
                        systemBars.left,
                        systemBars.top,
                        systemBars.right,
                        systemBars.bottom
                );
                return WindowInsets.CONSUMED;
            });
        }
        root.setOrientation(LinearLayout.VERTICAL);
        EditText editText = new EditText(this);
        //editText.setHint("点击展开键盘测试");
        root.addView(mListView, new LinearLayout.LayoutParams(-1, -2, 1)); // ListView 占据剩余空间
        root.addView(editText, new LinearLayout.LayoutParams(-1, -2)); // EditText 固定在底部
        TextView tv = new TextView(this);
        tv.setAutoLinkMask(Linkify.ALL);
        tv.setText("下载更多版本：https://github.com/nirenr/trime2/releases");
        root.addView(tv, new LinearLayout.LayoutParams(-1, -2)); // EditText 固定在底部
        setContentView(root);
        mListView.setOnItemClickListener(this);
     }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0,1,0,"部署").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0,2,0,"工具").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case 1:
                if (TrimeService.getInstance() == null) {
                    Toast.makeText(this, "请先启用输入法", Toast.LENGTH_SHORT).show();
                    return super.onOptionsItemSelected(item);
                }
                new DeployDialog(this).show();
                break;
            case 2:
                startActivity(new Intent(this,ToolActivity.class));
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LuaUtil.checkStorage(this);
   }

    @Override
    protected void onStart() {
        super.onStart();
        mToken=getWindow().getDecorView().getWindowToken();
    }

    @Override
    protected void onStop() {
        mToken=null;
        super.onStop();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (position){
            case 0:
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
                break;
            case 1:
                new SchemaGroupDialog(PrefLauncher.this).show();
                break;
            case 2:
                new SchemaDialog(this).show();
                break;
            case 3:
                new OptionsDialog(this).show();
                break;
            case 4:
                new ThemeDialog(this).show();
                break;
            case 5:
                new StyleDialog(this).show();
                break;
            case 6:
                new KeyboardDialog(this).show();
                break;
            case 7:
                new VoiceInputDialog(this).show();
                break;
            case 8: case 9: case 10: case 11:
                startActivity(new Intent(this, com.osfans.trime.editor.EditorActivity.class)
                        .putExtra(com.osfans.trime.editor.EditorActivity.MODE, position - 8));
                break;
        }
    }
}
