/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.androlua.LuaUtil;
import com.osfans.trime.core.DataManager;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeProto;
import com.osfans.trime.core.SchemaItem;
import com.osfans.trime.editor.SchemaDocument;
import com.osfans.trime.util.Function;
import com.osfans.trime.core.RimeMessage;
import com.osfans.trime.dialog.DeployDialog;
import com.osfans.trime.dialog.KeyboardDialog;
import com.osfans.trime.dialog.OptionsDialog;
import com.osfans.trime.dialog.SchemaDialog;
import com.osfans.trime.dialog.SchemaGroupDialog;
import com.osfans.trime.dialog.StyleDialog;
import com.osfans.trime.dialog.ThemeDialog;
import com.osfans.trime.dialog.VoiceInputDialog;
import com.osfans.trime.editor.EditorActivity;
import com.osfans.trime.ui.EdgeToEdge;
import com.osfans.trime.ui.HomeAppearance;
import com.osfans.trime.ui.HomeCardView;
import com.osfans.trime.ui.HomeConfigNames;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.luaj.Globals;
import org.luaj.LuaValue;
import org.luaj.lib.jse.JsePlatform;

public class PrefLauncher extends Activity {
    private static IBinder mToken;
    private static final String RELEASES = "https://github.com/nirenr/trime2/releases";
    private HomeAppearance a;
    private LinearLayout root, content, status;
    private ScrollView scroll;
    private EditText test;
    private TextView statusTitle, statusDetail, statusAction;
    private ImageView statusIcon;
    private HomeCardView theme, palette, keyboard, group, schema;
    private boolean enabled;
    private final ThreadPoolExecutor nameWorker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>());
    private final HomeConfigNames configNames=new HomeConfigNames(PrefLauncher::nameGlobals);
    private Future<?> nameTask;
    private String nameSelection;
    private HomeConfigNames.Names shownNames;
    private long nameGeneration;
    private boolean namesLoading, destroyed;
    // Same isolated Lua environment used by the visual editor. Name lookup should not
    // execute arbitrary I/O or Android commands on the user's behalf.
    private static Globals nameGlobals(){
        Globals g=JsePlatform.standardGlobals();
        for(String k:new String[]{"io","os","luajava","debug","package","require","dofile","loadfile","load","loadstring","module"})g.set(k,LuaValue.NIL);
        return g;
    }
    private void cancelNames(){nameGeneration++;namesLoading=false;if(nameTask!=null){nameTask.cancel(false);nameTask=null;nameWorker.purge();}}
    private void refreshConfigNames(String themeId,String styleId,String keyboardId){
        if(destroyed)return;
        String selection=themeId+"\n"+styleId+"\n"+keyboardId;
        boolean same=selection.equals(nameSelection);
        if(!same){cancelNames();nameSelection=selection;shownNames=null;}
        applyNames(shownNames==null?new HomeConfigNames.Names(themeId,styleId,TextUtils.isEmpty(keyboardId)?"自动匹配":keyboardId):shownNames);
        if(namesLoading)return;
        final long generation=++nameGeneration;namesLoading=true;
        nameTask=nameWorker.submit(()->{
            HomeConfigNames.Names result;
            try{result=configNames.read(new File(Config.getThemeDir(themeId)),themeId,styleId,keyboardId);}
            catch(Exception e){result=new HomeConfigNames.Names(themeId,styleId,TextUtils.isEmpty(keyboardId)?"自动匹配":keyboardId);}
            final HomeConfigNames.Names names=result;
            runOnUiThread(()->{if(destroyed||generation!=nameGeneration||!selection.equals(nameSelection))return;namesLoading=false;shownNames=names;applyNames(names);});
        });
    }
    private void applyNames(HomeConfigNames.Names names){if(theme!=null){theme.setValue(names.theme);palette.setValue(names.style);keyboard.setValue(names.keyboard);}}

    private Future<?> offlineSchemaTask;
    private String offlineSchemaSelection;
    private SchemaItem offlineSchema;
    private boolean offlineSchemaLoaded;
    private long offlineSchemaGeneration;

    private void cancelOfflineSchema() {
        if (offlineSchemaSelection == null && offlineSchemaTask == null) return;
        offlineSchemaGeneration++;
        if (offlineSchemaTask != null) {
            offlineSchemaTask.cancel(true);
            offlineSchemaTask = null;
            nameWorker.purge();
        }
        offlineSchemaSelection = null;
        offlineSchema = null;
        offlineSchemaLoaded = false;
    }

    private void showOfflineSchema() {
        final String groupId = Config.getGroup();
        final String savedId = Function.loadString(this, "select_schema_id", "");
        final String selection = groupId + "\n" + savedId;
        if (!selection.equals(offlineSchemaSelection)) {
            cancelOfflineSchema();
            offlineSchemaSelection = selection;
        }
        if (offlineSchemaLoaded) {
            if (offlineSchema != null) {
                String name = offlineSchema.getName();
                schema.setValue((TextUtils.isEmpty(name) ? offlineSchema.getId() : name) + "（上次）");
            } else {
                schema.setValue(enabled ? "暂无方案记录" : "请先启用输入法");
            }
            return;
        }
        schema.setValue("正在读取方案…");
        if (offlineSchemaTask != null || !homeStarted || destroyed) return;
        final long generation = ++offlineSchemaGeneration;
        offlineSchemaTask = nameWorker.submit(() -> {
            SchemaItem result = null;
            try {
                File user = new File(Config.getDataDir(), "schemas/" + groupId);
                File shared = DataManager.getSharedDataDir();
                result = SchemaDocument.lastUsed(user, shared, savedId);
            } catch (RuntimeException ignored) {}
            final SchemaItem item = result;
            schemaHandler.post(() -> {
                if (destroyed || !homeStarted || generation != offlineSchemaGeneration
                        || !selection.equals(offlineSchemaSelection)) return;
                offlineSchemaTask = null;
                offlineSchema = item;
                offlineSchemaLoaded = true;
                // Recheck live status: engine startup may have overtaken the disk read.
                refreshStatus();
            });
        });
    }

    private final android.database.ContentObserver imeObserver = new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refreshStatus(); }
    };
    // Listen only while the home screen is visible. Native messages may arrive on a worker.
    private final Handler schemaHandler = new Handler(Looper.getMainLooper());
    private volatile boolean homeStarted;
    private volatile RimeProto.Status shownSchemaStatus;
    private final Runnable schemaRefresh = () -> {
        if (homeStarted && !destroyed) refreshStatus();
    };
    private final Rime.Consumer<RimeMessage<?>> schemaListener = message -> {
        if (!homeStarted) return;
        if (message instanceof RimeMessage.StatusMessage) {
            RimeProto.Status incoming = ((RimeMessage.StatusMessage) message).getData();
            RimeProto.Status shown = shownSchemaStatus;
            if (incoming == null || (shown != null
                    && TextUtils.equals(incoming.getSchemaId(), shown.getSchemaId())
                    && TextUtils.equals(incoming.getSchemaName(), shown.getSchemaName()))) return;
        } else if (!(message instanceof RimeMessage.SchemaMessage)) {
            return;
        }
        // Do not query JNI or touch views inside the native callback.
        schemaHandler.removeCallbacks(schemaRefresh);
        schemaHandler.post(schemaRefresh);
    };
    public static IBinder getToken() { return mToken; }

    @Override protected void onCreate(@Nullable Bundle state) {
        setTheme(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? android.R.style.Theme_DeviceDefault_DayNight : android.R.style.Theme_DeviceDefault);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(state);
        DataManager.sync();
        buildHome();
        if (state != null) {
            test.setText(state.getString("home.test", ""));
            final int y=state.getInt("home.scroll"); scroll.post(() -> scroll.scrollTo(0,y));
        }
    }
    private int dp(float n) { return a.dp(n); }
    private boolean dynamic() { return getPreferences(MODE_PRIVATE).getBoolean("home.dynamic",false); }
    private LinearLayout vertical() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout horizontal() { LinearLayout v=new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }

    private void buildHome() {
        String draft=test==null?"":test.getText().toString();
        int selection=test==null?0:test.getSelectionStart();
        int oldScroll=scroll==null?0:scroll.getScrollY();
        boolean focused=test!=null&&test.hasFocus();
        a=new HomeAppearance(this,dynamic());
        root=vertical(); root.setBackgroundColor(a.page);
        root.setFocusableInTouchMode(true);
        LinearLayout top=horizontal(); top.setPadding(dp(16),dp(8),dp(12),dp(8)); top.setMinimumHeight(dp(64));
        TextView title=a.text(getString(R.string.app_name_release),24,a.ink,true); title.setMaxLines(2);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout deploy=horizontal(); deploy.setPadding(dp(12),0,dp(12),0); deploy.setMinimumHeight(dp(48));
        deploy.addView(HomeCardView.icon(this,R.drawable.home_deploy,a.accent),new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView deployLabel=a.text("部署",14,a.accent,true); LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-2,-2); dl.setMarginStart(dp(6)); deploy.addView(deployLabel,dl);
        deployLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); deploy.setContentDescription("部署");
        a.clickable(deploy,a.soft,24,0,v -> deploy()); top.addView(deploy,new LinearLayout.LayoutParams(-2,-2));
        ImageView more=HomeCardView.icon(this,R.drawable.home_more,a.ink); more.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); more.setContentDescription("更多功能"); more.setPadding(dp(12),dp(12),dp(12),dp(12));
        a.clickable(more,a.page,24,0,this::showMore); top.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));

        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        content=vertical(); content.setPadding(dp(16),dp(4),dp(16),dp(16));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        buildStatus();
        boolean narrow=getResources().getConfiguration().screenWidthDp<360 || getResources().getConfiguration().fontScale>1.2f;
        boolean compact=getResources().getConfiguration().fontScale>1.1f;
        section("外观配置");
        theme=card(R.drawable.home_image,a.accent,"键盘主题",!narrow&&!compact,"",v -> new ThemeDialog(this).show());
        palette=card(R.drawable.home_palette,a.accent,"配色样式",!narrow&&!compact,"",v -> new StyleDialog(this).show());
        keyboard=card(R.drawable.home_keyboard,a.accent,"默认键盘",!narrow&&!compact,"",v -> new KeyboardDialog(this).show());
        // On narrow displays use a full-width row for each setting, avoiding clipped labels.
        if(narrow) { cards(theme); cards(palette); cards(keyboard); }
        else if(compact) { cards(theme); cards(palette,keyboard); }
        else cards(theme,palette,keyboard);
        section("输入配置");
        group=card(R.drawable.home_layers,a.accent,"方案组",false,"",v -> new SchemaGroupDialog(this).show());
        HomeCardView manage=card(R.drawable.home_folder,a.accent,"管理方案",false,null,v -> new SchemaDialog(this).show());
        schema=card(R.drawable.home_document,a.accent,"输入方案",false,"",v -> new OptionsDialog(this).show());
        HomeCardView voice=card(R.drawable.home_mic,a.accent,"语音识别引擎",false,null,v -> new VoiceInputDialog(this).show());
        pairs(narrow,group,manage); pairs(narrow,schema,voice);
        section("编辑工作台");
        int purple=a.dark?0xffc9abff:0xff7652c5, orange=a.dark?0xffffc18b:0xffb75a27;
        pairs(narrow,card(R.drawable.home_image,purple,"主题编辑器",false,null,v -> edit(0)),card(R.drawable.home_palette,orange,"样式编辑器",false,null,v -> edit(1)));
        pairs(narrow,card(R.drawable.home_keyboard,a.accent,"键盘编辑器",false,null,v -> edit(2)),card(R.drawable.home_code,purple,"方案编辑器",false,null,v -> edit(3)));
        buildTest(draft,selection);
        setContentView(root); EdgeToEdge.apply(this,root);
        refreshStatus();
        scroll.post(() -> scroll.scrollTo(0,oldScroll));
        if(focused) test.requestFocus(); else root.requestFocus();
    }
    private void buildStatus() {
        status=horizontal(); status.setPadding(dp(12),dp(12),dp(12),dp(12)); status.setMinimumHeight(dp(76));
        statusIcon=HomeCardView.icon(this,R.drawable.home_check,a.success);
        status.addView(statusIcon,new LinearLayout.LayoutParams(dp(28),dp(28)));
        LinearLayout labels=vertical(); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMarginStart(dp(12));
        statusTitle=a.text("",16,a.ink,true); statusDetail=a.text("",12,a.muted,false);
        labels.addView(statusTitle); labels.addView(statusDetail); status.addView(labels,lp);
        statusAction=a.text("切换",14,a.accent,true); statusAction.setGravity(Gravity.CENTER); statusAction.setMinHeight(dp(48)); statusAction.setPadding(dp(14),0,dp(14),0);
        a.clickable(statusAction,a.soft,24,0,v -> switchIme()); status.addView(statusAction,new LinearLayout.LayoutParams(-2,-2));
        content.addView(status,new LinearLayout.LayoutParams(-1,-2));
    }
    private void section(String label) {
        TextView t=a.text(label,17,a.ink,true); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(18); p.bottomMargin=dp(10); content.addView(t,p);
        if(Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);
    }
    private HomeCardView card(int icon,int color,String label,boolean vertical,String value,View.OnClickListener action) { return new HomeCardView(this,a,icon,color,label,vertical,value,action); }
    private void cards(HomeCardView... items) {
        LinearLayout line=horizontal(); line.setGravity(Gravity.TOP); line.setBaselineAligned(false);
        for(int i=0;i<items.length;i++) { LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1); if(i>0)lp.setMarginStart(dp(8)); line.addView(items[i],lp); }
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dp(8); content.addView(line,rp);
    }
    private void pairs(boolean narrow,HomeCardView first,HomeCardView second) { if(narrow){cards(first); cards(second);}else cards(first,second); }
    private void buildTest(String draft,int selection) {
        LinearLayout bottom=horizontal(); bottom.setPadding(dp(16),dp(8),dp(16),dp(12));
        LinearLayout field=horizontal(); field.setPadding(dp(14),0,dp(12),0); field.setBackground(a.shape(a.surface,26,a.outline));
        ImageView icon=HomeCardView.icon(this,R.drawable.home_keyboard,a.muted); field.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
        test=new EditText(this); test.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); test.setMinLines(1); test.setMaxLines(2); test.setTextSize(15); test.setTextColor(a.ink); test.setHintTextColor(a.muted); test.setHint("点击测试键盘"); test.setBackgroundColor(android.graphics.Color.TRANSPARENT); test.setMinHeight(dp(52)); test.setPadding(dp(12),0,0,0); test.setText(draft); test.setSelection(Math.max(0,Math.min(selection,draft.length())));
        field.addView(test,new LinearLayout.LayoutParams(0,-2,1)); bottom.addView(field,new LinearLayout.LayoutParams(-1,-2)); root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
        icon.setOnClickListener(v -> {test.requestFocus(); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(test,InputMethodManager.SHOW_IMPLICIT);});
    }
    private boolean isOurIme(InputMethodInfo info) { return getPackageName().equals(info.getPackageName()) && TrimeService.class.getName().equals(info.getServiceName()); }
    private void refreshStatus() {
        if(status==null)return;
        InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        String active=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        enabled=false; boolean selected=false;
        if(imm!=null)for(InputMethodInfo info:imm.getEnabledInputMethodList())if(isOurIme(info)){enabled=true; selected=info.getId().equals(active); break;}
        status.setBackground(a.shape(enabled?a.successSurface:a.surface,18,enabled?0:a.outline));
        statusIcon.setImageResource(enabled?R.drawable.home_check:R.drawable.home_alert); statusIcon.setColorFilter(enabled?a.success:a.accent);
        statusTitle.setText(selected?"正在使用"+getString(R.string.app_name_release):enabled?"输入法已启用":"输入法未启用");
        statusDetail.setText(selected?"Rime 输入引擎":enabled?"点击切换为当前输入法":"请在系统设置中启用");
        statusAction.setText(enabled?"切换":"启用");
        String themeId=Config.getTheme(),styleId=Config.getStyle();group.setValue(Config.getGroup());
        // Do not call Config.getKeyboard() here: it also writes the default preference.
        TrimeService service=TrimeService.getInstance();
        Rime rime=service==null?null:service.getRime();
        boolean ready=rime!=null&&rime.isReady();
        RimeProto.Status current=null;
        if(ready) {
            try { current=Rime.getRimeStatus(); }
            catch(RuntimeException ignored) {}
        }
        shownSchemaStatus=current;
        String id=current==null?null:current.getSchemaId();
        String schemaName=current==null?null:current.getSchemaName();
        if(!ready || TextUtils.isEmpty(id)) {
            showOfflineSchema();
        } else if(".default".equals(id)) {
            cancelOfflineSchema();
            schema.setValue("未选择");
        } else {
            cancelOfflineSchema();
            schema.setValue(TextUtils.isEmpty(schemaName)||schemaName.trim().isEmpty()?id:schemaName);
        }
        String key=Config.getKeyboard(TextUtils.isEmpty(id)?".default":id);
        refreshConfigNames(themeId,styleId,key);
    }
    private void switchIme() {
        if(!enabled) startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        else ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
    }
    private void deploy() {
        if(TrimeService.getInstance()==null){Toast.makeText(this,"请先启用输入法",Toast.LENGTH_SHORT).show();return;}
        new DeployDialog(this).show();
    }
    private void edit(int mode) { startActivity(new Intent(this,EditorActivity.class).putExtra(EditorActivity.MODE,mode)); }
    private void showMore(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"工具管理");
        menu.getMenu().add(0,2,1,"下载更多版本");
        menu.getMenu().add(0,3,2,"版本信息");
        if(Build.VERSION.SDK_INT>=31)menu.getMenu().add(0,4,3,"跟随系统动态配色").setCheckable(true).setChecked(dynamic());
        menu.setOnMenuItemClickListener(item -> {switch(item.getItemId()){
            case 1:startActivity(new Intent(this,ToolActivity.class));return true;
            case 2:try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(RELEASES)));}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,RELEASES,Toast.LENGTH_LONG).show();}return true;
            case 3:showVersion();return true;
            case 4:getPreferences(MODE_PRIVATE).edit().putBoolean("home.dynamic",!dynamic()).apply();buildHome();return true;
            default:return false;
        }}); menu.show();
    }
    private void showVersion() {
        String version="未知版本";
        try {PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0); version=info.versionName;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        new AlertDialog.Builder(this).setTitle(R.string.app_name_release).setMessage("版本："+version+"\n\n"+RELEASES).setPositiveButton(android.R.string.ok,null).show();
    }
    @Override public void onConfigurationChanged(Configuration config) {super.onConfigurationChanged(config);buildHome();}
    @Override public void onWindowFocusChanged(boolean focus) {super.onWindowFocusChanged(focus);if(focus)refreshStatus();}
    @Override protected void onResume(){super.onResume();LuaUtil.checkStorage(this);refreshStatus();}
    @Override protected void onStart(){super.onStart();mToken=getWindow().getDecorView().getWindowToken();
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),false,imeObserver);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),false,imeObserver);
        homeStarted=true;
        Rime.registerRimeMessageHandler(schemaListener);
        refreshStatus();
    }
    @Override protected void onStop(){homeStarted=false;Rime.unregisterRimeMessageHandler(schemaListener);schemaHandler.removeCallbacks(schemaRefresh);cancelOfflineSchema();getContentResolver().unregisterContentObserver(imeObserver);mToken=null;cancelNames();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;cancelOfflineSchema();cancelNames();nameWorker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(test!=null)state.putString("home.test",test.getText().toString());if(scroll!=null)state.putInt("home.scroll",scroll.getScrollY());}
}
