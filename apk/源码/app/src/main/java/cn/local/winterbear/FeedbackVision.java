package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Fixed map, bear and error cues from the recording. No player text or arbitrary confirmation. */
final class FeedbackVision implements AutoCloseable {
    private final Mat body,title,button,city,search,bear,plus,plusColor,bearTop,plusTop,plusTopColor;
    private final Mat deploy,deployColor;
    private final Mat[] preset=new Mat[6];
    FeedbackVision(Config config){body=load(config,"troop_overflow");title=load(config,"confirm_title");button=load(config,"confirm_ok");city=load(config,"map_city");search=load(config,"map_search");bear=load(config,"bear_label");plus=load(config,"green_plus");plusColor=load(config,"green_plus",true);bearTop=load(config,"bear_label_top");plusTop=load(config,"green_plus_top");plusTopColor=load(config,"green_plus_top",true);deploy=load(config,"deploy");deployColor=load(config,"deploy",true);for(int i=0;i<6;i++)preset[i]=load(config,"preset"+i);}
    private Mat load(Config config,String key){
        return load(config,key,false);
    }
    private Mat load(Config config,String key,boolean color){
        try(InputStream in=config.context.getAssets().open("feedback/"+key+".png")){
            Bitmap image=BitmapFactory.decodeStream(in);Mat rgba=new Mat(),result=new Mat();
            try{Utils.bitmapToMat(image,rgba);Imgproc.cvtColor(rgba,result,color?Imgproc.COLOR_RGBA2RGB:Imgproc.COLOR_RGBA2GRAY);return result;}
            finally{image.recycle();rgba.release();}
        }catch(Exception e){return new Mat();}
    }
    private Point hit(Mat screen,Mat template,Rect region){
        if(template.empty()||region.width<template.cols()||region.height<template.rows())return null;
        Mat roi=screen.submat(region),scores=new Mat();
        try{Imgproc.matchTemplate(roi,template,scores,Imgproc.TM_CCOEFF_NORMED);Core.MinMaxLocResult mm=Core.minMaxLoc(scores);
            return Double.isFinite(mm.maxVal)&&mm.maxVal>=.88?new Point(region.x+mm.maxLoc.x+template.cols()/2.0,region.y+mm.maxLoc.y+template.rows()/2.0):null;
        }finally{roi.release();scores.release();}
    }
    void observe(Mat fullGray,BearFlow.Frame frame,int width,int height){
        // The recording was compressed to 324x720. Matching at that resolution avoids upscaled artifacts.
        if(Math.abs(width/(double)height-324.0/720)>.015)return;
        Mat small=new Mat();
        try{
            Imgproc.resize(fullGray,small,new Size(324,720),0,0,Imgproc.INTER_AREA);
            frame.worldMapChrome=hit(small,city,new Rect(275,695,47,25))!=null&&hit(small,search,new Rect(92,600,38,42))!=null;
            if(hit(small,body,new Rect(55,355,220,65))==null||hit(small,title,new Rect(125,285,75,45))==null)return;
            Point ok=hit(small,button,new Rect(85,425,155,75));
            if(ok!=null){frame.troopOverflow=true;frame.dialogOkX=(int)Math.round(ok.x*width/324);frame.dialogOkY=(int)Math.round(ok.y*height/720);}
        }finally{small.release();}
    }
    private List<Point> hits(Mat screen,Mat template,Rect region){
        List<Point> result=new ArrayList<>();
        if(template.empty()||region.width<template.cols()||region.height<template.rows())return result;
        Mat roi=screen.submat(region),scores=new Mat();
        try{
            Imgproc.matchTemplate(roi,template,scores,Imgproc.TM_CCOEFF_NORMED);
            for(int n=0;n<8;n++){
                Core.MinMaxLocResult mm=Core.minMaxLoc(scores);if(!Double.isFinite(mm.maxVal)||mm.maxVal<.88)break;
                int x=(int)mm.maxLoc.x,y=(int)mm.maxLoc.y;
                result.add(new Point(region.x+x+template.cols()/2.0,region.y+y+template.rows()/2.0));
                Mat suppress=scores.submat(Math.max(0,y-template.rows()/2),Math.min(scores.rows(),y+template.rows()/2+1),Math.max(0,x-template.cols()/2),Math.min(scores.cols(),x+template.cols()/2+1));
                suppress.setTo(new Scalar(-1));suppress.release();
            }
        }finally{roi.release();scores.release();}
        return result;
    }
    List<RallyOrder.Detection> rallies(Mat fullGray,Mat fullColor,int width,int height){
        List<RallyOrder.Detection> result=new ArrayList<>();
        if(Math.abs(width/(double)height-324.0/720)>.015||plusColor.empty())return result;
        Mat gray=new Mat(),color=new Mat();
        try{
            Imgproc.resize(fullGray,gray,new Size(324,720),0,0,Imgproc.INTER_AREA);
            Imgproc.resize(fullColor,color,new Size(324,720),0,0,Imgproc.INTER_AREA);
            List<Point> targets=hits(gray,bear,new Rect(0,144,120,511));
            targets.addAll(hits(gray,bearTop,new Rect(0,144,120,511)));
            for(Point target:targets){
                int top=Math.max(0,(int)target.y-94),bottom=Math.min(720,(int)target.y+94);
                Rect band=new Rect(227,top,97,bottom-top);
                List<Point> green=greenHits(gray,color,plus,plusColor,band);
                for(Point p:greenHits(gray,color,plusTop,plusTopColor,band))if(green.stream().noneMatch(q->Math.abs(p.x-q.x)<=6&&Math.abs(p.y-q.y)<=6))green.add(p);
                if(green.size()!=1)continue;
                Point join=green.get(0);
                result.add(new RallyOrder.Detection((int)Math.round(target.x*width/324),(int)Math.round(target.y*height/720),(int)Math.round(join.x*width/324),(int)Math.round(join.y*height/720),true,.88));
            }
        }finally{gray.release();color.release();}
        return result;
    }
    private List<Point> greenHits(Mat gray,Mat color,Mat shape,Mat expected,Rect region){
        List<Point> result=new ArrayList<>();if(expected.empty())return result;
        for(Point p:hits(gray,shape,region)){
            int x=(int)Math.round(p.x-expected.cols()/2.0),y=(int)Math.round(p.y-expected.rows()/2.0);
            Mat actual=color.submat(new Rect(x,y,expected.cols(),expected.rows())),delta=new Mat();
            try{Core.absdiff(actual,expected,delta);Scalar d=Core.mean(delta);if((d.val[0]+d.val[1]+d.val[2])/3.0<=35)result.add(p);}
            finally{actual.release();delta.release();}
        }
        return result;
    }
    void formation(Mat fullGray,Mat fullColor,BearFlow.Frame frame,int width,int height,Config config){
        if(!frame.formation||frame.header||Math.abs(width/(double)height-.45)>.015)return;
        Mat gray=new Mat(),color=new Mat();
        try{
            Imgproc.resize(fullGray,gray,new Size(324,720),0,0,Imgproc.INTER_AREA);
            Imgproc.resize(fullColor,color,new Size(324,720),0,0,Imgproc.INTER_AREA);
            List<Point> buttons=greenHits(gray,color,deploy,deployColor,new Rect(228,678,39,24));
            if(!frame.deploy&&buttons.size()==1){Point p=buttons.get(0);frame.deploy=true;frame.deployX=(int)Math.round(p.x*width/324);frame.deployY=(int)Math.round(p.y*height/720);}
            int[] x={29,63,96,129,163,196};
            for(int i=0;i<6;i++)if(!frame.savedPreset[i]&&Math.abs(config.p.getInt("presetX"+i,0)*324.0/width-x[i])<6&&Math.abs(config.p.getInt("presetY"+i,0)*720.0/height-128)<6&&hit(gray,preset[i],new Rect(x[i]-10,117,20,21))!=null)frame.savedPreset[i]=true;
        }finally{gray.release();color.release();}
    }
    public void close(){body.release();title.release();button.release();city.release();search.release();bear.release();plus.release();plusColor.release();bearTop.release();plusTop.release();plusTopColor.release();deploy.release();deployColor.release();for(Mat p:preset)p.release();}
}
