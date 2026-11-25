package com.osfans.trime.editor;

import org.luaj.LuaValue;
import org.luaj.LuaTable;
import java.util.*;

/** Schema for optional runtime style properties, plus arbitrary existing Lua fields. */
final class PaletteProperties {
    final Map<String,LuaValue> fields = new LinkedHashMap<>();
    /** Static field templates and built-in palette values. No live Lua tables are retained. */
    static final class Catalog {
        private final Map<String,LuaValue> fields;
        private Catalog(Map<String,LuaValue> fields){this.fields=Collections.unmodifiableMap(new LinkedHashMap<>(fields));}
    }
    static Catalog catalog(List<LuaValue> defaults){return new Catalog(new PaletteProperties(defaults).fields);}
    private static final java.util.regex.Pattern GLOBAL_NAME=java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final java.util.regex.Pattern PATH_PART=java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|[1-9][0-9]*");
    static boolean validPathPart(String name){return PATH_PART.matcher(name).matches();}
    private static final Set<String> IGNORED_GLOBALS=new HashSet<>(Arrays.asList("_G","_VERSION","math","string","table","coroutine","bit32","package","io","os","debug","luajava"));
    private void add(String p, String names, LuaValue v) { for(String n:names.split(" ")) fields.put(p.isEmpty()?n:p+"."+n,v); }
    private void num(String p,String names,double v){add(p,names,LuaValue.valueOf(v));}
    private void str(String p,String names,String v){add(p,names,LuaValue.valueOf(v));}
    private void bool(String p,String names,boolean v){add(p,names,LuaValue.valueOf(v));}
    private void box(String p){num(p,"background",0xffffffffL);num(p,"corner_radius stroke_width elevation",0);num(p,"stroke_color shadow_color",0);num(p,"width height",48);}
    private void edges(String p){num(p,"left top right bottom",0);}
    private void basic(String p){box(p);num(p,"text_color",0xff000000L);num(p,"text_size",18);str(p,"font","");str(p,"gravity","center");num(p,"offset_x offset_y translation_x translation_y translation_z",0);num(p,"scale_x scale_y",1);bool(p,"show",true);edges(p+".padding");edges(p+".margins");}
    private void background(String p){num(p,"background",0xffffffffL);num(p,"corner_radius stroke_width",0);num(p,"stroke_color",0);}
    private void shadow(String p){num(p,"elevation shadow_color",0);}
    private void textStyle(String p,int fontSize){num(p,"text_color",0xff000000L);num(p,"text_size",fontSize);str(p,"font","");}
    private void feedback(String p){bool(p,"sound_enabled vibration_enabled",false);str(p,"sound_effect","");num(p,"vibration_effect.1.1",30);num(p,"vibration_effect.2.1",128);}
    private void transforms(String p){num(p,"translation_x translation_y translation_z",0);num(p,"scale_x scale_y",1);}
    private void hint(String p,boolean direction){textStyle(p,8);bool(p,"show",true);num(p,"offset_x offset_y",0);if(!direction)str(p,"gravity","center");num(p+".pressed","text_color",0xff000000L);}
    private void key(String p){
        background(p);shadow(p);textStyle(p,18);str(p,"gravity","center");num(p,"offset_x offset_y",0);edges(p+".padding");edges(p+".margins");feedback(p);
        num(p,"long_click_time",1000);num(p,"repeat_click_time",200);feedback(p+".long_click");
        String pressed=p+".pressed";background(pressed);num(pressed,"text_color",0xff000000L);num(pressed,"shadow_color",0);transforms(pressed);
        String preview=p+".preview";background(preview);shadow(preview);num(preview,"text_color",0xff000000L);num(preview,"text_size",18);num(preview,"width height",48);num(preview,"scale_x scale_y",1);
        String mask=pressed+".mask";background(mask);num(mask,"width height",48);bool(mask,"show",true);transforms(mask);
        hint(p+".hint",false);hint(p+".long_click",false);for(String d:new String[]{"up","down","left","right"})hint(p+".hint."+d,true);
    }
    private PaletteProperties(List<LuaValue> defaults){
        str("","name author","");num("","background",0xffddddddL);num("","height",288);
        box("keyboard");num("keyboard","height",240);str("keyboard","font","");
        for(String p:new String[]{"key","space","enter","enter2","functional","popup.key","symbol.key","symbol.text","candidate.key","candidate.expanded.key","clipboard.key","clipboard.item","toolbar.key","toolbar.hide"})key(p);
        num("popup.key","width",10);num("popup.key","height",15);
        num("composition.key","text_size",18);num("composition.key","text_color",0xff000000L);num("composition.key","background",0xffffffffL);
        box("popup");num("popup","column_count",5);
        background("candidate");shadow("candidate");textStyle("candidate",22);num("candidate","height",48);
        background("candidate.pressed");num("candidate.pressed","text_color",0xff000000L);textStyle("candidate.comment",12);num("candidate.comment.pressed","text_color",0xff000000L);
        background("candidate.expanded");background("toolbar");shadow("toolbar");
        for(String p:new String[]{"symbol","clipboard"}){background(p);num(p,"indicator_color",0xff0055ffL);str(p+".tab_bar","gravity","top");num(p+".tab_bar","height",48);num(p+".tab_bar","indicator_color",0xff0055ffL);str(p+".tool_bar","gravity","right");num(p+".tool_bar","height",48);}
        background("preedit");num("preedit","text_size",18);num("preedit","text_color",0xff000000L);
        // The window and text spans have separate fields; keep its existing documented layout settings.
        basic("composition");num("composition.pressed","text_color",0xff000000L);num("composition.pressed","background",0xffffffffL);
        num("composition.key.hint","text_size",12);num("composition.key.hint","text_color",0xff000000L);str("composition.key.hint","font","");num("composition.key.pressed.hint","text_color",0xff000000L);
        str("toolbar.hide","text","▽");str("candidate.key","text","▽");str("candidate.expanded.filter_bar","gravity","left");bool("candidate.expanded.filter_bar","show",true);str("candidate.expanded.tool_bar","gravity","right");bool("toolbar","schema_switches",false);str("preedit","inline","none");str("composition","position","fixed");str("composition","movable","false");num("composition","min_length sticky_lines min_height line_spacing spacing border round_corner",0);num("composition","max_length",5);num("composition","max_entries cloud_max_entries",-1);num("composition","min_width",10);num("composition","max_width",230);num("composition","max_height",400);num("composition","line_spacing_multiplier",1);bool("composition","all_phrases",false);bool("composition","use_cursor",true);
        for(LuaValue builtin:defaults)collectGlobals(builtin);
    }
    PaletteProperties(LuaValue model,List<LuaValue> defaults){this(model,catalog(defaults));}
    PaletteProperties(LuaValue model,Catalog catalog){
        fields.putAll(catalog.fields);
        collectGlobals(model);
        // Dynamic properties can be added/removed by this edit. Order the final set once.
        Map<String,LuaValue> ordered=new LinkedHashMap<>();
        for(String p:PaletteOrder.sortedFields(fields.keySet()))ordered.put(p,fields.get(p));
        fields.clear();fields.putAll(ordered);
        PaletteCapabilities capabilities=new PaletteCapabilities(model);
        fields.keySet().removeIf(p -> !capabilities.supports(p) || StyleButtonLists.contains(p));
        // Do not offer children of an existing scalar (e.g. a custom string font).
        Iterator<String> it=fields.keySet().iterator();while(it.hasNext()){String p=it.next();LuaValue cur=model;String[] parts=p.split("\\.");for(int i=0;i<parts.length-1;i++){if(cur.isnil())break;if(!cur.istable()){it.remove();break;}cur=cur.get(luaKey(parts[i]));}}
    }
    private void collectGlobals(LuaValue model){
        for(LuaValue k:((LuaTable)model).keys()){String n=k.tojstring();if(!IGNORED_GLOBALS.contains(n)&&GLOBAL_NAME.matcher(n).matches())collect(n,model.rawget(k),new IdentityHashMap<LuaValue,Boolean>(),0);}
    }
    private void collect(String p,LuaValue v,IdentityHashMap<LuaValue,Boolean> seen,int depth){
        if(depth>16||StyleButtonLists.contains(p))return;
        if(v.istable()){fields.remove(p);if(seen.put(v,true)!=null)return;for(LuaValue k:((LuaTable)v).keys()){String n=k.tojstring();if(validPathPart(n))collect(p+"."+n,v.rawget(k),seen,depth+1);}seen.remove(v);}
        else if(v.type()==LuaValue.TNUMBER||v.type()==LuaValue.TSTRING||v.isboolean())fields.put(p,v);
    }
    static LuaValue luaKey(String s){
        if(!s.isEmpty()&&s.charAt(0)>='1'&&s.charAt(0)<='9'){
            long n=0;boolean valid=true;
            for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c<'0'||c>'9'){valid=false;break;}n=n*10+c-'0';if(n>Integer.MAX_VALUE){valid=false;break;}}
            if(valid)return LuaValue.valueOf((int)n);
        }
        return LuaValue.valueOf(s);
    }
    static boolean color(String p){String n=p.substring(p.lastIndexOf('.')+1);return n.equals("background")||n.endsWith("_color")||n.equals("color");}
    static String expression(String p){String[] parts=p.split("\\.");String out=parts[0];for(int i=1;i<parts.length;i++)out+=parts[i].matches("[1-9][0-9]*")?"["+parts[i]+"]":"[\""+parts[i]+"\"]";return out;}
    // Never write through an inherited intermediate table. Existing explicit aliases
    // remain shared unless the user explicitly requests a detached branch.
    static String assignment(String p,String literal){return assignment(p,literal,false,null);}
    static String assignment(String p,String literal,boolean detach){return assignment(p,literal,detach,null);}
    static String assignment(String p,String literal,boolean detach,PaletteResolver resolver){
        String[] parts=p.split("\\.");
        StringBuilder b=new StringBuilder("do\nlocal function apply()\nlocal function copy(t) local r={} for k,v in pairs(t) do r[k]=v end local m=getmetatable(t) if type(m)=='table' then setmetatable(r,m) end return r end\nlocal t=_G local copied=false\n");
        String prefix="";
        for(int i=0;i<parts.length-1;i++){
            prefix=prefix.isEmpty()?parts[i]:prefix+"."+parts[i];
            String k=parts[i].matches("[1-9][0-9]*")?parts[i]:"\""+parts[i]+"\"";
            b.append("local k=").append(k).append(" local v=rawget(t,k)\n");
            if(literal.equals("nil"))b.append("if v==nil then return end\n");
            String inherited="t[k]";
            if(resolver!=null && resolver.rawPath(prefix).isnil()) {
                java.util.List<String> paths=resolver.paths(resolver.value(prefix));
                if(!paths.isEmpty()) inherited=expression(paths.get(0));
            }
            b.append("if v==nil then local inherited=").append(inherited).append(" if type(inherited)=='table' then v=copy(inherited) copied=true else v={} end rawset(t,k,v) end\n");
            b.append("if ").append(detach?"true":"copied").append(" and type(v)=='table' then v=copy(v) rawset(t,k,v) end\n");
            b.append("t=v\n");
        }
        String last=parts[parts.length-1];String k=last.matches("[1-9][0-9]*")?last:"\""+last+"\"";
        return b.append("rawset(t,").append(k).append(",").append(literal).append(")\nend\napply()\nend").toString();
    }
}
