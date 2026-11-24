package com.yasserapps.store;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Branded, optional YASSERAPPS notifications; actual transfers remain in DownloadManager. */
public final class DownloadBranding {
    private static final String PROGRESS_CHANNEL = "yasserapps_transfer_progress";
    private static final String COMPLETE_CHANNEL = "yasserapps_transfer_completed";
    private DownloadBranding() {}

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void setup(Context context) {
        NotificationManager nm = manager(context);
        if (nm == null || Build.VERSION.SDK_INT < 26) return;
        NotificationChannel progress = new NotificationChannel(
            PROGRESS_CHANNEL, "تنزيلات YASSERAPPS", NotificationManager.IMPORTANCE_LOW);
        progress.setDescription("حالة تنزيل التطبيقات والألعاب من YASSERAPPS");
        nm.createNotificationChannel(progress);
        NotificationChannel complete = new NotificationChannel(
            COMPLETE_CHANNEL, "اكتمال تنزيلات YASSERAPPS", NotificationManager.IMPORTANCE_DEFAULT);
        complete.setDescription("تنبيه عند اكتمال التنزيل وجاهزية التثبيت");
        nm.createNotificationChannel(complete);
    }

    private static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static int notificationId(long downloadId) {
        return 140000 + (int)(downloadId % 100000);
    }

    private static Notification.Builder builder(Context context, String channel, DownloadCoordinator.Entry item) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ?
            new Notification.Builder(context, channel) : new Notification.Builder(context);
        Intent open = new Intent(context, MainActivity.class)
            .setAction("com.yasserapps.store.OPEN_DOWNLOADS")
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("download_id", item.id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(
            context, notificationId(item.id), open, flags);
        return b.setSmallIcon(R.drawable.ic_yasser_notification)
            .setContentTitle("YASSERAPPS • " + item.name)
            .setContentIntent(pending)
            .setColor(0xFF69FF00)
            .setOnlyAlertOnce(true);
    }

    public static void progress(Context context, DownloadCoordinator.Entry item) {
        if (!allowed(context) || item == null || !item.active()) return;
        setup(context);
        NotificationManager nm = manager(context);
        if (nm == null) return;
        int percentage = item.percent();
        Notification.Builder b = builder(context, PROGRESS_CHANNEL, item)
            .setContentText(percentage < 0 ?
                "جاري التحميل • افتح التنزيلات للمتابعة" :
                "جاري التحميل • " + percentage + "%")
            .setOngoing(true)
            .setProgress(percentage < 0 ? 0 : 100, percentage < 0 ? 0 : percentage, percentage < 0);
        nm.notify(notificationId(item.id), b.build());
    }

    public static void complete(Context context, DownloadCoordinator.Entry item) {
        if (!allowed(context) || item == null || !item.complete()) return;
        setup(context);
        NotificationManager nm = manager(context);
        if (nm == null) return;
        Notification.Builder b = builder(context, COMPLETE_CHANNEL, item)
            .setContentText("اكتمل التحميل ✓ • جاهز للتثبيت عبر أندرويد")
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false);
        nm.notify(notificationId(item.id), b.build());
    }

    public static void failed(Context context, DownloadCoordinator.Entry item) {
        if (!allowed(context) || item == null) return;
        setup(context);
        NotificationManager nm = manager(context);
        if (nm == null) return;
        Notification.Builder b = builder(context, COMPLETE_CHANNEL, item)
            .setContentText("تعذّر تنزيل " + item.name + " • اضغط لإعادة المحاولة")
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false);
        nm.notify(notificationId(item.id), b.build());
    }

    public static void clear(Context context, long id) {
        NotificationManager nm = manager(context);
        if (nm != null) nm.cancel(notificationId(id));
    }
}
