package com.osfans.trime.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowManager;
import com.osfans.trime.ui.EdgeToEdge;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import com.osfans.trime.Config;
import com.osfans.trime.core.DataManager;
import org.luaj.Globals;
import org.luaj.LuaValue;
import org.luaj.lib.jse.JsePlatform;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Standalone editor for Trime's Lua resources and Rime YAML sources. */
public class EditorActivity extends Activity {
    public static final String MODE = "mode";
    private int mode;
    private File file;
    private String original, source, baseSource;
    private LinearLayout root, panel;
    private EditorAppearance ui;
    private Toolbar toolbar;
    private static final int MENU_SAVE=1, MENU_CHOOSE=2, MENU_MODE=3, MENU_BASE=4, MENU_CLEAR=5, MENU_UNDO=6, MENU_REDO=7, MENU_FIND=8, MENU_LINE=9, MENU_INFO=10, MENU_MERGE=11, MENU_MERGE_RESULT=12, MENU_MERGE_ORIGINAL=13, MENU_MERGE_PATCH=14, MENU_MERGE_CANCEL=15, MENU_WHITESPACE=16;
    private ScrollView scroll;
    private com.androlua.LuaEditor codeEditor;
    private boolean yamlWhitespace=true;
    private LinearLayout codePane;
    private boolean editingBase;
    private MergeDraft mergeDraft;
    private static final class MergeDraft {
        final File target; final String base,diskPatch,usedPatch; final boolean regenerated;
        String merged; int page;
        MergeDraft(File target,String base,String diskPatch,String usedPatch,LuaSourceMerger.Result result){
            this.target=target;this.base=base;this.diskPatch=diskPatch;this.usedPatch=usedPatch;merged=result.source;regenerated=result.regenerated;
        }
    }
    private static final class MergeCommit { final Parsed parsed; final File backup; MergeCommit(Parsed parsed,File backup){this.parsed=parsed;this.backup=backup;} }

