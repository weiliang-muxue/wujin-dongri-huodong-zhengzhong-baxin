package cn.local.winterbear;

import cn.local.bullseye.FloatingGuard;
import cn.local.bullseye.FloatingGuard.Box;
import java.util.ArrayList;
import java.util.List;

/** Critical bear cues and gesture paths in screen pixels, independent of Android Views. */
final class BearOverlayGuard {
    // Keep the existing size when it fits. Wider phones need a shorter panel, not
    // a different placement rule or repeated hide/show around every screenshot.
    static float panelScale(int w,int h,float density){
        float scale=Math.min(density,w/360f);
        int lane=(int)Math.floor(h*.91f)-(int)Math.ceil(h*.721f);
        while(panelHeight(scale)+2*Math.round(8*scale)>lane-2&&scale>.1f)scale-=.01f;
        return scale;
    }
    static int panelHeight(float scale){
        return 2*Math.round(6*scale)+Math.round(40*scale)+Math.round(32*scale)+Math.round(48*scale)+Math.round(2*scale);
    }
    static List<Box> areas(int w,int h){
        List<Box> a=new ArrayList<>();
        a.add(FloatingGuard.normalized(w,h,0,0,1,.29f)); // back, headers, flags, troop count
        a.add(FloatingGuard.normalized(w,h,0,.18f,.37f,.91f)); // march and bear targets
        a.add(FloatingGuard.normalized(w,h,.80f,.15f,1,.91f)); // red entry and green +
        a.add(FloatingGuard.normalized(w,h,0,.455f,1,.695f)); // overflow text and confirmation
        a.add(FloatingGuard.normalized(w,h,0,.91f,1,1)); // deploy, world and alliance
        a.add(FloatingGuard.normalized(w,h,.28f,.83f,.405f,.905f)); // map search anchor
        a.add(FloatingGuard.normalized(w,h,.49f,.36f,.51f,.721f)); // list swipe
        return a;
    }
    static boolean current(long captured,long revision,boolean dragging){return FloatingGuard.current(captured,revision,dragging);}
    static boolean inputClear(Box overlay,float x1,float y1,float x2,float y2,int gap){
        if(overlay==null)return false;
        Box path=new Box((int)Math.floor(Math.min(x1,x2)),(int)Math.floor(Math.min(y1,y2)),
                (int)Math.ceil(Math.max(x1,x2))+1,(int)Math.ceil(Math.max(y1,y2))+1);
        return !overlay.padded(gap).overlaps(path);
    }
}
