package com.osfans.trime.editor;

import java.util.*;
import java.util.regex.*;

/** Pure Java completion using the same catalog as the visual schema editor. */
public final class RimeYamlCompletion {
    private RimeYamlCompletion() {}
    public static final class Item {
        public final String label, detail, text;
        Item(String label,String detail,String text){this.label=label;this.detail=detail;this.text=text;}
        @Override public String toString(){return label+"\n"+detail;}
    }
    public static final class Result {
        public final int start,end;
        public final List<Item> items;
        Result(int start,int end,List<Item> items){this.start=start;this.end=end;this.items=Collections.unmodifiableList(items);}
    }
    private static final Pattern KEY=Pattern.compile("^( *)(- +)?([\"']?)([a-zA-Z0-9_/@.+-]*)$");
    private static final Pattern VALUE=Pattern.compile("^( *)(- +)?([a-zA-Z0-9_./@+-]+|\"[^\"]+\"|'[^']+'):[ ]+([a-zA-Z0-9_@.-]*)$");
    private static final Pattern LIST=Pattern.compile("^( *)- +([a-zA-Z0-9_@.-]*)$");
    private static SchemaDocument.Node find(SchemaDocument.Node n,String marker){
        if(marker.equals(n.key)||marker.equals(n.value))return n;
        for(SchemaDocument.Node c:n.children){SchemaDocument.Node found=find(c,marker);if(found!=null)return found;}return null;
    }
    private static void add(List<Item> out,String prefix,String key,String detail,String text){
        if(key.startsWith(prefix)&&out.size()<80)out.add(new Item(key,detail,text));
    }
    public static Result complete(String source,int caret){
        List<Item> out=new ArrayList<>();Result empty=new Result(caret,caret,out);
        if(source==null||caret<0||caret>source.length()||source.length()>300000)return empty;
        int line=source.lastIndexOf('\n',caret-1)+1, end=source.indexOf('\n',caret);if(end<0)end=source.length();
        String before=source.substring(line,caret),after=source.substring(caret,end);
        // Do not modify existing values or suffixes. Only complete a token at line end.
        if(!after.trim().isEmpty())return empty;
        String marker="rime_completion_marker";while(source.contains(marker))marker+="_";
        Matcher v=VALUE.matcher(before), l=LIST.matcher(before), k=KEY.matcher(before);
        boolean value=v.matches(), list=l.matches(), key=k.matches();if(!value&&!list&&!key)return empty;
        String prefix;int start;String synthetic;
        if(value){prefix=v.group(4);start=line+v.start(4);synthetic=before.substring(0,v.start(4))+marker;}
        else if(list){prefix=l.group(2);start=line+l.start(2);synthetic=before.substring(0,l.start(2))+marker;}
        else{prefix=k.group(4);start=line+k.start(3);synthetic=before.substring(0,k.start(3))+marker+": null";}
        if(prefix.isEmpty()&&!value&&!list)return empty;
        try{
            SchemaDocument doc=new SchemaDocument(source.substring(0,line)+synthetic+source.substring(caret));
            SchemaDocument.Node node=find(doc.root,marker);if(node==null||node.parent==null||node.parent.kind==SchemaDocument.Kind.OPAQUE)return empty;
            if(value){
                SchemaProperties.Field field=SchemaProperties.get(doc,node.path());
                if(field!=null&&field.kind==SchemaDocument.Kind.BOOL){add(out,prefix,"true","布尔值","true");add(out,prefix,"false","布尔值","false");}
                for(String choice:SchemaProperties.choices(node))add(out,prefix,choice,"可选值",choice);
            }else{
                if(list){for(String choice:SchemaProperties.listChoices(node.parent))add(out,prefix,choice,"引擎组件",choice);}
                if(!list||out.isEmpty()){
                    if(list){doc=new SchemaDocument(source.substring(0,line)+before.substring(0,l.start(2))+marker+": null"+source.substring(caret));node=find(doc.root,marker);if(node==null||node.parent==null)return empty;}
                    boolean patch=node.parent.path().equals("patch");
                    Map<String,SchemaProperties.Field> fields=patch?SchemaProperties.patchSuggestions(doc,node.parent):SchemaProperties.suggestions(doc,node.parent);
                    if(node.parent==doc.root){
                        for(String group:new String[]{"simplifier","reverse_lookup","affix_segmentor","abc_segmentor"})
                            if(doc.root.child(group)==null)fields.putIfAbsent(group,new SchemaProperties.Field(group,"组件配置",SchemaDocument.Kind.MAP,SchemaProperties.WIKI));
                    }
                    for(Map.Entry<String,SchemaProperties.Field> e:fields.entrySet()){
                        String name=e.getKey();SchemaProperties.Field f=e.getValue();String insert=(patch?"\""+name+"\"":name)+":";
                        if(f.kind!=SchemaDocument.Kind.MAP&&f.kind!=SchemaDocument.Kind.LIST)insert+=" ";
                        add(out,prefix,name,f.name,insert);
                    }
                }
            }
            return new Result(start,caret,out);
        }catch(RuntimeException ignored){return empty;}
    }
}
