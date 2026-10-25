package cn.local.winterbear;

import org.junit.Test;
import static org.junit.Assert.*;

public class GestureGateTest {
    @Test public void oldJoinTimeoutCannotCancelPendingDeployGesture() {
        GestureGate gate=new GestureGate();
        long join=gate.begin();assertTrue(gate.finish(join));
        long preset=gate.begin();assertTrue(gate.finish(preset));
        long deploy=gate.begin();
        assertFalse(gate.finish(join));assertFalse(gate.finish(preset));assertTrue(gate.finish(deploy));
    }
    @Test public void pauseInvalidatesCallbackEvenAfterAnotherGestureStarts() {
        GestureGate gate=new GestureGate();long old=gate.begin();gate.cancel();long next=gate.begin();
        assertFalse(gate.finish(old));assertTrue(gate.finish(next));
    }
    @Test public void realTimeoutConsumesOnlyItsOwnCallback() {
        GestureGate gate=new GestureGate();long ticket=gate.begin();
        assertTrue(gate.finish(ticket));assertFalse(gate.finish(ticket));
    }
}
