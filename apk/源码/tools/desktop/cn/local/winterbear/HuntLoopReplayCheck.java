package cn.local.winterbear;

import android.graphics.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import static cn.local.winterbear.HuntFlow.*;

/** Full map recognition, including row ownership, rather than a rows-only shortcut. */
public final class HuntLoopReplayCheck {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Bitmap mask(Bitmap image,int x,int y,int w,int h){
        java.awt.Graphics2D g=image.image.createGraphics();g.setColor(java.awt.Color.BLACK);g.fillRect(x,y,w,h);g.dispose();return image;
    }
    private static void checkFlow(Frame f,String name){
        for(Mode mode:Mode.values()){
            HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,100));flow.start(0);flow.cansUsed=98;
            flow.next(f,1000);Action action=flow.next(f,2000);
            require(action.kind==Kind.OPEN,name+" did not start with 5 external teams: "+action.message);
            flow.suspend(2100,false);flow.resume(3000);
            action=flow.next(f,4000);
            require(action.kind==Kind.OPEN&&flow.cansUsed==98&&flow.marches.active()==0,name+" pause/resume reset quota or stalled");
        }
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);Path assets=Path.of(args[1]),frames=Path.of(args[2]),output=Path.of(args[3]);Files.createDirectories(output);
        int checks=0,readable=0;double worst=0;int[] clock={16702},ocrCalls={0};
        try(HuntVision vision=new HuntVision(p->Files.newInputStream(assets.resolve(p)),(b,r)->{
                ocrCalls[0]++;
                if(b.getWidth()==180&&b.getHeight()==38){
                    try{ImageIO.write(b.image,"png",output.resolve("clock-"+clock[0]+"-"+ocrCalls[0]+".png").toFile());}catch(IOException e){throw new UncheckedIOException(e);}
                    return String.format(Locale.ROOT,"%02d:%02d:%02d",clock[0]/3600,clock[0]/60%60,clock[0]%60);
                }
                return "5/6";
            });
            PrintWriter report=new PrintWriter(Files.newBufferedWriter(output.resolve("full-loop.tsv")))){
            report.println("case\tpage\trows\tms");
            for(int second=0;second<=16;second+=2){
                clock[0]=16702-second;int beforeOcr=ocrCalls[0];
                Bitmap image=mask(BitmapFactory.decodeFile(frames.resolve(String.format(Locale.ROOT,"%03d.jpg",second)).toString()),245,60,370,155);
                long start=System.nanoTime();Frame f=vision.observe(image,Mode.GIANT);double ms=(System.nanoTime()-start)/1e6;worst=Math.max(worst,ms);
                require(f.page==Page.MAP&&f.used==5,"Recording "+second+" page="+f.page+" count="+f.used);
                // The screenshot lifecycle rejects >4s frames. Check the complete
                // pixel path against that deadline, with count OCR annotated here.
                require(ms<4000,"Recording exceeded controller freshness deadline");
                require(ocrCalls[0]-beforeOcr<=2,"Unbounded row OCR");
                if(f.marches!=null){require(f.marches.length==5,"Wrong list size");checkFlow(f,"second="+second);readable++;}
                else{HuntFlow flow=new HuntFlow(new Options(Mode.GIANT,8,1,100));flow.start(0);require(flow.next(f,1000).kind==Kind.WAIT,"Unreadable rows clicked");}
                checks++;
                report.printf(Locale.ROOT,"recording-%d\t%s\t%s\t%.1f%n",second,f.page,f.marches==null?"unknown":f.marches.length,ms);report.flush();
                System.out.printf(Locale.ROOT,"PASS recording-%d MAP rows=%s %.1fms%n",second,f.marches==null?"unknown (wait)":f.marches.length,ms);
                if(second==2||second==8)ImageIO.write(image.image,"png",output.resolve("map-"+second+".png").toFile());image.recycle();
            }
            require(readable>=7,"Recording repeatedly fails complete list recognition");
            Bitmap base=BitmapFactory.decodeFile(output.resolve("map-8.png").toString());
            for(double zoom:new double[]{.98,1.02}){
                clock[0]=16694;
                Bitmap image=PageSweepCheck.changed(base,zoom,0,0,false);Frame f=vision.observe(image,Mode.GIANT);
                require(f.page==Page.MAP&&f.used==5&&f.marches!=null&&f.marches.length==5,"Map zoom "+zoom);checks++;
                checkFlow(f,"zoom="+zoom);
                ImageIO.write(image.image,"png",output.resolve("zoom-"+zoom+".png").toFile());
                System.out.println("PASS map zoom="+zoom+" rows="+(f.marches==null?"unknown (wait)":f.marches.length));image.recycle();
                int complete=0;
                for(int second=0;second<=8;second+=2){
                    clock[0]=16702-second;
                    Bitmap source=mask(BitmapFactory.decodeFile(frames.resolve(String.format(Locale.ROOT,"%03d.jpg",second)).toString()),245,60,370,155);
                    Bitmap next=PageSweepCheck.changed(source,zoom,0,0,false);Frame observed=vision.observe(next,Mode.GIANT);
                    if(observed.page==Page.MAP&&observed.marches!=null){checkFlow(observed,"zoom sequence "+zoom+" at "+second);complete++;}
                    source.recycle();next.recycle();
                }
                require(complete==5,"Zoomed sequence lost rows: "+zoom+" complete="+complete);
                checks++;System.out.println("PASS zoom sequence="+zoom+" complete="+complete+"/5");
            }
            Bitmap covered=mask(PageSweepCheck.changed(base,1,0,0,false),4,370,211,46);Frame missing=vision.observe(covered,Mode.GIANT);
            require(missing.page==Page.MAP&&missing.used==5&&missing.marches==null,"Missing first row invented a team");checks++;
            ImageIO.write(covered.image,"png",output.resolve("covered-row.png").toFile());
            Bitmap dim=PageSweepCheck.changed(base,1,0,0,false);java.awt.Graphics2D g=dim.image.createGraphics();g.setColor(new java.awt.Color(0,0,0,160));g.fillRect(0,0,dim.getWidth(),dim.getHeight());g.dispose();
            Frame hidden=vision.observe(dim,Mode.GIANT);require(hidden.page!=Page.MAP,"Dimmed dialog backdrop accepted as actionable map");checks++;
            ImageIO.write(dim.image,"png",output.resolve("dimmed.png").toFile());
            base.recycle();covered.recycle();dim.recycle();
        }
        System.out.printf(Locale.ROOT,"FULL LOOP PASS: %d image checks; both modes start/resume; max complete recorded-frame latency %.1fms; annotated count and fallback clock OCR, no gestures%n",checks,worst);
    }
}
