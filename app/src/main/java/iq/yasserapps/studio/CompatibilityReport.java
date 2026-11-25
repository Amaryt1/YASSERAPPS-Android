package iq.yasserapps.studio;
import java.util.*;
import java.io.*;
/** Read-only compatibility assessment; never claims installation or editing success. */
final class CompatibilityReport {
 static String assess(ApkEngine.Result r,int deviceSdk,int minSdk,int targetSdk,String[] deviceAbis,boolean parsed){
  StringBuilder b=new StringBuilder("تشخيص التوافق التلقائي\n");
  b.append("Android API للجهاز: ").append(deviceSdk).append('\n');
  if(parsed&&minSdk>0){
   b.append("الحد الأدنى للتطبيق: ").append(minSdk).append(" / Target: ").append(targetSdk).append('\n');
   b.append(minSdk>deviceSdk?"تحذير: إصدار Android أقل من المطلوب\n":"الحد الأدنى لإصدار Android متوافق مبدئياً\n");
  }else b.append("تعذر تحديد الحد الأدنى من Manifest عبر النظام\n");
  if(r.abis.isEmpty())b.append("لا توجد مكتبات بمعماريات ABI معروفة في المسارات القياسية\n");
  else{
   Set<String> matching=new TreeSet<>(r.abis);matching.retainAll(Arrays.asList(deviceAbis));
   b.append(matching.isEmpty()?"تحذير: لا توجد ABI مشتركة مع قائمة الجهاز\n":"معماريات مشتركة: "+matching+"\n");
  }
  b.append("DEX المقروء: ").append(r.dexScanned).append(" / ").append(r.dex).append('\n');
  if(r.dex==0)b.append("لا يوجد DEX قياسي؛ قد تكون الحزمة موارد أو جزءاً من تطبيق\n");
  if(!r.scanWarnings.isEmpty())b.append("الفحص جزئي؛ لا تعتمد عليه كفحص كامل\n");
  if(r.structure.headerErrors>0||r.structure.headerSkipped>0||r.structure.elfUnknown>0)b.append("بعض رؤوس الموارد أو المكتبات لم تُعرَف أو لم تُفحص\n");
  if(r.nestedApks>0)b.append("ملفات APK داخلية: ").append(r.nestedApks).append("؛ لا يتم فحص محتواها تلقائياً\n");
  if(r.bytes>ApkEngine.MAX_TOTAL||r.largestDex>ApkEngine.MAX_ENTRY)b.append("الحجم يتجاوز حدود التحرير الحالية لبعض المحركات\n");
  return b.append("هذا تشخيص قراءة فقط؛ لا يثبت التوافق الكامل أو أمان التطبيق أو دعم تعديله.").toString();
 }
 static String error(Exception e){
  if(e instanceof InterruptedIOException)return "أُلغي العمل";
  if(e instanceof java.util.zip.ZipException)return "أرشيف APK غير صالح أو تالف";
  String m=e.getMessage();if(m==null||m.isEmpty())return e.getClass().getSimpleName();
  if(m.contains("APK container"))return "هذا أرشيف يحتوي ملفات APK؛ استخرج APK المطلوب أولاً";
  if(m.contains("No AndroidManifest"))return "الملف لا يحتوي AndroidManifest.xml";
  if(m.contains("Unsafe entry"))return "أرشيف يحوي مساراً غير صالح أو مكرراً";
  if(m.contains("Size limit")||m.contains("Archive too large"))return "تجاوز الملف حدود العملية الحالية";
  if(m.contains("Insufficient temporary storage"))return "مساحة التخزين المؤقت غير كافية";
  if(m.contains("Verification failed")||m.contains("certificate mismatch"))return "تعذر التحقق من التوقيع";
  return e.getClass().getSimpleName()+": "+m;
 }
}
