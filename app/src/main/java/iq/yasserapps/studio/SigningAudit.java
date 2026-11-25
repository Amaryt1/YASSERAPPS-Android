package iq.yasserapps.studio;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
/** Streaming integrity comparison; signing metadata and directory entries are excluded. */
final class SigningAudit {
 static final long MAX_BYTES=8L*1024*1024*1024;
 static Map<String,String> snapshot(File apk)throws IOException{
  Map<String,String> out=new TreeMap<>();Set<String> seen=new HashSet<>();long total=0;
  byte[] buffer=new byte[65536];
  try(ZipFile z=new ZipFile(apk)){
   MessageDigest md=MessageDigest.getInstance("SHA-256");
   Enumeration<? extends ZipEntry> es=z.entries();
   while(es.hasMoreElements()){
    ZipEntry e=es.nextElement();ApkEngine.valid(e,seen);
    if(seen.size()>50000)throw new IOException("Too many archive entries");
    if(e.isDirectory()||ApkEngine.signingMetadata(e.getName()))continue;
    if(e.getSize()>MAX_BYTES-total)throw new IOException("Integrity audit exceeds size limit");
    md.reset();CRC32 crc=new CRC32();long read=0;
    try(InputStream in=z.getInputStream(e)){
     int n;while((n=in.read(buffer))!=-1){
      if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Audit cancelled");
      if(n==0||n>e.getSize()-read)throw new IOException("Invalid entry size");
      md.update(buffer,0,n);crc.update(buffer,0,n);read+=n;
     }
    }
    if(read!=e.getSize()||crc.getValue()!=e.getCrc())throw new IOException("Entry integrity failure: "+e.getName());
    total+=read;out.put(e.getName(),ApkEngine.hex(md.digest()));
   }
  }catch(NoSuchAlgorithmException e){throw new IOException(e);}
  if(!out.containsKey("AndroidManifest.xml"))throw new IOException("Missing manifest");
  return out;
 }
 static void requireSame(Map<String,String> prepared,Map<String,String> signed)throws IOException{
  if(!prepared.equals(signed))throw new IOException("Unexpected content change during signing");
 }
 static String difference(Map<String,String> original,Map<String,String> signed){
  int same=0,changed=0,added=0,removed=0;
  for(String n:original.keySet()){if(!signed.containsKey(n))removed++;else if(original.get(n).equals(signed.get(n)))same++;else changed++;}
  for(String n:signed.keySet())if(!original.containsKey(n))added++;
  return "ملفات متطابقة: "+same+"\nملفات تغيرت: "+changed+"\nملفات أضيفت: "+added+"\nملفات حذفت: "+removed
   +"\nManifest مطابق: "+Objects.equals(original.get("AndroidManifest.xml"),signed.get("AndroidManifest.xml"));
 }
 static String fingerprint(java.security.cert.X509Certificate c)throws GeneralSecurityException{
  return ApkEngine.hex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded()));
 }
}
