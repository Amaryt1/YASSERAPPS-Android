package iq.yasserapps.studio;
import java.io.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScanPerformanceTest {
 @Test public void onePassHashAndScanMatchExistingPath()throws Exception{
  File f=File.createTempFile("scan-copy",".apk");
  try{
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(ZipOutputStream z=new ZipOutputStream(bytes)){
    z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(1);z.closeEntry();
    z.putNextEntry(new ZipEntry("assets/test.txt"));z.write("optional update".getBytes(StandardCharsets.UTF_8));z.closeEntry();
   }
   String hash;
   try(OutputStream out=new FileOutputStream(f)){hash=ApkEngine.copyAndHash(new ByteArrayInputStream(bytes.toByteArray()),out,1000000);}
   assertEquals(ApkEngine.sha256(f),hash);
   ApkEngine.Result a=ApkEngine.scan(f),b=ApkEngine.scanCopied(f,hash);
   assertEquals(a.sha256,b.sha256);assertEquals(a.findings,b.findings);
   assertEquals(a.signals.report(),b.signals.report());assertEquals(a.structure.report(),b.structure.report());
   assertTrue(b.scanMillis>=0);
  }finally{f.delete();}
 }
 @Test public void copyHonorsLimitsAndInterrupt()throws Exception{
  try{ApkEngine.copyAndHash(new ByteArrayInputStream(new byte[8]),new ByteArrayOutputStream(),4);fail("expected size limit");}catch(IOException expected){}
  Thread.currentThread().interrupt();
  try{ApkEngine.copy(new ByteArrayInputStream(new byte[8]),new ByteArrayOutputStream(),100);fail("expected interruption");}catch(InterruptedIOException expected){}finally{Thread.interrupted();}
 }
}
