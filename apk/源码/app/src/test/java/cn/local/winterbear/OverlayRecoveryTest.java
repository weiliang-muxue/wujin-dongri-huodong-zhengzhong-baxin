package cn.local.winterbear;

import cn.local.bullseye.FloatingGuard.Box;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayRecoveryTest {
    @Test public void briefLayoutChangeDoesNotCollapse(){OverlayRecovery r=new OverlayRecovery();assertEquals(OverlayRecovery.Action.WAIT,r.blocked(0,false));assertEquals(OverlayRecovery.Action.WAIT,r.blocked(1000,false));}
    @Test public void prolongedClampingCollapsesOnce(){OverlayRecovery r=new OverlayRecovery();r.blocked(0,false);assertEquals(OverlayRecovery.Action.COLLAPSE,r.blocked(1250,false));assertEquals(OverlayRecovery.Action.WAIT,r.blocked(1500,true));}
    @Test public void perpetualLayoutOrCaptureInvalidationIsBounded(){OverlayRecovery r=new OverlayRecovery();r.blocked(0,false);assertEquals(OverlayRecovery.Action.STOP,r.blocked(6000,true));}
    @Test public void successfulFrameOrNewRunResetsDeadline(){OverlayRecovery r=new OverlayRecovery();r.blocked(0,true);r.reset();assertEquals(OverlayRecovery.Action.WAIT,r.blocked(10000,true));}
    @Test public void reportedHuaweiWindowClampRecoversWithCompactControl(){
        for(int w:new int[]{1080,1152,1344}){
            int h=Math.round(w*2376f/1152),minTop=Math.round(w*122f/1152);
            // The recording shows a 286px control at y=122 (1152px width),
            // while metrics allow a higher position than WindowManager accepts.
            Box actual=new Box(w*40/100,minTop,w*97/100,minTop+Math.round(w*286f/1152));
            OverlayRecovery retry=new OverlayRecovery();boolean collapsed=false,ready=false;
            for(long now=0;now<6000;now+=250){
                Box wanted=HuntOverlayGuard.place(actual,w,h,0,0,0,0);
                if(wanted!=null&&wanted.same(actual)){ready=true;break;}
                OverlayRecovery.Action a=retry.blocked(now,collapsed);assertNotEquals(OverlayRecovery.Action.STOP,a);
                if(a==OverlayRecovery.Action.COLLAPSE){collapsed=true;actual=new Box(actual.left,minTop,actual.left+w/6,minTop+w/9);}
                else if(wanted!=null){int y=Math.max(minTop,wanted.top);actual=new Box(wanted.left,y,wanted.right,y+actual.height());}
            }
            assertTrue("Should collapse after OEM clamp "+w,collapsed);assertTrue("Should resume capture "+w,ready);
        }
    }
}
