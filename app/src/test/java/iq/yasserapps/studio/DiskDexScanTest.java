package iq.yasserapps.studio;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiskDexScanTest {
 @Test public void scansLargeDexAndCleansTempFile()throws Exception{
  File dir=java.nio.file.Files.createTempDirectory("large-dex-test").toFile();
  File apk=new File(dir,"fixture.apk");
  try{
   int length=65*1024*1024, position=length-100;
   byte[] header=new byte[116];ByteBuffer b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
   b.put("dex\n035\0".getBytes(StandardCharsets.US_ASCII));b.putInt(32,length);b.putInt(56,1);b.putInt(60,112);b.putInt(112,position);
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(apk))){
    z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(1);z.closeEntry();
    z.putNextEntry(new ZipEntry("classes2.dex"));z.write(header);
    byte[] zero=new byte[65536];int left=position-header.length;while(left>0){int n=Math.min(left,zero.length);z.write(zero,0,n);left-=n;}
    byte[] text="optional update".getBytes(StandardCharsets.UTF_8);z.write(text.length);z.write(text);z.write(0);z.write(zero,0,length-position-text.length-2);z.closeEntry();
   }
   ApkEngine.Result r=ApkEngine.scan(apk);assertEquals(1,r.dexScanned);assertEquals(1,r.dex);assertTrue(r.scanWarnings.isEmpty());assertTrue(r.signals.report().contains("اختياري محتمل"));assertEquals(1,dir.list().length);
  }finally{apk.delete();dir.delete();}
 }
}
