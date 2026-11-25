package iq.yasserapps.studio;
import java.io.*;import java.util.*;import java.util.zip.*;
import org.json.JSONObject;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.immutable.instruction.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.rewriter.*;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
/** Explicitly authorized replacement of the recognized optional promo entry point. */
public final class LegacyBrand{
 static final String OWNER=CommonPromo.OWNER,WIN="Liq/yasserapps/studio/ReplacementWindow;";
 static final String TEMPLATE="__YASSERAPPS_BRAND_TEMPLATE__";
 static boolean member(String type){return WIN.equals(type)||type.startsWith(WIN.substring(0,WIN.length()-1)+"$");}
 static boolean target(MethodReference m){return OWNER.equals(m.getDefiningClass())&&"ll".equals(m.getName())&&"V".equals(m.getReturnType())&&m.getParameterTypes().size()==1&&"Landroid/app/Activity;".contentEquals(m.getParameterTypes().get(0));}
 static boolean launch(MethodReference m){return WIN.equals(m.getDefiningClass())&&"launch".equals(m.getName())&&"V".equals(m.getReturnType())&&m.getParameterTypes().size()==1&&"Landroid/app/Activity;".contentEquals(m.getParameterTypes().get(0));}
 static boolean profile(MethodReference m){return WIN.equals(m.getDefiningClass())&&"embeddedProfile".equals(m.getName())&&"Ljava/lang/String;".equals(m.getReturnType())&&m.getParameterTypes().isEmpty();}
 static MethodImplementation bridge(){return new ImmutableMethodImplementation(1,Arrays.asList(new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,0,0,0,0,0,new ImmutableMethodReference(WIN,"launch",Collections.singletonList("Landroid/app/Activity;"),"V")),new ImmutableInstruction10x(Opcode.RETURN_VOID)),null,null);}
 static MethodImplementation literal(String s){return new ImmutableMethodImplementation(1,Arrays.asList(new ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO,0,new ImmutableStringReference(s)),new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0)),null,null);}
 static boolean dexName(String name){return name.matches("classes([0-9]+)?\\.dex");}
 static void require(File src)throws IOException{if(CommonPromo.find(src,false)==null&&CommonPromo.find(src,true)==null)throw new IOException("Unsupported optional promotion");}
 static DexFile rewrite(DexFile dex,boolean template,String json){
  return new DexRewriter(new RewriterModule(){@Override public Rewriter<Method> getMethodRewriter(Rewriters rs){return new MethodRewriter(rs){@Override public Method rewrite(Method m){if(template?!profile(m):!target(m))return m;return new RewrittenMethod(m){@Override public MethodImplementation getImplementation(){return template?literal(json):bridge();}};}};}}).getDexFileRewriter().rewrite(dex);
 }
 static String readLiteral(Method m)throws IOException{if(m.getImplementation()==null)throw new IOException("Missing profile code");Iterator<? extends Instruction> it=m.getImplementation().getInstructions().iterator();if(!it.hasNext())throw new IOException("Missing literal");Instruction i=it.next();if(!(i instanceof ReferenceInstruction)||!(((ReferenceInstruction)i).getReference() instanceof StringReference))throw new IOException("Invalid profile literal");return ((StringReference)((ReferenceInstruction)i).getReference()).getString();}
 static Set<ClassDef> payload(File studio,String json)throws IOException{Set<ClassDef> out=new LinkedHashSet<>();boolean marker=false;try(ZipFile z=new ZipFile(studio)){Enumeration<? extends ZipEntry> en=z.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();if(!dexName(e.getName()))continue;DexFile d=DexTools.load(z,e);for(ClassDef c:d.getClasses())if(WIN.equals(c.getType()))for(Method m:c.getMethods())if(profile(m)&&TEMPLATE.equals(readLiteral(m)))marker=true;for(ClassDef c:rewrite(d,true,json).getClasses())if(member(c.getType()))out.add(c);}}if(!marker||out.isEmpty())throw new IOException("Missing bundled window runtime");return out;}
 static String apply(File src,File dst,File studio,JSONObject n)throws Exception{
  WindowProfile.parse(n.toString());CommonPromo.Hit hit=CommonPromo.find(src,false);if(hit==null)hit=CommonPromo.find(src,true);if(hit==null)throw new IOException("Unsupported optional promotion");Set<ClassDef> injected=payload(studio,n.toString());byte[] bytes;
  try(ZipFile z=new ZipFile(src)){Enumeration<? extends ZipEntry> en=z.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();if(!dexName(e.getName()))continue;for(ClassDef c:DexTools.load(z,e).getClasses())if(member(c.getType()))throw new IOException("Window runtime already exists");}
   DexFile d=DexTools.load(z,z.getEntry(hit.name));int targets=0;for(ClassDef c:d.getClasses())for(Method m:c.getMethods())if(target(m)){if(!AccessFlags.STATIC.isSet(m.getAccessFlags())||m.getImplementation()==null)throw new IOException("Unexpected entry point");targets++;}if(targets!=1)throw new IOException("Ambiguous promo entry point");
   Set<ClassDef> all=new LinkedHashSet<>();all.addAll(rewrite(d,false,"").getClasses());all.addAll(injected);final Opcodes op=d.getOpcodes();DexFile combined=new DexFile(){public Set<? extends ClassDef> getClasses(){return all;}public Opcodes getOpcodes(){return op;}};
   MemoryDataStore store=new MemoryDataStore();try{DexPool.writeTo(store,combined);bytes=Arrays.copyOf(store.getBuffer(),store.getSize());}finally{store.close();}
  }
  ApkEngine.repack(src,dst,hit.name,bytes);verify(dst,hit.name,n);checkEntries(src,dst,hit.name);return hit.name;
 }
 static void verify(File f,String name,JSONObject n)throws Exception{int routes=0,profiles=0,launches=0;try(ZipFile z=new ZipFile(f)){ZipEntry e=z.getEntry(name);if(e==null)throw new IOException("Missing output DEX");for(ClassDef c:DexTools.load(z,e).getClasses())for(Method m:c.getMethods()){
  if(profile(m)){if(!WindowProfile.same(n,new JSONObject(readLiteral(m))))throw new IOException("Embedded identity mismatch");profiles++;}
  if(launch(m)&&m.getImplementation()!=null)launches++;
  if(target(m)){if(m.getImplementation()==null)throw new IOException("Missing entry");Iterator<? extends Instruction> it=m.getImplementation().getInstructions().iterator();if(!it.hasNext())throw new IOException("Empty entry");Instruction i=it.next();if(i.getOpcode()!=Opcode.INVOKE_STATIC||!(i instanceof ReferenceInstruction)||!(((ReferenceInstruction)i).getReference() instanceof MethodReference)||!launch((MethodReference)((ReferenceInstruction)i).getReference())||!it.hasNext()||it.next().getOpcode()!=Opcode.RETURN_VOID||it.hasNext())throw new IOException("Replacement not connected");routes++;}
 }}if(routes!=1||profiles!=1||launches!=1)throw new IOException("Replacement verification failed");}
 static boolean signature(String n){String u=n.toUpperCase(Locale.ROOT);return u.equals("META-INF/MANIFEST.MF")||u.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)");}
 static void checkEntries(File src,File dst,String changed)throws IOException{try(ZipFile a=new ZipFile(src);ZipFile b=new ZipFile(dst)){Enumeration<? extends ZipEntry> en=a.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();if(signature(e.getName())||e.getName().equals(changed))continue;ZipEntry other=b.getEntry(e.getName());if(other==null||other.getSize()!=e.getSize()||other.getCrc()!=e.getCrc())throw new IOException("Unexpected changed entry: "+e.getName());}en=b.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();if(!signature(e.getName())&&a.getEntry(e.getName())==null)throw new IOException("Unexpected added entry");}}}
}