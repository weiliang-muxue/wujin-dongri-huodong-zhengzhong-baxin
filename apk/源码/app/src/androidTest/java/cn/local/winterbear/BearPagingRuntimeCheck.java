package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.io.File;
import cn.local.bullseye.FloatingGuard.Box;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/** The reported recording drives real recognition, paging and the next join. */
public final class BearPagingRuntimeCheck {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static BearFlow.Frame readComponents(Config config,Bitmap image){
        Matcher.Numbers unused=new Matcher.Numbers(){public int read(Bitmap b,Rect r){throw new AssertionError("Unexpected numeric OCR");}public void close(){}};
        Mat rgba=new Mat(),small=new Mat(),gray=new Mat();
        try(Matcher matcher=new Matcher(config,unused)){
            matcher.frame(image);int width=image.getWidth(),height=image.getHeight();
            double scale=Math.min(1,1080./width);
            Utils.bitmapToMat(image,rgba);Imgproc.resize(rgba,small,new Size(),scale,scale,Imgproc.INTER_AREA);
            Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
            CountdownVision timer=new CountdownVision(config::openAsset);
            BearFlow.Frame frame=new BearFlow.Frame();
            Matcher.Hit header=matcher.locate("header",new Rect(0,0,width*3/4,height/2));
            frame.header=header!=null;
            frame.rallyList=frame.header&&matcher.locate("rally_tab",new Rect(0,header.y,width/2,header.y+width/3))!=null;
            // The original frame uses the full detector. Other cases isolate
            // page labels, green buttons and timers using known bear-label bands.
            for(int i=0;i<2;i++){
                int y=Math.round((i==0?565:918)*width/620f);
                Matcher.Hit join=matcher.joinBeside(new Rect(width*45/620,y-12*width/620,width*190/620,y+12*width/620));
                frame.rows.add(join==null?new BearFlow.Row(i,0,0,false,-1):
                        new BearFlow.Row(i,join.x,join.y,true,timer.read(gray,width,join.x,join.y,scale)));
            }
            return frame;
        }finally{rgba.release();small.release();gray.release();image.recycle();}
    }
    public static void main(String[] args){
        try{
            System.load(args[0]+"/libc++_shared.so");
            System.load(args[0]+"/libopencv_java4.so");
            File fixtures=new File(args[1]),assets=new File(args[2]),work=new File(args[3]);
            int checks=0;
            for(int width:new int[]{620,1080,1152,1344}){
                int height=Math.round(width*1280f/620);
                Config config=CompatibilityRuntimeCheck.config(assets,new File(work,"width-"+width),width,height);
                for(int second:new int[]{6,12,18}){
                    Bitmap source=BitmapFactory.decodeFile(new File(fixtures,"paging-"+second+".png").getPath());
                    require(source!=null,"Missing recording frame "+second);
                    Bitmap frame=Bitmap.createScaledBitmap(source,width,height,true);
                    if(frame!=source)source.recycle();
                    BearFlow.Frame observed=width==620&&second==6?CompatibilityRuntimeCheck.read(config,frame,-1,false):readComponents(config,frame);
                    require(observed.rallyList,"Recording list missing at "+second+" / "+width);
                    observed.ageMillis=1;
                    for(BearFlow.Row row:observed.rows)System.out.println("PAGING ROW: "+width+" / "+second+" x="+row.x+" y="+row.y+" available="+row.available+" seconds="+row.secondsLeft);
                    if(second<18){
                        int shortRows=0;
                        for(BearFlow.Row row:observed.rows){
                            if(row.available&&row.secondsLeft>=0&&row.secondsLeft<=10)shortRows++;
                        }
                        require(shortRows>=2,"Short countdowns not read at "+second+" / "+width);
                    }
                    MarchFlow flow=MarchFlowTest.flow();MarchFlowTest.enter(flow,3);
                    BearFlow.Decision action=flow.next(observed,3000);
                    if(second==18&&action.kind==BearFlow.Kind.WAIT)action=flow.next(observed,9000);
                    require(action.kind==BearFlow.Kind.SCROLL,"Expected next page, got "+action.kind+" / "+action.message);
                    Box recordedPanel=new Box(width*45/100,height*58/100,width*80/100,height*74/100);
                    float[] swipe=BearOverlayGuard.nextPagePath(width,height,recordedPanel,8);
                    require(swipe!=null&&swipe[1]>swipe[3],"Paging must move toward later rows");
                    require(BearOverlayGuard.inputClear(recordedPanel,swipe[0],swipe[1],swipe[2],swipe[3],8),"Swipe crosses the controls");
                    flow.complete(true,9100);
                    require(flow.next(MarchFlowTest.list(),10200).kind==BearFlow.Kind.JOIN,"Next available rally was not joined");
                    require(flow.phase==MarchFlow.Phase.FIND,"List position was reset");
                    checks++;
                    System.out.println("PAGING IMAGE PASS: "+second+"s / "+width+"x"+height+" -> scroll -> next join");
                }
            }
            Config legacy=CompatibilityRuntimeCheck.config(assets,new File(work,"legacy"),1080,2400);
            for(String name:new String[]{"countdown-list.png","video-411.png","video-816.png"}){
                Bitmap image=BitmapFactory.decodeFile(new File(fixtures,name).getPath());
                Matcher.Numbers unused=new Matcher.Numbers(){public int read(Bitmap b,Rect r){return -1;}public void close(){}};
                try(Matcher matcher=new Matcher(legacy,unused)){
                    matcher.frame(image);int[] joins={836,1450,2061};
                    for(int i=0;i<3;i++){
                        int y=joins[i]+170;
                        Matcher.Hit join=matcher.joinBeside(new Rect(70,y-20,335,y+20));
                        boolean expected=!(name.equals("video-411.png")&&i==0)&&!(name.equals("video-816.png")&&i==2);
                        require((join!=null)==expected,"Green/gray distinction changed "+name+" row "+i);
                    }
                }finally{image.recycle();}
                checks++;System.out.println("PAGING COMPAT PASS: "+name+" green/gray buttons");
            }
            System.out.println("ANDROID PAGING PASS: "+checks+" image checks (1 full detector, 14 component checks); real countdowns, annotated bear-label bands for component checks; no gestures");
        }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
