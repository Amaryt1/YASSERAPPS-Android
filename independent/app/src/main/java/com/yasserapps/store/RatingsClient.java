package com.yasserapps.store;
import android.content.Context;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

final class RatingsClient {
    private static final ExecutorService IO=Executors.newFixedThreadPool(2);
    private static final String CONFIG="https://raw.githubusercontent.com/Amaryt1/YASSERAPPS-Android/main/catalog/ratings-service.json";
    private static String endpoint="";
    private static long checkedAt=0;
    static final class Rating {
        final long count;final double average;
        Rating(JSONObject o)throws Exception{
            count=o.getLong("count");average=o.getDouble("average");
            if(count<0||!Double.isFinite(average)||average<0||average>5)throw new IOException("Invalid rating");
        }
    }
    interface Callback{void done(Rating rating,String error);}
    private static JSONObject request(String address,JSONObject body)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
        try{
            conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(5000);conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept","application/json");
            if(body!=null){
                conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
                byte[] data=body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(data.length);
                try(OutputStream out=conn.getOutputStream()){out.write(data);}
            }
            if(conn.getResponseCode()!=200)throw new IOException("Service unavailable");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("Too large");out.write(b,0,n);}}
            return new JSONObject(out.toString("UTF-8"));
        }finally{conn.disconnect();}
    }
    private static synchronized String endpoint()throws Exception{
        long now=android.os.SystemClock.elapsedRealtime();
        if(checkedAt!=0&&now-checkedAt<300000)return endpoint;
        JSONObject config=request(CONFIG,null);
        String url=config.optString("baseUrl");
        URI uri=new URI(url.isEmpty()?"https://invalid.example":url);
        endpoint=config.optBoolean("enabled")&&"https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null?url.replaceAll("/+$",""):"";
        checkedAt=now;return endpoint;
    }
    private static synchronized String voter(Context c){
        android.content.SharedPreferences p=c.getSharedPreferences("ratings_identity_v1",Context.MODE_PRIVATE);
        String id=p.getString("voter",null);
        if(id==null){id=UUID.randomUUID().toString();p.edit().putString("voter",id).commit();}
        return id;
    }
    static void read(Context c,Catalog.App a,Callback cb){run(c,a,0,cb);}
    static void vote(Context c,Catalog.App a,int score,Callback cb){run(c,a,score,cb);}
    private static void run(Context c,Catalog.App a,int score,Callback cb){
        Context app=c.getApplicationContext();
        IO.execute(()->{
            try{
                String base=endpoint();if(base.isEmpty())throw new IOException("Not configured");
                String id=java.net.URLEncoder.encode(a.id,"UTF-8");
                JSONObject body=score==0?null:new JSONObject().put("voter",voter(app)).put("score",score);
                cb.done(new Rating(request(base+"/v1/ratings/"+id,body)),null);
            }catch(Exception e){cb.done(null,"unavailable");}
        });
    }
}