    private Globals model, palette;
    private boolean visual = true;
    private String paletteGroup="key";
    private PaletteProperties paletteSchema;
    private PaletteGroupSnapshot paletteGroups;
    private LinearLayout paletteDetail, paletteList;
    private String paletteDetailGroup;
    private long paletteListVersion;
    private Runnable paletteListFrame;
    private int paletteRestoreY=-1;
    private android.view.ViewTreeObserver.OnGlobalLayoutListener paletteRestoreLayout;
    private List<LuaValue> builtinPalettes;
    private boolean showOptional=false;
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ThreadPoolExecutor previews = new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.LinkedBlockingQueue<Runnable>());
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private java.util.concurrent.Future<?> previewTask;
    private Runnable pendingPreview;
    private Runnable latestPreview;
    private boolean previewScheduled;
    private long previewVersion;
    private boolean busy, destroyed;
    private final java.util.concurrent.ExecutorService resources = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.util.LruCache<String,android.graphics.Typeface> fonts = new android.util.LruCache<>(32);
    private android.app.ProgressDialog progress;
    private interface Work<T>{T run() throws Exception;}
    private interface Result<T>{void accept(T value);}
    private <T> void background(String message,Work<T> work,Result<T> result){
        if(busy||destroyed)return;
        busy=true; cancelPreview();
        progress=new android.app.ProgressDialog(this);progress.setMessage(message);progress.setCancelable(false);progress.show();
        io.execute(()->{try{T data=work.run();main.post(()->{if(destroyed)return;endWork();try{result.accept(data);}catch(Exception e){error(e);}});}catch(Exception e){main.post(()->{if(destroyed)return;endWork();error(e);});}});
    }
    private void endWork(){busy=false;if(progress!=null){progress.dismiss();progress=null;}}
    private void cancelPreview(){previewVersion++;if(pendingPreview!=null)main.removeCallbacks(pendingPreview);previewScheduled=false;latestPreview=null;if(previewTask!=null){previewTask.cancel(false);previews.purge();}}
    @Override protected void onDestroy(){if(codeEditor!=null)codeEditor.cancelSpanning();destroyed=true;cancelPaletteList();cancelPreview();main.removeCallbacksAndMessages(null);io.shutdown();previews.shutdownNow();resources.shutdownNow();if(progress!=null)progress.dismiss();super.onDestroy();}
    private static class Parsed { Globals model,palette; PaletteProperties schema; PaletteGroupSnapshot groups; Exception failure; File styleDir; ThemeServiceConfig[] services; List<PresetKeyConfig> presetKeys; String presetError; String paletteNotice; SchemaDocument scheme; }
    private Parsed parse(String input,boolean show){return parse(input,show,baseSource);}
    private Parsed parse(String input,boolean show,String base){Parsed r=new Parsed();if(!show)return r;try{if(mode==3){r.scheme=new SchemaDocument(input);return r;}r.model=evaluatePatched(base,input);if(mode==0){r.services=new ThemeServiceConfig[]{ThemeServiceConfig.read(r.model,"ai_config"),ThemeServiceConfig.read(r.model,"asr_config")};try{r.presetKeys=PresetKeyConfig.read(r.model,evaluate(readDefaultTheme()));}catch(Exception e){r.presetError=e.getMessage();}}if(mode==1){r.palette=r.model;r.schema=new PaletteProperties(r.model,builtinPalettes());r.groups=new PaletteGroupSnapshot(r.model,r.schema);}if(mode==2){Globals theme=null;try{theme=evaluateFile(new File(Config.getThemeDir(Config.getTheme()),"main.lua"));r.presetKeys=PresetKeyConfig.read(theme,evaluate(readDefaultTheme()));}catch(Exception e){r.presetError=e.toString();if(theme==null)try{theme=evaluate(readDefaultTheme());r.presetKeys=PresetKeyConfig.read(theme,theme);}catch(Exception ignored){}}loadKeyboardPalette(r,theme);}}catch(Exception e){r.failure=e;}return r;}
    private void install(Parsed r){model=r.model;palette=r.palette;paletteSchema=r.schema;paletteGroups=r.groups;parseFailure=r.failure;activeStyleDir=r.styleDir;themeServices=r.services;presetKeys=r.presetKeys;presetError=r.presetError;paletteNotice=r.paletteNotice;schemeDocument=r.scheme;}
    private String paletteNotice;
    private String readAsset(String path) throws IOException {try(InputStream in=getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private void loadKeyboardPalette(Parsed r,Globals theme){
        String name=Config.getStyle();File dir=new File(Config.getStyleDir(name));
        try{File base=new File(dir,"main.lua");String baseText=read(base);r.palette=evaluatePalette(baseText,theme);LuaPatchManager.applyChecked(r.palette,r.palette,baseText,LuaPatchManager.read(base));r.styleDir=dir;r.paletteNotice="当前配色："+name;return;}catch(Exception e){r.paletteNotice="当前配色加载失败，使用内置白昼预览："+e.getMessage();}
        try{r.palette=evaluatePalette(readAsset("themes/default/styles/light/main.lua"),theme);r.styleDir=new File(Config.getStyleDir("light"));}catch(Exception e){r.paletteNotice="配色解析失败："+e.getMessage();}
    }
    private Globals evaluatePalette(String input,Globals theme){Globals g=evaluate("");if(theme!=null){org.luaj.LuaTable mt=new org.luaj.LuaTable();mt.set("__index",theme);g.setmetatable(mt);}g.load(input,"palette preview").call();return g;}
    private final Set<String> keyboardKeyStyles=new HashSet<>();
    private PaletteResolver previewResolver(){return new PaletteResolver(palette,mode==2?keyboardKeyStyles:Collections.emptySet());}
    private Exception parseFailure;
    private ThemeServiceConfig[] themeServices;
    private List<PresetKeyConfig> presetKeys;
    private String presetError;
    private String readDefaultTheme() throws IOException {try(InputStream in=getAssets().open("themes/default/main.lua");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private String previewPath="key";
    private final PreviewIcons previewIcons = new PreviewIcons();
    private File activeStyleDir;
    private File previewStyleDir(){return mode==1&&file!=null?file.getParentFile():activeStyleDir;}
    private <T> void resource(Work<T> work,Result<T> result){
        if(destroyed)return;resources.execute(()->{try{T data=work.run();if(data!=null)main.post(()->{if(!destroyed)result.accept(data);});}catch(Exception ignored){}});
    }
    private void iconLabel(TextView target,String label,String style,float fallback){
        setIcon(target,label,tint(style,"text_color",Color.BLACK),Math.max(1,dp(Math.max(1,Math.min(80,metric(style,"text_size",fallback))))));
    }
    private void setIcon(TextView target,String label,int color,int pixels){
        target.setText(label);if(label==null||label.isEmpty())return;File dir=previewStyleDir();android.content.res.Resources res=getResources();
        resource(()->previewIcons.span(res,dir,label,color,pixels),span->{if(span instanceof android.text.Spanned)target.setText(span);});
    }
    private void loadFont(TextView target,String name){File dir=previewStyleDir();resource(()->{File f=PreviewIcons.find(dir,name,"fonts");if(f==null)return null;String key=f.getAbsolutePath()+":"+f.lastModified();android.graphics.Typeface font=fonts.get(key);if(font==null){font=android.graphics.Typeface.createFromFile(f);fonts.put(key,font);}return font;},target::setTypeface);}
    private void loadBackground(View target,String name){File dir=previewStyleDir();resource(()->{File f=PreviewIcons.find(dir,name,"images");return f==null?null:android.graphics.drawable.Drawable.createFromPath(f.getAbsolutePath());},drawable->{int l=target.getPaddingLeft(),t=target.getPaddingTop(),r=target.getPaddingRight(),b=target.getPaddingBottom();target.setBackground(drawable);target.setPadding(l,t,r,b);});}
    private String eventLabel(LuaValue event){return eventLabel(event,0);}
    private String eventLabel(LuaValue event,int depth){
        if(depth>8||event.isnil())return "";
        if(event.istable()){
            LuaValue label=event.get("label");if(!label.isnil())return label.tojstring();
            LuaValue states=event.get("states");if(states.istable()&&!states.get(1).isnil())return states.get(1).tojstring();
            for(String field:new String[]{"click","text","send","commit"})if(!event.get(field).isnil())return eventLabel(event.get(field),depth+1);
            return "";
        }
        String name=event.tojstring();
        if(mode==2&&presetKeys!=null){for(PresetKeyConfig preset:presetKeys){if(!preset.name.equals(name))continue;String label=preset.values[0];if(label!=null&&!label.isEmpty())return label;if(preset.states!=null&&preset.states.length>0&&preset.states[0]!=null)return preset.states[0];break;}return name;}
        try{LuaValue preset=com.osfans.trime.Key.presetKeys.get(name);if(preset.istable()){
            LuaValue label=preset.get("label");if(!label.isnil()&&!label.tojstring().isEmpty())return label.tojstring();
            LuaValue states=preset.get("states");if(states.istable()&&!states.get(1).isnil())return states.get(1).tojstring();
        }}catch(Exception ignored){}
        return name;
    }
    private final String[] titles = {"主题编辑器", "配色编辑器", "键盘编辑器", "方案编辑器"};
    @Override public void onCreate(Bundle state) {
        setTheme(Build.VERSION.SDK_INT >= 29 ? android.R.style.Theme_DeviceDefault_DayNight : android.R.style.Theme_DeviceDefault);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        super.onCreate(state);
        mode = Math.max(0, Math.min(3, getIntent().getIntExtra(MODE, 0)));
        setTitle(titles[mode]);
        ui = new EditorAppearance(this);
        root = column(); root.setPadding(0,0,0,0); root.setBackgroundColor(ui.page); setContentView(root);
        toolbar = new Toolbar(this); toolbar.setBackgroundColor(ui.surface); toolbar.setTitleTextColor(ui.ink); toolbar.setSubtitleTextColor(ui.muted);
        toolbar.setMinimumHeight((int)(56 * getResources().getDisplayMetrics().density + 0.5f));
        toolbar.setTitle(titles[mode]);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        if(toolbar.getNavigationIcon()!=null) toolbar.getNavigationIcon().setTint(ui.ink);
        toolbar.setNavigationContentDescription("返回");
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        EdgeToEdge.apply(this, root);
        toolbar.setOnMenuItemClickListener(this::onEditorMenu);
        refreshEditorMenu();
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        panel=column(); panel.setPadding(dp(16),dp(12),dp(16),dp(20)); scroll.addView(panel);
        section(panel,"开始编辑","选择配置文件后即可编辑，修改完成后点击保存。"); choose();
    }
    /** One persistent save action; infrequent actions remain in the overflow. */
    private void refreshEditorMenu(){
        android.view.Menu menu=toolbar.getMenu();menu.clear();
        toolbar.setTitle(mergeDraft==null?titles[mode]:"合并预览");
        android.text.SpannableString saveTitle=new android.text.SpannableString(mergeDraft==null?"保存":"确认合并");
        if(file!=null)saveTitle.setSpan(new android.text.style.ForegroundColorSpan(ui.accent),0,saveTitle.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(0,MENU_SAVE,0,saveTitle).setEnabled(file!=null&&(mergeDraft==null||mergeDraft.page==0)).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        if(Build.VERSION.SDK_INT>=23){
            android.graphics.drawable.Drawable overflow=toolbar.getOverflowIcon();
            if(overflow!=null){overflow=overflow.mutate();overflow.setTint(ui.ink);toolbar.setOverflowIcon(overflow);}
        }
        if(mergeDraft!=null){
            if(mergeDraft.page!=0)menu.add(0,MENU_MERGE_RESULT,1,"返回合并结果");
            if(mergeDraft.page!=1)menu.add(0,MENU_MERGE_ORIGINAL,2,"对照原始源码");
            if(mergeDraft.page!=2)menu.add(0,MENU_MERGE_PATCH,3,"对照当前补丁");
            if(mergeDraft.page==0){menu.add(0,MENU_UNDO,4,"撤销");menu.add(0,MENU_REDO,5,"重做");}
            menu.add(0,MENU_FIND,6,"查找");menu.add(0,MENU_LINE,7,"跳转行");menu.add(0,MENU_MERGE_CANCEL,8,"取消合并");return;
        }
        menu.add(0,MENU_CHOOSE,1,"选择文件");
        if(file==null)return;
        menu.add(0,MENU_INFO,10,"文件信息");
        menu.add(0,MENU_MODE,2,editingBase||!visual?"返回可视化":mode==3?"编辑源码":"编辑补丁源码");
        if(mode!=3&&!editingBase){
            menu.add(0,MENU_BASE,3,"编辑原始源码");
            menu.add(0,MENU_MERGE,9,"合并补丁到源码");
            if(visual)menu.add(0,MENU_CLEAR,8,"清除补丁");
        }
        if(editingBase||!visual){
            if(mode==3)menu.add(0,MENU_WHITESPACE,8,"显示空白符号").setCheckable(true).setChecked(yamlWhitespace);
            menu.add(0,MENU_UNDO,4,"撤销");menu.add(0,MENU_REDO,5,"重做");
            menu.add(0,MENU_FIND,6,"查找");menu.add(0,MENU_LINE,7,"跳转行");
        }
    }
    private void updateEditorSubtitle(boolean saved){
        if(file==null){toolbar.setSubtitle(null);return;}
        String name=(mode<2?file.getParentFile().getName()+" / ":"")+file.getName();
        if(mode!=3){
            if(editingBase)name+=" · 原始源码";
            else if(!visual)name=(mode<2?file.getParentFile().getName()+" / ":"")+LuaPatchManager.file(file).getName();
        }else if(!visual)name+=" · 源码";
        toolbar.setSubtitle(name+(saved?" · 已保存":""));
    }
    private boolean onEditorMenu(android.view.MenuItem item){
        if(busy||destroyed)return true;
        switch(item.getItemId()){
            case MENU_CHOOSE:choose();return true;
            case MENU_SAVE:if(mergeDraft!=null)confirmMerge();else save();return true;
            case MENU_MERGE:startMerge();return true;
            case MENU_MERGE_RESULT:switchMergePage(0);return true;
            case MENU_MERGE_ORIGINAL:switchMergePage(1);return true;
            case MENU_MERGE_PATCH:switchMergePage(2);return true;
            case MENU_MERGE_CANCEL:cancelMerge();return true;
            case MENU_MODE:toggleEditorMode();return true;
            case MENU_BASE:if(file!=null&&mode!=3)guard(()->{source=original;editingBase=true;render();});return true;
            case MENU_CLEAR:if(file!=null&&mode!=3&&visual&&!editingBase)new AlertDialog.Builder(this).setMessage("恢复原始配置？保存后生效。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->background("正在恢复…",()->parse("",true),r->{source="";install(r);render();})).show();return true;
            case MENU_WHITESPACE:yamlWhitespace=!yamlWhitespace;if(codeEditor!=null&&mode==3)codeEditor.setNonPrintingCharVisibility(yamlWhitespace);item.setChecked(yamlWhitespace);return true;
            case MENU_UNDO:if(codeEditor!=null)codeEditor.undo();return true;
            case MENU_REDO:if(codeEditor!=null)codeEditor.redo();return true;
            case MENU_FIND:if(codeEditor!=null)codeEditor.search();return true;
            case MENU_LINE:if(codeEditor!=null)codeEditor.gotoLine();return true;
            case MENU_INFO:if(file!=null)new AlertDialog.Builder(this).setTitle("文件信息").setMessage(file.getAbsolutePath()+(mode==3?"":"\n\n补丁："+LuaPatchManager.file(file).getAbsolutePath())).setPositiveButton("关闭",null).show();return true;
            default:return false;
        }
    }
    private void toggleEditorMode(){
        if(file==null||busy)return;
        if(editingBase){guard(this::leaveBaseEditor);return;}
        syncRaw();final boolean next=!visual;final String input=source;
        background("正在解析…",()->parse(input,next),r->{visual=next;install(r);render();});
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this); l.setOrientation(1); l.setPadding(dp(12),dp(8),dp(12),dp(8)); return l;}
    private int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private Button button(LinearLayout l,String name,View.OnClickListener action){
        Button b=new Button(this); b.setAllCaps(false); b.setText(name); b.setTextSize(14); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(8),dp(6),dp(8),dp(6));
        boolean primary="保存".equals(name); b.setTextColor(primary?ui.onAccent:ui.accent); ui.clickable(b,primary?ui.accent:ui.soft,14,0); if(Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null); b.setOnClickListener(action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(l.getOrientation()==0?0:-1,-2,l.getOrientation()==0?1:0); lp.setMargins(dp(3),dp(4),dp(3),dp(4)); l.addView(b,lp); return b;
    }
    private void text(LinearLayout l,String s){TextView t=ui.label(s,13,ui.muted,false);t.setPadding(0,dp(6),0,dp(6));l.addView(t);}
    private void section(LinearLayout l,String title,String detail){TextView t=ui.label(title,18,ui.ink,true);t.setPadding(0,dp(14),0,dp(4));l.addView(t);if(detail!=null)text(l,detail);}
    private LinearLayout card(LinearLayout parent){LinearLayout c=column(); c.setBackground(ui.shape(ui.surface,18,ui.outline)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(8));parent.addView(c,lp);return c;}
    private void styleInput(EditText e){e.setTextColor(ui.ink);e.setHintTextColor(ui.muted);e.setTextSize(15);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(ui.shape(ui.surface,12,ui.outline));e.setMinHeight(dp(48));}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("编辑器").setMessage(e.toString()).setPositiveButton("确定",null).show();}
    private ScrollView wrap(View v){ScrollView scroll=new ScrollView(this);scroll.addView(v);return scroll;}
    private void syncRaw(){if(editingBase||mergeDraft!=null)return;if(codeEditor!=null)source=codeEditor.getText().toString();}
    private boolean dirty(){if(editingBase)return codeEditor!=null&&!Objects.equals(baseSource,codeEditor.getText().toString());syncRaw();return file!=null&&!Objects.equals(original,source);}
    private void guard(Runnable action){if(busy)return;if(!dirty()){action.run();return;}new AlertDialog.Builder(this).setMessage("有未保存的修改，放弃修改？").setPositiveButton("放弃",(d,w)->action.run()).setNegativeButton("取消",null).show();}
    @Override public void onBackPressed(){if(busy)return;if(mergeDraft!=null){cancelMerge();return;}if(editingBase){guard(this::leaveBaseEditor);return;}if(!busy&&mode==3&&visual&&schemeDocument!=null&&schemeCurrent().parent!=null){schemeNavigate(schemeCurrent().parent);return;}guard(()->finish());}
    private void choose(){guard(()->background("正在查找文件…",()->{
        List<File> files=new ArrayList<>();
        if(mode==0){for(File d:list(new File(Config.getThemeDir())))add(files,new File(d,"main.lua"));}
        if(mode==1){for(File d:list(new File(Config.getStyleDir())))add(files,new File(d,"main.lua"));}
        if(mode==2){for(File f:list(new File(Config.getKeyboardDir())))if(f.getName().endsWith(".lua")&&!LuaPatchManager.isPatch(f.getName()))add(files,f);}
        if(mode==3){Map<String,File> map=new TreeMap<>();for(File d:new File[]{DataManager.getSharedDataDir(),DataManager.getUserDataDir()})for(File f:list(d))if(f.isFile()&&(f.getName().endsWith(".schema.yaml")||f.getName().endsWith(".custom.yaml")||f.getName().equals("default.yaml")))map.put(f.getName(),f);files.addAll(map.values());}
        return files;
    },files->{
        String[] names=new String[files.size()];for(int i=0;i<names.length;i++)names[i]=mode<2?files.get(i).getParentFile().getName():files.get(i).getName();
        new AlertDialog.Builder(this).setTitle(titles[mode]+" · 选择文件").setItems(names,(d,i)->open(files.get(i))).setNegativeButton("取消",null).show();
    }));}
    private File[] list(File dir){File[] a=dir.listFiles();if(a==null)return new File[0];Arrays.sort(a);return a;}
    private void add(List<File> a,File f){if(f.isFile())a.add(f);}
    private static String read(File f)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static final class Opened {String base,input;Parsed parsed;}
    private void open(File f){background("正在读取配置…",()->{Opened r=new Opened();r.base=read(f);r.input=mode==3?r.base:LuaPatchManager.read(f);r.parsed=parse(r.input,true,r.base);return r;},r->{baseSource=r.base;source=original=r.input;file=mode==3?new File(DataManager.getUserDataDir(),f.getName()):f;editingBase=false;visual=true;schemeLocation.clear();install(r.parsed);render();});}
    private Globals evaluatePatched(String base,String patch){Globals g=evaluate(base);LuaPatchManager.applyChecked(g,g,base,patch);return g;}
    private Globals evaluateFile(File f)throws IOException{return evaluatePatched(read(f),LuaPatchManager.read(f));}
    private String compact(String previous,String code){Globals g=evaluate(baseSource);LuaPatchManager.Snapshot before=new LuaPatchManager.Snapshot(g);LuaPatchManager.applyChecked(g,g,baseSource,previous);g.load(code,"editor change").call();return LuaPatchManager.bindToBase(baseSource,before.delta());}
    private Globals evaluate(String s){Globals g=JsePlatform.standardGlobals();for(String k:new String[]{"io","os","luajava","debug","package","require","dofile","loadfile","load","loadstring","module"})g.set(k,LuaValue.NIL);g.load(s,"editor").call();return g;}
    private void render(){cancelPaletteList();paletteDetail=null;paletteList=null;paletteDetailGroup=null;if(codePane!=null){if(codeEditor!=null)codeEditor.cancelSpanning();root.removeView(codePane);codePane=null;codeEditor=null;}scroll.setVisibility(View.VISIBLE);panel.removeAllViews();refreshEditorMenu();if(file==null)return;if(mergeDraft!=null){renderMergePreview();return;}if(editingBase||!visual){renderLuaSource();return;}updateEditorSubtitle(false);
        try{if(parseFailure!=null)throw parseFailure;if(mode==0){section(panel,"主题属性","编辑主题基本信息及默认资源。");for(String k:new String[]{"name","author","style","keyboard"})field(panel,k,k,false);if(themeServices!=null)for(ThemeServiceConfig service:themeServices)renderThemeService(service);renderPresetKeys();}
        if(mode==1){renderPalette();}
        if(mode==2)renderKeyboardEditor();
        if(mode==3)renderScheme();
        }catch(Exception e){text(panel,"无法生成预览："+e.getMessage()+"。可切换源码编辑。");}}
    // Scheme editor uses the same background queue as the other editors. UI reads immutable parsed nodes.
    private SchemaDocument schemeDocument;
    private final ArrayList<String> schemeLocation=new ArrayList<>();
    private SchemaDocument.Node schemeCurrent(){SchemaDocument.Node n=schemeDocument.root;for(String k:schemeLocation){SchemaDocument.Node next=n.child(k);if(next==null||(next.kind!=SchemaDocument.Kind.MAP&&next.kind!=SchemaDocument.Kind.LIST))break;n=next;}return n;}
    private void schemeNavigate(SchemaDocument.Node n){schemeLocation.clear();for(SchemaDocument.Node x=n;x.parent!=null;x=x.parent)schemeLocation.add(0,x.key);render();scroll.scrollTo(0,0);}
    private String schemeName(SchemaDocument.Node n){SchemaProperties.Field f=SchemaProperties.get(schemeDocument,n.path());return f!=null?f.name:n.key;}
    private String schemeSummary(SchemaDocument.Node n){if(n.kind==SchemaDocument.Kind.MAP||n.kind==SchemaDocument.Kind.LIST){String count=(n.kind==SchemaDocument.Kind.MAP?"映射 · ":"列表 · ")+n.children.size()+" 项";if(n.kind==SchemaDocument.Kind.MAP&&n.parent!=null&&n.parent.kind==SchemaDocument.Kind.LIST){for(String k:new String[]{"name","schema","accept"}){SchemaDocument.Node item=n.child(k);if(item!=null&&item.scalar())return item.value+" · "+count;}SchemaDocument.Node options=n.child("options");if(options!=null&&!options.children.isEmpty())return options.children.get(0).value+" 等 · "+count;}return count;}if(n.kind==SchemaDocument.Kind.OPAQUE)return "复杂 YAML · 源码编辑";return n.kind==SchemaDocument.Kind.EMPTY||n.kind==SchemaDocument.Kind.NULL?"空值 (null)":n.value;}
    private void renderScheme(){
        if(schemeDocument==null){text(panel,"未能解析当前方案，请切换源码编辑。");return;}
        section(panel,"方案属性","显示 YAML 源文件的实际定义；未展开引用或部署后的生效值。修改后请保存并部署。");
        SchemaDocument.Node current=schemeCurrent();
        LinearLayout nav=new LinearLayout(this);panel.addView(nav);button(nav,"全部分组",v->schemeNavigate(schemeDocument.root));if(current.parent!=null)button(nav,"返回上一级",v->schemeNavigate(current.parent));button(nav,"添加",v->schemeAdd(current));
        LinearLayout loc=card(panel);text(loc,current.parent==null?"根配置":current.path());
        SchemaProperties.Field group=SchemaProperties.get(schemeDocument,current.path());if(group!=null)text(loc,group.desc);
        EditText filter=new EditText(this);filter.setSingleLine(true);filter.setHint("搜索当前分组的属性、值或说明");styleInput(filter);panel.addView(filter);
        LinearLayout entries=column();entries.setPadding(0,0,0,0);panel.addView(entries);schemeRows(entries,current,"");
        filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){schemeRows(entries,current,s.toString());}public void afterTextChanged(Editable e){}});
        button(panel,"Rime 官方文档与源码",v->new AlertDialog.Builder(this).setTitle("属性说明来源").setItems(new String[]{"Rime 输入方案设计书","Rime 定制指南","朙月拼音官方方案","Rime 默认配置","librime 源码"},(d,i)->schemeLink(new String[]{SchemaProperties.WIKI,SchemaProperties.CUSTOM,SchemaProperties.EXAMPLE,SchemaProperties.DEFAULT,SchemaProperties.CORE}[i])).setNegativeButton("关闭",null).show());
    }
    private void schemeRows(LinearLayout list,SchemaDocument.Node parent,String query){
        list.removeAllViews();String q=query.toLowerCase(Locale.ROOT).trim();int count=0;for(SchemaDocument.Node n:parent.children){SchemaProperties.Field f=SchemaProperties.get(schemeDocument,n.path());String summary=schemeSummary(n);if(!q.isEmpty()&&!(n.key+" "+summary+" "+(f==null?"":f.name+" "+f.desc)).toLowerCase(Locale.ROOT).contains(q))continue;
            if(count++>=150){text(list,"仅显示前 150 项，请缩小搜索范围。");break;}
            LinearLayout item=card(list);ui.clickable(item,ui.surface,16,ui.outline);item.setFocusable(true);
            LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);item.addView(heading);
            TextView title=ui.label(schemeName(n),16,ui.ink,true);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));heading.addView(ui.label("›",22,ui.accent,true));
            if(f!=null&&!f.name.equals(n.key))text(item,n.key);TextView value=ui.label(summary,14,ui.ink,false);value.setMaxLines(3);value.setEllipsize(android.text.TextUtils.TruncateAt.END);item.addView(value);if(f!=null)text(item,f.desc);else if(n.scalar())text(item,"自定义字段 · 按现有类型编辑");
            item.setOnClickListener(v->{if(n.kind==SchemaDocument.Kind.MAP||n.kind==SchemaDocument.Kind.LIST)schemeNavigate(n);else if(n.scalar())schemeEditScalar(n);else new AlertDialog.Builder(this).setTitle(n.key).setMessage("此结构需要源码模式编辑，原始内容不会被覆盖。").setPositiveButton("切换源码",(d,w)->{visual=false;render();}).setNegativeButton("取消",null).show();});
            item.setOnLongClickListener(v->{schemeActions(n);return true;});
        }if(count==0)text(list,"没有匹配的属性。可通过添加创建配置。");
    }
    private void schemeLink(String url){try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){new AlertDialog.Builder(this).setMessage(url).setPositiveButton("关闭",null).show();}}
    private void schemeActions(SchemaDocument.Node n){ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();SchemaProperties.Field f=SchemaProperties.get(schemeDocument,n.path());if(f!=null){labels.add("查看官方来源");actions.add(()->schemeLink(f.url));}if(n.parent.kind==SchemaDocument.Kind.LIST){int i=n.parent.children.indexOf(n);if(i>0){labels.add("向上移动");actions.add(()->schemeChange(doc->doc.move(n,-1)));}if(i<n.parent.children.size()-1){labels.add("向下移动");actions.add(()->schemeChange(doc->doc.move(n,1)));}}if(n.kind!=SchemaDocument.Kind.OPAQUE){labels.add("删除本项定义");actions.add(()->new AlertDialog.Builder(this).setTitle("删除属性？").setMessage(n.path()+"\n删除后可能恢复部署时的默认值或引用值。").setPositiveButton("删除",(d,w)->schemeChange(doc->doc.remove(n))).setNegativeButton("取消",null).show());}if(labels.isEmpty())return;new AlertDialog.Builder(this).setTitle(n.key).setItems(labels.toArray(new String[0]),(d,i)->actions.get(i).run()).setNegativeButton("取消",null).show();}
    private interface SchemeMutation {String run(SchemaDocument doc);}
    private void schemeChange(SchemeMutation mutation){final SchemaDocument doc=schemeDocument;if(doc==null||busy)return;background("正在更新 YAML…",()->{String changed=mutation.run(doc);return new SchemaDocument(changed);},next->{schemeDocument=next;source=next.source;render();});}
    private String[] schemeTypes={"字符串","布尔值","整数","小数","映射（Map）","列表（List）","空值（null）"};
    private SchemaDocument.Kind[] schemeKinds={SchemaDocument.Kind.STRING,SchemaDocument.Kind.BOOL,SchemaDocument.Kind.INT,SchemaDocument.Kind.NUMBER,SchemaDocument.Kind.MAP,SchemaDocument.Kind.LIST,SchemaDocument.Kind.NULL};
    private void schemeEditScalar(SchemaDocument.Node n){SchemaProperties.Field f=SchemaProperties.get(schemeDocument,n.path());SchemaDocument.Kind type=(n.kind==SchemaDocument.Kind.EMPTY||n.kind==SchemaDocument.Kind.NULL)?SchemaDocument.Kind.STRING:n.kind;if(f!=null&&(f.kind==SchemaDocument.Kind.STRING||f.kind==SchemaDocument.Kind.BOOL||f.kind==SchemaDocument.Kind.INT||f.kind==SchemaDocument.Kind.NUMBER))type=f.kind;final SchemaDocument.Kind target=type;final String initial=n.kind==SchemaDocument.Kind.NULL?"":n.value;
        if(type==SchemaDocument.Kind.BOOL){final String[] options={"开启（true）","关闭（false）"};final int[] selected={"true".equalsIgnoreCase(initial)?0:"false".equalsIgnoreCase(initial)?1:-1};new AlertDialog.Builder(this).setTitle(schemeName(n)).setSingleChoiceItems(options,selected[0],(d,i)->selected[0]=i).setPositiveButton("应用",(d,w)->{if(selected[0]>=0){String chosen=selected[0]==0?"true":"false";if(!chosen.equalsIgnoreCase(initial))schemeChange(doc->doc.replace(n,chosen,target));}}).setNegativeButton("取消",null).show();return;}
        LinearLayout form=column();text(form,n.path());if(f!=null){text(form,f.desc);button(form,"官方来源",v->schemeLink(f.url));}EditText value=new EditText(this);styleInput(value);value.setText(initial);if(type==SchemaDocument.Kind.INT||type==SchemaDocument.Kind.NUMBER)value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED|(type==SchemaDocument.Kind.NUMBER?android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:0));else{value.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);value.setMinLines(2);}form.addView(value);String[] choices=SchemaProperties.choices(n);if(choices.length>0)button(form,"选择内置值",v->new AlertDialog.Builder(this).setItems(choices,(d,i)->value.setText(choices[i])).show());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(schemeName(n)).setView(wrap(form)).setPositiveButton("应用",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String s=value.getText().toString();try{SchemaDocument.encode(s,target);}catch(Exception e){value.setError(e.getMessage());return;}dialog.dismiss();if(!s.equals(initial))schemeChange(doc->doc.replace(n,s,target));}));dialog.show();
    }
    private void schemeAdd(SchemaDocument.Node parent){
        if(parent.kind==SchemaDocument.Kind.LIST){schemeAddForm(parent,"",null);return;}
        Map<String,SchemaProperties.Field> suggestions=parent.path().equals("patch")?SchemaProperties.patchSuggestions(schemeDocument,parent):SchemaProperties.suggestions(schemeDocument,parent);if(parent==schemeDocument.root&&file.getName().endsWith(".custom.yaml"))suggestions.keySet().retainAll(Arrays.asList("patch","__include","__patch"));
        ArrayList<String> names=new ArrayList<>(suggestions.keySet()),labels=new ArrayList<>();for(String name:names)labels.add(suggestions.get(name).name+" · "+name);labels.add("自定义属性 / 扩展字段");new AlertDialog.Builder(this).setTitle("添加属性").setItems(labels.toArray(new String[0]),(d,i)->schemeAddForm(parent,i<names.size()?names.get(i):"",i<names.size()?suggestions.get(names.get(i)):null)).setNegativeButton("取消",null).show();
    }
    private void schemeAddForm(SchemaDocument.Node parent,String suggested,SchemaProperties.Field known){
        LinearLayout form=column();if(known!=null)text(form,known.desc);EditText key=new EditText(this);styleInput(key);key.setHint("属性名称 / 补丁路径");key.setText(suggested);if(parent.kind==SchemaDocument.Kind.MAP)form.addView(key);
        int initial=known==null?0:Arrays.asList(schemeKinds).indexOf(known.kind);if(known==null&&parent.kind==SchemaDocument.Kind.LIST&&(parent.path().equals("switches")||parent.path().endsWith("/bindings")||parent.path().equals("schema_list")))initial=4;final int[] selected={Math.max(0,initial)};
        TextView type=ui.label("",15,ui.ink,true);ui.clickable(type,ui.surface,12,ui.muted);type.setPadding(dp(14),dp(12),dp(14),dp(12));type.setMinHeight(dp(48));type.setFocusable(true);form.addView(type);
        EditText val=new EditText(this);styleInput(val);val.setHint("属性值");form.addView(val);Switch bool=new Switch(this);bool.setText("启用");form.addView(bool);
        Runnable refresh=()->{int i=selected[0];type.setText("类型 · "+schemeTypes[i]+"   ▾");val.setVisibility(i==1||i>=4?View.GONE:View.VISIBLE);bool.setVisibility(i==1?View.VISIBLE:View.GONE);if(i==2||i==3)val.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED|(i==3?android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:0));else val.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);};refresh.run();
        type.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("属性类型").setSingleChoiceItems(schemeTypes,selected[0],(d,i)->{selected[0]=i;refresh.run();d.dismiss();}).setNegativeButton("取消",null).show());
        String[] componentChoices=SchemaProperties.listChoices(parent);if(componentChoices.length>0)button(form,"选择内置组件",v->new AlertDialog.Builder(this).setItems(componentChoices,(d,i)->{selected[0]=0;refresh.run();val.setText(componentChoices[i]);}).show());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(parent.kind==SchemaDocument.Kind.LIST?"添加列表项":"添加属性").setView(wrap(form)).setPositiveButton("添加",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=key.getText().toString();SchemaDocument.Kind k=schemeKinds[selected[0]];String value=k==SchemaDocument.Kind.BOOL?Boolean.toString(bool.isChecked()):val.getText().toString();if(parent.kind==SchemaDocument.Kind.MAP&&(name.trim().isEmpty()||parent.child(name)!=null)){key.setError("名称不能为空或重复");return;}try{SchemaDocument.encode(value,k);}catch(Exception e){val.setError(e.getMessage());return;}dialog.dismiss();schemeChange(doc->doc.add(parent,name,value,k));}));dialog.show();
    }

    private void renderPresetKeys(){
        section(panel,"Preset keys · 预设按键","管理主题中的按键事件定义，不修改键盘排列。");
        LinearLayout group=card(panel);
        if(presetKeys==null){text(group,"无法读取预设按键："+presetError+"。请使用源码模式。");return;}
        int count=0;for(PresetKeyConfig k:presetKeys)if(k.local)count++;
        text(group,"主题定义 "+count+" 个 · 内置继承 "+(presetKeys.size()-count)+" 个");
        button(group,"管理预设按键",v->showPresetKeys());
        button(group,"添加预设按键",v->editPresetKey(PresetKeyConfig.empty()));
    }
    private void showPresetKeys(){
        if(busy||presetKeys==null)return;
        LinearLayout content=column();
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("搜索名称、标签或事件");styleInput(search);content.addView(search);
        CheckBox localOnly=new CheckBox(this);localOnly.setText("仅显示主题定义");localOnly.setTextColor(ui.ink);content.addView(localOnly);
        ListView list=new ListView(this);list.setDividerHeight(dp(1));content.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));
        ArrayList<PresetKeyConfig> visible=new ArrayList<>();
        ArrayAdapter<PresetKeyConfig> adapter=new ArrayAdapter<PresetKeyConfig>(this,android.R.layout.simple_list_item_1,visible){
            @Override public View getView(int pos,View convert,android.view.ViewGroup parent){
                TextView row=ui.label(getItem(pos).summary(),15,ui.ink,false);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinHeight(dp(52));ui.clickable(row,ui.surface,8,0);return row;
            }
        };list.setAdapter(adapter);
        Runnable filter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(PresetKeyConfig k:presetKeys){if(localOnly.isChecked()&&!k.local)continue;String hay=k.name+" "+Arrays.toString(k.values);if(hay.toLowerCase(Locale.ROOT).contains(q))visible.add(k);}adapter.notifyDataSetChanged();};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});localOnly.setOnCheckedChangeListener((b,c)->filter.run());filter.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("预设按键").setView(content).setNegativeButton("关闭",null).setPositiveButton("添加",(d,w)->editPresetKey(PresetKeyConfig.empty())).create();
        list.setOnItemClickListener((parent,view,pos,id)->{PresetKeyConfig key=visible.get(pos);dialog.dismiss();presetKeyActions(key);});
        dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void presetKeyActions(PresetKeyConfig key){
        String[] actions=key.local?new String[]{"编辑","删除主题定义"}:new String[]{"创建主题覆盖并编辑"};
        new AlertDialog.Builder(this).setTitle(key.name).setItems(actions,(d,i)->{if(i==0)editPresetKey(key);else new AlertDialog.Builder(this).setTitle("删除 "+key.name+"？").setMessage((key.fallback?"删除后将恢复内置同名按键。":"删除后该预设名称不再由主题定义。")+" 键盘、工具栏中引用该名称的位置不会自动修改。").setNegativeButton("取消",(x,y)->showPresetKeys()).setPositiveButton("删除",(x,y)->applyPresetChange(PresetKeyConfig.delete(key.name))).show();}).setNegativeButton("返回",(d,w)->showPresetKeys()).show();
    }
    private void editPresetKey(PresetKeyConfig key){
        if(busy||destroyed)return;
        if(!key.editable){new AlertDialog.Builder(this).setMessage("该预设不是事件表，请使用源码模式编辑。").setPositiveButton("确定",null).show();return;}
        LinearLayout box=column();boolean adding=key.name.isEmpty();
        text(box,"预设名称（区分大小写，键盘通过名称引用）");EditText name=new EditText(this);styleInput(name);name.setSingleLine(true);name.setText(key.name);name.setEnabled(adding);box.addView(name);
        if(!key.local)text(box,"内置按键只读，应用后在当前主题创建独立覆盖。");
        else if(!adding)text(box,"只修改所选属性，保留其他字段；共享同一 Lua 表的按键会同步变化。");
        EditText[] edits=new EditText[PresetKeyConfig.TEXT.length];CheckBox[] enabled=new CheckBox[edits.length];
        for(int i=0;i<edits.length;i++){
            final int index=i;CheckBox check=new CheckBox(this);check.setText(PresetKeyConfig.TITLES[i]+" · "+PresetKeyConfig.TEXT[i]);check.setTextColor(ui.ink);check.setChecked(key.values[i]!=null);check.setEnabled(key.valid[i]);box.addView(check);enabled[i]=check;
            EditText e=new EditText(this);styleInput(e);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setText(key.values[i]==null?"":key.values[i]);e.setEnabled(key.valid[i]&&check.isChecked());box.addView(e);edits[i]=e;check.setOnCheckedChangeListener((b,on)->edits[index].setEnabled(on));
            if(!key.valid[i])text(box,"非字符串值保留，请使用源码模式修改。");
        }
        text(box,"勾选字段可设置值，取消勾选则清除字段；空字符串和未设置不同。shift_lock 支持 click / double / long。");
        Spinner[] flags=new Spinner[PresetKeyConfig.BOOL.length];
        for(int i=0;i<flags.length;i++){text(box,PresetKeyConfig.BOOL_TITLES[i]+" · "+PresetKeyConfig.BOOL[i]);Spinner sp=new Spinner(this);sp.setBackground(ui.shape(ui.surface,10,ui.outline));ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"未设置（默认）","关闭","开启"});a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);sp.setAdapter(a);sp.setSelection(Math.max(0,key.flags[i]));sp.setEnabled(key.flags[i]>=0);box.addView(sp,new LinearLayout.LayoutParams(-1,dp(48)));flags[i]=sp;}
        CheckBox useStates=new CheckBox(this);useStates.setText("状态标签 · states");useStates.setTextColor(ui.ink);useStates.setChecked(key.states!=null);useStates.setEnabled(key.statesEditable);box.addView(useStates);
        EditText states=new EditText(this);styleInput(states);states.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);states.setMinLines(2);String initial=key.states==null?"":android.text.TextUtils.join("\n",key.states);states.setText(initial);states.setEnabled(key.statesEditable&&useStates.isChecked());box.addView(states);useStates.setOnCheckedChangeListener((b,on)->states.setEnabled(on));text(box,"每行一个状态标签。复杂状态表请使用源码模式。");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(adding?"添加预设按键":"编辑 "+key.name).setView(wrap(box)).setNegativeButton("取消",(d,w)->showPresetKeys()).setPositiveButton("应用",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String n=name.getText().toString();if(n.trim().isEmpty()){name.setError("请输入名称");return;}if(adding){for(PresetKeyConfig k:presetKeys)if(k.name.equals(n)){name.setError("名称已存在，请从列表编辑");return;}}
            String[] values=new String[edits.length];for(int i=0;i<values.length;i++)values[i]=enabled[i].isChecked()?edits[i].getText().toString():null;
            int[] bool=new int[flags.length];for(int i=0;i<bool.length;i++)bool[i]=flags[i].getSelectedItemPosition();
            String value=states.getText().toString();String[] stateValues=!useStates.isChecked()?null:key.states!=null&&value.equals(initial)?key.states:value.isEmpty()?new String[0]:value.split("\n",-1);
            try{String code=PresetKeyConfig.assignment(key,n,values,bool,stateValues);dialog.dismiss();applyPresetChange(code);}catch(Exception e){error(e);}
        }));dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void applyPresetChange(String code){
        if(busy||destroyed)return;
        if(code.isEmpty()){showPresetKeys();return;}
        final String previous=source;final int y=scroll.getScrollY();
        background("正在应用预设按键…",()->{String next=compact(previous,code);Parsed r=parse(next,true);if(r.failure!=null)throw r.failure;if(r.presetKeys==null)throw new IllegalArgumentException(r.presetError);return new AbstractMap.SimpleImmutableEntry<>(next,r);},r->{source=r.getKey();install(r.getValue());render();scroll.post(()->scroll.scrollTo(0,y));showPresetKeys();});
    }
    private void renderThemeService(ThemeServiceConfig config) {
        boolean asr="asr_config".equals(config.path);
        section(panel,asr?"ASR 语音识别":"AI 服务",asr?"需要支持 input_audio 的多模态模型；不支持流式输出。":"使用主题自定义 AI 接口；不支持流式输出。");
        LinearLayout group=card(panel);
        text(group,config.path+" · "+(config.configured()?"必要参数已填写":"尚未填写完整"));
        for(int i=0;i<config.names.length;i++){
            String name=config.names[i], value=config.values[i];
            TextView line=ui.label(ThemeServiceConfig.title(name)+" · "+("key".equals(name)?(value.isEmpty()?"未设置":"已设置（隐藏）"):"extra_params".equals(name)?(config.extraTable?"Lua table":value.isEmpty()?"未设置":"JSON 字符串"):value.isEmpty()?"未设置":value),14,ui.ink,false);
            line.setMaxLines(2);line.setEllipsize(android.text.TextUtils.TruncateAt.END);line.setPadding(0,dp(6),0,dp(6));group.addView(line);
        }
        button(group,asr?"编辑 ASR 配置":"编辑 AI 配置",v->editThemeService(config));
    }
    private void editThemeService(ThemeServiceConfig config){
        LinearLayout box=column();
        EditText[] edits=new EditText[config.names.length];
        CheckBox tableMode=new CheckBox(this);tableMode.setText("扩展参数使用 Lua table（取消勾选则使用 JSON 字符串）");tableMode.setChecked(config.extraTable);
        for(int i=0;i<config.names.length;i++){
            String name=config.names[i];boolean multi=name.endsWith("prompt")||"extra_params".equals(name);
            text(box,ThemeServiceConfig.title(name)+" · "+name);
            EditText e=new EditText(this);e.setSingleLine(!multi);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(multi?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
            if("key".equals(name)){e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);e.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());}
            if(multi){e.setGravity(android.view.Gravity.TOP);e.setMinLines(3);e.setMaxLines(8);}
            styleInput(e);e.setText(config.values[i]);box.addView(e);edits[i]=e;
            if("key".equals(name)){
                CheckBox reveal=new CheckBox(this);reveal.setText("显示 API Key");box.addView(reveal);reveal.setOnCheckedChangeListener((b,checked)->{e.setTransformationMethod(checked?null:android.text.method.PasswordTransformationMethod.getInstance());e.setSelection(e.length());});
            }
            if("extra_params".equals(name)){
                box.addView(tableMode);text(box,"JSON 示例：{\"stream\":false}；Lua 示例：{stream=false}。留空表示无扩展参数。");
                if(!config.extraEditable){e.setEnabled(false);tableMode.setEnabled(false);text(box,"此扩展表包含无法安全转换的值，请在源码模式编辑；其他字段仍可修改。");}
            }
        }
        text(box,"密钥将保存在主题补丁及其备份中。应用修改后仍需点击保存，并重新加载主题。");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("编辑 "+("ai_config".equals(config.path)?"AI":"ASR")+" 配置").setView(wrap(box)).setNegativeButton("取消",null).setPositiveButton("应用",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            if(busy||destroyed)return;String[] inputs=new String[edits.length];for(int i=0;i<inputs.length;i++)inputs[i]=edits[i].getText().toString();boolean useTable=tableMode.isChecked();
            final String previous=source;final int y=scroll.getScrollY();dialog.dismiss();
            background("正在应用服务配置…",()->{
                String code=ThemeServiceConfig.assignment(config,inputs,useTable);if(code.isEmpty())return new AbstractMap.SimpleImmutableEntry<String,Parsed>(previous,null);
                String next=compact(previous,code);Parsed result=parse(next,true);
                if(result.failure!=null)throw new IllegalArgumentException("配置解析失败，请检查 Lua 扩展参数语法。");
                return new AbstractMap.SimpleImmutableEntry<>(next,result);
            },result->{if(result.getValue()==null)return;source=result.getKey();install(result.getValue());render();scroll.post(()->scroll.scrollTo(0,y));});
        }));dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private LuaValue value(LuaValue g,String p){if(g==null)return LuaValue.NIL;for(String k:p.split("\\.")){if(!g.istable())return LuaValue.NIL;g=g.get(PaletteProperties.luaKey(k));}return g;}
    private String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    private String literal(String s,LuaValue old,boolean color){if(color&&s.startsWith("#"))return "0x"+String.format(Locale.US,"%08x",Color.parseColor(s));if(old.isnumber()||color){if(s.matches("(?i)0x[0-9a-f]+"))return s;try{double n=Double.parseDouble(s);if(!Double.isNaN(n)&&!Double.isInfinite(n))return Double.toString(n);}catch(Exception ignored){}if(old.isnumber())throw new IllegalArgumentException("请输入有效数字");}return quote(s);}
    private String display(LuaValue v,boolean color){return color&&v.type()==LuaValue.TNUMBER?String.format(Locale.US,"#%08X",(int)v.tolong()):v.isnil()?"":v.tojstring();}
    private String propertyName(String key) {
        switch(key) {
            case "name": return "名称"; case "author": return "作者";
            case "style": return "样式"; case "keyboard": return "键盘";
            case "hint_long": return "长按助记"; case "hint_up": return "上滑助记"; case "hint_down": return "下滑助记"; case "hint_left": return "左滑助记"; case "hint_right": return "右滑助记"; case "label": return "标签"; case "click": return "单击";
            case "long_click": return "长按"; case "swipe_left": return "左滑";
            case "swipe_right": return "右滑"; case "swipe_up": return "上滑";
            case "swipe_down": return "下滑"; case "width": return "宽度";
            case "height": return "高度"; case "grow": return "布局权重";
            case "background": return "背景"; case "key": return "按键";
            case "text_color": return "文字颜色"; case "corner_radius": return "圆角";
            case "text_size": return "字号"; case "pressed": return "按下状态";
            case "functional": return "功能键"; case "enter": return "回车键";
            case "candidate": return "候选栏"; case "comment": return "注释";
            case "space": return "空格键"; case "enter2": return "备用回车键"; case "popup": return "弹出键盘"; case "symbol": return "符号面板"; case "clipboard": return "剪贴板"; case "toolbar": return "工具栏"; case "preedit": return "预编辑"; case "composition": return "悬浮窗口"; case "hint": return "助记"; case "preview": return "按键预览"; case "mask": return "遮罩"; case "margins": return "外边距"; case "padding": return "内边距"; case "elevation": return "阴影高度"; case "shadow_color": return "阴影颜色"; case "stroke_width": return "描边宽度"; case "stroke_color": return "描边颜色"; case "scale_x": return "水平缩放"; case "scale_y": return "垂直缩放"; case "translation_x": return "水平位移"; case "translation_y": return "垂直位移"; case "translation_z": return "Z 轴位移"; case "offset_x": return "水平偏移"; case "offset_y": return "垂直偏移"; case "show": return "显示"; case "font": return "字体"; case "gravity": return "对齐方式"; case "long_click_time": return "长按时间（ms）"; case "repeat_click_time": return "连击间隔（ms）"; case "left": return "左"; case "right": return "右"; case "top": return "上"; case "bottom": return "下"; case "up": return "上方"; case "down": return "下方"; case "expanded": return "展开面板"; case "indicator_color": return "指示器颜色"; case "vibration_enabled": return "振动开关"; case "sound_enabled": return "声音开关";
            default: return key;
        }
    }
    private String propertyTitle(String path) {
        StringBuilder title = new StringBuilder();
        for(String part:path.split("\\.")) { if(title.length()>0)title.append(" · "); title.append(propertyName(part)); }
        return title.toString();
    }
    private void field(LinearLayout l,String name,String p,boolean color) {
        PaletteGroupSnapshot.Field cached=mode==1&&paletteGroups!=null?paletteGroups.field(p):null;
        LuaValue actual=mode==1?(cached==null?new PaletteResolver(model).value(p):cached.actual):value(model,p);
        LuaValue old=mode==1&&actual.isnil()?paletteSchema.fields.getOrDefault(p,LuaValue.NIL):actual;
        String propertyOrigin=mode==1?(cached==null?new PaletteResolver(model).origin(p):cached.origin):"";
        LinearLayout item=new LinearLayout(this); item.setGravity(android.view.Gravity.CENTER_VERTICAL); item.setPadding(dp(14),dp(12),dp(14),dp(12)); item.setMinimumHeight(dp(68)); ui.clickable(item,ui.surface,14,ui.outline); LinearLayout.LayoutParams itemLp=new LinearLayout.LayoutParams(-1,-2); itemLp.setMargins(0,dp(4),0,dp(4)); l.addView(item,itemLp);
        LinearLayout labels=column();labels.setPadding(0,0,dp(8),0);item.addView(labels,new LinearLayout.LayoutParams(0,-2,1)); labels.addView(ui.label(propertyTitle(name),15,ui.ink,true)); labels.addView(ui.label(display(old,color),13,ui.muted,false)); if(mode==1)labels.addView(ui.label(propertyOrigin,12,ui.muted,false));
        if(color&&old.type()==LuaValue.TNUMBER){View chip=new View(this);chip.setBackground(ui.shape((int)old.tolong(),10,ui.outline));item.addView(chip,new LinearLayout.LayoutParams(dp(34),dp(34)));} TextView arrow=ui.label("  ›",23,ui.muted,false);item.addView(arrow); item.setContentDescription(propertyTitle(name)+"，"+display(old,color)); item.setOnClickListener(v->{
            if(mode==1&&!color&&(old.type()==LuaValue.TNUMBER||old.isboolean())){editScalar(p,old);return;}
            previewPath=p;cancelPreview();
            EditText edit=new EditText(this); edit.setSingleLine(true); edit.setText(display(old,color)); styleInput(edit);
            LinearLayout box=column();
            if(color) {
                ColorPaletteView picker=new ColorPaletteView(this);
                int initial=old.type()==LuaValue.TNUMBER?(int)old.tolong():Color.WHITE;
                picker.setColor(initial);
                float[] hsv=new float[3]; Color.colorToHSV(initial,hsv);
                text(box,"色相"); SeekBar hue=new SeekBar(this); hue.setMax(359); hue.setProgress((int)hsv[0]);
                int[] rainbow=new int[7]; for(int i=0;i<7;i++)rainbow[i]=Color.HSVToColor(new float[]{i*59.999f,1,1});
                GradientDrawable spectrum=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,rainbow);
                spectrum.setCornerRadius(dp(12));hue.setBackground(spectrum); box.addView(hue);
                text(box,"饱和度 / 明度"); picker.setBackground(ui.shape(ui.surface,14,0));picker.setClipToOutline(true);box.addView(picker,new LinearLayout.LayoutParams(-1,dp(200)));
                text(box,"不透明度"); SeekBar opacity=new SeekBar(this); opacity.setMax(255); opacity.setProgress(Color.alpha(initial)); box.addView(opacity);
                View swatch=new View(this); swatch.setBackground(ui.shape(initial,12,ui.outline)); box.addView(swatch,new LinearLayout.LayoutParams(-1,dp(40)));
                text(box,p.endsWith("background")?"颜色值（#AARRGGBB）或图片路径":"颜色值（#AARRGGBB）"); box.addView(edit);
                LinearLayout live=column(); box.addView(live); previewFocused(live);
                final boolean[] syncing={false};
                picker.setListener(c->{if(syncing[0])return; syncing[0]=true; try{edit.setText(String.format(Locale.US,"#%08X",c));}finally{syncing[0]=false;}});
                SeekBar.OnSeekBarChangeListener slider=new SeekBar.OnSeekBarChangeListener(){
                    public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
                    public void onProgressChanged(SeekBar b,int n,boolean user){if(syncing[0])return;if(b==hue)picker.setHue(n);else picker.setOpacity(n);}
                }; hue.setOnSeekBarChangeListener(slider); opacity.setOnSeekBarChangeListener(slider);
                edit.addTextChangedListener(new TextWatcher(){
                    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                    public void afterTextChanged(Editable e){}
                    public void onTextChanged(CharSequence s,int start,int before,int count){
                        Globals saved=palette;
                        try {int c=Color.parseColor(s.toString()); swatch.setBackground(ui.shape(c,12,ui.outline));
                            if(!syncing[0]){syncing[0]=true;try{picker.setColor(c);float[] h=new float[3];Color.colorToHSV(c,h);hue.setProgress((int)h[0]);opacity.setProgress(Color.alpha(c));}finally{syncing[0]=false;}}
                            temporaryPreview(live,p,literal(s.toString(),old,true));
                        }catch(Exception ignored){if(p.endsWith("background")&&!s.toString().startsWith("#"))temporaryPreview(live,p,quote(s.toString()));}finally{palette=saved;}
                    }
                });
            } else {box.addView(edit); if(mode==1){LinearLayout live=column();box.addView(live);previewFocused(live);edit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void afterTextChanged(Editable e){} public void onTextChanged(CharSequence s,int st,int before,int count){temporaryPreview(live,p,quote(s.toString()));}});}}
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(propertyTitle(name)).setView(wrap(box)).setNegativeButton("取消",null).setPositiveButton("应用",null).create();
            dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(x->{try{String input=edit.getText().toString();if(color&&input.startsWith("#"))Color.parseColor(input);if(color&&!input.startsWith("#")&&!p.endsWith("background"))throw new IllegalArgumentException("请输入颜色");if(change(mode==1?paletteAssignment(p,color&&p.endsWith("background")&&!input.startsWith("#")?quote(input):literal(input,old,color)):p+" = "+literal(input,old,color)))dialog.dismiss();}catch(Exception e){edit.setError(color?"请输入有效的十六进制颜色值":e.getMessage());}}));dialog.show();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        });
        if(mode==1)item.setOnLongClickListener(v->{
            PaletteResolver resolver=new PaletteResolver(model);String origin=resolver.sourcePath(p);
            new AlertDialog.Builder(this).setTitle(resolver.origin(p)).setItems(new String[]{"编辑当前属性（共享表同步修改）","编辑来源属性","解除共享并保留当前值","清除当前覆盖"},(d,n)->{
                if(n==0)item.performClick();
                if(n==1){if(origin==null||origin.equals(p))item.performClick();else {LinearLayout hidden=column();field(hidden,origin,origin,PaletteProperties.color(origin));hidden.getChildAt(0).performClick();}}
                if(n==2){if(old.istable()||old.isnil()){Toast.makeText(this,"请先设置标量值",Toast.LENGTH_SHORT).show();return;}String lit=old.type()==LuaValue.TSTRING?quote(old.tojstring()):old.tojstring();change(paletteAssignment(p,lit,true));}
                if(n==3&&!resolver.rawPath(p).isnil())change(paletteAssignment(p,"nil",true));
            }).show();return true;
        });
    }
    private String paletteAssignment(String p,String literal){return paletteAssignment(p,literal,false);}
    private String paletteAssignment(String p,String literal,boolean detach){return PaletteProperties.assignment(p,literal,detach,new PaletteResolver(model));}
    private boolean change(String code){if(busy||destroyed)return false;final String previous=source;int y=scroll.getScrollY();background("正在应用修改…",()->{String next=compact(previous,code);Parsed r=parse(next,true);if(r.failure!=null)throw r.failure;return new AbstractMap.SimpleImmutableEntry<>(next,r);},r->{source=r.getKey();install(r.getValue());render();if(mode==1&&paletteListFrame!=null)paletteRestoreY=y;else scroll.post(()->scroll.scrollTo(0,y));});return true;}
    private List<LuaValue> builtinPalettes(){if(builtinPalettes!=null)return builtinPalettes;builtinPalettes=new ArrayList<>();try{String dir="themes/default/styles";for(String name:getAssets().list(dir)){try(InputStream in=getAssets().open(dir+"/"+name+"/main.lua");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);builtinPalettes.add(evaluate(new String(out.toByteArray(),StandardCharsets.UTF_8)));}catch(Exception ignored){}}}catch(IOException ignored){}return builtinPalettes;}
    private void renderPalette(){
        Set<String> groups=paletteGroups.names();
        if(groups.isEmpty()){text(panel,"没有可编辑的配色属性");return;}
        if(!groups.contains(paletteGroup))paletteGroup=groups.iterator().next();
        section(panel,"实时预览","示例键盘与当前属性状态；修改后即时刷新");
        previewPath=paletteGroup;
        LinearLayout preview=card(panel);
        previewKeyboardOverview(preview);
        paletteDetail=column();paletteDetail.setPadding(0,0,0,0);preview.addView(paletteDetail);
        section(panel,"配色属性",null);
        String[] names=groups.toArray(new String[0]);String[] labels=new String[names.length];for(int i=0;i<names.length;i++)labels[i]=propertyName(names[i]);
        addPaletteGroupSelector(names,labels);
        Switch optional=new Switch(this);optional.setText("显示未设置的可选属性");optional.setChecked(showOptional);panel.addView(optional);
        paletteList=column();panel.addView(paletteList);
        refreshPaletteGroup(true);
        optional.setOnCheckedChangeListener((v,checked)->{if(busy||destroyed){if(checked!=showOptional)optional.setChecked(showOptional);return;}showOptional=checked;refreshPaletteGroup(false);});
    }
    private void cancelPaletteList(){
        paletteListVersion++;
        paletteRestoreY=-1;
        if(paletteListFrame!=null){panel.removeCallbacks(paletteListFrame);paletteListFrame=null;}
        if(paletteRestoreLayout!=null){
            android.view.ViewTreeObserver observer=panel.getViewTreeObserver();
            if(observer.isAlive())observer.removeOnGlobalLayoutListener(paletteRestoreLayout);
            paletteRestoreLayout=null;
        }
    }
    private void finishPaletteList(long generation){
        paletteListFrame=null;
        if(paletteRestoreY<0)return;
        final int y=paletteRestoreY;paletteRestoreY=-1;
        // The ScrollView must measure the final batch before restoring its offset.
        paletteRestoreLayout=new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
            @Override public void onGlobalLayout(){
                android.view.ViewTreeObserver observer=panel.getViewTreeObserver();
                if(observer.isAlive())observer.removeOnGlobalLayoutListener(this);
                if(paletteRestoreLayout==this)paletteRestoreLayout=null;
                if(!destroyed&&generation==paletteListVersion)scroll.scrollTo(0,y);
            }
        };
        panel.getViewTreeObserver().addOnGlobalLayoutListener(paletteRestoreLayout);
    }
    private void refreshPaletteGroup(boolean detail){
        cancelPaletteList();
        final long generation=paletteListVersion;
        final LinearLayout list=paletteList, sample=paletteDetail;
        if(list==null)return;
        final String group=paletteGroup;
        final List<PaletteGroupSnapshot.Field> rows=paletteGroups.rows(group,showOptional);
        list.removeAllViews();
        final boolean needsDetail=sample!=null&&(detail||!group.equals(paletteDetailGroup));
        if(needsDetail){sample.removeAllViews();paletteDetailGroup=null;}
        // Let the selected label and cleared list paint before creating the next group.
        paletteListFrame=new Runnable(){
            int index;String previous="";boolean updateDetail=needsDetail;
            public void run(){
                if(destroyed||generation!=paletteListVersion||list!=paletteList||!list.isAttachedToWindow())return;
                if(updateDetail){updateDetail=false;String old=previewPath;try{previewPath=group;previewGroupDetail(sample);}catch(Exception e){text(sample,"无法生成属性样例："+e.getMessage());}finally{previewPath=old;}paletteDetailGroup=group;panel.postOnAnimation(this);return;}
                if(rows.isEmpty()){text(list,"本组未设置属性，打开上方开关添加。");finishPaletteList(generation);return;}
                long start=android.os.SystemClock.uptimeMillis();int count=0;
                do {
                    PaletteGroupSnapshot.Field row=rows.get(index++);String p=row.path;
                    int cut=p.lastIndexOf('.');String parent=cut<0?"":p.substring(0,cut);
                    if(!parent.equals(previous)){section(list,propertyTitle(parent),null);previous=parent;}
                    field(list,p,p,PaletteProperties.color(p));count++;
                }while(index<rows.size()&&count<8&&android.os.SystemClock.uptimeMillis()-start<5);
                if(index<rows.size())panel.postOnAnimation(this);else finishPaletteList(generation);
            }
        };
        panel.postOnAnimation(paletteListFrame);
    }
    /** Outlined selector with a bounded, themed popup; no platform Spinner background. */
    private void addPaletteGroupSelector(final String[] names, final String[] labels) {
        final int[] selected = {Math.max(0, Arrays.asList(names).indexOf(paletteGroup))};
        final LinearLayout anchor = new LinearLayout(this);
        anchor.setOrientation(LinearLayout.HORIZONTAL);
        anchor.setGravity(android.view.Gravity.CENTER_VERTICAL);
        anchor.setMinimumHeight(dp(60));
        ui.clickable(anchor, ui.surface, 12, ui.muted);
        anchor.setPadding(dp(14), dp(8), dp(14), dp(8));
        anchor.setFocusable(true);
        anchor.setContentDescription("配色分组，当前：" + labels[selected[0]] + "，点击展开");
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(ui.label("配色分组", 12, ui.muted, false));
        TextView current = ui.label(labels[selected[0]], 16, ui.ink, true);
        current.setSingleLine(true);
        current.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(current);
        anchor.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView arrow = ui.label("▾", 22, ui.accent, true);
        arrow.setGravity(android.view.Gravity.CENTER);
        anchor.addView(arrow, new LinearLayout.LayoutParams(dp(32), -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(12));
        panel.addView(anchor, lp);

        final ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(anchor);
        popup.setModal(true);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        android.graphics.drawable.InsetDrawable popupBackground =
                new android.graphics.drawable.InsetDrawable(ui.shape(ui.surface, 12, ui.muted), dp(1));
        popup.setBackgroundDrawable(popupBackground);
        popup.setVerticalOffset(dp(4));
        final ArrayAdapter<String> adapter=new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                boolean active = position == selected[0];
                LinearLayout row = new LinearLayout(EditorActivity.this);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(48));
                ui.clickable(row, active ? ui.soft : ui.surface, 8, 0);
                row.setPadding(dp(14), dp(8), dp(14), dp(8));
                TextView label = ui.label(getItem(position), 15, active ? ui.accent : ui.ink, active);
                row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView mark = ui.label(active ? "✓" : "", 17, ui.accent, true);
                mark.setGravity(android.view.Gravity.CENTER);
                row.addView(mark, new LinearLayout.LayoutParams(dp(28), -2));
                row.setContentDescription(getItem(position) + (active ? "，已选中" : ""));
                return row;
            }
        };
        popup.setAdapter(adapter);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            popup.dismiss();
            if (!busy&&!destroyed&&!paletteGroup.equals(names[position])) {
                paletteGroup=names[position];previewPath=paletteGroup;selected[0]=position;
                current.setText(labels[position]);
                anchor.setContentDescription("配色分组，当前："+labels[position]+"，点击展开");
                adapter.notifyDataSetChanged();
                refreshPaletteGroup(true);
            }
        });
        popup.setOnDismissListener(() -> {
            arrow.setText("▾");
            ui.clickable(anchor, ui.surface, 12, ui.muted);
            anchor.setPadding(dp(14), dp(8), dp(14), dp(8));
        });
        anchor.setOnClickListener(v -> {
            if (popup.isShowing()) { popup.dismiss(); return; }
            popup.setWidth(anchor.getWidth());
            android.graphics.Rect visible = new android.graphics.Rect();
            anchor.getWindowVisibleDisplayFrame(visible);
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int available = Math.max(location[1] - visible.top, visible.bottom - location[1] - anchor.getHeight());
            popup.setHeight(Math.min(Math.max(dp(48), available - dp(8)), Math.min(dp(320), dp(48) * labels.length + dp(10))));
            popup.show();
            if (popup.getListView() != null) {
                popup.getListView().setDivider(null);
                popup.getListView().setPadding(dp(4), dp(4), dp(4), dp(4));
                popup.getListView().setClipToPadding(false);
                popup.getListView().setSelection(selected[0]);
            }
            arrow.setText("▴");
            ui.clickable(anchor, ui.surface, 12, ui.accent);
            anchor.setPadding(dp(14), dp(8), dp(14), dp(8));
        });
        anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { popup.dismiss(); }
        });
    }
    private void temporaryPreview(LinearLayout live,String p,String literal){
        if(busy||destroyed)return;final long version=++previewVersion;final String input=source;
        latestPreview=()->{if(destroyed||busy)return;if(previewTask!=null){previewTask.cancel(false);previews.purge();}previewTask=previews.submit(()->{try{Globals result=evaluatePatched(baseSource,input);result.load(PaletteProperties.assignment(p,literal,false,new PaletteResolver(result)),"preview").call();main.post(()->{if(destroyed||busy||version!=previewVersion||!live.isAttachedToWindow())return;Globals saved=palette;String oldPath=previewPath;try{palette=result;previewPath=p;live.removeAllViews();previewFocused(live);}finally{palette=saved;previewPath=oldPath;}});}catch(Exception ignored){}});};if(!previewScheduled){previewScheduled=true;pendingPreview=()->{previewScheduled=false;Runnable task=latestPreview;latestPreview=null;if(task!=null)task.run();};main.postDelayed(pendingPreview,48);}
    }
    private void editScalar(String p,LuaValue old){
        previewPath=p;cancelPreview();LinearLayout box=column();LinearLayout live=column();final String[] selected={old.tojstring()};
        if(old.isboolean()){Switch toggle=new Switch(this);toggle.setText("启用");toggle.setChecked(old.toboolean());box.addView(toggle);toggle.setOnCheckedChangeListener((v,on)->{selected[0]=Boolean.toString(on);temporaryPreview(live,p,selected[0]);});}
        else {String n=p.substring(p.lastIndexOf('.')+1);double start=old.todouble();boolean fractional=n.startsWith("scale_")||n.equals("line_spacing_multiplier")||n.equals("letter_spacing")||start!=Math.rint(start);double step=fractional?.01:1;double min=n.startsWith("translation_")||n.startsWith("offset_")||n.equals("letter_spacing")?-100:n.endsWith("entries")?-1:0;double max=p.contains("vibration_effect.2.")?255:n.endsWith("_time")||p.contains("vibration_effect.1.")?3000:n.startsWith("scale_")||n.equals("line_spacing_multiplier")?3:n.contains("height")||n.contains("width")?800:n.equals("text_size")?80:n.equals("column_count")?20:100;min=Math.min(min,start);max=Math.max(max,start);final double lo=min,increment=Math.max(step,(max-min)/20000d);TextView number=ui.label(selected[0],20,ui.accent,true);box.addView(number);SeekBar seek=new SeekBar(this);seek.setMax(Math.max(1,(int)Math.ceil((max-min)/increment)));seek.setProgress((int)Math.round((start-min)/increment));box.addView(seek);text(box,"范围："+min+" ～ "+max);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int progress,boolean user){if(!user)return;double v=lo+progress*increment;selected[0]=fractional?String.format(Locale.US,"%.2f",v):Long.toString(Math.round(v));number.setText(selected[0]);temporaryPreview(live,p,selected[0]);}});}
        box.addView(live);previewFocused(live);new AlertDialog.Builder(this).setTitle(propertyTitle(p)).setView(wrap(box)).setNegativeButton("取消",null).setNeutralButton("清除设置",(d,w)->change(paletteAssignment(p,"nil",true))).setPositiveButton("应用",(d,w)->change(paletteAssignment(p,selected[0]))).show();
    }
    private LuaValue inherited(String p,String field){String path=p+"."+field;if(mode==1&&palette==model&&paletteGroups!=null){PaletteGroupSnapshot.Field cached=paletteGroups.field(path);if(cached!=null)return cached.actual;}return previewResolver().value(path);}
    private int color(String p,int fallback){LuaValue v=value(palette,p);return v.type()==LuaValue.TNUMBER?(int)v.tolong():fallback;}
    private float metric(String p,String f,float fallback){LuaValue v=inherited(p,f);return v.type()==LuaValue.TNUMBER?(float)v.todouble():fallback;}
    private int tint(String p,String f,int fallback){LuaValue v=inherited(p,f);return v.type()==LuaValue.TNUMBER?(int)v.tolong():fallback;}
    private int size(String p,String f,int fallback,int cap){return dp(Math.max(0,Math.min(cap,metric(p,f,fallback))));}
    private TextView keyView(String label,String style){TextView t=new TextView(this);iconLabel(t,label,style,18);t.setGravity(com.osfans.trime.theme.Style.parse(inherited(style,"gravity").optjstring("center"),17));t.setTextColor(tint(style,"text_color",Color.BLACK));t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,Math.max(1,Math.min(80,metric(style,"text_size",18))));GradientDrawable bg=new GradientDrawable();bg.setColor(tint(style,"background",Color.WHITE));bg.setCornerRadius(size(style,"corner_radius",0,100));bg.setStroke(size(style,"stroke_width",0,30),tint(style,"stroke_color",0));t.setBackground(bg);LuaValue image=inherited(style,"background");if(image.type()==LuaValue.TSTRING){loadBackground(t,image.tojstring());}t.setElevation(size(style,"elevation",0,40));if(Build.VERSION.SDK_INT>=28){t.setOutlineAmbientShadowColor(tint(style,"shadow_color",0));t.setOutlineSpotShadowColor(tint(style,"shadow_color",0));}t.setScaleX(Math.max(0,Math.min(3,metric(style,"scale_x",1))));t.setScaleY(Math.max(0,Math.min(3,metric(style,"scale_y",1))));t.setTranslationX(dp(metric(style,"translation_x",0)+metric(style,"offset_x",0)));t.setTranslationY(dp(metric(style,"translation_y",0)+metric(style,"offset_y",0)));t.setTranslationZ(dp(metric(style,"translation_z",0)));t.setPadding(size(style,"padding.left",0,40),size(style,"padding.top",0,40),size(style,"padding.right",0,40),size(style,"padding.bottom",0,40));LuaValue font=inherited(style,"font");if(font.istable())font=font.get(1);if(font.type()==LuaValue.TSTRING){try{loadFont(t,font.tojstring());}catch(Exception ignored){}}LuaValue show=inherited(style,"show");if(!show.isnil()&&!show.toboolean())t.setVisibility(View.INVISIBLE);return t;}
    private boolean inGroup(String p,String group){return p.equals(group)||p.startsWith(group+".");}
    private String previewStyle(String p){int i=p.indexOf(".preview");return i<0?null:p.substring(0,i);}
    private boolean configured(String style,String child){return previewResolver().hasRaw(style,child);}
    private TextView hintLabel(String label,String style,int gravity,boolean pressed){
        TextView t=new com.osfans.trime.keyboard.TightTextView(this);
        String colorStyle=pressed?style+".pressed":style;
        int textColor=tint(colorStyle,"text_color",Color.BLACK);
        float textSize=Math.max(1,Math.min(80,metric(style,"text_size",8)));
        setIcon(t,label,textColor,Math.max(1,dp(textSize)));t.setSingleLine(true);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,textSize);
        t.setTextColor(textColor);
        t.setGravity(gravity);t.setTranslationX(dp(metric(style,"offset_x",0)));t.setTranslationY(dp(metric(style,"offset_y",0)));
        LuaValue font=inherited(style,"font");if(font.isnil())font=value(palette,"keyboard.font");if(font.istable())font=font.get(1);
        if(font.type()==LuaValue.TSTRING)try{loadFont(t,font.tojstring());}catch(Exception ignored){}
        return t;
    }
    private void addSampleHint(FrameLayout key,String label,String style,int gravity,boolean pressed,boolean customGravity){
        LuaValue show=inherited(style,"show");if(!show.isnil()&&!show.toboolean())return;
        if(customGravity)gravity=com.osfans.trime.theme.Style.parse(inherited(style,"gravity").optjstring(""),gravity);
        TextView hint=hintLabel(label,style,gravity,pressed);
        // The key face has elevation/translationZ; drawing order alone is insufficient.
        View face=key.getChildAt(0);
        hint.setTranslationZ(Math.max(0,face.getZ())+dp(1));
        key.addView(hint,new FrameLayout.LayoutParams(-2,-2,gravity));
    }
    // Each sample uses one kind of hint so their positions never obscure one another.
    private FrameLayout decoratedKey(String label,String style,int kind,boolean pressed,boolean draftHint){
        FrameLayout key=new FrameLayout(this);key.setClipChildren(false);key.setClipToPadding(false);
        TextView face=keyView(label,pressed?style+".pressed":style);key.addView(face,new FrameLayout.LayoutParams(-1,-1));
        int top=android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL,bottom=android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL;
        if(kind==1&&(draftHint||configured(style,"long_click")))addSampleHint(key,"1",style+".long_click",top,pressed,true);
        if(kind==2&&(draftHint||configured(style,"hint")))addSampleHint(key,"注",style+".hint",bottom,pressed,true);
        if(kind==3){String h=style+".hint";String[] dirs={"up","down","left","right"};String[] labels={"↑","↓","←","→"};int[] grav={top,bottom,android.view.Gravity.LEFT|android.view.Gravity.CENTER_VERTICAL,android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL};for(int i=0;i<4;i++)if(draftHint||configured(h,dirs[i]))addSampleHint(key,labels[i],h+"."+dirs[i],grav[i],pressed,false);}
        return key;
    }
    private void addKeyboardKey(LinearLayout row,String label,String style,int kind,boolean pressed,boolean popup){
        addKeyboardKey(row,label,style,kind,pressed,popup,false);
    }
    private void addKeyboardKey(LinearLayout row,String label,String style,int kind,boolean pressed,boolean popup,boolean draftHint){
        int height=Math.max(dp(24),size("keyboard","height",240,600)/4);
        FrameLayout key=decoratedKey(label,style,kind,pressed,draftHint);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,height,1);lp.setMargins(size(style,"margins.left",1,24),size(style,"margins.top",2,24),size(style,"margins.right",1,24),size(style,"margins.bottom",2,24));row.addView(key,lp);
        if(popup&&configured(style,"preview")){
            String p=style+".preview";TextView bubble=keyView(label,p);bubble.setGravity(android.view.Gravity.CENTER);bubble.setPadding(0,0,0,0);bubble.setTranslationX(0);bubble.setTranslationZ(Math.max(dp(24),key.getChildAt(0).getZ()+dp(2)));
            int w=inherited(p,"width").isnil()?-1:Math.max(1,size(p,"width",64,240));int h=inherited(p,"height").isnil()?height:Math.max(1,size(p,"height",64,240));
            key.addView(bubble,new FrameLayout.LayoutParams(w,h,android.view.Gravity.CENTER));bubble.setTranslationY(-h*bubble.getScaleY()*1.01f);bubble.setVisibility(View.VISIBLE);
            key.setOnClickListener(v->bubble.setVisibility(bubble.getVisibility()==View.VISIBLE?View.INVISIBLE:View.VISIBLE));key.setContentDescription("按键弹出提示，点击切换");key.setElevation(dp(25));
            row.setElevation(dp(25));
        }
    }
    private void keyboardSample(LinearLayout parent,String style,boolean popupOnly){
        LinearLayout keyboard=column();keyboard.setPadding(dp(3),dp(3),dp(3),dp(3));applyKeyboardBackground(keyboard,model.get("style").optjstring("keyboard"),0xffdddddd);keyboard.setClipChildren(false);keyboard.setClipToPadding(false);parent.addView(keyboard);
        for(String letters:new String[]{"QWERTYUIOP","ASDFGHJKL","ZXCVBNM"}){
            LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);keyboard.addView(row);
            for(char c:letters.toCharArray()){int kind=c=='Q'?1:c=='W'?2:c=='E'?3:0;addKeyboardKey(row,String.valueOf(c),popupOnly&&c=='G'?style:"key",kind,c=='G',c=='G');}
        }
        LinearLayout row=new LinearLayout(this);keyboard.addView(row);sample(row,"123","functional");sample(row,"空格","space");sample(row,"回车","enter");
        if(!popupOnly){StringBuilder missing=new StringBuilder();if(!configured("key","hint"))missing.append("单助记 ");for(String dir:new String[]{"up","down","left","right"})if(!configured("key.hint",dir))missing.append(propertyTitle(dir)).append("助记 ");if(missing.length()>0)text(parent,"未配置："+missing+"（可在对应属性中预览并添加）");}
        if(popupOnly&&!configured(style,"preview"))text(parent,"当前样式未启用弹出提示，设置 Preview 属性后显示。");
    }
    private void popupSample(LinearLayout parent,String style,String label){keyboardSample(parent,style,true);}
    private boolean hintFocus(LinearLayout parent,String path){
        String[] parts=path.split("\\.");String style="";
        for(int i=0;i<parts.length;i++){
            String part=parts[i];if(part.equals("hint")||part.equals("long_click")){
                if(style.isEmpty())return false;
                if(style.endsWith(".pressed"))style=style.substring(0,style.length()-8);
                int kind=part.equals("long_click")?1:2;
                if(kind==2&&i+1<parts.length&&Arrays.asList("up","down","left","right").contains(parts[i+1]))kind=3;
                LinearLayout row=new LinearLayout(this);parent.addView(row);addKeyboardKey(row,"A",style,kind,false,false,true);addKeyboardKey(row,"A",style,kind,true,false,true);
                if(kind==3){StringBuilder missing=new StringBuilder();for(String dir:new String[]{"up","down","left","right"})if(!configured(style+".hint",dir)){if(missing.length()>0)missing.append("、");missing.append(propertyTitle(dir));}if(missing.length()>0)text(parent,"未定义方向："+missing+"（仅显示编辑示例，实际键盘不显示未定义方向）");}
                else if(!configured(style,part))text(parent,"当前样式未定义该助记，以下为添加属性后的编辑示例。");
                if(kind!=3&&!inherited(style+"."+part,"show").optboolean(true))text(parent,"当前助记已关闭（show=false）");
                text(parent,"左：正常状态　右：按下状态");return true;
            }
            style=style.isEmpty()?part:style+"."+part;
        }return false;
    }
    private void toolbarSample(LinearLayout parent){
        text(parent,"工具栏 · 收起键盘");
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setBackground(ui.shape(color("toolbar.background",color("candidate.background",Color.LTGRAY)),8,0));parent.addView(row);
        int h=Math.max(dp(20),size("candidate","height",42,180));
        LinearLayout items=new LinearLayout(this);row.addView(items,new LinearLayout.LayoutParams(0,h,1));
        LuaValue toolbarKeys=value(palette,"toolbar.keys");
        if(toolbarKeys.istable()){
            for(int i=1;i<=Math.min(toolbarKeys.length(),24);i++){
                LuaValue entry=toolbarKeys.get(i);String label=eventLabel(entry);
                String keyStyle=entry.istable()?entry.get("style").optjstring("toolbar.key"):"toolbar.key";
                if(!label.isEmpty())sample(items,label,keyStyle);
            }
        }else for(String s:new String[]{"菜单","中/英","剪贴板"})sample(items,s,"toolbar.key");
        LuaValue hide=new PaletteResolver(palette).value("toolbar.hide");String label=hide.istable()?hide.get("text").optjstring("▽"):hide.optjstring("▽");
        String path=hide.istable()?"toolbar.hide":"toolbar.key";
        TextView normal=keyView(label,path);normal.setMinimumWidth(h);normal.setContentDescription("收起键盘（预览）");row.addView(normal,new LinearLayout.LayoutParams(-2,h));
        final TextView pressedSample=keyView(label,path+".pressed");
        normal.setOnClickListener(v->{int index=row.indexOfChild(v);TextView pressed=pressedSample;pressed.setMinimumWidth(h);row.removeView(v);row.addView(pressed,index,new LinearLayout.LayoutParams(-2,h));pressed.postDelayed(()->{if(pressed.getParent()==row){row.removeView(pressed);row.addView(normal,index,new LinearLayout.LayoutParams(-2,h));}},350);});
        if(previewPath.startsWith("toolbar.hide.pressed")){TextView pressed=pressedSample;pressed.setMinimumWidth(h);row.removeView(normal);row.addView(pressed,new LinearLayout.LayoutParams(-2,h));}
    }
    private void appendCompositionSpan(TextView window,android.text.SpannableStringBuilder out,String s,String path,boolean highlight){
        if(s.isEmpty())return;int start=out.length();out.append(s);int end=out.length();int flags=android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        out.setSpan(new android.text.style.ForegroundColorSpan(tint(path,"text_color",Color.BLACK)),start,end,flags);
        out.setSpan(new android.text.style.AbsoluteSizeSpan(Math.max(1,dp(metric(path,"text_size",18)))),start,end,flags);
        if(highlight)out.setSpan(new android.text.style.BackgroundColorSpan(tint(path,"background",Color.LTGRAY)),start,end,flags);
        if(path.equals("composition.key")||path.startsWith("composition.key.")){
            File dir=previewStyleDir();android.content.res.Resources res=getResources();int color=tint(path,"text_color",Color.BLACK),pixels=Math.max(1,dp(metric(path,"text_size",18)));
            resource(()->previewIcons.span(res,dir,s,color,pixels),span->{if(span instanceof android.text.Spanned){android.text.Spanned sp=(android.text.Spanned)span;for(android.text.style.ImageSpan image:sp.getSpans(0,sp.length(),android.text.style.ImageSpan.class))out.setSpan(image,start+sp.getSpanStart(image),start+sp.getSpanEnd(image),flags);window.setText(out);}});
        }
    }
    private String formatSample(LuaValue format,String sample){if(format.isnil())return "";try{return String.format(Locale.ROOT,format.tojstring(),sample);}catch(Exception e){return sample;}}
    private void compositionSample(LinearLayout parent){compositionSample(parent,false);}
    private void compositionSample(LinearLayout parent,boolean compact){
        if(!compact)text(parent,"Composition · 悬浮预编辑窗口");
        LuaValue style=new PaletteResolver(palette).value("composition"); if(!style.istable())style=new org.luaj.LuaTable();
        if(!style.get("show").optboolean(true)){if(!compact)text(parent,"悬浮窗口已关闭");return;}
        TextView window=keyView("","composition");window.setGravity(android.view.Gravity.START|android.view.Gravity.TOP);
        if(value(palette,"composition.background").type()!=LuaValue.TSTRING){GradientDrawable bg=new GradientDrawable();bg.setColor(tint("composition","background",Color.WHITE));bg.setCornerRadius(size("composition","round_corner",8,100));bg.setStroke(size("composition","border",0,30),tint("composition","stroke_color",0));window.setBackground(bg);}
        window.setMinWidth(size("composition","min_width",40,600));window.setMaxWidth(Math.max(dp(1),size("composition","max_width",230,800)));window.setMinHeight(size("composition","min_height",0,600));window.setMaxHeight(Math.max(dp(1),size("composition","max_height",400,800)));
        float multiplier=metric("composition","line_spacing_multiplier",1);window.setLineSpacing(metric("composition","line_spacing",0),multiplier>0?multiplier:1);
        android.text.SpannableStringBuilder out=new android.text.SpannableStringBuilder();LuaValue components=value(palette,"composition.window");int remaining=compact?1:Integer.MAX_VALUE;
        if(components.istable())for(int i=1;i<=Math.min(components.length(),32);i++){
            LuaValue c=components.get(i);if(!c.istable())continue;
            if(!c.get("move").isnil()){out.append(c.get("start").optjstring(""));appendCompositionSpan(window,out,c.get("move").tojstring(),"composition.key",false);out.append(c.get("end").optjstring(""));}
            if(!c.get("composition").isnil()){out.append(c.get("start").optjstring(""));appendCompositionSpan(window,out,"ni ","composition",false);appendCompositionSpan(window,out,"hao", "composition.pressed",true);out.append(c.get("end").optjstring(""));window.setLetterSpacing((float)c.get("letter_spacing").optdouble(0));}
            if(!c.get("candidate").isnil()){
                String[] words={"你好世界欢迎使用输入法","输入法主题实时编辑体验","配色实时预览与键盘编辑","键盘编辑"};int count=(int)metric("composition","max_entries",-1);if(count<0)count=words.length;count=Math.min(count,remaining);int max=(int)metric("composition","max_length",10),min=(int)metric("composition","min_length",0),sticky=(int)metric("composition","sticky_lines",0),line=0,shown=0;
                for(int j=0;j<words.length&&shown<count;j++){String word=compact&&min<=2&&j==0?"你好":words[j];if(word.length()<min){if(style.get("all_phrases").optboolean(false))continue;break;}String sep=shown==0?c.get("start").optjstring("\n"):((shown<=sticky||(max>0&&line+word.length()>max))?"\n":c.get("sep").optjstring(" "));out.append(sep);if(sep.contains("\n"))line=0;boolean selected=shown==0&&style.get("use_cursor").optboolean(true);appendCompositionSpan(window,out,formatSample(c.get("label"),Integer.toString(j+1)),selected?"composition.key.pressed.hint":"composition.key.hint",false);appendCompositionSpan(window,out,formatSample(c.get("candidate"),word),selected?"candidate.pressed":"candidate",selected);appendCompositionSpan(window,out,formatSample(c.get("comment"),"ni hao"),selected?"candidate.comment.pressed":"candidate.comment",false);line+=word.length();shown++;}remaining-=shown;out.append(c.get("end").optjstring(""));
            }
        }
        else {appendCompositionSpan(window,out,"ni ","composition",false);appendCompositionSpan(window,out,"hao","composition.pressed",true);out.append("\n");appendCompositionSpan(window,out,"1. 你好 ","candidate.pressed",true);if(!compact)appendCompositionSpan(window,out,"2. 世界","candidate",false);}
        window.setText(out);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);String pos=style.get("position").optjstring("fixed");lp.gravity=pos.contains("right")?android.view.Gravity.RIGHT:android.view.Gravity.LEFT;int spacing=compact?Math.min(dp(2),size("composition","spacing",1,80)):size("composition","spacing",1,80);lp.setMargins(0,spacing,0,spacing);parent.addView(window,lp);
    }
    private void previewFocused(LinearLayout l){String popup=previewStyle(previewPath);if(popup!=null){popupSample(l,popup,popup.equals("space")?"空格":"G");return;}if(inGroup(previewPath,"toolbar")){toolbarSample(l);return;}if(inGroup(previewPath,"composition")){compositionSample(l);return;}if(inGroup(previewPath,"preedit")){LinearLayout pre=new LinearLayout(this);l.addView(pre);sample(pre,"ni hao","preedit");return;}if(inGroup(previewPath,"candidate")){compositionSample(l);return;}if(hintFocus(l,previewPath))return;String focus=previewPath;int dot=focus.lastIndexOf('.');if(dot>=0)focus=focus.substring(0,dot);while(focus.contains(".")&&(focus.endsWith(".padding")||focus.endsWith(".margins")||!value(palette,focus).istable()))focus=focus.substring(0,focus.lastIndexOf('.'));if(focus.equals("keyboard")||focus.equals("height")||focus.equals("background")){previewSample(l);return;}text(l,"实时预览 · "+propertyTitle(focus));LinearLayout row=new LinearLayout(this);l.addView(row);sample(row,"示例 Aa",focus);sample(row,"按下",focus+".pressed");}
    private void previewSample(LinearLayout l){previewKeyboardOverview(l);previewGroupDetail(l);}
    private void previewKeyboardOverview(LinearLayout l){LinearLayout b=column();b.setBackground(ui.shape(color("background",Color.LTGRAY),14,0));l.addView(b);compositionSample(b,true);toolbarSample(b);LinearLayout pre=new LinearLayout(this);b.addView(pre);sample(pre,"ni hao","preedit");LinearLayout cand=new LinearLayout(this);b.addView(cand);sample(cand,"你好","candidate");sample(cand,"世界","candidate.pressed");sample(cand,"注释","candidate.comment");keyboardSample(b,"key",false);}
    private void previewGroupDetail(LinearLayout l){String focus=previewPath;while(focus.contains(".")&&!value(palette,focus).istable())focus=focus.substring(0,focus.lastIndexOf('.'));if(!focus.equals("基本信息")&&!inGroup(focus,"toolbar")&&!inGroup(focus,"composition")&&value(palette,focus).istable()){if(hintFocus(l,previewPath))return;text(l,"属性样例 · "+propertyTitle(focus));LinearLayout detail=new LinearLayout(this);l.addView(detail);sample(detail,"示例 Aa",focus);sample(detail,"按下",focus+".pressed");} }
    private void sample(LinearLayout r,String label,String style){int h=style.equals("candidate")||style.equals("candidate.pressed")||style.equals("candidate.comment")?size("candidate","height",42,100):style.equals("key")||style.equals("key.pressed")||style.equals("space")||style.equals("functional")||style.equals("enter")?Math.max(dp(20),size("keyboard","height",240,600)/4):size(style,"height",42,180);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Math.max(dp(12),h),1);lp.setMargins(size(style,"margins.left",1,24),size(style,"margins.top",2,24),size(style,"margins.right",1,24),size(style,"margins.bottom",2,24));r.addView(keyView(label,style),lp);}
    private static final String NEW_KEY = "{click=\"a\"}";
    private static class LayoutEntry {
        final LuaValue config; final String path, label; final boolean root;
        LayoutEntry(LuaValue config,String path,String label,boolean root){this.config=config;this.path=path;this.label=label;this.root=root;}
    }
    private final List<LayoutEntry> layoutEntries=new ArrayList<>();
    private int keyboardPreviewHeight(){return Math.max(dp(24),size("keyboard","height",240,600));}
    private int tableLength(LuaValue value){return value.istable()?value.length():0;}
    private double rowDefaultWidth(){LuaValue rows=model.get("rows");int n=rows.length()>0&&rows.get(1).istable()?tableLength(rows.get(1).get("keys")):0;return model.get("key_width").optdouble(100.0/Math.max(1,n));}
    private double rowDefaultHeight(){return model.get("key_height").optdouble(100.0/Math.max(1,model.get("rows").length()));}
    private boolean absoluteKeyboard(){return !model.get("rows").istable()&&!model.get("flex_box").istable()&&model.get("keys").istable();}
    private void renderKeyboardEditor(){
        previewPath="key";layoutEntries.clear();keyboardKeyStyles.clear();
        section(panel,"键盘布局","点击按键编辑；长按可在前后插入、删除、移动或编辑所在行。");
        LinearLayout controls=card(panel);
        if(paletteNotice!=null)text(controls,paletteNotice);
        LinearLayout layoutCard=card(panel);
        boolean rows=model.get("rows").istable();
        if(rows){
            text(controls,"RowKeyboard · 百分比布局");
            LinearLayout actions=new LinearLayout(this);controls.addView(actions);
            button(actions,"默认宽高",v->editLayoutFields(model,"",new String[]{"key_width","key_height","style"},true,false));
            renderRows(layoutCard);
            button(actions,"行属性",v->chooseLayoutEntry(true));
            button(actions,"添加一行",v->change("table.insert(rows, {keys={"+NEW_KEY+"}})"));
        }else if(model.get("flex_box").istable()){
            text(controls,"Flexbox · 容器布局");
            LuaValue config=model.get("flex_box");
            com.google.android.flexbox.FlexboxLayout rootFlex=makeFlex(config);
            FrameLayout surface=new FrameLayout(this);applyKeyboardBackground(surface,model.get("style").optjstring("keyboard"),0xffdddddd);surface.addView(rootFlex,new FrameLayout.LayoutParams(-1,-1));layoutCard.addView(surface,new LinearLayout.LayoutParams(-1,keyboardPreviewHeight()));
            fillFlex(rootFlex,config,"flex_box",0,true);
            button(controls,"行 / 列属性",v->chooseLayoutEntry(false));
        }else if(absoluteKeyboard()){
            text(controls,"AbsKeyboard · 绝对坐标百分比布局");
            text(controls,"点击按键编辑坐标与功能；长按可插入、删除或调整叠放顺序。");
            LinearLayout actions=new LinearLayout(this);controls.addView(actions);
            button(actions,"布局属性",v->editLayoutFields(model,"",new String[]{"key_width","key_height","style"},true,false));
            if(model.get("keys").length()==0)button(actions,"创建首个按键",v->insertKey("keys",1,true));
            else button(actions,"全部按键",v->chooseAbsoluteKey());
            renderAbsolute(layoutCard);
        }else{text(layoutCard,"此布局类型暂不支持可视化，请使用源码编辑。");}
    }
    private void chooseAbsoluteKey(){
        LuaValue keys=model.get("keys");LayoutEntry entry=new LayoutEntry(model,"","绝对布局",true);
        ArrayList<String> names=new ArrayList<>();ArrayList<Integer> indices=new ArrayList<>();
        for(int i=1;i<=keys.length();i++){
            LuaValue key=keys.get(i);if(!key.istable())continue;
            String label=key.get("label").optjstring(eventLabel(key.get("click")));
            names.add(i+". "+(label.isEmpty()?"空白按键":label)+" · ("+key.get("x").optdouble(0)+"%, "+key.get("y").optdouble(0)+"%)");indices.add(i);
        }
        new AlertDialog.Builder(this).setTitle("全部按键（含重叠及画布外按键）").setItems(names.toArray(new String[0]),(d,n)->{
            int index=indices.get(n);LuaValue key=keys.get(index);
            new AlertDialog.Builder(this).setTitle(names.get(n)).setItems(new String[]{"编辑按键","管理按键"},(a,b)->{
                if(b==0)editKey(key,"keys["+index+"]",true);else showKeyActions(key,entry,index,true);
            }).setNegativeButton("取消",null).show();
        }).setNegativeButton("取消",null).show();
    }
    private void applyKeyboardBackground(View target,String style,int fallback){
        LuaValue v=palette==null?LuaValue.NIL:palette.get(style);
        if(!v.istable())v=new org.luaj.LuaTable();
        LuaValue image=v.get("background");GradientDrawable bg=new GradientDrawable();bg.setColor(image.type()==LuaValue.TNUMBER?(int)image.tolong():fallback);bg.setCornerRadius(dp((float)v.get("corner_radius").optdouble(0)));bg.setStroke(Math.max(0,dp((float)v.get("stroke_width").optdouble(0))),v.get("stroke_color").optint(0));target.setBackground(bg);
        if(image.type()==LuaValue.TSTRING)loadBackground(target,image.tojstring());
    }
    private void renderAbsolute(LinearLayout parent){
        final int totalHeight=keyboardPreviewHeight();
        final ArrayList<double[]> rectangles=new ArrayList<>();
        FrameLayout keyboard=new FrameLayout(this){
            @Override protected void onMeasure(int ws,int hs){
                int width=View.MeasureSpec.getSize(ws);
                for(int i=0;i<getChildCount();i++){
                    double[] r=rectangles.get(i);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)getChildAt(i).getLayoutParams();
                    lp.width=Math.max(0,(int)(width*r[2]/100));lp.height=Math.max(0,(int)(totalHeight*r[3]/100));
                    lp.leftMargin=(int)(width*r[0]/100);lp.topMargin=(int)(totalHeight*r[1]/100);
                }
                super.onMeasure(ws,hs);
            }
        };
        applyKeyboardBackground(keyboard,model.get("style").optjstring("keyboard"),0xffdddddd);
        keyboard.setClipChildren(true);keyboard.setClipToPadding(true);
        parent.addView(keyboard,new LinearLayout.LayoutParams(-1,totalHeight));
        LayoutEntry entry=new LayoutEntry(model,"","绝对布局",true);
        LuaValue keys=model.get("keys");boolean overflow=false;
        for(int i=1;i<=keys.length();i++){
            LuaValue key=keys.get(i);if(!key.istable())continue;
            double x=key.get("x").optdouble(0),y=key.get("y").optdouble(0);
            double w=key.get("width").optdouble(model.get("key_width").optdouble(20)),h=key.get("height").optdouble(model.get("key_height").optdouble(10));
            rectangles.add(new double[]{x,y,w,h});
            keyboard.addView(editorKey(key,entry,i,true),new FrameLayout.LayoutParams(0,0,android.view.Gravity.TOP|android.view.Gravity.LEFT));
            if(x<0||y<0||x+w>100.0001||y+h>100.0001||w<=0||h<=0)overflow=true;
        }
        if(overflow)text(parent,"部分按键超出画布或尺寸为零，可通过「全部按键」继续编辑。");
    }
    /** Absolute percentages, not LinearLayout weights: unused width stays empty. */
    private void renderRows(LinearLayout parent){
        LuaValue rows=model.get("rows");final int totalHeight=keyboardPreviewHeight();
        final ArrayList<double[]> rectangles=new ArrayList<>();
        FrameLayout keyboard=new FrameLayout(this){
            @Override protected void onMeasure(int ws,int hs){
                int width=View.MeasureSpec.getSize(ws);
                for(int i=0;i<getChildCount();i++){
                    double[] r=rectangles.get(i);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)getChildAt(i).getLayoutParams();
                    lp.width=Math.max(0,(int)(width*r[2]/100));lp.height=Math.max(0,(int)(totalHeight*r[3]/100));
                    lp.leftMargin=(int)(width*r[0]/100);lp.topMargin=(int)(totalHeight*r[1]/100);
                }
                super.onMeasure(ws,hs);
            }
        };
        applyKeyboardBackground(keyboard,model.get("style").optjstring("keyboard"),0xffdddddd);
        parent.addView(keyboard,new LinearLayout.LayoutParams(-1,totalHeight));
        double top=0;boolean overflow=false;
        for(int i=1;i<=rows.length();i++){
            LuaValue row=rows.get(i);if(!row.istable())continue;
            String path="rows["+i+"]";LayoutEntry entry=new LayoutEntry(row,path,"第 "+i+" 行",false);layoutEntries.add(entry);
            double height=row.get("height").optdouble(rowDefaultHeight()),width=row.get("width").optdouble(rowDefaultWidth()),left=0;
            if(row.get("style").type()==LuaValue.TSTRING){View background=new View(this);applyKeyboardBackground(background,row.get("style").tojstring(),0);rectangles.add(0,new double[]{0,top,100,height});keyboard.addView(background,0,new FrameLayout.LayoutParams(0,0,android.view.Gravity.TOP|android.view.Gravity.LEFT));}
            LuaValue keys=row.get("keys");
            for(int j=1;j<=tableLength(keys);j++){
                LuaValue key=keys.get(j);if(!key.istable())continue;
                double w=key.get("width").optdouble(width),h=key.get("height").optdouble(height);
                rectangles.add(new double[]{left,top,w,h});
                keyboard.addView(editorKey(key,entry,j,true),new FrameLayout.LayoutParams(0,0,android.view.Gravity.TOP|android.view.Gravity.LEFT));
                if(top+h>100.0001)overflow=true;left+=w;
            }
            if(left>100.0001)overflow=true;top+=height;
        }
        if(rows.length()==0)text(parent,"键盘没有行，可点击「添加一行」恢复。");
        if(overflow||top>100.0001)text(parent,"部分按键或行超出键盘范围，请调整宽高百分比。预览不会自动压缩。");
    }
    private com.google.android.flexbox.FlexboxLayout makeFlex(LuaValue config){
        com.google.android.flexbox.FlexboxLayout view=new com.google.android.flexbox.FlexboxLayout(this);
        view.setFlexDirection("column".equals(config.get("direction").optjstring("row"))?com.google.android.flexbox.FlexDirection.COLUMN:com.google.android.flexbox.FlexDirection.ROW);
        view.setJustifyContent(com.google.android.flexbox.JustifyContent.FLEX_START);view.setAlignItems(com.google.android.flexbox.AlignItems.STRETCH);view.setFlexWrap(com.google.android.flexbox.FlexWrap.NOWRAP);
        view.setClipChildren(false);view.setClipToPadding(false);
        if(config.get("style").isstring()){
            String style=config.get("style").tojstring();GradientDrawable bg=new GradientDrawable();bg.setColor(tint(style,"background",0));bg.setCornerRadius(size(style,"corner_radius",0,100));bg.setStroke(size(style,"stroke_width",0,30),tint(style,"stroke_color",0));view.setBackground(bg);view.setElevation(size(style,"elevation",0,40));
            LuaValue image=inherited(style,"background");if(image.type()==LuaValue.TSTRING){File dir=previewStyleDir();String name=image.tojstring();resource(()->{File f=PreviewIcons.find(dir,name,"images");return f==null?null:android.graphics.drawable.Drawable.createFromPath(f.getAbsolutePath());},view::setBackground);}
        }
        return view;
    }
    private com.google.android.flexbox.FlexboxLayout.LayoutParams flexParams(com.google.android.flexbox.FlexboxLayout parent,LuaValue config){
        int w=config.get("width").optint(-1),h=config.get("height").optint(-1);float grow=(float)config.get("grow").optdouble(1);
        boolean row=parent.getFlexDirection()==com.google.android.flexbox.FlexDirection.ROW;
        com.google.android.flexbox.FlexboxLayout.LayoutParams lp=new com.google.android.flexbox.FlexboxLayout.LayoutParams(w>0?dp(w):row?0:-1,h>0?dp(h):row?-1:0);
        lp.setFlexGrow((row?w>0:h>0)?0:grow);lp.setFlexShrink(w>0||h>0?0:1);return lp;
    }
    private void fillFlex(com.google.android.flexbox.FlexboxLayout parent,LuaValue config,String path,int depth,boolean root){
        if(depth>12)return;
        LayoutEntry entry=new LayoutEntry(config,path,root?"根容器":path.substring("flex_box".length())+" · "+("column".equals(config.get("direction").optjstring("row"))?"列":"行"),root);layoutEntries.add(entry);
        for(int i=1;i<=config.length();i++){
            LuaValue child=config.get(i);if(!child.istable()||(!child.get("keys").istable()&&child.length()==0))continue;
            com.google.android.flexbox.FlexboxLayout container=makeFlex(child);parent.addView(container,flexParams(parent,child));fillFlex(container,child,path+"["+i+"]",depth+1,false);
        }
        if(!root){LuaValue keys=config.get("keys");for(int i=1;i<=tableLength(keys);i++){LuaValue key=keys.get(i);if(!key.istable())continue;parent.addView(editorKey(key,entry,i,false),flexParams(parent,key));}}
    }
    private View editorKey(LuaValue key,LayoutEntry entry,int index,boolean percent){
        String path=entry.path.isEmpty()?"keys":entry.path+".keys",p=path+"["+index+"]";
        String label=key.get("label").optjstring(eventLabel(key.get("click")));
        String style=key.get("style").optjstring(key.get("click").optjstring("key"));
        if(style.isEmpty()||palette==null||!palette.get(style).istable())style="key";
        keyboardKeyStyles.add(style);
        FrameLayout view=new FrameLayout(this);view.setClipChildren(false);view.setClipToPadding(false);
        FrameLayout face=new FrameLayout(this);face.setClipChildren(false);face.setClipToPadding(false);
        TextView mainKey=keyView(label,style);face.addView(mainKey,new FrameLayout.LayoutParams(-1,-1));
        int elevation=size(style,"elevation",0,40);
        FrameLayout.LayoutParams faceParams=new FrameLayout.LayoutParams(-1,-1);
        faceParams.setMargins(size(style,"margins.left",0,40),size(style,"margins.top",0,40),size(style,"margins.right",0,40),size(style,"margins.bottom",0,40));
        if(inherited(style,"margins").isnil())faceParams.setMargins(elevation/3,elevation/3,elevation/3,elevation/3*2);
        view.addView(face,faceParams);
        int top=android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL,bottom=android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL;
        String longLabel=key.get("long_click").isnil()?"":key.get("hint_long").optjstring(eventLabel(key.get("long_click")));
        if(!longLabel.isEmpty()&&configured(style,"long_click"))addSampleHint(face,longLabel,style+".long_click",top,false,true);
        String hint=key.get("hint").optjstring("");if(!hint.isEmpty()&&configured(style,"hint"))addSampleHint(face,hint,style+".hint",bottom,false,true);
        String[] dirs={"up","down","left","right"};int[] grav={top,bottom,android.view.Gravity.LEFT|android.view.Gravity.CENTER_VERTICAL,android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL};
        for(int i=0;i<dirs.length;i++){String dir=dirs[i];String h=key.get("hint_"+dir).optjstring(eventLabel(key.get("swipe_"+dir)));if(!h.isEmpty()&&configured(style+".hint",dir))addSampleHint(face,h,style+".hint."+dir,grav[i],false,false);}
        if(label.isEmpty())view.setAlpha(.4f);
        view.setContentDescription(label.isEmpty()?"空白按键":label);
        view.setOnClickListener(v->editKey(key,p,percent));
        view.setOnLongClickListener(v->{showKeyActions(key,entry,index,percent);return true;});return view;
    }
    private void showKeyActions(LuaValue key,LayoutEntry entry,int index,boolean percent){
        boolean absolute=entry.path.isEmpty();String path=absolute?"keys":entry.path+".keys";
        String label=key.get("label").optjstring(eventLabel(key.get("click")));
        ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
        labels.add("在前面添加按键");actions.add(()->insertKey(path,index,percent));
        labels.add("在后面添加按键");actions.add(()->insertKey(path,index+1,percent));
        if(index>1){labels.add(absolute?"向前调整叠放顺序":"向前移动");actions.add(()->swapKeys(path,index,index-1));}
        if(index<entry.config.get("keys").length()){labels.add(absolute?"向后调整叠放顺序":"向后移动");actions.add(()->swapKeys(path,index,index+1));}
        labels.add(absolute?"编辑布局属性":percent?"编辑行属性":"编辑所在行 / 列属性");actions.add(()->{
            if(absolute)editLayoutFields(model,"",new String[]{"key_width","key_height","style"},true,false);else editLayoutEntry(entry,percent);
        });
        labels.add("删除按键");actions.add(()->{
            new AlertDialog.Builder(this).setMessage("删除此按键？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->change("table.remove("+path+", "+index+")")).show();
        });
        new AlertDialog.Builder(this).setTitle(label.isEmpty()?"空白按键":label).setItems(labels.toArray(new String[0]),(d,n)->actions.get(n).run()).show();
    }
    private void swapKeys(String path,int a,int b){String x=path+"["+a+"]",y=path+"["+b+"]";change(x+", "+y+" = "+y+", "+x);}
    private void chooseLayoutEntry(boolean percent){
        if(layoutEntries.isEmpty()){Toast.makeText(this,"没有可编辑的行",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[layoutEntries.size()];for(int i=0;i<names.length;i++)names[i]=layoutEntries.get(i).label;
        new AlertDialog.Builder(this).setTitle(percent?"选择行":"选择布局容器").setItems(names,(d,n)->editLayoutEntry(layoutEntries.get(n),percent)).setNegativeButton("取消",null).show();
    }
    private void editLayoutEntry(LayoutEntry entry,boolean percent){
        String[] fields=percent?new String[]{"width","height","style"}:entry.root?new String[]{"direction","style"}:new String[]{"direction","style","width","height","grow"};
        editLayoutFields(entry.config,entry.path,fields,percent,entry.root);
    }
    private void editLayoutFields(LuaValue config,String path,String[] fields,boolean percent,boolean rootContainer){
        LinearLayout box=column();EditText[] inputs=new EditText[fields.length];Spinner[] choices=new Spinner[fields.length];String[] initial=new String[fields.length];
        String[] dirs={"未设置（row）","row","column"};
        text(box,percent?"宽高为整个键盘宽高的百分比；留空使用默认值。style 为背景样式，不改变按键独立样式。":"固定宽高使用 dp；主轴未固定尺寸时 grow 才参与分配。留空使用默认值。");
        for(int i=0;i<fields.length;i++){
            String field=fields[i];LuaValue val=config.get(field);initial[i]=val.isnil()?"":val.tojstring();
            text(box,(field.equals("key_width")?"默认按键宽度":field.equals("key_height")?"默认行高度":field.equals("direction")?"排列方向":propertyName(field))+" ("+field+")");
            if(field.equals("direction")){Spinner choice=new Spinner(this);choice.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,dirs));choice.setSelection("column".equals(initial[i])?2:"row".equals(initial[i])?1:0);choices[i]=choice;box.addView(choice);}
            else{EditText input=new EditText(this);input.setSingleLine(true);styleInput(input);input.setText(initial[i]);if(!field.equals("style")){input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);if(percent&&absoluteKeyboard())input.setHint("默认 "+(field.endsWith("width")?20:10)+" %");else if(percent)input.setHint("默认 "+(field.endsWith("width")?(field.equals("key_width")?100.0/Math.max(1,model.get("rows").length()>0&&model.get("rows").get(1).istable()?tableLength(model.get("rows").get(1).get("keys")):0):rowDefaultWidth()):(field.equals("key_height")?100.0/Math.max(1,model.get("rows").length()):rowDefaultHeight()))+" %");}inputs[i]=input;box.addView(input);}
        }
        final AlertDialog[] owner=new AlertDialog[1];
        if(!rootContainer&&!path.isEmpty()&&tableLength(config.get("keys"))>0){
            button(box,"查看本行 / 列的按键",v->{
                if(owner[0]!=null)owner[0].dismiss();
                LuaValue keys=config.get("keys");String[] names=new String[keys.length()];
                for(int i=1;i<=keys.length();i++){LuaValue key=keys.get(i);names[i-1]=i+". "+(key.istable()?key.get("label").optjstring(eventLabel(key.get("click"))):eventLabel(key));}
                new AlertDialog.Builder(this).setTitle("选择按键（包含超出预览的按键）").setItems(names,(d,n)->editKey(keys.get(n+1),path+".keys["+(n+1)+"]",percent)).setNegativeButton("取消",null).show();
            });
        }
        if(!path.isEmpty()){
            if(percent){
                int index=Integer.parseInt(path.substring(5,path.length()-1));
                button(box,"在前面添加一行",v->{if(owner[0]!=null)owner[0].dismiss();change("table.insert(rows, "+index+", {keys={"+NEW_KEY+"}})");});
                button(box,"在后面添加一行",v->{if(owner[0]!=null)owner[0].dismiss();change("table.insert(rows, "+(index+1)+", {keys={"+NEW_KEY+"}})");});
                button(box,"删除此行",v->confirmRemoveLayout(owner,"删除整行及其中所有按键？","table.remove(rows, "+index+")"));
            }else{
                button(box,"添加子行 / 列",v->{if(owner[0]!=null)owner[0].dismiss();addFlexContainer(path,config.length()+1);});
                if(!rootContainer){
                    int bracket=path.lastIndexOf('['),index=Integer.parseInt(path.substring(bracket+1,path.length()-1));String parent=path.substring(0,bracket);
                    button(box,"在前面添加行 / 列",v->{if(owner[0]!=null)owner[0].dismiss();addFlexContainer(parent,index);});
                    button(box,"在后面添加行 / 列",v->{if(owner[0]!=null)owner[0].dismiss();addFlexContainer(parent,index+1);});
                    String remove="table.remove("+parent+", "+index+")";
                    if(!parent.equals("flex_box"))remove+="\nif #"+parent+"==0 and type("+parent+".keys)~='table' then "+parent+".keys={} end";
                    final String removeCode=remove;
                    button(box,"删除此容器",v->confirmRemoveLayout(owner,"删除此容器及其中所有按键、子容器？",removeCode));
                }
            }
        }
        ScrollView sc=new ScrollView(this);sc.addView(box);AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(path.isEmpty()?"键盘默认宽高":percent?"行属性":"布局容器属性").setView(sc).setNegativeButton("取消",null).setPositiveButton("应用",null);
        if(!path.isEmpty()&&!rootContainer&&tableLength(config.get("keys"))==0)builder.setNeutralButton("创建首个按键",(d,w)->change(config.get("keys").istable()?"table.insert("+path+".keys, "+NEW_KEY+")":path+".keys = {"+NEW_KEY+"}"));
        
        AlertDialog dialog=builder.create();owner[0]=dialog;dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{
            StringBuilder code=new StringBuilder();for(int i=0;i<fields.length;i++){
                String field=fields[i];String input=inputs[i]!=null?inputs[i].getText().toString().trim():choices[i].getSelectedItemPosition()==0?"":dirs[choices[i].getSelectedItemPosition()];
                if(input.equals(initial[i])||(field.equals("direction")&&choices[i].getSelectedItemPosition()==0&&!initial[i].isEmpty()&&!initial[i].equals("row")&&!initial[i].equals("column")))continue;String value=input.isEmpty()?"nil":field.equals("style")||field.equals("direction")?quote(input):layoutNumber(input,percent,field);
                code.append(path.isEmpty()?field:path+"."+field).append(" = ").append(value).append('\n');
            }if(code.length()==0||change(code.toString()))dialog.dismiss();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void confirmRemoveLayout(AlertDialog[] owner,String message,String code){new AlertDialog.Builder(this).setMessage(message).setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{if(owner[0]!=null)owner[0].dismiss();change(code);}).show();}
    private void addFlexContainer(String path,int index){new AlertDialog.Builder(this).setTitle("新建布局容器").setItems(new String[]{"横向（row）","纵向（column）"},(d,n)->change("table.insert("+path+", "+index+", {direction="+quote(n==0?"row":"column")+", keys={"+NEW_KEY+"}})")).setNegativeButton("取消",null).show();}
    private String layoutNumber(String input,boolean percent,String field){
        double n=Double.parseDouble(input);if(Double.isNaN(n)||Double.isInfinite(n)||(n<0&&!field.equals("x")&&!field.equals("y"))||Math.abs(n)>Integer.MAX_VALUE||(!percent&&!field.equals("grow")&&n!=Math.floor(n)))throw new IllegalArgumentException("请输入有效的"+(percent?(field.equals("x")||field.equals("y")?"坐标百分比":"非负百分比"):field.equals("grow")?"非负权重":"非负整数尺寸"));
        return Double.toString(n);
    }
    /** Pick a reference, not the label or a copy of the preset definition. */
    private void chooseKeyEvent(TextView target,String field, String[] values, int index){
        final List<PresetKeyConfig> all=presetKeys==null?Collections.emptyList():presetKeys;
        LinearLayout content=column();
        if(presetKeys==null) text(content,"无法加载预设按键，仍可直接输入。");
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("搜索名称、标签、描述或事件");styleInput(search);content.addView(search);
        CheckBox local=new CheckBox(this);local.setText("仅显示主题定义");local.setTextColor(ui.ink);content.addView(local);
        TextView count=ui.label("",13,ui.muted,false);content.addView(count);
        ListView list=new ListView(this);list.setDividerHeight(dp(1));content.addView(list,new LinearLayout.LayoutParams(-1,dp(300)));
        ArrayList<PresetKeyConfig> visible=new ArrayList<>();
        ArrayAdapter<PresetKeyConfig> adapter=new ArrayAdapter<PresetKeyConfig>(this,android.R.layout.simple_list_item_1,visible){
            @Override public View getView(int pos,View convert,android.view.ViewGroup parent){
                PresetKeyConfig key=getItem(pos);String label=key.summary();if(key.name.equals(values[index]))label="✓ "+label;
                TextView row=ui.label(label,15,ui.ink,false);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setMinHeight(dp(52));ui.clickable(row,ui.surface,8,0);return row;
            }
        };list.setAdapter(adapter);
        Runnable filter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(PresetKeyConfig key:all){if(local.isChecked()&&!key.local)continue;if((key.name+" "+Arrays.toString(key.values)).toLowerCase(Locale.ROOT).contains(q))visible.add(key);}adapter.notifyDataSetChanged();count.setText(visible.isEmpty()?"没有匹配的预设按键，可返回直接输入。":"共 "+visible.size()+" 个预设按键");};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});local.setOnCheckedChangeListener((v,c)->filter.run());filter.run();
        AlertDialog picker=new AlertDialog.Builder(this).setTitle(propertyName(field)+" · 选择预设按键").setView(content).setNegativeButton("取消",null).setNeutralButton("清除",(d,w)->setKeyEvent(target,values,index,"" )).setPositiveButton("直接输入",(d,w)->inputKeyEvent(target,field,values,index)).create();
        list.setOnItemClickListener((parent,view,pos,id)->{setKeyEvent(target,values,index,visible.get(pos).name);picker.dismiss();});
        picker.show();picker.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void setKeyEvent(TextView target,String[] values,int index,String value){
        values[index]=value;
        target.setText(value.isEmpty()?"未设置 · 点击选择":value);
        target.setTextColor(value.isEmpty()?ui.muted:ui.ink);
    }
    private void inputKeyEvent(TextView target,String field,String[] values,int index){
        LinearLayout content=column();
        EditText input=new EditText(this);input.setSingleLine(true);styleInput(input);
        input.setHint("输入事件或预设名称");input.setText(values[index]);input.selectAll();content.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(propertyName(field)+" · 直接输入").setView(content)
            .setNegativeButton("取消",null).setPositiveButton("应用",(d,w)->setKeyEvent(target,values,index,input.getText().toString())).create();
        dialog.show();input.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void addKeyEventSelector(LinearLayout box,String field,String[] values,int index){
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(8),dp(14),dp(8));row.setMinimumHeight(dp(56));ui.clickable(row,ui.surface,12,ui.muted);
        TextView value=ui.label("",15,ui.ink,false);value.setSingleLine(true);value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=ui.label("⌄",24,ui.accent,true);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),-2));
        setKeyEvent(value,values,index,values[index]);
        row.setFocusable(true);row.setOnClickListener(v->chooseKeyEvent(value,field,values,index));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));box.addView(row,lp);
    }
    private void insertKey(String path,int index,boolean percent){
        org.luaj.LuaTable key=new org.luaj.LuaTable();key.set("click",LuaValue.valueOf("a"));
        editKey(key,path,percent,index);
    }
    private void editKey(LuaValue k,String path,boolean percent){editKey(k,path,percent,0);}
    private void editKey(LuaValue k,String path,boolean percent,int insertIndex){
        boolean absolute=absoluteKeyboard();
        String[] fields=absolute?new String[]{"label","click","long_click","swipe_left","swipe_right","swipe_up","swipe_down","combo","style","width","height","x","y","hint","hint_long","hint_up","hint_down","hint_left","hint_right"}:percent?new String[]{"label","click","long_click","swipe_left","swipe_right","swipe_up","swipe_down","combo","style","width","height","hint","hint_long","hint_up","hint_down","hint_left","hint_right"}:new String[]{"label","click","long_click","swipe_left","swipe_right","swipe_up","swipe_down","combo","style","width","height","grow","hint","hint_long","hint_up","hint_down","hint_left","hint_right"};
        LinearLayout box=column();EditText[] edits=new EditText[fields.length];String[] initial=new String[fields.length];String[] eventValues=new String[fields.length];
        for(int i=0;i<fields.length;i++){
            if(i==0)section(box,"基本属性",null);if(i==2)section(box,"手势事件","可直接输入事件或选择预设按键名称。");if(i==8)section(box,"外观与布局",absolute?"坐标和宽高均为键盘总尺寸百分比；x/y 默认 0，宽高留空继承键盘默认值。":percent?"宽高为键盘总尺寸的百分比，留空继承行属性。":"宽高为 dp，留空自动布局；grow 为伸展权重。");
            final String field=fields[i];boolean event=i>=1&&i<=7;if(field.equals("hint"))section(box,"助记文字","方向助记留空时使用对应滑动事件的标签。");
            text(box,(field.equals("x")?"水平坐标":field.equals("y")?"垂直坐标":field.equals("combo")?"组合按键":propertyName(field))+" ("+field+")");
            LuaValue val=k.get(field);initial[i]=val.isnil()?"":val.tojstring();
            if(event){eventValues[i]=initial[i];addKeyEventSelector(box,field,eventValues,i);if(val.istable()||val.isfunction())text(box,"当前为复杂事件；不修改则保留原值。");}
            else {EditText e=new EditText(this);e.setSingleLine(true);styleInput(e);if(i>=9&&!field.startsWith("hint")){e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|(field.equals("x")||field.equals("y")?android.text.InputType.TYPE_NUMBER_FLAG_SIGNED:0));if(absolute)e.setHint("默认 "+(field.equals("width")?model.get("key_width").optdouble(20):field.equals("height")?model.get("key_height").optdouble(10):0)+" %");}e.setText(initial[i]);edits[i]=e;box.addView(e);}
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(insertIndex>0?"添加按键":"编辑按键").setView(scroll).setNegativeButton("取消",null).setPositiveButton("应用",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{StringBuilder code=new StringBuilder();try{for(int i=0;i<fields.length;i++){String input=edits[i]==null?eventValues[i]:edits[i].getText().toString();if(insertIndex==0&&input.equals(initial[i]))continue;if(insertIndex>0&&input.isEmpty())continue;String val=input.isEmpty()?"nil":i>=9&&!fields[i].startsWith("hint")?layoutNumber(input,percent,fields[i]):quote(input);if(insertIndex>0)code.append(fields[i]).append(" = ").append(val).append(", ");else code.append(path).append('.').append(fields[i]).append(" = ").append(val).append('\n');}String patch=insertIndex>0?"table.insert("+path+", "+insertIndex+", {"+code+"})":code.toString();if(patch.isEmpty()||change(patch))d.dismiss();}catch(Exception e){error(e);}}));
        d.show();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    /** Merge is an explicit operation, never part of ordinary Save. Include any
     * unsaved visual/patch edits in the draft, but do not change files at preview. */
    private void startMerge(){
        if(file==null||mode==3||editingBase||mergeDraft!=null||busy)return;
        syncRaw();final File target=file;final String base=baseSource,diskPatch=original,usedPatch=source;
        if(usedPatch.trim().isEmpty()){Toast.makeText(this,"没有可合并的补丁",Toast.LENGTH_SHORT).show();return;}
        background("正在生成合并预览…",()->{
            if(!read(target).equals(base)||!LuaPatchManager.read(target).equals(diskPatch))throw new IOException("原始源码或补丁已变化，请重新打开文件");
            LuaSourceMerger.Result result=LuaSourceMerger.merge(base,usedPatch,LuaSourceMerger::evaluateFile);
            // File loading and the editor's string loading differ for some Unicode
            // strings in this LuaJ fork. Check both environments before offering Save.
            LuaSourceMerger.verifyEquivalent(base,usedPatch,result.source,LuaSourceMerger::evaluateEditor);
            return new MergeDraft(target,base,diskPatch,usedPatch,result);
        },draft->{mergeDraft=draft;render();});
    }
    private void syncMergeDraft(){if(mergeDraft!=null&&mergeDraft.page==0&&codeEditor!=null)mergeDraft.merged=codeEditor.getText().toString();}
    private void switchMergePage(int page){if(mergeDraft==null||mergeDraft.page==page)return;syncMergeDraft();mergeDraft.page=page;render();}
    private void cancelMerge(){
        if(mergeDraft==null)return;syncMergeDraft();
        new AlertDialog.Builder(this).setTitle("取消合并？").setMessage("只放弃本次合并预览。原始源码、已保存补丁及此前尚未保存的编辑均保留。")
            .setNegativeButton("继续查看",null).setPositiveButton("取消合并",(d,w)->{mergeDraft=null;render();}).show();
    }
    private void renderMergePreview(){
        final MergeDraft draft=mergeDraft;scroll.setVisibility(View.GONE);
        toolbar.setSubtitle(draft.target.getName()+" · "+(draft.page==0?"合并结果":draft.page==1?"原始源码":"当前补丁"));
        codePane=column();codePane.setPadding(dp(12),dp(8),dp(12),dp(8));root.addView(codePane,new LinearLayout.LayoutParams(-1,0,1));
        if(draft.page==0){
            text(codePane,draft.regenerated?"部分表声明已重新生成，内部注释或格式可能变化。可编辑结果；确认前将再次校验配置等价性。":"已尝试原位更新属性。可编辑结果；确认前将再次校验配置等价性。");
            if(!draft.usedPatch.equals(draft.diskPatch))text(codePane,"本次合并包含尚未保存的补丁修改。");
        }else text(codePane,"仅供对照，修改此视图不会保存。通过更多菜单返回合并结果。");
        codeEditor=new com.androlua.LuaEditor(this);
        boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        codeEditor.setDark(dark);codeEditor.setTextColor(ui.ink);codeEditor.setBackgroundColor(ui.surface);codeEditor.setWordWrap(false);codeEditor.setShowLineNumbers(true);
        codeEditor.setText(draft.page==0?draft.merged:draft.page==1?draft.base:draft.usedPatch);
        if(draft.page!=0){codeEditor.setFocusableInTouchMode(false);codeEditor.setFocusable(false);codeEditor.setLongClickable(false);}
        codePane.addView(codeEditor,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void confirmMerge(){
        if(mergeDraft==null||mergeDraft.page!=0||busy)return;syncMergeDraft();final MergeDraft draft=mergeDraft;final String candidate=draft.merged;
        new AlertDialog.Builder(this).setTitle("合并补丁到源码")
            .setMessage("确认后将备份原始源码和补丁，将合并结果写入原始 Lua 文件，并停用旧补丁。普通保存仍然使用独立补丁。"+(draft.regenerated?"\n\n部分表声明重新生成，内部注释和格式可能变化。":""))
            .setNegativeButton("取消",null).setPositiveButton("确认合并",(d,w)->background("正在校验并合并…",()->{
                LuaSourceMerger.verifyEquivalent(draft.base,draft.usedPatch,candidate,LuaSourceMerger::evaluateFile);
                LuaSourceMerger.verifyEquivalent(draft.base,draft.usedPatch,candidate,LuaSourceMerger::evaluateEditor);
                Parsed parsed=parse("",true,candidate);if(parsed.failure!=null)throw parsed.failure;
                File backup=LuaSourceMerger.commit(draft.target,draft.base,draft.diskPatch,draft.usedPatch,candidate);
                return new MergeCommit(parsed,backup);
            },result->{baseSource=candidate;source=original="";mergeDraft=null;editingBase=false;visual=true;install(result.parsed);render();updateEditorSubtitle(true);
                new AlertDialog.Builder(this).setTitle("合并完成").setMessage("补丁已合并到原始源码，旧补丁已停用。重新加载主题后生效。\n\n备份目录：\n"+result.backup.getAbsolutePath()).setPositiveButton("确定",null).show();
            })).show();
    }
    /** The existing TextWarrior editor owns its scroll area; never nest it inside ScrollView. */
    private void renderLuaSource(){
        scroll.setVisibility(View.GONE);
        updateEditorSubtitle(false);
        codePane=column();codePane.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.addView(codePane,new LinearLayout.LayoutParams(-1,0,1));
        text(codePane,mode==3?"YAML · 保存后请部署。":editingBase?"直接修改原文件。已有补丁时，保存前将确认是否备份并移除旧补丁。":"独立 Lua 补丁 · 保存不会修改原文件。");
        codeEditor=new com.androlua.LuaEditor(this);
        boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        codeEditor.setDark(dark);codeEditor.setTextColor(ui.ink);codeEditor.setBackgroundColor(ui.surface);
        codeEditor.setWordWrap(false);codeEditor.setShowLineNumbers(true);
        if(mode==3){codeEditor.setYamlMode(true);codeEditor.setNonPrintingCharVisibility(yamlWhitespace);}
        codeEditor.setText(editingBase?baseSource:source);
        codePane.addView(codeEditor,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void leaveBaseEditor(){
        background("正在解析…",()->parse(source,true,baseSource),r->{editingBase=false;visual=true;install(r);render();});
    }
    private void saveBaseSource(){
        if(codeEditor==null)return;
        final String input=codeEditor.getText().toString(),previous=baseSource,patch=original;
        final File target=file;
        if(input.equals(previous)){Toast.makeText(this,"原始源码没有修改",Toast.LENGTH_SHORT).show();return;}
        if(!patch.trim().isEmpty()){
            new AlertDialog.Builder(this).setTitle("原始源码与已有补丁").setMessage("原始源码变更可能导致旧补丁中的按键或行索引错位。继续将备份原始源码与旧补丁，并移除生效的旧补丁；不会自动重绑。取消可保留当前编辑内容。")
                .setNegativeButton("取消",null).setPositiveButton("备份并保存",(d,w)->writeBaseSource(target,input,previous,patch)).show();return;
        }
        writeBaseSource(target,input,previous,patch);
    }
    private void writeBaseSource(File target,String input,String previous,String patch){
        background("正在校验并保存原始源码…",()->{
            if(!read(target).equals(previous))throw new IOException("原始文件已被其他程序修改，请重新打开后编辑");
            if(!LuaPatchManager.read(target).equals(patch))throw new IOException("补丁已变化，请重新打开后编辑");
            Parsed parsed=parse("",true,input);if(parsed.failure!=null)throw parsed.failure;
            File parent=target.getParentFile();File tmp=File.createTempFile("editor-base-", ".tmp",parent);
            File sidecar=LuaPatchManager.file(target),patchBackup=null;
            boolean moved=false,committed=false;
            try{
                try(FileOutputStream out=new FileOutputStream(tmp)){out.write(input.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                try(FileOutputStream out=new FileOutputStream(new File(parent,target.getName()+".editor.bak"))){out.write(previous.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                if(!patch.trim().isEmpty()){
                    patchBackup=File.createTempFile(sidecar.getName()+"-", ".editor.bak",parent);
                    if(!sidecar.renameTo(patchBackup)){patchBackup.delete();throw new IOException("无法备份旧补丁");}
                    moved=true;
                }
                if(!tmp.renameTo(target))throw new IOException("无法替换原始文件");
                committed=true;
            }finally{
                tmp.delete();
                if(moved&&!committed&&!patchBackup.renameTo(sidecar))throw new IOException("无法恢复旧补丁，备份位于："+patchBackup.getAbsolutePath());
            }
            return parsed;
        },parsed->{baseSource=input;if(!patch.trim().isEmpty())source=original="";install(parsed);updateEditorSubtitle(true);Toast.makeText(this,patch.trim().isEmpty()?"原始源码已保存；重新加载主题后生效":"原始源码已保存，旧补丁已备份并移除；重新加载主题后生效",Toast.LENGTH_LONG).show();});
    }
    private void save(){
        if(file==null||busy)return;if(editingBase){saveBaseSource();return;}syncRaw();final boolean lua=mode!=3;final File base=file,target=lua?LuaPatchManager.file(base):base;
        final String input=lua&&source.trim().isEmpty()?"":source,previous=original,baseText=baseSource;
        background("正在保存…",()->{
            if(lua){
                if(!read(base).equals(baseText))throw new IOException("原始文件已变化，请重新打开后编辑");
                if(!LuaPatchManager.read(base).equals(previous))throw new IOException("补丁文件已被其他程序修改，请重新打开后编辑");
                evaluatePatched(baseText,input);
                if(input.equals(previous)&&(!input.isEmpty()||!target.exists()))return input;
            }
            File parent=target.getParentFile();if(!parent.exists()&&!parent.mkdirs())throw new IOException("无法创建目录");
            File tmp=File.createTempFile("editor-", ".tmp",parent);
            try{
                if(!(lua&&input.isEmpty()))try(FileOutputStream out=new FileOutputStream(tmp)){out.write(input.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                if(target.exists()){try(FileOutputStream out=new FileOutputStream(new File(parent,target.getName()+".editor.bak"))){out.write(read(target).getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
                if(lua&&input.isEmpty()){if(target.exists()&&!target.delete())throw new IOException("无法移除补丁文件");}
                else if(!tmp.renameTo(target))throw new IOException("无法替换文件");
            }finally{tmp.delete();}
            return input;
        },saved->{source=original=saved;if(codeEditor!=null&&!codeEditor.getText().toString().equals(saved))codeEditor.setText(saved);updateEditorSubtitle(true);Toast.makeText(this,lua?"补丁已保存；重新加载主题后生效":"已保存，请部署",Toast.LENGTH_LONG).show();});
    }
}
