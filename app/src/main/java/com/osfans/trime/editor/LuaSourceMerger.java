package com.osfans.trime.editor;

import org.luaj.Globals;
import org.luaj.LuaClosure;
import org.luaj.LuaString;
import org.luaj.LoadState;
import org.luaj.compiler.LuaC;
import org.luaj.lib.BaseLib;
import org.luaj.lib.PackageLib;
import org.luaj.lib.TableLib;
import org.luaj.lib.StringLib;
import org.luaj.LuaValue;
import org.luaj.Prototype;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit, conservative source consolidation. Never appends the sidecar program.
 *  It first rewrites existing literal locations. A structural rewrite is used only
 *  when necessary; every proposed result must reproduce the patched Lua graph.
 *  The supported base syntax is declarative assignments, table constructors,
 *  static references, arithmetic, table.clone, setmetatable and named functions.
 *  Unsupported executable top-level statements are rejected rather than flattened.
 */
public final class LuaSourceMerger {
    private LuaSourceMerger() {}
    public interface Evaluator { Globals evaluate(String source); }
    public static Globals evaluateFile(String source) { return evaluateSandbox(source,true); }
    public static Globals evaluateEditor(String source) { return evaluateSandbox(source,false); }
    private static Globals evaluateSandbox(String source,boolean file) {
        Globals g=new Globals();g.load(new BaseLib());g.load(new PackageLib());g.load(new TableLib());g.load(new StringLib());
        LoadState.install(g);LuaC.install(g);
        for(String k:new String[]{"io","os","luajava","debug","package","require","dofile","loadfile","load","loadstring","module","print"})g.set(k,LuaValue.NIL);
        if(file)g.load(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"@editor.lua","t",g).call();
        else g.load(source,"editor").call();
        return g;
    }
    public static final class Result {
        public final String source;
        public final boolean regenerated;
        Result(String source, boolean regenerated) { this.source=source; this.regenerated=regenerated; }
    }
    private static IllegalArgumentException unsupported(String detail) {
        return new IllegalArgumentException("无法安全合并："+detail+"。原始源码和补丁均未修改；可继续使用独立补丁。");
    }
    private static LuaValue nil(LuaValue v) { return v==null?LuaValue.NIL:v; }
    private static boolean scalar(LuaValue v) {
        return v.isnil()||v.isboolean()||v.type()==LuaValue.TSTRING||v.isnumber();
    }
    private static boolean identifier(String s) {
        return s.matches("[A-Za-z_][A-Za-z_0-9]*")&&!Arrays.asList("and","break","do","else","elseif","end","false","for","function","goto","if","in","local","nil","not","or","repeat","return","then","true","until","while").contains(s);
    }
    private static String quote(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c=='\\'||c=='\"') b.append('\\').append(c);
            else if(c<32||c==127) b.append(String.format(Locale.ROOT,"\\%03d",(int)c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
    private static String literal(LuaValue v) {
        if(v.isnil()) return "nil";
        if(v.isboolean()) return v.toboolean()?"true":"false";
        if(v.type()==LuaValue.TSTRING) {
            String s=v.tojstring();
            // Do not silently change non-UTF-8 Lua byte strings.
            if(v.raweq(LuaValue.valueOf(s))) return quote(s);
            if(v instanceof LuaString) {
                LuaString bytes=(LuaString)v;
                // The project String loader uses CESU-8 for supplementary characters.
                // Preserve the original bytes when they cannot round trip as UTF-8.
                StringBuilder escaped=new StringBuilder("\"");
                for(int i=0;i<bytes.m_length;i++)escaped.append(String.format(Locale.ROOT,"\\%03d",bytes.m_bytes[bytes.m_offset+i]&255));
                return escaped.append('"').toString();
            }
            throw unsupported("存在无法保留的字符串编码");
        }
        if(v.isnumber()) {
            double d=v.todouble();
            if(Double.isNaN(d)||Double.isInfinite(d)) throw unsupported("存在非有限数值");
            return v.tojstring();
        }
        throw unsupported("无法将 "+v.typename()+" 写回声明");
    }
    private static String index(String parent,LuaValue key) {
        return key.type()==LuaValue.TSTRING&&identifier(key.tojstring())?parent+"."+key.tojstring():parent+"["+literal(key)+"]";
    }
    private static String field(LuaValue key) {
        return key.type()==LuaValue.TSTRING&&identifier(key.tojstring())?key.tojstring():"["+literal(key)+"]";
    }
    private static List<LuaValue> keys(LuaValue table) {
        List<LuaValue> out=new ArrayList<>(Arrays.asList(table.checktable().keys()));
        for(LuaValue k:out) if(!scalar(k)||k.isnil()) throw unsupported("存在非标量 Lua 表键");
        Collections.sort(out,(a,b)->{
            if(a.type()==LuaValue.TNUMBER&&b.type()==LuaValue.TNUMBER)return Double.compare(a.todouble(),b.todouble());
            if((a.type()==LuaValue.TNUMBER)!=(b.type()==LuaValue.TNUMBER))return a.type()==LuaValue.TNUMBER?-1:1;
            if(a.type()!=b.type())return Integer.compare(a.type(),b.type());
            return a.tojstring().compareTo(b.tojstring());
        });
        return out;
    }
    private static boolean arrayKey(LuaValue k,int expected) {return k.type()==LuaValue.TNUMBER&&k.todouble()==expected;}
    private static LuaValue at(Globals g,List<LuaValue> path) {
        LuaValue v=g;
        for(LuaValue k:path) {if(!v.istable())return LuaValue.NIL;v=v.rawget(k);}
        return v;
    }
    private static List<LuaValue> child(List<LuaValue> path,LuaValue key) {List<LuaValue> p=new ArrayList<>(path);p.add(key);return p;}
    private static List<LuaValue> root(String name) {return Collections.singletonList(LuaValue.valueOf(name));}
    private static boolean prefix(List<LuaValue> a,List<LuaValue> b) {return a.size()<=b.size()&&a.equals(b.subList(0,a.size()));}

    /** Graph equality compares raw slots, metatables, aliases, cycles and Lua function
     * code/upvalue contents. The global environment itself is mapped, not recursed
     * from _G. No __index / __eq metamethod is executed by this comparison. */
    private static final class Graph {
        final IdentityHashMap<LuaValue,LuaValue> left=new IdentityHashMap<>(),right=new IdentityHashMap<>();
        Graph(Globals a,Globals b){left.put(a,b);right.put(b,a);}
        boolean eq(LuaValue a,LuaValue b) {
            a=nil(a);b=nil(b);
            if(scalar(a)||scalar(b))return scalar(a)&&scalar(b)&&a.type()==b.type()&&a.raweq(b);
            if(a.type()!=b.type())return false;
            if(left.containsKey(a)||right.containsKey(b))return left.get(a)==b&&right.get(b)==a;
            left.put(a,b);right.put(b,a);
            if(a.istable()) {
                LuaValue[] ak=a.checktable().keys(),bk=b.checktable().keys();
                if(ak.length!=bk.length)return false;
                for(LuaValue k:ak) {if(!scalar(k)||!eq(a.rawget(k),b.rawget(k)))return false;}
                return eq(a.getmetatable(),b.getmetatable());
            }
            if(a.isclosure()&&b.isclosure()) {
                LuaClosure x=a.checkclosure(),y=b.checkclosure();
                if(!proto(x.p,y.p)||x.upValues.length!=y.upValues.length)return false;
                for(int i=0;i<x.upValues.length;i++) {
                    if(x.upValues[i]==null||y.upValues[i]==null) {if(x.upValues[i]!=y.upValues[i])return false;}
                    else if(!eq(x.upValues[i].getValue(),y.upValues[i].getValue()))return false;
                }
                return true;
            }
            // Standard native library functions are installed anew in each sandbox.
            return a.isfunction()&&b.isfunction()&&a.getClass()==b.getClass();
        }
    }
    private static boolean proto(Prototype a,Prototype b) {
        if(a.numparams!=b.numparams||a.is_vararg!=b.is_vararg||a.maxstacksize!=b.maxstacksize||!Arrays.equals(a.code,b.code)||a.k.length!=b.k.length||a.p.length!=b.p.length||a.upvalues.length!=b.upvalues.length)return false;
        for(int i=0;i<a.k.length;i++)if(!scalar(a.k[i])||!a.k[i].raweq(b.k[i]))return false;
        for(int i=0;i<a.p.length;i++)if(!proto(a.p[i],b.p[i]))return false;
        for(int i=0;i<a.upvalues.length;i++)if(a.upvalues[i].idx!=b.upvalues[i].idx||a.upvalues[i].instack!=b.upvalues[i].instack)return false;
        return true;
    }
    private static boolean same(Globals a,Globals b,LuaValue x,LuaValue y) {return new Graph(a,b).eq(x,y);}
    private static boolean sameGlobals(Globals a,Globals b) {
        Graph g=new Graph(a,b);
        LuaValue[] ak=a.keys(),bk=b.keys();if(ak.length!=bk.length)return false;
        for(LuaValue k:ak)if(!scalar(k)||!g.eq(a.rawget(k),b.rawget(k)))return false;
        return g.eq(a.getmetatable(),b.getmetatable());
    }

    private static final class Token {
        final String text;final int start,end;final char kind;
        Token(String text,int start,int end,char kind){this.text=text;this.start=start;this.end=end;this.kind=kind;}
    }
    private static boolean nameChar(char c){return c>='A'&&c<='Z'||c>='a'&&c<='z'||c=='_';}
    private static boolean digit(char c){return c>='0'&&c<='9';}
    private static int longOpen(String s,int i){if(i>=s.length()||s.charAt(i)!='[')return -1;int p=i+1;while(p<s.length()&&s.charAt(p)=='=')p++;return p<s.length()&&s.charAt(p)=='['?p-i-1:-1;}
    private static int longEnd(String s,int i,int n){StringBuilder close=new StringBuilder("]");for(int j=0;j<n;j++)close.append('=');close.append(']');int p=s.indexOf(close.toString(),i+n+2);if(p<0)throw unsupported("未闭合的长字符串或注释");return p+n+2;}
    private static List<Token> lex(String s) {
        List<Token> out=new ArrayList<>();int i=0;
        while(i<s.length()) {
            char c=s.charAt(i);
            if(Character.isWhitespace(c)||c=='\ufeff'){i++;continue;}
            if(s.startsWith("--",i)) {int n=longOpen(s,i+2);if(n>=0)i=longEnd(s,i+2,n);else{while(i<s.length()&&s.charAt(i)!='\n'&&s.charAt(i)!='\r')i++;}continue;}
            int start=i;char kind='p';
            if(c=='\''||c=='"') {kind='s';i++;boolean closed=false;while(i<s.length()){char x=s.charAt(i++);if(x=='\\'){if(i<s.length())i++;}else if(x==c){closed=true;break;}}if(!closed)throw unsupported("未闭合的字符串");}
            else if(longOpen(s,i)>=0){kind='s';i=longEnd(s,i,longOpen(s,i));}
            else if(nameChar(c)){kind='n';i++;while(i<s.length()&&(nameChar(s.charAt(i))||digit(s.charAt(i))))i++;}
            else if(digit(c)||c=='.'&&i+1<s.length()&&digit(s.charAt(i+1))) {
                kind='d';i++;while(i<s.length()){char x=s.charAt(i);if(digit(x)||nameChar(x)||x=='.'&&!s.startsWith("..",i))i++;else if((x=='+'||x=='-')&&i>start&&(s.charAt(i-1)=='e'||s.charAt(i-1)=='E'||s.charAt(i-1)=='p'||s.charAt(i-1)=='P'))i++;else break;}
            }else{String op=null;for(String x:new String[]{"...","..","==","~=","<=",">=","//","<<",">>","::"})if(s.startsWith(x,i)){op=x;break;}i+=op==null?1:op.length();}
            out.add(new Token(s.substring(start,i),start,i,kind));
        }
        out.add(new Token("<eof>",s.length(),s.length(),'e'));return out;
    }
    private static final class Node {
        final int start;int end;boolean table;final LinkedHashMap<LuaValue,Node> fields=new LinkedHashMap<>();
        Node(int start){this.start=start;}
    }
    private static final class Statement {
        final String root;final List<LuaValue> path;final int start,end;final Node value;final boolean function;
        Statement(String root,List<LuaValue> path,int start,int end,Node value,boolean function){this.root=root;this.path=path;this.start=start;this.end=end;this.value=value;this.function=function;}
    }
    private static final class Document {
        final String text;final List<Token> tokens;final Globals literals;int pos;boolean patchMode;final Set<String> externalRoots=new HashSet<>();
        final List<Statement> statements=new ArrayList<>();
        final Map<List<LuaValue>,Node> locations=new LinkedHashMap<>();
        final LinkedHashSet<String> roots=new LinkedHashSet<>(),functions=new LinkedHashSet<>();
        Document(String text,Globals literals){this(text,literals,false);}
        Document(String text,Globals literals,boolean tokensOnly){this.text=text;this.literals=literals;tokens=lex(text);if(!tokensOnly)parse();}
        Token t(){return tokens.get(pos);} Token next(){return tokens.get(Math.min(pos+1,tokens.size()-1));}
        boolean is(String s){return t().kind!='s'&&t().text.equals(s);} boolean take(String s){if(is(s)){pos++;return true;}return false;}
        Token consume(){if(t().kind=='e')throw fail("意外结束");return tokens.get(pos++);}
        void need(String s){if(!take(s))throw fail("需要 "+s);}
        IllegalArgumentException fail(String s){int line=1;for(int i=0;i<t().start;i++)if(text.charAt(i)=='\n')line++;return unsupported("第 "+line+" 行 "+s);}
        String name(){if(t().kind!='n'||!identifier(t().text))throw fail("需要配置名称");return consume().text;}
        LuaValue key(){int start=t().start;if(t().kind=='s'||t().kind=='d')consume();else if(take("-")){if(t().kind!='d')throw fail("不支持动态表键");consume();}else if(is("true")||is("false"))consume();else throw fail("不支持动态表键");LuaValue key=literals.load("return "+text.substring(start,tokens.get(pos-1).end),"merge literal").call();if(!scalar(key)||key.isnil())throw fail("无效表键");return key;}
        List<LuaValue> path(){List<LuaValue> p=new ArrayList<>();p.add(LuaValue.valueOf(name()));while(true){if(take("."))p.add(LuaValue.valueOf(name()));else if(take("[")){p.add(key());need("]");}else break;}return p;}
        void register(List<LuaValue> p,Node n){Iterator<List<LuaValue>> it=locations.keySet().iterator();while(it.hasNext())if(prefix(p,it.next()))it.remove();registerTree(p,n);}
        void registerTree(List<LuaValue> p,Node n){locations.put(p,n);if(n.table)for(Map.Entry<LuaValue,Node> e:n.fields.entrySet())registerTree(child(p,e.getKey()),e.getValue());}
        void parse(){while(t().kind!='e'){
            if(take(";"))continue;int start=t().start;
            if(take("function")){
                String root=name();if(HOST_NAMES.contains(root)||!literals.rawget(root).isnil())throw fail("不支持修改内置全局 "+root);if(!is("("))throw fail("不支持成员函数声明");
                // Named function body is preserved verbatim, never evaluated during scan.
                int depth=1;while(depth>0){Token q=consume();if(q.kind!='n')continue;String s=q.text;if(s.equals("function")||s.equals("if")||s.equals("do")||s.equals("repeat"))depth++;else if(s.equals("end")||s.equals("until"))depth--;}
                if(roots.contains(root)||!functions.add(root))throw fail("函数与属性重名");
                statements.add(new Statement(root,root(root),start,tokens.get(pos-1).end,null,true));continue;
            }
            // Metatable assignments generated by the normalizer are also supported.
            if(is("setmetatable")&&next().text.equals("(")){
                consume();need("(");List<LuaValue> p=path();need(",");expr(0);need(")");String r=p.get(0).tojstring();if(!patchMode&&!roots.contains(r))throw fail("元表目标尚未声明");
                statements.add(new Statement(r,p,start,tokens.get(pos-1).end,null,false));take(";");continue;
            }
            List<LuaValue> p=path();String r=p.get(0).tojstring();
            if(HOST_NAMES.contains(r)||!literals.rawget(r).isnil())throw fail("不支持修改内置全局 "+r);
            if(functions.contains(r))throw fail("不支持重写函数");
            if(!patchMode&&p.size()>1&&!roots.contains(r))throw fail("属性根未声明："+r);
            need("=");Node n=expr(0);roots.add(r);register(p,n);
            statements.add(new Statement(r,p,start,n.end,n,false));take(";");
        }}
        int precedence(String op){switch(op){case "or":return 1;case "and":return 2;case "<":case ">":case "<=":case ">=":case "~=":case "==":return 3;case "|":return 4;case "~":return 5;case "&":return 6;case "<<":case ">>":return 7;case "..":return 8;case "+":case "-":return 9;case "*":case "/":case "//":case "%":return 10;case "^":return 12;default:return -1;}}
        Node expr(int min){Node n=atom();while(t().kind!='s'&&precedence(t().text)>=min){String op=consume().text;int p=precedence(op);Node rhs=expr(p+(op.equals("^")||op.equals("..")?0:1));Node all=new Node(n.start);all.end=rhs.end;n=all;}return n;}
        Node atom(){int start=t().start;Node n=new Node(start);
            if(take("{")){
                n.table=true;int array=1;
                while(!take("}")) {
                    LuaValue k;if(take("[")){k=key();need("]");need("=");}else if(t().kind=='n'&&identifier(t().text)&&next().text.equals("=")){k=LuaValue.valueOf(name());need("=");}else k=LuaValue.valueOf(array++);
                    Node v=expr(0);n.fields.put(k,v); // Lua constructor may contain repeated named fields; verify final value.
                    if(take(",")||take(";"))continue;need("}");break;
                }
            }else if(take("(")){expr(0);need(")");}
            else if(take("-")||take("+")||take("not")||take("#")||take("~")){expr(11);}
            else if(t().kind=='s'||t().kind=='d'||is("true")||is("false")||is("nil")){consume();}
            else if(t().kind=='n'&&identifier(t().text)){
                List<LuaValue> p=path();
                if(take("(")){
                    String f=p.toString();
                    if(!(p.size()==2&&p.get(0).tojstring().equals("table")&&p.get(1).tojstring().equals("clone"))&&!(p.size()==1&&p.get(0).tojstring().equals("getmetatable")))throw fail("不支持动态调用 "+f);
                    expr(0);need(")");
                }else {String r=p.get(0).tojstring();if(!roots.contains(r)&&!functions.contains(r)&&!externalRoots.contains(r))throw fail("不支持依赖外部环境的值 "+r);}
            }else throw fail("不支持的动态表达式");
            n.end=tokens.get(pos-1).end;return n;
        }
    }
    private static final class Edit {final int start,end;final String value;Edit(int start,int end,String value){this.start=start;this.end=end;this.value=value;}}
    private static String replace(String source,List<Edit> edits){
        edits.sort((a,b)->Integer.compare(b.start,a.start));StringBuilder b=new StringBuilder(source);int last=source.length();
        for(Edit e:edits){if(e.end>last)throw unsupported("源码定位重叠");b.replace(e.start,e.end,e.value);last=e.start;}
        return b.toString();
    }

    /** Serializes ordinary declarations, not a replay of the patch. References to
     * already-declared tables remain Lua aliases. Intra-table shared/cyclic values
     * are bound after the constructor, within the same declaration group. */
    private static final class Writer {
        final IdentityHashMap<LuaValue,String> known=new IdentityHashMap<>();
        final Set<LuaValue> ready=Collections.newSetFromMap(new IdentityHashMap<LuaValue,Boolean>());
        final List<String> fixups=new ArrayList<>(),metas=new ArrayList<>();
        final List<Map.Entry<String,LuaValue>> pendingMetas=new ArrayList<>();
        final Set<LuaValue> active=Collections.newSetFromMap(new IdentityHashMap<LuaValue,Boolean>());
        void register(LuaValue v,String path){if(scalar(v)||known.containsKey(v))return;known.put(v,path);ready.add(v);if(v.istable()){for(LuaValue k:keys(v))register(v.rawget(k),index(path,k));LuaValue m=nil(v.getmetatable());if(!m.isnil())register(m,"getmetatable("+path+")");}}
        String scalarText(LuaValue v){return literal(v);}
        String value(LuaValue v,String path,int depth) {
            if(depth>100)throw unsupported("表嵌套过深");
            if(scalar(v))return scalarText(v);
            String ref=known.get(v);
            if(ref!=null){if(ready.contains(v))return ref;fixups.add(path+" = "+ref);return "nil";}
            if(!v.istable())throw unsupported("存在无法定位的函数或对象");
            known.put(v,path);active.add(v);
            List<LuaValue> ks=keys(v);StringBuilder b=new StringBuilder("{");int array=1;boolean arrayPrefix=true;
            for(LuaValue k:ks){LuaValue child=v.rawget(k);boolean implicit=arrayPrefix&&arrayKey(k,array);if(implicit)array++;else arrayPrefix=false;
                b.append('\n').append(indent(depth+1));if(!implicit)b.append(field(k)).append(" = ");
                b.append(value(child,index(path,k),depth+1)).append(',');}
            if(!ks.isEmpty())b.append('\n').append(indent(depth));b.append('}');
            LuaValue meta=nil(v.getmetatable());if(!meta.isnil())pendingMetas.add(new AbstractMap.SimpleImmutableEntry<>(path,meta));
            return b.toString();
        }
        private void markReady(){ready.addAll(active);active.clear();}
        String declaration(String name,LuaValue v){fixups.clear();metas.clear();pendingMetas.clear();String expression=value(v,name,0);markReady();
            // All ordinary tables now have paths. Fresh metatables may refer to
            // those paths. Reject metatable graphs requiring forward fixups.
            for(int i=0;i<pendingMetas.size();i++){
                Map.Entry<String,LuaValue> m=pendingMetas.get(i);String path=m.getKey();int before=fixups.size();String value=value(m.getValue(),"getmetatable("+path+")",0);if(fixups.size()!=before)throw unsupported("复杂的元表内部共享或循环");
                metas.add("setmetatable("+path+", "+value+")");markReady();
            }
            StringBuilder b=new StringBuilder(name+" = "+expression);
            for(String s:fixups)b.append('\n').append(s);
            for(String s:metas)b.append('\n').append(s);
            return b.toString();
        }
        String standalone(LuaValue v){String out=value(v,"_merged",0);if(!fixups.isEmpty()||!pendingMetas.isEmpty())throw unsupported("该局部表存在共享引用或元表");return out;}
    }
    private static String indent(int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append("    ");return b.toString();}
    private static String scalarReplacement(String old,LuaValue v){
        if(old.trim().matches("(?i)0x[0-9a-f]+")&&v.isnumber()){double d=v.todouble();if(d>=0&&d<=4294967295L&&d==Math.floor(d))return "0x"+Long.toHexString((long)d);}
        return literal(v);
    }
    private static final class LocalRewriter {
        final Document doc;final Globals before,after;final List<Edit> edits=new ArrayList<>();
        final IdentityHashMap<LuaValue,Set<LuaValue>> visited=new IdentityHashMap<>();boolean regenerated;
        LocalRewriter(Document doc,Globals before,Globals after){this.doc=doc;this.before=before;this.after=after;}
        boolean overlap(Node n){for(Edit e:edits)if(n.start<e.end&&e.start<n.end)return true;return false;}
        void diff(List<LuaValue> p,LuaValue a,LuaValue b){
            if(same(before,after,a,b))return;
            Node n=doc.locations.get(p);
            if(a.istable()&&b.istable()){
                Set<LuaValue> seen=visited.get(a);if(seen==null){seen=Collections.newSetFromMap(new IdentityHashMap<LuaValue,Boolean>());visited.put(a,seen);}if(!seen.add(b))return;
                Set<LuaValue> ak=new HashSet<>(keys(a)),bk=new HashSet<>(keys(b));
                if(ak.equals(bk)&&same(before,after,nil(a.getmetatable()),nil(b.getmetatable()))) {for(LuaValue k:ak)diff(child(p,k),a.rawget(k),b.rawget(k));return;}
                // Structural changes: replace the literal only if it contains the
                // full table declaration (not a chain of later assignments).
                if(n!=null&&n.table&&!overlap(n)){
                    boolean complete=n.fields.keySet().equals(ak);
                    for(Map.Entry<List<LuaValue>,Node> e:doc.locations.entrySet())if(prefix(p,e.getKey())&&e.getKey().size()>p.size()&&(e.getValue().start<n.start||e.getValue().end>n.end))complete=false;
                    if(complete){try{edits.add(new Edit(n.start,n.end,new Writer().standalone(b)));regenerated=true;return;}catch(IllegalArgumentException ignored){}}
                }
                return;
            }
            if(n!=null&&!overlap(n)&&scalar(a)&&scalar(b)&&!b.isnil())edits.add(new Edit(n.start,n.end,scalarReplacement(doc.text.substring(n.start,n.end),b)));
        }
        String build(Set<String> roots){for(String r:roots)diff(root(r),before.rawget(r),after.rawget(r));return replace(doc.text,edits);}
    }
    private static String normalize(Document doc,Globals after,Set<String> rewrite,Set<String> added) {
        Writer writer=new Writer();List<Edit> edits=new ArrayList<>();Set<String> done=new HashSet<>();
        for(Statement s:doc.statements){
            if(s.function){writer.register(after.rawget(s.root),s.root);continue;}
            if(!rewrite.contains(s.root)){
                // Register only after the last statement for this root; aliases to
                // incomplete values would otherwise point to not-yet-created paths.
                boolean last=true;for(Statement q:doc.statements)if(q.start>s.start&&q.root.equals(s.root)){last=false;break;}
                if(last)writer.register(after.rawget(s.root),s.root);continue;
            }
            if(done.add(s.root)){
                LuaValue v=after.rawget(s.root);
                edits.add(new Edit(s.start,s.end,v.isnil()?"":writer.declaration(s.root,v)));
            }else edits.add(new Edit(s.start,s.end,""));
        }
        String out=replace(doc.text,edits);StringBuilder b=new StringBuilder(out);
        for(String r:added){if(b.length()>0&&b.charAt(b.length()-1)!='\n')b.append('\n');b.append(writer.declaration(r,after.rawget(r))).append('\n');}
        return b.toString();
    }
    private static LinkedHashSet<String> names(Globals g){LinkedHashSet<String> out=new LinkedHashSet<>();for(LuaValue k:g.keys()){if(k.type()!=LuaValue.TSTRING||!identifier(k.tojstring()))throw unsupported("存在非标识符的全局变量");out.add(k.tojstring());}return out;}
    private static boolean verifies(String candidate,Globals expected,Evaluator evaluator){try{new Document(candidate,evaluator.evaluate(""));return sameGlobals(expected,evaluator.evaluate(candidate));}catch(RuntimeException ex){return false;}}
    private static final Set<String> HOST_NAMES=new HashSet<>(Arrays.asList("_G","_VERSION","_ENV","service","this","activity","android","java","com","org","Http","http","file","json","loadlayout","printf","print","math","bit32","utf8","coroutine","io","os","debug","package","require","dofile","loadfile","load","loadstring","module","luajava"));
    private static void checkHost(Globals empty,Globals g){Graph graph=new Graph(empty,g);Set<String> all=new HashSet<>(names(empty));all.addAll(HOST_NAMES);for(String k:all)if(!graph.eq(empty.rawget(k),g.rawget(k)))throw unsupported("配置修改了内置 Lua 环境："+k);}
    /** Only editor-generated raw-slot deltas and declarative handwritten patches
     * are merged. This avoids silently absorbing arbitrary executable code. */
    private static void checkPatch(String patch,Globals literals,Document base){
        if(patch.trim().isEmpty())throw unsupported("没有可合并的补丁");
        String normalized=patch.replace("\r\n","\n");String header="-- Trime editor: managed sidecar; final differences from base";
        if(normalized.contains(header)){
            // Validate token structure, not just a comment marker. All calls in a
            // generated patch have fixed built-in names and scalar/ref arguments.
            Managed m=new Managed(normalized,literals,base);m.parse();
        }else {Document doc=new Document(patch,literals,true);doc.patchMode=true;doc.externalRoots.addAll(base.roots);doc.externalRoots.addAll(base.functions);doc.parse();if(!doc.functions.isEmpty())throw unsupported("手写补丁包含函数声明");} // static assignments only
    }
    private static final class Managed {
        final Document d;final Document base;final Set<String> host;
        Managed(String text,Globals literals,Document base){d=new Document(text,literals,true);this.base=base;host=names(literals);host.addAll(HOST_NAMES);}
        void rootKey(boolean existing){if(d.t().kind!='s')throw d.fail("全局补丁键必须为配置名称");LuaValue key=d.key();String name=key.tojstring();if(!identifier(name)||host.contains(name)||existing&&!base.roots.contains(name)&&!base.functions.contains(name))throw d.fail("补丁引用了非配置全局："+name);}
        void local(String name,String value){d.need("local");d.need(name);d.need("=");d.need(value);}
        void ref(){if(d.take("root"))return;d.need("r");d.need("[");if(d.t().kind!='d'||!d.t().text.matches("[1-9][0-9]*"))throw d.fail("无效的补丁引用");d.consume();d.need("]");}
        void simple(){if(d.is("root")||d.is("r"))ref();else if(d.take("nil")){}else if(d.take("true")||d.take("false")){}else if(d.take("(")){if(d.take("-")){}d.need("1");d.need("/");d.need("0");d.need(")");}else if(d.take("-")){if(d.t().kind!='d')throw d.fail("补丁值不是标量");d.consume();}else if(d.t().kind=='s'||d.t().kind=='d')d.consume();else throw d.fail("补丁值不是标量或引用");}
        void parse(){local("root","...");local("r","{");d.need("}");while(d.t().kind!='e'){
            if(d.is("r")){ref();d.need("=");if(d.take("{"))d.need("}");else if(d.take("rawget")){d.need("(");boolean root=d.is("root");ref();d.need(",");if(root)rootKey(true);else simple();d.need(")");}else if(d.take("getmetatable")){d.need("(");if(d.is("root"))throw d.fail("不支持引用配置环境元表");ref();d.need(")");}else throw d.fail("无效的补丁声明");}
            else if(d.take("rawset")){d.need("(");boolean root=d.is("root");ref();d.need(",");if(root)rootKey(false);else simple();d.need(",");simple();d.need(")");}
            else if(d.take("setmetatable")){d.need("(");if(d.is("root"))throw d.fail("不支持修改配置环境元表");ref();d.need(",");simple();d.need(")");}
            else throw d.fail("补丁包含动态语句");
        }}
    }
    public static Result merge(String base,String patch,Evaluator evaluator) {
        LuaPatchManager.verifyBase(base,patch);
        Globals empty=evaluator.evaluate("");Document doc=new Document(base,empty);checkPatch(patch,empty,doc);
        Globals before=evaluator.evaluate(base),after=evaluator.evaluate(base);LuaPatchManager.applyChecked(after,after,base,patch);
        checkHost(empty,before);checkHost(empty,after);
        LinkedHashSet<String> roots=new LinkedHashSet<>(doc.roots),added=new LinkedHashSet<>(names(after));added.removeAll(names(empty));added.removeAll(roots);added.removeAll(doc.functions);
        for(String r:added)if(after.rawget(r).isfunction())throw unsupported("新增动态函数 "+r);
        for(String r:doc.functions)if(!same(before,after,before.rawget(r),after.rawget(r)))throw unsupported("函数定义发生变化："+r);
        if(sameGlobals(before,after))return new Result(base,false);
        LocalRewriter rewriter=new LocalRewriter(doc,before,after);
        String local=rewriter.build(roots);
        if(added.isEmpty()&&verifies(local,after,evaluator))return new Result(local,rewriter.regenerated);
        // Preserve original expressions/comments whenever possible. A local edit
        // to key.* may change independent table.clone() consumers when the base
        // is re-run; rewrite only those dependent declarations as necessary.
        try {
            String candidate=local;
            for(int pass=0;pass<4;pass++) {
                Document current=new Document(candidate,empty);Globals currentValues=evaluator.evaluate(candidate);
                LinkedHashSet<String> changed=new LinkedHashSet<>();
                for(String r:current.roots)if(!same(currentValues,after,currentValues.rawget(r),after.rawget(r)))changed.add(r);
                LinkedHashSet<String> missing=new LinkedHashSet<>(added);missing.removeAll(current.roots);
                if(changed.isEmpty()&&missing.isEmpty())break;
                String next=normalize(current,after,changed,missing);
                if(verifies(next,after,evaluator))return new Result(next,true);
                if(next.equals(candidate))break;candidate=next;
            }
        }catch(RuntimeException ignored){}
        LinkedHashSet<String> changed=new LinkedHashSet<>();for(String r:roots)if(!same(before,after,before.rawget(r),after.rawget(r)))changed.add(r);
        try{String partial=normalize(doc,after,changed,added);if(verifies(partial,after,evaluator))return new Result(partial,true);}catch(RuntimeException ignored){}
        // Alias-only changes may not appear in per-root equality. Rebuild table
        // declarations as a last resort but preserve unchanged scalar expressions.
        LinkedHashSet<String> tables=new LinkedHashSet<>(changed);for(String r:roots)if(before.rawget(r).istable()||after.rawget(r).istable())tables.add(r);
        try{String all=normalize(doc,after,tables,added);if(verifies(all,after,evaluator))return new Result(all,true);}catch(RuntimeException ignored){}
        throw unsupported("合并后的值、函数或共享关系与补丁生效结果不一致");
    }
    public static void verifyEquivalent(String base,String patch,String candidate,Evaluator evaluator){
        Globals empty=evaluator.evaluate("");Document doc=new Document(base,empty);checkPatch(patch,empty,doc);new Document(candidate,empty);
        Globals expected=evaluator.evaluate(base);LuaPatchManager.applyChecked(expected,expected,base,patch);checkHost(empty,expected);
        Globals actual=evaluator.evaluate(candidate);checkHost(empty,actual);
        if(!sameGlobals(expected,actual))throw unsupported("编辑后的合并结果与当前生效配置不一致");
    }

    private static String read(File f)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)b.write(data,0,n);return new String(b.toByteArray(),StandardCharsets.UTF_8);}}
    private static void write(File f,String text)throws IOException{try(FileOutputStream out=new FileOutputStream(f)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
    /** Check disk versions again after preview. Both originals are backed up before
     * replacing anything; the active sidecar is restored if the base rename fails. */
    public static synchronized File commit(File base,String expectedBase,String expectedDiskPatch,String usedPatch,String merged)throws IOException{
        if(!read(base).equals(expectedBase)||!LuaPatchManager.read(base).equals(expectedDiskPatch))throw new IOException("源码或补丁已被其他程序修改，请重新打开后合并");
        File parent=base.getParentFile(),side=LuaPatchManager.file(base);
        File backup=File.createTempFile(base.getName()+"-merge-", ".editor.bak",parent);
        if(!backup.delete()||!backup.mkdir())throw new IOException("无法创建合并备份目录");
        File tmp=null,parked=new File(backup,"active-patch.bak");boolean moved=false,done=false;
        try{
            write(new File(backup,"original-source.bak"),expectedBase);
            if(side.exists())write(new File(backup,"original-patch.bak"),expectedDiskPatch);
            if(!usedPatch.equals(expectedDiskPatch)||!side.exists())write(new File(backup,"merged-patch.bak"),usedPatch);
            tmp=File.createTempFile("editor-merge-", ".tmp",parent);write(tmp,merged);
            if(!read(base).equals(expectedBase)||!LuaPatchManager.read(base).equals(expectedDiskPatch))throw new IOException("文件已变化，未执行合并");
            if(side.exists()){if(!side.renameTo(parked))throw new IOException("无法停用原补丁");moved=true;}
            if(!tmp.renameTo(base))throw new IOException("无法替换原始源码");
            done=true;return backup;
        }finally{
            if(tmp!=null)tmp.delete();
            if(moved&&!done&&!parked.renameTo(side))throw new IOException("恢复补丁失败，原始备份位于："+backup.getAbsolutePath());
        }
    }
}
