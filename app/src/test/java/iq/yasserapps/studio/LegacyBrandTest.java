package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import org.jf.dexlib2.Opcode;
public class LegacyBrandTest {
 @Test public void bundledPayloadCanBeRead()throws Exception{File f=new File("build/outputs/apk/debug/app-debug.apk");String json=WindowProfile.load(f).toString();assertTrue(LegacyBrand.payload(f,json).size()>=2);}
 @Test public void bridgeCallsThenReturns(){java.util.Iterator<? extends org.jf.dexlib2.iface.instruction.Instruction> i=LegacyBrand.bridge().getInstructions().iterator();assertEquals(Opcode.INVOKE_STATIC,i.next().getOpcode());assertEquals(Opcode.RETURN_VOID,i.next().getOpcode());assertFalse(i.hasNext());}
 @Test public void unrelatedAppRejected()throws Exception{File f=new File("build/outputs/apk/debug/app-debug.apk");try{LegacyBrand.require(f);fail("Unexpected promotion match");}catch(java.io.IOException expected){}}
}