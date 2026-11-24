package com.myopicmobile.textwarrior.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Tolerant YAML syntax highlighting, not a YAML validator. Spans store UTF-16 lengths. */
public final class YamlLexer {
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:0[xX][0-9a-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+|(?:[0-9][0-9_]*(?:\\.[0-9_]*)?|\\.[0-9_]+)(?:[eE][+-]?[0-9]+)?|\\.(?:inf|Inf|INF|nan|NaN|NAN))");
    private YamlLexer() {}
    private static boolean space(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
    private static boolean flow(char c) { return c == '[' || c == ']' || c == '{' || c == '}' || c == ','; }
    private static boolean sep(String s, int i, int end, int depth) {
        return i >= end || space(s.charAt(i)) || (depth > 0 && flow(s.charAt(i)));
    }
    private static void add(List<Pair> a, int n, int kind) {
        if (n <= 0) return;
        if (!a.isEmpty() && a.get(a.size()-1).getSecond() == kind) {
            Pair p = a.get(a.size()-1); p.setFirst(p.getFirst()+n);
        } else a.add(new Pair(n,kind));
    }
    private static int scalar(String s) {
        if (s.equals("~") || s.equalsIgnoreCase("null") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false") ||
                s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("no") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("off") || NUMBER.matcher(s).matches()) return Lexer.LITERAL;
        return Lexer.STRING;
    }
    /** Includes one normal span unit for TextWarrior's EOF sentinel. */
    public static List<Pair> scan(String text) {
        List<Pair> out = new ArrayList<>();
        int pos=0, depth=0, blockParent=-1, blockIndent=-1;
        char quote=0;
        while(pos<text.length()) {
            if(Thread.currentThread().isInterrupted()) return null;
            int end=text.indexOf('\n',pos); if(end<0)end=text.length();
            int limit=end; if(limit>pos && text.charAt(limit-1)=='\r')limit--;
            int first=pos; while(first<limit && text.charAt(first)==' ')first++;
            int indent=first-pos;
            boolean blank=first==limit;
            // UTF-8 BOM is not part of the first key or directive.
            if(pos==0 && first==0 && limit>0 && text.charAt(0)=='\ufeff') {
                add(out,1,Lexer.NORMAL);pos++;first++;indent=0;blank=first==limit;
            }
            if(blockParent>=0) {
                if(blank || (blockIndent>=0 ? indent>=blockIndent : indent>blockParent)) {
                    if(!blank && blockIndent<0)blockIndent=indent;
                    add(out,limit-pos,Lexer.STRING);add(out,end-limit+(end<text.length()?1:0),Lexer.NORMAL);
                    pos=end+(end<text.length()?1:0);continue;
                }
                blockParent=blockIndent=-1;
            }
            int i=pos;
            boolean quotedContinuation=quote!=0;
            if(!quotedContinuation) {add(out,first-pos,Lexer.NORMAL);i=first;}
            boolean valueStart=true;
            int structuralIndent=indent;
            while(i<limit) {
                if(Thread.currentThread().isInterrupted())return null;
                int start=i;char c=text.charAt(i);
                if(quote!=0 || (valueStart && (c=='\'' || c=='"'))) {
                    if(quote==0){quote=c;i++;}
                    while(i<limit) {
                        if((i&4095)==0 && Thread.currentThread().isInterrupted())return null;
                        char q=text.charAt(i++);
                        if(quote=='"' && q=='\\' && i<limit){i++;continue;}
                        if(q==quote){if(quote=='\'' && i<limit && text.charAt(i)=='\''){i++;continue;}quote=0;break;}
                    }
                    int j=i;while(j<limit&&space(text.charAt(j)))j++;
                    boolean key=quote==0&&j<limit&&text.charAt(j)==':'&&(depth>0||sep(text,j+1,limit,depth));
                    if(key) structuralIndent=start-pos;
                    add(out,i-start,key?Lexer.NAME:Lexer.STRING);valueStart=false;continue;
                }
                if(space(c)){i++;add(out,1,Lexer.NORMAL);continue;}
                if(c=='#' && (i==pos||space(text.charAt(i-1)))){add(out,limit-i,Lexer.COMMENT);i=limit;continue;}
                if(i==first && indent==0 && (c=='%' || ((text.startsWith("---",i)||text.startsWith("...",i))&&sep(text,i+3,limit,0)))) {
                    if(c=='%'){int j=i;while(j<limit&&!(text.charAt(j)=='#'&&j>i&&space(text.charAt(j-1))))j++;add(out,j-i,Lexer.OPERATOR);i=j;}
                    else {add(out,3,Lexer.OPERATOR);i+=3;depth=0;}
                    continue;
                }
                if(valueStart && depth==0 && (c=='|'||c=='>')) {
                    int j=i+1, explicit=0;while(j<limit && (text.charAt(j)=='+'||text.charAt(j)=='-'||(text.charAt(j)>='1'&&text.charAt(j)<='9'))){char k=text.charAt(j++);if(k>='1'&&k<='9')explicit=k-'0';}
                    if(sep(text,j,limit,0)){blockParent=structuralIndent;blockIndent=explicit==0?-1:structuralIndent+explicit;add(out,j-i,Lexer.OPERATOR);i=j;valueStart=false;continue;}
                }
                if(valueStart && (c=='&'||c=='*'||c=='!')) {
                    i++;if(c=='!'&&i<limit&&text.charAt(i)=='<'){i++;while(i<limit&&text.charAt(i)!='>')i++;if(i<limit)i++;}
                    else while(i<limit&&!space(text.charAt(i))&&!flow(text.charAt(i))&&
                            !(text.charAt(i)==':'&&sep(text,i+1,limit,depth)))i++;
                    add(out,i-start,Lexer.GLOBAL);continue;
                }
                if((valueStart&&(c=='['||c=='{'))||(depth>0&&flow(c))) {
                    if(c=='['||c=='{')depth++;else if(c==']'||c=='}')depth=Math.max(0,depth-1);
                    add(out,1,Lexer.OPERATOR);i++;valueStart=c=='['||c=='{'||c==',';continue;
                }
                if(c==':'&&(sep(text,i+1,limit,depth)||depth>0&&!valueStart)) {add(out,1,Lexer.OPERATOR);i++;valueStart=true;continue;}
                if(valueStart&&(c=='-'||c=='?')&&sep(text,i+1,limit,depth)) {
                    if(c=='-')structuralIndent=i-pos;
                    add(out,1,Lexer.OPERATOR);i++;continue;
                }
                // Plain scalars may contain spaces, colons in URLs, and unseparated #.
                int j=i+1;
                while(j<limit) {
                    if((j&4095)==0 && Thread.currentThread().isInterrupted())return null;
                    char k=text.charAt(j);
                    if(k==':'&&sep(text,j+1,limit,depth))break;
                    if(k=='#'&&space(text.charAt(j-1)))break;
                    if(depth>0&&flow(k))break;
                    j++;
                }
                int tail=j;while(tail>i&&space(text.charAt(tail-1)))tail--;
                boolean key=j<limit&&text.charAt(j)==':'&&sep(text,j+1,limit,depth);
                if(key) structuralIndent=i-pos;
                add(out,tail-i,key?Lexer.NAME:scalar(text.substring(i,tail)));add(out,j-tail,Lexer.NORMAL);
                i=j;valueStart=false;
            }
            add(out,end-limit+(end<text.length()?1:0),Lexer.NORMAL);
            pos=end+(end<text.length()?1:0);
        }
        add(out,1,Lexer.NORMAL);
        return out;
    }
}
