package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
public class MarchCountTest {
    @Test public void acceptsEveryValidNumerator(){for(int i=0;i<=6;i++)assertEquals(i,MarchCount.used(i+"/6"));}
    @Test public void permitsWhitespaceAndFullwidthSlash(){assertEquals(5,MarchCount.used(" 5 ／ 6\n"));}
    @Test public void rejectsMissingOrAmbiguousEvidence(){for(String s:new String[]{"","5","/6","?/6","S/6","5/8","7/6","-1/6","15/6","5/6 6/6","行军5/6","152810/152810"})assertEquals(s,-1,MarchCount.used(s));}
    @Test public void nullIsUnknown(){assertEquals(-1,MarchCount.used(null));}
    @Test public void backgroundNameOnAnotherLineDoesNotHideCounter(){assertEquals(3,MarchCount.fromLines("3/6\n[Ben]军工大亨"));}
    @Test public void multipleCounterLinesRemainAmbiguous(){assertEquals(-1,MarchCount.fromLines("3/6\n4/6"));assertEquals(-1,MarchCount.fromLines("3/6\n3/6"));}
    @Test public void embeddedOrIncompleteCountersAreNotExtracted(){for(String s:new String[]{"行军3/6","[Ben]3/6","13/6","3/8","3/6 4/6","3\n/6"})assertEquals(s,-1,MarchCount.fromLines(s));}
}
