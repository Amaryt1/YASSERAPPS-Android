package com.yasserapps.store;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Catalog {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static class App {
        public String id="", name="", packageName="", category="app", version="", genre="";
        public String apkUrl="", iconUrl="", bannerUrl="", summary="", description="", changelog="", sha256="";
        public String sourceName="", signerSha256="", license="", legacyMd5="", legacySignerSha1="";
        public int versionCode;
        public long sizeBytes, updatedAt, firstSeenAt;
        String browseTopic;
        public boolean featured, discoveryOnly;
        public String originalPlatform="", gameFiles="", filesInstructions="", filesPath="", publisherUrl="", attribution="";
        public boolean needsFiles() { return "owned".equals(gameFiles)||"required".equals(gameFiles); }
        public ArrayList<String> screenshots = new ArrayList<>();
        public boolean isGame() { return "game".equals(category); }
    }
    public static class Result {
        public final List<App> apps;
        public final String warning;
        public final boolean finalResult;
        Result(List<App> apps, String warning, boolean finalResult) {
            this.apps = apps; this.warning = warning; this.finalResult = finalResult;
        }
    }
    public interface Callback { void loaded(Result result); }
    static JSONObject toJson(App a) throws Exception {
        return new JSONObject().put("id",a.id).put("name",a.name).put("packageName",a.packageName)
            .put("category",a.category).put("genre",a.genre).put("version",a.version).put("versionCode",a.versionCode)
            .put("apkUrl",a.apkUrl).put("iconUrl",a.iconUrl).put("bannerUrl",a.bannerUrl)
            .put("summary",a.summary).put("description",a.description).put("changelog",a.changelog)
            .put("sha256",a.sha256).put("sizeBytes",a.sizeBytes).put("sourceName",a.sourceName)
            .put("legacyMd5",a.legacyMd5).put("legacySignerSha1",a.legacySignerSha1).put("signerSha256",a.signerSha256).put("license",a.license).put("updatedAt",a.updatedAt).put("firstSeenAt",a.firstSeenAt)
            .put("screenshots",new JSONArray(a.screenshots))
            .put("discoveryOnly",a.discoveryOnly).put("originalPlatform",a.originalPlatform).put("gameFiles",a.gameFiles)
            .put("filesInstructions",a.filesInstructions).put("filesPath",a.filesPath).put("publisherUrl",a.publisherUrl).put("attribution",a.attribution);
    }
    static App fromJson(JSONObject obj) throws Exception {
        App a=new App();
        a.id=obj.getString("id"); a.name=obj.getString("name"); a.packageName=obj.getString("packageName");
        a.category=obj.getString("category"); a.genre=obj.optString("genre"); a.version=obj.getString("version"); a.versionCode=obj.getInt("versionCode");
        a.apkUrl=obj.getString("apkUrl"); a.iconUrl=obj.optString("iconUrl"); a.bannerUrl=obj.optString("bannerUrl");
        a.summary=obj.optString("summary"); a.description=obj.optString("description"); a.changelog=obj.optString("changelog");
        a.sha256=obj.getString("sha256"); a.sizeBytes=obj.getLong("sizeBytes"); a.sourceName=obj.getString("sourceName");
        a.legacyMd5=obj.optString("legacyMd5"); a.legacySignerSha1=obj.optString("legacySignerSha1"); a.signerSha256=obj.getString("signerSha256"); a.license=obj.optString("license"); a.updatedAt=obj.optLong("updatedAt"); a.firstSeenAt=obj.optLong("firstSeenAt");
        a.discoveryOnly=obj.optBoolean("discoveryOnly"); a.originalPlatform=obj.optString("originalPlatform");
        a.gameFiles=obj.optString("gameFiles"); a.filesInstructions=obj.optString("filesInstructions");
        a.filesPath=obj.optString("filesPath"); a.publisherUrl=obj.optString("publisherUrl"); a.attribution=obj.optString("attribution");
        if(a.discoveryOnly ? !(PortFeeds.publisherLink(a.publisherUrl)||AptoideFeed.listingLink(a.publisherUrl)||("APKMirror".equals(a.sourceName)&&MirrorFeed.listingLink(a.publisherUrl))||PublicChartFeed.listingLink(a.sourceName,a.publisherUrl)) : (!DownloadCoordinator.isDirectApk(a.apkUrl)||(!a.sha256.matches("(?i)[a-f0-9]{64}")&&!AptoideFeed.directEligible(a))||a.versionCode<1))
            throw new IllegalArgumentException("Invalid cached entry");
        JSONArray shots=obj.optJSONArray("screenshots");
        if(shots!=null) for(int i=0;i<shots.length();i++) a.screenshots.add(shots.getString(i));
        return a;
    }
    private static final ExecutorService DETAILS=Executors.newSingleThreadExecutor();
    static void enrich(Context context,App original,java.util.function.Consumer<App> callback) {
        Context app=context.getApplicationContext();
        DETAILS.execute(()->{
            App fresh=null;
            try { fresh=DirectSources.latestAptoide(app,original); }
            catch(Exception e){android.util.Log.w("YASSERAPPS_CATALOG","DETAIL_LOOKUP_FAILED",e);}
            callback.accept(fresh);
        });
    }
    public static void load(Context context,Callback callback) {
        Context app=context.getApplicationContext();
        IO.execute(() -> {
            List<App> owner=OwnerCatalog.load(app);
            DirectSources.load(app,result -> callback.loaded(new Result(OwnerCatalog.merge(result.apps,owner),result.warning,result.finalResult)));
        });
    }
}
