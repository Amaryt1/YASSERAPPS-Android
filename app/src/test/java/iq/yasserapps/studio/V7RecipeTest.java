package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
public class V7RecipeTest {
 @Test public void xmlMatchesTestedPayload() throws Exception { assertEquals(572,OptionalRecipe.xml().length);assertEquals(OptionalRecipe.XML_NEW,OptionalRecipe.hash(OptionalRecipe.xml()));}
 @Test public void rejectsOtherVersions(){assertFalse(OptionalRecipe.matches("other"));assertTrue(OptionalRecipe.matches(OptionalRecipe.APK));}
 @Test public void rejectsUnknownDex() throws Exception {try {OptionalRecipe.transform(new byte[66712],true,true);fail("Accepted unknown DEX");}catch(java.io.IOException expected){}}
}