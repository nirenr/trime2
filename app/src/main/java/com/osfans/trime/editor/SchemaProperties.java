package com.osfans.trime.editor;

import java.util.*;
import static com.osfans.trime.editor.SchemaDocument.Kind.*;

/** Field help, not runtime defaults. Sources are upstream master, not a promise about the installed core. */
final class SchemaProperties {
    static final String WIKI="https://github.com/rime/home/wiki/RimeWithSchemata";
    static final String CUSTOM="https://github.com/rime/home/wiki/CustomizationGuide";
    static final String EXAMPLE="https://github.com/rime/rime-luna-pinyin/blob/master/luna_pinyin.schema.yaml";
    static final String DEFAULT="https://github.com/rime/rime-prelude/blob/master/default.yaml";
    static final String CORE="https://github.com/rime/librime/blob/master/src/rime/";
    static final String OCTAGRAM="https://github.com/lotem/librime-octagram/blob/master/src/octagram.cc";
    static final class Field {
        final String name,desc,url;final SchemaDocument.Kind kind;
        Field(String name,String desc,SchemaDocument.Kind kind,String url){this.name=name;this.desc=desc;this.kind=kind;this.url=url;}
    }
    private static final Map<String,Field> fields=new LinkedHashMap<>();
    private static void f(String path,String name,String desc,SchemaDocument.Kind kind,String url){fields.put(path,new Field(name,desc,kind,url));}
    private static void strings(String group,String url,String... pairs){for(int i=0;i<pairs.length;i+=2)f(group+"/"+pairs[i],pairs[i+1],pairs[i+1],STRING,url);}
    private static void bools(String group,String url,String... pairs){for(int i=0;i<pairs.length;i+=2)f(group+"/"+pairs[i],pairs[i+1],pairs[i+1]+"；未定义时由引擎决定。",BOOL,url);}
    private static void ints(String group,String url,String... pairs){for(int i=0;i<pairs.length;i+=2)f(group+"/"+pairs[i],pairs[i+1],pairs[i+1],INT,url);}
    private static void lists(String group,String url,String... pairs){for(int i=0;i<pairs.length;i+=2)f(group+"/"+pairs[i],pairs[i+1],pairs[i+1]+"；列表顺序保留。",LIST,url);}
    static {
        f("schema","方案信息","方案标识与元数据",MAP,WIKI);f("switches","方案开关","布尔或互斥选项及其状态标签",LIST,EXAMPLE);f("engine","引擎组件","按列表顺序配置输入处理、分段、翻译和过滤组件",MAP,WIKI);f("speller","拼写规则","编码字符与自动选词、拼写变换",MAP,CORE+"gear/speller.cc");f("translator","翻译器","将编码映射为候选，具体字段取决于组件类型",MAP,WIKI);f("menu","候选菜单","候选分页与选择键",MAP,CORE+"schema.cc");f("punctuator","标点符号","按半角、全角和符号表定义输出",MAP,EXAMPLE);f("key_binder","按键绑定","在指定输入状态中重映射按键或切换选项",MAP,CORE+"gear/key_binder.cc");f("recognizer","模式识别","按正则表达式识别输入片段",MAP,CORE+"gear/recognizer.cc");f("patch","用户补丁","部署时合并到目标配置的路径和值",MAP,CUSTOM);f("__include","配置包含","引用另一配置文件或节点；本编辑器不展开引用",STRING,EXAMPLE);f("__patch","配置补丁","部署时应用补丁；保留原始结构",LIST,EXAMPLE);
        f("schema_list","启用方案列表","按顺序列出可切换方案",LIST,DEFAULT);strings("schema_list/@",CORE+"switcher.cc","schema","方案 ID");lists("schema_list/@",CORE+"switcher.cc","case","显示该方案的条件路径列表");f("switcher","方案菜单","切换方案和保留选项的配置",MAP,DEFAULT);strings("switcher",DEFAULT,"caption","菜单标题","option_list_separator","选项分隔文字");lists("switcher",DEFAULT,"hotkeys","切换方案快捷键","save_options","跨会话保存的选项");bools("switcher",DEFAULT,"fold_options","折叠开关选项","abbreviate_options","缩写选项名称");f("config_version","配置版本","默认配置的版本标识",STRING,DEFAULT);
        strings("schema",EXAMPLE,"schema_id","方案标识（关联文件名，不自动重命名）","name","方案显示名称","version","方案版本","description","方案说明");lists("schema",EXAMPLE,"author","作者","dependencies","依赖方案");
        lists("engine",WIKI,"processors","按键处理器","segmentors","输入分段器","translators","候选翻译器","filters","候选过滤器");
        strings("switches/@",EXAMPLE,"name","布尔选项名称");ints("switches/@",CORE+"switches.cc","reset","重置时选中的状态下标（从 0 开始）");lists("switches/@",EXAMPLE,"options","互斥选项名称","states","各状态显示文字","abbrev","状态缩写");
        String sp=CORE+"gear/speller.cc";strings("speller",sp,"alphabet","允许输入的编码字符","delimiter","音节分隔字符","initials","编码起始字符","finals","编码结束字符","auto_select_pattern","允许自动选择的编码正则","auto_clear","清空策略：auto / manual / max_length");ints("speller",sp,"max_code_length","最大编码长度");bools("speller",sp,"auto_select","自动选择候选","use_space","允许空格参与拼写");lists("speller",EXAMPLE,"algebra","拼写运算规则");
        String tc=CORE+"gear/translator_commons.cc";bools("translator",CORE+"dict/user_dictionary.cc","enable_user_dict","启用用户词典（上游默认开启）");strings("translator",EXAMPLE,"dictionary","使用的词典名称","prism","拼写棱镜名称","user_dict","用户词典名称","db_class","用户词典数据库类型");strings("translator",tc,"delimiter","翻译器音节分隔符");bools("translator",tc,"contextual_suggestions","上下文建议","enable_completion","编码补全","strict_spelling","严格拼写");f("translator/initial_quality","初始质量","参与候选排序的初始质量分数，可为小数",NUMBER,tc);ints("translator",tc,"max_sentences","最大句子数量（源码限制 1–100）");f("translator/sentence_cutoff_threshold","整句截断阈值","用于整句候选筛选的阈值",NUMBER,tc);lists("translator",tc,"preedit_format","预编辑文本变换","comment_format","候选注释变换","disable_user_dict_for_patterns","禁用用户词典的编码正则列表");
        String script=CORE+"gear/script_translator.cc";ints("script_translator",script,"spelling_hints","显示原拼写的最大音节数","max_word_length","最大词长","core_word_length","核心词长","max_homophones","同音候选数量限制");bools("script_translator",script,"always_show_comments","总是显示注释","enable_correction","启用纠错","enable_word_completion","词语补全");lists("script_translator",script,"canonicalize","规范化拼写规则");
        String table=CORE+"gear/table_translator.cc";bools("table_translator",table,"enable_charset_filter","字符集过滤","enable_sentence","整句输入","sentence_over_completion","整句优先于补全","enable_encoder","启用造词编码器","encode_commit_history","根据提交历史造词");ints("table_translator",table,"max_phrase_length","最大词组长度","max_homographs","同形候选数量限制");
        ints("menu",CORE+"schema.cc","page_size","每页候选数量（小于 1 时回退为 5）");strings("menu",CORE+"schema.cc","alternative_select_keys","替代选词键");bools("menu",CORE+"schema.cc","page_down_cycle","末页继续翻页时循环");
        strings("punctuator",EXAMPLE,"import_preset","导入预设标点表");for(String k:new String[]{"half_shape","full_shape","symbols"})f("punctuator/"+k,k,"符号映射；可以包含字符串、列表或映射",MAP,EXAMPLE);
        strings("key_binder",EXAMPLE,"import_preset","导入预设绑定");f("key_binder/bindings","绑定列表","按键触发条件、来源与动作",LIST,CORE+"gear/key_binder.cc");strings("key_binder/bindings/@",CORE+"gear/key_binder.cc","when","触发条件：always / composing / has_menu / paging / predicting","accept","接受的按键","send","发送目标按键","toggle","切换选项");strings("key_binder/bindings/@",CORE+"gear/key_binder.cc","send_sequence","发送按键序列","set_option","开启选项","unset_option","关闭选项","select","切换到指定方案");strings("recognizer",EXAMPLE,"import_preset","导入预设识别规则");f("recognizer/patterns","识别正则","名称对应 Boost 正则表达式",MAP,CORE+"gear/recognizer.cc");
        f("ascii_composer","中英文切换","方案配置优先，部分属性回退到 default.yaml",MAP,CORE+"gear/ascii_composer.cc");bools("ascii_composer",CORE+"gear/ascii_composer.cc","good_old_caps_lock","传统 Caps Lock 行为","inline_uppercase","行内大写输入","inline_keypad","行内数字键盘输入");f("ascii_composer/switch_key","中英文切换键","按键名称映射到切换方式",MAP,CORE+"gear/ascii_composer.cc");strings("punctuator",CORE+"gear/punctuator.cc","digit_separators","数字分隔字符","digit_separator_action","数字分隔动作（commit 直接提交）");bools("punctuator",CORE+"gear/punctuator.cc","use_space","允许空格作为标点键");String sim=CORE+"gear/simplifier.cc";strings("simplifier",sim,"option_name","控制转换的选项名称","tips","转换提示：none / char / all");strings("simplifier",EXAMPLE,"opencc_config","OpenCC 配置文件");bools("simplifier",sim,"show_in_comment","将转换结果显示在注释中","inherit_comment","保留原候选注释","random","随机选择转换结果");lists("simplifier",sim,"excluded_types","排除的候选类型","comment_format","注释变换规则");
        String aff=CORE+"gear/affix_segmentor.cc";strings("affix_segmentor",aff,"tag","分段标签","prefix","触发前缀","suffix","结束后缀","tips","开始提示","closing_tips","结束提示");lists("affix_segmentor",aff,"extra_tags","附加标签");lists("abc_segmentor",CORE+"gear/abc_segmentor.cc","extra_tags","附加分段标签");strings("reverse_lookup",CORE+"gear/reverse_lookup_translator.cc","tag","反查分段标签","prefix","反查前缀","suffix","反查后缀","tips","反查提示");strings("reverse_lookup",EXAMPLE,"dictionary","反查词典");lists("reverse_lookup",EXAMPLE,"preedit_format","预编辑变换","comment_format","注释变换");bools("reverse_lookup",EXAMPLE,"enable_completion","反查编码补全");
        f("grammar","语言模型","八股文语言模型：根据词语与上下文的搭配评分辅助候选排序和组句；需要 octagram 插件及对应 .gram 文件。",MAP,OCTAGRAM);
        f("grammar/language","语言模型名称","模型资源名称（不含 .gram 后缀），应与已安装的语言模型文件匹配；简繁及字词模型应与词典相符。",STRING,OCTAGRAM);
        f("grammar/collocation_max_length","最大搭配长度","限制查询时截取的上下文尾部与候选首部字符数（各至多为此值减一，并受编码上限限制）；上游默认 4。",INT,OCTAGRAM);
        f("grammar/collocation_min_length","最小搭配长度","搭配字符数达到此值或完整命中查询时按普通搭配评分，否则按弱搭配评分；上游默认 3。",INT,OCTAGRAM);
        f("grammar/collocation_penalty","普通搭配惩罚","普通搭配命中时加到模型分数上的修正量；上游默认 -12，可为小数。数值越负，该项得分越低。",NUMBER,OCTAGRAM);
        f("grammar/non_collocation_penalty","无搭配基准分","未匹配搭配时的基准分，也作为候选搭配评分的下限；无模型或无上下文时直接返回此值。上游默认 -12。",NUMBER,OCTAGRAM);
        f("grammar/weak_collocation_penalty","弱搭配惩罚","搭配未达到最小长度且未完整命中时，加到模型分数上的修正量；上游默认 -24。",NUMBER,OCTAGRAM);
        f("grammar/rear_penalty","句尾搭配惩罚","候选处于句尾且完整候选命中句尾标记时，加到模型分数上的修正量，与其他得分取最大值；上游默认 -18。",NUMBER,OCTAGRAM);
        f("translator/contextual_suggestions","上下文建议","根据前文对候选重新加权；实际效果取决于翻译器及语言模型。",BOOL,tc);
        f("script_translator/max_homophones","同音候选组句上限","组句时每个相同编码起止位置纳入的同音候选数量上限；增加可能扩大搜索范围及耗时，并非候选栏总数限制。",INT,script);
        f("table_translator/max_homographs","同码候选组句上限","码表翻译器组句搜索使用的同码候选数量限制，并非候选栏总数限制。",INT,table);
        f("translator/tag","主要分段标签","指定翻译器处理的主要输入分段标签。",STRING,tc);
        f("translator/tags","分段标签列表","指定翻译器接受的分段标签；与 tag 一同决定匹配范围。",LIST,tc);
        f("translator/dictionary_exclude","词典排除列表","查词时排除指定词条。",LIST,tc);
        f("simplifier","简繁及文本转换","使用 OpenCC 对候选进行转换。",MAP,sim);
        f("reverse_lookup","反查词典","通过辅助编码查询词条及其原编码。",MAP,EXAMPLE);
        f("affix_segmentor","前后缀分段","用前后缀标记特殊输入片段。",MAP,aff);
        f("abc_segmentor","普通编码分段","识别普通编码片段及附加标签。",MAP,CORE+"gear/abc_segmentor.cc");
        f("punctuator/half_shape","半角标点","半角模式下的标点映射。",MAP,EXAMPLE);
        f("punctuator/full_shape","全角标点","全角模式下的标点映射。",MAP,EXAMPLE);
        f("punctuator/symbols","符号表","符号编码与候选列表的映射。",MAP,EXAMPLE);
    }
    private static String normalized(String path){return path.replaceFirst("^patch/","").replaceAll("/@(?:before|after)?[0-9]+(?=/|$)","/@").replaceAll("/\\+$","");}
    private static Set<String> components(SchemaDocument doc,String namespace){Set<String> result=new LinkedHashSet<>();SchemaDocument.Node engine=doc.root.child("engine");if(engine==null)return result;for(SchemaDocument.Node l:engine.children)for(SchemaDocument.Node n:l.children){if(!n.scalar())continue;String v=n.value;int i=v.indexOf('@');if(i>0&&v.substring(i+1).equals(namespace))result.add(v.substring(0,i));if(i<0&&namespace.equals("translator")&&(v.equals("script_translator")||v.equals("table_translator")))result.add(v);}return result;}
    private static boolean translation(String c){return c.equals("script_translator")||c.equals("table_translator");}
    static Field get(SchemaDocument doc,String path){String p=normalized(path);Field field=fields.get(p);if(field==null&&p.startsWith("octagram/")){int g=p.indexOf("/grammar");if(g>=0)field=fields.get(p.substring(g+1));}if(field!=null)return field;int i=p.indexOf('/');if(i>=0){String ns=p.substring(0,i),suffix=p.substring(i);for(String c:components(doc,ns)){field=fields.get(c+suffix);if(field==null&&translation(c))field=fields.get("translator"+suffix);if(field!=null)return field;}}if(path.startsWith("patch/")&&p.equals("translator/max_homophones"))return fields.get("script_translator/max_homophones");if(path.startsWith("patch/")&&p.equals("translator/max_homographs"))return fields.get("table_translator/max_homographs");if(p.endsWith("/__include"))return fields.get("__include");if(p.endsWith("/__patch"))return fields.get("__patch");if(p.startsWith("ascii_composer/switch_key/"))return new Field("切换方式","inline_ascii / commit_text / commit_code / clear / set_ascii_mode / unset_ascii_mode；具体支持取决于引擎版本",STRING,CORE+"gear/ascii_composer.cc");if(p.startsWith("recognizer/patterns/"))return new Field("识别规则","对应输入片段的 Boost 正则表达式",STRING,CORE+"gear/recognizer.cc");if(i<0&&!p.isEmpty()){Set<String> components=components(doc,p);if(!components.isEmpty())return new Field(p,"由 engine 中同名命名空间组件使用："+components,MAP,WIKI);}return null;}
    /** Chinese annotations for built-in component/value selectors; never change stored IDs. */
    static String choiceDescription(String value){
        if(value==null)return "";
        switch(value){
            case "ascii_mode": return "中英文切换（开启为英文模式）";
            case "full_shape": return "全角/半角切换（开启为全角）";
            case "ascii_punct": return "中英文标点切换（开启为英文标点）";
            case "simplification": return "简繁转换（通常开启为简体，取决于转换器配置）";
            case "extended_charset": return "扩展字符集（取决于方案过滤器支持）";
            case "traditionalization": return "繁体转换（需方案配置对应转换器）";
            case "emoji": return "表情候选（需方案配置对应组件）";
            case "ascii_composer": return "中英文切换处理";
            case "recognizer": return "识别网址、反查等特殊输入模式";
            case "key_binder": return "按键绑定与状态切换";
            case "speller": return "编码输入与拼写处理";
            case "punctuator": return "标点符号处理";
            case "selector": return "候选选择与翻页";
            case "navigator": return "输入光标移动";
            case "express_editor": return "编辑输入及候选上屏";
            case "ascii_segmentor": return "英文输入分段";
            case "matcher": return "按识别规则匹配分段";
            case "abc_segmentor": return "普通编码分段";
            case "affix_segmentor": return "带前后缀的特殊编码分段";
            case "punct_segmentor": return "标点分段";
            case "fallback_segmentor": return "未匹配内容的后备分段";
            case "punct_translator": return "生成标点候选";
            case "table_translator": return "按码表查询候选";
            case "reverse_lookup_translator": return "反查词典及编码提示";
            case "script_translator": return "音节拼写翻译与组句";
            case "simplifier": return "OpenCC 简繁及文本转换";
            case "uniquifier": return "去除重复候选";
            case "single_char_filter": return "单字候选过滤";
            case "reverse_lookup_filter": return "为候选添加反查编码注释";
            case "always": return "任意状态";
            case "composing": return "正在输入编码";
            case "has_menu": return "存在候选菜单";
            case "paging": return "候选翻页状态";
            case "predicting": return "预测状态（视引擎版本而定）";
            case "auto": return "自动";
            case "manual": return "手动";
            case "max_length": return "达到最大编码长度";
            case "inline_ascii": return "行内英文输入";
            case "commit_text": return "提交当前候选文字";
            case "commit_code": return "提交原始编码";
            case "clear": return "清除当前输入";
            case "set_ascii_mode": return "开启英文模式";
            case "unset_ascii_mode": return "关闭英文模式";
            case "noop": return "不执行切换";
            default: return "";
        }
    }
    static String[] choiceLabels(String[] values){String[] labels=values.clone();for(int i=0;i<labels.length;i++){String desc=choiceDescription(values[i]);if(!desc.isEmpty())labels[i]=values[i]+" · "+desc;}return labels;}
    static String[] switchChoices(){return new String[]{"ascii_mode","full_shape","ascii_punct","simplification","extended_charset","traditionalization","emoji"};}
    static boolean switchValuePath(String path){String p=normalized(path);return p.equals("switches/@/name")||p.equals("switches/@/options/@");}
    static String nodeChoiceDescription(SchemaDocument.Node n){
        String path=normalized(n.path());
        if(path.equals("switches/@")&&n.kind==MAP){
            SchemaDocument.Node name=n.child("name");
            if(name!=null)return nodeChoiceDescription(name);
            SchemaDocument.Node options=n.child("options");
            if(options!=null){List<String> labels=new ArrayList<>();for(SchemaDocument.Node option:options.children){String desc=nodeChoiceDescription(option);if(!desc.isEmpty())labels.add(option.value+" · "+desc);}return String.join("；",labels);}
        }
        if(switchValuePath(n.path())){for(String choice:switchChoices())if(choice.equals(n.value))return choiceDescription(choice);return "";}
        if(path.matches("engine/(processors|segmentors|translators|filters)/@")){
            String v=n.value;if(v==null)return "";int at=v.indexOf('@');return choiceDescription(at<0?v:v.substring(0,at));
        }
        for(String choice:choices(n))if(choice.equals(n.value))return choiceDescription(choice);
        return "";
    }
    static String[] listChoices(SchemaDocument.Node parent){String p=normalized(parent.path());if(p.equals("switches/@/options"))return switchChoices();if(p.equals("engine/processors"))return new String[]{"ascii_composer","recognizer","key_binder","speller","punctuator","selector","navigator","express_editor"};if(p.equals("engine/segmentors"))return new String[]{"ascii_segmentor","matcher","abc_segmentor","affix_segmentor","punct_segmentor","fallback_segmentor"};if(p.equals("engine/translators"))return new String[]{"punct_translator","table_translator","reverse_lookup_translator","script_translator"};if(p.equals("engine/filters"))return new String[]{"simplifier","uniquifier","single_char_filter","reverse_lookup_filter"};return new String[0];}
    static String[] choices(SchemaDocument.Node node){if(switchValuePath(node.path()))return switchChoices();if(node.parent!=null&&node.parent.kind==LIST){String[] options=listChoices(node.parent);if(options.length>0)return options;}String p=normalized(node.path());if(p.equals("speller/auto_clear"))return new String[]{"auto","manual","max_length"};if(p.equals("key_binder/bindings/@/when"))return new String[]{"always","composing","has_menu","paging","predicting"};if(p.startsWith("ascii_composer/switch_key/"))return new String[]{"inline_ascii","commit_text","commit_code","clear","set_ascii_mode","unset_ascii_mode","noop"};return new String[0];}
    static Map<String,Field> patchSuggestions(SchemaDocument doc,SchemaDocument.Node parent){Map<String,Field> result=new LinkedHashMap<>();for(Map.Entry<String,Field> e:fields.entrySet()){String k=e.getKey();Field f=e.getValue();if(k.contains("/@")||k.startsWith("__")||k.startsWith("script_translator/")||k.startsWith("table_translator/")||k.startsWith("affix_segmentor/")||k.startsWith("simplifier/")||f.kind==MAP||k.indexOf('/')<0||parent.child(k)!=null)continue;result.put(k,f);}for(String field:new String[]{"max_homophones","max_homographs"}){String k="translator/"+field;if(parent.child(k)==null)result.put(k,fields.get((field.equals("max_homophones")?"script_translator/":"table_translator/")+field));}return result;}
    static Map<String,Field> suggestions(SchemaDocument doc,SchemaDocument.Node parent){String p=normalized(parent.path());if(p.startsWith("octagram/")){int g=p.indexOf("/grammar");if(g>=0)p=p.substring(g+1);}Map<String,Field> result=new LinkedHashMap<>();if(parent.kind!=MAP)return result;Set<String> components=components(doc,p);for(Map.Entry<String,Field> e:fields.entrySet()){String k=e.getKey();int i=k.lastIndexOf('/');String group=i<0?"":k.substring(0,i);String name=i<0?k:k.substring(i+1);boolean match=group.equals(p)||components.contains(group);if(!match&&group.equals("translator"))for(String c:components)if(translation(c)){match=true;break;}if(match&&parent.child(name)==null&&!name.equals("@"))result.put(name,e.getValue());}if(parent==doc.root){SchemaDocument.Node engine=doc.root.child("engine");if(engine!=null)for(SchemaDocument.Node l:engine.children)for(SchemaDocument.Node n:l.children){if(!n.scalar())continue;String v=n.value;int i=v.indexOf('@');if(i>0&&i+1<v.length()){String ns=v.substring(i+1);if(parent.child(ns)==null)result.putIfAbsent(ns,new Field(ns,"组件命名空间："+v,MAP,WIKI));}}}return result;}
}
