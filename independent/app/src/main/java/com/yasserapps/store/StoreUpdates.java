package com.yasserapps.store;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owner-controlled, checksum-pinned update notices. Never silently installs anything. */
public final class StoreUpdates {
    public static final String URL =
        "https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/store-release.json";
    private static final int JOB_ID = 73081;
    private static final int NOTIFY_ID = 708;
    private static final String CHANNEL = "yasserapps_updates";
    private static final ExecutorService THREADS = Executors.newSingleThreadExecutor();

    public static class Release {
        public int versionCode;
        public String version, apkUrl, sha256, notes;
        public long sizeBytes;
        public boolean enabled;

        public Catalog.App toApp() {
            Catalog.App app = new Catalog.App();
            app.id = "yasserapps.official.update";
            app.name = "YASSERAPPS";
            app.packageName = "com.yasserapps.store";
            app.category = "app";
            app.version = version;
            app.versionCode = versionCode;
            app.apkUrl = apkUrl;
            app.iconUrl = "https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/independent/app/src/main/res/drawable/yasser_logo.xml";
            app.description = notes;
            app.summary = "تحديث متجر YASSERAPPS";
            app.changelog = notes;
            app.sha256 = sha256;
            app.sizeBytes = sizeBytes;
            return app;
        }
    }

    public interface Callback {
        void done(Release update, String error);
    }

    private StoreUpdates() {}

    public static void schedule(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobs == null) return;
        JobInfo job = new JobInfo.Builder(
            JOB_ID, new ComponentName(context, UpdateJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(24L * 60L * 60L * 1000L)
            .setPersisted(true)
            .build();
        try {
            jobs.schedule(job);
        } catch (SecurityException | IllegalArgumentException e) {
            android.util.Log.w("YASSERAPPS_UPDATE", "Periodic update schedule unavailable", e);
            // Never crash the storefront because a device restricted background jobs.
        }
    }

    private static int installedVersion(Context ctx) {
        try {
            PackageInfo pkg = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? pkg.getLongVersionCode() : pkg.versionCode;
            return (int) Math.min(Integer.MAX_VALUE, code);
        } catch (Exception ex) {
            return 0;
        }
    }

    public static void check(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        THREADS.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(URL).openConnection();
                connection.setConnectTimeout(8500);
                connection.setReadTimeout(11000);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) throw new Exception("الخدمة غير متاحة حاليًا");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int total = 0, n;
                    while ((n = input.read(buffer)) != -1) {
                        total += n;
                        if (total > 64_000) throw new Exception("بيانات التحديث أكبر من المتوقع");
                        output.write(buffer, 0, n);
                    }
                }
                JSONObject root = new JSONObject(output.toString("UTF-8"));
                if (!"YASSERAPPS".equals(root.optString("store")) ||
                    root.optInt("schemaVersion") != 1)
                    throw new Exception("بيانات تحديث غير صحيحة");
                Release release = new Release();
                release.enabled = root.optBoolean("enabled", false);
                release.versionCode = root.optInt("versionCode", 0);
                release.version = root.optString("version");
                release.apkUrl = root.optString("apkUrl");
                release.sha256 = root.optString("sha256");
                release.notes = root.optString("notes");
                release.sizeBytes = root.optLong("sizeBytes", 0L);
                if (release.enabled && release.versionCode > installedVersion(app)) {
                    if (!DownloadCoordinator.isDirectApk(release.apkUrl) ||
                        !release.apkUrl.split("[?#]", 2)[0].toLowerCase(Locale.ROOT).endsWith(".apk") ||
                        !release.sha256.matches("(?i)[a-f0-9]{64}") ||
                        release.version.isEmpty()) throw new Exception("رابط أو بصمة تحديث المتجر غير صالحة");
                    callback.done(release, null);
                } else {
                    callback.done(null, null);
                }
            } catch (Exception e) {
                callback.done(null, e.getMessage() == null ? "تعذّر فحص التحديثات" : e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static void notify(Context context, Release release) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "تحديثات YASSERAPPS", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("إشعار عند نشر تحديث رسمي للمتجر");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class)
            .setAction("com.yasserapps.store.SHOW_UPDATE")
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent action = PendingIntent.getActivity(context, NOTIFY_ID, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ?
            new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.yasser_logo)
            .setContentTitle("تحديث YASSERAPPS متوفر")
            .setContentText("الإصدار " + release.version + " — افتح المتجر لتنزيله")
            .setContentIntent(action)
            .setAutoCancel(true);
        manager.notify(NOTIFY_ID, builder.build());
    }
}
