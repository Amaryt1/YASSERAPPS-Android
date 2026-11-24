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
            "3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A")
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
            connection.setRequestProperty("User-Agent","YASSERAPPS/10.3");
            connection.setInstanceFollowRedirects(false);
            int status=connection.getResponseCode();
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
        try(JarFile jar=new JarFile(file,true)) {
            JarEntry entry=jar.getJarEntry("entry.json");
            if(entry==null) throw new SecurityException("Missing signed entry.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=jar.getInputStream(entry)) {
                byte[] b=new byte[8192]; int n;
                while((n=in.read(b))!=-1) {
                    if(out.size()+n>4*1024*1024) throw new IOException("Entry too large");
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
    private static boolean game(JSONObject metadata) {
        JSONArray categories=metadata.optJSONArray("categories");
        if(categories!=null) for(int i=0;i<categories.length();i++) {
            String c=categories.optString(i).toLowerCase(Locale.ROOT);
            if(c.equals("game")||c.equals("games")||c.startsWith("game:")||c.startsWith("games:")) return true;
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
        a.category=game(meta)?"game":"app"; a.version=m.optString("versionName",String.valueOf(best));
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
                long time=0; String hash=""; List<Catalog.App> apps=new ArrayList<>();
                r.beginObject();
                while(r.hasNext()) {
                    String key=r.nextName();
                    if(key.equals("timestamp")) time=r.nextLong();
                    else if(key.equals("hash")) hash=r.nextString();
                    else if(key.equals("apps")) {
                        r.beginArray();
                        while(r.hasNext()) apps.add(Catalog.fromJson((JSONObject)readValue(r,0)));
                        r.endArray();
                    } else r.skipValue();
                }
                r.endObject();
                if(time<=0||!hash(hash)||apps.isEmpty()) return null;
                return new Snapshot(time,hash,apps);
            }
        } catch(Exception e) { return null; }
    }
    private static void writeCache(Context context,Source source,Snapshot snapshot) throws Exception {
        AtomicFile file=cacheFile(context,source); FileOutputStream out=file.startWrite();
        try {
            BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
            writer.write("{\"timestamp\":"+snapshot.timestamp+",\"hash\":"+JSONObject.quote(snapshot.hash)+",\"apps\":[");
            boolean first=true;
            for(Catalog.App a:snapshot.apps) {
                if(!first) writer.write(','); first=false;
                writer.write(Catalog.toJson(a).toString());
            }
            writer.write("]}"); writer.flush(); file.finishWrite(out);
        } catch(Exception e) { file.failWrite(out); throw e; }
    }
    private static Snapshot update(Context context,Source source,Snapshot cached) throws Exception {
        File jar=File.createTempFile("entry-", ".jar",context.getCacheDir());
        File index=File.createTempFile("index-", ".json",context.getCacheDir());
        try {
            download(source.base+"/entry.jar",jar,8L*1024*1024,-1,null);
            JSONObject entry=verifiedEntry(jar,source);
            long timestamp=entry.getLong("timestamp");
            JSONObject descriptor=entry.getJSONObject("index");
            String digest=descriptor.getString("sha256"); long size=descriptor.getLong("size");
            if(!hash(digest)||size<=0||size>MAX_INDEX_BYTES||timestamp<=0) throw new SecurityException("Invalid signed index descriptor");
            if(cached!=null&&timestamp<cached.timestamp) throw new SecurityException("Repository rollback");
            if(cached!=null&&digest.equalsIgnoreCase(cached.hash)) return cached;
            download(url(source,descriptor.getString("name")),index,MAX_INDEX_BYTES,size,digest);
            Snapshot fresh=new Snapshot(timestamp,digest,parseIndex(index,source));
            writeCache(context,source,fresh);
            return fresh;
        } finally { jar.delete(); index.delete(); }
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
        android.util.Log.i("YASSERAPPS_CATALOG","DIRECT_CATALOG total="+combined.size()+" sources="+succeeded);
        callback.loaded(new Catalog.Result(combined,warning,true));
    }
}
