package com.osfans.trime.editor;

import java.util.HashSet;
import java.util.Set;
import org.luaj.LuaTable;
import org.luaj.LuaValue;

/** The four ordered button lists actually consumed by the Android renderers. */
final class StyleButtonLists {
    static final String TOOLBAR="toolbar.keys", CANDIDATE="candidate.expanded.tool_bar.keys",
            SYMBOL="symbol.tool_bar.keys", CLIPBOARD="clipboard.tool_bar.keys";
    static final String[] PATHS={TOOLBAR,CANDIDATE,SYMBOL,CLIPBOARD};
    interface Edit { void apply(LuaTable list); }
    private StyleButtonLists() {}
    static boolean contains(String path){for(String p:PATHS)if(path.equals(p)||path.startsWith(p+"."))return true;return false;}
    static boolean supports(String path){for(String p:PATHS)if(p.equals(path))return true;return false;}
    static String[] defaults(String path){
        if(TOOLBAR.equals(path))return new String[0];
        if(CANDIDATE.equals(path))return new String[]{"hide","page_up","page_down","char_filter"};
        if(SYMBOL.equals(path))return new String[]{"hide","page_up","page_down","BackSpace"};
        if(CLIPBOARD.equals(path))return new String[]{"hide","page_up","page_down","undo"};
        throw new IllegalArgumentException("未知按钮列表："+path);
    }
    static LuaValue configured(LuaValue root,String path){return new PaletteResolver(root).value(path);}
    static LuaTable read(LuaValue root,String path){
        LuaValue value=configured(root,path);if(value.istable())return value.checktable();
        LuaTable list=new LuaTable();int i=1;for(String key:defaults(path))list.rawset(i++,LuaValue.valueOf(key));return list;
    }
    static String fixedLabel(String path,String name){
        if(TOOLBAR.equals(path))return null;
        switch(name){case "hide":return "△";case "page_up":return "⇑";case "page_down":return "⇓";case "char_filter":return CANDIDATE.equals(path)?"全/单":null;default:return null;}
    }
    private static LuaTable copy(LuaValue value){
        LuaTable out=new LuaTable();if(!value.istable())return out;
        for(LuaValue k:value.checktable().keys())out.rawset(k,value.rawget(k));
        LuaValue meta=value.getmetatable();if(meta!=null&&!meta.isnil()){
            if(!meta.istable())throw new IllegalArgumentException("受保护的样式表，请使用源码编辑");
            out.setmetatable(meta);
        }return out;
    }
    /** Preserve explicitly shared Lua tables. An inherited list instead receives a local
     * override, copying only the relevant path and list, not the entire style graph. */
    static void apply(LuaValue root,String path,Edit edit){
        if(!supports(path))throw new IllegalArgumentException("未知按钮列表："+path);
        PaletteResolver resolver=new PaletteResolver(root);LuaValue effective=resolver.value(path);
        if(!effective.isnil()&&!effective.istable())throw new IllegalArgumentException("按钮列表不是 Lua table，请使用源码编辑");
        LuaTable seed=read(root,path);checkSequence(seed);
        String[] parts=path.split("\\.");LuaValue[] inherited=new LuaValue[parts.length-1];String prefix="";
        for(int i=0;i<inherited.length;i++){prefix=prefix.isEmpty()?parts[i]:prefix+"."+parts[i];inherited[i]=resolver.value(prefix);}
        LuaTable parent=root.checktable();boolean detached=false;
        for(int i=0;i<parts.length-1;i++){
            LuaValue own=parent.rawget(parts[i]);
            if(!own.isnil()&&!own.istable())throw new IllegalArgumentException("样式路径不是 Lua table："+parts[i]);
            if(own.isnil()||detached){LuaTable local=copy(own.isnil()?inherited[i]:own);parent.rawset(parts[i],local);parent=local;detached=true;}
            else parent=own.checktable();
        }
        String field=parts[parts.length-1];LuaValue own=parent.rawget(field);
        LuaTable list=!detached&&own.istable()?own.checktable():copy(seed);
        if(list!=own)parent.rawset(field,list);
        edit.apply(list);checkSequence(list);checkFixedButtons(path,list);
    }
    private static void checkSequence(LuaTable list){
        int n=list.length();for(int i=1;i<=n;i++)if(list.rawget(i).isnil())throw new IllegalArgumentException("按钮列表含空槽，请使用源码编辑");
        for(LuaValue k:list.keys())if(k.isint()&&k.toint()>n)throw new IllegalArgumentException("按钮列表不是连续数组，请使用源码编辑");
    }
    private static void checkFixedButtons(String path,LuaTable list){
        if(TOOLBAR.equals(path))return;Set<String> seen=new HashSet<>();
        for(int i=1;i<=list.length();i++){LuaValue e=list.rawget(i);if(e.type()!=LuaValue.TSTRING)continue;String key=e.tojstring();
            if(fixedLabel(path,key)!=null&&!seen.add(key))throw new IllegalArgumentException("内置按钮不能重复添加："+key);}
    }
    private static void index(LuaTable list,int index){if(index<1||index>list.length())throw new IllegalArgumentException("按钮位置已变化，请重新选择");}
    static void insert(LuaTable list,int index,String event,String label){
        if(event.trim().isEmpty())throw new IllegalArgumentException("请输入按键事件");int n=list.length();
        if(index<1||index>n+1)throw new IllegalArgumentException("按钮位置已变化，请重新选择");
        for(int i=n;i>=index;i--)list.rawset(i+1,list.rawget(i));list.rawset(index,entry(event,label));
    }
    private static LuaValue entry(String event,String label){
        if(label==null||label.isEmpty())return LuaValue.valueOf(event);LuaTable value=new LuaTable();value.rawset("click",LuaValue.valueOf(event));value.rawset("label",LuaValue.valueOf(label));return value;
    }
    static void replace(LuaTable list,int index,String event,String label){index(list,index);if(event.trim().isEmpty())throw new IllegalArgumentException("请输入按键事件");list.rawset(index,entry(event,label));}
    static void label(LuaTable list,int index,String label){
        index(list,index);LuaValue e=list.rawget(index);if(!e.istable()||e.get("options").istable())throw new IllegalArgumentException("该按钮需要使用源码编辑");
        e.rawset("label",label.isEmpty()?LuaValue.NIL:LuaValue.valueOf(label));
    }
    static void remove(LuaTable list,int index){index(list,index);int n=list.length();for(int i=index;i<n;i++)list.rawset(i,list.rawget(i+1));list.rawset(n,LuaValue.NIL);}
    static void swap(LuaTable list,int a,int b){index(list,a);index(list,b);LuaValue e=list.rawget(a);list.rawset(a,list.rawget(b));list.rawset(b,e);}
}
