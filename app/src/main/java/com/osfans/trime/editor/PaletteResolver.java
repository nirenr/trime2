package com.osfans.trime.editor;

import org.luaj.LuaValue;
import org.luaj.LuaTable;
import java.util.*;

/** Non-mutating projection of the renderer's explicit Style/KeyStyle fallback rules. */
final class PaletteResolver {
    final LuaValue root;
    final Set<String> extraKeys;
    PaletteResolver(LuaValue root) { this(root,Collections.emptySet()); }
    PaletteResolver(LuaValue root,Set<String> extraKeys) { this.root = root == null ? LuaValue.NIL : root; this.extraKeys=extraKeys; }
    static final class Result {
        final LuaValue value, owner;
        Result(LuaValue value, LuaValue owner) { this.value=value; this.owner=owner; }
    }
    private static final Result MISSING = new Result(LuaValue.NIL,LuaValue.NIL);
    private static final Set<String> KEY_STYLES=new HashSet<>(Arrays.asList("key","space","enter","enter2","functional","candidate","candidate.comment","composition","toolbar.key","toolbar.hide","composition.key","candidate.key","candidate.expanded.key","popup.key","symbol.key","symbol.text","clipboard.key","symbol.tab_bar","symbol.tool_bar","clipboard.tab_bar","clipboard.tool_bar","candidate.expanded.filter_bar","candidate.expanded.tool_bar"));
    private static final Set<String> KEY_CHILDREN=new HashSet<>(Arrays.asList("pressed","hint","long_click","preview","mask","up","down","left","right"));
    private static final Set<String> DIRECTIONS=new HashSet<>(Arrays.asList("up","down","left","right"));
    private static final Set<String> PATH_IGNORED_ROOTS=new HashSet<>(Arrays.asList("_G","math","string","table","coroutine","bit32","package"));
    private String fallback(String p) {
        if(extraKeys.contains(p)&&!p.equals("key"))return "key";
        switch(p) {
            case "space": case "enter": case "enter2": case "functional":
            case "toolbar.key": case "composition.key": case "candidate.key":
            case "candidate.expanded.key": case "popup.key": case "symbol.key":
            case "symbol.text": case "clipboard.key": return "key";
            case "toolbar.hide": return "toolbar.key";
            case "popup": return "keyboard";
            case "candidate.expanded": return "candidate";
            case "symbol.tab_bar": case "symbol.tool_bar": case "clipboard.tab_bar": case "clipboard.tool_bar": return "candidate";
        }
        int i=p.lastIndexOf('.'); if(i<0)return null;
        String parent=p.substring(0,i), child=p.substring(i+1);
        if (child.equals("pressed") || child.equals("hint") || child.equals("long_click") || child.equals("preview")) {
            if (isKey(parent)) return parent;
        }
        if (p.equals("candidate.comment")) return "candidate";
        if (parent.endsWith(".hint") && isKey(parent) && DIRECTIONS.contains(child)) return parent;
        return null;
    }
    private boolean isKey(String p) {
        if(extraKeys.contains(p))return true;
        if(KEY_STYLES.contains(p))return true;
        int i=p.lastIndexOf('.'); if(i<0)return false;
        String child=p.substring(i+1); return KEY_CHILDREN.contains(child)&&isKey(p.substring(0,i));
    }
    LuaValue rawPath(String p) { LuaValue t=root; if(p.isEmpty())return t; for(String k:p.split("\\.")){if(!t.istable())return LuaValue.NIL;t=t.rawget(PaletteProperties.luaKey(k));}return t; }
    private Result lookup(String style, String field, int depth) {
        if(depth>40)return MISSING;
        LuaValue t;
        if(style.isEmpty())t=root;
        else {int i=style.lastIndexOf('.');t=lookup(i<0?"":style.substring(0,i),i<0?style:style.substring(i+1),depth+1).value;}
        String def=fallback(style);
        LuaValue k=PaletteProperties.luaKey(field);
        if(t.istable()) {
            LuaValue own=t.rawget(k);if(!own.isnil())return new Result(own,t);
            // Style(t, def) replaces __index, not a merge with the original metatable.
            if(def==null) { LuaValue v=t.get(k); if(!v.isnil()) {LuaValue owner=metaOwner(t,k);return new Result(v,owner);} }
        }
        return def==null?MISSING:lookup(def,field,depth+1);
    }
    private LuaValue metaOwner(LuaValue t,LuaValue k) {
        Set<LuaValue> seen=Collections.newSetFromMap(new IdentityHashMap<LuaValue,Boolean>());
        while(t.istable()&&seen.add(t)){if(!t.rawget(k).isnil())return t;LuaValue mt=t.getmetatable();if(mt==null||!mt.istable())break;t=mt.rawget(LuaValue.valueOf("__index"));}return LuaValue.NIL;
    }
    Result resolve(String p) {int i=p.lastIndexOf('.');return lookup(i<0?"":p.substring(0,i),i<0?p:p.substring(i+1),0);}
    // Nested padding/margins are independent Style tables, not per-edge merges.
    LuaValue value(String p) {return resolve(p).value;}
    boolean hasRaw(String style,String field) {
        LuaValue t=value(style);
        for(int i=0;!t.istable()&&i<30;i++){style=fallback(style);if(style==null)return false;t=value(style);}
        return t.istable()&&!t.rawget(LuaValue.valueOf(field)).isnil();
    }
    List<String> paths(LuaValue target) {List<String> out=new ArrayList<>();if(target.istable())walk(root,"",target,out,new IdentityHashMap<LuaValue,Boolean>(),0);return out;}
    private void walk(LuaValue t,String path,LuaValue target,List<String> out,IdentityHashMap<LuaValue,Boolean> stack,int depth){
        if(!t.istable()||depth>12||out.size()>=32||stack.put(t,true)!=null)return;
        if(t==target&&!path.isEmpty())out.add(path);
        for(LuaValue k:((LuaTable)t).keys()){String n=k.tojstring();if(!PaletteProperties.validPathPart(n)||path.isEmpty()&&PATH_IGNORED_ROOTS.contains(n))continue;LuaValue v=t.rawget(k);if(v.istable())walk(v,path.isEmpty()?n:path+"."+n,target,out,stack,depth+1);}
        stack.remove(t);
    }
    /** One bounded DFS collects the same first 32 paths per table as paths(target).
     * A dynamic __index can change the graph during resolution. In that case retain the
     * previous per-owner traversal behavior rather than publishing a stale index. */
    static final class AliasIndex {
        private final IdentityHashMap<LuaValue,List<String>> paths=new IdentityHashMap<>();
        private int budget=16384;
        private boolean dynamic;
        private AliasIndex(LuaValue root){
            dynamic=hasDynamicLookup(root);
            if(!dynamic)walkAll(root,"",new IdentityHashMap<LuaValue,Boolean>(),0);
        }
        private static boolean hasDynamicLookup(LuaValue root){
            Set<LuaValue> seen=Collections.newSetFromMap(new IdentityHashMap<LuaValue,Boolean>());
            ArrayDeque<LuaValue> pending=new ArrayDeque<>();
            if(root.istable()){pending.add(root);seen.add(root);}
            int remaining=16384;
            while(!pending.isEmpty()){
                LuaValue t=pending.removeFirst();if(--remaining<0)return true;
                // Java-backed tables can implement get() differently; keep the conservative path.
                if(t.getClass()!=LuaTable.class&&t.getClass()!=org.luaj.Globals.class)return true;
                LuaValue mt=t.getmetatable();
                if(mt!=null&&!mt.isnil()){
                    if(!mt.istable())return true;
                    LuaValue index=mt.rawget(LuaValue.valueOf("__index"));
                    if(!index.isnil()&&!index.istable())return true;
                    // Weak tables may change during collection; do not cache their paths.
                    if(!mt.rawget(LuaValue.valueOf("__mode")).isnil())return true;
                    if(index.istable()&&seen.add(index))pending.add(index);
                }
                for(LuaValue k:((LuaTable)t).keys()){
                    if(t==root&&PATH_IGNORED_ROOTS.contains(k.tojstring()))continue;
                    LuaValue child=t.rawget(k);
                    if(child.istable()&&seen.add(child))pending.add(child);
                    if(seen.size()>16384)return true;
                }
            }
            return false;
        }
        private void walkAll(LuaValue t,String path,IdentityHashMap<LuaValue,Boolean> stack,int depth){
            if(dynamic||!t.istable()||depth>12||stack.put(t,true)!=null)return;
            if(--budget<0){dynamic=true;return;}
            if(!path.isEmpty()){
                List<String> list=paths.computeIfAbsent(t,k->new ArrayList<>());
                if(list.size()<32)list.add(path);
            }
            for(LuaValue k:((LuaTable)t).keys()){
                String n=k.tojstring();if(!PaletteProperties.validPathPart(n)||path.isEmpty()&&PATH_IGNORED_ROOTS.contains(n))continue;
                LuaValue v=t.rawget(k);if(v.istable())walkAll(v,path.isEmpty()?n:path+"."+n,stack,depth+1);
                if(dynamic)return;
            }
            stack.remove(t);
        }
        List<String> paths(LuaValue target){List<String> list=paths.get(target);return list==null?Collections.emptyList():list;}
    }
    AliasIndex aliasIndex(){AliasIndex index=new AliasIndex(root);return index.dynamic?null:index;}
    private String join(List<String> paths){StringBuilder b=new StringBuilder();for(String p:paths){if(b.length()>0)b.append(" / ");b.append(p);}return b.toString();}
    String origin(String p) {Result r=resolve(p);return origin(p,r,paths(r.owner));}
    String origin(String p,Result r,List<String> aliases) {if(r.value.isnil())return "未设置 · 建议值（非实际默认值）";int i=p.lastIndexOf('.');String parent=i<0?"":p.substring(0,i),field=i<0?p:p.substring(i+1);if(r.owner==rawPath(parent)){return aliases.size()>1?"共享引用 · "+join(aliases):"当前独立定义";}return aliases.isEmpty()?"继承 / 动态值":"继承自 "+aliases.get(0)+"."+field;}
    String sourcePath(String p) {Result r=resolve(p);List<String> aliases=paths(r.owner);int i=p.lastIndexOf('.');return aliases.isEmpty()?null:aliases.get(0)+"."+p.substring(i+1);}
}
