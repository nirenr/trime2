package com.osfans.trime.editor;

import java.util.*;

/** Stable semantic ordering; unknown fields sort after known fields at each level. */
final class PaletteOrder {
    private static final List<String> GROUPS=Arrays.asList("基本信息","keyboard","key","functional","space","enter","enter2","popup","toolbar","candidate","preedit","composition","symbol","clipboard");
    private static final List<String> FIELDS=Arrays.asList("name","author","background","text_color","text_size","font","corner_radius","round_corner","stroke_color","stroke_width","border","elevation","shadow_color","gravity","offset_x","offset_y","width","height","min_width","max_width","min_height","max_height","padding","margins","left","top","right","bottom","show","scale_x","scale_y","translation_x","translation_y","translation_z","pressed","mask","preview","long_click","hint","up","down","position","movable","spacing","line_spacing","line_spacing_multiplier","min_length","max_length","max_entries","cloud_max_entries","sticky_lines","all_phrases","use_cursor","inline","column_count","indicator_color","schema_switches","text","key","hide","comment","expanded","filter_bar","tab_bar","tool_bar","item","window","keys","long_click_time","repeat_click_time","sound_enabled","sound_effect","vibration_enabled","vibration_effect");
    private static final Map<String,Integer> GROUP_RANK=ranks(GROUPS), FIELD_RANK=ranks(FIELDS);
    private static final Map<String,Integer> HINT_RANK=ranks(Arrays.asList("show","text_color","pressed","text_size","font","gravity","offset_x","offset_y","up","down","left","right"));
    private static final Map<String,Integer> EDGE_RANK=ranks(Arrays.asList("left","top","right","bottom"));
    private static Map<String,Integer> ranks(List<String> list){Map<String,Integer> out=new HashMap<>();for(int i=0;i<list.size();i++)out.put(list.get(i),i);return out;}
    private static int rank(Map<String,Integer> map,String s){Integer i=map.get(s);return i==null?Integer.MAX_VALUE:i;}
    private static boolean digits(String s){if(s.isEmpty())return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c<'0'||c>'9')return false;}return true;}
    static int groups(String a,String b){int c=Integer.compare(rank(GROUP_RANK,a),rank(GROUP_RANK,b));return c!=0?c:a.compareTo(b);}
    private static int segment(String a,String b,String parent){
        Map<String,Integer> special="hint".equals(parent)?HINT_RANK:("padding".equals(parent)||"margins".equals(parent))?EDGE_RANK:null;
        if(special!=null){int c=Integer.compare(rank(special,a),rank(special,b));if(c!=0)return c;}
        boolean na=digits(a),nb=digits(b);if(na!=nb)return na?1:-1;
        if(na&&nb){int c=Integer.compare(a.length(),b.length());return c!=0?c:a.compareTo(b);}
        int c=Integer.compare(rank(FIELD_RANK,a),rank(FIELD_RANK,b));return c!=0?c:a.compareTo(b);
    }
    private static int compare(String[] x,String[] y){
        String ga=x.length==1?"基本信息":x[0],gb=y.length==1?"基本信息":y[0];int c=groups(ga,gb);if(c!=0)return c;
        for(int i=0;i<Math.min(x.length,y.length);i++){
            if(!x[i].equals(y[i])){boolean xl=i==x.length-1,yl=i==y.length-1;if(xl!=yl)return xl?-1:1;}
            c=segment(x[i],y[i],i>=2?x[i-1]:null);if(c!=0)return c;
        }
        return Integer.compare(x.length,y.length);
    }
    static int fields(String a,String b){return compare(a.split("\\."),b.split("\\."));}
    /** Split each path once per sort, rather than on every TreeMap insertion/comparison. */
    static List<String> sortedFields(Collection<String> paths){
        ArrayList<String> out=new ArrayList<>(paths);Map<String,String[]> parts=new HashMap<>();
        for(String p:out)parts.put(p,p.split("\\."));
        out.sort((a,b)->compare(parts.get(a),parts.get(b)));return out;
    }
}
