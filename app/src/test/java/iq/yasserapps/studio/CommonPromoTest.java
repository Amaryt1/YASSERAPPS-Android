package iq.yasserapps.studio;
import org.junit.Test;
import static org.junit.Assert.*;
public class CommonPromoTest {
 @Test public void exactSequenceAndDifferentReferences(){int[] a=CommonPromo.PATTERN.clone();for(int i=0;i<a.length;i++)if(a[i]<0)a[i]=500+i;assertTrue(CommonPromo.pattern(a,false));assertFalse(CommonPromo.pattern(a,true));a[9]=0x0212;assertTrue(CommonPromo.pattern(a,true));assertFalse(CommonPromo.pattern(a,false));a[14]=0x39;assertFalse(CommonPromo.pattern(a,true));}
 @Test public void refusesWrongLength(){assertFalse(CommonPromo.pattern(new int[30],false));}
 @Test public void checksumAndOneInstruction() throws Exception {byte[] a=new byte[100];a[70]=0x12;a[71]=0x12;byte[] b=CommonPromo.patch(a,70);assertEquals(2,b[71]);assertEquals(0x12,a[71]);for(int i=32;i<a.length;i++)if(i!=71)assertEquals(a[i],b[i]);}
}