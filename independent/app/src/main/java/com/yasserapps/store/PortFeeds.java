package com.yasserapps.store;

import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Links-only port discovery. An unverified APK never enters the verified installer. */
final class PortFeeds {
    static boolean publisherLink(String value) {
        try {
            URI u=new URI(value);
            return "https".equals(u.getScheme())&&"github.com".equalsIgnoreCase(u.getHost())&&
                u.getUserInfo()==null&&u.getPort()==-1&&u.getQuery()==null&&u.getFragment()==null&&
                u.getPath().matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/releases(?:/.*)?)?/?")&&
                !u.getPath().contains("..");
        } catch(Exception e) { return false; }
    }
    static String repository(String value) {
        if(!publisherLink(value))return "";
        try { String[] p=new URI(value).getPath().split("/");
            return "https://github.com/"+p[1]+"/"+p[2];
        } catch(Exception e) { return ""; }
    }
    private static String safeText(String s,int max) {
        s=android.text.Html.fromHtml(s,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return s.length()>max?s.substring(0,max):s;
    }
    static boolean compatible(JSONArray abis) {
        if(abis==null||abis.length()==0)return true; // unknown; discovery only, not certified compatible
        for(int i=0;i<abis.length();i++) {
            String abi=abis.optString(i);
            if("universal".equals(abi))return true;
            for(String device:Build.SUPPORTED_ABIS)if(device.equals(abi))return true;
        }
        return false;
    }
    static List<Catalog.App> parsePortHub(JSONObject root) throws Exception {
        if(root.optInt("version")!=1)throw new IllegalArgumentException("Unsupported PortHub schema");
        JSONArray games=root.getJSONArray("games");
        LinkedHashMap<String,Catalog.App> unique=new LinkedHashMap<>();
        for(int i=0;i<games.length();i++) {
            JSONObject g=games.optJSONObject(i);
            if(g==null)continue;
            JSONObject a=g.optJSONObject("android"),links=g.optJSONObject("links"),setup=g.optJSONObject("setup");
            if(a==null||links==null||!a.optBoolean("supported")||!compatible(a.optJSONArray("abis")))continue;
            String repo=repository(links.optString("repo"));
            if(repo.isEmpty())continue;
            String release=links.optString("releases");
            if(!repo.equalsIgnoreCase(repository(release))) release=repo+"/releases";
            JSONArray downloads=g.optJSONArray("downloads");
            boolean advertised=false;
            if(downloads!=null) for(int j=0;j<downloads.length();j++) {
                JSONObject d=downloads.optJSONObject(j);
                if(d==null||!"apk".equals(d.optString("kind")))continue;
                String link=d.optString("url");
                if(repo.equalsIgnoreCase(repository(link))&&link.contains("/releases")) { advertised=true; break; }
            }
            if(!advertised)continue;
            String title=safeText(g.optString("title"),200);
            if(title.isEmpty())continue;
            String slug=g.optString("id");
            if(!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))continue;
            Catalog.App port=new Catalog.App();
            port.id="porthub."+slug;
            // Synthetic ID only for display/favourites. Never sent to Android installer.
            port.packageName="porthub."+slug.replace('-','_');
            port.name=title; port.category="game"; port.discoveryOnly=true;
            port.publisherUrl=release;
            port.sourceName="PortHub"; port.license=g.optString("license");
            port.attribution="PortHub — https://github.com/orev7s/porthub-catalog — upstream licences apply";
            JSONArray platforms=g.optJSONArray("platforms");
            StringBuilder platform=new StringBuilder();
            if(platforms!=null)for(int k=0;k<Math.min(platforms.length(),8);k++){
                if(platform.length()>0)platform.append(" / ");
                platform.append(safeText(platforms.optString(k),40));
            }
            port.originalPlatform=platform.toString();
            String needs=a.optString("requiresGameFiles","unknown");
            if(setup!=null&&("owned".equals(setup.optString("gameFiles"))||"required".equals(setup.optString("gameFiles"))))
                needs=setup.optString("gameFiles");
            port.gameFiles=needs;
            port.summary="بورت أندرويد • "+g.optString("status","unknown");
            port.description="مشروع بورت مدرج في فهرس PortHub. افتح صفحة إصدارات المطوّر؛ لم نتحقق بعد من بصمة APK وتوقيعه، لذلك لا يُثبّت مباشرة من المتجر.";
            if(setup!=null) {
                StringBuilder instructions=new StringBuilder();
                for(String key:new String[]{"mustOwn","requiredFiles"}) {
                    String t=safeText(setup.optString(key),1500);
                    if(!t.isEmpty())instructions.append(t).append('\n');
                }
                JSONArray steps=setup.optJSONArray("steps");
                if(steps!=null)for(int k=0;k<Math.min(steps.length(),12);k++)instructions.append(safeText(steps.optString(k),800)).append('\n');
                port.filesInstructions=instructions.toString().trim();
                port.filesPath=safeText(setup.optString("installPath"),500);
            }
            try { port.updatedAt=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(g.optString("lastUpdated")).getTime(); }
            catch(Exception ignored){}
            port.version="غير متحقق";
            // PortHub links may refer to the same project many times. Keep a single entry per publisher repo.
            unique.putIfAbsent(repo.toLowerCase(Locale.ROOT),port);
        }
        return new ArrayList<>(unique.values());
    }
}
