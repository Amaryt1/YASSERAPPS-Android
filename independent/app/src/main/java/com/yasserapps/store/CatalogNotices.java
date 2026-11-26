package com.yasserapps.store;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;

/** Opt-in catalogue growth notification. Count is real catalogue metadata, not APK installs. */
final class CatalogNotices {
    static void check(Context c){
        if(!UserPrefs.notices(c))return;
        try{
            JSONObject index=DirectSources.fetchJson(c,"https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/api/v1/index.json",128*1024);
            int count=index.getInt("total");
            if(count<0||count>50000)return;
            int before=UserPrefs.get(c).getInt("seen_count",-1);
            if(before<0||count<=before){UserPrefs.get(c).edit().putInt("seen_count",count).apply();return;}
            if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
            NotificationManager manager=c.getSystemService(NotificationManager.class);
            if(manager==null)return;
            String channel="yasser_catalog_new";
            manager.createNotificationChannel(new NotificationChannel(channel,"إضافات المتجر",NotificationManager.IMPORTANCE_LOW));
            Intent open=new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi=PendingIntent.getActivity(c,821,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            manager.notify(821,new Notification.Builder(c,channel).setSmallIcon(R.drawable.yasser_logo).setContentTitle("إضافات جديدة في YASSERAPPS").setContentText("زاد عدد السجلات بمقدار "+(count-before)).setContentIntent(pi).setAutoCancel(true).build());
            UserPrefs.get(c).edit().putInt("seen_count",count).apply();
        }catch(Exception ignored){}
    }
}
