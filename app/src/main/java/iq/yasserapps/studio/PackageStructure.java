package iq.yasserapps.studio;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** Bounded, read-only structural inventory. Does not execute or disassemble native code. */
final class PackageStructure {
 int layouts,xml,assets,images,nativeFiles,elfValid,elfUnknown,resourceTables,binaryXml,headerErrors,headerSkipped;
 long nativeBytes,resourceBytes;
 final Map<String,Integer> machines=new TreeMap<>();
 static final int MAX_HEADERS=4096;
 private static final java.util.regex.Pattern IMAGE=java.util.regex.Pattern.compile(".*\\.(png|webp|jpg|jpeg|gif)$");
 private int headers;
 private static int u16(byte[] b,int p,boolean le){return le?(b[p]&255)|((b[p+1]&255)<<8):((b[p]&255)<<8)|(b[p+1]&255);}
 private static long u32(byte[] b,int p){return (long)(b[p]&255)|((long)(b[p+1]&255)<<8)|((long)(b[p+2]&255)<<16)|((long)(b[p+3]&255)<<24);}
 static String machine(int id){switch(id){case 3:return "x86";case 40:return "ARM";case 62:return "x86_64";case 183:return "AArch64";case 243:return "RISC-V";default:return "ELF-"+id;}}
 private byte[] header(ZipFile z,ZipEntry e,int max)throws IOException{
  byte[] b=new byte[(int)Math.min(max,e.getSize())];try(InputStream in=z.getInputStream(e)){int pos=0;while(pos<b.length){int n=in.read(b,pos,b.length-pos);if(n<=0)throw new EOFException("Short header");pos+=n;}}return b;
 }
 void observe(ZipFile z,ZipEntry e){
  if(e.isDirectory())return;
  String n=e.getName().toLowerCase(Locale.ROOT);
  boolean lib=n.startsWith("lib/")&&n.endsWith(".so"),table=n.equals("resources.arsc"),resXml=n.equals("androidmanifest.xml")||(n.startsWith("res/")&&n.endsWith(".xml"));
  if(n.startsWith("assets/"))assets++;
  if(n.startsWith("res/")){resourceBytes+=e.getSize();if(n.startsWith("res/layout")&&n.endsWith(".xml"))layouts++;if(IMAGE.matcher(n).matches())images++;}
  if(table)resourceBytes+=e.getSize();
  if(resXml)xml++;
  if(lib){nativeFiles++;nativeBytes+=e.getSize();}
  if(!lib&&!table&&!resXml)return;
  if(headers++>=MAX_HEADERS){headerSkipped++;return;}
  try{
   byte[] b=header(z,e,64);
   if(lib){
    if(b.length<20||b[0]!=0x7f||b[1]!='E'||b[2]!='L'||b[3]!='F'||(b[4]!=1&&b[4]!=2)||(b[5]!=1&&b[5]!=2)){elfUnknown++;return;}
    int min=b[4]==1?52:64;if(b.length<min){elfUnknown++;return;}
    elfValid++;String k=machine(u16(b,18,b[5]==1))+" / "+(b[4]==1?"32-bit":"64-bit");machines.put(k,machines.getOrDefault(k,0)+1);
   }else if(b.length>=8){
    int kind=u16(b,0,true),hs=u16(b,2,true);long size=u32(b,4);
    if(hs>=8&&size>=hs&&size<=e.getSize()){
     if(table&&kind==2)resourceTables++;
     if(resXml&&kind==3)binaryXml++;
    }
   }
  }catch(IOException ex){headerErrors++;}
 }
 String report(){return "جرد بنية الحزمة — قراءة فقط\n"
  +"ملفات التخطيط: "+layouts+"\nXML: "+xml+" (ثنائي: "+binaryXml+")\nجداول الموارد المعروفة: "+resourceTables
  +"\nصور الموارد: "+images+"\nملفات Assets: "+assets+"\nحجم الموارد: "+resourceBytes
  +"\nمكتبات SO: "+nativeFiles+" (رؤوس ELF معروفة: "+elfValid+"، غير معروفة: "+elfUnknown+")\nمعماريات ELF: "+machines
  +"\nحجم المكتبات: "+nativeBytes+"\nرؤوس متجاوزة: "+headerSkipped+"، أخطاء القراءة: "+headerErrors
  +"\nالجرد يعتمد على أسماء الملفات ورؤوسها؛ لا يفكك تعليمات المكتبات ولا يثبت سلوك التطبيق.";}
}
