package iq.yasserapps.studio;
import java.io.*;
import org.json.JSONObject;
/** Batch editing of developer-integrated optional branding profiles only. */
final class BatchExport {
 static final int MAX_FILES=20;
 static void prepare(File source,File unsigned,JSONObject preset)throws Exception{
  JSONObject original=WindowProfile.load(source);
  BrandCompatibility.require(source,original);
  JSONObject next=WindowProfile.replacement(original,preset);
  if(WindowProfile.same(original,next))throw new IOException("No profile change");
  WindowProfile.rewrite(source,unsigned,next);
  verify(unsigned,next);
 }
 static void verify(File output,JSONObject expected)throws Exception{
  JSONObject actual=WindowProfile.load(output);
  if(!WindowProfile.same(expected,actual))throw new IOException("Profile mismatch");
  BrandCompatibility.require(output,actual);
 }
 static String outputName(int index,String displayName){
  String n=displayName.replaceAll("[^\\p{L}\\p{N}._-]","_").replaceFirst("(?i)\\.apk$","");
  if(n.length()>65)n=n.substring(0,65);
  if(n.isEmpty())n="application";
  return String.format(java.util.Locale.ROOT,"%02d-%s-YASSER.apk",index,n);
 }
}
