package com.osfans.trime.editor;

import org.luaj.LuaValue;
import java.util.*;

/** Renderer-specific property capabilities. Unknown styles are not destructively filtered. */
final class PaletteCapabilities {
    private static final Set<String> KEYBOARD = set("key space enter enter2 functional");
    private static final Set<String> BUTTONS = set("toolbar.hide toolbar.key candidate.key candidate.expanded.key popup.key symbol.key symbol.text clipboard.key clipboard.item");
    private static final Set<String> NON_KEY = set("keyboard popup candidate candidate.expanded candidate.comment candidate.expanded.comment symbol clipboard toolbar preedit composition symbol.tab_bar symbol.tool_bar clipboard.tab_bar clipboard.tool_bar candidate.expanded.filter_bar candidate.expanded.tool_bar");
    private static final Set<String> FACE = set("background text_color text_size font gravity offset_x offset_y corner_radius stroke_color stroke_width elevation shadow_color");
    private static final Set<String> PRESSED = set("background text_color corner_radius stroke_color stroke_width shadow_color scale_x scale_y translation_x translation_y translation_z");
    private static final Set<String> PREVIEW = set("background text_color text_size corner_radius stroke_color stroke_width elevation shadow_color width height scale_x scale_y");
    private static final Set<String> MASK = set("background corner_radius stroke_color stroke_width width height show scale_x scale_y translation_x translation_y translation_z");
    private static final Set<String> HINT = set("show text_color text_size font offset_x offset_y");
    private static final Set<String> FEEDBACK = set("sound_enabled sound_effect vibration_enabled");
    private static final Set<String> COMPOSITION_KEY = set("background text_color text_size hint.text_color hint.text_size pressed.hint.text_color");
    private static final Set<String> DIRECTIONS = set("up down left right");
    private final Set<String> toolbarHints=new HashSet<>();
    private boolean toolbarEvents;
    PaletteCapabilities(LuaValue root){
        LuaValue toolbar=root.get("toolbar");
        LuaValue keys=toolbar.istable()?toolbar.get("keys"):LuaValue.NIL;
        if(keys.istable())for(int i=1;i<=keys.length();i++){
            LuaValue k=keys.get(i);
            if(k.type()==LuaValue.TSTRING){toolbarEvents=true;continue;}
            if(!k.istable()||k.get("options").istable())continue;
            toolbarEvents=true;
            if(!k.get("click").isnil())scan(k,0);
        }
    }
    private void scan(LuaValue k,int depth){
        if(depth>12)return;
        if(!k.get("long_click").isnil())toolbarHints.add("long_click");
        if(!k.get("hint").optjstring("").isEmpty())toolbarHints.add("hint");
        for(String d:DIRECTIONS)if(!k.get("hint_"+d).isnil()||!k.get("swipe_"+d).isnil()){toolbarHints.add("hint");toolbarHints.add("hint."+d);}
        LuaValue ascii=k.get("ascii");if(ascii.istable()&&!ascii.get("click").isnil())scan(ascii,depth+1);
    }
    private static Set<String> set(String s){return new HashSet<>(Arrays.asList(s.split(" ")));}
    private static boolean under(String p,String root){return p.startsWith(root+".");}
    private static boolean edge(String p){return p.matches("(padding|margins)\\.(left|top|right|bottom)");}
    private static boolean font(String p){return p.equals("font")||p.matches("font\\.[1-9][0-9]*");}
    private static boolean feedback(String p){return FEEDBACK.contains(p)||p.matches("vibration_effect(\\.[1-9][0-9]*)+");}
    private static boolean hintField(String s,boolean direction,boolean longer){return HINT.contains(s)||font(s)||s.equals("pressed.text_color")||(!direction&&s.equals("gravity"))||(longer&&feedback(s));}
    static boolean isButton(String style){return KEYBOARD.contains(style)||BUTTONS.contains(style);}
    static boolean keyboardEffects(String style){return KEYBOARD.contains(style)||style.equals("popup.key");}
    static String buttonOwner(String path){for(String p:KEYBOARD)if(path.equals(p)||under(path,p))return p;for(String p:BUTTONS)if(path.equals(p)||under(path,p))return p;return null;}
    boolean supports(String path){
        if(under(path,"composition.key")){
            String s=path.substring("composition.key.".length());
            return COMPOSITION_KEY.contains(s)||s.startsWith("hint.")&&font(s.substring(5));
        }
        String owner=null;
        for(String p:KEYBOARD)if(under(path,p))owner=p;
        for(String p:BUTTONS)if(under(path,p))owner=p;
        if(owner!=null){
            String s=path.substring(owner.length()+1);
            boolean keyboard=KEYBOARD.contains(owner), popup=owner.equals("popup.key"), toolbar=owner.equals("toolbar.key");
            if(FACE.contains(s)||font(s)||edge(s)||feedback(s))return true;
            if(s.equals("text")&&(owner.equals("toolbar.hide")||owner.equals("candidate.key")))return true;
            if((s.equals("width")||s.equals("height"))&&popup)return true;
            boolean hasEvent=keyboard||popup||toolbar&&toolbarEvents||owner.equals("symbol.key")||owner.equals("clipboard.key")||owner.equals("candidate.expanded.key");
            if(s.equals("long_click_time")||s.equals("repeat_click_time"))return hasEvent;
            if(s.startsWith("preview."))return (keyboard||popup)&&PREVIEW.contains(s.substring(8));
            if(s.startsWith("pressed.mask."))return (keyboard||popup)&&MASK.contains(s.substring(13));
            if(s.startsWith("pressed."))return PRESSED.contains(s.substring(8));
            if(s.startsWith("long_click.")){String rest=s.substring(11);return feedback(rest)?hasEvent:(keyboard||toolbar&&toolbarHints.contains("long_click"))&&hintField(rest,false,false);}
            if(s.startsWith("hint.")){
                String rest=s.substring(5);int dot=rest.indexOf('.');String first=dot<0?rest:rest.substring(0,dot);boolean dir=DIRECTIONS.contains(first);
                return (keyboard||toolbar&&toolbarHints.contains(dir?"hint."+first:"hint"))&&hintField(dir&&dot>=0?rest.substring(dot+1):rest,dir,false);
            }
            return false;
        }
        // Only filter key-only subtrees on known non-key renderers. Other container fields
        // and unknown extension styles remain available rather than guessing their semantics.
        for(String p:NON_KEY)if(under(path,p)){
            String s=path.substring(p.length()+1);
            if(s.matches("(hint|long_click|preview|mask|pressed\\.(hint|long_click|mask))(\\..*)?"))return false;
        }
        return true;
    }
}
