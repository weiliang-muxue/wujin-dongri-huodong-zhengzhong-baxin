package cn.local.bullseye;

import java.util.ArrayList;
import java.util.List;

/** Screen-pixel geometry shared by overlay placement, capture masking and input checks. */
final class FloatingGuard {
    static final class Box {
        final int left,top,right,bottom;
        Box(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        int width(){return right-left;}
        int height(){return bottom-top;}
        boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
        boolean overlaps(Box b){return left<b.right&&right>b.left&&top<b.bottom&&bottom>b.top;}
        Box padded(int n){return new Box(left-n,top-n,right+n,bottom+n);}
        boolean same(Box b){return b!=null&&left==b.left&&top==b.top&&right==b.right&&bottom==b.bottom;}
    }
    static Box normalized(int w,int h,float l,float t,float r,float b){
        return new Box(Math.max(0,(int)Math.floor(l*w)),Math.max(0,(int)Math.floor(t*h)),
                Math.min(w,(int)Math.ceil(r*w)),Math.min(h,(int)Math.ceil(b*h)));
    }
    static List<Box> protectedAreas(int w,int h,float[] count,float[] exit,float sx,float sy,float[] xs,float[] ys){
        List<Box> result=new ArrayList<>();
        result.add(normalized(w,h,count[0],count[1],count[2],count[3]));
        result.add(normalized(w,h,exit[0],exit[1],exit[2],exit[3]));
        result.add(normalized(w,h,sx-.025f,sy-.025f,sx+.025f,sy+.025f));
        for(int i=0;i<xs.length;i++)result.add(normalized(w,h,xs[i]-.018f,ys[i]-.018f,xs[i]+.018f,ys[i]+.018f));
        // Entire multiplier search and price regions, including the one-ticket fallback.
        float dx=xs[1]-xs[0],dy=ys[4]-ys[0];
        result.add(normalized(w,h,sx+.65f*dx,sy-.38f*dy,sx+1.9f*dx,sy+.55f*dy));
        result.add(normalized(w,h,sx-.08f*dx,sy+.10f*dy,sx+.40f*dx,sy+.38f*dy));
        return result;
    }
    static boolean blocked(Box overlay,List<Box> areas,int gap){
        if(overlay==null)return true;
        for(Box b:areas)if(overlay.padded(gap).overlaps(b))return true;
        return false;
    }
    static boolean current(long captured,long current,boolean dragging){return !dragging&&captured==current;}
    static Box place(Box wanted,Box screen,List<Box> areas,int gap){
        int w=wanted.width(),h=wanted.height();
        if(w>screen.width()||h>screen.height())return null;
        List<Integer> xs=new ArrayList<>(),ys=new ArrayList<>();
        xs.add(wanted.left);xs.add(screen.left);xs.add(screen.right-w);
        ys.add(wanted.top);ys.add(screen.top);ys.add(screen.bottom-h);
        for(Box b:areas){xs.add(b.left-gap-w);xs.add(b.right+gap);ys.add(b.top-gap-h);ys.add(b.bottom+gap);}
        Box best=null;long distance=Long.MAX_VALUE;
        for(int x:xs)for(int y:ys){
            x=Math.max(screen.left,Math.min(screen.right-w,x));y=Math.max(screen.top,Math.min(screen.bottom-h,y));
            Box candidate=new Box(x,y,x+w,y+h);
            long dx=(long)x-wanted.left,dy=(long)y-wanted.top,d=dx*dx+dy*dy;
            if(d<distance&&!blocked(candidate,areas,gap)){best=candidate;distance=d;}
        }
        return best;
    }
}
