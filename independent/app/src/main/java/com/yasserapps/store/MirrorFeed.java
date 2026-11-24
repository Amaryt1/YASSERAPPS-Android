package com.yasserapps.store;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/** Public release announcements. No guessed package IDs, download links, or version codes. */
final class MirrorFeed {
    static boolean listingLink(String value) {
        try {
            URI u=new URI(value);
            String host=u.getHost();
            String path=u.getPath();
            return "https".equals(u.getScheme())&&("www.apkmirror.com".equals(host)||"apkmirror.com".equals(host))
                &&u.getPort()==-1&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null
                &&path!=null&&path.matches("/apk/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_.-]+)?/?");
        }catch(Exception e){return false;}
    }
    static boolean safeImage(String value){
        try{
            URI u=new URI(value);String host=u.getHost();
            return "https".equals(u.getScheme())&&host!=null&&(host.equals("apkmirror.com")||host.endsWith(".apkmirror.com"))
                &&u.getPort()==-1&&u.getUserInfo()==null;
        }catch(Exception e){return false;}
    }
    static List<Catalog.App> parse(InputStream in)throws Exception{
        XmlPullParser p=Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        p.setInput(in,"UTF-8");
        Map<String,Catalog.App> result=new LinkedHashMap<>();
        String title="",link="",description="",date="",icon="";
        int depth=-1;
        for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.nextToken()){
            if(event==XmlPullParser.DOCDECL)throw new SecurityException("DOCTYPE rejected");
            if(event==XmlPullParser.START_TAG){
                String name=p.getName();
                if("item".equals(name)){depth=p.getDepth();title=link=description=date=icon="";}
                else if(depth>0&&p.getDepth()==depth+1){
                    if("title".equals(name))title=p.nextText();
                    else if("link".equals(name))link=p.nextText();
                    else if("description".equals(name))description=p.nextText();
                    else if("pubDate".equals(name))date=p.nextText();
                    else if("thumbnail".equals(name)||"enclosure".equals(name)||"content".equals(name)){
                        String url=p.getAttributeValue(null,"url");
                        if(url!=null&&safeImage(url)&&("thumbnail".equals(name)||url.matches("(?i).+\\.(png|jpg|jpeg|webp)(\\?.*)?")))icon=url;
                    }
                }
            }else if(event==XmlPullParser.END_TAG&&depth==p.getDepth()&&"item".equals(p.getName())){
                depth=-1;
                if(!listingLink(link.trim())||title.trim().isEmpty())continue;
                URI u=new URI(link.trim());String[] parts=u.getPath().split("/");
                if(parts.length<4)continue;
                String appPath="/apk/"+parts[2]+"/"+parts[3]+"/";
                String key="apkmirror:"+appPath;
                Catalog.App a=new Catalog.App();a.id=key;
                a.name=title.trim().substring(0,Math.min(title.trim().length(),200));
                a.packageName="";a.sourceName="APKMirror";a.discoveryOnly=true;a.publisherUrl=link.trim();
                a.summary="APKMirror • إعلان إصدار من المصدر";a.attribution="APKMirror";
                a.description=android.text.Html.fromHtml(description,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                if(a.description.length()>4000)a.description=a.description.substring(0,4000);
                a.iconUrl=icon;
                try{a.updatedAt=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",Locale.US).parse(date).getTime();}catch(Exception ignored){}
                Catalog.App old=result.get(key);
                if(old==null||a.updatedAt>old.updatedAt)result.put(key,a);
                if(result.size()>=100)break;
            }
        }
        return new ArrayList<>(result.values());
    }
}
