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
