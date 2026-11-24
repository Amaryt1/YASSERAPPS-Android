package com.yasserapps.store;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The system completes the transfer even if YASSERAPPS has closed. */
public final class DownloadCompleteReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (id <= 0) return;
        DownloadCoordinator.Entry entry = new DownloadCoordinator(context).find(id);
        if (entry == null) return; // Ignore other apps' downloads and untrusted IDs.
        if (entry.complete()) DownloadBranding.complete(context, entry);
        else if (entry.failed()) DownloadBranding.failed(context, entry);
    }
}
