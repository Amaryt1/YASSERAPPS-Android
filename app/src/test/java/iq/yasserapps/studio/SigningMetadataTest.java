package iq.yasserapps.studio;
import java.io.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SigningMetadataTest {
 static void put(ZipOutputStream z,String n,byte[] b)throws IOException{z.putNextEntry(new ZipEntry(n));z.write(b);z.closeEntry();}
 @Test public void rebuildDistinguishesStampFromAppContent()throws Exception{
  File src=File.createTempFile("stamp-source",".apk"),dst=File.createTempFile("stamp-output",".apk"),bad=File.createTempFile("stamp-bad",".apk");
  try{
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(src))){put(z,"stamp-cert-sha256",new byte[32]);put(z,"META-INF/OLD.RSA",new byte[]{9});put(z,"classes.dex",new byte[]{1});put(z,"assets/stamp-cert-sha256",new byte[]{2});put(z,"AndroidManifest.xml",new byte[]{3});}
   String hash=ApkEngine.sha256(src);ApkEngine.repack(src,dst,"classes.dex",new byte[]{5});LegacyBrand.checkEntries(src,dst,"classes.dex");
   try(ZipFile z=new ZipFile(dst)){assertNull(z.getEntry("stamp-cert-sha256"));assertNull(z.getEntry("META-INF/OLD.RSA"));assertNotNull(z.getEntry("assets/stamp-cert-sha256"));assertNotNull(z.getEntry("AndroidManifest.xml"));}
   ApkEngine.repack(dst,bad,"assets/stamp-cert-sha256",new byte[]{6});try{LegacyBrand.checkEntries(src,bad,"classes.dex");fail("Unrelated change accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("assets/stamp-cert-sha256"));}
   assertEquals(hash,ApkEngine.sha256(src));
  }finally{src.delete();dst.delete();bad.delete();}
 }
}