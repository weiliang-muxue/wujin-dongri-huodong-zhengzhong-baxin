package cn.local.automation;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Runs the production locator, without an Android shim or a rewritten matcher. */
public final class ScaleSweepCheck {
    static PrintWriter report;
    static int checks,failed;
    static double largestError;
    static Mat read(Path file,boolean color)throws Exception{
        BufferedImage image=ImageIO.read(file.toFile());
        if(image==null)throw new IOException("Unreadable image: "+file);
        byte[] bytes=new byte[image.getWidth()*image.getHeight()*3];int index=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int rgb=image.getRGB(x,y);bytes[index++]=(byte)(rgb>>16);bytes[index++]=(byte)(rgb>>8);bytes[index++]=(byte)rgb;
        }
        Mat rgb=new Mat(image.getHeight(),image.getWidth(),CvType.CV_8UC3);rgb.put(0,0,bytes);
        if(color)return rgb;
        Mat gray=new Mat();Imgproc.cvtColor(rgb,gray,Imgproc.COLOR_RGB2GRAY);rgb.release();return gray;
    }
    static void result(String group,String name,boolean ok,double error,String detail){
        checks++;if(!ok)failed++;if(Double.isFinite(error))largestError=Math.max(largestError,error);
        report.printf(Locale.ROOT,"%s\t%s\t%s\t%.4f\t%s%n",group,name,ok?"PASS":"FAIL",error,detail);report.flush();
        if(!ok)System.out.println("FAIL "+group+" "+name+" "+detail+" error="+error);
    }
    static Rect paste(Mat frame,Mat template,int width,int x,int y,int interpolation){
        int height=Math.max(1,(int)Math.round(width*template.rows()/(double)template.cols()));
        Mat resized=new Mat();Imgproc.resize(template,resized,new Size(width,height),0,0,interpolation);
        Rect box=new Rect(x,y,width,height);Mat view=frame.submat(box);resized.copyTo(view);view.release();resized.release();return box;
    }
    static void expected(String group,String name,Mat frame,Mat template,Rect box){
        long start=System.nanoTime();
        List<VisualLocator.Match> matches=VisualLocator.findAdaptive(frame,template,new Rect(0,0,frame.cols(),frame.rows()),.88,2,true);
        VisualLocator.Match match=matches.size()==1?matches.get(0):null;
        double error=match==null?Double.NaN:Math.hypot(match.x-box.x-box.width/2.,match.y-box.y-box.height/2.);
        result(group,name,match!=null&&error<=3,error,"count="+matches.size()+" score="+(match==null?0:match.confidence)+" ms="+(System.nanoTime()-start)/1000000);
    }
    static void dense(Path assets)throws Exception{
        String[] keys={"hunt/search","hunt/rally_start","hunt/deploy","hunt/attack_action","hunt/flag0","hunt/level_label","feedback/green_plus","feedback/preset0","reference/bear_label","reference/header","reference/back"};
        for(String key:keys){
            Mat template=read(assets.resolve(key+".png"),false);
            SortedSet<Integer> widths=new TreeSet<>();
            for(int width=(int)Math.floor(template.cols()*.80);width<=Math.ceil(template.cols()*1.20);width++)widths.add(width);
            for(double scale:new double[]{.67,.73,1.27,1.33,1.46})widths.add((int)Math.round(template.cols()*scale));
            for(int width:widths)for(int interpolation:new int[]{Imgproc.INTER_AREA,Imgproc.INTER_LINEAR}){
                Mat frame=new Mat(280,580,CvType.CV_8UC1,new Scalar(35));
                try{
                    int x=61+(width*17%67),y=43+(width*13%41);
                    Rect box=paste(frame,template,width,x,y,interpolation);
                    expected("pixel-sweep",key+" width="+width+" interpolation="+interpolation,frame,template,box);
                }finally{frame.release();}
            }
            template.release();System.out.println("DONE pixel-sweep "+key+" checks="+checks+" failures="+failed);
        }
    }
    static void boundary(Path assets)throws Exception{
        for(String key:new String[]{"hunt/search","feedback/green_plus","reference/bear_label"}){
            Mat template=read(assets.resolve(key+".png"),false);
            for(double scale:new double[]{.83,.98,1.02,1.17})for(int corner=0;corner<4;corner++){
                int w=(int)Math.round(template.cols()*scale),h=(int)Math.round(w*template.rows()/(double)template.cols());
                Mat frame=new Mat(320,580,CvType.CV_8UC1,new Scalar(35));
                try{Rect box=paste(frame,template,w,(corner%2==0)?0:580-w,corner<2?0:320-h,Imgproc.INTER_LINEAR);
                    expected("edge-visible",key+" scale="+scale+" corner="+corner,frame,template,box);
                }finally{frame.release();}
            }
            for(int side=0;side<4;side++){
                Mat frame=new Mat(320,580,CvType.CV_8UC1,new Scalar(35));
                Rect box=paste(frame,template,template.cols(),150,110,Imgproc.INTER_LINEAR);
                Rect cover=side<2?new Rect(box.x+(side==1?box.width/2:0),box.y,box.width/2,box.height):new Rect(box.x,box.y+(side==3?box.height/2:0),box.width,box.height/2);
                Mat view=frame.submat(cover);view.setTo(new Scalar(35));view.release();
                List<VisualLocator.Match> found=VisualLocator.findAdaptive(frame,template,new Rect(0,0,580,320),.88,2,true);
                result("half-occluded",key+" side="+side,found.isEmpty(),0,"count="+found.size());frame.release();
            }
            template.release();
        }
    }
    static void actualPages(Path assets,Path fixtures)throws Exception{
        for(String[] item:new String[][]{{"hunt/search","giant_search"},{"hunt/deploy","giant_formation"},{"hunt/attack_action","beast_detail"},{"hunt/deploy","beast_formation"},{"feedback/green_plus","huawei-bear-list"}}){
            Mat template=read(assets.resolve(item[0]+".png"),false),full=read(fixtures.resolve(item[1]+".png"),false),base=new Mat();
            // feedback assets are 270px-wide references; hunt assets are 540px.
            double normalizedWidth=item[0].startsWith("feedback/")?270:540;
            Imgproc.resize(full,base,new Size(normalizedWidth,Math.round(full.rows()*normalizedWidth/full.cols())),0,0,Imgproc.INTER_AREA);full.release();
            List<VisualLocator.Match> original=VisualLocator.findAdaptive(base,template,new Rect(0,0,base.cols(),base.rows()),.88,4,true);
            result("actual-baseline",item[1],!original.isEmpty(),0,"count="+original.size());
            if(!original.isEmpty())for(double scale:new double[]{.93,.98,1.02,1.07}){
                Mat changed=new Mat();Imgproc.resize(base,changed,new Size(),scale,scale,Imgproc.INTER_LINEAR);
                Mat frame=new Mat(changed.rows()+75,changed.cols()+63,CvType.CV_8UC1,new Scalar(0));
                Mat target=frame.submat(new Rect(23,31,changed.cols(),changed.rows()));changed.copyTo(target);target.release();
                List<VisualLocator.Match> found=VisualLocator.findAdaptive(frame,template,new Rect(0,0,frame.cols(),frame.rows()),.88,4,true);
                double max=0;boolean ok=found.size()==original.size();
                for(VisualLocator.Match before:original){double error=Double.POSITIVE_INFINITY;
                    for(VisualLocator.Match after:found)error=Math.min(error,Math.hypot(after.x-(before.x*changed.cols()/base.cols()+23),after.y-(before.y*changed.rows()/base.rows()+31)));
                    max=Math.max(max,error);ok&=error<=4;
                }
                result("actual-scaled-shifted",item[1]+" scale="+scale,ok,max,"before="+original.size()+" after="+found.size());frame.release();changed.release();
            }
            base.release();template.release();System.out.println("DONE actual-page "+item[1]);
        }
    }
    static void colors(Path assets)throws Exception{
        Mat reference=read(assets.resolve("feedback/green_plus.png"),true),shape=new Mat(),inactive=new Mat();
        Imgproc.cvtColor(reference,shape,Imgproc.COLOR_RGB2GRAY);Imgproc.cvtColor(shape,inactive,Imgproc.COLOR_GRAY2RGB);
        try{for(int width=20;width<=42;width++){
            Mat frame=new Mat(200,320,CvType.CV_8UC3,new Scalar(35,35,35)),gray=new Mat();
            try{
                paste(frame,inactive,width,25,50,Imgproc.INTER_LINEAR);Rect box=paste(frame,reference,width,210,120,Imgproc.INTER_LINEAR);
                Imgproc.cvtColor(frame,gray,Imgproc.COLOR_RGB2GRAY);
                List<VisualLocator.Match> found=VisualLocator.findAdaptive(gray,shape,new Rect(0,0,320,200),.88,2,true,m->VisualLocator.sameColor(frame,reference,m,35));
                double error=found.size()==1?Math.hypot(found.get(0).x-box.x-box.width/2.,found.get(0).y-box.y-box.height/2.):Double.NaN;
                result("button-color","width="+width,found.size()==1&&error<=3,error,"count="+found.size());
                Mat view=frame.submat(box);view.setTo(new Scalar(35,35,35));view.release();Imgproc.cvtColor(frame,gray,Imgproc.COLOR_RGB2GRAY);
                found=VisualLocator.findAdaptive(gray,shape,new Rect(0,0,320,200),.88,2,true,m->VisualLocator.sameColor(frame,reference,m,35));
                result("button-color-negative","width="+width,found.isEmpty(),0,"count="+found.size());
            }finally{frame.release();gray.release();}
        }}finally{reference.release();shape.release();inactive.release();}
    }
    static void anchors(Path fixtures)throws Exception{
        Mat first=read(fixtures.resolve("bullseye-home.png"),false),second=read(fixtures.resolve("bullseye-board.png"),false),home=new Mat(),board=new Mat();
        Imgproc.resize(first,home,new Size(540,Math.round(first.rows()*540./first.cols())),0,0,Imgproc.INTER_AREA);
        Imgproc.resize(second,board,home.size(),0,0,Imgproc.INTER_AREA);first.release();second.release();
        // Same header band and excluded ticket count as the saved 1080x2400 activity.
        try(ReferenceAnchors anchors=new ReferenceAnchors(home,board,new Rect(0,160,540,220),Collections.singletonList(new Rect(437,325,44,33)))){
            for(int percent=92;percent<=108;percent++)for(int padding:new int[]{0,110}){
                double scale=percent/100.;Mat changed=new Mat();Imgproc.resize(home,changed,new Size(),scale,scale,Imgproc.INTER_LINEAR);
                Mat frame=new Mat(changed.rows()+padding+50,changed.cols()+40,CvType.CV_8UC1,new Scalar(0));
                try{
                    Mat target=frame.submat(new Rect(17,23+padding,changed.cols(),changed.rows()));changed.copyTo(target);target.release();
                    LayoutTransform t=anchors.locate(frame);double error=Double.NaN;
                    if(t!=null){error=0;for(double[] p:new double[][]{{50,330},{460,330},{100,800},{440,1100}})
                        error=Math.max(error,Math.hypot(t.x(p[0])-(p[0]*changed.cols()/home.cols()+17),t.y(p[1])-(p[1]*changed.rows()/home.rows()+23+padding)));}
                    result("bullseye-anchors","scale="+scale+" padding="+padding,t!=null&&error<=4,error,"scale="+(t==null?0:t.scale));
                }finally{frame.release();changed.release();}
            }
            Mat blank=new Mat(1200,540,CvType.CV_8UC1,new Scalar(0));
            try{result("bullseye-negative","blank",anchors.locate(blank)==null,0,"");}finally{blank.release();}
        }finally{home.release();board.release();}
    }
    public static void main(String[] args)throws Exception{
        System.load(Path.of(args[0]).toAbsolutePath().toString());Core.setNumThreads(1);
        Path assets=Path.of(args[1]),fixtures=Path.of(args[2]);
        try(PrintWriter output=new PrintWriter(Files.newBufferedWriter(Path.of(args[3])))){
            report=output;report.println("group\tcase\tresult\terror_px\tdetail");
            System.out.println("Production Java locator; OpenCV="+Core.VERSION+"; synthetic transformations are not phone tests");
            String part=args.length>4?args[4]:"all";
            if(part.equals("all")||part.equals("dense"))dense(assets);
            if(part.equals("all")||part.equals("boundary"))boundary(assets);
            if(part.equals("all")||part.equals("colors"))colors(assets);
            if(part.equals("all")||part.equals("pages"))actualPages(assets,fixtures);
            if(part.equals("all")||part.equals("anchors"))anchors(fixtures);
            System.out.println("SCALE SWEEP: checks="+checks+" failures="+failed+" largest_error="+largestError);
        }
        if(failed>0)System.exit(1);
    }
}
