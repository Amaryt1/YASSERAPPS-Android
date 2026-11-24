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
    private static final String API_ADDRESS =
        "https://api.github.com/repos/Amaryt1/YASSERAPPS-Android/contents/catalog/apps.json?ref=main";
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

        public final boolean finalResult;

        Result(List<App> apps, String warning, boolean finalResult) {
            this.apps = apps;
            this.warning = warning;
            this.finalResult = finalResult;
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
                !app.sha256.matches("(?i)[a-f0-9]{64}") ||
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
        // The app ships a verified starter catalog so first launch works even
        // without a network. Online GitHub updates always override this snapshot.
        try (InputStream file = context.getAssets().open("yasserapps_starter_catalog.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = file.read(buffer)) > 0) {
                if (out.size() + count > MAX_BYTES) throw new Exception("Starter catalog too large");
                out.write(buffer, 0, count);
            }
            return parse(out.toString("UTF-8"));
        } catch (Exception e) {
            android.util.Log.w("YASSERAPPS_CATALOG", "Offline catalog not available", e);
        }
        return new ArrayList<>();
    }

    private static String readLive(String address, String accept) throws Exception {
        HttpURLConnection conn = null;
        try {
            String separator = address.contains("?") ? "&" : "?";
            conn = (HttpURLConnection) new URL(address + separator + "_ts=" + System.currentTimeMillis()).openConnection();
            conn.setUseCaches(false);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
            conn.setRequestProperty("Pragma", "no-cache");
            conn.setRequestProperty("Accept", accept);
            conn.setRequestProperty("User-Agent", "YASSERAPPS-Android-Independent");
            int status = conn.getResponseCode();
            if (status != 200) throw new Exception("Catalog HTTP status " + status);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = conn.getInputStream()) {
                byte[] buffer = new byte[16384];
                int total = 0, read;
                while ((read = stream.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) throw new Exception("Catalog too large");
                    bytes.write(buffer, 0, read);
                }
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void load(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<App> initial = cache(app);
            if (!initial.isEmpty()) callback.loaded(new Result(initial, null, false));
            String[] endpoints = {ADDRESS, API_ADDRESS};
            String[] accepts = {"application/json", "application/vnd.github.raw+json"};
            for (int i = 0; i < endpoints.length; i++) {
                try {
                    String data = readLive(endpoints[i], accepts[i]);
                    List<App> apps = parse(data);
                    JSONArray entries = new JSONObject(data).getJSONArray("apps");
                    int expected = 0;
                    for (int n = 0; n < entries.length(); n++) {
                        if (entries.getJSONObject(n).optBoolean("enabled", true)) expected++;
                    }
                    if (apps.size() != expected) {
                        throw new Exception("Catalog validation rejected " + (expected - apps.size()) + " enabled entries");
                    }
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(CACHE, data).apply();
                    android.util.Log.i("YASSERAPPS_CATALOG", "LIVE_CATALOG_OK count=" + apps.size() + " endpoint=" + i);
                    callback.loaded(new Result(apps, null, true));
                    return;
                } catch (Exception e) {
                    android.util.Log.w("YASSERAPPS_CATALOG", "Catalog endpoint " + i + " failed", e);
                }
            }
            android.util.Log.w("YASSERAPPS_CATALOG", "LIVE_CATALOG_FAILED cached=" + initial.size());
            callback.loaded(new Result(initial, "تعذّر تحديث المتجر. نعرض آخر قائمة محفوظة.", true));
        });
    }
}
