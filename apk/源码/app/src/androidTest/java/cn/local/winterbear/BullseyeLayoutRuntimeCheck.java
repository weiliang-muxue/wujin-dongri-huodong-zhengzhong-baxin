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
    static void targetSimilarity(Bitmap frame,Bitmap board,LiveLayout.Result layout,PointF original,int index){
        float radiusX=(float)(.018*layout.scale);
        float radiusY=radiusX*board.getHeight()/board.getWidth()*frame.getWidth()/frame.getHeight();
        Bitmap actual=Vision.patch(frame,layout.config.targets.get(index),radiusX,radiusY),reference=Vision.patch(board,original,.018f);
        try{require(Vision.similarity(actual,reference)>.72,"Unopened target rejected after relocation: "+index);}
        finally{actual.recycle();reference.recycle();}
    }
    static void buttonPlacement(Bitmap home,Bitmap board,cn.local.bullseye.Config c){
        try(LiveLayout v=new LiveLayout(c,home,board)){
            for(int[] variant:new int[][]{{58,0,0,2400},{80,90,100,3600},{100,0,0,4400}}){
                float factor=variant[0]/100f;int dx=variant[1],dy=variant[2],height=variant[3];
                float expectedX=c.start.x*home.getWidth()*factor+dx,expectedY=c.start.y*home.getHeight()*factor+dy;
                require(expectedY<height/2f,"Button regression must cross the old half-screen boundary");
                for(boolean isHome:new boolean[]{true,false}){
                    Bitmap source=isHome?home:board,frame=Bitmap.createBitmap(source.getWidth(),height,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(frame);canvas.drawColor(Color.BLACK);
                    canvas.drawBitmap(source,null,new android.graphics.Rect(dx,dy,dx+Math.round(source.getWidth()*factor),dy+Math.round(source.getHeight()*factor)),new Paint(Paint.FILTER_BITMAP_FLAG));
                    try{
                        LiveLayout.Result r=v.observe(frame);require(r!=null,"Upper button layout missing at "+variant[0]+"% height="+height);
                        require(r.home==isHome,"Upper button page mismatch at "+variant[0]+"% home="+isHome);
                        if(isHome){
                            require(Math.abs(r.config.start.x*frame.getWidth()-expectedX)<8,"Upper button X shifted");
                            require(Math.abs(r.config.start.y*frame.getHeight()-expectedY)<8,"Upper button Y shifted");
                        }else for(int i=0;i<12;i++){
                            PointF original=c.targets.get(i),actual=r.config.targets.get(i);
                            require(Math.abs(actual.x*frame.getWidth()-(original.x*board.getWidth()*factor+dx))<8,"Upper board target X "+i);
                            require(Math.abs(actual.y*frame.getHeight()-(original.y*board.getHeight()*factor+dy))<8,"Upper board target Y "+i);
                        }
                        checks++;System.out.println("BULLSEYE UPPER BUTTON PASS: "+variant[0]+"% height="+height+" home="+isHome);System.out.flush();
                    }finally{frame.recycle();}
                }
            }
            // Move only the complete button; the page header and ticket area stay fixed.
            Bitmap button=Bitmap.createBitmap(home,300,1960,480,200);
            try{
                for(int offset:new int[]{-120,120,-240}){
                    boolean duplicate=offset==-240;Bitmap frame=home.copy(Bitmap.Config.ARGB_8888,true);
                    try{
                        Canvas canvas=new Canvas(frame);Paint erase=new Paint();erase.setColor(Color.BLACK);
                        if(!duplicate)canvas.drawRect(300,1960,780,2160,erase);
                        canvas.drawBitmap(button,300,1960+offset,null);
                        LiveLayout.Result r=v.observe(frame);require(r!=null,"Button-only movement lost header layout");
                        require(r.home!=duplicate,"Moved/ambiguous button classified incorrectly: "+offset);
                        if(!duplicate){
                            require(Math.abs(r.config.start.x*frame.getWidth()-c.start.x*home.getWidth())<8,"Independently moved button X");
                            require(Math.abs(r.config.start.y*frame.getHeight()-(c.start.y*home.getHeight()+offset))<8,"Independently moved button Y");
                        }
                        checks++;System.out.println("BULLSEYE LOCAL BUTTON PASS: offset="+offset+" duplicate="+duplicate);System.out.flush();
                    }finally{frame.recycle();}
                }
            }finally{button.recycle();}
            Bitmap blank=Bitmap.createBitmap(1080,4400,Bitmap.Config.ARGB_8888);
            try{require(v.observe(blank)==null,"Blank padded page accepted");checks++;}finally{blank.recycle();}
        }
    }
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        Bitmap home=BitmapFactory.decodeFile(new File(args[1],"bullseye-home.png").getPath()),board=BitmapFactory.decodeFile(new File(args[1],"bullseye-board.png").getPath());
        require(home!=null&&board!=null,"Private bullseye fixtures");cn.local.bullseye.Config c=config(board);
        require(c.matchesReferences(home,board),"Saved reference images rejected");
        boolean zoomOnly=args.length>2&&"zoom".equals(args[2]),buttonOnly=args.length>2&&"button".equals(args[2]);
        if(!zoomOnly)buttonPlacement(home,board,c);
        if(buttonOnly){home.recycle();board.recycle();System.out.println("ANDROID BULLSEYE UPPER BUTTON PASS: "+checks+" image checks; transformed real home, synthetic board and blank; no phone/gesture claims");return;}
        try(LiveLayout v=new LiveLayout(c,home,board)){
            if(!zoomOnly)for(int[] size:new int[][]{{1080,2400,0,0},{1080,2520,24,72},{720,1680,-12,48},{1344,2772,0,30},{1152,2376,0,0}}){
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
                            targetSimilarity(frame,board,r,c.targets.get(i),i);
                        }
                        checks++;System.out.println("BULLSEYE LAYOUT PASS: "+w+"x"+h+" home="+isHome);System.out.flush();
                    }finally{frame.recycle();}
                }
            }
            for(int[] zoom:new int[][]{{85,70,200},{112,-60,-100}}){
                float factor=zoom[0]/100f;
                for(boolean isHome:new boolean[]{true,false}){
                    Bitmap source=isHome?home:board;
                    Bitmap frame=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(frame);canvas.drawColor(Color.BLACK);
                    canvas.drawBitmap(source,null,new android.graphics.Rect(zoom[1],zoom[2],zoom[1]+Math.round(source.getWidth()*factor),zoom[2]+Math.round(source.getHeight()*factor)),new Paint(Paint.FILTER_BITMAP_FLAG));
                    try{
                        LiveLayout.Result r=v.observe(frame);require(r!=null&&r.home==isHome,"Content zoom missing "+factor+" home="+isHome);
                        require(Math.abs(r.scale-factor)<.04,"Content scale estimate incorrect");
                        if(isHome){
                            require(Math.abs(r.config.start.x*frame.getWidth()-(c.start.x*home.getWidth()*factor+zoom[1]))<8,"Zoomed start X");
                            require(Math.abs(r.config.start.y*frame.getHeight()-(c.start.y*home.getHeight()*factor+zoom[2]))<8,"Zoomed start Y");
                        }else for(int i=0;i<12;i++){
                            PointF original=c.targets.get(i),actual=r.config.targets.get(i);
                            float expectedX=original.x*board.getWidth()*factor+zoom[1],expectedY=original.y*board.getHeight()*factor+zoom[2];
                            require(Math.abs(actual.x*frame.getWidth()-expectedX)<8,"Zoomed target X "+i+" actual="+actual.x*frame.getWidth()+" expected="+expectedX);
                            require(Math.abs(actual.y*frame.getHeight()-expectedY)<8,"Zoomed target Y "+i+" actual="+actual.y*frame.getHeight()+" expected="+expectedY);
                            targetSimilarity(frame,board,r,original,i);
                        }
                        checks++;System.out.println("BULLSEYE CONTENT ZOOM PASS: "+factor+" home="+isHome);System.out.flush();
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
