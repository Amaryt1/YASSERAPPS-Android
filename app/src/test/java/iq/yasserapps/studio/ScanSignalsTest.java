package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScanSignalsTest {
 @Test public void classificationsAreConservative(){
  assertEquals("أمني محتمل",ScanSignals.classify("optional security update"));
  assertEquals("إلزامي محتمل",ScanSignals.classify("mandatory update"));
  assertEquals("اختياري محتمل",ScanSignals.classify("optional update"));
  assertEquals("تحديث غير محدد",ScanSignals.classify("update"));
  assertEquals("قناة أو ترويج محتمل",ScanSignals.classify("telegram"));
 }
 @Test public void scansAfterDisplayCapAndRedactsUrlDetails(){
  ApkEngine.Result r=new ApkEngine.Result();
  for(int i=0;i<3000;i++)r.findings.add("fixture"+i);
  ApkEngine.add(r,"classes14.dex","https://updates.example.org/security/update?token=private");
  ApkEngine.add(r,"classes14.dex","Lsample/Dialog;");
  String s=r.signals.report();
  assertTrue(s.contains("classes14.dex"));
  assertTrue(s.contains("updates.example.org"));
  assertTrue(s.contains("نوع واجهة مرشح"));
  assertFalse(s.contains("token=private"));
  assertEquals(3000,r.findings.size());
 }
}
