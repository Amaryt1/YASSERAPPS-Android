package iq.yasserapps.studio;
import android.app.Activity;
import android.content.SharedPreferences;
import java.io.*;
import org.json.JSONObject;
/** Opt-in host calls this in its activity instead of its former optional promotional dialog. */
public final class BrandRuntime {
 private BrandRuntime(){}
 public static void showOnLaunch(Activity host){
  if(host.isFinishing()||host.isDestroyed())return;
  try(InputStream in=host.getAssets().open("yasserapps-window-profile.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[8192];int count,total=0;while((count=in.read(b))!=-1){total+=count;if(total>200000)throw new IOException("Profile too large");out.write(b,0,count);}
   JSONObject n=WindowProfile.parse(out.toString("UTF-8"));if(!"native-brand-v1".equals(n.optString("runtime"))||!n.optBoolean("replacementEnabled"))return;
   SharedPreferences prefs=host.getSharedPreferences("yasserapps-brand-runtime",0);String key="hidden-"+n.toString().hashCode();if(prefs.getBoolean(key,false))return;
   BrandDialog.show(host,n,false,()->prefs.edit().putBoolean(key,true).apply());
  }catch(Exception e){android.util.Log.w("YasserBrand","Optional brand window not shown",e);}
 }
}