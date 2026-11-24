package iq.yasserapps.studio;
import org.junit.Test;import static org.junit.Assert.*;
public class FocusedScanTest{
 @Test public void groupsRelevantIndicators(){ApkEngine.Result r=new ApkEngine.Result();r.findings.add("asset: telegram invite");r.findings.add("asset: update available");assertTrue(FocusedScan.report(r,"inventory",true).contains("asset: update available"));assertFalse(FocusedScan.report(r,"inventory",true).contains("asset: telegram invite"));assertTrue(FocusedScan.report(r,"inventory",false).contains("asset: telegram invite"));}
}