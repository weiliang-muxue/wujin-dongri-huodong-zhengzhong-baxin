package cn.local.winterbear;

import cn.local.bullseye.FloatingGuard;
import cn.local.bullseye.FloatingGuard.Box;
import java.util.Collections;

/** Top-right control lane, accounting for actual system bars and display cutouts. */
final class HuntOverlayGuard {
    static float scale(int width,int topInset,float density){
        float available=Math.max(1,width*.33f-topInset-12);
        return Math.min(Math.min(density,width/440f),available/106f);
    }
    static Box place(Box panel,int width,int height,int left,int top,int right,int bottom){
        Box lane=new Box(Math.max(left,(int)Math.ceil(width*.32f)+1),top,
                width-right,Math.min(height-bottom,(int)Math.floor(width*.33f)-1));
        if(lane.width()<=0||lane.height()<=0)return null;
        return FloatingGuard.place(panel,lane,Collections.emptyList(),0);
    }
}
