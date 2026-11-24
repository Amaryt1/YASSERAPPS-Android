package iq.yasserapps.studio;
import java.io.*;import java.util.*;import java.util.zip.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.immutable.instruction.*;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.rewriter.*;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
public final class DexTools {
 public static final class Hit {final String dex,type,method;final int offset;Hit(String d,String t,String m,int o){dex=d;type=t;method=m;offset=o;} public String label(){return dex+" • "+type+" → "+method+" @"+offset;}}
 static String methodKey(Method m){return m.getName()+"("+String.join("",m.getParameterTypes())+")"+m.getReturnType();}
 static boolean match(Instruction i,String text){return (i.getOpcode()==Opcode.CONST_STRING||i.getOpcode()==Opcode.CONST_STRING_JUMBO)&&i instanceof ReferenceInstruction&&((ReferenceInstruction)i).getReference() instanceof StringReference&&((StringReference)((ReferenceInstruction)i).getReference()).getString().equals(text);}
 static DexBackedDexFile load(ZipFile z,ZipEntry e)throws IOException{byte[] b=ApkEngine.read(z,e,ApkEngine.MAX_ENTRY);return DexBackedDexFile.fromInputStream(null,new BufferedInputStream(new ByteArrayInputStream(b)));}
 static List<Hit> scan(String name,DexFile file,String text){List<Hit> hits=new ArrayList<>();for(ClassDef c:file.getClasses())for(Method m:c.getMethods()){MethodImplementation impl=m.getImplementation();if(impl==null)continue;int pos=0;for(Instruction i:impl.getInstructions()){if(match(i,text)){hits.add(new Hit(name,c.getType(),methodKey(m),pos));if(hits.size()>=1000)return hits;}pos+=i.getCodeUnits();}}return hits;}
 public static List<Hit> find(File apk,String text)throws IOException{if(text.isEmpty()||text.length()>2048)throw new IOException("Invalid search");List<Hit> out=new ArrayList<>();try(ZipFile z=new ZipFile(apk)){Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();ApkEngine.valid(e,seen);if(!e.getName().matches("classes([0-9]+)?\\.dex"))continue;out.addAll(scan(e.getName(),load(z,e),text));if(out.size()>=1000)break;}}return out;}
 public static void replace(File src,File dst,Hit target,String old,String next)throws IOException{if(old.isEmpty()||old.equals(next)||next.length()>2048||src.getCanonicalFile().equals(dst.getCanonicalFile()))throw new IOException("Invalid replacement");ApkEngine.scan(src);byte[] bytes;try(ZipFile z=new ZipFile(src)){ZipEntry e=z.getEntry(target.dex);if(e==null)throw new IOException("Missing DEX");DexBackedDexFile dex=load(z,e);final Instruction[] selected={null};for(ClassDef c:dex.getClasses())if(c.getType().equals(target.type))for(Method m:c.getMethods())if(methodKey(m).equals(target.method)&&m.getImplementation()!=null){int pos=0;for(Instruction i:m.getImplementation().getInstructions()){if(pos==target.offset&&match(i,old))selected[0]=i;pos+=i.getCodeUnits();}}
 if(selected[0]==null)throw new IOException("Target no longer matches");
 // Rewriter can enumerate instructions multiple times; compare method context and offset via method implementation rewriter, not object identity.
 DexRewriter rw=new DexRewriter(new RewriterModule(){@Override public Rewriter<Method> getMethodRewriter(Rewriters rs){return new MethodRewriter(rs){@Override public Method rewrite(Method m){if(!m.getDefiningClass().equals(target.type)||!methodKey(m).equals(target.method))return m;return new RewrittenMethod(m){@Override public MethodImplementation getImplementation(){MethodImplementation impl=m.getImplementation();return new MethodImplementation(){public int getRegisterCount(){return impl.getRegisterCount();}public Iterable<? extends Instruction> getInstructions(){List<Instruction> list=new ArrayList<>();int pos=0;for(Instruction i:impl.getInstructions()){if(pos==target.offset&&match(i,old)){int reg=((OneRegisterInstruction)i).getRegisterA();ImmutableStringReference s=new ImmutableStringReference(next);list.add(i.getOpcode()==Opcode.CONST_STRING?new ImmutableInstruction21c(Opcode.CONST_STRING,reg,s):new ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO,reg,s));}else list.add(i);pos+=i.getCodeUnits();}return list;}public List<? extends TryBlock<? extends ExceptionHandler>> getTryBlocks(){return impl.getTryBlocks();}public Iterable<? extends org.jf.dexlib2.iface.debug.DebugItem> getDebugItems(){return impl.getDebugItems();}};}};}};}});
 MemoryDataStore store=new MemoryDataStore();try{DexPool.writeTo(store,rw.getDexFileRewriter().rewrite(dex));bytes=Arrays.copyOf(store.getBuffer(),store.getSize());}finally{store.close();}
 }
 ApkEngine.repack(src,dst,target.dex,bytes);boolean ok=false;try{List<Hit> after=find(dst,next);for(Hit h:after)if(h.dex.equals(target.dex)&&h.type.equals(target.type)&&h.method.equals(target.method)){ok=true;break;}if(!ok)throw new IOException("Changed literal not found");}finally{if(!ok)dst.delete();}
 }
}