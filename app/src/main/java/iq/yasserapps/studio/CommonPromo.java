package iq.yasserapps.studio;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
/** Recognizes only the optional LITEAPKS local preference implementation. */
public final class CommonPromo {
 static final int[] PATTERN={0x001a,-1,0x0112,0x306e,-1,0x0103,0x000c,0x011a,-1,0x1212,0x3072,-1,0x0210,0x000a,0x0038,0x0010,0x0022,-1,0x1070,-1,0x0000,0x106e,-1,0x0003,0x010c,0x021a,-1,0x306e,-1,0x0210,0x000e};
 static final String OWNER="Lcom/zx/LB;";
 static class Hit {final String name;final int offset;Hit(String n,int o){name=n;offset=o;}}
 static class D {
  final byte[] b;final ByteBuffer v;final int sc,so,tc,to,mc,mo,cc,co;
  D(byte[] data)throws IOException{b=data;v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);if(b.length<112||b[0]!='d'||b[1]!='e'||b[2]!='x'||b[3]!='\n'||i(32)!=b.length||i(40)!=0x12345678)throw new IOException("Unsupported DEX");sc=i(56);so=i(60);tc=i(64);to=i(68);mc=i(88);mo=i(92);cc=i(96);co=i(100);table(sc,so,4);table(tc,to,4);table(mc,mo,8);table(cc,co,32);}
  void range(int p,int n)throws IOException{if(p<0||n<0||(long)p+n>b.length)throw new IOException("Invalid DEX range");}
  int i(int p)throws IOException{range(p,4);return v.getInt(p);}
  int h(int p)throws IOException{range(p,2);return v.getShort(p)&65535;}
  void table(int n,int p,int width)throws IOException{if(n<0||n>4000000||(long)n*width>Integer.MAX_VALUE)throw new IOException("Invalid table");range(p,n*width);}
  int index(int x,int size)throws IOException{if(x<0||x>=size)throw new IOException("Invalid index");return x;}
  int leb(int[] p)throws IOException{long x=0;for(int k=0;k<5;k++){range(p[0],1);int n=b[p[0]++]&255;x|=(long)(n&127)<<(7*k);if((n&128)==0){if(x>Integer.MAX_VALUE)throw new IOException("Invalid ULEB");return (int)x;}}throw new IOException("Invalid ULEB");}
  String str(int x)throws IOException{int p=i(so+4*index(x,sc));int[] at={p};leb(at);int start=at[0],end=start;while(end<b.length&&b[end]!=0&&end-start<=4096)end++;if(end>=b.length||end-start>4096)throw new IOException("Invalid string");return new String(b,start,end-start,StandardCharsets.UTF_8);}
  String type(int x)throws IOException{return str(i(to+4*index(x,tc)));}
  String owner(int x)throws IOException{return type(h(mo+8*index(x,mc)));}
  String method(int x)throws IOException{return str(i(mo+8*index(x,mc)+4));}
  boolean methodIs(int x,String cls,String n)throws IOException{return cls.equals(owner(x))&&n.equals(method(x));}
  boolean body(int code,boolean patched)throws IOException{if(code==0)return false;range(code,16);if(h(code)!=4||h(code+2)!=1||h(code+4)!=3||h(code+6)!=0||i(code+12)!=31)return false;int p=code+16;range(p,62);int[] a=new int[31];for(int k=0;k<a.length;k++)a[k]=h(p+2*k);if(!pattern(a,patched))return false;
   return "MyPreferences".equals(str(a[1]))&&"show_dialog".equals(str(a[8]))&&"custom_dialog".equals(str(a[26]))&&OWNER.equals(type(a[17]))&&methodIs(a[4],"Landroid/app/Activity;","getSharedPreferences")&&methodIs(a[11],"Landroid/content/SharedPreferences;","getBoolean")&&methodIs(a[19],OWNER,"<init>")&&methodIs(a[22],"Landroid/app/Activity;","getFragmentManager")&&methodIs(a[28],OWNER,"show");}
  int locate(boolean patched)throws IOException{int found=-1;for(int c=0;c<cc;c++){int p=co+32*c;if(!OWNER.equals(type(i(p))))continue;int data=i(p+24);if(data==0)continue;int[] at={data};int sf=leb(at),inf=leb(at),dm=leb(at),vm=leb(at);if((long)sf+inf+dm+vm>1000000)throw new IOException("Invalid class data");for(int k=0;k<sf+inf;k++){leb(at);leb(at);}for(int count:new int[]{dm,vm}){int idx=0;for(int k=0;k<count;k++){int delta=leb(at);if((long)idx+delta>=mc)throw new IOException("Invalid method index");idx+=delta;leb(at);int code=leb(at);if("ll".equals(method(idx))&&OWNER.equals(owner(idx))&&body(code,patched)){if(found!=-1)throw new IOException("Ambiguous promo method");found=code+16+18;}}}}return found;}
 }
 static boolean pattern(int[] a,boolean patched){if(a.length!=PATTERN.length)return false;for(int k=0;k<a.length;k++){int expected=k==9&&patched?0x0212:PATTERN[k];if(expected>=0&&a[k]!=expected)return false;}return true;}
 static Hit find(File f,boolean patched)throws IOException{Hit hit=null;try(ZipFile z=new ZipFile(f)){Enumeration<? extends ZipEntry> en=z.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();if(!e.getName().matches("classes([0-9]+)?\\.dex"))continue;byte[] b=ApkEngine.read(z,e,ApkEngine.MAX_ENTRY);int at=new D(b).locate(patched);if(at>=0){if(hit!=null)throw new IOException("Ambiguous promo across DEX files");hit=new Hit(e.getName(),at);}}}return hit;}
 static byte[] read(File f,String n)throws IOException{try(ZipFile z=new ZipFile(f)){ZipEntry e=z.getEntry(n);if(e==null)throw new IOException("Missing DEX");return ApkEngine.read(z,e,ApkEngine.MAX_ENTRY);}}
 static byte[] patch(byte[] b,int p)throws Exception{byte[] n=b.clone();if(p<32||p+1>=n.length||(n[p]&255)!=0x12||(n[p+1]&255)!=0x12)throw new IOException("Unexpected promo instruction");n[p+1]=2;byte[] sha=MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(n,32,n.length));System.arraycopy(sha,0,n,12,20);Adler32 a=new Adler32();a.update(n,12,n.length-12);ByteBuffer.wrap(n).order(ByteOrder.LITTLE_ENDIAN).putInt(8,(int)a.getValue());return n;}
 static void apply(File src,File dst)throws Exception{Hit h=find(src,false);if(h==null)throw new IOException("Optional promo not supported");byte[] n=patch(read(src,h.name),h.offset);ApkEngine.repack(src,dst,h.name,n);verify(src,dst);}
 static void verify(File src,File dst)throws Exception{Hit a=find(src,false),b=find(dst,true);if(a==null||b==null||!a.name.equals(b.name)||a.offset!=b.offset||find(dst,false)!=null||!Arrays.equals(patch(read(src,a.name),a.offset),read(dst,b.name)))throw new IOException("Promo patch verification failed");}
}