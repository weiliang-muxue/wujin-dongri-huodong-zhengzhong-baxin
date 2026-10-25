package cn.local.winterbear;

/** Confirms two fresh real screenshots from the same foreground app and dimensions. */
final class LiveMapCheck {
    private String app="";
    private int width,height,used=-1;
    private long at;
    boolean accept(String foreground,int w,int h,boolean map,int count,long capturedAt,long now){
        return accept(foreground,w,h,map,count,false,capturedAt,now);
    }
    boolean accept(String foreground,int w,int h,boolean map,int count,boolean hidden,long capturedAt,long now){
        hidden=hidden&&!MarchCount.valid(count);
        if(hidden)count=-2;
        if(foreground==null||foreground.isEmpty()||w<=0||h<=0||!map||(!hidden&&(count<0||count>6))||now<capturedAt||now-capturedAt>4000){reset();return false;}
        boolean same=foreground.equals(app)&&w==width&&h==height&&count==used;
        if(same&&capturedAt-at>=500&&capturedAt-at<=6000){return true;}
        if(!same||capturedAt-at>6000){app=foreground;width=w;height=h;used=count;at=capturedAt;}
        return false;
    }
    void reset(){app="";used=-1;at=0;}
}
