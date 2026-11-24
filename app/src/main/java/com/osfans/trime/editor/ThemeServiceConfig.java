package com.osfans.trime.editor;

import org.luaj.LuaValue;
import org.luaj.Varargs;
import java.util.IdentityHashMap;

/** Snapshot of the fields consumed by ThemeManager.loadAiClient/loadAsrClient. */
final class ThemeServiceConfig {
    final String path;
    final String[] names, values;
    boolean extraTable, extraEditable=true;
    private ThemeServiceConfig(String path) {
        this.path=path;
        names="asr_config".equals(path)?new String[]{"base_url","model","key","prompt","system_prompt","extra_params"}:new String[]{"base_url","model","key","system_prompt","extra_params"};
        values=new String[names.length];
    }
    static ThemeServiceConfig read(LuaValue globals,String path){
        ThemeServiceConfig c=new ThemeServiceConfig(path);LuaValue table=globals.get(path);
        for(int i=0;i<c.names.length;i++){
            LuaValue v=table.istable()?table.get(c.names[i]):LuaValue.NIL;
            if("extra_params".equals(c.names[i])&&v.istable()){
                c.extraTable=true;
                try{c.values[i]=literal(v,new IdentityHashMap<LuaValue,Boolean>(),0);}catch(RuntimeException ex){c.extraEditable=false;c.values[i]="";}
            }else c.values[i]=v.isstring()?v.tojstring():"";
        }
        return c;
    }
    boolean configured(){return !values[0].isEmpty()&&!values[1].isEmpty()&&!values[2].isEmpty();}
    static String title(String name){switch(name){case "base_url":return "API 地址";case "model":return "模型名称";case "key":return "API Key";case "prompt":return "识别提示词";case "system_prompt":return "系统提示词";case "extra_params":return "扩展参数";default:return name;}}
    static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t").replace(String.valueOf((char)0),"\\000")+"\"";}
    static String literal(LuaValue v,IdentityHashMap<LuaValue,Boolean> active,int depth){
        if(depth>32)throw new IllegalArgumentException();
        if(v.type()==LuaValue.TSTRING)return quote(v.tojstring());
        if(v.type()==LuaValue.TNUMBER){double n=v.todouble();if(Double.isNaN(n)||Double.isInfinite(n))throw new IllegalArgumentException();return v.tojstring();}
        if(v.isboolean()||v.isnil())return v.tojstring();
        if(!v.istable()||active.put(v,Boolean.TRUE)!=null)throw new IllegalArgumentException();
        StringBuilder b=new StringBuilder("{");LuaValue k=LuaValue.NIL;
        try{while(true){Varargs pair=v.next(k);k=pair.arg1();if(k.isnil())break;if(k.type()!=LuaValue.TSTRING&&k.type()!=LuaValue.TNUMBER&& !k.isboolean())throw new IllegalArgumentException();b.append("[").append(literal(k,active,depth+1)).append("]=").append(literal(pair.arg(2),active,depth+1)).append(",");if(b.length()>1000000)throw new IllegalArgumentException();}return b.append("}").toString();}finally{active.remove(v);}
    }
    static String assignment(ThemeServiceConfig c,String[] inputs,boolean tableMode){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<c.names.length;i++){
            String n=c.names[i],s=inputs[i];boolean extra="extra_params".equals(n);
            if(extra&&!c.extraEditable)continue;
            if(s.equals(c.values[i])&&(!extra||tableMode==c.extraTable))continue;
            String value=quote(s);
            if(extra&&!s.trim().isEmpty()){
                if(tableMode){if(!s.trim().startsWith("{")||!s.trim().endsWith("}"))throw new IllegalArgumentException("Lua 扩展参数必须为表，例如 {stream=false}");value="("+s+"\n)";}
                else{try{new org.json.JSONObject(s);}catch(org.json.JSONException e){throw new IllegalArgumentException("扩展参数必须是有效的 JSON 对象。");}}
            }else if(extra&&tableMode)value="{}";
            b.append(c.path).append('.').append(n).append(" = ").append(value).append('\n');
        }
        if(b.length()==0)return "";
        return "if type("+c.path+") ~= 'table' then "+c.path+" = {} end\n"+b;
    }
}
