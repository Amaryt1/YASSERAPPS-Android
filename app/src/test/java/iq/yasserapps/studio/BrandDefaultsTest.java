package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import org.json.JSONObject;
public class BrandDefaultsTest{
 @Test public void bundledDesignIsValid()throws Exception{JSONObject p=WindowProfile.load(new File("build/outputs/apk/debug/app-debug.apk"));assertEquals("YASSERAPPS",p.getString("brand"));assertEquals("https://t.me/YasserAppsIQ",p.getString("channelUrl"));assertEquals("#090B09",p.getString("background"));assertEquals("#39FF7A",p.getString("accent"));assertTrue(p.getString("title").contains("ياسر"));}
}