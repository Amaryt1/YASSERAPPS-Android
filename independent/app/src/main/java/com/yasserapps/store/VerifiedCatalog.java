package com.yasserapps.store;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

final class VerifiedCatalog {
    static final String URL="https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/generated/direct-index.json";
    static void exclude(Context c,Catalog.App a){
        c.getSharedPreferences("unavailable_direct",0).edit().putInt(a.packageName,a.versionCode).apply();
    }
    static boolean eligible(Catalog.App a){
        return !a.discoveryOnly&&!a.packageName.isEmpty()&&(AptoideFeed.directEligible(a)||("YASSERAPPS".equals(a.sourceName)&&DownloadCoordinator.isDirectApk(a.apkUrl)&&a.sha256.matches("(?i)[a-f0-9]{64}")&&a.signerSha256.matches("(?i)[a-f0-9]{64}")));
    }
    static List<Catalog.App> parse(JSONObject root)throws Exception{
        if(root.optInt("schemaVersion")!=1)throw new IOException("Bad schema");
        JSONArray array=root.getJSONArray("apps");
        if(array.length()>50000)throw new IOException("Too many apps");
        ArrayList<Catalog.App> rows=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            Catalog.App a=Catalog.fromJson(array.getJSONObject(i));
            if(eligible(a)&&"Aptoide".equals(a.sourceName))rows.add(a);
        }
        return rows;
    }
    static List<Catalog.App> seed(Context c){
        try(InputStream in=c.getAssets().open("direct-catalog.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>32*1024*1024)throw new IOException();out.write(b,0,n);}
            return parse(new JSONObject(out.toString("UTF-8")));
        }catch(Exception e){return Collections.emptyList();}
    }
    static List<Catalog.App> remote(Context c,List<Catalog.App> seed){
        try{return parse(DirectSources.fetchJson(c,URL,32*1024*1024));}
        catch(Exception e){return seed;}
    }
    static final class State {
        List<Catalog.App> rows=Collections.emptyList();
        final Map<String,Integer> rejected=new HashMap<>();
    }
    static State state(Context c){
        State s=new State();
        try{
            JSONObject root=DirectSources.fetchJson(c,URL,32*1024*1024);
            s.rows=parse(root);
            JSONObject bad=root.optJSONObject("rejectedVersions");
            if(bad!=null){Iterator<String> keys=bad.keys();while(keys.hasNext()){String pkg=keys.next();int code=bad.optInt(pkg,0);if(code>0)s.rejected.put(pkg,code);}}
        }catch(Exception e){s.rows=seed(c);}
        return s;
    }
    static List<Catalog.App> restore(Context c,List<Catalog.App> metadata,State state,List<Catalog.App> owner){
        Map<String,Catalog.App> unique=new LinkedHashMap<>();
        Map<String,Catalog.App> approved=new HashMap<>();
        for(Catalog.App a:state.rows)if(eligible(a))approved.put(a.packageName,a);
        android.content.SharedPreferences blocked=c.getSharedPreferences("unavailable_direct",0);
        for(Catalog.App a:owner)if(eligible(a))unique.put(a.packageName,a);
        for(Catalog.App a:metadata){
            if(!"Aptoide".equals(a.sourceName)&&!eligible(a))continue;
            if(a.packageName.isEmpty()||unique.containsKey(a.packageName)&&OwnerCatalog.SOURCE.equals(unique.get(a.packageName).sourceName))continue;
            if("Aptoide".equals(a.sourceName)){
                Catalog.App verified=approved.get(a.packageName);
                if(verified!=null&&verified.versionCode>=a.versionCode)a=verified;
                if(blocked.getInt(a.packageName,-1)>=a.versionCode||state.rejected.getOrDefault(a.packageName,-1)>=a.versionCode)continue;
            }
            Catalog.App old=unique.get(a.packageName);
            if(old==null||a.versionCode>old.versionCode||a.versionCode==old.versionCode&&old.discoveryOnly&&!a.discoveryOnly)unique.put(a.packageName,a);
        }
        for(Catalog.App a:state.rows)if(eligible(a)&&!unique.containsKey(a.packageName)&&blocked.getInt(a.packageName,-1)<a.versionCode&&state.rejected.getOrDefault(a.packageName,-1)<a.versionCode)unique.put(a.packageName,a);
        ArrayList<Catalog.App> out=new ArrayList<>(unique.values());
        out.sort(Comparator.comparingLong((Catalog.App a)->a.updatedAt).reversed().thenComparing(a->a.name));
        return out;
    }
    static List<Catalog.App> merge(Context c,List<Catalog.App> rows,List<Catalog.App> owner){
        Map<String,Catalog.App> unique=new LinkedHashMap<>();
        ArrayList<Catalog.App> all=new ArrayList<>(owner);all.addAll(rows);
        for(Catalog.App a:all){
            if(!eligible(a))continue;
            if("Aptoide".equals(a.sourceName)&&c.getSharedPreferences("unavailable_direct",0).getInt(a.packageName,-1)>=a.versionCode)continue;
            Catalog.App old=unique.get(a.packageName);
            if(old==null||(!OwnerCatalog.SOURCE.equals(old.sourceName)&&a.versionCode>old.versionCode))unique.put(a.packageName,a);
        }
        ArrayList<Catalog.App> result=new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong((Catalog.App a)->a.updatedAt).reversed().thenComparing(a->a.name));
        return result;
    }
}
