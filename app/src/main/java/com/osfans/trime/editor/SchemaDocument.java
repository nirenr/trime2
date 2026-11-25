package com.osfans.trime.editor;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import com.osfans.trime.core.SchemaItem;

/** Conservative source-span reader. Never serializes the complete YAML document.
 * Unsupported YAML constructs are opaque; only recognized spans can be changed. */
public final class SchemaDocument {
    enum Kind { MAP, LIST, STRING, BOOL, INT, NUMBER, NULL, EMPTY, OPAQUE }
    static final class Node {
        final String key; final Node parent; final int indent, start;
        int end, valueStart, valueEnd, bodyStart=-1, bodyEnd=-1; boolean flow; Kind kind; String value;
        final List<Node> children = new ArrayList<>();
        Node(String key, Node parent, int indent, int start) {this.key=key;this.parent=parent;this.indent=indent;this.start=start;}
        String path(){return parent==null?"":(parent.parent==null?key:parent.path()+"/"+key);}
        Node child(String key){for(Node n:children)if(n.key.equals(key))return n;return null;}
        boolean scalar(){return kind==Kind.STRING||kind==Kind.BOOL||kind==Kind.INT||kind==Kind.NUMBER||kind==Kind.EMPTY||kind==Kind.NULL;}
    }
    final String source, newline; final Node root;
    private final List<Line> lines=new ArrayList<>(); private int cursor;
    private static final class Line {
        int start,end,contentEnd,indent; String code;
        boolean skip(){return code.trim().isEmpty();}
    }
    SchemaDocument(String source){
        this.source=source;newline=source.contains("\r\n")?"\r\n":"\n";
        for(int pos=0;pos<source.length();){int e=source.indexOf('\n',pos);if(e<0)e=source.length();Line l=new Line();l.start=pos==0&&source.charAt(0)=='\uFEFF'?1:pos;l.end=e<source.length()?e+1:e;l.contentEnd=e>pos&&source.charAt(e-1)=='\r'?e-1:e;String raw=source.substring(l.start,l.contentEnd);int off=0;while(off<raw.length()&&raw.charAt(off)==' ')off++;if(off<raw.length()&&raw.charAt(off)=='\t')throw new IllegalArgumentException("YAML 缩进含 Tab，请使用源码模式");l.indent=off;l.code=raw.substring(off,comment(raw,off)).trim();lines.add(l);pos=l.end;}
        root=new Node("",null,-1,0);root.kind=Kind.MAP;root.end=source.length();
        skip();if(cursor<lines.size()&&lines.get(cursor).code.equals("---")){cursor++;skip();}
        if(cursor<lines.size()&&!lines.get(cursor).code.equals("..."))parseBlock(root,0,false,0);
        skip();if(cursor<lines.size()&&lines.get(cursor).code.equals("...")){root.end=lines.get(cursor++).start;skip();}
        if(cursor<lines.size())throw new IllegalArgumentException("暂不支持多文档或复杂根节点，请使用源码模式");
    }
    private void skip(){while(cursor<lines.size()&&lines.get(cursor).skip())cursor++;}
    private static boolean dash(String s){return s.equals("-")||s.startsWith("- ");}
    private static int comment(String s,int from){char q=0;boolean escape=false;for(int i=from;i<s.length();i++){char c=s.charAt(i);if(q=='"'&&escape){escape=false;continue;}if(q=='"'&&c=='\\'){escape=true;continue;}if(q!=0){if(c==q){if(q=='\''&&i+1<s.length()&&s.charAt(i+1)==q){i++;continue;}q=0;}continue;}if((c=='\''||c=='"')&&(i==from||" :,[{-\t".indexOf(s.charAt(i-1))>=0)){q=c;continue;}if(c=='#'&&(i==from||Character.isWhitespace(s.charAt(i-1))))return i;}return s.length();}
    private static int colon(String s){char q=0;int nesting=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(q!=0){if(q=='"'&&c=='\\'){i++;continue;}if(c==q){if(q=='\''&&i+1<s.length()&&s.charAt(i+1)==q){i++;continue;}q=0;}continue;}if(c=='\''||c=='"'){q=c;continue;}if(c=='['||c=='{')nesting++;else if(c==']'||c=='}')nesting--;else if(c==':'&&nesting==0&&(i+1==s.length()||Character.isWhitespace(s.charAt(i+1))))return i;}return -1;}
    private void parseBlock(Node parent,int indent,boolean sequence,int depth){
        if(depth>48)throw new IllegalArgumentException("配置嵌套过深");parent.kind=sequence?Kind.LIST:Kind.MAP;
        while(true){skip();if(cursor>=lines.size())return;Line l=lines.get(cursor);if(l.indent<indent||l.code.equals("...")||l.code.equals("---"))return;if(l.indent!=indent)throw new IllegalArgumentException("无法识别第 "+(cursor+1)+" 行缩进，请使用源码模式");if(dash(l.code)!=sequence)return;cursor++;
            if(sequence){int at=l.start+indent+1;while(at<l.contentEnd&&source.charAt(at)==' ')at++;String s=l.code.substring(1).trim();Node n=new Node("@"+parent.children.size(),parent,indent,l.start);parent.children.add(n);n.end=l.end;
                if(colon(s)>=0&&!s.startsWith("{")&&!s.startsWith("[")){n.kind=Kind.MAP;parsePair(n,l,s,at,indent+2,depth+1);skip();if(cursor<lines.size()&&lines.get(cursor).indent>indent){parseBlock(n,lines.get(cursor).indent,false,depth+1);}for(Node ch:n.children)n.end=Math.max(n.end,ch.end);}
                else parseValue(n,l,s,at,depth+1);
                parent.end=Math.max(parent.end,n.end);
            }else{int c=colon(l.code);if(c<0)throw new IllegalArgumentException("无法识别第 "+cursor+" 行映射，请使用源码模式");Node n=parsePair(parent,l,l.code,l.start+indent,indent,depth+1);parent.end=Math.max(parent.end,n.end);}
        }
    }
    private Node parsePair(Node parent,Line l,String code,int at,int indent,int depth){int c=colon(code);if(c<0)throw new IllegalArgumentException("不支持的映射");String k=decode(code.substring(0,c).trim());if(k.isEmpty()||k.startsWith("?")||k.startsWith("&")||k.startsWith("!")||k.startsWith("[")||k.startsWith("{"))throw new IllegalArgumentException("复杂映射键，请使用源码模式");if(parent.child(k)!=null)throw new IllegalArgumentException("重复键："+k+"，请使用源码模式");Node n=new Node(k,parent,indent,l.start);parent.children.add(n);n.end=l.end;int v=at+c+1;while(v<l.contentEnd&&source.charAt(v)==' ')v++;parseValue(n,l,code.substring(c+1).trim(),v,depth);return n;}
    private void parseValue(Node n,Line l,String s,int at,int depth){
        n.valueStart=at;n.valueEnd=at+s.length();n.value=s;n.kind=kind(s);
        if(s.matches("\\|[-+]?")){literal(n,l,s);return;}
        skip();Line next=cursor<lines.size()?lines.get(cursor):null;
        if(s.isEmpty()&&next!=null&&(next.indent>n.indent||(next.indent==n.indent&&dash(next.code)&&n.parent.kind==Kind.MAP))){parseBlock(n,next.indent,dash(next.code),depth);return;}
        if(n.kind==Kind.OPAQUE||(!s.isEmpty()&&next!=null&&next.indent>n.indent)){if(next!=null&&next.indent>n.indent){n.kind=Kind.OPAQUE;while(cursor<lines.size()){Line x=lines.get(cursor);if(!x.skip()&&x.indent<=n.indent)break;n.end=x.end;cursor++;}}}
        if(n.kind==Kind.OPAQUE && (s.startsWith("[")||s.startsWith("{")) && n.end==l.end){try{Flow f=new Flow(at,at+s.length());f.read(n,0);f.ws();if(f.pos!=f.limit)throw new IllegalArgumentException();n.end=l.end;}catch(IllegalArgumentException e){n.children.clear();n.kind=Kind.OPAQUE;n.flow=false;n.value=s;n.valueStart=at;n.valueEnd=at+s.length();n.end=l.end;}}
        if(n.kind==Kind.STRING)n.value=decode(s);
    }
    private void literal(Node n,Line header,String indicator){
        n.bodyStart=header.end;n.bodyEnd=header.end;int contentIndent=-1,lastContent=header.end;StringBuilder b=new StringBuilder();
        while(cursor<lines.size()){Line x=lines.get(cursor);String raw=source.substring(x.start,x.contentEnd);boolean blank=raw.trim().isEmpty();if(!blank&&x.indent<=n.indent)break;if(!blank&&contentIndent<0)contentIndent=x.indent;if(!blank&&x.indent<contentIndent){n.kind=Kind.OPAQUE;while(cursor<lines.size()){Line y=lines.get(cursor);if(!y.skip()&&y.indent<=n.indent)break;n.end=y.end;cursor++;}return;}if(!blank){b.append(raw.substring(contentIndent));lastContent=x.end;}else if(contentIndent>=0&&raw.length()>contentIndent)b.append(raw.substring(contentIndent));if(x.end>x.contentEnd&&source.charAt(x.end-1)=='\n')b.append('\n');n.bodyEnd=x.end;n.end=x.end;cursor++;}
        String value=b.toString();if(!indicator.endsWith("+")){int end=value.length();while(end>0&&value.charAt(end-1)=='\n')end--;boolean hadNewline=end<value.length();value=value.substring(0,end);if(!indicator.endsWith("-")&&hadNewline&&end>0)value+="\n";}if(!indicator.endsWith("+"))n.bodyEnd=lastContent;n.value=value;n.kind=Kind.STRING;
    }
    private final class Flow {
        int pos; final int limit;
        Flow(int pos,int limit){this.pos=pos;this.limit=limit;}
        void ws(){while(pos<limit&&Character.isWhitespace(source.charAt(pos)))pos++;}
        char peek(){ws();return pos<limit?source.charAt(pos):0;}
        void expect(char c){if(peek()!=c)throw new IllegalArgumentException();pos++;}
        String token(boolean key){ws();int begin=pos;char q=0;while(pos<limit){char c=source.charAt(pos);if(q!=0){pos++;if(q=='"'&&c=='\\'){if(pos<limit)pos++;continue;}if(c==q){if(q=='\''&&pos<limit&&source.charAt(pos)==q){pos++;continue;}q=0;}continue;}if((c=='"'||c=='\'')&&pos==begin){q=c;pos++;continue;}if(c==','||c==']'||c=='}'||(key&&c==':'))break;if(c=='['||c=='{'||c=='#')throw new IllegalArgumentException();pos++;}if(q!=0)throw new IllegalArgumentException();return source.substring(begin,pos).trim();}
        void read(Node n,int depth){if(depth>48)throw new IllegalArgumentException();ws();int start=pos;n.flow=true;n.valueStart=start;char c=peek();if(c=='['||c=='{'){pos++;n.kind=c=='['?Kind.LIST:Kind.MAP;char close=c=='['?']':'}';while(peek()!=close){if(peek()==0)throw new IllegalArgumentException();int childStart=pos;String key="@"+n.children.size();if(c=='{'){key=decode(token(true));if(key.isEmpty()||n.child(key)!=null)throw new IllegalArgumentException();expect(':');}Node child=new Node(key,n,n.indent,childStart);read(child,depth+1);n.children.add(child);if(peek()==close)break;expect(',');}expect(close);n.valueEnd=pos;n.value=source.substring(start,pos);}
            else{String v=token(false);n.kind=kind(v);if(v.isEmpty()||n.kind==Kind.OPAQUE)throw new IllegalArgumentException();n.valueEnd=start+v.length();n.value=n.kind==Kind.STRING?decode(v):v;}n.end=pos;
        }
    }
    static Kind kind(String s){if(s.isEmpty())return Kind.EMPTY;if(s.startsWith("[")||s.startsWith("{")||s.startsWith("|")||s.startsWith(">")||s.startsWith("&")||s.startsWith("*")||s.startsWith("!")||s.startsWith("%"))return Kind.OPAQUE;if(s.matches("(?i:true|false)"))return Kind.BOOL;if(s.matches("[-+]?[0-9]+"))return Kind.INT;if(s.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?"))return Kind.NUMBER;if(s.matches("(?i:null|~)"))return Kind.NULL;if(s.matches("(?i:yes|no|on|off|\\.nan|[+-]?\\.inf)")||s.matches("0[xob].*"))return Kind.OPAQUE;if(s.startsWith("\"")||s.startsWith("'")){if(s.length()<2||s.charAt(s.length()-1)!=s.charAt(0))return Kind.OPAQUE;try{decode(s);}catch(IllegalArgumentException e){return Kind.OPAQUE;}}return Kind.STRING;}
    static String decode(String s){if(s.length()<2)return s;char q=s.charAt(0);if(q=='\''&&s.endsWith("'"))return s.substring(1,s.length()-1).replace("''","'");if(q!='"'||!s.endsWith("\""))return s;StringBuilder b=new StringBuilder();for(int i=1;i<s.length()-1;i++){char c=s.charAt(i);if(c!='\\'){b.append(c);continue;}if(++i>=s.length()-1)throw new IllegalArgumentException("复杂转义");c=s.charAt(i);switch(c){case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;case '"':case '\\':case '/':b.append(c);break;default:throw new IllegalArgumentException("复杂转义");}}return b.toString();}
    static String encode(String value,Kind type){if(type==Kind.NULL)return "null";if(type==Kind.MAP)return "{}";if(type==Kind.LIST)return "[]";if(type==Kind.BOOL){if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("请选择 true 或 false");return value;}if(type==Kind.INT){if(!value.matches("[-+]?[0-9]+"))throw new IllegalArgumentException("请输入整数");Integer.parseInt(value);return value;}if(type==Kind.NUMBER){if(!value.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?")||!Double.isFinite(Double.parseDouble(value)))throw new IllegalArgumentException("请输入有效数值");return value;}for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<32&&c!='\n'&&c!='\r'&&c!='\t')throw new IllegalArgumentException("包含不可写入的控制字符");}return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    String replace(Node n,String value,Kind type){if(!n.scalar())throw new IllegalArgumentException("该结构请使用源码模式");String encoded=encode(value,type);if(n.valueStart==n.valueEnd&&n.valueStart>0&&!Character.isWhitespace(source.charAt(n.valueStart-1)))encoded=" "+encoded; if(n.valueStart==n.valueEnd&&n.valueStart<source.length()&&source.charAt(n.valueStart)=='#')encoded+=" ";if(n.bodyStart>=0&&n.bodyEnd>=n.bodyStart)return source.substring(0,n.valueStart)+encoded+source.substring(n.valueEnd,n.bodyStart)+source.substring(n.bodyEnd);return splice(n.valueStart,n.valueEnd,encoded);}
    String remove(Node n){if(n.parent!=null&&n.parent.flow&&n.flow){List<Node> siblings=n.parent.children;int i=siblings.indexOf(n);int a=n.start,b=n.end;if(i+1<siblings.size())b=siblings.get(i+1).start;else if(i>0)a=siblings.get(i-1).end;else {int j=b;while(j<n.parent.valueEnd-1&&Character.isWhitespace(source.charAt(j)))j++;if(j<n.parent.valueEnd-1&&source.charAt(j)==',')b=j+1;}return splice(a,b,"");}if(n.parent==null||n.kind==Kind.OPAQUE||(n.start!=lineStart(n.start)&&!(n.start==1&&source.charAt(0)=='\uFEFF'))||isInlineMap(n)||(n.flow&&n.parent.flow))throw new IllegalArgumentException("该节点请使用源码模式删除");if(n.parent!=root&&n.parent.children.size()==1&&!n.parent.flow){Node p=n.parent;String empty=p.kind==Kind.LIST?"[]":"{}";String first=source.substring(0,p.valueStart);if(p.valueStart>0&&!Character.isWhitespace(source.charAt(p.valueStart-1)))empty=" "+empty;if(p.valueStart<source.length()&&source.charAt(p.valueStart)=='#')empty+=" ";return first+empty+source.substring(p.valueStart,n.start)+source.substring(n.end);}return splice(n.start,n.end,"");}
    private boolean isInlineMap(Node n){return n.parent!=null&&n.parent.parent!=null&&n.parent.parent.kind==Kind.LIST&&n.start==n.parent.start;}
    private int lineStart(int at){return source.lastIndexOf('\n',Math.max(0,at-1))+1;}
    String add(Node parent,String key,String value,Kind type){if(parent.kind!=Kind.MAP&&parent.kind!=Kind.LIST)throw new IllegalArgumentException("请选择容器");if(parent.kind==Kind.MAP&&(key.trim().isEmpty()||parent.child(key)!=null))throw new IllegalArgumentException("名称不能为空或重复");if(parent.flow){int at=parent.valueEnd-1;int last=at-1;while(last>=parent.valueStart&&Character.isWhitespace(source.charAt(last)))last--;String prefix=parent.children.isEmpty()||source.charAt(last)==','?"":", ";return splice(at,at,prefix+(parent.kind==Kind.MAP?encode(key,Kind.STRING)+": ":"")+encode(value,type));}int indent=parent.children.isEmpty()?parent.indent+2:parent.children.get(0).indent;if(parent==root)indent=0;String text=spaces(indent)+(parent.kind==Kind.LIST?"- ":encode(key,Kind.STRING)+": ")+encode(value,type)+newline;int at=parent.end;if(parent==root&&parent.children.isEmpty())at=root.end;if(at>0&&source.charAt(at-1)!='\n')text=newline+text;return splice(at,at,text);}
    String move(Node n,int delta){Node parent=n.parent;if(parent==null||parent.kind!=Kind.LIST)throw new IllegalArgumentException("仅列表支持调整顺序");int i=parent.children.indexOf(n),j=i+delta;if(j<0||j>=parent.children.size())return source;Node a=parent.children.get(Math.min(i,j)),b=parent.children.get(Math.max(i,j));if(a.kind==Kind.OPAQUE||b.kind==Kind.OPAQUE)throw new IllegalArgumentException("复杂项请在源码调整");if(a.end>b.start)throw new IllegalArgumentException("无法识别列表范围");String first=source.substring(a.start,a.end),second=source.substring(b.start,b.end),gap=source.substring(a.end,b.start);if(!parent.flow&&(!first.endsWith("\n")||!second.endsWith("\n")))throw new IllegalArgumentException("请先在文件末尾添加换行");return splice(a.start,b.end,second+gap+first);}
    private static String spaces(int n){char[] c=new char[Math.max(0,n)];Arrays.fill(c,' ');return new String(c);}
    private String splice(int a,int b,String s){return source.substring(0,a)+s+source.substring(b);}
    List<Node> all(){List<Node> out=new ArrayList<>();walk(root,out);return out;}
    private static void walk(Node n,List<Node> out){for(Node x:n.children){out.add(x);walk(x,out);}}

    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final int MAX_SECTION_CHARS = 256 * 1024;

    private static String scalar(File file, String section, String field) {
        if (!file.isFile() || file.length() > MAX_FILE_BYTES) return "";
        Pattern header = Pattern.compile("^(?:" + Pattern.quote(section) + "|'" + Pattern.quote(section)
                + "'|\"" + Pattern.quote(section) + "\")[ ]*:(?:[ ]+.*|$)");
        StringBuilder block = new StringBuilder();
        boolean found = false, first = true;
        int scanned = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) return "";
                scanned += line.length() + 1;
                if (scanned > MAX_FILE_BYTES) return "";
                if (first && line.startsWith("\uFEFF")) line = line.substring(1);
                first = false;
                if (!found) {
                    if (!header.matcher(line).matches()) continue;
                    found = true;
                } else if (!line.trim().isEmpty() && !line.trim().startsWith("#")
                        && !Character.isWhitespace(line.charAt(0))) {
                    break;
                }
                if (block.length() + line.length() + 1 > MAX_SECTION_CHARS) return "";
                block.append(line).append('\n');
            }
            if (!found) return "";
            SchemaDocument.Node parent = new SchemaDocument(block.toString()).root.child(section);
            SchemaDocument.Node node = parent == null ? null : parent.child(field);
            if (node == null || !node.scalar() || node.kind == SchemaDocument.Kind.NULL || node.value == null) return "";
            return node.value.trim();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static boolean validId(String id) {
        return id != null && !id.trim().isEmpty() && id.length() <= 255
                && !".default".equals(id) && !".".equals(id) && !"..".equals(id)
                && id.indexOf('/') < 0 && id.indexOf('\\') < 0 && id.indexOf('\0') < 0;
    }

    private static File[] candidates(File user, File shared, String id) {
        String file = id + ".schema.yaml";
        return new File[]{new File(user, "build/" + file), new File(shared, "build/" + file),
                new File(user, file), new File(shared, file)};
    }

    private static String name(File user, File shared, String id) {
        for (File file : candidates(user, shared, id)) {
            String value = scalar(file, "schema", "name");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    /** A hint for the launcher, not proof that the engine has selected the schema. */
    public static SchemaItem lastUsed(File user, File shared, String savedId) {
        String recent = scalar(new File(user, "user.yaml"), "var", "previously_selected_schema");
        if (validId(recent)) return new SchemaItem(recent, name(user, shared, recent));
        if (validId(savedId)) {
            // select_schema_id is global in older versions. Only use it when a matching
            // configuration is available in this group or the shared data directory.
            for (File file : candidates(user, shared, savedId)) {
                if (file.isFile()) return new SchemaItem(savedId, name(user, shared, savedId));
            }
        }
        return null;
    }
}
