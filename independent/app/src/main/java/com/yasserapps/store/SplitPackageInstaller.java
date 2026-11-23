package com.yasserapps.store;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stream APK splits from APKS/APKM/XAPK archives through Android's supported
 * PackageInstaller.Session API. No root and no silent installs.
 *
 * Important limitation: Android 11+ blocks third-party writes to other apps'
 * Android/obb directories. XAPK files containing OBB data are rejected rather
 * than misleading users with a partial/game-broken install.
 */
public final class SplitPackageInstaller {
    private static final long MAX_TOTAL = 8L * 1024 * 1024 * 1024;
    private static final long MAX_MEMBER = 3L * 1024 * 1024 * 1024;
    private static final int MAX_APKS = 64;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback { void done(boolean success, String message); }
    private SplitPackageInstaller() {}

    static boolean isBundle(String uri) {
        if (uri == null) return false;
        String path = uri.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
        return path.endsWith(".apks") || path.endsWith(".apkm") || path.endsWith(".xapk");
    }

    private static boolean safeName(String name) {
        if (name == null || name.isEmpty() || name.length() > 300) return false;
        if (name.charAt(0) == '/' || name.contains("\\") || name.contains("\0")) return false;
        for (String part : name.split("/")) {
            if ("..".equals(part) || ".".equals(part) || part.isEmpty()) return false;
        }
        return true;
    }

    private static boolean isPackageFile(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private static PackageInfo parseApk(Context context, File file) {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= 33) {
            return pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.PackageInfoFlags.of(0));
        }
        return pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
    }

    private static void copy(InputStream in, OutputStream out, long expected) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int count;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > expected || total > MAX_MEMBER) throw new Exception("حجم APK غير متوقع");
            out.write(buffer, 0, count);
        }
        if (total != expected) throw new Exception("فشل التحقق من اكتمال ملف APK");
    }

    public static void install(Context context, File archive, String packageName,
                               int versionCode, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                if (!DownloadCoordinator.allowedToInstall(app)) {
                    throw new Exception("امنح صلاحية التثبيت لـ YASSERAPPS ثم أعد المحاولة");
                }
                if (archive == null || !archive.isFile()) throw new Exception("ملف الحزمة غير موجود");
                try (ZipFile zip = new ZipFile(archive)) {
                    List<ZipEntry> apks = new ArrayList<>();
                    long totalSize = 0;
                    boolean hasObb = false;
                    java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
                    while (enumeration.hasMoreElements()) {
                        ZipEntry entry = enumeration.nextElement();
                        if (entry.isDirectory()) continue;
                        String name = entry.getName();
                        if (!safeName(name)) throw new Exception("أسماء ملفات غير آمنة داخل الحزمة");
                        String low = name.toLowerCase(Locale.ROOT);
                        if (low.endsWith(".obb") || low.startsWith("android/obb/") ||
                            low.startsWith("android/data/")) hasObb = true;
                        if (isPackageFile(name)) {
                            long size = entry.getSize();
                            if (size < 1 || size > MAX_MEMBER || apks.size() >= MAX_APKS)
                                throw new Exception("عدد أو حجم ملفات APK غير صالح");
                            long compressed = entry.getCompressedSize();
                            if (compressed > 0 && size > compressed * 400L)
                                throw new Exception("معدل ضغط الحزمة غير آمن");
                            totalSize += size;
                            if (totalSize > MAX_TOTAL) throw new Exception("الحزمة أكبر من الحد الآمن");
                            apks.add(entry);
                        }
                    }
                    if (hasObb) {
                        throw new Exception("الحزمة تحتوي OBB/بيانات لعبة. أندرويد الحديث لا يسمح للتطبيق بنقلها إلى مجلد لعبة أخرى؛ لن نثبت اللعبة ناقصة.");
                    }
                    if (apks.isEmpty()) throw new Exception("الحزمة لا تحتوي ملفات APK");
                    // Identify base.apk; multi-split installs without a base are invalid.
                    ZipEntry base = null;
                    for (ZipEntry e : apks) {
                        String file = new File(e.getName()).getName().toLowerCase(Locale.ROOT);
                        if ("base.apk".equals(file)) { base = e; break; }
                    }
                    if (base == null && apks.size() == 1) base = apks.get(0);
                    if (base == null) throw new Exception("ملف base.apk الأساسي مفقود");

                    File temp = File.createTempFile("yasser-base-", ".apk", app.getCacheDir());
                    try {
                        try (InputStream input = zip.getInputStream(base);
                             FileOutputStream output = new FileOutputStream(temp)) {
                            copy(input, output, base.getSize());
                        }
                        PackageInfo p = parseApk(app, temp);
                        if (p == null || !packageName.equals(p.packageName))
                            throw new Exception("اسم الحزمة داخل base.apk لا يطابق المتجر");
                        long code = Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
                        if (code != versionCode)
                            throw new Exception("رقم إصدار base.apk لا يطابق الإصدار المنشور");
                    } finally {
                        if (temp.exists()) temp.delete();
                    }

                    PackageInstaller installer = app.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    params.setAppPackageName(packageName);
                    params.setSize(totalSize);
                    int sessionId = installer.createSession(params);
                    boolean committed = false;
                    try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                        int index = 0;
                        for (ZipEntry entry : apks) {
                            String name = entry == base ? "base.apk" : String.format(Locale.ROOT, "split_%02d.apk", index++);
                            try (InputStream input = zip.getInputStream(entry);
                                 OutputStream output = session.openWrite(name, 0, entry.getSize())) {
                                copy(input, output, entry.getSize());
                                session.fsync(output);
                            }
                        }
                        Intent result = new Intent(app, InstallResultReceiver.class)
                            .setAction("com.yasserapps.store.INSTALL_RESULT")
                            .putExtra("requested_package", packageName);
                        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                        PendingIntent pending = PendingIntent.getBroadcast(app, sessionId, result, flags);
                        session.commit(pending.getIntentSender());
                        committed = true;
                    } finally {
                        if (!committed) installer.abandonSession(sessionId);
                    }
                }
                MAIN.post(() -> callback.done(true, "تم إرسال الحزمة إلى مثبت أندرويد؛ أكمل الموافقة على التثبيت"));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "تعذّر تثبيت الحزمة" : e.getMessage();
                MAIN.post(() -> callback.done(false, message));
            }
        }, "YasserSplitInstaller").start();
    }
}
