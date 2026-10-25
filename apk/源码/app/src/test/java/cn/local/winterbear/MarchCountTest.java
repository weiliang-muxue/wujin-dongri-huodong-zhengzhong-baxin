package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
public class MarchCountTest {
    @Test public void acceptsEveryValidNumerator(){for(int i=0;i<=6;i++)assertEquals(i,MarchCount.used(i+"/6"));}
    @Test public void permitsWhitespaceAndFullwidthSlash(){assertEquals(5,MarchCount.used(" 5 ／ 6\n"));}
    @Test public void rejectsMissingOrAmbiguousEvidence(){for(String s:new String[]{"","5","/6","?/6","S/6","5/8","7/6","-1/6","15/6","5/6 6/6","行军5/6","152810/152810"})assertEquals(s,-1,MarchCount.used(s));}
    @Test public void nullIsUnknown(){assertEquals(-1,MarchCount.used(null));}
}
