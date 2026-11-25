package iq.yasserapps.studio;
import java.io.*;
/** Sequential, bounded, read-only batch scan with one temporary APK at a time. */
final class BatchScan {
 static final int MAX_FILES=20;
 interface Source {InputStream open()throws IOException;}
 static String inspect(File dir,Source source,int sdk,String[] abis)throws IOException{
  File f=File.createTempFile("batch-inspect-",".apk",dir);
  try{
   String hash;
   try(InputStream in=source.open();OutputStream out=new BufferedOutputStream(new FileOutputStream(f),262144)){
    if(in==null)throw new IOException("Cannot open");hash=ApkEngine.copyAndHash(in,out,ApkEngine.MAX_INPUT_SCAN);
   }
   ApkEngine.Result r=ApkEngine.scanCopied(f,hash);
   return (r.scanWarnings.isEmpty()?"اكتمل فحص البنية":"فحص جزئي")+"\nSHA-256: "+r.sha256+"\nحجم APK: "+f.length()+" bytes\nعدد الملفات: "+r.entries+"\n"+CompatibilityReport.assess(r,sdk,0,0,abis,false)+"\n"+(r.scanWarnings.isEmpty()?"":String.join("\n",r.scanWarnings));
  }finally{f.delete();}
 }
}
