package com.yasserapps.store;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Bounded pagination for the documented Aptoide partner catalog.
 * Metadata only: a listing is not a verified APK or a distribution grant.
 * A future authenticated transport must supply the approved partner store.
 */
final class LargeCatalogPager {
    interface PageProvider { JSONObject page(String category,int offset,int limit) throws Exception; }
    static final class Listing {
        final String pkg,name,version,icon,category;
        final long versionCode,size;
        Listing(JSONObject row,String category) {
            JSONObject file=row.optJSONObject("file");
            pkg=row.optString("package"); name=row.optString("name");
            version=file==null?"":file.optString("vername");
            versionCode=file==null?0:file.optLong("vercode");
            size=file==null?0:file.optLong("filesize",row.optLong("size"));
            icon=row.optString("icon");
            this.category=category;
        }
    }
    static List<Listing> collect(PageProvider transport,String category,int maxPages,int limit) throws Exception {
        if(maxPages<1||maxPages>100||limit<1||limit>100)throw new IllegalArgumentException("Pagination bounds");
        LinkedHashMap<String,Listing> unique=new LinkedHashMap<>();
        int offset=0;
        for(int p=0;p<maxPages;p++) {
            JSONObject root=transport.page(category,offset,limit);
            JSONObject info=root.optJSONObject("info");
            if(info==null||!"OK".equals(info.optString("status")))throw new java.io.IOException("Partner API did not return OK");
            JSONObject data=root.optJSONObject("datalist");
            if(data==null)throw new java.io.IOException("Missing partner data list");
            JSONArray rows=data.optJSONArray("list");
            if(rows==null)throw new java.io.IOException("Missing partner rows");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i);
                if(row==null)continue;
                JSONObject file=row.optJSONObject("file");
                JSONObject malware=file==null?null:file.optJSONObject("malware");
                if(malware==null||!"TRUSTED".equals(malware.optString("rank")))continue;
                Listing a=new Listing(row,category);
                if(!a.pkg.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")||a.name.isEmpty()||a.versionCode<1||a.size<1)continue;
                Listing previous=unique.get(a.pkg);
                if(previous==null||a.versionCode>previous.versionCode)unique.put(a.pkg,a);
            }
            int next=data.optInt("next",-1);
            if(rows.length()==0||next<=offset||next>=data.optInt("total",Integer.MAX_VALUE))break;
            offset=next;
        }
        return new ArrayList<>(unique.values());
    }
}
