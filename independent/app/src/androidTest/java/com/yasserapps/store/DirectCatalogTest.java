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
            org.json.JSONObject sample=new org.json.JSONObject().put("package","test.market.app").put("name","Market fixture").put("uname","market-fixture").put("file",new org.json.JSONObject().put("vercode",1).put("vername","1.0").put("filesize",1000).put("malware",new org.json.JSONObject().put("rank","TRUSTED")));
            List<Catalog.App> market=AptoideFeed.parse(new org.json.JSONObject().put("info",new org.json.JSONObject().put("status","OK")).put("datalist",new org.json.JSONObject().put("list",new org.json.JSONArray().put(sample))),"games");
            if(market.size()!=1||!market.get(0).discoveryOnly||!market.get(0).apkUrl.isEmpty()||!market.get(0).isGame())throw new AssertionError("Aptoide metadata boundary");
            if(!Catalog.fromJson(Catalog.toJson(market.get(0))).discoveryOnly||AptoideFeed.listingLink("https://x.en.aptoide.com.evil.example/app"))throw new AssertionError("Aptoide cache or URL validation");
            // Real list responses omit the malware verdict; only matching detailed releases qualify.
            org.json.JSONObject listing=new org.json.JSONObject(sample.toString());
            String md5="0123456789abcdef0123456789abcdef";
            listing.getJSONObject("file").put("md5sum",md5).remove("malware");
            org.json.JSONObject detail=new org.json.JSONObject(sample.toString());
            detail.getJSONObject("file").put("md5sum",md5);
            org.json.JSONObject response=new org.json.JSONObject().put("info",new org.json.JSONObject().put("status","OK")).put("data",detail);
            Catalog.App checked=AptoideFeed.checkedDetail(listing,response,"games");
            if(checked==null||!checked.discoveryOnly||!checked.apkUrl.isEmpty())throw new AssertionError("Matching detail must remain metadata-only");
            detail.getJSONObject("file").put("vercode",2).put("vername","2.0");
            Catalog.App newest=AptoideFeed.latestDetail(checked,response);
            if(newest==null||newest.versionCode!=2||!newest.discoveryOnly||!newest.apkUrl.isEmpty())throw new AssertionError("Latest source detail");
            detail.put("package","wrong.market.app");
            if(AptoideFeed.latestDetail(checked,response)!=null)throw new AssertionError("Wrong detail package");
            detail.put("package","test.market.app");
            detail.getJSONObject("file").put("vercode",1).put("vername","1.0");
            detail.getJSONObject("file").put("md5sum","fedcba9876543210fedcba9876543210");
            if(AptoideFeed.checkedDetail(listing,response,"games")!=null)throw new AssertionError("Mismatched release accepted");
            detail.getJSONObject("file").put("md5sum",md5).put("malware",new org.json.JSONObject().put("rank","UNKNOWN"));
            if(AptoideFeed.checkedDetail(listing,response,"games")!=null)throw new AssertionError("Unknown security verdict accepted");
            org.json.JSONObject listResponse=new org.json.JSONObject().put("info",new org.json.JSONObject().put("status","OK")).put("datalist",new org.json.JSONObject().put("list",new org.json.JSONArray().put(listing)));
            List<Catalog.App> unchecked=AptoideFeed.discover(listResponse,"games");
            if(unchecked.size()!=1||!unchecked.get(0).discoveryOnly||!unchecked.get(0).apkUrl.isEmpty()||!AptoideFeed.parse(listResponse,"games").isEmpty())throw new AssertionError("Unchecked entries must remain discovery-only");
            listing.getJSONObject("file").put("malware",new org.json.JSONObject().put("rank","WARN"));
            if(!AptoideFeed.discover(listResponse,"games").isEmpty())throw new AssertionError("Explicit non-trusted listing rejected");
            android.util.Log.i("YASSERAPPS_CATALOG","PASS Aptoide detail checks and bulk discovery-only boundary");
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
            String rss="<rss version='2.0'><channel><item><title>Example 2.0</title><link>https://www.apkmirror.com/apk/example/example-app/example-2-release/</link><description>Example description</description><pubDate>Tue, 24 Nov 2026 10:00:00 +0000</pubDate></item><item><title>Unsafe</title><link>https://www.apkmirror.com.evil.example/apk/a/b/</link></item></channel></rss>";
            List<Catalog.App> mirror=MirrorFeed.parse(new java.io.ByteArrayInputStream(rss.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if(mirror.size()!=1||!mirror.get(0).discoveryOnly||!mirror.get(0).packageName.isEmpty()||!mirror.get(0).apkUrl.isEmpty()||!Catalog.fromJson(Catalog.toJson(mirror.get(0))).id.equals(mirror.get(0).id))throw new AssertionError("Mirror RSS metadata boundary");
            result.putString("stream","\nPASS market metadata boundaries and pagination fixtures\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("stream","\nFAIL "+android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
