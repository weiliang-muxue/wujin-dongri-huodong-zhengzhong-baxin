package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.opencv.core.Core;

/** Real-frame manual crops exercise production Matcher pairing, with navigation isolated. */
public final class CustomSampleScaleCheck {
    private static void crop(Bitmap frame,Path directory,String key,int x,int y,int w,int h)throws Exception{
        ImageIO.write(frame.image.getSubimage(x,y,w,h),"png",directory.resolve(key+".png").toFile());
    }
    private static void point(Config config,String key,int x,int y,Bitmap base,double zoom,int dx,int dy){
        config.p.values.put(key+"X",(int)Math.round(x*zoom+PageSweepCheck.tx(base,zoom,dx)));
        config.p.values.put(key+"Y",(int)Math.round(y*zoom+PageSweepCheck.ty(base,zoom,dy)));
        config.p.values.put(key+"Scale",1);
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);
        Path assets=Path.of(args[1]),layout=Path.of(args[2]),fixtures=Path.of(args[3]),output=Path.of(args[4]);
        String[] names={"video-315","huawei-bear-list"};
        // Pixel coordinates were marked on the unmodified screenshots before running Matcher.
        int[][] bears={{120,740,165,185},{122,626,182,174}};
        int[][] headers={{140,279,190,48},{160,297,202,52}};
        int[][] tabs={{160,415,85,58},{170,431,88,57}};
        int[][][] joins={{{973,839},{973,1451},{973,2063}},{{1028,720}}};
        int checks=0,failures=0;
        for(int n=0;n<names.length;n++){
            Bitmap base=BitmapFactory.decodeFile(fixtures.resolve(names[n]+".png").toString());
            if(base==null)throw new IllegalArgumentException("Missing "+names[n]);
            Path samples=output.resolve(names[n]+"-samples");Files.createDirectories(samples);
            int[] b=bears[n],h=headers[n],tab=tabs[n];int radius=Math.max(14,base.getWidth()/45);
            crop(base,samples,"bear_row",b[0],b[1],b[2],b[3]);
            crop(base,samples,"join_row",joins[n][0][0]-radius,joins[n][0][1]-radius,2*radius,2*radius);
            crop(base,samples,"header",h[0],h[1],h[2],h[3]);crop(base,samples,"rally_tab",tab[0],tab[1],tab[2],tab[3]);
            System.setProperty("custom.samples",samples.toString());
            for(double zoom:new double[]{.9,1,1.1})for(int shifted:new int[]{0,1}){
                // The right edge remains visible; the second variant adds translation in both axes.
                int dx=(zoom>1?-28:0)+shifted*-12,dy=shifted*21;
                Bitmap image=PageSweepCheck.changed(base,zoom,dx,dy,false);
                Config config=new Config(assets,layout,image.getWidth(),image.getHeight());
                config.p.values.put("rallySampleReady",1);config.p.values.put("manualCalibration",1);
                config.p.values.put("bear_rowScale",1);config.p.values.put("join_rowScale",1);
                config.p.values.put("rowJoinDx",joins[n][0][0]-(b[0]+b[2]/2));
                config.p.values.put("rowJoinDy",joins[n][0][1]-(b[1]+b[3]/2));
                // Header/tab positions are supplied independently so this suite isolates
                // the manual row-to-button pairing, not manual navigation calibration.
                point(config,"header",h[0]+h[2]/2,h[1]+h[3]/2,base,zoom,dx,dy);
                point(config,"rally_tab",tab[0]+tab[2]/2,tab[1]+tab[3]/2,base,zoom,dx,dy);
                try(Matcher matcher=new Matcher(config,new CounterOcr())){
                    matcher.frame(image);BearFlow.Frame frame=matcher.observe();
                    long available=frame.rows.stream().filter(r->r.available).count();double error=0;
                    for(int[] expected:joins[n]){
                        double best=Double.POSITIVE_INFINITY;
                        for(BearFlow.Row row:frame.rows)if(row.available)best=Math.min(best,PageSweepCheck.error(new int[]{row.x,row.y},expected,base,zoom,dx,dy));
                        error=Math.max(error,best);
                    }
                    boolean ok=config.customRallyReady()&&frame.rallyList&&available==joins[n].length&&error<=12;
                    checks++;if(!ok){failures++;ImageIO.write(image.image,"png",output.resolve(names[n]+"-"+zoom+"-"+shifted+".png").toFile());}
                    System.out.printf(Locale.ROOT,"%s %s zoom=%.2f shift=%d list=%s rows=%d available=%d expected=%d max_error=%.2f%n",ok?"PASS":"FAIL",names[n],zoom,shifted,frame.rallyList,frame.rows.size(),available,joins[n].length,error);
                }finally{image.recycle();}
            }
            base.recycle();
        }
        System.out.println("CUSTOM SAMPLE SCALE: checks="+checks+" failures="+failures+"; production row pairing; navigation coordinates isolated; no OCR/gestures");
        if(failures>0)System.exit(1);
    }
}
