package cn.local.bullseye;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FloatingGuardTest {
    FloatingGuard.Box box(int l,int t,int r,int b){return new FloatingGuard.Box(l,t,r,b);}
    @Test public void clearPositionDoesNotMoveOnRepeatedCaptures(){
        FloatingGuard.Box original=box(12,48,252,192),screen=box(0,24,360,780);
        List<FloatingGuard.Box> areas=Arrays.asList(box(280,210,340,240),box(20,350,340,700));
        for(int i=0;i<100;i++)assertTrue(original.same(FloatingGuard.place(original,screen,areas,8)));
    }
    @Test public void coveringCounterMovesToNearestClearSpot(){
        FloatingGuard.Box overlay=box(100,200,300,300),counter=box(280,210,340,240);
        FloatingGuard.Box moved=FloatingGuard.place(overlay,box(0,24,360,780),Arrays.asList(counter),8);
        assertNotNull(moved);assertFalse(moved.same(overlay));assertFalse(FloatingGuard.blocked(moved,Arrays.asList(counter),8));
    }
    @Test public void noFreeSpaceDoesNotInventSafeCoordinates(){
        assertNull(FloatingGuard.place(box(0,0,200,100),box(0,0,360,780),Arrays.asList(box(0,0,360,780)),8));
    }
    @Test public void placementsStayWithinInsets(){
        FloatingGuard.Box placed=FloatingGuard.place(box(-20,-30,180,70),box(8,40,352,750),Collections.emptyList(),8);
        assertEquals(8,placed.left);assertEquals(40,placed.top);
    }
    @Test public void pendingCaptureIsDiscardedAfterDragEvenWhenDragEnded(){
        assertFalse(FloatingGuard.current(4,5,false));assertFalse(FloatingGuard.current(4,4,true));assertTrue(FloatingGuard.current(4,4,false));
    }
    @Test public void earlierMoveCannotCompleteDuringTheNextDrag(){
        long firstMove=1,nextMove=2;
        assertFalse(FloatingGuard.current(firstMove,nextMove,false));
        assertTrue(FloatingGuard.current(nextMove,nextMove,false));
    }
    @Test public void resumeDoesNotMakeAnOldCaptureCurrent(){
        long captured=8,afterPause=9;
        assertFalse(FloatingGuard.current(captured,afterPause,false));
        assertTrue(FloatingGuard.current(afterPause,afterPause,false));
    }
    @Test public void everyRecognitionAndInputAreaIsProtected(){
        float[] xs={.15f,.38f,.61f,.84f,.15f,.38f,.61f,.84f,.15f,.38f,.61f,.84f};
        float[] ys={.50f,.50f,.50f,.50f,.65f,.65f,.65f,.65f,.80f,.80f,.80f,.80f};
        List<FloatingGuard.Box> areas=FloatingGuard.protectedAreas(1080,2400,new float[]{.8f,.2f,.9f,.24f},new float[]{0,.87f,1,1},.5f,.8f,xs,ys);
        assertEquals(17,areas.size());
        for(FloatingGuard.Box area:areas){assertTrue(FloatingGuard.blocked(area,areas,0));assertTrue(area.width()>0&&area.height()>0);}
        assertFalse(FloatingGuard.blocked(box(30,100,650,420),areas,8));
    }
    @Test public void overlayBoundsRejectClicksIncludingMargin(){
        FloatingGuard.Box mask=box(100,100,300,200).padded(8);
        assertTrue(mask.contains(96,150));assertTrue(mask.contains(200,150));assertFalse(mask.contains(400,150));
    }
}
