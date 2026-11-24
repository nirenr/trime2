package com.osfans.trime.editor;

import org.luaj.LuaValue;
import org.luaj.LuaTable;
import java.util.*;

/** Schema for optional runtime style properties, plus arbitrary existing Lua fields. */
final class PaletteProperties {
    final Map<String,LuaValue> fields = new TreeMap<>();
    private void add(String p, String names, LuaValue v) { for(String n:names.split(" ")) fields.put(p.isEmpty()?n:p+"."+n,v); }
    private void num(String p,String names,double v){add(p,names,LuaValue.valueOf(v));}
    private void str(String p,String names,String v){add(p,names,LuaValue.valueOf(v));}
    private void bool(String p,String names,boolean v){add(p,names,LuaValue.valueOf(v));}
    private void box(String p){num(p,"background",0xffffffffL);num(p,"corner_radius stroke_width elevation",0);num(p,"stroke_color shadow_color",0);num(p,"width height",48);}
    private void edges(String p){num(p,"left top right bottom",0);}
    private void basic(String p){box(p);num(p,"text_color",0xff000000L);num(p,"text_size",18);str(p,"font","");str(p,"gravity","center");num(p,"offset_x offset_y translation_x translation_y translation_z",0);num(p,"scale_x scale_y",1);bool(p,"show",true);edges(p+".padding");edges(p+".margins");}
    private void key(String p){basic(p);num(p,"long_click_time",1000);num(p,"repeat_click_time",200);bool(p,"sound_enabled vibration_enabled",false);str(p,"sound_effect","");num(p,"vibration_effect.1.1",30);num(p,"vibration_effect.2.1",128);basic(p+".pressed");basic(p+".preview");basic(p+".hint");basic(p+".long_click");num(p+".long_click.pressed","text_color",0xff000000L);num(p+".hint.pressed","text_color",0xff000000L);bool(p+".long_click","sound_enabled vibration_enabled",false);str(p+".long_click","sound_effect","");num(p+".long_click","vibration_effect.1.1",30);num(p+".long_click","vibration_effect.2.1",128);basic(p+".mask");basic(p+".pressed.mask");basic(p+".pressed.hint");basic(p+".pressed.long_click");for(String d:new String[]{"up","down","left","right"}){basic(p+".hint."+d);num(p+".hint."+d+".pressed","text_color",0xff000000L);}}
    PaletteProperties(LuaValue model, List<LuaValue> defaults){
        str("","name author","");num("","background",0xffddddddL);num("","height",288);
        box("keyboard");num("keyboard","height",240);str("keyboard","font","");
        for(String p:new String[]{"key","space","enter","enter2","functional","popup.key","symbol.key","symbol.text","candidate.key","candidate.expanded.key","clipboard.key","clipboard.item","toolbar.key","toolbar.hide","composition.key"})key(p);
        box("popup");num("popup","column_count",5);
        for(String p:new String[]{"candidate","candidate.expanded","symbol","clipboard","toolbar","preedit","composition"}){basic(p);basic(p+".pressed");}
        for(String p:new String[]{"candidate","candidate.expanded"}){basic(p+".comment");basic(p+".comment.pressed");num(p+".pressed","ripple_color",0x40000000);}
        for(String p:new String[]{"symbol","clipboard"}){num(p,"indicator_color",0xff0055ffL);basic(p+".tab_bar");num(p+".tab_bar","indicator_color",0xff0055ffL);basic(p+".tool_bar");}
        str("toolbar.hide","text","▽");basic("candidate.expanded.filter_bar");basic("candidate.expanded.tool_bar");bool("toolbar","schema_switches",false);str("preedit","inline","none");str("composition","position","fixed");str("composition","movable","false");num("composition","min_length sticky_lines min_height line_spacing spacing border round_corner",0);num("composition","max_length",5);num("composition","max_entries cloud_max_entries",-1);num("composition","min_width",10);num("composition","max_width",230);num("composition","max_height",400);num("composition","line_spacing_multiplier",1);bool("composition","all_phrases",false);bool("composition","use_cursor",true);
        for(LuaValue builtin:defaults)collectGlobals(builtin);
        collectGlobals(model);
        fields.keySet().removeIf(p -> !supportedLabelProperty(p));
        // Do not offer children of an existing scalar (e.g. a custom string font).
        Iterator<String> it=fields.keySet().iterator();while(it.hasNext()){String p=it.next();LuaValue cur=model;String[] parts=p.split("\\.");for(int i=0;i<parts.length-1;i++){if(cur.isnil())break;if(!cur.istable()){it.remove();break;}cur=cur.get(luaKey(parts[i]));}}
    }
    // KeyView creates text-only hint/long-click labels; pressed labels read only text_color.
    private static boolean supportedLabelProperty(String path) {
        String[] p=path.split("\\.");
        for(int i=0;i<p.length-1;i++) {
            if(!p[i].equals("hint")&&!p[i].equals("long_click")) continue;
            boolean longClick=p[i].equals("long_click");
            boolean direction=!longClick && i+1<p.length-1 && Arrays.asList("up","down","left","right").contains(p[i+1]);
            int j=i+(direction?2:1);
            if(j>=p.length)return false;
            // Composition reads label color and size through its own renderer.
            if(i>0 && p[i-1].equals("pressed")) return j==p.length-1 && p[j].equals("text_color");
            if(p[j].equals("pressed"))return j+1==p.length-1 && p[j+1].equals("text_color");
            if(longClick && p[j].equals("vibration_effect"))return true;
            if(j!=p.length-1)return false;
            return Arrays.asList("text_color","text_size","font","show","offset_x","offset_y").contains(p[j])
                    || (!direction && p[j].equals("gravity"))
                    || (longClick && Arrays.asList("sound_enabled","sound_effect","vibration_enabled").contains(p[j]));
        }
        return true;
    }
    private void collectGlobals(LuaValue model){
        Set<String> ignored=new HashSet<>(Arrays.asList("_G","_VERSION","math","string","table","coroutine","bit32","package","io","os","debug","luajava"));
        for(LuaValue k:((LuaTable)model).keys()){String n=k.tojstring();if(!ignored.contains(n)&&n.matches("[A-Za-z_][A-Za-z0-9_]*"))collect(n,model.rawget(k),new IdentityHashMap<LuaValue,Boolean>(),0);}
    }
    private void collect(String p,LuaValue v,IdentityHashMap<LuaValue,Boolean> seen,int depth){
        if(depth>16)return;
        if(v.istable()){fields.remove(p);if(seen.put(v,true)!=null)return;for(LuaValue k:((LuaTable)v).keys()){String n=k.tojstring();if(n.matches("[A-Za-z_][A-Za-z0-9_]*|[1-9][0-9]*"))collect(p+"."+n,v.rawget(k),seen,depth+1);}seen.remove(v);}
        else if(v.type()==LuaValue.TNUMBER||v.type()==LuaValue.TSTRING||v.isboolean())fields.put(p,v);
    }
    static LuaValue luaKey(String s){if(s.matches("[1-9][0-9]*")){try{return LuaValue.valueOf(Integer.parseInt(s));}catch(NumberFormatException ignored){}}return LuaValue.valueOf(s);}
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
