package cn.local.winterbear;
import cn.local.bullseye.FloatingGuard;
import cn.local.bullseye.FloatingGuard.Box;
import org.junit.Test;
import static org.junit.Assert.*;

public class BearOverlayGuardTest {
    @Test public void recordingPanelCannotBlockTheNextPageSwipe(){
        for(int[] size:new int[][]{{620,1280},{1080,2400},{1152,2376},{1344,2772},{1260,2800}}){
            int width=size[0],height=size[1],gap=Math.max(4,width/60);
            Box panel=new Box(width*45/100,height*58/100,width*80/100,height*74/100);
            assertFalse(BearOverlayGuard.inputClear(panel,width*.5f,height*.78f,width*.5f,height*.36f,gap));
            for(int page=0;page<8;page++){
                float[] swipe=BearOverlayGuard.nextPagePath(width,height,panel,gap);
                assertNotNull(swipe);assertTrue(swipe[1]>swipe[3]);
                assertTrue(BearOverlayGuard.inputClear(panel,swipe[0],swipe[1],swipe[2],swipe[3],gap));
            }
        }
    }
    @Test public void blockedSwipeRequestsOverlayRecovery(){
        assertNull(BearOverlayGuard.nextPagePath(1080,2400,new Box(0,900,1080,1500),16));
        assertNull(BearOverlayGuard.nextPagePath(1080,2400,null,16));
    }
    @Test public void giantSizeControlsFitTheBearProtectionAreas(){
        for(int[] size:new int[][]{{620,1280},{720,1600},{1080,2400},{1152,2376},{1344,2772},{1260,2800}}){
            int width=size[0],height=size[1];
            for(int top:new int[]{0,60,120}){
                float scale=FloatingGuard.controlScale(width,top,3f);
                Box panel=new Box(width*40/100,top,width*40/100+Math.round(width*.57f),top+8+Math.round(106*scale));
                Box clear=FloatingGuard.place(panel,new Box(0,top,width,height-60),BearOverlayGuard.areas(width,height),8);
                if(clear==null){
                    panel=new Box(panel.left,top,panel.left+Math.round(72*scale),top+8+Math.round(44*scale));
                    clear=FloatingGuard.place(panel,new Box(0,top,width,height-60),BearOverlayGuard.areas(width,height),8);
                }
                assertNotNull(width+"x"+height,clear);
                assertNotNull(BearOverlayGuard.nextPagePath(width,height,clear,8));
            }
        }
    }
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
    @Test public void huaweiReproducesOldFailureAndAdaptivePanelFits(){
        int w=1152,h=2376;float old=3.2f;
        Box screen=new Box(0,90,w,h-96);
        assertNull(FloatingGuard.place(new Box(440,710,440+(int)(w*.34f),710+BearOverlayGuard.panelHeight(old)),screen,BearOverlayGuard.areas(w,h),Math.round(8*old)));
        for(int[] dimensions:new int[][]{{1152,2376},{1344,2772},{1080,2400},{720,1600},{1440,3200},{1080,2160},{1080,1920}}){
            w=dimensions[0];h=dimensions[1];
            for(float density:new float[]{2,2.75f,3,3.5f,4,4.5f})for(int inset:new int[]{0,90,144}){
                float d=BearOverlayGuard.panelScale(w,h,density);int gap=Math.round(8*d);
                Box panel=new Box(w/3,h/3,w/3+(int)(w*.34f),h/3+BearOverlayGuard.panelHeight(d));
                Box clear=FloatingGuard.place(panel,new Box(0,inset,w,h-inset),BearOverlayGuard.areas(w,h),gap);
                assertNotNull(w+"x"+h+" density="+density+" inset="+inset,clear);
                assertFalse(FloatingGuard.blocked(clear,BearOverlayGuard.areas(w,h),gap));
                assertTrue(BearOverlayGuard.inputClear(clear,w*.5f,h*.36f,w*.5f,h*.72f,gap));
            }
        }
    }
    @Test public void xiaomiKeepsItsExistingPanelSize(){
        for(float density:new float[]{2,2.75f,3,3.5f,4})assertEquals(Math.min(density,3),BearOverlayGuard.panelScale(1080,2400,density),0);
    }
    @Test public void additionalPhoneAndFoldableFloatingBoundsFit(){
        // Geometry only: the Pura X game layout and container capabilities are not certified here.
        for(int[] size:new int[][]{{1080,2374},{1260,2800},{1320,2120},{1248,2004},{720,1156},{1080,2520},{1440,3120},{1080,2340},{1220,2712}}){
            int w=size[0],h=size[1];
            for(float density:new float[]{1,2,3,4,5})for(int inset:new int[]{0,60,120}){
                float scale=BearOverlayGuard.panelScale(w,h,density);
                Box clear=FloatingGuard.place(new Box(0,h/3,(int)(w*.34),h/3+BearOverlayGuard.panelHeight(scale)),new Box(0,inset,w,h-inset),BearOverlayGuard.areas(w,h),Math.round(8*scale));
                if(clear==null){
                    // Runtime collapses only if the expanded panel cannot fit.
                    clear=FloatingGuard.place(new Box(0,h/3,Math.round(72*scale),h/3+2*Math.round(6*scale)+Math.round(40*scale)),new Box(0,inset,w,h-inset),BearOverlayGuard.areas(w,h),Math.round(8*scale));
                }
                assertNotNull(w+"x"+h,clear);
                scale=HuntOverlayGuard.scale(w,inset,density);
                int panelHeight=8+Math.round(32*scale)+Math.round(30*scale)+Math.round(44*scale);
                clear=HuntOverlayGuard.place(new Box(0,0,(int)(w*.57),panelHeight),w,h,0,inset,0,inset);
                assertNotNull("hunt "+w+"x"+h+" inset "+inset,clear);
                assertTrue(clear.top>=inset&&clear.bottom<w*.33&&clear.left>w*.32&&clear.right<=w);
            }
        }
    }
    @Test public void referenceAnchorsRespectAspectRatioAndOriginalLayout(){
        ReferenceLayout original=new ReferenceLayout(1080,2400),huawei=new ReferenceLayout(1152,2376);
        for(int x:new int[]{98,209,319,431,542,654})assertEquals(x,original.x(x));
        for(int y:new int[]{307,427,607,635,2301,2364})assertEquals(y,original.y(y));
        assertEquals(455,huawei.y(427));assertEquals(2338,huawei.y(2364));
        assertEquals(668,huawei.feedbackHeight());assertEquals(720,original.feedbackHeight());
        assertFalse(ReferenceLayout.supported(2376,1152));assertFalse(ReferenceLayout.supported(1000,1000));
    }
}
