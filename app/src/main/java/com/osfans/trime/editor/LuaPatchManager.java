package com.osfans.trime.editor;

import org.luaj.Globals;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Sidecar patches are executed after the original chunk, in its actual environment. */
public final class LuaPatchManager {
    private LuaPatchManager() {}
    public static boolean isPatch(String name) { return name.endsWith(".custom.lua"); }
    public static File file(File base) {
        String name=base.getName();
        if(!name.endsWith(".lua")||isPatch(name)) throw new IllegalArgumentException("Not a base Lua file: "+base);
        return new File(base.getParentFile(),name.substring(0,name.length()-4)+".custom.lua");
    }
    public static String read(File base) throws IOException {
        File patch=file(base); return patch.exists()?readText(patch):"";
    }
    private static String readText(File path) throws IOException {
        try(InputStream in=new FileInputStream(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    public static void apply(Globals globals,LuaTable env,String patch) {
        if(!patch.trim().isEmpty())globals.load(patch,"@editor.custom.lua",env).call(env);
    }
    private static final String BASE_HEADER="-- Trime editor base-sha256: ";
    private static String fingerprint(String base) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    /** A generated array delta is tied to the exact original file. Do not apply it to a changed base. */
    public static String bindToBase(String base,String delta){return delta.trim().isEmpty()?"":BASE_HEADER+fingerprint(base)+"\n"+delta;}
    public static void verifyBase(String base,String patch){
        if(patch.startsWith(BASE_HEADER)){
            int end=patch.indexOf('\n');String expected=patch.substring(BASE_HEADER.length(),end<0?patch.length():end).trim();
            if(!expected.equals(fingerprint(base)))throw new IllegalArgumentException("原始 Lua 文件已变化，补丁未应用；请核对补丁或清除后重新编辑");
        }
    }
    public static void applyChecked(Globals globals,LuaTable env,String base,String patch){verifyBase(base,patch);apply(globals,env,patch);}
    public static void applyFile(Globals globals,LuaTable env,File base) throws IOException {
        if(!base.isFile()||isPatch(base.getName()))return;
        String patch=read(base);if(patch.startsWith(BASE_HEADER))verifyBase(readText(base),patch);apply(globals,env,patch);
    }
    private static LuaValue nil(LuaValue v){return v==null?LuaValue.NIL:v;}
    private static boolean same(LuaValue a,LuaValue b){return nil(a).raweq(nil(b));}
    private static String literal(LuaValue v){
        if(v.isnil())return "nil";
        if(v.isboolean())return v.toboolean()?"true":"false";
        if(v.type()==LuaValue.TSTRING){StringBuilder s=new StringBuilder("\"");String str=v.tojstring();for(int i=0;i<str.length();i++){char c=str.charAt(i);if(c=='\\'||c=='\"')s.append('\\').append(c);else if(c<32||c==127)s.append(String.format(Locale.ROOT,"\\%03d",(int)c));else s.append(c);}return s.append('"').toString();}
        if(v.isnumber()){double d=v.todouble();if(Double.isNaN(d))return "(0/0)";if(Double.isInfinite(d))return d>0?"(1/0)":"(-1/0)";return v.tojstring();}
        throw new IllegalArgumentException("补丁无法序列化新建的 "+v.typename()+"；请使用补丁源码模式");
    }
    private static boolean scalar(LuaValue v){return v.isnil()||v.isboolean()||v.type()==LuaValue.TSTRING||v.isnumber();}
    /** Capture original identities BEFORE executing edits. Array edits become final raw slots,
     * not replayed insert/remove operations. Existing objects (including functions) are referenced. */
    public static final class Snapshot {
        private final LuaTable root;
        private final IdentityHashMap<LuaValue,Origin> origins=new IdentityHashMap<>();
        private final IdentityHashMap<LuaTable,State> states=new IdentityHashMap<>();
        private final ArrayList<LuaTable> tables=new ArrayList<>();
        private static final class Origin {final LuaTable parent;final LuaValue key;final boolean meta;Origin(LuaTable p,LuaValue k,boolean m){parent=p;key=k;meta=m;}}
        private static final class State {final LinkedHashMap<LuaValue,LuaValue> entries=new LinkedHashMap<>();final LuaValue meta;State(LuaTable t){meta=nil(t.getmetatable());for(LuaValue k:t.keys())entries.put(k,t.rawget(k));}}
        public Snapshot(LuaTable root){this.root=root;visit(root,new Origin(null,null,false));}
        private void visit(LuaValue value,Origin o){
            if(scalar(value)||origins.containsKey(value))return;
            origins.put(value,o);if(!value.istable())return;
            LuaTable t=value.checktable();State state=new State(t);states.put(t,state);tables.add(t);
            for(Map.Entry<LuaValue,LuaValue> e:state.entries.entrySet())if(scalar(e.getKey()))visit(e.getValue(),new Origin(t,e.getKey(),false));
            if(!state.meta.isnil())visit(state.meta,new Origin(t,null,true));
        }
        public String delta(){return new Writer().build();}
        private final class Writer {
            final IdentityHashMap<LuaValue,Integer> refs=new IdentityHashMap<>();
            final IdentityHashMap<LuaTable,Boolean> seen=new IdentityHashMap<>();
            final StringBuilder declarations=new StringBuilder(),writes=new StringBuilder(),metas=new StringBuilder();
            String ref(LuaValue v){
                if(scalar(v))return literal(v);
                if(v==root)return "root";
                Integer i=refs.get(v);if(i!=null)return "r["+i+"]";
                Origin o=origins.get(v);String expr;
                if(o!=null){String parent=ref(o.parent);expr=o.meta?"getmetatable("+parent+")":"rawget("+parent+","+literal(o.key)+")";}
                else if(v.istable())expr="{}";
                else throw new IllegalArgumentException("补丁无法安全保存新建的 "+v.typename());
                i=refs.size()+1;refs.put(v,i);String name="r["+i+"]";declarations.append(name).append(" = ").append(expr).append('\n');return name;
            }
            void walk(LuaTable t){
                if(seen.put(t,true)!=null)return;
                State before=states.get(t),after=new State(t);
                if(before!=null)for(Map.Entry<LuaValue,LuaValue> e:before.entries.entrySet())if(t.rawget(e.getKey()).isnil())write(t,e.getKey(),LuaValue.NIL);
                for(Map.Entry<LuaValue,LuaValue> e:after.entries.entrySet()){
                    LuaValue v=e.getValue();if(before==null||!same(before.entries.get(e.getKey()),v))write(t,e.getKey(),v);
                    if(v.istable())walk(v.checktable());
                }
                if(!same(before==null?LuaValue.NIL:before.meta,after.meta))metas.append("setmetatable(").append(ref(t)).append(',').append(ref(after.meta)).append(")\n");
                if(after.meta.istable())walk(after.meta.checktable());
            }
            void write(LuaTable t,LuaValue key,LuaValue value){writes.append("rawset(").append(ref(t)).append(',').append(literal(key)).append(',').append(ref(value)).append(")\n");}
            String build(){walk(root);if(writes.length()==0&&metas.length()==0)return "";return "-- Trime editor: managed sidecar; final differences from base\nlocal root = ...\nlocal r = {}\n"+declarations+writes.toString()+metas;}
        }
    }
}
