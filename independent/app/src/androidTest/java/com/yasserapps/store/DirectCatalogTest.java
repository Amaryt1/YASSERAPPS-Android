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
            int total=0, games=0;
            java.util.ArrayList<Catalog.App> combined=new java.util.ArrayList<>();
            for(String id:new String[]{"fdroid","izzyondroid"}) {
                List<Catalog.App> apps=DirectSources.verifyFixture(getTargetContext(),id,new File(root,id+".jar"),new File(root,id+".json"));
                if(apps.isEmpty()) throw new AssertionError("Empty "+id);
                for(Catalog.App a:apps) {
                    if(a.isGame()) games++;
                    if(!DownloadCoordinator.isDirectApk(a.apkUrl)||!a.sha256.matches("(?i)[a-f0-9]{64}"))
                        throw new AssertionError("Invalid APK metadata");
                    Catalog.App copy=Catalog.fromJson(Catalog.toJson(a));
                    if(copy.versionCode!=a.versionCode||!copy.apkUrl.equals(a.apkUrl))
                        throw new AssertionError("Cache conversion failed");
                }
                total+=apps.size();
                combined.addAll(apps);
            }
            if(games==0||total<100) throw new AssertionError("Unexpected counts: "+total+"/"+games);
            java.util.Map<String,Catalog.App> unique=new java.util.LinkedHashMap<>();
            for(Catalog.App a:combined) unique.putIfAbsent(a.packageName,a);
            List<Catalog.App> featured=FeaturedApps.choose(new java.util.ArrayList<>(unique.values()));
            if(featured.size()!=10) throw new AssertionError("Expected ten featured recommendations");
            String copy=FeaturedApps.displayCopy("Manage your photos.\nSource: https://github.com/example/repo\nBrowse albums and edit images.\nhttps://gitlab.com/foo");
            if(!copy.contains("Manage your photos.")||!copy.contains("Browse albums and edit images.")||copy.contains("https")||copy.contains("Source:"))
                throw new AssertionError("Description cleanup failed: "+copy);
            result.putString("stream","\nPASS signed direct sources on Android: "+total+" entries, "+games+" games before deduplication\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("stream","\nFAIL "+android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
