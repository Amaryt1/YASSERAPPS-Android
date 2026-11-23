package com.yasserapps.store;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Original persistent DownloadManager coordinator for YASSERAPPS.
 * File downloads survive process death because the OS owns the transfer;
 * metadata survives process death in private SharedPreferences.
 * Installs always require user confirmation in Android's package installer.
 */
public final class DownloadCoordinator {
    private static final String PREFERENCES = "yasser_download_records_v1";
    private static final String RECORDS = "records";
    private static final String WIFI_ONLY = "wifi_only";
    private static final int MAX_HISTORY = 100;
    private static final ExecutorService VERIFY = Executors.newSingleThreadExecutor();

    public static class Entry {
        public long id;
        public String appId = "";
        public String name = "";
        public String packageName = "";
        public String version = "";
        public int versionCode;
        public String url = "";
        public String sha256 = "";
        public String fileName = "";
        public long createdAt;
        public int status;
        public int reason;
        public long bytesDone;
        public long bytesTotal;
        public boolean missing;
        public boolean verifying;

        public int percent() {
            if (bytesTotal <= 0) return -1;
            return (int) Math.min(100L, Math.max(0L, (bytesDone * 100L) / bytesTotal));
        }
        public boolean complete() { return status == DownloadManager.STATUS_SUCCESSFUL && !missing; }
        public boolean failed() { return status == DownloadManager.STATUS_FAILED || missing; }
        public boolean active() {
            return status == DownloadManager.STATUS_PENDING ||
                   status == DownloadManager.STATUS_PAUSED ||
                   status == DownloadManager.STATUS_RUNNING;
        }
        public String message() {
            if (missing) return "الملف غير موجود، أعد التحميل";
            if (status == DownloadManager.STATUS_PENDING) return "بانتظار بدء التحميل";
            if (status == DownloadManager.STATUS_RUNNING) return "جاري التحميل";
            if (status == DownloadManager.STATUS_PAUSED) return "متوقف مؤقتًا بواسطة النظام أو الشبكة";
            if (status == DownloadManager.STATUS_SUCCESSFUL) return "مكتمل، جاهز لفحص الملف والتثبيت";
            if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) return "فشل التحميل: المساحة غير كافية";
            if (reason == DownloadManager.ERROR_CANNOT_RESUME) return "تعذر استكمال التحميل، جرّب مجددًا";
            if (reason == DownloadManager.ERROR_HTTP_DATA_ERROR) return "فشل الاتصال بالخادم";
            if (status == DownloadManager.STATUS_FAILED) return "فشل التحميل (الرمز " + reason + ")";
            return "حالة غير معروفة";
        }

