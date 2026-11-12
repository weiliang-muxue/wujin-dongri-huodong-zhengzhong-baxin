package cn.local.winterbear;

import android.graphics.Bitmap;
import cn.local.automation.VisualLocator;
import cn.local.automation.VisualAnchor;
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
        Mat right=new Mat();Core.flip(templates.get("selection"),right,1);templates.put("selection_right",right);
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
    Frame observe(Bitmap image,Mode mode){
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
                f.page=Page.MAP;point(f,Kind.OPEN,mapEntry,s);observeMarchPanel(image,s,gray,f);return f;
            }
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
            VisualLocator.Match searchMatch=VisualLocator.uniqueAdaptive(gray,templates.get("search"),new org.opencv.core.Rect(70,rows/2,430,rows-rows/2),.88,true);
            Point search=searchMatch==null?null:VisualAnchor.from(searchMatch,templates.get("search"));
            int sx=search==null?270:(int)Math.round(search.x),sy=search==null?rows-50:(int)Math.round(search.y);
            Point sheetAnchor=search==null?new Point(sx,sy):search;
            Point beast=near(gray,"tab_beast",sheetAnchor,-245,-235,-155,-165,false),giant=near(gray,"tab_giant",sheetAnchor,-145,-235,-30,-165,false);
            boolean sheet=search!=null&&beast!=null&&giant!=null;
            {
                // A target card can cover the Search label behind it. Its own
                // orange action and level label identify the card independently.
                Point rally=unique(gray,"rally_action",80,250,470,rows-30),attack=unique(gray,"attack_action",80,250,470,rows-30);
                Point action=rally!=null&&attack==null?rally:attack!=null&&rally==null?attack:null;
                // Short action labels provide a noisier scale estimate than the
                // level prefix, especially after JPEG resampling. Keep enough
                // relative vertical margin to contain the complete prefix.
                Point prefix=action==null?null:near(gray,"level_label",action,-105,-205,30,-125,false);
                if(prefix!=null&&orangeAction(small,action)){
                    f.page=rally!=null?Page.GIANT:Page.BEAST;point(f,Kind.TARGET,action,s);
                    double q=VisualAnchor.scale(prefix);
                    f.level=glyphs.level(image,s,(int)Math.round(prefix.x+templates.get("level_label").cols()*q/2.),(int)Math.round(prefix.y),q,numbers);
                    f.point(Kind.BACK,(int)(Math.max(8,action.x-250*q)*s),(int)((prefix.y-70*q)*s));return f;
                }
            }
            if(sheet){
                f.page=Page.SEARCH;point(f,Kind.SEARCH,search,s);
                point(f,Kind.CATEGORY,mode==Mode.GIANT?giant:beast,s);
                double q=VisualAnchor.scale(search);
                Point selected=near(gray,"selection",search,-270,-290,-25,-230,false);
                if(selected!=null)f.category=Math.abs(selected.x-(beast.x-51*q))<Math.abs(selected.x-(giant.x-51*q))?Mode.BEAST.ordinal():Mode.GIANT.ordinal();
                else{
                    Point corner=near(gray,"selection_right",VisualAnchor.at(search,beast.x,search.y),32,-290,73,-230,false);
                    Point other=near(gray,"selection_right",VisualAnchor.at(search,giant.x,search.y),32,-290,73,-230,false);
                    if((corner!=null)!=(other!=null))f.category=corner!=null?Mode.BEAST.ordinal():Mode.GIANT.ordinal();
                }
                Point less=near(gray,"level_minus",search,-250,-150,-180,-90,false);
                Point more=near(gray,"level_plus",search,50,-150,125,-90,false);
                if(less!=null&&(more==null||Math.abs(less.y-more.y)<8*q)){
                    org.opencv.core.Rect value=VisualAnchor.near(VisualAnchor.at(search,less.x,less.y),373,-19,436,13);
                    f.level=number(read(image,s,value.x,value.y,value.x+value.width,value.y+value.height));
                    point(f,Kind.LESS,less,s);point(f,Kind.MORE,more,s);
                }
                f.point(Kind.BACK,(int)(search.x*s),(int)(Math.max(30,beast.y-200*q)*s));return f;
            }
            return f;
        }finally{rgba.release();small.release();gray.release();}
    }
    private Point clearMapEntry(Mat gray,Mat rgba,int rows){
        Mat rgb=new Mat();
        try{
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            Point city=mapControl(gray,rgb,"map_city",new org.opencv.core.Rect(390,rows*2/3,150,rows-rows*2/3));
            if(city==null)return null;
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
