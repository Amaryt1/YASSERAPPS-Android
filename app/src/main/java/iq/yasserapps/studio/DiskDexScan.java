package iq.yasserapps.studio;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Read-only DEX string-table inspection using two fixed-size page caches. */
final class DiskDexScan {
 static final int PAGE=65536;
 private static final class Reader {
  final RandomAccessFile file; final long length; final byte[] page=new byte[PAGE]; long base=-1; int valid;
  Reader(RandomAccessFile file,long length){this.file=file;this.length=length;}
  int u8(long p)throws IOException{
   if(p<0||p>=length)throw new EOFException("Invalid DEX offset");
   if(base<0||p<base||p>=base+valid){base=p-(p%PAGE);valid=(int)Math.min(PAGE,length-base);file.seek(base);file.readFully(page,0,valid);}
   return page[(int)(p-base)]&255;
  }
  long u32(long p)throws IOException{return (long)u8(p)|((long)u8(p+1)<<8)|((long)u8(p+2)<<16)|((long)u8(p+3)<<24);}
 }
 static void inspect(File file,String name,ApkEngine.Result result)throws IOException{
  try(RandomAccessFile f=new RandomAccessFile(file,"r")){
   long len=f.length();Reader table=new Reader(f,len),data=new Reader(f,len);
   if(len<112||table.u8(0)!='d'||table.u8(1)!='e'||table.u8(2)!='x'||table.u8(3)!='\n')throw new IOException("Invalid DEX");
   long count=table.u32(56),offset=table.u32(60);
   if(count>4000000||offset+count*4>len)throw new IOException("Invalid string table");
   byte[] value=new byte[8192];
   for(long i=0;i<count;i++){
    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("DEX scan cancelled");
    long p=table.u32(offset+4*i);
    if(p>=len)continue;
    boolean prefix=false;
    for(int k=0;k<5&&p<len;k++){if((data.u8(p++)&128)==0){prefix=true;break;}}
    if(!prefix)continue;
    int n=0;
    while(p<len&&n<value.length){int b=data.u8(p++);if(b==0){ApkEngine.add(result,name,new String(value,0,n,StandardCharsets.UTF_8));break;}value[n++]=(byte)b;}
   }
  }
 }
}
