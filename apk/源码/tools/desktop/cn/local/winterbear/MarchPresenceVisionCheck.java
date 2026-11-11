package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import cn.local.automation.VisualAnchor;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.io.PrintWriter;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Masks only the real roster heading; visible rows must not become a zero roster. */
public final class MarchPresenceVisionCheck {
    private static Mat normalized(Bitmap bitmap){
        Mat gray=PageSweepCheck.mat(bitmap,false),small=new Mat();
        try{Imgproc.resize(gray,small,new Size(540,Math.round(bitmap.getHeight()*540./bitmap.getWidth())),0,0,Imgproc.INTER_AREA);return small;}
        finally{gray.release();}
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);
        Path assets=Path.of(args[1]),fixtures=Path.of(args[2]),output=Path.of(args[3]);Files.createDirectories(output);
        Method hit=HuntVision.class.getDeclaredMethod("hit",Mat.class,String.class,int.class,int.class,int.class,int.class,double.class);hit.setAccessible(true);
        int[] reads={0};int checks=0;
        try(HuntVision vision=new HuntVision(path->Files.newInputStream(assets.resolve(path)),(bitmap,region)->{reads[0]++;return "";});
            PrintWriter report=new PrintWriter(Files.newBufferedWriter(output.resolve("march-presence.tsv")))){
            report.println("case\tabsent\tused\trows\tocr_calls");
            Bitmap base=BitmapFactory.decodeFile(fixtures.resolve("xiaomi-rally.jpg").toString());
            try{
                for(double scale:new double[]{1,.99,1.01}){
                    int dx=scale>1?(int)Math.ceil(base.getWidth()*(scale-1)/2):0;
                    Bitmap image=PageSweepCheck.changed(base,scale,dx,0,false);Mat gray=normalized(image);
                    try{
                        Point anchor=(Point)hit.invoke(vision,gray,"march",0,100,180,gray.rows()/2,.72);
                        if(anchor==null)throw new AssertionError("Original heading not detected at "+scale);
                        Rect area=VisualAnchor.near(anchor,-45,-18,55,18);
                        java.awt.Graphics2D graphics=image.image.createGraphics();graphics.setColor(new java.awt.Color(45,65,95));
                        graphics.fillRect(area.x*2,area.y*2,area.width*2,area.height*2);graphics.dispose();
                        gray.release();gray=normalized(image);
                        HuntFlow.Frame frame=new HuntFlow.Frame();reads[0]=0;vision.observeMarchPanel(image,2,gray,frame);
                        String name="covered-heading-"+(int)Math.round(scale*100);
                        if(!frame.marchPanelAbsent||frame.used!=-1||frame.marches!=null||reads[0]!=0)
                            throw new AssertionError(name+" absent="+frame.marchPanelAbsent+" used="+frame.used+" OCR="+reads[0]);
                        javax.imageio.ImageIO.write(image.image,"png",output.resolve(name+".png").toFile());
                        report.println(name+"\ttrue\t-1\tnull\t0");report.flush();checks++;
                    }finally{gray.release();image.recycle();}
                }
            }finally{base.recycle();}
        }
        System.out.println("MARCH PRESENCE VISION PASS "+checks);
    }
}
