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
    private static final Source[] SOURCES = {
        new Source("fdroid", "F-Droid", "https://f-droid.org/repo",
            "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"),
        new Source("izzyondroid", "IzzyOnDroid", "https://apt.izzysoft.de/fdroid/repo",
            "3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A"),
        new Source("guardian", "Guardian Project", "https://guardianproject.info/fdroid/repo",
            "B7C2EEFD8DAC7806AF67DFCD92EB18126BC08312A7F2D6F3862E46013C7A6135"),
        new Source("microg", "microG", "https://repo.microg.org/fdroid/repo",
            "9BD06727E62796C0130EB6DAB39B73157451582CBD138E86C468ACC395D14165"),
        new Source("playfdroid", "PlayFDroid", "https://raw.githubusercontent.com/PlayFDroid/fdroid-repo/gh-pages/fdroid/repo", "077873B95749B78FA46971CC117F6F6086639D785208F0DDF3DA2143CB1E8DDD"),
        new Source("kde", "KDE Stable", "https://cdn.kde.org/android/stable-releases/fdroid/repo", "13784BA6C80FF4E2181E55C56F961EED5844CEA16870D3B38D58780B85E1158F"),
        new Source("newpipe", "NewPipe", "https://archive.newpipe.net/fdroid/repo", "E2402C78F9B97C6C89E97DB914A2751FDA1D02FE2039CC0897A462BDB57E7501"),
        new Source("collabora", "Collabora Office", "https://www.collaboraoffice.com/downloads/fdroid/repo", "573258C84E149B5F4D9299E7434B2B69A8410372921D4AE586BA91EC767892CC"),
        new Source("bitwarden", "Bitwarden", "https://mobileapp.bitwarden.com/fdroid/repo", "BC54EA6FD1CD5175BCCCC47C561C5726E1C3ED7E686B6DB4B18BAC843A3EFE6C"),
        new Source("molly", "Molly", "https://molly.im/fdroid/foss/fdroid/repo", "5198DAEF37FC23C14D5EE32305B2AF45787BD7DF2034DE33AD302BDB3446DF74"),
        new Source("session", "Session", "https://fdroid.getsession.org/fdroid/repo", "DB0E5297EB65CC22D6BD93C869943BDCFCB6A07DC69A48A0DD8C7BA698EC04E6"),
        new Source("simplex", "SimpleX", "https://app.simplex.chat/fdroid/repo", "9F358FF284D1F71656A2BFAF0E005DEAE6AA14143720E089F11FF2DDCFEB01BA"),
        new Source("wind", "Wind Offline", "https://guardianproject-wind.s3.amazonaws.com/fdroid/repo", "182CF464D219D340DA443C62155198E399FEC1BC4379309B775DD9FC97ED97E1"),
        new Source("modernapps", "Modern Apps", "https://maproj.org/fdroid/repo", "176FCB2525573E5BE8E1CB3A496DD97B137E81CA5B887A1D32CB894B4E5717B4"),
        new Source("rmgames", "rm's F-Droid repo", "https://hunke.ws/fdroid/repo", "80438B253C257BCCE05CDCB9E3AC9B6174C2250659962B14FCBE7F32FD42D53E"),
        new Source("2113games", "2113 Apps", "https://raw.githubusercontent.com/2113apps/verified-android-apps/main/apps.json", ""),
        new Source("porthub", "PortHub", "https://raw.githubusercontent.com/orev7s/porthub-catalog/main/games.json", "")
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
            connection.setRequestProperty("User-Agent","YASSERAPPS/11.0");
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
    private static JSONObject fetchJson(Context context,String address,int limit) throws Exception {
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
    private static Snapshot update(Context context,Source source,Snapshot cached) throws Exception {
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
                Catalog.App old=unique.get(a.packageName);
                if(old==null||(old.signerSha256.equalsIgnoreCase(a.signerSha256)&&a.versionCode>old.versionCode))
                    unique.put(a.packageName,a);
            }
        }
        List<Catalog.App> result=new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong((Catalog.App a)->a.updatedAt).reversed().thenComparing(a->a.name,String.CASE_INSENSITIVE_ORDER));
        // Keep the existing branded hero and choose a real game with artwork if available.
        for(Catalog.App a:result) a.featured=false;
        for(Catalog.App a:result) if(a.isGame()&&(!a.bannerUrl.isEmpty()||!a.screenshots.isEmpty())) { a.featured=true; break; }
        return result;
    }
    static void load(Context context,Catalog.Callback callback) {
        Map<String,Snapshot> snapshots=new LinkedHashMap<>();
        for(Source source:SOURCES) {
            Snapshot cached=readCache(context,source);
            if(cached!=null) snapshots.put(source.id,cached);
        }
        if(!snapshots.isEmpty()) callback.loaded(new Catalog.Result(merge(snapshots),null,false));
        int succeeded=0;
        for(Source source:SOURCES) {
            try {
                Snapshot snapshot=update(context,source,snapshots.get(source.id));
                snapshots.put(source.id,snapshot); succeeded++;
                android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_SOURCE_OK "+source.id+" count="+snapshot.apps.size());
                callback.loaded(new Catalog.Result(merge(snapshots),null,false));
            } catch(Exception e) {
                android.util.Log.w("YASSERAPPS_CATALOG","DIRECT_SOURCE_FAILED "+source.id+": "+e,e);
            }
        }
        List<Catalog.App> combined=merge(snapshots);
        String warning=succeeded==SOURCES.length?null:"تعذّر الاتصال ببعض المصادر، نعرض آخر محتوى متوفر.";
        int downloadable=0, ports=0; for(Catalog.App a:combined) { if(a.discoveryOnly) ports++; else downloadable++; }
        android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_CATALOG total="+combined.size()+" sources="+succeeded+" downloadable="+downloadable+" discoveryPorts="+ports);
        callback.loaded(new Catalog.Result(combined,warning,true));
    }
}
