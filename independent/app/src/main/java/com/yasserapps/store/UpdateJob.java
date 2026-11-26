package com.yasserapps.store;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic network-constrained notice; no background APK installation. */
public final class UpdateJob extends JobService {
    @Override public boolean onStartJob(JobParameters parameters) {
        StoreUpdates.check(getApplicationContext(), (release, error) -> {
            if (release != null) StoreUpdates.notify(getApplicationContext(), release);
            CatalogNotices.check(getApplicationContext());
            StoreNotices.check(getApplicationContext(), result -> {
                if(!result.failed)StoreNotices.notifyNew(getApplicationContext(),result.notices);
                jobFinished(parameters, false);
            });
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters) {
        return true;
    }
}
