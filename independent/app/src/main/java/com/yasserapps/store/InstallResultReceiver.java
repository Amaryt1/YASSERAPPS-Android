package com.yasserapps.store;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public final class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.yasserapps.store.INSTALL_RESULT".equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm;
            if (Build.VERSION.SDK_INT >= 33) {
                confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(confirm); }
                catch (Exception e) {
                    Toast.makeText(context, "تعذّر فتح تأكيد التثبيت من أندرويد", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String text = status == PackageInstaller.STATUS_SUCCESS ?
            "اكتمل تثبيت التطبيق بنجاح" :
            "فشل التثبيت: " + (message == null ? status : message);
        Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG).show();
        android.util.Log.i("YASSERAPPS_INSTALL", text);
    }
}
