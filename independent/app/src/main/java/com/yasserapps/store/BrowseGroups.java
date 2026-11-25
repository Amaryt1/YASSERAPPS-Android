package com.yasserapps.store;

import java.util.*;
import java.util.regex.Pattern;

/** Local browsing groups: use provider genre first, then conservative keyword hints.
 * Unknown entries stay in the general category; no invented download statistics.
 */
final class BrowseGroups {
    static final String[][] APP={
        {"social","التواصل الاجتماعي","social|social networking|communication|messaging|messenger|chat|whatsapp|telegram|signal|viber|discord|facebook|instagram|snapchat|تواصل اجتماعي|مراسلة|دردشة"},
        {"photo","تعديل الصور","photography|photo editor|photo editing|photo effects|image editor|image editing|camera|picsart|lightroom|snapseed|photoeditor|تعديل الصور|تحرير الصور|كاميرا"},
        {"video","تحرير الفيديو","video editor|video editing|video maker|video player|video players|capcut|kinemaster|inshot|vlog|videoeditor|تحرير الفيديو|مونتاج"},
        {"audio","الموسيقى والصوت","music|audio|podcast|radio|spotify|sound recorder|equalizer|موسيقى|صوتيات|بودكاست"},
        {"ai","الذكاء الاصطناعي","artificial intelligence|ai assistant|ai chatbot|ai generator|chatgpt|gemini|copilot|ذكاء اصطناعي"},
        {"tools","الأدوات","tools|utilities|utility|file manager|calculator|scanner|vpn|keyboard|browser|launcher|weather|clock|أدوات|حاسبة|متصفح|مدير الملفات"}
    };
    static final String[][] GAME={
        {"action","ألعاب الأكشن","action|shooter|shooting|fps|battle royale|combat|fighting|pubg|call of duty|free fire|اكشن|أكشن|قتال"},
        {"adventure","ألعاب المغامرات","adventure|platformer|quest|adventurer|مغامرة|مغامرات"},
        {"racing","ألعاب السيارات والسباقات","racing|race car|car racing|drift|motorcycle|motorsport|asphalt|سباق|سباقات|سيارات"},
        {"openworld","ألعاب العالم المفتوح","open world|sandbox|minecraft|grand theft auto|gta|عالم مفتوح"},
        {"sports","ألعاب الرياضة وكرة القدم","sports|football|soccer|basketball|tennis|cricket|efootball|fifa|رياضة|كرة القدم"},
        {"simulation","ألعاب المحاكاة","simulation|simulator|tycoon|flight sim|truck sim|محاكاة"},
        {"puzzle","ألعاب الألغاز","puzzle|match 3|sudoku|brain teaser|word game|ألغاز|لغز"},
        {"strategy","ألعاب الاستراتيجية","strategy|tower defense|real time strategy|استراتيجية"}
    };
    private static final Map<String,Pattern> patterns=new HashMap<>();
    static{
        for(String[][] table:new String[][][]{APP,GAME})for(String[] row:table)
            patterns.put(row[0],Pattern.compile("(?iu)(?:^|[^\\p{L}\\p{N}])(?:"+row[2]+")(?:$|[^\\p{L}\\p{N}])"));
    }
    static String normalize(String v){return v==null?"":v.toLowerCase(Locale.ROOT).replaceAll("[._/\\-]+"," ");}
    private static String match(String[][] rows,String input){
        if(input.isEmpty())return "";
        // Prefer more specific subjects before broad action/social categories.
        for(String id:new String[]{"openworld","racing","sports","ai","photo","video"})
            for(String[] row:rows)if(row[0].equals(id)&&patterns.get(id).matcher(input).find())return id;
        for(String[] row:rows)if(patterns.get(row[0]).matcher(input).find())return row[0];
        return "";
    }
    static String topic(Catalog.App a){
        if(a.browseTopic==null)a.browseTopic=computeTopic(a);
        return a.browseTopic;
    }
    private static String computeTopic(Catalog.App a){
        String[][] table=a.isGame()?GAME:APP;
        String value=match(table,normalize(a.genre));
        if(!value.isEmpty())return value;
        value=match(table,normalize(a.name+" "+a.packageName));
        if(!value.isEmpty())return value;
        return match(table,normalize(a.summary));
    }
    static List<Catalog.App> select(List<Catalog.App> all,String key){
        ArrayList<Catalog.App> out=new ArrayList<>();
        if(key.startsWith("topic:")){
            String[] parts=key.split(":",3);
            if(parts.length!=3)return out;
            for(Catalog.App a:all)if(a.isGame()=="game".equals(parts[1])&&parts[2].equals(topic(a)))out.add(a);
        }else{
            boolean onlyGames=key.endsWith(":game"),onlyApps=key.endsWith(":app");
            for(Catalog.App a:all)if((!onlyGames||a.isGame())&&(!onlyApps||!a.isGame())){
                if(key.equals("recent")||key.equals("changed")){if(a.updatedAt<=0)continue;}
                if(key.equals("added")&&a.firstSeenAt<=0)continue;
                out.add(a);
            }
            if(key.startsWith("picks")){
                long day=System.currentTimeMillis()/86400000L;
                out.sort(Comparator.comparingLong(a->mix(a.id.hashCode()^day)));
            }else if(key.equals("added")){
                out.sort(Comparator.comparingLong((Catalog.App a)->a.firstSeenAt).reversed().thenComparing(a->a.id));
            }else if(key.equals("recent")||key.equals("changed")){
                out.sort(Comparator.comparingLong((Catalog.App a)->a.updatedAt).reversed().thenComparing(a->a.id));
            }
        }
        return out;
    }
    private static long mix(long x){x=(x^(x>>>33))*0xff51afd7ed558ccdL;x=(x^(x>>>33))*0xc4ceb9fe1a85ec53L;return x^(x>>>33);}
    static List<Catalog.App> others(List<Catalog.App> all,Catalog.App current){
        ArrayList<Catalog.App> out=new ArrayList<>();
        for(Catalog.App a:all)if(a.isGame()==current.isGame()&&!a.id.equals(current.id)&&(current.packageName.isEmpty()||!current.packageName.equals(a.packageName)))out.add(a);
        return out;
    }
    static List<Catalog.App> related(List<Catalog.App> all,Catalog.App current){
        ArrayList<Catalog.App> out=new ArrayList<>();
        String group=topic(current);
        for(Catalog.App a:all){
            if(a.isGame()!=current.isGame()||a.id.equals(current.id)||(!current.packageName.isEmpty()&&current.packageName.equals(a.packageName)))continue;
            if(!group.isEmpty()&&group.equals(topic(a)))out.add(a);
        }
        return out;
    }
}
