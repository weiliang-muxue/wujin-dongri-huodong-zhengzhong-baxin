package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
public class ModeGateTest {
    @Test public void switchingInvalidatesOldOwner(){ModeGate g=new ModeGate();long bear=g.select(ModeGate.Mode.BEAR);assertTrue(g.owns(ModeGate.Mode.BEAR,bear));long target=g.select(ModeGate.Mode.BULLSEYE);assertFalse(g.owns(ModeGate.Mode.BEAR,bear));assertTrue(g.owns(ModeGate.Mode.BULLSEYE,target));}
    @Test public void returningToChooserStopsBoth(){ModeGate g=new ModeGate();long target=g.select(ModeGate.Mode.BULLSEYE);g.select(ModeGate.Mode.NONE);assertFalse(g.owns(ModeGate.Mode.BULLSEYE,target));assertFalse(g.is(ModeGate.Mode.BEAR));}
    @Test public void restartingSameModeInvalidatesPreviousCallbacks(){ModeGate g=new ModeGate();long first=g.select(ModeGate.Mode.BEAR);g.select(ModeGate.Mode.BEAR);assertFalse(g.owns(ModeGate.Mode.BEAR,first));}
}
