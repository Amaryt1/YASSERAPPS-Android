package iq.yasserapps.studio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SigningAuditTest {
 static void fixture(File f,String value,boolean metadata)throws IOException{
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
   z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(1);z.closeEntry();
   z.putNextEntry(new ZipEntry("assets/sample.txt"));z.write(value.getBytes(StandardCharsets.UTF_8));z.closeEntry();
   if(metadata){z.putNextEntry(new ZipEntry("META-INF/TEST.SF"));z.write(2);z.closeEntry();}
  }
 }
 @Test public void comparesActualContentsAndIgnoresSignatureMetadata()throws Exception{
  File a=File.createTempFile("audit-a",".apk"),b=File.createTempFile("audit-b",".apk");
  try{
   fixture(a,"same",false);fixture(b,"same",true);
   Map<String,String> before=SigningAudit.snapshot(a),after=SigningAudit.snapshot(b);
   SigningAudit.requireSame(before,after);assertEquals(2,before.size());
   fixture(b,"changed",true);after=SigningAudit.snapshot(b);
   assertTrue(SigningAudit.difference(before,after).contains("ملفات تغيرت: 1"));
   try{SigningAudit.requireSame(before,after);fail("Expected content mismatch");}catch(IOException expected){}
  }finally{a.delete();b.delete();}
 }
}
