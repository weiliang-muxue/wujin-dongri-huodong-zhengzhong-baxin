package cn.local.winterbear;

import android.graphics.Bitmap;
import cn.local.automation.VisualLocator;
import android.graphics.BitmapFactory;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Related map, bear and error landmarks from the recording; no arbitrary confirmation. */
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
        List<VisualLocator.Match> found=VisualLocator.findAdaptive(screen,template,region,.88,1,true);
        return found.isEmpty()?null:new Point(found.get(0).x,found.get(0).y);
    }
    void observe(Mat fullGray,BearFlow.Frame frame,int width,int height){
        if(!ReferenceLayout.supported(width,height))return;
        int rows=Math.round(height*324f/width);
        observeAt(fullGray,frame,width,height,rows);
        int legacy=new ReferenceLayout(width,height).feedbackHeight();
        if(rows!=legacy&&!frame.troopOverflow&&!frame.rallyTooLate)observeAt(fullGray,frame,width,height,legacy);
    }
    private void observeAt(Mat fullGray,BearFlow.Frame frame,int width,int height,int rows){
        Mat small=new Mat();
        try{
            Imgproc.resize(fullGray,small,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            Point warning=hit(small,lateBody,new Rect(0,rows/4,324,rows/2));
            Point cancelButton=warning==null?null:hit(small,cancel,new Rect((int)warning.x-140,(int)warning.y+35,150,100));
            if(warning!=null&&cancelButton!=null&&warning.x-cancelButton.x>20&&warning.x-cancelButton.x<110&&cancelButton.y-warning.y>40&&cancelButton.y-warning.y<125){
                frame.rallyTooLate=true;frame.cancelX=(int)Math.round(cancelButton.x*width/324);frame.cancelY=(int)Math.round(cancelButton.y*height/rows);return;
            }
            frame.worldMapChrome|=hit(small,city,new Rect(220,rows*2/3,104,rows-rows*2/3))!=null&&hit(small,search,new Rect(55,rows*2/3,150,rows-rows*2/3))!=null;
            frame.formation|=formationAnchor(small,rows)!=null;
            Mat[] bodies={body,bodyV2},buttons={button,buttonV2};
            for(int i=0;i<bodies.length;i++){
                Point text=hit(small,bodies[i],new Rect(0,rows/4,324,rows/2));
                Point ok=text==null?null:hit(small,buttons[i],new Rect((int)text.x-82,(int)text.y+35,165,100));
                // Specific error + aligned button; the title may be covered by a floating control.
                if(text!=null&&ok!=null&&Math.abs(text.x-ok.x)<30&&ok.y-text.y>40&&ok.y-text.y<125){
                    frame.troopOverflow=true;frame.dialogOkX=(int)Math.round(ok.x*width/324);frame.dialogOkY=(int)Math.round(ok.y*height/rows);break;
                }
            }
        }finally{small.release();}
    }
    private List<Point> hits(Mat screen,Mat template,Rect region){
        List<Point> result=new ArrayList<>();
        for(VisualLocator.Match m:VisualLocator.findAdaptive(screen,template,region,.88,8,true))result.add(new Point(m.x,m.y));
        return result;
    }
    List<RallyOrder.Detection> rallies(Mat fullGray,Mat fullColor,int width,int height){
        if(!ReferenceLayout.supported(width,height))return new ArrayList<>();
        int rows=Math.round(height*324f/width);
        List<RallyOrder.Detection> found=ralliesAt(fullGray,fullColor,width,height,rows);
        int legacy=new ReferenceLayout(width,height).feedbackHeight();
        return found.isEmpty()&&legacy!=rows?ralliesAt(fullGray,fullColor,width,height,legacy):found;
    }
    private List<RallyOrder.Detection> ralliesAt(Mat fullGray,Mat fullColor,int width,int height,int rows){
        List<RallyOrder.Detection> result=new ArrayList<>();
        if(!ReferenceLayout.supported(width,height)||plusColor.empty())return result;
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
        for(VisualLocator.Match match:VisualLocator.findAdaptive(gray,shape,region,.88,8,true,
                candidate->VisualLocator.sameColor(color,expected,candidate,35)))result.add(new Point(match.x,match.y));
        return result;
    }
    private Point formationAnchor(Mat gray,int rows){
        List<Point> labels=hits(gray,formationLabel,new Rect(0,80,200,Math.max(0,rows*2/3-80)));
        labels.sort(java.util.Comparator.comparingDouble(p->p.x));
        for(Point label:labels){
            int dx=(int)Math.round(label.x-54),dy=(int)Math.round(label.y-190.5);
            if(hit(gray,preset[0],new Rect(19+dx,117+dy,20,21))!=null&&hit(gray,preset[1],new Rect(53+dx,117+dy,20,21))!=null)return label;
        }return null;
    }
    void formation(Mat fullGray,Mat fullColor,BearFlow.Frame frame,int width,int height,Config config){
        if(!frame.formation||frame.header||!ReferenceLayout.supported(width,height))return;
        int rows=Math.round(height*324f/width);
        formationAt(fullGray,fullColor,frame,width,height,config,rows);
        int legacy=new ReferenceLayout(width,height).feedbackHeight();
        if(rows!=legacy)formationAt(fullGray,fullColor,frame,width,height,config,legacy);
    }
    private void formationAt(Mat fullGray,Mat fullColor,BearFlow.Frame frame,int width,int height,Config config,int rows){
        Mat gray=new Mat(),color=new Mat();
        try{
            Imgproc.resize(fullGray,gray,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            Imgproc.resize(fullColor,color,new Size(324,rows),0,0,Imgproc.INTER_AREA);
            List<Point> buttons=greenHits(gray,color,deploy,deployColor,new Rect(180,rows/2,144,rows-rows/2));
            if(!frame.deploy&&buttons.size()==1){Point p=buttons.get(0);frame.deploy=true;frame.deployX=(int)Math.round(p.x*width/324);frame.deployY=(int)Math.round(p.y*height/rows);}
            Point anchor=formationAnchor(gray,rows);if(anchor==null)return;
            int dx=(int)Math.round(anchor.x-54),dy=(int)Math.round(anchor.y-190.5);
            int[] x={29,63,96,129,163,196};
            boolean automatic=config.p.getBoolean("referenceLayout",false)&&!config.p.getBoolean("manualCalibration",false);
            for(int i=0;i<6;i++)if(!frame.savedPreset[i]&&(automatic||(Math.abs(config.p.getInt("presetX"+i,0)*324.0/width-x[i])<6&&Math.abs(config.p.getInt("presetY"+i,0)*rows/(double)height-128)<6))){
                Point p=hit(gray,preset[i],new Rect(x[i]-10+dx,117+dy,20,21));
                if(p!=null){frame.savedPreset[i]=true;frame.presetX[i]=(int)Math.round(p.x*width/324);frame.presetY[i]=(int)Math.round(p.y*height/rows);}
            }
        }finally{gray.release();color.release();}
    }
    public void close(){body.release();title.release();button.release();bodyV2.release();buttonV2.release();formationLabel.release();lateBody.release();cancel.release();city.release();search.release();bear.release();plus.release();plusColor.release();bearTop.release();plusTop.release();plusTopColor.release();deploy.release();deployColor.release();for(Mat p:preset)p.release();}
}
