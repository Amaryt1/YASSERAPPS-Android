package iq.yasserapps.studio;
import java.util.*;
public final class FocusedScan {
 public static String report(ApkEngine.Result result,String inventory,boolean update){StringBuilder b=new StringBuilder("تم العثور على مؤشرات نصية محتملة، لا دليل على منطق النافذة أو صلاحية تعديلها.\n");int count=0;for(String s:result.findings){String t=s.toLowerCase(Locale.ROOT);boolean match=update?(t.contains("update")||t.contains("تحديث")):(t.contains("telegram")||t.contains("t.me/")||t.contains("تيليجرام")||t.contains("subscribe"));if(match&&count++<60)b.append(s).append('\n');}if(count==0)b.append("لا توجد مطابقة في النصوص المفحوصة.\n");return b+"\nلا يوفّر APK إعدادات Studio المدعومة. لم يتم تعديل الملف.\n\n"+inventory+"\n\n"+result.signals.report();}
}