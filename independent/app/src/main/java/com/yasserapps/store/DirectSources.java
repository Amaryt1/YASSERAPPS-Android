package com.yasserapps.store;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads publisher-signed F-Droid v2 indexes directly; no GitHub app catalog. */
final class DirectSources {
    private static final long MAX_INDEX_BYTES = 256L * 1024 * 1024;
    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;
    // Only the seven owner-selected providers may be activated in this release.
    // Aptoide is the only live, verified public listing interface so far.
    private static final Source[] SOURCES = {
        new Source("aptoide", "Aptoide", "https://ws75.aptoide.com/api/7/apps/get", ""),
        new Source("apkmirror", "APKMirror", "https://www.apkmirror.com/feed/", ""),
        new Source("uptodown","Uptodown","https://en.uptodown.com/android/apps/top",""),
        new Source("taptap","TapTap","https://www.taptap.io/top/","")
    };
    static final class Source {
        final String id, name, base, fingerprint;
        Source(String id, String name, String base, String fingerprint) {
            this.id=id; this.name=name; this.base=base; this.fingerprint=fingerprint;
        }
    }
    static final class Snapshot {
        final long timestamp;
        final String hash;
        final List<Catalog.App> apps;
        Snapshot(long timestamp, String hash, List<Catalog.App> apps) {
            this.timestamp=timestamp; this.hash=hash; this.apps=apps;
        }
    }
    private static String hex(byte[] bytes) {
        StringBuilder s=new StringBuilder();
        for(byte b:bytes) s.append(String.format(Locale.ROOT,"%02x",b&255));
        return s.toString();
    }
    private static boolean hash(String value) { return value.matches("(?i)[a-f0-9]{64}"); }
    private static String url(Source source, String path) throws Exception {
        if(path.startsWith("/")) path=path.substring(1);
        if(path.isEmpty() || path.contains("\\") || path.contains("?") || path.contains("#") || path.contains(":"))
            throw new IOException("Invalid repository path");
        for(String part:path.split("/",-1))
            if(part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Invalid repository path");
        return source.base + new URI(null,null,"/"+path,null).toASCIIString();
    }
    private static void download(String address, File output, long limit, long expectedSize, String expectedHash) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(45000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control","no-cache");
            connection.setRequestProperty("User-Agent","YASSERAPPS/11.0.8");
            connection.setInstanceFollowRedirects(false);
            int status=connection.getResponseCode();
            if(status==404||status==410) throw new FileNotFoundException("HTTP "+status);
            if(status!=200) throw new IOException("HTTP "+status);
            if(connection.getContentLengthLong()>limit) throw new IOException("Index exceeds safety limit");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long total=0;
            try(InputStream in=connection.getInputStream(); OutputStream out=new BufferedOutputStream(new FileOutputStream(output))) {
                byte[] buf=new byte[65536]; int n;
                while((n=in.read(buf))!=-1) {
                    total+=n;
                    if(total>limit || (expectedSize>=0 && total>expectedSize)) throw new IOException("Unexpected index size");
                    digest.update(buf,0,n); out.write(buf,0,n);
                }
            }
            if(expectedSize>=0 && total!=expectedSize) throw new IOException("Incomplete index");
            if(expectedHash!=null && !hex(digest.digest()).equalsIgnoreCase(expectedHash)) throw new SecurityException("Index SHA-256 mismatch");
        } finally { connection.disconnect(); }
    }
    private static JSONObject verifiedEntry(File file, Source source) throws Exception {
        return verifiedJson(file,source,"entry.json",4*1024*1024);
    }
    private static JSONObject verifiedJson(File file, Source source,String name,int limit) throws Exception {
        try(JarFile jar=new JarFile(file,true)) {
            JarEntry entry=jar.getJarEntry(name);
            if(entry==null) throw new SecurityException("Missing signed entry.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=jar.getInputStream(entry)) {
                byte[] b=new byte[8192]; int n;
                while((n=in.read(b))!=-1) {
                    if(out.size()+n>limit) throw new IOException("Entry too large");
                    out.write(b,0,n);
                }
            }
            Certificate[] certs=entry.getCertificates();
            boolean trusted=false;
            if(certs!=null) for(Certificate cert:certs)
                if(hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())).equalsIgnoreCase(source.fingerprint)) trusted=true;
            if(!trusted) throw new SecurityException("Repository signing certificate mismatch");
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    private static Object readValue(JsonReader reader, int depth) throws Exception {
        if(depth>80) throw new IOException("Index nesting too deep");
        switch(reader.peek()) {
            case BEGIN_OBJECT:
                JSONObject obj=new JSONObject(); reader.beginObject();
                while(reader.hasNext()) obj.put(reader.nextName(),readValue(reader,depth+1));
                reader.endObject(); return obj;
            case BEGIN_ARRAY:
                JSONArray arr=new JSONArray(); reader.beginArray();
                while(reader.hasNext()) arr.put(readValue(reader,depth+1));
                reader.endArray(); return arr;
            case BOOLEAN: return reader.nextBoolean();
            case NULL: reader.nextNull(); return JSONObject.NULL;
            case NUMBER: return reader.nextString();
            case STRING: return reader.nextString();
            default: throw new IOException("Unexpected index token");
        }
    }
    private static Object localized(Object value) {
        if(!(value instanceof JSONObject)) return value;
        JSONObject obj=(JSONObject)value;
        for(String key:new String[]{"ar","ar-SA","en-US","en"}) if(obj.has(key)) return obj.opt(key);
        Iterator<String> keys=obj.keys();
        return keys.hasNext()?obj.opt(keys.next()):null;
    }
    private static String str(Object value) {
        Object v=localized(value);
        return v instanceof String?(String)v:"";
    }
    private static String clean(Object value, int limit) {
        String text=str(value);
        text=android.text.Html.fromHtml(text,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return text.length()>limit?text.substring(0,limit):text;
    }
    private static String media(Source source,Object value) {
        Object obj=localized(value);
        if(obj instanceof JSONObject) {
            try { return url(source,((JSONObject)obj).getString("name")); } catch(Exception ignored) {}
        }
        return "";
    }
    static boolean game(JSONObject metadata) {
        JSONArray categories=metadata.optJSONArray("categories");
        if(categories!=null) for(int i=0;i<categories.length();i++) {
            String c=categories.optString(i).trim().toLowerCase(Locale.ROOT).replace('_',' ').replace('-',' ');
            if(c.equals("game")||c.equals("games")||c.startsWith("game:")||c.startsWith("games:")||c.endsWith(" game")||c.endsWith(" games")) return true;
        }
        return false;
    }
    private static Catalog.App convert(Source source,String pkg,JSONObject item) throws Exception {
        if(!pkg.matches("[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)+")) return null;
        JSONObject meta=item.optJSONObject("metadata"), versions=item.optJSONObject("versions");
        if(meta==null||versions==null) return null;
        JSONObject selected=null; int best=0; String bestSigner="";
        String preferred=meta.optString("preferredSigner","");
        Iterator<String> keys=versions.keys();
        while(keys.hasNext()) {
            JSONObject v=versions.optJSONObject(keys.next());
            if(v==null) continue;
            JSONObject m=v.optJSONObject("manifest"), f=v.optJSONObject("file");
            if(m==null||f==null) continue;
            int code=m.optInt("versionCode",0);
            JSONObject sdk=m.optJSONObject("usesSdk");
            if(sdk!=null&&sdk.optInt("minSdkVersion",1)>Build.VERSION.SDK_INT) continue;
            JSONArray channels=v.optJSONArray("releaseChannels");
            if(channels!=null&&channels.length()>0) continue;
            String apk=f.optString("name"), digest=f.optString("sha256");
            if(code<=0||f.optLong("size",0)<=0||!apk.toLowerCase(Locale.ROOT).endsWith(".apk")||!hash(digest)) continue;
            JSONObject signer=m.optJSONObject("signer"); JSONArray fingerprints=signer==null?null:signer.optJSONArray("sha256");
            if(fingerprints==null||fingerprints.length()!=1||!hash(fingerprints.optString(0))) continue;
            String sign=fingerprints.optString(0);
            if(!preferred.isEmpty()&&!preferred.equalsIgnoreCase(sign)) continue;
            JSONArray abis=m.optJSONArray("nativecode");
            if(abis!=null&&abis.length()>0) {
                boolean compatible=false;
                for(String abi:Build.SUPPORTED_ABIS) for(int i=0;i<abis.length();i++) if(abi.equals(abis.optString(i))) compatible=true;
                if(!compatible) continue;
            }
            if(code>best) { selected=v; best=code; bestSigner=sign; }
        }
        if(selected==null) return null;
        JSONObject m=selected.getJSONObject("manifest"),f=selected.getJSONObject("file");
        Catalog.App a=new Catalog.App();
        a.id=source.id+"."+pkg; a.packageName=pkg;
        a.name=clean(meta.opt("name"),200); if(a.name.isEmpty()) a.name=pkg;
        a.category=(source.id.equals("playfdroid")||(source.id.equals("modernapps")&&a.name.startsWith("MA Games "))||game(meta))?"game":"app"; a.version=m.optString("versionName",String.valueOf(best));
        a.versionCode=best; a.sizeBytes=f.getLong("size"); a.sha256=f.getString("sha256").toLowerCase(Locale.ROOT);
        a.apkUrl=url(source,f.getString("name")); a.iconUrl=media(source,meta.opt("icon"));
        a.bannerUrl=media(source,meta.opt("featureGraphic"));
        a.summary=clean(meta.opt("summary"),500);
        a.description=clean(meta.opt("description"),20000);
        a.changelog=clean(selected.opt("whatsNew"),12000);
        a.sourceName=source.name; a.signerSha256=bestSigner;
        a.license=meta.optString("license","");
        a.updatedAt=Math.max(meta.optLong("lastUpdated",0),selected.optLong("added",0));
        JSONObject screenshots=meta.optJSONObject("screenshots");
        if(screenshots!=null) {
            Object shots=localized(screenshots.opt("phone"));
            if(shots instanceof JSONArray) for(int i=0;i<Math.min(12,((JSONArray)shots).length());i++) {
                JSONObject photo=((JSONArray)shots).optJSONObject(i);
                if(photo!=null) try { a.screenshots.add(url(source,photo.getString("name"))); } catch(Exception ignored) {}
            }
        }
        return a;
    }
    private static List<Catalog.App> parseIndex(File index,Source source) throws Exception {
        ArrayList<Catalog.App> apps=new ArrayList<>();
        boolean found=false;
        try(JsonReader reader=new JsonReader(new InputStreamReader(new FileInputStream(index),StandardCharsets.UTF_8))) {
            reader.beginObject();
            while(reader.hasNext()) {
                String key=reader.nextName();
                if(!key.equals("packages")) { reader.skipValue(); continue; }
                found=true; reader.beginObject();
                while(reader.hasNext()) {
                    String pkg=reader.nextName(); JSONObject item=(JSONObject)readValue(reader,0);
                    try { Catalog.App a=convert(source,pkg,item); if(a!=null) apps.add(a); }
                    catch(Exception ex) { android.util.Log.w("YASSERAPPS_CATALOG","Skipping invalid source entry "+pkg); }
                }
                reader.endObject();
            }
            reader.endObject();
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Trailing index data");
        }
        if(!found||apps.isEmpty()) throw new IOException("No compatible source entries");
        return apps;
    }
    private static AtomicFile cacheFile(Context context,Source source) {
        return new AtomicFile(new File(context.getFilesDir(),"direct-"+source.id+".json"));
    }
    private static Snapshot readCache(Context context,Source source) {
        try {
            AtomicFile file=cacheFile(context,source);
            if(file.getBaseFile().length()>MAX_CACHE_BYTES) return null;
            try(JsonReader r=new JsonReader(new InputStreamReader(file.openRead(),StandardCharsets.UTF_8))) {
                long time=0; int classification=0; String hash=""; List<Catalog.App> apps=new ArrayList<>();
                r.beginObject();
                while(r.hasNext()) {
                    String key=r.nextName();
                    if(key.equals("timestamp")) time=r.nextLong();
                    else if(key.equals("hash")) hash=r.nextString();
                    else if(key.equals("classification")) classification=r.nextInt();
                    else if(key.equals("apps")) {
                        r.beginArray();
                        while(r.hasNext()) apps.add(Catalog.fromJson((JSONObject)readValue(r,0)));
                        r.endArray();
                    } else r.skipValue();
                }
                r.endObject();
                if(time<=0||!hash(hash)||apps.isEmpty()) return null;
                // Preserve the old cache offline, but force a signed reparse on next connection.
                return new Snapshot(time,classification==3?hash:"0000000000000000000000000000000000000000000000000000000000000000",apps);
            }
        } catch(Exception e) { return null; }
    }
    private static void writeCache(Context context,Source source,Snapshot snapshot) throws Exception {
        AtomicFile file=cacheFile(context,source); FileOutputStream out=file.startWrite();
        try {
            BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
            writer.write("{\"classification\":3,\"timestamp\":"+snapshot.timestamp+",\"hash\":"+JSONObject.quote(snapshot.hash)+",\"apps\":[");
            boolean first=true;
            for(Catalog.App a:snapshot.apps) {
                if(!first) writer.write(','); first=false;
                writer.write(Catalog.toJson(a).toString());
            }
            writer.write("]}"); writer.flush(); file.finishWrite(out);
        } catch(Exception e) { file.failWrite(out); throw e; }
    }
    private static Snapshot legacy(Context context,Source source,File jar,Snapshot cached) throws Exception {
        JSONObject root=verifiedJson(jar,source,"index-v1.json",16*1024*1024);
        long time=root.getJSONObject("repo").getLong("timestamp");
        if(time<=0||(cached!=null&&time<cached.timestamp)) throw new SecurityException("Repository rollback");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        String h=hex(digest.digest(root.toString().getBytes(StandardCharsets.UTF_8)));
        if(cached!=null&&h.equals(cached.hash)) return cached;
        JSONArray metadata=root.getJSONArray("apps");
        JSONObject packages=root.getJSONObject("packages");
        List<Catalog.App> list=new ArrayList<>();
        for(int i=0;i<metadata.length();i++) {
            JSONObject a=metadata.getJSONObject(i);
            String pkg=a.optString("packageName");
            JSONArray releases=packages.optJSONArray(pkg);
            if(releases==null) continue;
            JSONObject meta=new JSONObject();
            JSONObject local=a.optJSONObject("localized");
            JSONObject l=(JSONObject)localized(local);
            if(l==null) l=new JSONObject();
            for(String key:new String[]{"name","summary","description"}) meta.put(key,l.optString(key,a.optString(key)));
            meta.put("categories",a.optJSONArray("categories"));
            meta.put("license",a.optString("license"));
            meta.put("lastUpdated",a.optLong("lastUpdated",0));
            String icon=a.optString("icon");
            if(!icon.isEmpty()) meta.put("icon",new JSONObject().put("en-US",new JSONObject().put("name","icons/"+icon)));
            String locale="en-US";
            if(local!=null) { for(String key:new String[]{"ar","ar-SA","en-US","en"}) if(local.has(key)){locale=key;break;} }
            JSONArray photos=l.optJSONArray("phoneScreenshots");
            if(photos!=null) {
                JSONArray shots=new JSONArray();
                for(int k=0;k<Math.min(12,photos.length());k++)
                    shots.put(new JSONObject().put("name",pkg+"/"+locale+"/phoneScreenshots/"+photos.getString(k)));
                meta.put("screenshots",new JSONObject().put("phone",new JSONObject().put(locale,shots)));
            }
            JSONObject versions=new JSONObject();
            int suggested=a.optInt("suggestedVersionCode",0);
            for(int j=0;j<releases.length();j++) {
                JSONObject r=releases.getJSONObject(j);
                if(suggested>0&&r.optInt("versionCode")>suggested) continue;
                if(!"sha256".equalsIgnoreCase(r.optString("hashType","sha256"))||!hash(r.optString("signer"))) continue;
                JSONObject m=new JSONObject().put("versionCode",r.optInt("versionCode"))
                    .put("versionName",r.optString("versionName"))
                    .put("usesSdk",new JSONObject().put("minSdkVersion",r.optInt("minSdkVersion",1)))
                    .put("signer",new JSONObject().put("sha256",new JSONArray().put(r.optString("signer"))));
                if(r.has("nativecode")) m.put("nativecode",r.get("nativecode"));
                JSONObject file=new JSONObject().put("name",r.optString("apkName")).put("sha256",r.optString("hash")).put("size",r.optLong("size"));
                versions.put(String.valueOf(j),new JSONObject().put("manifest",m).put("file",file).put("added",r.optLong("added")).put("whatsNew",l.optString("whatsNew")));
            }
            try {
                Catalog.App converted=convert(source,pkg,new JSONObject().put("metadata",meta).put("versions",versions));
                if(converted!=null) list.add(converted);
            } catch(Exception ex) { android.util.Log.w("YASSERAPPS_CATALOG","Skipping invalid legacy entry "+pkg); }
        }
        if(list.isEmpty()) throw new IOException("No compatible signed legacy entries");
        Snapshot fresh=new Snapshot(time,h,list);
        writeCache(context,source,fresh);
        return fresh;
    }

    // CC BY 4.0 discovery data, not a signed F-Droid index. Only original
    // publisher release assets matching the independently published SHA-256 are accepted.
    static Catalog.App latestAptoide(Context context,Catalog.App app) throws Exception {
        if(!"Aptoide".equals(app.sourceName)||!app.packageName.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+"))return null;
        JSONObject data=fetchJson(context,"https://ws75.aptoide.com/api/7/app/getMeta/package_name="+app.packageName,2*1024*1024);
        return AptoideFeed.latestDetail(app,data);
    }
    private static String fetchText(Context context,String address)throws Exception{
        File temp=File.createTempFile("chart-", ".html",context.getCacheDir());
        try{
            download(address,temp,4*1024*1024,-1,null);
            try(InputStream in=new FileInputStream(temp);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{temp.delete();}
    }
    static JSONObject fetchJson(Context context,String address,int limit) throws Exception {
        File file=File.createTempFile("gamefeed-", ".json",context.getCacheDir());
        try {
            download(address,file,limit,-1,null);
            try(JsonReader reader=new JsonReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))) {
                JSONObject data=(JSONObject)readValue(reader,0);
                if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON");
                return data;
            }
        } finally { file.delete(); }
    }
    private static Snapshot gameFeed(Context context,Source source,Snapshot cached) throws Exception {
        JSONObject root=fetchJson(context,source.base,4*1024*1024);
        JSONArray records=root.getJSONArray("apps");
        Map<String,Catalog.App> previous=new HashMap<>();
        if(cached!=null) for(Catalog.App a:cached.apps) previous.put(a.packageName,a);
        ArrayList<Catalog.App> result=new ArrayList<>();
        int requests=0;
        for(int i=0;i<records.length();i++) {
            JSONObject r=records.optJSONObject(i);
            if(r==null) continue;
            String pkg=r.optString("package"),digest=r.optString("sha256");
            int version=r.optInt("versionCode"); long size=r.optLong("size");
            JSONArray signers=r.optJSONArray("signers"),abis=r.optJSONArray("abis");
            if(!pkg.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")||
                !hash(digest)||size<=0||version<=0||signers==null||signers.length()!=1||
                !hash(signers.optString(0))||abis==null||r.optInt("minSdk",1000)>Build.VERSION.SDK_INT) continue;
            boolean compatible=abis.length()==0;
            for(String abi:Build.SUPPORTED_ABIS) for(int k=0;k<abis.length();k++) if(abi.equals(abis.optString(k))) compatible=true;
            if(!compatible) continue;
            String signer=signers.optString(0);
            Catalog.App old=previous.get(pkg);
            if(old!=null&&(!old.signerSha256.equalsIgnoreCase(signer)||version<=old.versionCode||digest.equalsIgnoreCase(old.sha256))) {
                result.add(old); continue;
            }
            // Main F-Droid and Izzy already supply these apps. Do not make hundreds of
            // redundant calls, or bypass the upstream's API rate limits.
            if("fdroid".equals(r.optString("source"))||"izzy".equals(r.optString("source"))) continue;
            try {
                URI page=new URI(r.optString("page"));
                if(!"https".equals(page.getScheme())||!"h5.2113.net".equalsIgnoreCase(page.getHost())||
                    page.getPort()!=-1||page.getUserInfo()!=null||page.getQuery()!=null||page.getFragment()!=null||
                    !page.getPath().matches("/(?:apps|games)/[A-Za-z0-9_-]+\\.html")) continue;
                if(++requests>40) { if(old!=null)result.add(old); continue; }
                File html=File.createTempFile("2113-", ".html", context.getCacheDir());
                String apk="";
                try {
                    download(page.toASCIIString(),html,512*1024,-1,null);
                    String body=new String(java.nio.file.Files.readAllBytes(html.toPath()),StandardCharsets.UTF_8);
                    java.util.regex.Matcher links=java.util.regex.Pattern.compile("https://h5-dl\\.2113\\.net/([A-Za-z0-9_.-]+\\.apk)").matcher(body);
                    String expected=pkg+"_"+version;
                    while(links.find()) {
                        String file=links.group(1);
                        if(file.equals(expected+"-"+digest.substring(0,8).toLowerCase(Locale.ROOT)+".apk")||
                           file.equals(expected+".apk")) { apk=links.group(0); break; }
                    }
                } finally { html.delete(); }
                if(apk.isEmpty()) { if(old!=null)result.add(old); continue; }
                Catalog.App a=new Catalog.App();
                a.id=source.id+"."+pkg; a.packageName=pkg; a.name=clean(r.opt("name"),200);
                if(a.name.isEmpty()) a.name=pkg;
                String category=r.optString("category");
                a.category=Arrays.asList("Puzzle & Arcade","Adventure & RPG","Strategy Games","Racing Games","Sandbox Games","Simulation Games","Educational Games").contains(category)?"game":"app";
                a.version=r.optString("version"); a.versionCode=version;
                a.sizeBytes=size; a.sha256=digest; a.signerSha256=signer; a.apkUrl=apk;
                a.sourceName="2113 Apps"; a.license=r.optString("license","");
                a.summary=category; a.description="ملف APK مطابق للبصمة المنشورة؛ يتم التحقق منه وتوقيعه قبل التثبيت.";
                a.attribution="2113 Apps — https://h5.2113.net/ — CC BY 4.0";
                try { a.updatedAt=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(r.getString("released")).getTime(); }
                catch(Exception ignored) {}
                // Some source ports need retail data; do not assume all games are standalone.
                if(Arrays.asList("com.github.bvschaik.julius","com.github.Keriew.augustus","io.openrct2","org.fheroes2").contains(pkg)) {
                    a.gameFiles="owned"; a.originalPlatform="PC"; a.publisherUrl=r.optString("sourceCode");
                }
                result.add(a);
            } catch(Exception ex) { if(old!=null)result.add(old); android.util.Log.w("YASSERAPPS_CATALOG","GAME_FEED_SKIP "+pkg+": "+ex); }
        }
        if(result.isEmpty()) throw new IOException("No matching 2113 APK links");
        Snapshot fresh=new Snapshot(System.currentTimeMillis(),hex(MessageDigest.getInstance("SHA-256").digest(root.toString().getBytes(StandardCharsets.UTF_8))),result);
        writeCache(context,source,fresh);
        return fresh;
    }
    private static Snapshot update(Context context,Source source,Snapshot cached,java.util.function.Consumer<List<Catalog.App>> preview) throws Exception {
        if(source.id.equals("uptodown")||source.id.equals("taptap")||source.id.equals("apkmirror")){
            long now=System.currentTimeMillis();
            final int target=2000;
            if(cached!=null&&now>=cached.timestamp&&now-cached.timestamp<(cached.apps.size()<target?15L*60*1000:6L*60*60*1000))return cached;
            Map<String,Catalog.App> unique=new LinkedHashMap<>();
            if(cached!=null)for(Catalog.App a:cached.apps)if(source.name.equals(a.sourceName))unique.put(a.id,a);
            int accepted=0,checked=0;
            if(source.id.equals("apkmirror")){
                File temp=File.createTempFile("mirror-rss-", ".xml",context.getCacheDir());
                try{
                    download(source.base,temp,2*1024*1024,-1,null);
                    try(InputStream in=new FileInputStream(temp)){
                        for(Catalog.App a:MirrorFeed.parse(in)){
                            if(unique.size()<target||unique.containsKey(a.id)){unique.put(a.id,a);accepted++;}
                        }
                    }
                }catch(Exception e){android.util.Log.w("YASSERAPPS_CATALOG","RSS_REFRESH_FAILED",e);}
                finally{temp.delete();}
            }
            String[] charts=source.id.equals("uptodown")?new String[]{
                source.base,"https://en.uptodown.com/android/games/top","https://en.uptodown.com/android/latest-updates","https://en.uptodown.com/android/newreleases"
            }:source.id.equals("apkmirror")?new String[]{"https://www.apkmirror.com/uploads/"}:new String[]{source.base};
            LinkedHashSet<String> urls=new LinkedHashSet<>();
            for(String chart:charts){
                try{urls.addAll(PublicChartFeed.links(source.name,fetchText(context,chart),chart));}
                catch(Exception e){android.util.Log.w("YASSERAPPS_CATALOG","CHART_LIST_FAILED "+source.id,e);}
                Thread.sleep(1000);
            }
            List<String> links=new ArrayList<>(urls);
            android.content.SharedPreferences p=context.getSharedPreferences("public_chart_cursor_v2",Context.MODE_PRIVATE);
            int offset=links.isEmpty()?0:Math.floorMod(p.getInt(source.id,0),links.size());
            long deadline=android.os.SystemClock.elapsedRealtime()+85000L;
            // Rotate through real chart URLs. Never fabricate app IDs or bypass blocked endpoints.
            for(int i=0;i<Math.min(40,links.size())&&android.os.SystemClock.elapsedRealtime()<deadline;i++){
                String link=links.get((offset+i)%links.size());checked++;
                try{
                    Catalog.App a=PublicChartFeed.parse(source.name,link,fetchText(context,link));
                    if(a!=null&&(unique.containsKey(a.id)||unique.size()<target)){unique.put(a.id,a);accepted++;}
                }catch(Exception e){android.util.Log.w("YASSERAPPS_CATALOG","CHART_DETAIL_FAILED "+source.id,e);break;}
                if(checked%10==0&&!unique.isEmpty())preview.accept(new ArrayList<>(unique.values()));
                Thread.sleep(1200);
            }
            if(accepted==0&&cached==null)throw new IOException("No validated public chart metadata");
            if(unique.isEmpty())throw new IOException("Empty public chart");
            List<Catalog.App> rows=new ArrayList<>(unique.values());
            Snapshot fresh=new Snapshot(now,hex(MessageDigest.getInstance("SHA-256").digest(Long.toString(now).getBytes(StandardCharsets.UTF_8))),rows);
            writeCache(context,source,fresh);
            if(!links.isEmpty())p.edit().putInt(source.id,(offset+checked)%links.size()).apply();
            android.util.Log.i("YASSERAPPS_CATALOG","CHART_IMPORT "+source.id+" available="+links.size()+" checked="+checked+" accepted="+accepted+" cached="+rows.size());
            return fresh;
        }
        if(source.id.equals("aptoide")) {
            final int target=20000;
            long now=System.currentTimeMillis();
            android.content.SharedPreferences progress=context.getSharedPreferences("market_catalog_progress_v12",Context.MODE_PRIVATE);
            if(cached!=null&&now>=cached.timestamp&&now-cached.timestamp<(cached.apps.size()<target?15L*60*1000:6L*60*60*1000))return cached;
            Map<String,Catalog.App> unique=new LinkedHashMap<>();
            if(cached!=null)for(Catalog.App app:cached.apps)if("Aptoide".equals(app.sourceName))unique.put(app.packageName,app);
            int accepted=0,attempted=0,rejected=0,listed=0,pages=0;
            long deadline=android.os.SystemClock.elapsedRealtime()+150000L;
            android.content.SharedPreferences.Editor checkpoint=progress.edit();
            String[] groups={"games","applications"};
            int[] offsets={cached==null?0:progress.getInt("games",0),cached==null?0:progress.getInt("applications",0)};
            // Retain cursors even when the target is reached, so older entries are refreshed as well.
            // Refresh popular pages, then continue where the last successful batch ended.
            // Interleave the categories, keep a request/time budget and respect upstream errors.
            for(int page=0;page<35;page++) {
                for(int g=0;g<groups.length;g++){
                    if(android.os.SystemClock.elapsedRealtime()>=deadline)break;
                    String group=groups[g];
                    int offset=page==0?0:offsets[g];
                    JSONObject data=fetchJson(context,source.base+"/group_name="+group+"/limit=50/offset="+offset+"/mature=false/sort=downloads",4*1024*1024);
                    pages++;
                    JSONObject datalist=data.getJSONObject("datalist");
                    JSONArray rows=datalist.getJSONArray("list");
                    for(Catalog.App app:AptoideFeed.discover(data,group)) {
                        Catalog.App old=unique.get(app.packageName);
                        if(old==null&&unique.size()<target||old!=null&&(app.versionCode>old.versionCode||app.versionCode==old.versionCode&&old.discoveryOnly&&old.description.isEmpty()))unique.put(app.packageName,app);
                        listed++;
                    }
                    // Remove an explicitly non-trusted listing, even when a previous version is cached.
                    for(int i=0;i<rows.length();i++){
                        JSONObject row=rows.optJSONObject(i);
                        JSONObject file=row==null?null:row.optJSONObject("file");
                        JSONObject verdict=file==null?null:file.optJSONObject("malware");
                        if(verdict!=null&&!"TRUSTED".equals(verdict.optString("rank")))unique.remove(row.optString("package"));
                    }
                    if(page==0&&rows.length()>0) {
                        JSONObject r=rows.optJSONObject(0);
                        String pkg=r==null?"":r.optString("package");
                        if(pkg.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")){
                            attempted++;
                            JSONObject detail=fetchJson(context,"https://ws75.aptoide.com/api/7/app/getMeta/package_name="+pkg,2*1024*1024);
                            Catalog.App app=AptoideFeed.checkedDetail(r,detail,group);
                            if(app!=null){accepted++;if(unique.containsKey(pkg)||unique.size()<target)unique.put(pkg,app);}
                            else{rejected++;unique.remove(pkg);}
                            Thread.sleep(500);
                        }
                    }
                    int next=datalist.optInt("next",-1);
                    if(page>0||offsets[g]==0)offsets[g]=rows.length()==0||next<=offset||next>=datalist.optInt("total",Integer.MAX_VALUE)?0:next;
                    if((page==0||page%10==0)&&!unique.isEmpty())preview.accept(new ArrayList<>(unique.values()));
                    Thread.sleep(750);
                }
                if(android.os.SystemClock.elapsedRealtime()>=deadline)break;
            }
            android.util.Log.i("YASSERAPPS_CATALOG","APTOIDE_BULK pages="+pages+" listed="+listed+" unique="+unique.size()+" attempted="+attempted+" checked="+accepted+" rejected="+rejected+" gamesOffset="+offsets[0]+" appsOffset="+offsets[1]);
            if(unique.isEmpty()||pages<2||accepted==0)throw new IOException("No Aptoide metadata or trusted detail samples");
            List<Catalog.App> entries=new ArrayList<>(unique.values());
            Snapshot fresh=new Snapshot(now,hex(MessageDigest.getInstance("SHA-256").digest(Integer.toString(entries.size()).getBytes(StandardCharsets.UTF_8))),entries);
            writeCache(context,source,fresh);
            checkpoint.putInt("games",offsets[0]).putInt("applications",offsets[1]).apply();
            return fresh;
        }
        if(source.id.equals("2113games")) return gameFeed(context,source,cached);
        if(source.id.equals("porthub")) {
            JSONObject root=fetchJson(context,source.base,24*1024*1024);
            List<Catalog.App> ports=PortFeeds.parsePortHub(root);
            if(ports.isEmpty()) throw new IOException("No supported port release listings");
            Snapshot fresh=new Snapshot(System.currentTimeMillis(),hex(MessageDigest.getInstance("SHA-256").digest(root.toString().getBytes(StandardCharsets.UTF_8))),ports);
            writeCache(context,source,fresh); return fresh;
        }
        File jar=File.createTempFile("entry-", ".jar",context.getCacheDir());
        File index=File.createTempFile("index-", ".json",context.getCacheDir());
        try {
            try { download(source.base+"/entry.jar",jar,8L*1024*1024,-1,null); }
            catch(FileNotFoundException e) {
                download(source.base+"/index-v1.jar",jar,16L*1024*1024,-1,null);
                return legacy(context,source,jar,cached);
            }
            JSONObject entry=verifiedEntry(jar,source);
            long timestamp=entry.getLong("timestamp");
            JSONObject descriptor=entry.getJSONObject("index");
            String digest=descriptor.getString("sha256"); long size=descriptor.getLong("size");
            if(!hash(digest)||size<=0||size>MAX_INDEX_BYTES||timestamp<=0) throw new SecurityException("Invalid signed index descriptor");
            if(cached!=null&&timestamp<cached.timestamp) throw new SecurityException("Repository rollback");
            if(cached!=null&&digest.equalsIgnoreCase(cached.hash)) return cached;
            download(url(source,descriptor.getString("name")),index,MAX_INDEX_BYTES,size,digest);
            Snapshot fresh=new Snapshot(timestamp,digest,parseIndex(index,source));
            try { writeCache(context,source,fresh); }
            catch(Exception e) { android.util.Log.w("YASSERAPPS_CATALOG","Local cache unavailable",e); }
            return fresh;
        } finally { jar.delete(); index.delete(); }
    }
    // Test-only callers provide real publisher files downloaded by the CI runner.
    static List<Catalog.App> verifyFixture(Context context, String id, File jar, File index) throws Exception {
        Source source=null;
        for(Source s:SOURCES) if(s.id.equals(id)) source=s;
        if(source==null) throw new IllegalArgumentException("Unknown source");
        if(!index.exists()) return legacy(context,source,jar,null).apps;
        JSONObject entry=verifiedEntry(jar,source);
        JSONObject descriptor=entry.getJSONObject("index");
        if(index.length()!=descriptor.getLong("size")) throw new SecurityException("Size mismatch");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(index)) {
            byte[] bytes=new byte[65536]; int n;
            while((n=input.read(bytes))!=-1) digest.update(bytes,0,n);
        }
        if(!hex(digest.digest()).equalsIgnoreCase(descriptor.getString("sha256"))) throw new SecurityException("Index hash mismatch");
        List<Catalog.App> apps=parseIndex(index,source);
        writeCache(context,source,new Snapshot(entry.getLong("timestamp"),descriptor.getString("sha256"),apps));
        return apps;
    }
    private static List<Catalog.App> merge(Map<String,Snapshot> snapshots) {
        Map<String,Catalog.App> unique=new LinkedHashMap<>();
        for(Source source:SOURCES) {
            Snapshot snap=snapshots.get(source.id);
            if(snap==null) continue;
            for(Catalog.App a:snap.apps) {
                String key=a.packageName.isEmpty()?a.id:a.packageName;
                Catalog.App old=unique.get(key);
                if(old==null||(old.discoveryOnly&&!a.discoveryOnly)||((old.discoveryOnly==a.discoveryOnly)&&old.signerSha256.equalsIgnoreCase(a.signerSha256)&&a.versionCode>old.versionCode))
                    unique.put(key,a);
            }
        }
        List<Catalog.App> result=new ArrayList<>(unique.values());
        result.sort(Popularity.ORDER);
        // Keep the existing branded hero and choose a real game with artwork if available.
        for(Catalog.App a:result) a.featured=false;
        for(Catalog.App a:result) if(a.isGame()&&(!a.bannerUrl.isEmpty()||!a.screenshots.isEmpty())) { a.featured=true; break; }
        return result;
    }
    private static Map<String,Snapshot> serverSnapshots(Context context,InputStream stream)throws Exception{
        Map<String,Snapshot> result=new LinkedHashMap<>();
        Map<String,List<Catalog.App>> grouped=new LinkedHashMap<>();
        long timestamp=0;int schema=0,count=0;
        try(JsonReader r=new JsonReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            r.beginObject();
            while(r.hasNext()){
                String key=r.nextName();
                if(key.equals("schemaVersion"))schema=r.nextInt();
                else if(key.equals("generatedAt"))timestamp=r.nextLong();
                else if(key.equals("apps")){
                    r.beginArray();
                    while(r.hasNext()){
                        if(++count>50000)throw new IOException("Server catalogue limit");
                        Catalog.App a=Catalog.fromJson((JSONObject)readValue(r,0));
                        // A public catalogue only supplies listings. Resolve APKs from the official source on demand.
                        if(!a.discoveryOnly||!a.apkUrl.isEmpty())throw new SecurityException("Unexpected server download");
                        for(Source source:SOURCES)if(source.name.equals(a.sourceName)){
                            grouped.computeIfAbsent(source.id,k->new ArrayList<>()).add(a);break;
                        }
                    }
                    r.endArray();
                }else r.skipValue();
            }
            r.endObject();
            if(r.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing catalogue data");
        }
        if(schema!=1||timestamp<=0||timestamp>System.currentTimeMillis()+86400000L||grouped.isEmpty())throw new IOException("Invalid server catalogue");
        String digest=hex(MessageDigest.getInstance("SHA-256").digest(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8)));
        for(Map.Entry<String,List<Catalog.App>> e:grouped.entrySet())result.put(e.getKey(),new Snapshot(timestamp,digest,e.getValue()));
        return result;
    }
    private static void mergeServer(Map<String,Snapshot> snapshots,Map<String,Snapshot> remote){
        for(Source source:SOURCES){
            Snapshot incoming=remote.get(source.id);
            if(incoming==null)continue;
            Snapshot local=snapshots.get(source.id);
            if(local!=null&&incoming.timestamp<local.timestamp)continue;
            Map<String,Catalog.App> rows=new LinkedHashMap<>();
            Map<String,Catalog.App> previous=new LinkedHashMap<>();
            if(local!=null)for(Catalog.App a:local.apps)previous.put(a.id,a);
            for(Catalog.App a:incoming.apps){
                Catalog.App old=previous.get(a.id);
                if(old!=null&&old.versionCode==a.versionCode&&!old.description.isEmpty()&&a.description.isEmpty()){
                    // Keep display metadata, but never retain stale direct-download eligibility.
                    a.description=old.description;a.summary=old.summary;a.screenshots.addAll(old.screenshots);
                }
                rows.put(a.id,a);
            }
            snapshots.put(source.id,new Snapshot(incoming.timestamp,incoming.hash,new ArrayList<>(rows.values())));
        }
    }
    private static boolean loadServer(Context context,Map<String,Snapshot> snapshots,Catalog.Callback callback){
        boolean available=false;
        try(InputStream in=context.getAssets().open("server-catalog.json")){
            mergeServer(snapshots,serverSnapshots(context,in));
            available=true;
            callback.loaded(new Catalog.Result(merge(snapshots),null,false));
        }catch(Exception e){android.util.Log.i("YASSERAPPS_CATALOG","SERVER_SEED_UNAVAILABLE");}
        try{
            // The public API manifest is a cheap freshness check. Full snapshot
            // remains the compatible bulk transport and avoids dozens of page requests.
            final String base="https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/";
            try {
                JSONObject manifest=fetchJson(context,base+"api/v1/index.json",128*1024);
                long generation=manifest.getLong("generatedAt");
                JSONObject counts=manifest.getJSONObject("counts");
                boolean current=manifest.getInt("schemaVersion")==1 && manifest.optBoolean("metadataOnly") && generation>0;
                int total=0;
                for(Source source:SOURCES){
                    int expected=counts.optInt(source.name,0);
                    Snapshot local=snapshots.get(source.id);
                    total+=expected;
                    if(expected==0){if(local!=null&&!local.apps.isEmpty())current=false;}
                    else if(local==null||local.timestamp!=generation||local.apps.size()!=expected)current=false;
                }
                if(current&&total>0&&total==manifest.getInt("total")){
                    android.util.Log.i("YASSERAPPS_CATALOG","API_CATALOG_CURRENT total="+total);
                    return true;
                }
            }catch(Exception ex){android.util.Log.i("YASSERAPPS_CATALOG","API_MANIFEST_UNAVAILABLE");}
            // Stream the bounded complete snapshot from a temporary file rather
            // than constructing a second in-memory JSONObject and JSON string.
            Map<String,Snapshot> remote;
            File full=File.createTempFile("server-catalog-", ".json",context.getCacheDir());
            try {
                try { download(base+"generated/full-index.json",full,64L*1024*1024,-1,null); }
                catch(FileNotFoundException missing) { download(base+"generated/index.json",full,25L*1024*1024,-1,null); }
                try(InputStream in=new BufferedInputStream(new FileInputStream(full))) {
                    remote=serverSnapshots(context,in);
                }
            } finally { full.delete(); }
            mergeServer(snapshots,remote);
            for(Source source:SOURCES){
                Snapshot snap=snapshots.get(source.id);
                if(snap!=null)writeCache(context,source,snap);
            }
            available=true;
            android.util.Log.i("YASSERAPPS_CATALOG","SERVER_CATALOG total="+merge(snapshots).size()+" sources="+snapshots.size());
        }catch(Exception e){android.util.Log.w("YASSERAPPS_CATALOG","SERVER_CATALOG_UNAVAILABLE",e);}
        return available;
    }
    static void load(Context context,Catalog.Callback callback) {
        Map<String,Snapshot> snapshots=new LinkedHashMap<>();
        for(Source source:SOURCES) {
            Snapshot cached=readCache(context,source);
            if(cached!=null) snapshots.put(source.id,cached);
        }
        if(!snapshots.isEmpty()) callback.loaded(new Catalog.Result(merge(snapshots),null,false));
        if(loadServer(context,snapshots,callback)){
            List<Catalog.App> combined=merge(snapshots);
            int downloadable=0;for(Catalog.App a:combined)if(!a.discoveryOnly)downloadable++;
            for(Source source:SOURCES){Snapshot snap=snapshots.get(source.id);if(snap!=null)android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_SOURCE_OK "+source.id+" count="+snap.apps.size());}
            android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_CATALOG total="+combined.size()+" sources="+snapshots.size()+" downloadable="+downloadable+" discoveryPorts=0 marketplaceListings="+(combined.size()-downloadable));
            callback.loaded(new Catalog.Result(combined,null,true));return;
        }
        int succeeded=0;
        for(Source source:SOURCES) {
            try {
                Snapshot snapshot=update(context,source,snapshots.get(source.id),partial -> {
                    Map<String,Snapshot> view=new LinkedHashMap<>(snapshots);
                    view.put(source.id,new Snapshot(System.currentTimeMillis(),"",partial));
                    android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_PREVIEW "+source.id+" count="+partial.size());
                    callback.loaded(new Catalog.Result(merge(view),null,false));
                });
                snapshots.put(source.id,snapshot); succeeded++;
                android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_SOURCE_OK "+source.id+" count="+snapshot.apps.size());
                callback.loaded(new Catalog.Result(merge(snapshots),null,false));
            } catch(Exception e) {
                android.util.Log.w("YASSERAPPS_CATALOG","DIRECT_SOURCE_FAILED "+source.id+": "+e,e);
            }
        }
        List<Catalog.App> combined=merge(snapshots);
        String warning=succeeded==SOURCES.length?null:"تعذّر الاتصال ببعض المصادر، نعرض آخر محتوى متوفر.";
        int downloadable=0, ports=0, marketplace=0; for(Catalog.App a:combined) { if(!a.discoveryOnly) downloadable++; else if("PortHub".equals(a.sourceName))ports++; else marketplace++; }
        android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_CATALOG total="+combined.size()+" sources="+succeeded+" downloadable="+downloadable+" discoveryPorts="+ports+" marketplaceListings="+marketplace);
        callback.loaded(new Catalog.Result(combined,warning,true));
    }
}
