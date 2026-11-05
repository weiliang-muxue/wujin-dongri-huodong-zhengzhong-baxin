package cn.local.automation;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayoutTransformTest {
    @Test public void followsObservedScaleAndPadding(){
        LayoutTransform t=LayoutTransform.between(10,20,210,120,27,64,267,184,1);
        assertNotNull(t);assertEquals(1.2,t.scale,1e-6);assertEquals(10,t.inverseX(t.x(10)),1e-6);
        assertEquals(20,t.inverseY(t.y(20)),1e-6);assertTrue(t.agrees(110,70,147,124,1));
    }
    @Test public void rejectsRotationAndConflictingAnchors(){
        assertNull(LayoutTransform.between(0,0,200,0,0,0,200,40,2));
        assertNull(LayoutTransform.between(0,0,200,0,200,0,0,0,2));
        assertNull(LayoutTransform.between(0,0,1,1,0,0,1,1,2));
    }
    @Test public void rejectsInvalidAndExtremeInput(){
        assertNull(LayoutTransform.between(0,0,200,0,0,0,1000,0,2));
        assertNull(LayoutTransform.between(0,0,200,0,Double.NaN,0,200,0,2));
        assertNull(LayoutTransform.between(0,0,200,0,0,0,200,0,-1));
    }
}
