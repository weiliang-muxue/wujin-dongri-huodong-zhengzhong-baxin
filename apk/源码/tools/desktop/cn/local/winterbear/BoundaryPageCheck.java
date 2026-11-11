package cn.local.winterbear;

import android.graphics.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import org.opencv.core.Core;
import static cn.local.winterbear.HuntFlow.*;

/** Adjacent scales, small aspect changes and system-bar padding; no OCR/gesture claims. */
public final class BoundaryPageCheck {
    record Variant(String name,double sx,double sy,int top,int bottom){}
    static final Variant[] variants={
        new Variant("97pct",.97,.97,0,0),new Variant("99pct",.99,.99,0,0),
        new Variant("101pct",1.01,1.01,0,0),new Variant("103pct",1.03,1.03,0,0),
        new Variant("wide102",1.02,1,0,0),new Variant("tall102",1,1.02,0,0),
        new Variant("bars",1,1,96,32),new Variant("bars99",.99,.99,96,32)
    };
    static double tx(Bitmap b,Variant v){return b.getWidth()*(1-v.sx)/2-(v.sx>1?24:0);}
    static double ty(Bitmap b,Variant v){return (b.getHeight()-40)*(1-v.sy)+v.top;}
    static Bitmap transform(Bitmap b,Variant v){
        BufferedImage out=new BufferedImage(b.getWidth(),b.getHeight()+v.top+v.bottom,BufferedImage.TYPE_INT_RGB);
        var g=out.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(b.image,new java.awt.geom.AffineTransform(v.sx,0,0,v.sy,tx(b,v),ty(b,v)),null);g.dispose();return new Bitmap(out);
    }
    static double error(int[] found,int[] before,Bitmap b,Variant v){return Math.hypot(found[0]-(before[0]*v.sx+tx(b,v)),found[1]-(before[1]*v.sy+ty(b,v)));}
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);
        Path assets=Path.of(args[1]),fixtures=Path.of(args[2]);PageSweepCheck.output=Path.of(args[3]);
        Files.createDirectories(PageSweepCheck.output);
        try(var report=new java.io.PrintWriter(Files.newBufferedWriter(PageSweepCheck.output.resolve("boundary-pages.tsv")))){
            PageSweepCheck.report=report;report.println("case\tresult\tdetail");
            String[][] cases={{"giant_search","SEARCH"},{"giant_detail","GIANT"},{"rally","RALLY"},{"huawei-formation","FORMATION"},{"beast_detail","BEAST"},{"energy","ENERGY"}};
            try(HuntVision vision=new HuntVision(p->Files.newInputStream(assets.resolve(p)),(b,r)->"")){
                for(String[] item:cases){
                    Bitmap base=BitmapFactory.decodeFile(fixtures.resolve(item[0]+".png").toString());
                    Mode mode=item[0].startsWith("beast")?Mode.BEAST:Mode.GIANT;Page expected=Page.valueOf(item[1]);Frame ref=vision.observe(base,mode);
                    Kind required=switch(expected){case SEARCH->Kind.SEARCH;case GIANT,BEAST->Kind.TARGET;case RALLY->Kind.LAUNCH;case FORMATION->Kind.DEPLOY;case ENERGY->Kind.USE;default->throw new AssertionError(expected);};
                    if(ref.page!=expected||ref.points[required.ordinal()][0]<=0)throw new AssertionError("Invalid baseline "+item[0]);
                    if(expected==Page.ENERGY&&ref.points[Kind.CLOSE.ordinal()][0]<=0)throw new AssertionError("Missing baseline energy close");
                    for(Variant v:variants){Bitmap image=transform(base,v);Frame f=vision.observe(image,mode);boolean ok=f.page==expected;double max=0;
                        for(Kind k:Kind.values())if(k!=Kind.BACK&&ref.points[k.ordinal()][0]>0){double e=error(f.points[k.ordinal()],ref.points[k.ordinal()],base,v);max=Math.max(max,e);ok&=e<=12;}
                        if(expected==Page.GIANT||expected==Page.BEAST)ok&=f.level==(expected==Page.GIANT?8:30);
                        if(expected==Page.SEARCH)ok&=f.category==Mode.GIANT.ordinal();
                        if(expected==Page.FORMATION)for(int i=0;i<6;i++){max=Math.max(max,error(f.flags[i],ref.flags[i],base,v));ok&=ref.presets[i]&&f.presets[i];}
                        PageSweepCheck.record(item[0]+"-"+v.name,ok&&max<=12,"page="+f.page+" level="+f.level+" max_error="+max,image);
                    }
                }
            }
            for(String name:new String[]{"huawei-bear-list","video-170","video-315"}){
                Bitmap base=BitmapFactory.decodeFile(fixtures.resolve(name+".png").toString());boolean formation=name.equals("video-170");BearFlow.Frame ref;
                try(Matcher m=new Matcher(new Config(assets,Path.of(args[4]),base.getWidth(),base.getHeight()),new CounterOcr())){m.frame(base);ref=m.observe();}
                for(Variant v:variants){Bitmap image=transform(base,v);boolean ok;double max=0;
                    try(Matcher m=new Matcher(new Config(assets,Path.of(args[4]),image.getWidth(),image.getHeight()),new CounterOcr())){
                        m.frame(image);BearFlow.Frame f=m.observe();
                        if(formation){ok=f.formation&&f.deploy;max=error(new int[]{f.deployX,f.deployY},new int[]{ref.deployX,ref.deployY},base,v);for(int i=0;i<6;i++){max=Math.max(max,error(new int[]{f.presetX[i],f.presetY[i]},new int[]{ref.presetX[i],ref.presetY[i]},base,v));ok&=ref.savedPreset[i]&&f.savedPreset[i];}}
                        else{var available=f.rows.stream().filter(r->r.available).sorted(Comparator.comparingInt(r->r.y)).toList();var original=ref.rows.stream().filter(r->r.available).sorted(Comparator.comparingInt(r->r.y)).toList();
                            // Timer expectations are read from the screenshot, not copied from the recognizer.
                            int[] seconds=name.equals("video-315")?new int[]{38,92,107}:new int[]{-1};
                            ok=f.rallyList&&available.size()==seconds.length&&original.size()==seconds.length;
                            if(ok)for(int i=0;i<seconds.length;i++){max=Math.max(max,error(new int[]{available.get(i).x,available.get(i).y},new int[]{original.get(i).x,original.get(i).y},base,v));ok&=available.get(i).secondsLeft==seconds[i];}}
                        PageSweepCheck.record(name+"-"+v.name,ok&&max<=12,"formation="+f.formation+" rows="+f.rows.size()+" max_error="+max,image);
                        if(formation){
                            Properties p=new Properties();p.setProperty("type","bear");p.setProperty("formation","true");
                            p.setProperty("deploy",(ref.deployX*v.sx+tx(base,v))+","+(ref.deployY*v.sy+ty(base,v)));
                            for(int i=0;i<6;i++)p.setProperty("flag."+i,(ref.presetX[i]*v.sx+tx(base,v))+","+(ref.presetY[i]*v.sy+ty(base,v)));
                            PageSweepCheck.export(name+"-"+v.name,image,p);
                        }
                    }
                }
            }
        }
        System.out.println("BOUNDARY PAGES: "+PageSweepCheck.checks+" checks, "+PageSweepCheck.failures+" failures");
        if(PageSweepCheck.failures>0)System.exit(1);
    }
}
