package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import java.io.File;
import java.io.FileInputStream;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import cn.local.bullseye.FloatingGuard;
import cn.local.bullseye.FloatingGuard.Box;

/** New timer/error cases use actual digit recognition; no annotated substitute for countdowns. */
public final class CountdownRuntimeCheck {
    static int checks;
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void passed(String name){checks++;System.out.println("COUNTDOWN IMAGE PASS: "+name);System.out.flush();}
    static Bitmap load(File fixtures,String name){Bitmap b=BitmapFactory.decodeFile(new File(fixtures,name).getAbsolutePath());require(b!=null,"Missing "+name);return b;}
    static int[] read(CountdownVision vision,Bitmap bitmap,int... joins){
        Mat rgba=new Mat(),full=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,full,Imgproc.COLOR_RGBA2GRAY);
            double scale=Math.min(1,1080.0/bitmap.getWidth());Imgproc.resize(full,gray,new Size(),scale,scale,Imgproc.INTER_AREA);
            int[] result=new int[joins.length];for(int i=0;i<joins.length;i++)result[i]=vision.read(gray,bitmap.getWidth(),joins[i],scale);return result;
        }finally{rgba.release();full.release();gray.release();}
    }
    static BearFlow.Frame popup(FeedbackVision vision,Bitmap bitmap){
        Mat rgba=new Mat(),gray=new Mat();
        try{Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);BearFlow.Frame f=new BearFlow.Frame();vision.observe(gray,f,bitmap.getWidth(),bitmap.getHeight());return f;}
        finally{rgba.release();gray.release();}
    }
    static Bitmap scaled(Bitmap source,int w,int h,boolean center){
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);float s=w/(float)source.getWidth();
        if(center)c.translate(0,(h-source.getHeight()*s)/2);c.scale(s,s);c.drawBitmap(source,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));return result;
    }
    static BearFlow.Frame rows(int[] seconds,int[] joins){
        BearFlow.Frame f=MarchFlowTest.list();f.rows.clear();for(int i=0;i<seconds.length;i++)f.rows.add(new BearFlow.Row(i,960,joins[i],true,seconds[i]));return f;
    }
    static void syntheticTimer(Bitmap bitmap,Bitmap atlas,String value){
        Canvas c=new Canvas(bitmap);Paint p=new Paint();p.setColor(Color.rgb(160,81,74));c.drawRect(853,542,1048,612,p);
        int x=870,y=560;
        for(int i=0;i<value.length();i++){
            char digit=value.charAt(i);
            if(digit==':'){p.setColor(Color.WHITE);c.drawRect(x+1,y+7,x+5,y+11,p);c.drawRect(x+1,y+22,x+5,y+26,p);x+=8;continue;}
            int d=digit-'0';
            // Transparent binary glyph, preserving the real learned digit shape.
            for(int gy=0;gy<28;gy++)for(int gx=0;gx<21;gx++)if((atlas.getPixel(d*24+gx*24/21,gy*32/28)&255)>128){p.setColor(Color.WHITE);c.drawRect(x+gx,y+gy,x+gx+1,y+gy+1,p);}
            x+=24;
        }
    }
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.out.flush();System.exit(1);}}
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assets=new File(args[2]);
        FeedbackVision.Assets provider=path->new FileInputStream(new File(assets,path));
        CountdownVision timer=new CountdownVision(provider);
        Bitmap list=load(fixtures,"countdown-list.png"),warning=load(fixtures,"too-late.png");
        int[] joins={836,1450,2061};
        try(FeedbackVision vision=new FeedbackVision(provider)){
            for(int[] size:new int[][]{{1080,2400},{1152,2376},{1344,2772}}){
                Bitmap phone=scaled(list,size[0],size[1],false);int[] points=new int[3];for(int i=0;i<3;i++)points[i]=Math.round(joins[i]*size[0]/1080f);
                int[] result=read(timer,phone,points);phone.recycle();
                require(java.util.Arrays.equals(result,new int[]{33,57,61}),"Timer at "+size[0]+": "+java.util.Arrays.toString(result));passed("33 / 57 / 61 seconds at "+size[0]+"x"+size[1]);
                Bitmap error=scaled(warning,size[0],size[1],true);
                BearFlow.Frame detected=popup(vision,error);require(detected.rallyTooLate&&detected.cancelX<size[0]*.5,"Must locate left Cancel, not Confirm");
                float d=BearOverlayGuard.panelScale(size[0],size[1],4f);int gap=Math.round(8*d);
                Box box=FloatingGuard.place(new Box(0,0,(int)(size[0]*.34),BearOverlayGuard.panelHeight(d)),new Box(0,90,size[0],size[1]-90),BearOverlayGuard.areas(size[0],size[1]),gap);
                require(box!=null&&BearOverlayGuard.inputClear(box,detected.cancelX,detected.cancelY,detected.cancelX,detected.cancelY,gap),"Panel blocks Cancel");
                Paint paint=new Paint();paint.setColor(Color.BLACK);new Canvas(error).drawRect(box.left-gap,box.top-gap,box.right+gap,box.bottom+gap,paint);
                require(popup(vision,error).rallyTooLate,"Panel mask erased warning");error.recycle();passed("late warning and stable panel at "+size[0]);
            }
            int[][] expected={{38,92,107},{130,151,157},{83,106,131},{97,122,135},{61,103,119}};
            int[] seconds={315,411,771,780,816};
            for(int i=0;i<seconds.length;i++){
                Bitmap b=load(fixtures,"video-"+seconds[i]+".png");int[] actual=read(timer,b,joins);b.recycle();
                require(java.util.Arrays.equals(actual,expected[i]),"Recorded timers "+seconds[i]+": "+java.util.Arrays.toString(actual));passed("recorded timers "+seconds[i]);
            }
            Bitmap atlas=BitmapFactory.decodeFile(new File(assets,"feedback/timer_digits.png").getAbsolutePath());
            for(int secondsLeft:new int[]{0,1,8,9,10,11}){
                Bitmap b=list.copy(Bitmap.Config.ARGB_8888,true);syntheticTimer(b,atlas,String.format(java.util.Locale.ROOT,"00:00:%02d",secondsLeft));
                int[] actual=read(timer,b,joins);b.recycle();require(actual[0]==secondsLeft,"Synthetic boundary "+secondsLeft+": "+actual[0]);
                MarchFlow flow=MarchFlowTest.flow();MarchFlowTest.enter(flow,5);
                BearFlow.Decision join=flow.next(rows(actual,joins),3000);require(join.kind==BearFlow.Kind.JOIN&&join.y==(secondsLeft<10?1450:836),"Boundary selected wrong rally");passed("synthetic "+secondsLeft+"s skip/join");
            }
            atlas.recycle();
            for(int part=0;part<2;part++){
                Bitmap b=warning.copy(Bitmap.Config.ARGB_8888,true);Paint black=new Paint();black.setColor(Color.BLACK);
                new Canvas(b).drawRect(part==0?new Rect(130,1180,950,1320):new Rect(100,1470,520,1630),black);
                require(!popup(vision,b).rallyTooLate,"Incomplete warning must not be clicked");b.recycle();passed("missing warning body / Cancel "+part);
            }
            Bitmap hidden=list.copy(Bitmap.Config.ARGB_8888,true);Paint black=new Paint();black.setColor(Color.BLACK);new Canvas(hidden).drawRect(853,542,1048,612,black);
            int[] hiddenTime=read(timer,hidden,joins);hidden.recycle();require(hiddenTime[0]==-1,"Hidden time treated as known");
            MarchFlow unknown=MarchFlowTest.flow();MarchFlowTest.enter(unknown,5);require(unknown.next(rows(hiddenTime,joins),3000).y==1450,"Hidden time was joined");passed("unknown timer skips to next row");
            MarchFlow f=MarchFlowTest.flow();MarchFlowTest.enter(f,5);BearFlow.Frame realRows=rows(read(timer,list,joins),joins);
            f.next(realRows,3000);f.complete(true,3100);f.next(MarchFlowTest.deploy(),4400);f.complete(true,4500);f.next(MarchFlowTest.deploy(),5300);f.complete(true,5400);
            BearFlow.Frame late=popup(vision,warning);BearFlow.Decision cancel=f.next(late,6700);require(cancel.kind==BearFlow.Kind.NAVIGATE&&cancel.x<500,"Real warning did not cancel");f.complete(true,6800);
            require(f.next(MarchFlowTest.deploy(),8100).kind==BearFlow.Kind.BACK,"Real warning did not return");f.complete(true,8200);
            require(f.next(realRows,9500).y==1450&&f.nextPreset==0&&f.submitted==0,"Real warning repeated old row / lost preset");passed("actual warning -> Cancel -> BACK -> different green +");
        }finally{list.recycle();warning.recycle();}
        System.out.println("ANDROID COUNTDOWN PASS: "+checks+" cases; actual digit recognition; Xiaomi plus 2 synthetic Huawei resolutions; no gestures");
    }
}
