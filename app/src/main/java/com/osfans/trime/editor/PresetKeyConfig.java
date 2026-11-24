package com.osfans.trime.editor;

import org.luaj.LuaValue;
import org.luaj.Varargs;
import java.util.*;

/** Immutable UI snapshot, built on the editor's I/O worker. */
final class PresetKeyConfig {
    static final String[] TEXT={"label","description","send","text","commit","command","option","select","toggle","preview","shift_lock"};
    static final String[] TITLES={"标签","描述","发送按键","事件文本","直接上屏","命令","命令参数","选择目标","状态开关","弹出提示","Shift 锁定方式"};
    static final String[] BOOL={"sticky","repeatable","functional"};
    static final String[] BOOL_TITLES={"粘滞按键","连续触发","功能键"};
    final String name;
    final boolean local, fallback, editable;
    final String[] values=new String[TEXT.length];
    final boolean[] valid=new boolean[TEXT.length];
    final int[] flags=new int[BOOL.length]; // 0 unset, 1 false, 2 true, -1 unsupported
    String[] states;
    boolean statesEditable=true;
    String fallbackLiteral;
    private PresetKeyConfig(String name,LuaValue v,boolean local,boolean fallback){
        this.name=name;this.local=local;this.fallback=fallback;editable=v.istable();
        if(!editable)return;
        for(int i=0;i<TEXT.length;i++){LuaValue x=v.get(TEXT[i]);valid[i]=x.isnil()||x.type()==LuaValue.TSTRING;values[i]=x.isnil()?null:x.tojstring();}
        for(int i=0;i<BOOL.length;i++){LuaValue x=v.get(BOOL[i]);flags[i]=x.isnil()?0:x.isboolean()?(x.toboolean()?2:1):-1;}
        LuaValue st=v.get("states");
        if(!st.isnil()){
            if(!st.istable()||st.length()>1000)statesEditable=false;
            else {states=new String[st.length()];for(int i=0;i<states.length;i++){LuaValue x=st.rawget(i+1);if(x.type()!=LuaValue.TSTRING){statesEditable=false;break;}states[i]=x.tojstring();}LuaValue k=LuaValue.NIL;while(statesEditable){Varargs p=st.next(k);k=p.arg1();if(k.isnil())break;if(!k.isint()||k.toint()<1||k.toint()>states.length)statesEditable=false;}}
        }
        if(!local)try{fallbackLiteral=ThemeServiceConfig.literal(v,new IdentityHashMap<LuaValue,Boolean>(),0);}catch(RuntimeException e){fallbackLiteral=null;}
    }
    static PresetKeyConfig empty(){return new PresetKeyConfig("",new org.luaj.LuaTable(),true,false);}
    static List<PresetKeyConfig> read(LuaValue globals,LuaValue defaults){
        LuaValue local=globals.get("preset_keys"),base=defaults.get("preset_keys");
        if(!local.isnil()&&!local.istable())throw new IllegalArgumentException("preset_keys 必须为 Lua table");
        Set<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER.thenComparing((a,b)->a.compareTo(b)));
        collect(names,local);collect(names,base);List<PresetKeyConfig> result=new ArrayList<>();
        for(String name:names){LuaValue own=local.istable()?local.rawget(name):LuaValue.NIL;LuaValue def=base.istable()?base.rawget(name):LuaValue.NIL;result.add(new PresetKeyConfig(name,own.isnil()?def:own,!own.isnil(),!def.isnil()));}
        return result;
    }
    private static void collect(Set<String> names,LuaValue table){if(!table.istable())return;LuaValue k=LuaValue.NIL;while(true){Varargs p=table.next(k);k=p.arg1();if(k.isnil())break;if(k.type()==LuaValue.TSTRING)names.add(k.tojstring());}}
    String summary(){String label=values[0];return name+"  ·  "+(local?"主题定义":"内置继承")+(label==null||label.isEmpty()?"":"\n"+label);}
    static String quote(String s){return ThemeServiceConfig.quote(s);}
    static String target(String name){return "preset_keys["+quote(name)+"]";}
    static String delete(String name){return "rawset(preset_keys,"+quote(name)+",nil)";}
    static String assignment(PresetKeyConfig old,String name,String[] text,int[] bool,String[] states){
        String t=target(name);StringBuilder b=new StringBuilder();
        if(old.name.isEmpty()||!old.local){String initial=old.name.isEmpty()?"{}":old.fallbackLiteral;if(initial==null)throw new IllegalArgumentException("内置按键包含复杂值，请使用源码编辑");b.append("rawset(preset_keys,").append(quote(name)).append(',').append(initial).append(")\n");}
        for(int i=0;i<TEXT.length;i++)if(old.valid[i]&&!Objects.equals(text[i],old.values[i]))set(b,t,TEXT[i],text[i]==null?"nil":quote(text[i]));
        for(int i=0;i<BOOL.length;i++)if(old.flags[i]>=0&&bool[i]!=old.flags[i])set(b,t,BOOL[i],bool[i]==0?"nil":bool[i]==1?"false":"true");
        if(old.statesEditable&&!Arrays.equals(states,old.states)){String value="nil";if(states!=null){StringBuilder arr=new StringBuilder("{");for(String s:states)arr.append(quote(s)).append(',');value=arr.append('}').toString();}set(b,t,"states",value);}
        return b.length()==0?"":"if type(preset_keys) ~= 'table' then preset_keys = {} end\n"+b;
    }
    private static void set(StringBuilder b,String target,String field,String value){b.append("rawset(").append(target).append(',').append(quote(field)).append(',').append(value).append(")\n");}
}
