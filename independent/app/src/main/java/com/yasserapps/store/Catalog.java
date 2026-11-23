package com.yasserapps.store;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Catalog {
    public static final String ADDRESS =
        "https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/apps.json";
    private static final String PREFS = "yasserapps_live_catalog";
    private static final String CACHE = "last_valid_json";
    private static final int MAX_BYTES = 2_000_000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    public static class App {
        public String id, name, packageName, category, version;
        public String apkUrl, iconUrl, bannerUrl, summary, description, changelog, sha256;
        public int versionCode;
        public long sizeBytes;
        public boolean featured;
        public ArrayList<String> screenshots = new ArrayList<>();

        public boolean isGame() {
            return "game".equals(category);
        }
    }

    public static class Result {
        public final List<App> apps;
        public final String warning;

        Result(List<App> apps, String warning) {
            this.apps = apps;
            this.warning = warning;
        }
    }

    public interface Callback {
        void loaded(Result result);
    }

    private static boolean validHttps(String raw) {
        if (raw == null || !raw.startsWith("https://") || raw.length() > 2048) return false;
        try {
            java.net.URI uri = new java.net.URI(raw);
            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception ex) {
            return false;
        }
    }

    static List<App> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optInt("schemaVersion") != 1 ||
            !"YASSERAPPS".equals(root.optString("store"))) {
            throw new IllegalArgumentException("Invalid catalog format");
        }
        JSONArray items = root.getJSONArray("apps");
        if (items.length() > 5000) throw new IllegalArgumentException("Catalog is too large");
        ArrayList<App> apps = new ArrayList<>();
        HashSet<String> seenIds = new HashSet<>();
        HashSet<String> seenPackages = new HashSet<>();
        for (int i = 0; i < items.length(); ++i) {
            JSONObject source = items.getJSONObject(i);
            if (!source.optBoolean("enabled", true)) continue;
            App app = new App();
            app.id = source.optString("id");
            app.name = source.optString("name");
            app.packageName = source.optString("packageName");
            app.category = source.optString("category", "app");
            app.version = source.optString("version");
            app.versionCode = source.optInt("versionCode");
            app.apkUrl = source.optString("apkUrl");
            app.iconUrl = source.optString("iconUrl");
            app.bannerUrl = source.optString("bannerUrl");
            app.summary = source.optString("summary");
            app.description = source.optString("description");
            app.changelog = source.optString("changelog");
            app.sha256 = source.optString("sha256");
            app.sizeBytes = Math.max(0L, source.optLong("sizeBytes"));
            app.featured = source.optBoolean("featured");
            if (app.id.length() < 3 || app.name.isEmpty() ||
                !app.packageName.matches("[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)+") ||
                app.versionCode < 1 || !validHttps(app.apkUrl) || !validHttps(app.iconUrl) ||
                !DownloadCoordinator.isDirectApk(app.apkUrl) ||
                !("app".equals(app.category) || "game".equals(app.category)) ||
                !seenIds.add(app.id) || !seenPackages.add(app.packageName)) {
                continue;
            }
            if (!app.bannerUrl.isEmpty() && !validHttps(app.bannerUrl)) app.bannerUrl = "";
            JSONArray photos = source.optJSONArray("screenshots");
            if (photos != null) {
                for (int j = 0; j < Math.min(photos.length(), 12); ++j) {
                    String photo = photos.optString(j);
                    if (validHttps(photo)) app.screenshots.add(photo);
                }
            }
            apps.add(app);
        }
        return apps;
    }

    private static List<App> cache(Context context) {
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String value = p.getString(CACHE, "");
            if (!value.isEmpty()) return parse(value);
        } catch (Exception ignored) { }
        return new ArrayList<>();
    }

    public static void load(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            Result result;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(ADDRESS).openConnection();
                conn.setConnectTimeout(9000);
                conn.setReadTimeout(13000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "YASSERAPPS-Android-Independent");
                if (conn.getResponseCode() != 200) throw new Exception("Catalog HTTP status " + conn.getResponseCode());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0, read;
                    while ((read = stream.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_BYTES) throw new Exception("Catalog too large");
                        bytes.write(buffer, 0, read);
                    }
                }
                String data = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                List<App> apps = parse(data);
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(CACHE, data).apply();
                result = new Result(apps, null);
            } catch (Exception e) {
                List<App> offline = cache(app);
                result = new Result(offline, offline.isEmpty() ? null : "تعذّر تحديث المتجر. نعرض آخر قائمة محفوظة.");
            } finally {
                if (conn != null) conn.disconnect();
            }
            callback.loaded(result);
        });
    }
}
