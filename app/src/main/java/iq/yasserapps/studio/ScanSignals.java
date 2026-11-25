package iq.yasserapps.studio;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
/** Heuristic read-only evidence, not proof of behavior or authorization. */
public final class ScanSignals {
 private static final Pattern URL=Pattern.compile("https?://[^\\s\\\"<>\\\\]{3,400}",Pattern.CASE_INSENSITIVE);
 private final Map<String,Set<String>> sources=new LinkedHashMap<>(),hosts=new TreeMap<>();
 private long strings; private boolean limited; private final EncodingStats encoding=new EncodingStats();
 public static String classify(String value){
  String s=value.toLowerCase(Locale.ROOT);
  boolean update=s.contains("update")||s.contains("upgrade")||s.contains("تحديث");
  if(s.contains("security update")||s.contains("security patch")||s.contains("security_update")||s.contains("security_patch")||s.contains("تحديث أمني"))return "أمني محتمل";
  if(update&&(s.contains("mandatory")||s.contains("force")||s.contains("required")||s.contains("إجباري")))return "إلزامي محتمل";
  if(update&&(s.contains("optional")||s.contains("later")||s.contains("remind")||s.contains("اختياري")))return "اختياري محتمل";
  if(update)return "تحديث غير محدد";
  if(s.contains("telegram")||s.contains("t.me/")||s.contains("subscribe")||s.contains("تيليجرام")||s.contains("اشتراك")||s.contains("promotion")||s.contains("promo_dialog"))return "قناة أو ترويج محتمل";
  if(s.contains("dialog")||s.contains("popup")||s.contains("نافذة"))return "نافذة محتملة";
  return "";
 }
 private void mark(Map<String,Set<String>> map,String key,String category,int limit){
  Set<String> set=map.get(key);
  if(set==null){if(map.size()>=limit){limited=true;return;}set=new LinkedHashSet<>();map.put(key,set);}
  set.add(category);
 }
 public void observe(String source,String value){
  strings++;
  if(value==null||value.length()>8192)return;
  encoding.observe(value);
  String category=classify(value);
  if(!category.isEmpty())mark(sources,source,category,256);
  if(value.length()>2&&value.charAt(0)=='L'&&value.endsWith(";")){
   String simple=value.substring(value.lastIndexOf('/')+1,value.length()-1).toLowerCase(Locale.ROOT);
   if(simple.contains("dialog")||simple.contains("popup")||simple.contains("window"))mark(sources,source,"نوع واجهة مرشح",256);
   if(simple.equals("strings")||simple.equals("colors")||simple.equals("dimens")||simple.equals("base64image"))mark(sources,source,"مكوّن موارد مرشح",256);
  }
  String lower=value.toLowerCase(Locale.ROOT);
  if(!lower.contains("http://")&&!lower.contains("https://"))return;
  Matcher m=URL.matcher(value);
  while(m.find())try{
   URI u=URI.create(m.group());String host=u.getHost();if(host==null)continue;
   host=host.toLowerCase(Locale.ROOT);
   String c=classify(host+(u.getRawPath()==null?"":u.getRawPath()));
   if(!c.isEmpty())mark(hosts,host,c,150);
  }catch(IllegalArgumentException ignored){}
 }
 public String report(){
  StringBuilder b=new StringBuilder("تحليل المؤشرات — قراءة فقط\nالنصوص المفحوصة: ").append(strings).append('\n');
  for(Map.Entry<String,Set<String>> e:sources.entrySet())b.append(e.getKey()).append(": ").append(String.join("، ",e.getValue())).append('\n');
  b.append("\nمضيفو الروابط المرشحة (دون مسارات أو استعلامات):\n");
  for(Map.Entry<String,Set<String>> e:hosts.entrySet())b.append(e.getKey()).append(": ").append(String.join("، ",e.getValue())).append('\n');
  if(limited)b.append("حد عرض المؤشرات: بعض النتائج لم تُدرج.\n");
  return b.append("التصنيف تقديري؛ لا يثبت وظيفة الكود أو أمان تعطيل التحديث. لا يتصل الفحص بأي سيرفر ولا يغير الملف.").append("\n\n").append(encoding.report()).toString();
 }
}
