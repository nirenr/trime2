package com.osfans.trime.ui;

import com.osfans.trime.editor.LuaPatchManager;
import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.Globals;
import org.luaj.LuaTable;
import org.luaj.LuaValue;

/** Selected configuration names only; all file inspection and Lua execution is worker-confined. */
public final class HomeConfigNames {
    public interface Factory { Globals create(); }
    public static final class Names {
        public final String theme,style,keyboard;
        public Names(String theme,String style,String keyboard){this.theme=theme;this.style=style;this.keyboard=keyboard;}
    }
    private final Factory factory;
    private final Map<String,String> cache=new LinkedHashMap<String,String>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> eldest){return size()>32;}
    };
    public HomeConfigNames(Factory factory){this.factory=factory;}
    private static String stamp(File f){return f.getAbsolutePath()+":"+f.isFile()+":"+f.lastModified()+":"+f.length();}
    private static String version(File f){return stamp(f)+"\n"+stamp(LuaPatchManager.file(f));}
    private static String name(LuaTable env,String id){
        // A style's missing name must not accidentally display its parent theme name.
        LuaValue value=env.rawget("name");return value.type()==LuaValue.TSTRING&&!value.tojstring().trim().isEmpty()?value.tojstring():id;
    }
    private static void load(Globals globals,LuaTable env,File file)throws Exception{
        try(FileInputStream in=new FileInputStream(file)){globals.load(in,"@"+file.getAbsolutePath(),"bt",env).call();}
        LuaPatchManager.applyFile(globals,env,file);
    }
    public Names read(File dir,String themeId,String styleId,String keyboardId){
        File themeFile=new File(dir,"main.lua"),styleFile=new File(dir,"styles/"+styleId+"/main.lua");
        String themeVersion=version(themeFile),tKey="theme\n"+themeVersion,sKey="style\n"+themeVersion+"\n"+version(styleFile);
        String theme=cache.get(tKey),style=cache.get(sKey),keyboard="自动匹配";
        // A cache hit requires file metadata checks only; no full theme/palette scan.
        if(theme==null||style==null){
            Globals globals=factory.create();
            try{load(globals,globals,themeFile);theme=name(globals,themeId);cache.put(tKey,theme);}
            catch(Exception e){theme=themeId;cache.put(tKey,theme);globals=factory.create();}
            if(style==null){
                try{LuaTable env=new LuaTable();LuaTable meta=new LuaTable();meta.rawset("__index",globals);env.setmetatable(meta);
                    load(globals,env,styleFile);style=name(env,styleId);
                }catch(Exception e){style=styleId;}
                cache.put(sKey,style);
            }
        }
        if(keyboardId!=null&&!keyboardId.isEmpty()){
            File file=new File(dir,"keyboards/"+keyboardId+".lua");String key="keyboard\n"+version(file);keyboard=cache.get(key);
            if(keyboard==null){try{Globals globals=factory.create();load(globals,globals,file);keyboard=name(globals,keyboardId);}
                catch(Exception e){keyboard=keyboardId;}cache.put(key,keyboard);}
        }
        return new Names(theme,style,keyboard);
    }
}
