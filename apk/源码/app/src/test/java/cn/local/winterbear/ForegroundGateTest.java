package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
public class ForegroundGateTest {
    @Test public void shortNotificationDoesNotRequireRestart(){ForegroundGate g=new ForegroundGate();assertFalse(g.timedOut(false,1000));assertFalse(g.timedOut(false,2500));assertFalse(g.timedOut(true,3000));assertFalse(g.timedOut(false,6000));}
    @Test public void sustainedAppSwitchTimesOut(){ForegroundGate g=new ForegroundGate();assertFalse(g.timedOut(false,1000));assertTrue(g.timedOut(false,4000));}
}
