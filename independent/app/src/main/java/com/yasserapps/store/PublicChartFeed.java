package com.yasserapps.store;

import java.net.URI;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Bounded public catalogue discovery; only real publisher metadata, never guessed APKs. */
final class PublicChartFeed {
    static boolean listingLink(String source,String url){
        try{
            URI u=new URI(url);String host=u.getHost(),path=u.getPath();
            if(!"https".equals(u.getScheme())||host==null||u.getUserInfo()!=null||u.getPort()!=-1||u.getQuery()!=null||u.getFragment()!=null)return false;
            if("Uptodown".equals(source))return host.matches("[a-z0-9][a-z0-9-]{0,100}\\.en\\.uptodown\\.com")&&"/android".equals(path);
            if("TapTap".equals(source))return "www.taptap.io".equals(host)&&path.matches("/app/[0-9]{1,12}");
        }catch(Exception ignored){}
        return false;
    }
    static boolean imageLink(String source,String url){
        try{
            URI u=new URI(url);String host=u.getHost();
            return "https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getPort()==-1&&host!=null&&
                ("Uptodown".equals(source)?host.equals("img.utdstc.com"):"TapTap".equals(source)&&host.matches("img[0-9]*\\.tapimg\\.net"));
        }catch(Exception e){return false;}
    }
    static List<String> links(String source,String html,String base){
        LinkedHashSet<String> links=new LinkedHashSet<>();
        for(Element e:Jsoup.parse(html,base).select("a[href]")){
            String href=e.absUrl("href");
            if(listingLink(source,href))links.add(href);
            if(links.size()>=200)break;
        }
        return new ArrayList<>(links);
    }
    private static JSONObject software(Object o){
        if(o instanceof JSONObject){
            JSONObject j=(JSONObject)o;
            String type=j.optString("@type");
            if(type.contains("SoftwareApplication")||type.contains("MobileApplication")||type.contains("VideoGame"))return j;
            return software(j.opt("@graph"));
        }
        if(o instanceof JSONArray){JSONArray a=(JSONArray)o;for(int i=0;i<a.length();i++){JSONObject j=software(a.opt(i));if(j!=null)return j;}}
        return null;
    }
    private static String meta(Document doc,String key){
        Element e=doc.selectFirst("meta[property="+key+"]");
        return e==null?"":e.attr("content").trim();
    }
    private static String clean(String value,int max){
        String v=Jsoup.parse(value).text().trim();
        return v.substring(0,Math.min(v.length(),max));
    }
    static Catalog.App parse(String source,String url,String html)throws Exception{
        if(!listingLink(source,url))return null;
        Document doc=Jsoup.parse(html,url);
        JSONObject data=null;
        for(Element e:doc.select("script[type=application/ld+json]")){
            try{data=software(new JSONTokener(e.data()).nextValue());if(data!=null)break;}catch(Exception ignored){}
        }
        // Actual application names come from application schema or the visible H1, not URL slugs.
        Element h=doc.selectFirst("h1");
        String name=data==null?"":data.optString("name");
        if(name.isEmpty()&&h!=null)name=h.text();
        name=clean(name,200);
        if(name.isEmpty())return null;
        Catalog.App a=new Catalog.App();a.id=source.toLowerCase(Locale.ROOT)+":"+url;a.name=name;
        a.sourceName=source;a.attribution=source;a.publisherUrl=url;a.discoveryOnly=true;
        a.category="TapTap".equals(source)?"game":"app";
        if(data!=null){
            String cat=data.optString("applicationCategory");
            if(cat.toLowerCase(Locale.ROOT).contains("game"))a.category="game";
            a.version=clean(data.optString("softwareVersion"),80);
            a.summary=clean(data.optString("description"),180);
            String img=data.optString("image");
            if(imageLink(source,img))a.iconUrl=img;
        }
        if(a.summary.isEmpty())a.summary=clean(meta(doc,"og:description"),180);
        if(a.iconUrl.isEmpty()){
            String img=meta(doc,"og:image");
            if(imageLink(source,img))a.iconUrl=img;
        }
        if(a.iconUrl.isEmpty()||a.summary.isEmpty())return null;
        // No package/version-code or byte size is invented. Unknown identity stays discovery-only.
        return a;
    }
}
