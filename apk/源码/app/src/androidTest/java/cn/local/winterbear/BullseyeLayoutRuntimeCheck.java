package cn.local.winterbear;

import android.graphics.*;
import java.io.File;
import cn.local.bullseye.LiveLayout;
import cn.local.bullseye.Vision;
import cn.local.automation.VisualLocator;
import org.opencv.core.*;

/** Actual Android pixels with a real activity home and an explicitly synthetic 12-target board. */
public final class BullseyeLayoutRuntimeCheck {
    static int checks;
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static cn.local.bullseye.Config config(Bitmap board){
        cn.local.bullseye.Config c=new cn.local.bullseye.Config();c.width=board.getWidth();c.height=board.getHeight();
        c.targets=Vision.targets(board);require(c.targets.size()==12,"Reference target grid");
        float dx=c.targets.get(1).x-c.targets.get(0).x,dy=c.targets.get(4).y-c.targets.get(0).y;
        c.region=new float[]{Math.max(0,c.targets.get(0).x-.75f*dx),Math.max(0,c.targets.get(0).y-3.9f*dy),Math.min(1,c.targets.get(3).x+.75f*dx),Math.min(1,c.targets.get(8).y+.9f*dy)};
        c.start=new PointF(.5f,2040f/2400);c.countRect=new float[]{.82f,660f/2400,.88f,705f/2400};c.exitRect=Vision.exitRegion(c.targets);return c;
    }
    static Bitmap moved(Bitmap b,int width,int height,int dx,int dy){
        Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.BLACK);
        c.drawBitmap(b,null,new android.graphics.Rect(dx,dy,dx+width,dy+Math.round(b.getHeight()*width/(float)b.getWidth())),new Paint(Paint.FILTER_BITMAP_FLAG));return out;
    }
    static void point(PointF source,PointF actual,int originalWidth,int originalHeight,int w,int h,int dx,int dy,String what){
        float s=w/(float)originalWidth;
        require(Math.abs(actual.x*w-(source.x*originalWidth*s+dx))<8&&Math.abs(actual.y*h-(source.y*originalHeight*s+dy))<8,what+" stale coordinate "+actual);
    }
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        Bitmap home=BitmapFactory.decodeFile(new File(args[1],"bullseye-home.png").getPath()),board=BitmapFactory.decodeFile(new File(args[1],"bullseye-board.png").getPath());
        require(home!=null&&board!=null,"Private bullseye fixtures");cn.local.bullseye.Config c=config(board);
        require(c.matchesReferences(home,board),"Saved reference images rejected");
        try(LiveLayout v=new LiveLayout(c,home,board)){
            for(int[] size:new int[][]{{1080,2400,0,0},{1080,2520,24,72},{720,1680,-12,48},{1344,2772,0,30},{1152,2376,0,0}}){
                int w=size[0],h=size[1],dx=size[2],dy=size[3];
                for(boolean isHome:new boolean[]{true,false}){
                    Bitmap frame=moved(isHome?home:board,w,h,dx,dy);
                    try{
                        LiveLayout.Result r=v.observe(frame);require(r!=null,"Missing layout "+w+" home="+isHome);require(r.home==isHome,"Wrong page home="+isHome);
                        Bitmap exit=Vision.crop(frame,r.config.exitRect);require(exit.getWidth()>0&&exit.getHeight()>0,"Clipped exit region");if(exit!=frame)exit.recycle();
                        android.graphics.Rect text=Vision.textRegion(frame,r.config.exitRect);
                        if(text!=null)require(text.left>=0&&text.top>=0&&text.right<=w&&text.bottom<=h&&text.width()>=32&&text.height()>=32,"Unsafe OCR input");
                        if(r.config.exitRect[1]>=1)require(text==null,"Off-screen prompt sent to OCR");
                        point(new PointF(c.countRect[0],c.countRect[1]),new PointF(r.config.countRect[0],r.config.countRect[1]),c.width,c.height,w,h,dx,dy,"Ticket region");
                        if(isHome)point(c.start,r.config.start,c.width,c.height,w,h,dx,dy,"Start button");
                        else for(int i=0;i<12;i++){
                            point(c.targets.get(i),r.config.targets.get(i),c.width,c.height,w,h,dx,dy,"Target "+i);
                            Bitmap actual=Vision.patch(frame,r.config.targets.get(i),.018f),original=Vision.patch(board,c.targets.get(i),.018f);
                            try{require(Vision.similarity(actual,original)>.72,"Unopened target rejected after relocation: "+i+" at "+w+"x"+h);}
                            finally{actual.recycle();original.recycle();}
                        }
                        checks++;System.out.println("BULLSEYE LAYOUT PASS: "+w+"x"+h+" home="+isHome);System.out.flush();
                    }finally{frame.recycle();}
                }
            }
            Bitmap full=moved(board,1080,2520,24,72);v.observe(full);
            Bitmap partial=full.copy(Bitmap.Config.ARGB_8888,true);Paint mask=new Paint();mask.setColor(Color.BLACK);
            new Canvas(partial).drawRect(40,1460,220,1650,mask);
            LiveLayout.Result r=v.observe(partial);require(r!=null&&!r.home,"Partial board lost layout");
            for(int i=0;i<12;i++)point(c.targets.get(i),r.config.targets.get(i),c.width,c.height,1080,2520,24,72,"Partial target "+i);
            checks++;full.recycle();partial.recycle();
            Bitmap wrong=Bitmap.createBitmap(1080,2400,Bitmap.Config.ARGB_8888);require(v.observe(wrong)==null,"Wrong page accepted");checks++;wrong.recycle();
        }
        Mat flat=Mat.zeros(10,10,CvType.CV_8UC1),screen=Mat.ones(30,30,CvType.CV_8UC1);
        require(VisualLocator.unique(screen,flat,new org.opencv.core.Rect(-50,-50,200,200),.8,false)==null,"Flat template matched");checks++;flat.release();screen.release();
        Bitmap small=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        require(!c.matchesReferences(home,small)&&!c.matchesReferences(small,board),"Mismatched reference images accepted");
        require(Vision.textRegion(small,new float[]{0,1,1,1.2f})==null,"Off-screen OCR region");
        require(Vision.textRegion(small,new float[]{0,0,1,.1f})==null,"Undersized OCR region");
        require(Vision.textRegion(small,new float[]{Float.NaN,0,1,1})==null,"Invalid OCR region");
        require(new android.graphics.Rect(16,16,48,48).equals(Vision.textRegion(small,new float[]{.25f,.25f,.75f,.75f})),"Minimum readable OCR region");
        require(new android.graphics.Rect(0,0,64,64).equals(Vision.textRegion(small,new float[]{-.2f,-.2f,1.2f,1.2f})),"Clipped OCR region");checks++;small.recycle();
        home.recycle();board.recycle();
        System.out.println("ANDROID BULLSEYE LAYOUT PASS: "+checks+" image checks; synthetic board and layout variants; no gestures or OCR claims");
    }
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
}
