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
    private final Map<String,List<Field>> all=new TreeMap<>(), defined=new TreeMap<>();
    PaletteGroupSnapshot(LuaValue root,PaletteProperties schema){
        PaletteResolver resolver=new PaletteResolver(root);
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
            List<String> paths=r.value.isnil()?Collections.emptyList():aliases.computeIfAbsent(r.owner,resolver::paths);
            Field row=new Field(p,r.value,resolver.origin(p,r,paths));
            fields.put(p,row);full.add(row);if(!r.value.isnil())visible.add(row);
        }
    }
    Set<String> names(){return Collections.unmodifiableSet(all.keySet());}
    Field field(String path){return fields.get(path);}
    List<Field> rows(String group,boolean optional){List<Field> rows=(optional?all:defined).get(group);return rows==null?Collections.emptyList():Collections.unmodifiableList(rows);}
}
