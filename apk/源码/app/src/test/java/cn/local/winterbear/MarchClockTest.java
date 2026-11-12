package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
public final class MarchClockTest {
    @Test public void completeClockSupportsSpacingAndFullWidthColon(){
        assertEquals(16702,CountdownDigits.clockText("04:38:22"));
        assertEquals(31,CountdownDigits.clockText("00 ： 00 ： 31"));
    }
    @Test public void partialInvalidAndUnrelatedTextStaysUnknown(){
        for(String value:new String[]{null,"","5/6","04:38","4:38:22","00:60:01","00:01:99","采集中04:38:22","04:38:22 00:00:31","04;38;22"})assertEquals(value,-1,CountdownDigits.clockText(value));
    }
}
