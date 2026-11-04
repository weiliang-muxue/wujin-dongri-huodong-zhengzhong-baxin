package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static cn.local.winterbear.HuntFlow.*;

/** Width-normalized fixed UI cues; bottom controls remain anchored to the bottom edge. */
final class HuntVision implements AutoCloseable {
    interface Numbers { String text(Bitmap image,Rect region); }
    private final Map<String,Mat> templates=new HashMap<>();
    private final Numbers numbers;
    private final FeedbackVision feedback;
    private final HuntGlyphs glyphs;
    private static final Pattern INTEGER=Pattern.compile("^[0-9]{1,5}$");
    HuntVision(FeedbackVision.Assets assets,Numbers numbers){
        this.numbers=numbers;feedback=new FeedbackVision(assets);glyphs=new HuntGlyphs(assets);
        String[] names={"level_label","map_search","map_city","search","tab_beast","tab_giant","selection","level_minus","level_plus","rally_action","attack_action","rally_title","rally_start","formation","deploy","march","energy_title","energy_label","energy_can","energy_use","energy_close","flag0","flag1","flag2","flag3","flag4","flag5"};
        for(String name:names)try(InputStream in=assets.open("hunt/"+name+".png")){
            Bitmap b=BitmapFactory.decodeStream(in);Mat rgba=new Mat(),gray=new Mat();
            try{Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);templates.put(name,gray);}finally{b.recycle();rgba.release();}
        }catch(Exception e){throw new IllegalStateException("狩猎识别素材不完整："+name,e);}
    }
    static int number(String text){String s=text==null?"":text.replaceAll("\\s","");return INTEGER.matcher(s).matches()?Integer.parseInt(s):-1;}
    static int level(String text){
        java.util.regex.Matcher m=Pattern.compile("(?<![0-9])([0-9]{1,2})(?![0-9])").matcher(text==null?"":text);
        if(!m.find())return -1;int n=Integer.parseInt(m.group(1));return m.find()?-1:n;
    }
    private Point hit(Mat image,String key,int l,int t,int r,int b){
        return hit(image,key,l,t,r,b,.88);
    }
    private Point hit(Mat image,String key,int l,int t,int r,int b,double threshold){
        Mat template=templates.get(key);l=Math.max(0,l);t=Math.max(0,t);r=Math.min(image.cols(),r);b=Math.min(image.rows(),b);
        if(r-l<template.cols()||b-t<template.rows())return null;
        Mat roi=image.submat(t,b,l,r),score=new Mat();
        try{Imgproc.matchTemplate(roi,template,score,Imgproc.TM_CCOEFF_NORMED);Core.MinMaxLocResult m=Core.minMaxLoc(score);
            // An odd physical-pixel vertical offset becomes half a pixel after
            // normalization (e.g. 1080x2374). Compare both mildly smoothed images
            // only for a near match, with a stricter score than the raw matcher.
            if(Double.isFinite(m.maxVal)&&m.maxVal>=.78&&m.maxVal<threshold){
                Mat smoothImage=new Mat(),smoothTemplate=new Mat();
                try{
                    Imgproc.GaussianBlur(roi,smoothImage,new Size(3,3),1);
                    Imgproc.GaussianBlur(template,smoothTemplate,new Size(3,3),1);
                    Imgproc.matchTemplate(smoothImage,smoothTemplate,score,Imgproc.TM_CCOEFF_NORMED);
                    Core.MinMaxLocResult soft=Core.minMaxLoc(score);
                    if(Double.isFinite(soft.maxVal)&&soft.maxVal>=Math.max(.91,threshold))m=soft;
                }finally{smoothImage.release();smoothTemplate.release();}
            }
            return Double.isFinite(m.maxVal)&&m.maxVal>=threshold?new Point(l+m.maxLoc.x+template.cols()/2.,t+m.maxLoc.y+template.rows()/2.):null;
        }finally{roi.release();score.release();}
    }
    private String read(Bitmap image,double scale,int l,int t,int r,int b){
        Rect region=new Rect(Math.max(0,(int)Math.floor(l*scale)),Math.max(0,(int)Math.floor(t*scale)),Math.min(image.getWidth(),(int)Math.ceil(r*scale)),Math.min(image.getHeight(),(int)Math.ceil(b*scale)));
        return region.width()>0&&region.height()>0?numbers.text(image,region):"";
    }
    private void point(Frame f,Kind k,Point p,double s){if(p!=null)f.point(k,(int)Math.round(p.x*s),(int)Math.round(p.y*s));}
    private boolean green(Mat color,Point p){if(p==null)return false;double[] v=color.get((int)p.y,(int)p.x-28);return v!=null&&v[1]>v[0]*1.25&&v[1]>v[2]*1.1;}
    Frame observe(Bitmap image,Mode mode){
        Frame f=new Frame();int w=image.getWidth(),h=image.getHeight();if(!ReferenceLayout.supported(w,h))return f;
        double s=w/540.;int rows=(int)Math.round(h/s),delta=rows-1200;
        Mat rgba=new Mat(),small=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(image,rgba);Imgproc.resize(rgba,small,new Size(540,rows),0,0,Imgproc.INTER_AREA);Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
            Point title=hit(gray,"energy_title",170,120,375,rows-300),label=hit(gray,"energy_label",100,140,270,rows-250);
            if(title!=null&&label!=null&&label.y-title.y>35&&label.y-title.y<100){
                f.page=Page.ENERGY;
                Point use=hit(gray,"energy_use",350,(int)label.y+140,510,Math.min(rows,(int)label.y+320));
                Point can=use==null?null:hit(gray,"energy_can",25,(int)use.y-50,125,(int)use.y+40);
                Point close=hit(gray,"energy_close",465,(int)title.y-25,525,(int)title.y+25);
                if(use!=null&&can!=null&&green(small,use)){
                    point(f,Kind.USE,use,s);
                    f.stock=number(read(image,s,82,(int)use.y+15,114,(int)use.y+35));
                }
                String energy=read(image,s,252,(int)label.y+27,334,(int)label.y+52).replaceAll("\\s","");
                java.util.regex.Matcher m=Pattern.compile("^([0-9]{1,4})[/／]200$").matcher(energy);if(m.matches())f.energy=Integer.parseInt(m.group(1));
                point(f,Kind.CLOSE,close,s);return f;
            }
            BearFlow.Frame error=new BearFlow.Frame();feedback.observe(gray,error,w,h);
            if(error.troopOverflow||error.rallyTooLate){f.page=Page.ERROR;f.point(Kind.DISMISS,error.rallyTooLate?error.cancelX:error.dialogOkX,error.rallyTooLate?error.cancelY:error.dialogOkY);return f;}
            Point rallyTitle=hit(gray,"rally_title",150,200,400,rows-300),launch=hit(gray,"rally_start",160,400,390,rows-100);
            if(rallyTitle!=null&&launch!=null&&launch.y-rallyTitle.y>240&&launch.y-rallyTitle.y<400){f.page=Page.RALLY;point(f,Kind.LAUNCH,launch,s);point(f,Kind.BACK,hit(gray,"energy_close",450,(int)rallyTitle.y-30,510,(int)rallyTitle.y+30),s);return f;}
            Point form=hit(gray,"formation",40,285,145,350),deploy=hit(gray,"deploy",320,rows-95,500,rows-20);
            if(form!=null&&deploy!=null){
                int flags=0;
                for(int i=0;i<6;i++){
                    int x=(98+111*i)/2;Point flag=hit(gray,"flag"+i,x-18,190,x+18,240);
                    if(flag!=null){flags++;f.presets[i]=true;f.flags[i]=new int[]{(int)Math.round(flag.x*s),(int)Math.round(flag.y*s)};}
                }
                if(flags>=2){
                    f.page=Page.FORMATION;point(f,Kind.DEPLOY,deploy,s);
                    f.point(Kind.BACK,(int)(35*s),(int)(153*s));
                    TroopCount troop=TroopCount.parse(read(image,s,49,254,187,282));f.knownTroops=troop!=null;f.validTroops=troop!=null&&troop.usable();
                    double[] red=small.get(rows-38,420);f.lowEnergy=red!=null&&red[0]>red[1]*1.4;return f;
                }
            }
            Point search=hit(gray,"search",200,rows-90,340,rows-20);
            Point beast=hit(gray,"tab_beast",30,rows-286,110,rows-221),giant=hit(gray,"tab_giant",130,rows-286,235,rows-221);
            boolean sheet=search!=null&&beast!=null&&giant!=null;
            if(search!=null){
                Point rally=hit(gray,"rally_action",205,350,335,rows-90),attack=hit(gray,"attack_action",205,350,335,rows-90);
                Point action=rally!=null?rally:attack;
                if(action!=null){
                    f.page=rally!=null?Page.GIANT:Page.BEAST;point(f,Kind.TARGET,action,s);
                    Point prefix=hit(gray,"level_label",165,(int)action.y-183,300,(int)action.y-142);
                    if(prefix!=null)f.level=glyphs.level(image,s,(int)Math.round(prefix.x+templates.get("level_label").cols()/2.),(int)Math.round(prefix.y),numbers);
                    f.point(Kind.BACK,(int)(15*s),(int)(rows*.45*s));return f;
                }
            }
            if(sheet){
                f.page=Page.SEARCH;point(f,Kind.SEARCH,search,s);
                point(f,Kind.CATEGORY,mode==Mode.GIANT?giant:beast,s);
                Point selected=hit(gray,"selection",0,rows-340,245,rows-280);
                if(selected!=null)f.category=selected.x<100?Mode.BEAST.ordinal():Mode.GIANT.ordinal();
                f.level=number(read(image,s,425,rows-187,488,rows-155));
                point(f,Kind.LESS,hit(gray,"level_minus",20,rows-200,85,rows-140),s);
                point(f,Kind.MORE,hit(gray,"level_plus",330,rows-200,395,rows-140),s);
                f.point(Kind.BACK,(int)(270*s),(int)(710*s));return f;
            }
            Point city=hit(gray,"map_city",460,rows-45,535,rows),entry=hit(gray,"map_search",0,rows-355,85,rows-240);
            if(city!=null&&entry!=null){
                f.page=Page.MAP;point(f,Kind.OPEN,entry,s);
                Point march=hit(gray,"march",15,230,100,400,.72);
                if(march!=null){int y=(int)march.y;f.used=MarchCount.used(read(image,s,146,y-16,190,y+16));f.marches=glyphs.marches(image,s,y,f.used);}
                else{
                    int used=MarchCount.used(read(image,s,146,290,190,320));
                    // The absent header is accepted only with two independently recognized map controls.
                    f.used=used>=0?used:0;
                    if(f.used==0)f.marches=new HuntMarches.Row[0];
                }
            }
            return f;
        }finally{rgba.release();small.release();gray.release();}
    }
    public void close(){for(Mat m:templates.values())m.release();feedback.close();}
}
