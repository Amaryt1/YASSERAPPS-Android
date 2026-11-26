package com.yasserapps.store;

import android.content.*;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class UserPrefs {
    private static final String FILE="yasser_user_v1";
    static android.content.SharedPreferences get(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    static List<String> recent(Context c){
        ArrayList<String> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(get(c).getString("recent","[]"));for(int i=0;i<a.length();i++)out.add(a.getString(i));}catch(Exception ignored){}
        return out;
    }
    static void remember(Context c,String value){
        String q=value.trim();if(q.length()<2||q.length()>150)return;
        List<String> a=recent(c);a.removeIf(v->v.equalsIgnoreCase(q));a.add(0,q);
        while(a.size()>12)a.remove(a.size()-1);
        get(c).edit().putString("recent",new JSONArray(a).toString()).apply();
    }
    static void clearRecent(Context c){get(c).edit().remove("recent").apply();}
    static int rating(Context c,String id){return get(c).getInt("rating:"+id,0);}
    static void rating(Context c,String id,int value){if(value>=1&&value<=5)get(c).edit().putInt("rating:"+id,value).apply();}
    static boolean notices(Context c){return get(c).getBoolean("catalog_notices",false);}
    static void notices(Context c,boolean value){get(c).edit().putBoolean("catalog_notices",value).remove("seen_count").apply();}
    static byte[] backup(Context c)throws Exception{
        Set<String> fav=c.getSharedPreferences("favorites_store_v1",0).getStringSet("ids",Collections.emptySet());
        JSONObject o=new JSONObject().put("schemaVersion",1).put("favorites",new JSONArray(fav)).put("catalogNotices",notices(c));
        return o.toString(2).getBytes(StandardCharsets.UTF_8);
    }
    static void restore(Context c,InputStream in)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>1024*1024)throw new IOException("Backup too large");out.write(buf,0,n);}
        JSONObject o=new JSONObject(out.toString("UTF-8"));
        if(o.optInt("schemaVersion")!=1)throw new IOException("Invalid backup");
        JSONArray a=o.getJSONArray("favorites");if(a.length()>50000)throw new IOException("Too many favorites");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<a.length();i++){String id=a.getString(i);if(id.isEmpty()||id.length()>2048)throw new IOException("Invalid ID");ids.add(id);}
        c.getSharedPreferences("favorites_store_v1",0).edit().putStringSet("ids",ids).apply();
        notices(c,o.optBoolean("catalogNotices",false));
    }
}
