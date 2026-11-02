package cn.local.winterbear;
import cn.local.bullseye.FloatingGuard;
import cn.local.bullseye.FloatingGuard.Box;
import org.junit.Test;
import static org.junit.Assert.*;

public class BearOverlayGuardTest {
    @Test public void compactPanelLeavesAllBearControlsAndPopupVisible(){
        for(int w:new int[]{720,1080,1440})for(float density:new float[]{2f,2.75f,3f,3.5f,4f}){
            int h=w*20/9;float d=Math.min(density,w/360f);int gap=Math.round(8*d);
            Box panel=new Box(w*38/100,h*30/100,w*38/100+(int)(w*.34f),h*30/100+Math.round(134*d));
            Box clear=FloatingGuard.place(panel,new Box(0,Math.round(24*d),w,h-Math.round(24*d)),BearOverlayGuard.areas(w,h),gap);
            assertNotNull("width="+w+", density="+density,clear);
            assertFalse(FloatingGuard.blocked(clear,BearOverlayGuard.areas(w,h),gap));
            assertTrue(BearOverlayGuard.inputClear(clear,w*.5f,h*.36f,w*.5f,h*.72f,gap));
            assertTrue(BearOverlayGuard.inputClear(clear,w*.5f,h*.65f,w*.5f,h*.65f,gap));
        }
    }
    @Test public void dragOrChangedLayoutRejectsOldObservation(){
        assertTrue(BearOverlayGuard.current(5,5,false));
        assertFalse(BearOverlayGuard.current(5,6,false));
        assertFalse(BearOverlayGuard.current(5,5,true));
    }
    @Test public void tapAndSwipeNeverGoThroughVisibleControls(){
        Box panel=new Box(400,1700,800,2100);
        assertFalse(BearOverlayGuard.inputClear(panel,600,1800,600,1800,8));
        assertFalse(BearOverlayGuard.inputClear(panel,600,1600,600,2200,8));
        assertTrue(BearOverlayGuard.inputClear(panel,950,800,950,1400,8));
    }
}
