package com.yasserapps.store;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.File;
import java.util.List;

public final class DirectCatalogTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            File root=getTargetContext().getFilesDir();
            for(String category:new String[]{"Game","Games","Action Game","Role-Playing Game","Puzzle Game","Strategy Game","Sport Game","game:arcade"}) {
                if(!DirectSources.game(new org.json.JSONObject().put("categories",new org.json.JSONArray().put(category))))
                    throw new AssertionError("Game classification failed: "+category);
            }
            if(DirectSources.game(new org.json.JSONObject().put("categories",new org.json.JSONArray().put("Development"))))
                throw new AssertionError("False game classification");
            int total=0, games=0, verified=0;
            java.util.ArrayList<Catalog.App> combined=new java.util.ArrayList<>();
            for(String id:new String[]{"fdroid","izzyondroid","guardian","microg","playfdroid","kde","newpipe","collabora","bitwarden","molly","session","simplex","wind","modernapps","rmgames"}) {
                List<Catalog.App> apps;
                try { apps=DirectSources.verifyFixture(getTargetContext(),id,new File(root,id+".jar"),new File(root,id+".json")); }
                catch(Exception e) { android.util.Log.e("YASSERAPPS_CATALOG","FIXTURE_FAILED "+id,e); if(verified<4&&java.util.Arrays.asList("fdroid","izzyondroid","guardian","microg").contains(id)) throw e; continue; }
                verified++;
                int sourceGames=0;
                if(apps.isEmpty()) throw new AssertionError("Empty "+id);
                for(Catalog.App a:apps) {
                    if(a.isGame()) { games++; sourceGames++; }
                    if(!DownloadCoordinator.isDirectApk(a.apkUrl)||!a.sha256.matches("(?i)[a-f0-9]{64}"))
                        throw new AssertionError("Invalid APK metadata");
                    Catalog.App copy=Catalog.fromJson(Catalog.toJson(a));
                    if(copy.versionCode!=a.versionCode||!copy.apkUrl.equals(a.apkUrl))
                        throw new AssertionError("Cache conversion failed");
                }
                android.util.Log.i("YASSERAPPS_CATALOG","FIXTURE_SOURCE "+id+" count="+apps.size()+" games="+sourceGames);
                total+=apps.size();
                combined.addAll(apps);
            }
            if(verified!=15||games<50||total<100) throw new AssertionError("Unexpected counts: "+total+"/"+games);
            java.util.Map<String,Catalog.App> unique=new java.util.LinkedHashMap<>();
            for(Catalog.App a:combined) unique.putIfAbsent(a.packageName,a);
            List<Catalog.App> featured=FeaturedApps.choose(new java.util.ArrayList<>(unique.values()));
            if(featured.size()!=10) throw new AssertionError("Expected ten featured recommendations");
            // Featured cards must update when a newer release arrives.
            Catalog.App first=new Catalog.App(); first.packageName="test.first"; first.iconUrl="https://example.org/first.png"; first.updatedAt=10; first.versionCode=1;
            Catalog.App second=new Catalog.App(); second.packageName="test.second"; second.iconUrl="https://example.org/second.png"; second.updatedAt=20; second.versionCode=1;
            if(FeaturedApps.choose(java.util.Arrays.asList(first,second)).get(0)!=second) throw new AssertionError("Featured release order");
            first.updatedAt=30; first.versionCode=2;
            if(FeaturedApps.choose(java.util.Arrays.asList(first,second)).get(0)!=first) throw new AssertionError("Featured refresh order");
            org.json.JSONObject port=new org.json.JSONObject()
                .put("id","test-port").put("title","Test port")
                .put("platforms",new org.json.JSONArray().put("PS2"))
                .put("android",new org.json.JSONObject().put("supported",true).put("requiresGameFiles","owned"))
                .put("links",new org.json.JSONObject().put("repo","https://github.com/example/port"))
                .put("downloads",new org.json.JSONArray().put(new org.json.JSONObject().put("kind","apk").put("url","https://github.com/example/port/releases/latest")))
                .put("setup",new org.json.JSONObject().put("requiredFiles","Owned data").put("installPath","Document picker"));
            List<Catalog.App> ports=PortFeeds.parsePortHub(new org.json.JSONObject().put("version",1).put("games",new org.json.JSONArray().put(port)));
            if(ports.size()!=1||!ports.get(0).needsFiles()||!ports.get(0).discoveryOnly||!ports.get(0).apkUrl.isEmpty()) throw new AssertionError("Port data isolation");
            Catalog.App cachedPort=Catalog.fromJson(Catalog.toJson(ports.get(0)));
            if(!cachedPort.needsFiles()||!cachedPort.filesPath.equals("Document picker")||!cachedPort.discoveryOnly) throw new AssertionError("Port data cache");
            if(first.needsFiles()) throw new AssertionError("Ordinary APK must not show files button");
            if(PortFeeds.publisherLink("https://github.com.evil.example/example/port")) throw new AssertionError("Unsafe publisher link");
            android.util.Log.i("YASSERAPPS_CATALOG","PASS featured recency and port requirements");
            // Exercise large-catalog pagination without storing upstream content or using keys.
            java.util.List<LargeCatalogPager.Listing> large=LargeCatalogPager.collect((category,offset,limit)->{
                org.json.JSONArray rows=new org.json.JSONArray();
                for(int i=0;i<limit;i++) rows.put(new org.json.JSONObject().put("package","test.large.app"+(offset+i)).put("name","Fixture "+(offset+i)).put("file",new org.json.JSONObject().put("vercode",1).put("filesize",100).put("malware",new org.json.JSONObject().put("rank","TRUSTED"))));
                return new org.json.JSONObject().put("info",new org.json.JSONObject().put("status","OK")).put("datalist",new org.json.JSONObject().put("list",rows).put("next",offset+limit).put("total",5000));
            },"games",50,100);
            if(large.size()!=5000)throw new AssertionError("Large-catalog pagination");
            android.util.Log.i("YASSERAPPS_CATALOG","PASS synthetic 5000-entry pagination (not live apps)");
            String copy=FeaturedApps.displayCopy("Manage your photos.\nSource: https://github.com/example/repo\nBrowse albums and edit images.\nhttps://gitlab.com/foo");
            if(!copy.contains("Manage your photos.")||!copy.contains("Browse albums and edit images.")||copy.contains("https")||copy.contains("Source:"))
                throw new AssertionError("Description cleanup failed: "+copy);
            result.putString("stream","\nPASS signed direct sources on Android: "+total+" entries, "+games+" games before deduplication; verified sources="+verified+"\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("stream","\nFAIL "+android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
