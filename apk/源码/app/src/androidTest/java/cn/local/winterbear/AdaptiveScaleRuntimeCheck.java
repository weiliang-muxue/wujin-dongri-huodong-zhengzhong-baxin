package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import cn.local.automation.VisualLocator;
import java.io.File;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Model-independent scale, ambiguity and button-color checks on actual recognition assets. */
public final class AdaptiveScaleRuntimeCheck {
    private static int checks;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Mat load(File assets,String name,boolean color){
        Bitmap bitmap=BitmapFactory.decodeFile(new File(assets,name+".png").getPath());require(bitmap!=null,"Missing "+name);
        Mat rgba=new Mat(),result=new Mat();
        try{Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,result,color?Imgproc.COLOR_RGBA2RGB:Imgproc.COLOR_RGBA2GRAY);return result;}
        finally{rgba.release();bitmap.recycle();}
    }
    private static Rect stamp(Mat canvas,Mat template,double scale,int x,int y){
        Mat resized=new Mat();
        try{
            Imgproc.resize(template,resized,new Size(),scale,scale,scale<1?Imgproc.INTER_AREA:Imgproc.INTER_CUBIC);
            Rect box=new Rect(x,y,resized.cols(),resized.rows());Mat target=canvas.submat(box);
            try{resized.copyTo(target);}finally{target.release();}
            return box;
        }finally{resized.release();}
    }
    private static void centered(VisualLocator.Match match,Rect expected,String message){
        require(match!=null,message+" missing");
        require(Math.abs(match.x-(expected.x+expected.width/2.0))<=3
                &&Math.abs(match.y-(expected.y+expected.height/2.0))<=3,message+" wrong coordinate "+match.x+","+match.y);
        require(match.confidence>=.88,message+" confidence reduced");
    }
    private static void scales(File assets){
        for(String key:new String[]{"hunt/search","feedback/green_plus","hunt/rally_start","reference/back","reference/bear_label"}){
            Mat template=load(assets,key,false);
            try{
                boolean strip=key.equals("reference/bear_label");
                double[] scales=strip?new double[]{.67,.81,.93,.989,1.015,1.07,1.23,1.46}:new double[]{.67,.81,.93,1.07,1.23,1.46};
                for(double scale:scales){
                    Mat frame=new Mat(320,580,CvType.CV_8UC1,new Scalar(35));
                    try{
                        Rect expected=stamp(frame,template,scale,strip?80:170,110);
                        java.util.List<VisualLocator.Match> found=VisualLocator.findAdaptive(frame,template,new Rect(0,0,strip?486:frame.cols(),frame.rows()),.88,2,true);
                        if(found.size()!=1)for(VisualLocator.Match candidate:found)
                            System.out.println("ADAPTIVE CANDIDATE: "+candidate.x+","+candidate.y+" "+candidate.width+"x"+candidate.height+" confidence="+candidate.confidence);
                        centered(found.size()==1?found.get(0):null,expected,key+" scale="+scale+" candidates="+found.size());
                        checks++;System.out.println("ADAPTIVE SCALE PASS: "+key+" scale="+scale);System.out.flush();
                    }finally{frame.release();}
                }
            }finally{template.release();}
        }
    }
    private static void negatives(File assets){
        Mat template=load(assets,"hunt/search",false);
        try{
            for(double first:new double[]{1.23,1.0}){
                Mat frame=new Mat(320,580,CvType.CV_8UC1,new Scalar(35));
                try{
                    stamp(frame,template,first,70,90);stamp(frame,template,1.23,370,170);
                    require(VisualLocator.uniqueAdaptive(frame,template,new Rect(0,0,580,320),.88,true)==null,"Different target locations merged");checks++;
                }finally{frame.release();}
            }
            Mat frame=new Mat(320,580,CvType.CV_8UC1,new Scalar(35));
            Mat flat=new Mat(32,48,CvType.CV_8UC1,new Scalar(100));
            try{
                require(VisualLocator.uniqueAdaptive(frame,flat,new Rect(0,0,580,320),.88,true)==null,"Flat template accepted");checks++;
                stamp(frame,template,1.23,370,170);
                require(VisualLocator.uniqueAdaptive(frame,template,new Rect(0,0,200,150),.88,true)==null,"Adaptive matching escaped allowed region");checks++;
            }finally{frame.release();flat.release();}
        }finally{template.release();}
    }
    private static void colors(File assets){
        Mat expected=load(assets,"feedback/green_plus",true),shape=new Mat(),grayButton=new Mat();
        try{
            Imgproc.cvtColor(expected,shape,Imgproc.COLOR_RGB2GRAY);Imgproc.cvtColor(shape,grayButton,Imgproc.COLOR_GRAY2RGB);
            for(double scale:new double[]{.83,1.29}){
                Mat color=new Mat(320,580,CvType.CV_8UC3,new Scalar(35,35,35)),gray=new Mat();
                try{
                    stamp(color,grayButton,1,70,90);Rect target=stamp(color,expected,scale,370,170);
                    Imgproc.cvtColor(color,gray,Imgproc.COLOR_RGB2GRAY);
                    java.util.List<VisualLocator.Match> found=VisualLocator.findAdaptive(gray,shape,new Rect(0,0,580,320),.88,2,true,
                            match->VisualLocator.sameColor(color,expected,match,35));
                    require(found.size()==1,"Gray button retained or green button lost");centered(found.get(0),target,"green plus "+scale);checks++;
                }finally{color.release();gray.release();}
            }
            for(boolean orange:new boolean[]{false,true}){
                Mat color=new Mat(320,580,CvType.CV_8UC3,new Scalar(35,35,35)),gray=new Mat(),button=grayButton.clone();
                try{
                    if(orange){
                        java.util.List<Mat> channels=new java.util.ArrayList<>();Core.split(expected,channels);
                        try{Core.merge(java.util.Arrays.asList(channels.get(1),channels.get(0),channels.get(2)),button);}
                        finally{for(Mat channel:channels)channel.release();}
                    }
                    stamp(color,button,1.29,170,110);Imgproc.cvtColor(color,gray,Imgproc.COLOR_RGB2GRAY);
                    require(VisualLocator.findAdaptive(gray,shape,new Rect(0,0,580,320),.88,2,true,
                            match->VisualLocator.sameColor(color,expected,match,35)).isEmpty(),"Inactive/changed-color button accepted");checks++;
                }finally{color.release();gray.release();button.release();}
            }
        }finally{expected.release();shape.release();grayButton.release();}
    }
    private static void smallMarkers(File assets){
        for(String key:new String[]{"hunt/level_label","feedback/preset0"}){
            Mat template=load(assets,key,false);
            try{
                int[] widths=key.endsWith("preset0")?new int[]{8,9,10,11}:new int[]{23};
                for(int width:widths)for(int interpolation:new int[]{Imgproc.INTER_AREA,Imgproc.INTER_LINEAR}){
                    Mat frame=new Mat(160,240,CvType.CV_8UC1,new Scalar(35)),sample=new Mat();
                    try{
                        int height=(int)Math.round(width*template.rows()/(double)template.cols());
                        Imgproc.resize(template,sample,new Size(width,height),0,0,interpolation);
                        Rect box=new Rect(73,61,width,height);Mat target=frame.submat(box);
                        try{sample.copyTo(target);}finally{target.release();}
                        centered(VisualLocator.uniqueAdaptive(frame,template,new Rect(0,0,240,160),.88,true),box,key+" width="+width);
                        checks++;System.out.println("ADAPTIVE SMALL PASS: "+key+" width="+width+" interpolation="+interpolation);
                    }finally{frame.release();sample.release();}
                }
            }finally{template.release();}
        }
    }
    public static void main(String[] args){
        try{
            System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
            File assets=new File(args[1]);scales(assets);negatives(assets);colors(assets);smallMarkers(assets);
            System.out.println("ANDROID ADAPTIVE SCALE PASS: "+checks+" synthetic checks using real assets; continuous local scale refinement; no device-specific settings or gestures");
        }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
