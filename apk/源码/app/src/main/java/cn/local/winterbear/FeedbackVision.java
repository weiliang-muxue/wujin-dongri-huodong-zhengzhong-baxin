package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Fixed map, bear and error cues from the recording. No player text or arbitrary confirmation. */
final class FeedbackVision implements AutoCloseable {
    private final Mat body,title,button,city,search,bear,plus,plusColor,bearTop,plusTop,plusTopColor;
    private final Mat deploy,deployColor;
    private final Mat bodyV2,buttonV2;
    private final Mat formationLabel;
    private final Mat lateBody,cancel;
    private final Mat[] preset=new Mat[6];
    interface Assets { InputStream open(String path) throws IOException; }
    FeedbackVision(Config config){this(config::openAsset);}
    FeedbackVision(Assets assets){body=load(assets,"troop_overflow");title=load(assets,"confirm_title");button=load(assets,"confirm_ok");bodyV2=load(assets,"troop_overflow_v2");buttonV2=load(assets,"confirm_ok_v2");formationLabel=load(assets,"formation");lateBody=load(assets,"rally_too_late");cancel=load(assets,"rally_cancel");city=load(assets,"map_city");search=load(assets,"map_search");bear=load(assets,"bear_label");plus=load(assets,"green_plus");plusColor=load(assets,"green_plus",true);bearTop=load(assets,"bear_label_top");plusTop=load(assets,"green_plus_top");plusTopColor=load(assets,"green_plus_top",true);deploy=load(assets,"deploy");deployColor=load(assets,"deploy",true);for(int i=0;i<6;i++)preset[i]=load(assets,"preset"+i);}
    private Mat load(Assets assets,String key){
        return load(assets,key,false);
    }
    private Mat load(Assets assets,String key,boolean color){
        try(InputStream in=assets.open("feedback/"+key+".png")){
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
        if(!ReferenceLayout.supported(width,height))return;
        int rows=new ReferenceLayout(width,height).feedbackHeight(),middle=(rows-720)/2;
        Mat small=new Mat();
        try{
            Imgproc.resize(fullGray,small,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            Point warning=hit(small,lateBody,new Rect(30,320+middle,270,110));
            Point cancelButton=hit(small,cancel,new Rect(20,420+middle,150,100));
            if(warning!=null&&cancelButton!=null&&warning.x-cancelButton.x>20&&warning.x-cancelButton.x<110&&cancelButton.y-warning.y>40&&cancelButton.y-warning.y<125){
                frame.rallyTooLate=true;frame.cancelX=(int)Math.round(cancelButton.x*width/324);frame.cancelY=(int)Math.round(cancelButton.y*height/rows);return;
            }
            frame.worldMapChrome=hit(small,city,new Rect(275,rows-25,47,25))!=null&&hit(small,search,new Rect(92,rows-120,38,42))!=null;
            if(hit(small,formationLabel,new Rect(29,179,58,24))!=null){
                int markers=0;int[] xs={29,63,96,129,163,196};
                for(int i=0;i<6;i++)if(hit(small,preset[i],new Rect(xs[i]-10,117,20,21))!=null)markers++;
                frame.formation=markers>=2;
            }
            Mat[] bodies={body,bodyV2},buttons={button,buttonV2};
            for(int i=0;i<bodies.length;i++){
                Point text=hit(small,bodies[i],new Rect(50,330+middle,235,110));
                Point ok=hit(small,buttons[i],new Rect(80,425+middle,165,90));
                // Specific error + aligned button; the title may be covered by a floating control.
                if(text!=null&&ok!=null&&Math.abs(text.x-ok.x)<30&&ok.y-text.y>40&&ok.y-text.y<125){
                    frame.troopOverflow=true;frame.dialogOkX=(int)Math.round(ok.x*width/324);frame.dialogOkY=(int)Math.round(ok.y*height/rows);break;
                }
            }
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
        if(!ReferenceLayout.supported(width,height)||plusColor.empty())return result;
        int rows=new ReferenceLayout(width,height).feedbackHeight();
        Mat gray=new Mat(),color=new Mat();
        try{
            Imgproc.resize(fullGray,gray,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            Imgproc.resize(fullColor,color,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            List<Point> targets=hits(gray,bear,new Rect(0,144,120,rows-209));
            targets.addAll(hits(gray,bearTop,new Rect(0,144,120,rows-209)));
            for(Point target:targets){
                int top=Math.max(0,(int)target.y-94),bottom=Math.min(rows,(int)target.y+94);
                Rect band=new Rect(227,top,97,bottom-top);
                List<Point> green=greenHits(gray,color,plus,plusColor,band);
                for(Point p:greenHits(gray,color,plusTop,plusTopColor,band))if(green.stream().noneMatch(q->Math.abs(p.x-q.x)<=6&&Math.abs(p.y-q.y)<=6))green.add(p);
                if(green.size()!=1)continue;
                Point join=green.get(0);
                result.add(new RallyOrder.Detection((int)Math.round(target.x*width/324),(int)Math.round(target.y*height/rows),(int)Math.round(join.x*width/324),(int)Math.round(join.y*height/rows),true,.88));
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
        if(!frame.formation||frame.header||!ReferenceLayout.supported(width,height))return;
        int rows=new ReferenceLayout(width,height).feedbackHeight();
        Mat gray=new Mat(),color=new Mat();
        try{
            Imgproc.resize(fullGray,gray,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            Imgproc.resize(fullColor,color,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            List<Point> buttons=greenHits(gray,color,deploy,deployColor,new Rect(228,rows-42,39,24));
            if(!frame.deploy&&buttons.size()==1){Point p=buttons.get(0);frame.deploy=true;frame.deployX=(int)Math.round(p.x*width/324);frame.deployY=(int)Math.round(p.y*height/rows);}
            int[] x={29,63,96,129,163,196};
            for(int i=0;i<6;i++)if(!frame.savedPreset[i]&&Math.abs(config.p.getInt("presetX"+i,0)*324.0/width-x[i])<6&&Math.abs(config.p.getInt("presetY"+i,0)*rows/(double)height-128)<6&&hit(gray,preset[i],new Rect(x[i]-10,117,20,21))!=null)frame.savedPreset[i]=true;
        }finally{gray.release();color.release();}
    }
    public void close(){body.release();title.release();button.release();bodyV2.release();buttonV2.release();formationLabel.release();lateBody.release();cancel.release();city.release();search.release();bear.release();plus.release();plusColor.release();bearTop.release();plusTop.release();plusTopColor.release();deploy.release();deployColor.release();for(Mat p:preset)p.release();}
}
