package cn.local.winterbear;

import android.graphics.Bitmap;
import cn.local.automation.VisualLocator;
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
    private final Numbers numbers;
    private final FeedbackVision feedback;
    private final HuntGlyphs glyphs;
    private static final Pattern INTEGER=Pattern.compile("^[0-9]{1,5}$");
    HuntVision(FeedbackVision.Assets assets,Numbers numbers){
        this.numbers=numbers;feedback=new FeedbackVision(assets);glyphs=new HuntGlyphs(assets);
        String[] names={"back","level_label","map_search","map_city","search","tab_beast","tab_giant","selection","level_minus","level_plus","rally_action","attack_action","rally_title","rally_start","formation","deploy","march","energy_title","energy_label","energy_can","energy_use","energy_close","flag0","flag1","flag2","flag3","flag4","flag5"};
        for(String name:names)try(InputStream in=assets.open((name.equals("back")?"reference/":"hunt/")+name+".png")){
            Bitmap b=BitmapFactory.decodeStream(in);Mat rgba=new Mat(),gray=new Mat();
            try{
                Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                // The preset's editable name overlaps the bottom of the old sample.
                // Match only the white flag; names and selected-border colors may vary.
                if(name.startsWith("flag")){Mat symbol=gray.rowRange(0,15).clone();gray.release();gray=symbol;}
                if(name.equals("back")){Mat half=new Mat();Imgproc.resize(gray,half,new Size(),.5,.5,Imgproc.INTER_AREA);gray.release();gray=half;}
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
        java.util.List<VisualLocator.Match> matches=VisualLocator.find(image,templates.get(key),new org.opencv.core.Rect(l,t,Math.max(0,r-l),Math.max(0,b-t)),threshold,unique?2:1,true);
        if(matches.isEmpty()||(unique&&matches.size()!=1))return null;
        VisualLocator.Match m=matches.get(0);return new Point(m.x,m.y);
    }
    private Point formation(Mat gray,int rows){
        // “暂无战争增益” also contains this label. The left label owns the preset
        // row; the right caption cannot be used as its coordinate origin.
        java.util.List<VisualLocator.Match> candidates=VisualLocator.find(gray,templates.get("formation"),new org.opencv.core.Rect(0,100,300,Math.max(0,rows*2/3-100)),.88,4,true);
        candidates.sort(java.util.Comparator.comparingDouble(m->m.x));
        for(VisualLocator.Match m:candidates){
            Point first=hit(gray,"flag0",(int)m.x-65,(int)m.y-131,(int)m.x-20,(int)m.y-77);
            Point second=hit(gray,"flag1",(int)m.x-8,(int)m.y-131,(int)m.x+36,(int)m.y-77);
            if(first!=null&&second!=null)return new Point(m.x,m.y);
        }
        return null;
    }
    private String read(Bitmap image,double scale,int l,int t,int r,int b){
        Rect region=new Rect(Math.max(0,(int)Math.floor(l*scale)),Math.max(0,(int)Math.floor(t*scale)),Math.min(image.getWidth(),(int)Math.ceil(r*scale)),Math.min(image.getHeight(),(int)Math.ceil(b*scale)));
        return region.width()>0&&region.height()>0?numbers.text(image,region):"";
    }
    private void point(Frame f,Kind k,Point p,double s){if(p!=null)f.point(k,(int)Math.round(p.x*s),(int)Math.round(p.y*s));}
    private boolean green(Mat color,Point p){if(p==null)return false;double[] v=color.get((int)p.y,(int)p.x-28);return v!=null&&v[1]>v[0]*1.25&&v[1]>v[2]*1.1;}
    private boolean orangeAction(Mat color,Point p){
        if(p==null)return false;
        int orange=0,total=0;
        // Sample the button fill on both sides of its text, not the white label.
        for(int dx:new int[]{-65,-55,55,65})for(int dy:new int[]{-4,0,4}){
            int x=(int)Math.round(p.x)+dx,y=(int)Math.round(p.y)+dy;
            if(x<0||y<0||x>=color.cols()||y>=color.rows())continue;
            double[] v=color.get(y,x);total++;
            if(v[0]>180&&v[1]>40&&v[1]<180&&v[0]>v[1]*1.4&&v[2]<80)orange++;
        }
        return total==12&&orange>=9;
    }
    Frame observe(Bitmap image,Mode mode){
        Frame f=new Frame();int w=image.getWidth(),h=image.getHeight();if(!ReferenceLayout.supported(w,h))return f;
        double s=w/540.;int rows=(int)Math.round(h/s),delta=rows-1200;
        Mat rgba=new Mat(),small=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(image,rgba);Imgproc.resize(rgba,small,new Size(540,rows),0,0,Imgproc.INTER_AREA);Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
            Point title=unique(gray,"energy_title",50,60,510,rows-250);
            Point label=title==null?null:hit(gray,"energy_label",(int)title.x-160,(int)title.y+30,(int)title.x-25,(int)title.y+105);
            if(title!=null&&label!=null&&label.y-title.y>35&&label.y-title.y<100){
                f.page=Page.ENERGY;
                int dx=(int)Math.round(title.x-270.5);
                Point use=unique(gray,"energy_use",350+dx,(int)label.y+140,510+dx,Math.min(rows,(int)label.y+320));
                Point can=use==null?null:hit(gray,"energy_can",(int)use.x-410,(int)use.y-50,(int)use.x-310,(int)use.y+40);
                Point close=hit(gray,"energy_close",(int)title.x+190,(int)title.y-25,(int)title.x+255,(int)title.y+25);
                if(use!=null&&can!=null&&green(small,use)){
                    point(f,Kind.USE,use,s);
                    int stockX=(int)Math.round(can.x);
                    f.stock=number(read(image,s,stockX+6,(int)use.y+15,stockX+38,(int)use.y+35));
                }
                String energy=read(image,s,252+dx,(int)label.y+27,334+dx,(int)label.y+52).replaceAll("\\s","");
                java.util.regex.Matcher m=Pattern.compile("^([0-9]{1,4})[/／]200$").matcher(energy);if(m.matches())f.energy=Integer.parseInt(m.group(1));
                point(f,Kind.CLOSE,close,s);return f;
            }
            BearFlow.Frame error=new BearFlow.Frame();feedback.observe(gray,error,w,h);
            if(error.troopOverflow||error.rallyTooLate){f.page=Page.ERROR;f.point(Kind.DISMISS,error.rallyTooLate?error.cancelX:error.dialogOkX,error.rallyTooLate?error.cancelY:error.dialogOkY);return f;}
            Point rallyTitle=unique(gray,"rally_title",40,100,520,rows-250);
            Point launch=rallyTitle==null?null:unique(gray,"rally_start",(int)rallyTitle.x-120,(int)rallyTitle.y+230,(int)rallyTitle.x+120,(int)rallyTitle.y+410);
            if(rallyTitle!=null&&launch!=null&&launch.y-rallyTitle.y>240&&launch.y-rallyTitle.y<400){f.page=Page.RALLY;point(f,Kind.LAUNCH,launch,s);point(f,Kind.BACK,hit(gray,"energy_close",(int)rallyTitle.x+180,(int)rallyTitle.y-30,(int)rallyTitle.x+250,(int)rallyTitle.y+30),s);return f;}
            Point form=formation(gray,rows),deploy=unique(gray,"deploy",220,rows/2,540,rows);
            if(form!=null&&deploy!=null){
                int flags=0;
                for(int i=0;i<6;i++){
                    int x=(int)Math.round(form.x-42+55.5*i),y=(int)Math.round(form.y-104);
                    Point flag=hit(gray,"flag"+i,x-22,y-27,x+22,y+27);
                    if(flag!=null){flags++;f.presets[i]=true;f.flags[i]=new int[]{(int)Math.round(flag.x*s),(int)Math.round(flag.y*s)};}
                }
                if(flags>=2){
                    f.page=Page.FORMATION;point(f,Kind.DEPLOY,deploy,s);
                    point(f,Kind.BACK,unique(gray,"back",0,0,150,(int)form.y-110),s);
                    int redY=(int)deploy.y+27,redX=(int)deploy.x+8;
                    double[] red=redY<rows&&redX<540?small.get(redY,redX):null;f.lowEnergy=red!=null&&red[0]>red[1]*1.4;return f;
                }
            }
            Point search=unique(gray,"search",70,rows/2,500,rows);
            int sx=search==null?270:(int)Math.round(search.x),sy=search==null?rows-50:(int)Math.round(search.y);
            Point beast=hit(gray,"tab_beast",sx-245,sy-235,sx-155,sy-165),giant=hit(gray,"tab_giant",sx-145,sy-235,sx-30,sy-165);
            boolean sheet=search!=null&&beast!=null&&giant!=null;
            {
                // A target card can cover the Search label behind it. Its own
                // orange action and level label identify the card independently.
                Point rally=unique(gray,"rally_action",80,250,470,rows-30),attack=unique(gray,"attack_action",80,250,470,rows-30);
                Point action=rally!=null&&attack==null?rally:attack!=null&&rally==null?attack:null;
                Point prefix=action==null?null:hit(gray,"level_label",(int)action.x-105,(int)action.y-183,(int)action.x+30,(int)action.y-142);
                if(prefix!=null&&orangeAction(small,action)){
                    f.page=rally!=null?Page.GIANT:Page.BEAST;point(f,Kind.TARGET,action,s);
                    f.level=glyphs.level(image,s,(int)Math.round(prefix.x+templates.get("level_label").cols()/2.),(int)Math.round(prefix.y),numbers);
                    f.point(Kind.BACK,(int)(Math.max(8,action.x-250)*s),(int)((prefix.y-70)*s));return f;
                }
            }
            if(sheet){
                f.page=Page.SEARCH;point(f,Kind.SEARCH,search,s);
                point(f,Kind.CATEGORY,mode==Mode.GIANT?giant:beast,s);
                Point selected=hit(gray,"selection",sx-270,sy-290,sx-25,sy-230);
                if(selected!=null)f.category=Math.abs(selected.x-(beast.x-51))<Math.abs(selected.x-(giant.x-51))?Mode.BEAST.ordinal():Mode.GIANT.ordinal();
                else{
                    Point corner=hit(gray,"selection_right",(int)beast.x+32,sy-290,(int)beast.x+73,sy-230);
                    Point other=hit(gray,"selection_right",(int)giant.x+32,sy-290,(int)giant.x+73,sy-230);
                    if((corner!=null)!=(other!=null))f.category=corner!=null?Mode.BEAST.ordinal():Mode.GIANT.ordinal();
                }
                Point less=hit(gray,"level_minus",sx-250,sy-150,sx-180,sy-90);
                Point more=hit(gray,"level_plus",sx+50,sy-150,sx+125,sy-90);
                if(less!=null&&(more==null||Math.abs(less.y-more.y)<8)){
                    int dx=(int)Math.round(less.x-52),y=(int)Math.round(less.y);
                    f.level=number(read(image,s,425+dx,y-19,488+dx,y+13));
                    point(f,Kind.LESS,less,s);point(f,Kind.MORE,more,s);
                }
                f.point(Kind.BACK,(int)(search.x*s),(int)(Math.max(30,beast.y-200)*s));return f;
            }
            Point city=unique(gray,"map_city",390,rows*2/3,540,rows),entry=unique(gray,"map_search",0,rows/2,120,rows-100);
            if(city!=null&&entry!=null&&city.x-entry.x>300&&city.y-entry.y>150&&city.y-entry.y<450){
                f.page=Page.MAP;point(f,Kind.OPEN,entry,s);
                Point march=hit(gray,"march",0,100,180,rows/2,.72);
                if(march!=null){int y=(int)march.y,dx=(int)Math.round(march.x-52);f.used=MarchCount.used(read(image,s,146+dx,y-16,190+dx,y+16));f.marches=glyphs.marches(image,s,y,f.used,dx);}
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