        JSONObject serialize() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("appId", appId);
            o.put("name", name);
            o.put("packageName", packageName);
            o.put("version", version);
            o.put("versionCode", versionCode);
            o.put("url", url);
            o.put("sha256", sha256);
            o.put("fileName", fileName);
            o.put("createdAt", createdAt);
            return o;
        }
        static Entry deserialize(JSONObject o) {
            Entry e = new Entry();
            e.id = o.optLong("id");
            e.appId = o.optString("appId");
            e.name = o.optString("name");
            e.packageName = o.optString("packageName");
            e.version = o.optString("version");
            e.versionCode = o.optInt("versionCode");
            e.url = o.optString("url");
            e.sha256 = o.optString("sha256");
            e.fileName = o.optString("fileName");
            e.createdAt = o.optLong("createdAt");
            return e;
        }
    }

    public interface Checked {
        void done(boolean success, String message, Uri contentUri);
    }

    private final Context app;
    private final SharedPreferences preferences;
    private final DownloadManager manager;

    public DownloadCoordinator(Context context) {
        app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        manager = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public boolean wifiOnly() { return preferences.getBoolean(WIFI_ONLY, false); }
    public void setWifiOnly(boolean enabled) { preferences.edit().putBoolean(WIFI_ONLY, enabled).apply(); }

    private synchronized ArrayList<Entry> load() {
        ArrayList<Entry> entries = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(preferences.getString(RECORDS, "[]"));
            for (int i = 0; i < json.length(); i++) {
                Entry entry = Entry.deserialize(json.getJSONObject(i));
                if (entry.id > 0 && !entry.fileName.isEmpty()) entries.add(entry);
            }
        } catch (Exception ignored) { }
        entries.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return entries;
    }

    private synchronized void save(List<Entry> entries) {
        JSONArray array = new JSONArray();
        int count = 0;
        for (Entry entry : entries) {
            if (++count > MAX_HISTORY) break;
            try { array.put(entry.serialize()); } catch (Exception ignored) { }
        }
        preferences.edit().putString(RECORDS, array.toString()).commit();
    }

    private File file(Entry entry) {
        File base = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return base == null ? null : new File(base, entry.fileName);
    }

    /** DownloadManager persists and resumes transfers independently of the Activity. */
    public synchronized Entry start(Catalog.App item) throws Exception {
        if (!isDirectApk(item.apkUrl)) throw new IllegalArgumentException("يجب أن يكون الرابط HTTPS لملف APK مباشر");
        ArrayList<Entry> entries = load();
        for (Entry old : entries) {
            if (old.packageName.equals(item.packageName) && old.versionCode == item.versionCode) {
                refresh(old);
                if (old.active() || old.complete()) return old;
            }
        }
        String clean = item.packageName.replaceAll("[^A-Za-z0-9_.-]", "_");
        String fileName = clean + "_" + item.versionCode + "_" +
            System.currentTimeMillis() + ".apk";
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(item.apkUrl));
        req.setTitle(item.name);
        req.setDescription("تحميل من YASSERAPPS");
        req.setMimeType("application/vnd.android.package-archive");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setAllowedOverMetered(!wifiOnly());
        req.setAllowedOverRoaming(false);
        req.setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName);
        Entry entry = new Entry();
        entry.id = manager.enqueue(req);
        entry.appId = item.id;
        entry.name = item.name;
        entry.packageName = item.packageName;
        entry.version = item.version;
        entry.versionCode = item.versionCode;
        entry.url = item.apkUrl;
        entry.sha256 = item.sha256 == null ? "" : item.sha256;
        entry.fileName = fileName;
        entry.createdAt = System.currentTimeMillis();
        entry.status = DownloadManager.STATUS_PENDING;
        entries.add(0, entry);
        save(entries);
        return entry;
    }

    public static boolean isDirectApk(String link) {
        if (link == null || link.length() > 2048) return false;
        try {
            URI uri = new URI(link);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                uri.getHost() != null && uri.getUserInfo() == null &&
                uri.getPath() != null && uri.getPath().toLowerCase(Locale.ROOT).endsWith(".apk");
        } catch (Exception e) { return false; }
    }

    private void refresh(Entry entry) {
        entry.missing = false;
        try (android.database.Cursor cursor = manager.query(
            new DownloadManager.Query().setFilterById(entry.id))) {
            if (cursor == null || !cursor.moveToFirst()) {
                // No registered system download means no trusted content URI,
                // even if a stray local file happens to exist.
                entry.missing = true;
                entry.status = DownloadManager.STATUS_FAILED;
                return;
            }
            entry.status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            entry.reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            entry.bytesDone = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            entry.bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (entry.complete()) {
                File f = file(entry);
                entry.missing = f == null || !f.exists();
            }
        } catch (Exception e) {
            entry.missing = true;
        }
    }

    public synchronized List<Entry> all() {
        ArrayList<Entry> entries = load();
        for (Entry entry : entries) refresh(entry);
        return entries;
    }

    public synchronized Entry latestFor(Catalog.App app) {
        for (Entry entry : all()) {
            if (entry.packageName.equals(app.packageName) && entry.versionCode == app.versionCode) {
                return entry;
            }
        }
        return null;
    }

    public synchronized void remove(long id) {
        ArrayList<Entry> entries = load();
        for (Entry item : entries) {
            if (item.id == id) {
                manager.remove(id);
                File f = file(item);
                if (f != null && f.exists()) f.delete();
                break;
            }
        }
        entries.removeIf(e -> e.id == id);
        save(entries);
    }

    /** Validate local file and actual package metadata before invoking the system installer. */
    public void verify(Entry entry, Checked checked) {
        VERIFY.execute(() -> {
            String error = null;
            Uri uri = null;
            try {
                Entry current = null;
                for (Entry item : all()) if (item.id == entry.id) { current = item; break; }
                if (current == null || !current.complete()) throw new Exception("التحميل لم يكتمل بعد");
                File apk = file(current);
                if (apk == null || !apk.isFile() || apk.length() == 0) {
                    throw new Exception("الملف غير موجود أو فارغ");
                }
                if (current.sha256 != null && !current.sha256.isEmpty()) {
                    if (!current.sha256.matches("(?i)[0-9a-f]{64}")) {
                        throw new Exception("قيمة SHA-256 غير صالحة");
                    }
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    try (FileInputStream stream = new FileInputStream(apk)) {
                        byte[] buffer = new byte[32768];
                        int n;
                        while ((n = stream.read(buffer)) > 0) md.update(buffer, 0, n);
                    }
                    StringBuilder actual = new StringBuilder();
                    for (byte b : md.digest()) actual.append(String.format(Locale.ROOT, "%02x", b & 255));
                    if (!actual.toString().equalsIgnoreCase(current.sha256)) {
                        throw new Exception("فشل فحص SHA-256، الملف لا يطابق النسخة المعتمدة");
                    }
                }
                PackageInfo packageInfo;
                PackageManager pm = app.getPackageManager();
                if (Build.VERSION.SDK_INT >= 33) {
                    packageInfo = pm.getPackageArchiveInfo(
                        apk.getAbsolutePath(), PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageInfo = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                }
                if (packageInfo == null || !current.packageName.equals(packageInfo.packageName)) {
                    throw new Exception("اسم الحزمة داخل APK لا يطابق المتجر، تم منع التثبيت");
                }
                long code = Build.VERSION.SDK_INT >= 28 ?
                    packageInfo.getLongVersionCode() : packageInfo.versionCode;
                if (code != current.versionCode) {
                    throw new Exception("رقم إصدار APK مختلف عن الرقم المعلن في المتجر");
                }
                uri = manager.getUriForDownloadedFile(current.id);
                if (uri == null) throw new Exception("تعذّر الوصول إلى ملف التثبيت");
            } catch (Exception e) {
                error = e.getMessage() == null ? "تعذّر التحقق من الملف" : e.getMessage();
            }
            final String message = error;
            final Uri verifiedUri = uri;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> checked.done(message == null, message, verifiedUri));
        });
    }

    public static boolean allowedToInstall(Context context) {
        return Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent permissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.getPackageName()));
    }
}
