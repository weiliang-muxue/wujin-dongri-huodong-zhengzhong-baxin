package cn.local.automation;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Shared pixel matcher. Activities supply landmarks/relationships, never guessed tap points. */
public final class VisualLocator {
    private VisualLocator(){}
    public static final class Match {
        public final double x,y,confidence;
        public final int width,height;
        Match(double x,double y,double confidence,int width,int height){this.x=x;this.y=y;this.confidence=confidence;this.width=width;this.height=height;}
    }
    public static List<Match> find(Mat image,Mat template,Rect area,double threshold,int limit,boolean soften){
        List<Match> found=new ArrayList<>();
        int l=Math.max(0,area.x),t=Math.max(0,area.y),r=Math.min(image.cols(),area.x+area.width),b=Math.min(image.rows(),area.y+area.height);
        if(image.empty()||template.empty()||r-l<template.cols()||b-t<template.rows()||limit<1)return found;
        // A flat patch has undefined normalized correlation and must not match every pixel.
        MatOfDouble mean=new MatOfDouble(),deviation=new MatOfDouble();
        try{Core.meanStdDev(template,mean,deviation);if(deviation.toArray()[0]<1)return found;}
        finally{mean.release();deviation.release();}
        Mat roi=image.submat(t,b,l,r),scores=new Mat();
        try{
            Imgproc.matchTemplate(roi,template,scores,Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult first=Core.minMaxLoc(scores);
            if(soften&&Double.isFinite(first.maxVal)&&first.maxVal>=.78&&first.maxVal<threshold){
                Mat a=new Mat(),c=new Mat();
                try{Imgproc.GaussianBlur(roi,a,new Size(3,3),1);Imgproc.GaussianBlur(template,c,new Size(3,3),1);Imgproc.matchTemplate(a,c,scores,Imgproc.TM_CCOEFF_NORMED);threshold=Math.max(.91,threshold);}
                finally{a.release();c.release();}
            }
            for(int n=0;n<limit;n++){
                Core.MinMaxLocResult m=Core.minMaxLoc(scores);
                if(!Double.isFinite(m.maxVal)||m.maxVal<threshold)break;
                int x=(int)m.maxLoc.x,y=(int)m.maxLoc.y;
                found.add(new Match(l+x+template.cols()/2.,t+y+template.rows()/2.,m.maxVal,template.cols(),template.rows()));
                Mat peak=scores.submat(Math.max(0,y-template.rows()/2),Math.min(scores.rows(),y+template.rows()/2+1),Math.max(0,x-template.cols()/2),Math.min(scores.cols(),x+template.cols()/2+1));
                try{peak.setTo(new Scalar(-1));}finally{peak.release();}
            }
        }finally{roi.release();scores.release();}
        return found;
    }
    public static Match unique(Mat image,Mat template,Rect area,double threshold,boolean soften){
        List<Match> found=find(image,template,area,threshold,2,soften);return found.size()==1?found.get(0):null;
    }
}
