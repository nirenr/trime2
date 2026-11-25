package com.osfans.trime.editor;

import java.util.*;
import org.luaj.LuaValue;

/** Build once on the configuration worker; the main thread only reads grouped rows. */
final class PaletteGroupSnapshot {
    static final class Field {
        final String path, origin;
        final LuaValue actual;
        Field(String path,LuaValue actual,String origin){this.path=path;this.actual=actual;this.origin=origin;}
    }
    private final Map<String,Field> fields=new HashMap<>();
    private final Map<String,List<Field>> all=new TreeMap<>(PaletteOrder::groups), defined=new TreeMap<>(PaletteOrder::groups);
    PaletteGroupSnapshot(LuaValue root,PaletteProperties schema){
        PaletteResolver resolver=new PaletteResolver(root);
        PaletteResolver.AliasIndex index=resolver.aliasIndex();
        Map<LuaValue,List<String>> aliases=new IdentityHashMap<>();
        for(String p:schema.fields.keySet()){
            int dot=p.indexOf('.');String group=dot<0?"基本信息":p.substring(0,dot);
            List<Field> full=all.computeIfAbsent(group,k->new ArrayList<>());
            List<Field> visible=defined.computeIfAbsent(group,k->new ArrayList<>());
            // Preserve the previous table-valued property exclusion.
            LuaValue direct=root;
            for(String part:p.split("\\.")){
                if(!direct.istable()){direct=LuaValue.NIL;break;}
                direct=direct.get(PaletteProperties.luaKey(part));
            }
            if(direct.istable())continue;
            PaletteResolver.Result r=resolver.resolve(p);
            List<String> paths=r.value.isnil()?Collections.emptyList():index!=null?index.paths(r.owner):aliases.computeIfAbsent(r.owner,resolver::paths);
            Field row=new Field(p,r.value,resolver.origin(p,r,paths));
            fields.put(p,row);full.add(row);if(!r.value.isnil())visible.add(row);
        }
    }
    Set<String> names(){return Collections.unmodifiableSet(all.keySet());}
    Field field(String path){return fields.get(path);}
    List<Field> rows(String group,boolean optional){List<Field> rows=(optional?all:defined).get(group);return rows==null?Collections.emptyList():Collections.unmodifiableList(rows);}
    /** Null means the list structure changed. Otherwise indices identify rows whose
     * rendered value, suggested default or origin actually needs replacement. */
    static List<Integer> changedRows(List<Field> oldRows,PaletteProperties oldSchema,List<Field> newRows,PaletteProperties newSchema){
        if(oldRows.size()!=newRows.size())return null;
        List<Integer> changed=new ArrayList<>();
        for(int i=0;i<oldRows.size();i++){
            Field a=oldRows.get(i),b=newRows.get(i);if(!a.path.equals(b.path))return null;
            LuaValue av=a.actual.isnil()?oldSchema.fields.getOrDefault(a.path,LuaValue.NIL):a.actual;
            LuaValue bv=b.actual.isnil()?newSchema.fields.getOrDefault(b.path,LuaValue.NIL):b.actual;
            if(a.actual.isnil()!=b.actual.isnil()||!a.origin.equals(b.origin)||!sameScalar(av,bv))changed.add(i);
        }
        return changed;
    }
    private static boolean sameScalar(LuaValue a,LuaValue b){
        return a.type()==b.type()&&(a.isnil()||a.isboolean()||a.type()==LuaValue.TNUMBER||a.type()==LuaValue.TSTRING)
            &&a.raweq(b)&&a.tojstring().equals(b.tojstring());
    }
}
