package cn.local.winterbear;

import android.graphics.Bitmap;
import cn.local.automation.VisualLocator;
import cn.local.automation.VisualAnchor;
import android.graphics.Rect;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Matcher implements AutoCloseable {
    static final class Hit {
        final int x,y,width,height; final double confidence;
        Hit(int x,int y,int width,int height,double confidence) { this.x=x;this.y=y;this.width=width;this.height=height;this.confidence=confidence; }
    }
    private final Config config;
    private final Map<String,Mat> templates=new HashMap<>();
    private final Map<String,Mat> colorTemplates=new HashMap<>();
    private Mat gray,color;
    private double scale;
    interface Numbers {int read(Bitmap screenshot,Rect region);void close();}
    private final Numbers counter;
    private Bitmap currentBitmap;
    private String stage="初始化";
    String stage(){return stage;}
    private final FeedbackVision feedback;
    private final CountdownVision countdown;
    Matcher(Config config) {
        this(config,new CounterOcr());
    }
    Matcher(Config config,Numbers counter) {
        this.config=config;
        this.counter=counter;
        if(!OpenCVLoader.initLocal()) throw new IllegalStateException("图像识别组件初始化失败");
        feedback=new FeedbackVision(config);
        countdown=new CountdownVision(config::openAsset);
    }
    void frame(Bitmap bitmap) {
        stage="读取截图";
        currentBitmap=bitmap;
        if(gray!=null) gray.release();
        if(color!=null) color.release();
        Mat rgba=new Mat(); Mat original=new Mat(),rgb=new Mat();
        try {
            Utils.bitmapToMat(bitmap,rgba); Imgproc.cvtColor(rgba,original,Imgproc.COLOR_RGBA2GRAY);
            // Keep 1080px reference screenshots at native scale: downsampling tiny flags/text
            // independently from their source frame introduces subpixel misalignment.
            scale=Math.min(1.0,1080.0/bitmap.getWidth());
            gray=new Mat(); Imgproc.resize(original,gray,new Size(),scale,scale,Imgproc.INTER_AREA);
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);color=new Mat();Imgproc.resize(rgb,color,new Size(),scale,scale,Imgproc.INTER_AREA);
        } finally { rgba.release(); original.release();rgb.release(); }
    }
    private Mat template(String key) {
        if(templates.containsKey(key)) return templates.get(key);
        boolean referenceScale=key.equals("bear_label");
        boolean marchCore=key.equals("march_anchor_core");
        String sourceKey=marchCore?"march_anchor":key;
        double factor=referenceScale?config.width()/1080.0:config.p.getFloat(sourceKey+"Scale",1f);
        Bitmap bitmap=config.image(sourceKey); if(bitmap==null) return null;
        Mat rgba=new Mat(), original=new Mat(), result=new Mat(),rgb=new Mat();
        try {
            Utils.bitmapToMat(bitmap,rgba); Imgproc.cvtColor(rgba,original,Imgproc.COLOR_RGBA2GRAY);
            Mat detail=original;
            if(marchCore){int m=Math.round(Math.min(original.cols(),original.rows())*.15f);detail=original.submat(m,original.rows()-m,m,original.cols()-m);}
            try{Imgproc.resize(detail,result,new Size(),scale*factor,scale*factor,referenceScale?Imgproc.INTER_CUBIC:Imgproc.INTER_AREA);}
            finally{if(detail!=original)detail.release();}
            if(key.startsWith("join")||key.equals("deploy")) {
                Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);Mat small=new Mat();
                Imgproc.resize(rgb,small,new Size(),scale*factor,scale*factor,Imgproc.INTER_AREA);colorTemplates.put(key,small);
            }
            templates.put(key,result); return result;
        } finally { bitmap.recycle(); rgba.release(); original.release();rgb.release(); }
    }
    List<Hit> find(String key,Rect region,int limit) {
        List<Hit> hits=new ArrayList<>(); Mat tpl=template(key);
        if(tpl==null || gray==null) return hits;
        int left=0,top=0,right=gray.cols(),bottom=gray.rows();
        if(region!=null) {
            left=Math.max(0,(int)(region.left*scale)); top=Math.max(0,(int)(region.top*scale));
            right=Math.min(right,(int)Math.ceil(region.right*scale)); bottom=Math.min(bottom,(int)Math.ceil(region.bottom*scale));
        }
        if(right<=left || bottom<=top) return hits;
        Mat expected=colorTemplates.get(key);
        for(VisualLocator.Match m:VisualLocator.findAdaptive(gray,tpl,new org.opencv.core.Rect(left,top,right-left,bottom-top),config.threshold(),limit,false,
                expected==null?null:candidate->VisualLocator.sameColor(color,expected,candidate,35)))
            hits.add(new Hit((int)Math.round(m.x/scale),(int)Math.round(m.y/scale),(int)Math.ceil(m.width/scale),(int)Math.ceil(m.height/scale),m.confidence));
        return hits;
    }
    Hit near(String key,int x,int y) {
        Mat tpl=template(key); if(tpl==null) return null;
        int w=(int)Math.ceil(tpl.cols()/scale),h=(int)Math.ceil(tpl.rows()/scale);
        int margin=Math.max(12,config.width()/70);
        List<Hit> hits=find(key,new Rect(x-w/2-margin,y-h/2-margin,x+w/2+margin,y+h/2+margin),1);
        if(hits.isEmpty())return null;
        return hits.get(0);
    }
    BearFlow.Frame observe() {
        stage="识别页面";
        BearFlow.Frame f=new BearFlow.Frame();
        feedback.observe(gray,f,config.width(),config.height());
        if(f.troopOverflow||f.rallyTooLate)return f;
        int w=config.width(),height=config.height();
        Hit header=locate("header",new Rect(0,0,w*3/4,height/2));
        f.header=header!=null;
        Hit deploy=locate("deploy",new Rect(w/2,height/2,w,height));
        f.deploy=deploy!=null;
        if(deploy!=null) { f.deployX=deploy.x; f.deployY=deploy.y; }
        Hit formation=locateFormation(new Rect(0,height/12,w*2/3,height*2/3));
        f.formation=f.formation||formation!=null;
        f.autoJoin=near("auto_join",config.p.getInt("auto_joinX",0),config.p.getInt("auto_joinY",0))!=null;
        Hit march=nearConfigured("march_anchor");String marchKey="march_anchor";
        // The march header moves with in-game scaling and top/bottom insets.
        // Search the left upper half rather than cutting through its old row.
        Rect marchArea=new Rect(0,0,(int)(w*.45),height/2);
        if(march==null){
            List<Hit> moved=find("march_anchor",marchArea,1);
            if(!moved.isEmpty())march=moved.get(0);
        }
        if(march==null){
            // Transparent map backgrounds vary by phone; retain the text and threshold,
            // discarding only the surrounding background. Unreadable visible numbers must wait.
            List<Hit> core=find("march_anchor_core",marchArea,1);
            if(!core.isEmpty()){march=core.get(0);marchKey="march_anchor_core";}
        }
        boolean mapChrome=f.worldMapChrome||(locate("map_city",new Rect(w*2/3,height*2/3,w,height))!=null&&locate("alliance",new Rect(w/2,height*2/3,w,height))!=null);
        f.map=!f.header&&!f.formation&&!f.deploy&&(march!=null||mapChrome);
        if(f.map&&config.marchReady()){
            stage="读取行军数量";
            // Read the number even if the word anchor missed: a clear 6/6 always means full.
            Rect count=marchCounter(march,marchKey);
            f.marchUsed=counter.read(currentBitmap,count);
        }
        f.marchHidden=f.map&&march==null&&mapChrome&&!MarchCount.valid(f.marchUsed);
        stage="识别页面入口";
        Hit h=locate("back",new Rect(0,0,w/3,height/2));if(h!=null){f.backX=h.x;f.backY=h.y;}
        h=locate("world",new Rect(w*2/3,height*2/3,w,height));if(h!=null){f.worldX=h.x;f.worldY=h.y;}
        h=nearConfigured("war");if(h!=null){f.warX=h.x;f.warY=h.y;}
        h=header==null?nearConfigured("rally_tab"):locate("rally_tab",new Rect(0,header.y,w/2,Math.min(height,header.y+w/3)));if(h!=null){f.rallyX=h.x;f.rallyY=h.y;}
        f.rallyList=f.header&&h!=null;
        if(f.map){
            List<Hit> shortcuts=find("shortcut",new Rect((int)(config.width()*.8),(int)(config.height()*.15),config.width(),(int)(config.height()*.8)),1);
            if(!shortcuts.isEmpty()){f.shortcutX=shortcuts.get(0).x;f.shortcutY=shortcuts.get(0).y;}
            h=nearConfigured("alliance");if(h!=null&&config.has("war")&&config.has("rally_tab")){f.allianceX=h.x;f.allianceY=h.y;}
        }
        if(f.formation||f.deploy){
            stage="识别预设编队";
            for(int i=0;i<6;i++){
                Hit flag=presetNear(i,formation);
                f.savedPreset[i]=flag!=null;
                if(flag!=null){f.presetX[i]=flag.x;f.presetY[i]=flag.y;}
            }
            feedback.formation(gray,color,f,config.width(),config.height(),config);
        }
        if(f.rallyList && !f.deploy && config.rallyReady() && !config.customRallyReady()) {
            stage="识别巨熊集结";
            List<RallyOrder.Detection> detections=new ArrayList<>();
            Rect targetColumn=new Rect(0,(int)(config.height()*.16),(int)(config.width()*.45),(int)(config.height()*.94));
            List<Hit> targets=new ArrayList<>(find("bear_label",targetColumn,8));
            // The level caption may be covered or clipped while the fixed bear
            // portrait is still fully visible. Both are activity-specific assets;
            // the paired join button must independently pass its color check.
            targets.addAll(find("bear_reference",targetColumn,8));
            for(Hit target:targets){
                int halfWidth=(int)Math.ceil(target.width/2.0),halfHeight=(int)Math.ceil(target.height/2.0);
                Hit join=joinBeside(new Rect(target.x-halfWidth,target.y-halfHeight,target.x+halfWidth,target.y+halfHeight));
                detections.add(new RallyOrder.Detection(target.x,target.y,join==null?0:join.x,join==null?0:join.y,join!=null,target.confidence));
            }
            List<RallyOrder.Detection> recorded=feedback.rallies(gray,color,config.width(),config.height());
            int distance=Math.max(24,config.height()/12);
            detections.removeIf(d->!d.available&&recorded.stream().anyMatch(r->Math.abs(d.x-r.x)<=distance&&Math.abs(d.y-r.y)<=distance));
            detections.addAll(recorded);
            f.rows.addAll(RallyOrder.rows(detections,distance));
        }else if(f.rallyList && !f.deploy && config.customRallyReady()) {
            stage="识别取样的巨熊集结";
            List<RallyOrder.Detection> detections=new ArrayList<>();
            for(Hit bear:find("bear_row",null,20)) {
                Hit join=customJoin(bear);
                detections.add(new RallyOrder.Detection(bear.x,bear.y,join==null?0:join.x,join==null?0:join.y,join!=null,bear.confidence));
            }
            Mat bearTemplate=template("bear_row");
            if(bearTemplate==null)throw new IllegalStateException("巨熊模板损坏，请重新取样");
            int duplicateDistance=Math.max(12,(int)Math.round(Math.min(bearTemplate.rows(),bearTemplate.cols())/scale*.75));
            f.rows.addAll(RallyOrder.rows(detections,duplicateDistance));
        }
        if(!f.rows.isEmpty()){
            stage="读取每条集结倒计时";
            for(int i=0;i<f.rows.size();i++){
                BearFlow.Row row=f.rows.get(i);
                int seconds=-1;
                if(row.available&&row.x>0&&row.y>0){
                    Hit join=near("join_reference",row.x,row.y);Mat sample=template("join_reference");
                    double q=join!=null&&sample!=null?join.width*scale/sample.cols():1;
                    seconds=countdown.read(gray,(int)Math.round(config.width()*q),row.x,row.y,scale);
                }
                f.rows.set(i,new BearFlow.Row(row.order,row.x,row.y,row.available,seconds));
            }
        }
        return f;
    }
    private Hit nearConfigured(String key){return near(key,config.p.getInt(key+"X",0),config.p.getInt(key+"Y",0));}
    private Hit customJoin(Hit bear){
        Mat sample=template("bear_row"),join=template("join_row");
        if(sample==null||sample.empty()||join==null||join.empty())return null;
        // Manual crops and rowJoinDx/Dy are saved in screenshot pixels. The
        // detected bear supplies the new content scale for their relative geometry.
        double q=bear.width*scale/sample.cols();
        VisualAnchor anchor=new VisualAnchor(bear.x,bear.y,q,1.0/sample.cols());
        int dx=config.p.getInt("rowJoinDx",0),dy=config.p.getInt("rowJoinDy",0);
        double halfWidth=join.cols()/(2*scale),halfHeight=join.rows()/(2*scale);
        org.opencv.core.Rect related=VisualAnchor.near(anchor,dx-halfWidth,dy-halfHeight,dx+halfWidth,dy+halfHeight);
        int margin=Math.max(12,config.width()/70);
        Rect area=new Rect(related.x-margin,related.y-margin,related.x+related.width+margin,related.y+related.height+margin);
        // A larger search envelope never substitutes for a unique, color-checked button.
        List<Hit> hits=find("join_row",area,2);
        return hits.size()==1?hits.get(0):null;
    }
    private Rect marchCounter(Hit march,String key){
        Rect count=config.counterRect();if(march==null)return count;
        Mat label=template(key);if(label==null||label.empty())return count;
        double q=march.width*scale/label.cols();
        double centerError=1.0/scale;
        int x=config.p.getInt("march_anchorX",0),y=config.p.getInt("march_anchorY",0);
        // Carry the observed label scale to both the number's offset and size.
        // Core-only matching uses its own cropped template width, not the full label.
        // A one-pixel template-width/center rounding error is amplified by the
        // distant counter, so include its geometric envelope before OCR.
        org.opencv.core.Rect area=VisualAnchor.near(new VisualAnchor(march.x,march.y,q,1.0/label.cols()),count.left-x,count.top-y,count.right-x,count.bottom-y);
        return new Rect((int)Math.floor(area.x-centerError),(int)Math.floor(area.y-centerError),
                (int)Math.ceil(area.x+area.width+centerError),(int)Math.ceil(area.y+area.height+centerError));
    }
    private Hit presetNear(int i,Hit formation){
        int x=config.p.getInt("presetX"+i,0),y=config.p.getInt("presetY"+i,0);
        if(formation==null)return near("preset_marker"+i,x,y);
        Mat label=template("formation"),flag=template("preset_marker"+i);if(label==null||flag==null)return null;
        double q=formation.width*scale/label.cols();
        x=(int)Math.round(formation.x+(x-config.p.getInt("formationX",0))*q);
        y=(int)Math.round(formation.y+(y-config.p.getInt("formationY",0))*q);
        int halfWidth=(int)Math.ceil(flag.cols()*q/scale/2),halfHeight=(int)Math.ceil(flag.rows()*q/scale/2);
        int margin=Math.max(12,config.width()/70);
        List<Hit> hits=find("preset_marker"+i,new Rect(x-halfWidth-margin,y-halfHeight-margin,x+halfWidth+margin,y+halfHeight+margin),1);
        return hits.isEmpty()?null:hits.get(0);
    }
    private Hit locateFormation(Rect region){
        Hit local=nearConfigured("formation");
        if(local!=null&&presetNear(0,local)!=null&&presetNear(1,local)!=null)return local;
        List<Hit> candidates=find("formation",region,4);candidates.sort(java.util.Comparator.comparingInt(h->h.x));
        for(Hit candidate:candidates){
            int flags=0;
            for(int i=0;i<2;i++)if(presetNear(i,candidate)!=null)flags++;
            if(flags==2)return candidate;
        }
        return null;
    }
    Hit locate(String key,Rect region){
        Hit fixed=nearConfigured(key);if(fixed!=null)return fixed;
        // Search the page region only after the quick local match misses. Multiple
        // lookalikes are ambiguous; never select an arbitrary one to click.
        // Manual calibration changes the saved template, not the relocation rules.
        List<Hit> matches=find(key,region,2);
        return matches.size()==1?matches.get(0):null;
    }
    Hit joinBeside(Rect target){
        if(!config.has("join_reference"))return null;
        int band=(int)(config.height()*.13);
        Rect area=new Rect(Math.max(target.right,(int)(config.width()*.70)),Math.max(0,target.centerY()-band),config.width(),Math.min(config.height(),target.centerY()+band));
        Hit result=null;
        for(Hit hit:find("join_reference",area,4)){
            if(result!=null&&Math.hypot(result.x-hit.x,result.y-hit.y)>config.width()*.025)return null;
            if(result==null||hit.confidence>result.confidence)result=hit;
        }
        return result;
    }
    @Override public void close() {
        counter.close();currentBitmap=null;
        feedback.close();
        if(gray!=null) { gray.release();gray=null; }
        if(color!=null){color.release();color=null;}
        for(Mat mat:templates.values()) mat.release(); templates.clear();
        for(Mat mat:colorTemplates.values())mat.release();colorTemplates.clear();
    }
}
