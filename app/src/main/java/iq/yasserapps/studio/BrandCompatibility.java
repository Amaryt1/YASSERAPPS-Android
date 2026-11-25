package iq.yasserapps.studio;
import java.io.*;
import java.util.zip.*;
import java.util.*;
import org.json.JSONObject;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.MethodReference;
public final class BrandCompatibility {
 static final String RUNTIME="Liq/yasserapps/studio/BrandRuntime;",DIALOG="Liq/yasserapps/studio/BrandDialog;";
 static boolean call(MethodReference r){return RUNTIME.equals(r.getDefiningClass())&&"showOnLaunch".equals(r.getName())&&"V".equals(r.getReturnType())&&r.getParameterTypes().size()==1&&"Landroid/app/Activity;".contentEquals(r.getParameterTypes().get(0));}
 public static void require(File apk,JSONObject p)throws IOException{
  if(!"native-brand-v1".equals(p.optString("runtime")))throw new IOException("Missing developer runtime contract");
  boolean runtime=false,dialog=false,hostCall=false;
  try(ZipFile z=new ZipFile(apk)){Enumeration<? extends ZipEntry> en=z.entries();while(en.hasMoreElements()){
    ZipEntry e=en.nextElement();if(!e.getName().matches("classes([0-9]+)?\\.dex"))continue;
    for(ClassDef c:DexTools.load(z,e).getClasses()){
     if(DIALOG.equals(c.getType()))dialog=true;
     for(Method m:c.getMethods()){
      if(RUNTIME.equals(c.getType())&&call(m)&&m.getImplementation()!=null)runtime=true;
      if(RUNTIME.equals(c.getType())||m.getImplementation()==null)continue;
      for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction&&((ReferenceInstruction)i).getReference() instanceof MethodReference&&i.getOpcode().name().startsWith("INVOKE_STATIC")&&call((MethodReference)((ReferenceInstruction)i).getReference()))hostCall=true;
     }
    }
   }}
  if(!runtime||!dialog||!hostCall)throw new IOException("Missing integrated renderer or host invocation");
 }
}