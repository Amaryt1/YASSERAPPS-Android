package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
public class PackageStructureTest{
 static void put(ZipOutputStream z,String name,byte[] b)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(b);z.closeEntry();}
 @Test public void inventoriesResourcesAndElfWithoutChangingFile()throws Exception{
  File f=File.createTempFile("structure",".apk");
  try{
   byte[] elf=new byte[64];elf[0]=0x7f;elf[1]='E';elf[2]='L';elf[3]='F';elf[4]=2;elf[5]=1;elf[18]=(byte)183;
   byte[] xml={3,0,8,0,8,0,0,0};byte[] arsc={2,0,8,0,8,0,0,0};
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
    put(z,"AndroidManifest.xml",xml);put(z,"res/layout/main.xml",xml);put(z,"resources.arsc",arsc);
    put(z,"lib/arm64-v8a/a.so",elf);put(z,"lib/arm64-v8a/b.so",new byte[2]);put(z,"assets/sample.txt","sample".getBytes(StandardCharsets.UTF_8));
   }
   String sha=ApkEngine.sha256(f);ApkEngine.Result r=ApkEngine.scan(f);
   assertEquals(1,r.structure.layouts);assertEquals(2,r.structure.binaryXml);assertEquals(1,r.structure.resourceTables);assertEquals(2,r.structure.nativeFiles);assertEquals(1,r.structure.elfValid);assertEquals(1,r.structure.elfUnknown);assertTrue(r.report().contains("AArch64 / 64-bit"));assertEquals(sha,ApkEngine.sha256(f));
  }finally{f.delete();}
 }
 @Test public void recognizesEncodedTextWithoutPublishingDecodedValue(){
  EncodingStats s=new EncodingStats();String plain="hello world!";
  s.observe(Base64.getEncoder().encodeToString("hello world".getBytes(StandardCharsets.UTF_8)));
  s.observe("literal \\u0041 value");s.observe("not!base64text");
  assertEquals(1,s.base64Text);assertEquals(1,s.escapedText);assertFalse(s.report().contains(plain));
 }
}
