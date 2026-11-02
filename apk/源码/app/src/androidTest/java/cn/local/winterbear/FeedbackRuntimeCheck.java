package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.File;
import java.io.FileInputStream;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/** Actual Android bitmap/OpenCV regression without requiring a running package manager. */
public final class FeedbackRuntimeCheck {
    static BearFlow.Frame read(FeedbackVision vision,File file,int erase){
        Bitmap original=BitmapFactory.decodeFile(file.getAbsolutePath());
        if(original==null)throw new AssertionError("Missing fixture: "+file.getName());
        Bitmap bitmap=original.copy(Bitmap.Config.ARGB_8888,true);original.recycle();
        Mat rgba=new Mat(),gray=new Mat();
        try{
            Canvas canvas=new Canvas(bitmap);Paint paint=new Paint();paint.setColor(Color.BLACK);
            if(erase==1)canvas.drawRect(438,1731,853,2182,paint); // compact overlay + padded shadow mask
            if(erase==2)canvas.drawRect(160,1100,950,1430,paint); // only OK remains
            if(erase==3)canvas.drawRect(265,1410,820,1730,paint); // only specific error remains
            Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            BearFlow.Frame frame=new BearFlow.Frame();vision.observe(gray,frame,bitmap.getWidth(),bitmap.getHeight());return frame;
        }finally{gray.release();rgba.release();bitmap.recycle();}
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assets=new File(args[2]);int checks=0;
        try(FeedbackVision vision=new FeedbackVision(path->new FileInputStream(new File(assets,path)))){
            File reported=new File(fixtures,"reported-overflow.png");
            for(int mask=0;mask<4;mask++){
                BearFlow.Frame frame=read(vision,reported,mask);
                if(frame.troopOverflow!=(mask<2))throw new AssertionError("Reported popup / mask="+mask);
                if(mask<2&&(frame.dialogOkX<340||frame.dialogOkX>740||frame.dialogOkY<1480||frame.dialogOkY>1620))throw new AssertionError("Wrong confirmation point");
                checks++;System.out.println("IMAGE PASS: "+checks);System.out.flush();
            }
            for(int second:new int[]{170,250,270,315,358,411,691,718,751,771,780,816,820}){
                boolean expected=second==358;
                if(read(vision,new File(fixtures,"video-"+second+".png"),0).troopOverflow!=expected)throw new AssertionError("Old recording / "+second);
                checks++;System.out.println("IMAGE PASS: "+checks);System.out.flush();
            }
            MarchFlow flow=MarchFlowTest.flow();MarchFlowTest.atVerify(flow);
            BearFlow.Decision dismiss=flow.next(read(vision,reported,1),6700);
            if(dismiss.kind!=BearFlow.Kind.NAVIGATE)throw new AssertionError("Popup did not dismiss");
            flow.complete(true,6800);
            if(flow.next(MarchFlowTest.deploy(),8100).kind!=BearFlow.Kind.BACK)throw new AssertionError("Did not return to list");
            flow.complete(true,8200);
            if(flow.next(MarchFlowTest.list(),9500).kind!=BearFlow.Kind.JOIN)throw new AssertionError("Did not rejoin via green +");
            System.out.println("ANDROID OPENCV PASS: "+checks+" image cases; actual popup -> dismiss -> BACK -> fresh JOIN; no game gestures");
        }
    }
}
