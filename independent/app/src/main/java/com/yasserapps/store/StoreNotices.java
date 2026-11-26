package com.yasserapps.store;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

final class StoreNotices {
    static final String ACTION="com.yasserapps.store.OPEN_NOTICES";
    private static final String URL="https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/notices.json";
    private static final String PREF="store_notices_v1";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static final class Notice {
        String id,title,body,type; long publishedAt;
        String key(){return id+":"+publishedAt;}
    }
    static final class Result {
        final List<Notice> notices; final boolean failed;
        Result(List<Notice> n,boolean f){notices=n;failed=f;}
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,0);}
    static List<Notice> parse(JSONObject root)throws Exception {
        if(root.optInt("schemaVersion")!=1)throw new JSONException("schema");
        JSONArray a=root.getJSONArray("notices");
        if(a.length()>100)throw new JSONException("too many notices");
        List<Notice> out=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(int i=0;i<a.length();i++){
            JSONObject row=a.getJSONObject(i); Notice n=new Notice();
            n.id=row.getString("id");n.title=row.getString("title");n.body=row.getString("body");
            n.type=row.optString("type","news");n.publishedAt=row.getLong("publishedAt");
            if(!n.id.matches("[a-z0-9][a-z0-9_-]{0,63}")||!ids.add(n.id)||n.title.trim().isEmpty()||n.title.length()>100||n.body.trim().isEmpty()||n.body.length()>1500||n.publishedAt<=0||n.publishedAt>System.currentTimeMillis()+86400000L||!Arrays.asList("news","update","addition","maintenance").contains(n.type))throw new JSONException("invalid notice");
            if(row.optBoolean("enabled",true))out.add(n);
        }
        out.sort(Comparator.comparingLong((Notice n)->n.publishedAt).reversed().thenComparing(n->n.id));return out;
    }
    static List<Notice> cached(Context c){
        try{return parse(new JSONObject(prefs(c).getString("feed","{\"schemaVersion\":1,\"notices\":[]}")));}catch(Exception e){return Collections.emptyList();}
    }
    static boolean unread(Context c,List<Notice> rows){
        Set<String> read=prefs(c).getStringSet("read",Collections.emptySet());
        for(Notice n:rows)if(!read.contains(n.key()))return true;return false;
    }
    static void markRead(Context c,List<Notice> rows){
        Set<String> read=new HashSet<>();
        for(Notice n:rows)read.add(n.key());
        prefs(c).edit().putStringSet("read",read).apply();
    }
    static void check(Context c,java.util.function.Consumer<Result> cb){
        Context app=c.getApplicationContext();
        IO.execute(()->{
            List<Notice> rows;boolean failed=false;
            try{
                JSONObject root=DirectSources.fetchJson(app,URL,512*1024);
                rows=parse(root);prefs(app).edit().putString("feed",root.toString()).apply();
            }catch(Exception e){rows=cached(app);failed=true;}
            cb.accept(new Result(rows,failed));
        });
    }
    static void notifyNew(Context c,List<Notice> rows){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(manager==null||!manager.areNotificationsEnabled())return;
        Set<String> notified=prefs(c).getStringSet("notified",Collections.emptySet());
        Set<String> read=prefs(c).getStringSet("read",Collections.emptySet());
        Set<String> next=new HashSet<>(); Notice newest=null;
        for(Notice n:rows){next.add(n.key());if(newest==null&&!notified.contains(n.key())&&!read.contains(n.key()))newest=n;}
        if(newest==null)return;
        String channel="yasserapps_announcements";
        manager.createNotificationChannel(new NotificationChannel(channel,"إعلانات YASSERAPPS",NotificationManager.IMPORTANCE_DEFAULT));
        if(manager.getNotificationChannel(channel).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        Intent intent=new Intent(c,MainActivity.class).setAction(ACTION).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,709,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(709,new Notification.Builder(c,channel).setSmallIcon(R.drawable.yasser_logo).setContentTitle(newest.title).setContentText(newest.body).setStyle(new Notification.BigTextStyle().bigText(newest.body)).setContentIntent(pi).setAutoCancel(true).build());
        prefs(c).edit().putStringSet("notified",next).apply();
    }
}
