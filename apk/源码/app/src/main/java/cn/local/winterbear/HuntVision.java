package cn.local.winterbear;

import android.graphics.Bitmap;
import cn.local.automation.VisualLocator;
import cn.local.automation.VisualAnchor;
import cn.local.automation.VisualTabStrip;
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

/** Normalizes icon size, then locates each page by related visual anchors. */
final class HuntVision implements AutoCloseable {
    interface Numbers { String text(Bitmap image,Rect region); }
    private final Map<String,Mat> templates=new HashMap<>();
    private final Map<String,Mat> mapColors=new HashMap<>();
    private final Map<String,Mat> tabLabels=new java.util.LinkedHashMap<>();
    private final Numbers numbers;
    private final FeedbackVision feedback;
    private final HuntGlyphs glyphs;
    private static final Pattern INTEGER=Pattern.compile("^[0-9]{1,5}$");
    HuntVision(FeedbackVision.Assets assets,Numbers numbers){
        this.numbers=numbers;feedback=new FeedbackVision(assets);glyphs=new HuntGlyphs(assets);
        String[] names={"back","level_label","map_search","map_city","search","tab_beast","tab_giant","selection","level_minus","level_plus","rally_action","attack_action","rally_title","rally_start","formation","deploy","deploy_button","march","energy_title","energy_label","energy_can","energy_use","energy_close","flag0","flag1","flag2","flag3","flag4","flag5"};
        for(String name:names)try(InputStream in=assets.open(name.equals("deploy_button")?"reference/deploy.png":(name.equals("back")?"reference/":"hunt/")+name+".png")){
            Bitmap b=BitmapFactory.decodeStream(in);Mat rgba=new Mat(),gray=new Mat();
            try{
                Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                if(name.equals("map_city")||name.equals("map_search")){
                    Mat rgb=new Mat();Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);mapColors.put(name,rgb);
                }
                // The preset's editable name overlaps the bottom of the old sample.
                // Match only the white flag; names and selected-border colors may vary.
                if(name.startsWith("flag")){Mat symbol=gray.rowRange(0,15).clone();gray.release();gray=symbol;}
                if(name.equals("back")||name.equals("deploy_button")){Mat half=new Mat();Imgproc.resize(gray,half,new Size(),.5,.5,Imgproc.INTER_AREA);gray.release();gray=half;}
                templates.put(name,gray);
            }finally{b.recycle();rgba.release();}
        }catch(Exception e){throw new IllegalStateException("狩猎识别素材不完整："+name,e);}
        for(String key:new String[]{"header"})try(InputStream in=assets.open("reference/"+key+".png")){
            Bitmap b=BitmapFactory.decodeStream(in);Mat rgba=new Mat(),gray=new Mat(),half=new Mat();
            try{Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);Imgproc.resize(gray,half,new Size(),.5,.5,Imgproc.INTER_AREA);templates.put(key,half);}
            finally{b.recycle();rgba.release();gray.release();}
        }catch(Exception e){throw new IllegalStateException("页面返回素材不完整："+key,e);}
        Mat right=new Mat();Core.flip(templates.get("selection"),right,1);templates.put("selection_right",right);
        tabLabels.put("beast",templates.get("tab_beast"));tabLabels.put("giant",templates.get("tab_giant"));
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
        return hit(image,key,l,t,r,b,threshold,false);
    }
    private Point unique(Mat image,String key,int l,int t,int r,int b){
        return hit(image,key,l,t,r,b,.88,true);
    }
    private Point hit(Mat image,String key,int l,int t,int r,int b,double threshold,boolean unique){
        java.util.List<VisualLocator.Match> matches=VisualLocator.findAdaptive(image,templates.get(key),new org.opencv.core.Rect(l,t,Math.max(0,r-l),Math.max(0,b-t)),threshold,unique?2:1,true);
        if(matches.isEmpty()||(unique&&matches.size()!=1))return null;
        return VisualAnchor.from(matches.get(0),templates.get(key));
    }
    private Point near(Mat image,String key,Point anchor,double l,double t,double r,double b,boolean unique){
        org.opencv.core.Rect area=VisualAnchor.near(anchor,l,t,r,b);
        return hit(image,key,area.x,area.y,area.x+area.width,area.y+area.height,.88,unique);
    }
    private Point formation(Mat gray,int rows){
        // “暂无战争增益” also contains this label. The left label owns the preset
        // row; the right caption cannot be used as its coordinate origin.
        java.util.List<VisualLocator.Match> candidates=VisualLocator.findAdaptive(gray,templates.get("formation"),new org.opencv.core.Rect(0,100,300,Math.max(0,rows*2/3-100)),.88,4,true);
        candidates.sort(java.util.Comparator.comparingDouble(m->m.x));
        for(VisualLocator.Match m:candidates){
            Point anchor=VisualAnchor.from(m,templates.get("formation"));
            Point first=near(gray,"flag0",anchor,-65,-131,-20,-77,false);
            Point second=near(gray,"flag1",anchor,-8,-131,36,-77,false);
            if(first!=null&&second!=null)return anchor;
        }
        return null;
    }
    private String read(Bitmap image,double scale,int l,int t,int r,int b){
        Rect region=new Rect(Math.max(0,(int)Math.floor(l*scale)),Math.max(0,(int)Math.floor(t*scale)),Math.min(image.getWidth(),(int)Math.ceil(r*scale)),Math.min(image.getHeight(),(int)Math.ceil(b*scale)));
        return region.width()>0&&region.height()>0?numbers.text(image,region):"";
    }
    private void point(Frame f,Kind k,Point p,double s){if(p!=null)f.point(k,(int)Math.round(p.x*s),(int)Math.round(p.y*s));}
    private boolean green(Mat color,Point p){if(p==null)return false;int x=(int)Math.round(p.x-28*VisualAnchor.scale(p)),y=(int)Math.round(p.y);if(x<0||y<0||x>=color.cols()||y>=color.rows())return false;double[] v=color.get(y,x);return v!=null&&v[1]>v[0]*1.25&&v[1]>v[2]*1.1;}
    private boolean orangeAction(Mat color,Point p){
        if(p==null)return false;
        int orange=0,total=0;
        // Sample the button fill on both sides of its text, not the white label.
        for(int dx:new int[]{-65,-55,55,65})for(int dy:new int[]{-4,0,4}){
            int x=(int)Math.round(p.x+dx*VisualAnchor.scale(p)),y=(int)Math.round(p.y+dy*VisualAnchor.scale(p));
            if(x<0||y<0||x>=color.cols()||y>=color.rows())continue;
            double[] v=color.get(y,x);total++;
            if(v[0]>180&&v[1]>40&&v[1]<180&&v[0]>v[1]*1.4&&v[2]<80)orange++;
        }
        return total==12&&orange>=9;
    }
    Frame navigation(Bitmap image){return observe(image,Mode.GIANT,true);}
    boolean mapVisible(Bitmap image){
        if(!ReferenceLayout.supported(image.getWidth(),image.getHeight()))return false;
        int rows=(int)Math.round(image.getHeight()*540.0/image.getWidth());
        Mat rgba=new Mat(),small=new Mat(),gray=new Mat();
        try{Utils.bitmapToMat(image,rgba);Imgproc.resize(rgba,small,new Size(540,rows),0,0,Imgproc.INTER_AREA);Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);return clearMapEntry(gray,small,rows)!=null;}
        finally{rgba.release();small.release();gray.release();}
    }
    Frame observe(Bitmap image,Mode mode){return observe(image,mode,false);}
    private Frame observe(Bitmap image,Mode mode,boolean navigation){
        Frame f=new Frame();int w=image.getWidth(),h=image.getHeight();f.width=w;f.height=h;if(!ReferenceLayout.supported(w,h))return f;
        double s=w/540.;int rows=(int)Math.round(h/s),delta=rows-1200;
        Mat rgba=new Mat(),small=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(image,rgba);Imgproc.resize(rgba,small,new Size(540,rows),0,0,Imgproc.INTER_AREA);Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
            // Most loop frames are the map. Identify its two independent controls
            // before searching every modal/page pyramid. The opaque bottom bar's
            // color rejects dimmed dialog backdrops. The translucent search icon
            // uses shape, since the moving terrain changes its background color.
            Point mapEntry=clearMapEntry(gray,small,rows);
            if(mapEntry!=null){
                f.page=Page.MAP;point(f,Kind.OPEN,mapEntry,s);if(!navigation)observeMarchPanel(image,s,gray,f);return f;
            }
            Point back=hit(gray,"back",0,0,150,rows/2);
            if(back!=null&&hit(gray,"header",0,0,420,rows/2)!=null){
                f.page=Page.ALLIANCE;point(f,Kind.BACK,back,s);return f;
            }
            // A clear search drawer has three independent, unobscured controls.
            // Check it before unrelated modal pyramids, without treating the
            // dimmed/covered drawer behind a target card as the foreground page.
            if(observeTarget(image,s,gray,small,f))return f;
            if(observeSearch(image,s,gray,small,f,mode))return f;
            Point title=unique(gray,"energy_title",50,60,510,rows-250);
            Point label=title==null?null:near(gray,"energy_label",title,-160,30,-25,105,false);
            if(title!=null&&label!=null&&label.y-title.y>35*VisualAnchor.scale(title)&&label.y-title.y<100*VisualAnchor.scale(title)){
                f.page=Page.ENERGY;
                Point use=near(gray,"energy_use",VisualAnchor.at(title,title.x,label.y),79.5,140,239.5,320,true);
                Point can=use==null?null:near(gray,"energy_can",use,-410,-50,-310,40,false);
                Point close=near(gray,"energy_close",title,190,-25,255,25,false);
                if(use!=null&&can!=null&&green(small,use)){
                    point(f,Kind.USE,use,s);
                    org.opencv.core.Rect stock=VisualAnchor.near(VisualAnchor.at(title,can.x,use.y),6,15,38,35);
                    f.stock=number(read(image,s,stock.x,stock.y,stock.x+stock.width,stock.y+stock.height));
                }
                org.opencv.core.Rect value=VisualAnchor.near(VisualAnchor.at(title,title.x,label.y),-18.5,27,63.5,52);
                String energy=read(image,s,value.x,value.y,value.x+value.width,value.y+value.height).replaceAll("\\s","");
                java.util.regex.Matcher m=Pattern.compile("^([0-9]{1,4})[/／]200$").matcher(energy);if(m.matches())f.energy=Integer.parseInt(m.group(1));
                point(f,Kind.CLOSE,close,s);return f;
            }
            BearFlow.Frame error=new BearFlow.Frame();feedback.observeErrors(gray,error,w,h);
            if(error.troopOverflow||error.rallyTooLate){f.page=Page.ERROR;f.point(Kind.DISMISS,error.rallyTooLate?error.cancelX:error.dialogOkX,error.rallyTooLate?error.cancelY:error.dialogOkY);return f;}
            // The title and blue button both say 发起集结. Resolve the pair by
            // their relationship instead of requiring the title text to be unique.
            Point rallyTitle=null,launch=null;int rallyPairs=0;
            for(VisualLocator.Match m:VisualLocator.findAdaptive(gray,templates.get("rally_title"),new org.opencv.core.Rect(40,100,480,Math.max(0,rows-350)),.88,4,true)){
                Point candidate=VisualAnchor.from(m,templates.get("rally_title"));
                Point button=near(gray,"rally_start",candidate,-120,230,120,410,true);
                if(button!=null){rallyTitle=candidate;launch=button;rallyPairs++;}
            }
            if(rallyPairs!=1){rallyTitle=null;launch=null;}
            if(rallyTitle!=null&&launch!=null&&launch.y-rallyTitle.y>240*VisualAnchor.scale(rallyTitle)&&launch.y-rallyTitle.y<400*VisualAnchor.scale(rallyTitle)){f.page=Page.RALLY;point(f,Kind.LAUNCH,launch,s);point(f,Kind.BACK,near(gray,"energy_close",rallyTitle,180,-30,250,30,false),s);return f;}
            Point form=formation(gray,rows),deploy=unique(gray,"deploy",220,rows/2,540,rows);
            // Existing bear button sample includes more context around the text.
            // Use it only on a verified formation page when the smaller glyph
            // sample loses strokes during downsampling; keep the same threshold.
            if(form!=null&&deploy==null)deploy=unique(gray,"deploy_button",220,rows/2,540,rows);
            if(form!=null&&deploy!=null){
                int flags=0;
                for(int i=0;i<6;i++){
                    double x=-42+55.5*i;
                    Point flag=near(gray,"flag"+i,form,x-22,-131,x+22,-77,false);
                    if(flag!=null){flags++;f.presets[i]=true;f.flags[i]=new int[]{(int)Math.round(flag.x*s),(int)Math.round(flag.y*s)};}
                }
                if(flags>=2){
                    f.page=Page.FORMATION;point(f,Kind.DEPLOY,deploy,s);
                    point(f,Kind.BACK,unique(gray,"back",0,0,150,(int)form.y-110),s);
                    int redY=(int)Math.round(deploy.y+27*VisualAnchor.scale(deploy)),redX=(int)Math.round(deploy.x+8*VisualAnchor.scale(deploy));
                    double[] red=redY>=0&&redX>=0&&redY<rows&&redX<540?small.get(redY,redX):null;f.lowEnergy=red!=null&&red[0]>red[1]*1.4;return f;
                }
            }
            return f;
        }finally{rgba.release();small.release();gray.release();}
    }
    private boolean observeTarget(Bitmap image,double scale,Mat gray,Mat rgba,Frame f){
        // Target cards can leave all search controls visible behind them.
        // Their own action + level pair always takes precedence over the drawer.
        Point rally=null,attack=null;
        // Locate candidate button surfaces from this frame, then read their
        // labels. No full-screen multi-scale search for two absent words on
        // every search-drawer or formation frame.
        for(org.opencv.core.Rect area:orangeButtons(rgba)){
            Point r=unique(gray,"rally_action",area.x,area.y,area.x+area.width,area.y+area.height);
            Point a=unique(gray,"attack_action",area.x,area.y,area.x+area.width,area.y+area.height);
            if(r!=null){if(rally!=null)return false;rally=r;}
            if(a!=null){if(attack!=null)return false;attack=a;}
        }
        Point action=rally!=null&&attack==null?rally:attack!=null&&rally==null?attack:null;
        Point prefix=action==null?null:near(gray,"level_label",action,-105,-205,30,-125,false);
        if(prefix==null||!orangeAction(rgba,action))return false;
        f.page=rally!=null?Page.GIANT:Page.BEAST;point(f,Kind.TARGET,action,scale);
        double q=VisualAnchor.scale(prefix);
        f.level=glyphs.level(image,scale,(int)Math.round(prefix.x+templates.get("level_label").cols()*q/2.),(int)Math.round(prefix.y),q,numbers);
        f.point(Kind.BACK,(int)(Math.max(8,action.x-250*q)*scale),(int)((prefix.y-70*q)*scale));return true;
    }
    private boolean observeSearch(Bitmap image,double scale,Mat gray,Mat rgba,Frame f,Mode mode){
        Mat rgb=new Mat();
        try{
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            java.util.List<VisualLocator.Match> searchMatches=VisualLocator.findAdaptive(gray,templates.get("search"),
                    new org.opencv.core.Rect(0,gray.rows()/2,gray.cols(),gray.rows()-gray.rows()/2),.88,2,true,
                    m->blueSearchFill(rgb,VisualAnchor.from(m,templates.get("search"))));
            Point search=searchMatches.size()==1?VisualAnchor.from(searchMatches.get(0),templates.get("search")):null;
            if(search==null)return false;
            // Plus/minus change between blue and grey at the level limits.
            // Match their shape; the search button already confirms foreground colour.
            Point less=near(gray,"level_minus",search,-270,-165,-155,-75,false);
            Point more=near(gray,"level_plus",search,35,-165,145,-75,false);
            double q=VisualAnchor.scale(search);
            if(less==null||more==null||Math.abs(less.y-more.y)>12*q)return false;
            org.opencv.core.Rect band=VisualAnchor.near(search,0,-255,1,-150);
            // The row's vertical relationship is retained, but its entire visible
            // width is searched. Seasonal entries may appear/disappear/reorder.
            band.x=0;band.width=gray.cols();
            Map<String,VisualTabStrip.Tab> tabs=VisualTabStrip.find(gray,tabLabels,band,
                    templates.get("selection"),templates.get("selection_right"),.88);
            if(tabs.isEmpty())return false;
            VisualTabStrip.Tab beast=tabs.get("beast"),giant=tabs.get("giant");
            if(beast!=null&&giant!=null&&Math.abs(beast.label.y-giant.label.y)>20*q)return false;
            f.page=Page.SEARCH;point(f,Kind.SEARCH,search,scale);
            VisualTabStrip.Tab desired=mode==Mode.GIANT?giant:beast;
            if(desired!=null)point(f,Kind.CATEGORY,desired.label,scale);
            boolean b=beast!=null&&beast.selected,g=giant!=null&&giant.selected;
            if(b!=g)f.category=b?Mode.BEAST.ordinal():Mode.GIANT.ordinal();
            org.opencv.core.Rect value=VisualAnchor.near(VisualAnchor.at(search,less.x,less.y),373,-19,436,13);
            // Only read the level after the requested tab is confirmed selected.
            // Otherwise this could read the previous category's level needlessly.
            if(f.category==mode.ordinal())f.level=number(read(image,scale,value.x,value.y,value.x+value.width,value.y+value.height));
            point(f,Kind.LESS,less,scale);point(f,Kind.MORE,more,scale);
            double tabY=tabs.values().iterator().next().label.y;
            f.point(Kind.BACK,(int)(search.x*scale),(int)(Math.max(30,tabY-200*q)*scale));
            return true;
        }finally{rgb.release();}
    }
    private boolean blueSearchFill(Mat rgb,Point label){
        // Validate the solid button fill away from glyph edges. Comparing each
        // text pixel's RGB would reintroduce sensitivity to 1 px raster changes.
        double q=VisualAnchor.scale(label);int clear=0;
        for(int dx:new int[]{-80,-60,60,80})for(int dy:new int[]{-6,0,6}){
            int x=(int)Math.round(label.x+dx*q),y=(int)Math.round(label.y+dy*q);
            if(x<0||y<0||x>=rgb.cols()||y>=rgb.rows())return false;
            double[] color=rgb.get(y,x);
            if(color[2]>185&&color[1]>110&&color[2]>color[0]*1.25)clear++;
        }
        return clear>=10;
    }
    private java.util.List<org.opencv.core.Rect> orangeButtons(Mat rgba){
        java.util.List<org.opencv.core.Rect> result=new java.util.ArrayList<>();
        java.util.List<MatOfPoint> contours=new java.util.ArrayList<>();Mat mask=new Mat(),hierarchy=new Mat();
        try{
            Core.inRange(rgba,new Scalar(180,40,0,0),new Scalar(255,180,80,255),mask);
            Imgproc.findContours(mask,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
            for(MatOfPoint contour:contours){
                org.opencv.core.Rect r=Imgproc.boundingRect(contour);
                double aspect=r.width/(double)Math.max(1,r.height);
                if(r.width<rgba.cols()*.12||r.height<rgba.cols()*.02||aspect<1.8||aspect>12||Imgproc.contourArea(contour)<r.area()*.65)continue;
                int pad=Math.max(3,r.height/6),l=Math.max(0,r.x-pad),t=Math.max(0,r.y-pad);
                result.add(new org.opencv.core.Rect(l,t,Math.min(rgba.cols(),r.x+r.width+pad)-l,Math.min(rgba.rows(),r.y+r.height+pad)-t));
            }
            return result;
        }finally{for(MatOfPoint c:contours)c.release();mask.release();hierarchy.release();}
    }
    private Point clearMapEntry(Mat gray,Mat rgba,int rows){
        Mat rgb=new Mat();
        try{
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            Point city=mapControl(gray,rgb,"map_city",new org.opencv.core.Rect(390,rows*2/3,150,rows-rows*2/3));
            if(city==null){
                // Zoom may crop the far-right city button. An exposed search
                // icon plus the march heading is independent map evidence;
                // a search drawer replaces that icon. Colour validation also
                // rejects the dim map behind a modal.
                Point entry=mapControl(gray,rgb,"map_search",new org.opencv.core.Rect(0,rows/2,120,rows/2-100));
                return entry!=null&&hit(gray,"march",0,100,180,rows/2,.88)!=null?entry:null;
            }
            Point entry=unique(gray,"map_search",0,rows/2,120,rows-100);
            return entry!=null&&city.x-entry.x>300&&city.y-entry.y>150&&city.y-entry.y<450?entry:null;
        }finally{rgb.release();}
    }
    private Point mapControl(Mat gray,Mat rgb,String name,org.opencv.core.Rect area){
        java.util.List<VisualLocator.Match> found=VisualLocator.findAdaptive(gray,templates.get(name),area,.88,2,true,
                m->VisualLocator.sameColor(rgb,mapColors.get(name),m,25));
        return found.size()==1?VisualAnchor.from(found.get(0),templates.get(name)):null;
    }
    /** Called after map identification; the list has its own movable header anchor. */
    void observeMarchPanel(Bitmap image,double scale,Mat gray,Frame f){
        Point march=hit(gray,"march",0,100,180,gray.rows()/2,.72);
        if(march!=null){
            double q=VisualAnchor.scale(march);
            org.opencv.core.Rect count=VisualAnchor.near(march,94,-16,138,16);
            f.used=MarchCount.used(read(image,scale,count.x,count.y,count.x+count.width,count.y+count.height));
            // Normalize the whole panel coordinate system, not just its title.
            // Row icons, spacing, timers and ownership hashes then share the
            // same scale when the UI is zoomed independently of screen width.
            Mat canonical=gray;
            try{
                if(Math.abs(q-1)>.001){canonical=new Mat();Imgproc.resize(gray,canonical,new Size(),1/q,1/q,Imgproc.INTER_AREA);}
                f.marches=glyphs.marches(image,scale*q,(int)Math.round(march.y/q),f.used,(int)Math.round(march.x/q-52),canonical,numbers);
            }finally{if(canonical!=gray)canonical.release();}
        }
        else{
            // A hidden/missed heading cannot turn an existing team into zero teams.
            f.marchPanelAbsent=true;
        }
    }
    public void close(){for(Mat m:templates.values())m.release();for(Mat m:mapColors.values())m.release();feedback.close();glyphs.close();}
}
