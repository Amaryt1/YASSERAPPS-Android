package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.zip.*;
import java.nio.file.Files;
public class BatchScanTest {
 @Test public void sequentialScanCleansTemporaryFileOnSuccessAndError()throws Exception{
  File dir=Files.createTempDirectory("batch-test").toFile();
  try{ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(1);z.closeEntry();}
   String result=BatchScan.inspect(dir,()->new ByteArrayInputStream(b.toByteArray()),33,new String[]{"arm64-v8a"});
   assertTrue(result.contains("اكتمل فحص البنية"));assertEquals(0,dir.list().length);
   try{BatchScan.inspect(dir,()->new ByteArrayInputStream(new byte[]{1}),33,new String[0]);fail("Expected invalid ZIP");}catch(IOException expected){}
   assertEquals(0,dir.list().length);
  }finally{dir.delete();}
 }
 @Test public void fastRepackPreservesUntouchedContents()throws Exception{
  File a=File.createTempFile("batch-original",".apk"),b=File.createTempFile("batch-output",".apk");
  try{try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(a))){
   z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(1);z.closeEntry();
   z.putNextEntry(new ZipEntry("assets/data.txt"));z.write(new byte[1024*1024]);z.closeEntry();
   z.putNextEntry(new ZipEntry("assets/change.txt"));z.write(2);z.closeEntry();}
   ApkEngine.repack(a,b,"assets/change.txt",new byte[]{3});
   java.util.Map<String,String> x=SigningAudit.snapshot(a),y=SigningAudit.snapshot(b);
   assertEquals(x.get("assets/data.txt"),y.get("assets/data.txt"));assertEquals(x.get("AndroidManifest.xml"),y.get("AndroidManifest.xml"));assertNotEquals(x.get("assets/change.txt"),y.get("assets/change.txt"));
  }finally{a.delete();b.delete();}
 }
}
