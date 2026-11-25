package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.zip.*;
public class CompatibilityReportTest {
 @Test public void reportsSdkAndAbiMismatches(){
  ApkEngine.Result r=new ApkEngine.Result();r.abis.add("arm64-v8a");r.dex=2;r.dexScanned=1;r.scanWarnings.add("fixture");
  String s=CompatibilityReport.assess(r,28,33,34,new String[]{"x86"},true);
  assertTrue(s.contains("أقل من المطلوب"));assertTrue(s.contains("لا توجد ABI مشتركة"));assertTrue(s.contains("الفحص جزئي"));
  s=CompatibilityReport.assess(r,35,26,34,new String[]{"arm64-v8a"},true);assertTrue(s.contains("معماريات مشتركة"));
 }
 @Test public void malformedDexIsPartialAndOtherEntriesAreRead()throws Exception{
  File f=File.createTempFile("compat",".apk");
  try{
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
    z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(1);z.closeEntry();
    z.putNextEntry(new ZipEntry("classes.dex"));z.write(2);z.closeEntry();
    z.putNextEntry(new ZipEntry("assets/test.txt"));z.write(3);z.closeEntry();
   }
   ApkEngine.Result r=ApkEngine.scan(f);assertEquals(1,r.dex);assertEquals(0,r.dexScanned);assertEquals(1,r.scanWarnings.size());assertEquals(3,r.entries);assertTrue(r.assets.contains("assets/test.txt"));
  }finally{f.delete();}
 }
 @Test public void recognizesContainerWithoutManifest()throws Exception{
  File f=File.createTempFile("container",".apk");
  try{
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){z.putNextEntry(new ZipEntry("base.apk"));z.write(1);z.closeEntry();}
   try{ApkEngine.scan(f);fail("Expected missing manifest");}catch(IOException e){assertTrue(CompatibilityReport.error(e).contains("استخرج APK"));}
  }finally{f.delete();}
 }
}
