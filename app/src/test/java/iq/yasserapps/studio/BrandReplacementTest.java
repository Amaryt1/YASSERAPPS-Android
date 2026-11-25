package iq.yasserapps.studio;
import java.io.File;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class BrandReplacementTest {
 @Test public void packagedRuntimeAndReplacement()throws Exception{
  File src=new File("build/outputs/apk/debug/app-debug.apk"),dst=File.createTempFile("brand-replace",".apk");
  try{JSONObject original=WindowProfile.load(src);BrandCompatibility.require(src,original);String hash=ApkEngine.sha256(src);
   JSONObject preset=WindowProfile.edit(original,false,"YASSERAPPS","New title","New body","https://t.me/YasserAppsIQ","Open","#39FF7A","#090B09");
   JSONObject merged=WindowProfile.replacement(original,preset);assertTrue(merged.getBoolean("replacementEnabled"));assertEquals(original.getBoolean("optionalUpdateEnabled"),merged.getBoolean("optionalUpdateEnabled"));
   WindowProfile.rewrite(src,dst,merged);JSONObject read=WindowProfile.load(dst);BrandCompatibility.require(dst,read);assertTrue(WindowProfile.same(merged,read));assertEquals("New title",read.getString("title"));assertEquals(hash,ApkEngine.sha256(src));
  }finally{dst.delete();}
 }
 @Test public void refusesUnsupportedContract()throws Exception{JSONObject p=WindowProfile.load(new File("build/outputs/apk/debug/app-debug.apk"));p.remove("runtime");try{WindowProfile.replacement(p,p);fail("missing runtime accepted");}catch(java.io.IOException expected){}}
}