package com.yasserapps.store;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Owner-maintained HTTPS catalogue, separate from third-party metadata. */
final class OwnerCatalog {
    static final String URL="https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/apps.json";
    static final String SOURCE="YASSERAPPS";
    static List<Catalog.App> parse(JSONObject root)throws Exception{
        if(root.optInt("schemaVersion")!=1||!SOURCE.equals(root.optString("store")))throw new SecurityException("Wrong owner catalogue");
        JSONArray rows=root.getJSONArray("apps");
        if(rows.length()>5000)throw new SecurityException("Owner catalogue too large");
        ArrayList<Catalog.App> result=new ArrayList<>();
        Set<String> ids=new HashSet<>(),packages=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);
            if(!r.optBoolean("enabled",true))continue;
            String id=r.getString("id"),pkg=r.getString("packageName");
            if(!id.matches("[a-z0-9][a-z0-9_.-]{2,79}")||!pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")||!ids.add(id)||!packages.add(pkg))throw new SecurityException("Duplicate or invalid owner entry");
            if(!r.optString("sha256").matches("(?i)[a-f0-9]{64}")||!r.optString("signerSha256").matches("(?i)[a-f0-9]{64}")||!r.optString("apkUrl").split("[?#]",2)[0].toLowerCase(Locale.ROOT).endsWith(".apk")||r.optLong("sizeBytes")<=0)throw new SecurityException("Unverified APK metadata");
            JSONObject normalized=new JSONObject(r.toString());
            normalized.put("id","owner."+id).put("sourceName",SOURCE).put("discoveryOnly",false).put("attribution",r.optString("attribution"));
            Catalog.App a=Catalog.fromJson(normalized);
            a.featured=r.optBoolean("featured");
            if(!"app".equals(a.category)&&!"game".equals(a.category))throw new SecurityException("Invalid category");
            result.add(a);
        }
        return result;
    }
    static List<Catalog.App> load(Context context){
        try{
            // A failed fetch is not permission to expose stale unpublished APKs.
            return parse(DirectSources.fetchJson(context,URL,8*1024*1024));
        }catch(Exception e){android.util.Log.w("YASSERAPPS_OWNER","Owner feed unavailable",e);return Collections.emptyList();}
    }
    static List<Catalog.App> merge(List<Catalog.App> external,List<Catalog.App> owner){
        ArrayList<Catalog.App> out=new ArrayList<>(external.size()+owner.size());
        // Keep upstream and owner versions separate: a different signer must not be substituted for an installed app.
        out.addAll(owner);out.addAll(external);
        return out;
    }
}
