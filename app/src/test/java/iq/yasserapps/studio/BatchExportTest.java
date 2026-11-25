package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.zip.*;
import org.json.JSONObject;
public class BatchExportTest {
 @Test public void namesAreDistinctAndSafe(){assertEquals("01-foo-YASSER.apk",BatchExport.outputName(1,"foo.apk"));assertNotEquals(BatchExport.outputName(1,"foo.apk"),BatchExport.outputName(2,"foo.apk"));assertFalse(BatchExport.outputName(1,"../a.apk").contains("/"));}
 @Test public void supportedDeveloperProfilePreservesUpdateFlag()throws Exception{
  File src=new File("build/outputs/apk/debug/app-debug.apk"),out=File.createTempFile("batch-edit",".apk");
  try{JSONObject before=WindowProfile.load(src),preset=WindowProfile.parse(before.toString());preset.put("title","Batch fixture");BatchExport.prepare(src,out,preset);
   JSONObject after=WindowProfile.load(out);assertEquals("Batch fixture",after.getString("title"));assertEquals(before.getBoolean("optionalUpdateEnabled"),after.getBoolean("optionalUpdateEnabled"));assertTrue(after.getBoolean("replacementEnabled"));
  }finally{out.delete();}
 }
 @Test public void rejectsUnsupportedArchive()throws Exception{
  File src=File.createTempFile("unsupported",".apk"),out=File.createTempFile("batch-edit",".apk");
  try{try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(src))){z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(1);z.closeEntry();}
   try{BatchExport.prepare(src,out,new JSONObject());fail("Expected rejection");}catch(Exception expected){}
   assertEquals(0,out.length());
  }finally{src.delete();out.delete();}
 }
}
