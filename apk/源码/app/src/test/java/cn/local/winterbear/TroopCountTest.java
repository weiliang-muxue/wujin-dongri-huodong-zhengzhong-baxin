package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
public class TroopCountTest {
    @Test public void parsesGroupedNumbers(){TroopCount c=TroopCount.parse("152,810 / 179,810");assertNotNull(c);assertTrue(c.usable());assertEquals(152810,c.selected);}
    @Test public void supportsFullWidthSlashAndWarning(){assertTrue(TroopCount.parse("1／139400!").usable());}
    @Test public void acceptsOnlyGroupedDots(){assertEquals(2840,TroopCount.parse("2.840/4.370").selected);assertNull(TroopCount.parse("12.34/4000"));assertNull(TroopCount.parse("1,2/4000"));}
    @Test public void emptyAndOverflowAreNotUsable(){assertFalse(TroopCount.parse("0/139400").usable());assertFalse(TroopCount.parse("200000/139400").usable());}
    @Test public void noGuessingAmbiguousDigits(){assertNull(TroopCount.parse("S/139400"));assertNull(TroopCount.parse("152810"));assertNull(TroopCount.parse("5/6"));assertNull(TroopCount.parse("152/810/179810"));}
}
