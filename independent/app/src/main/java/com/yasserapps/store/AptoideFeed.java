package com.yasserapps.store;

import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URI;
import java.util.*;
import java.text.SimpleDateFormat;

/** Official REST catalogue; metadata listings are not a grant to redistribute an APK.
 * No credentials, app impersonation, or private endpoints. Never weakens SHA-256 verification.
 */
final class AptoideFeed {
    static boolean listingLink(String value) {
        try {
            URI u=new URI(value);
            String host=u.getHost();
            return "https".equals(u.getScheme())&&host!=null&&host.matches("[a-z0-9][a-z0-9-]{0,62}\\.en\\.aptoide\\.com")&&
                u.getPort()==-1&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&"/app".equals(u.getPath());
        } catch(Exception e) { return false; }
    }
    static boolean safeImage(String value) {
        try {
            URI u=new URI(value);
            return "https".equals(u.getScheme())&&"pool.img.aptoide.com".equalsIgnoreCase(u.getHost())&&
                u.getUserInfo()==null&&u.getPort()==-1;
        } catch(Exception e){return false;}
    }
    static boolean sameRelease(JSONObject listing,JSONObject detail) {
        if(listing==null||detail==null)return false;
        String pkg=listing.optString("package");
        if(!pkg.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")||!pkg.equals(detail.optString("package")))return false;
        JSONObject a=listing.optJSONObject("file"),b=detail.optJSONObject("file");
        if(a==null||b==null||a.optInt("vercode")<1||a.optInt("vercode")!=b.optInt("vercode"))return false;
        String md5=a.optString("md5sum");
        return md5.matches("(?i)[a-f0-9]{32}")&&md5.equalsIgnoreCase(b.optString("md5sum"));
    }
    static Catalog.App checkedDetail(JSONObject listing,JSONObject response,String category) throws Exception {
        if(!"OK".equals(response.getJSONObject("info").optString("status")))return null;
        JSONObject detail=response.optJSONObject("data");
        if(!sameRelease(listing,detail))return null;
        JSONObject wrapper=new JSONObject().put("info",new JSONObject().put("status","OK"))
            .put("datalist",new JSONObject().put("list",new JSONArray().put(detail)));
        List<Catalog.App> parsed=parse(wrapper,category);
        if(parsed.isEmpty())return null;
        Catalog.App a=parsed.get(0);
        JSONObject media=detail.optJSONObject("media");
        if(media!=null) {
            String desc=media.optString("description");
            if(!desc.isEmpty()) {
                desc=android.text.Html.fromHtml(desc,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                if(desc.length()>12000)desc=desc.substring(0,12000);
                a.description=desc+"\n\n"+a.description;
            }
            JSONArray shots=media.optJSONArray("screenshots");
            if(shots!=null) for(int i=0;i<Math.min(shots.length(),12);i++) {
                JSONObject shot=shots.optJSONObject(i);
                if(shot!=null&&safeImage(shot.optString("url")))a.screenshots.add(shot.optString("url"));
            }
        }
        return a;
    }
    static List<Catalog.App> parse(JSONObject root,String category) throws Exception {
        if(!"OK".equals(root.getJSONObject("info").optString("status")))throw new java.io.IOException("Public API status not OK");
        JSONArray rows=root.getJSONObject("datalist").getJSONArray("list");
        ArrayList<Catalog.App> result=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r==null)continue;
            JSONObject f=r.optJSONObject("file");
            if(f==null)continue;
            JSONObject malware=f.optJSONObject("malware");
            if(malware==null||!"TRUSTED".equals(malware.optString("rank")))continue;
            String pkg=r.optString("package"),name=r.optString("name"),slug=r.optString("uname");
            int code=f.optInt("vercode");
            long size=f.optLong("filesize",r.optLong("size"));
            String link="https://"+slug+".en.aptoide.com/app";
            if(!pkg.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")||name.isEmpty()||name.length()>200||code<1||size<1||!listingLink(link))continue;
            Catalog.App a=new Catalog.App();
            a.id="aptoide."+pkg; a.packageName=pkg; a.name=name;
            a.category="games".equals(category)?"game":"app"; a.versionCode=code; a.version=f.optString("vername");
            a.sizeBytes=size; a.sourceName="Aptoide"; a.discoveryOnly=true;
            a.publisherUrl=link; a.attribution="Aptoide";
            a.summary="صفحة التطبيق في Aptoide";
            a.description="بيانات من واجهة Aptoide الرسمية. افتح صفحة المصدر لمعرفة التوافق والتنزيل. لم يتم التحقق من APK ببصمة SHA-256 داخل المتجر.";
            String icon=r.optString("icon"),graphic=r.optString("graphic");
            if(safeImage(icon))a.iconUrl=icon;
            if(safeImage(graphic))a.bannerUrl=graphic;
            try {SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT);sdf.setLenient(false);sdf.setTimeZone(TimeZone.getTimeZone("UTC"));a.updatedAt=sdf.parse(r.optString("updated")).getTime();}catch(Exception ignored){}
            result.add(a);
        }
        return result;
    }
}
