package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import static cn.local.winterbear.HuntFlow.*;

/** Only resamples the label interior; button edges and the rest of the page stay byte-identical. */
public final class LocalTextScaleCheck {
    record Case(String name,int x,int y,int w,int h,boolean bear){}
    static final Case[] cases={
        // Manually inspected padded text boxes in the original screenshot pixels.
        new Case("giant_search",482,2268,120,70,false),
        new Case("huawei-formation",828,2213,108,54,false),
        new Case("video-170",772,2270,122,70,true)
    };
    static Bitmap change(Bitmap base,Case c,double scale){
        BufferedImage out=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_RGB);
        var g=out.createGraphics();g.drawImage(base.image,0,0,null);g.dispose();
        BufferedImage patch=base.image.getSubimage(c.x,c.y,c.w,c.h);
        // Fill exposed shrink margins using the blue background at each row's ends.
        // All glyph strokes are well inside these manually inspected margins.
        for(int y=0;y<c.h;y++)for(int x=0;x<c.w;x++){
            int left=patch.getRGB(0,y),right=patch.getRGB(c.w-1,y),rgb=0xff000000;
            double t=x/(double)(c.w-1);
            for(int shift:new int[]{0,8,16})rgb|=(int)Math.round(((left>>shift)&255)*(1-t)+((right>>shift)&255)*t)<<shift;
            out.setRGB(c.x+x,c.y+y,rgb);
        }
        g=out.createGraphics();g.setClip(c.x,c.y,c.w,c.h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(patch,new java.awt.geom.AffineTransform(scale,0,0,scale,c.x+c.w*(1-scale)/2,c.y+c.h*(1-scale)/2),null);g.dispose();
        return new Bitmap(out);
    }
    static void unchangedOutside(Bitmap base,Bitmap image,Case c){
        int changed=0;
        for(int y=0;y<base.getHeight();y++)for(int x=0;x<base.getWidth();x++)if(base.getPixel(x,y)!=image.getPixel(x,y)){
            if(x<c.x||y<c.y||x>=c.x+c.w||y>=c.y+c.h)throw new AssertionError("Modified outside label interior");
            changed++;
        }
        if(changed==0)throw new AssertionError("No transformed pixels");
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);
        Path assets=Path.of(args[1]),fixtures=Path.of(args[2]),output=Path.of(args[3]),layout=Path.of(args[4]);
        Files.createDirectories(output);int checks=0,failures=0;
        try(var report=new java.io.PrintWriter(Files.newBufferedWriter(output.resolve("local-text.tsv")))){
            report.println("case\tscale\tresult\tmax_error\tdetail");
            for(Case c:cases){
                Bitmap base=BitmapFactory.decodeFile(fixtures.resolve(c.name+".png").toString());
                int[] reference;Object baseline;
                if(c.bear){try(Matcher m=new Matcher(new Config(assets,layout,base.getWidth(),base.getHeight()),new CounterOcr())){m.frame(base);BearFlow.Frame f=m.observe();if(!f.formation||!f.deploy)throw new AssertionError("Invalid bear baseline");baseline=f;reference=new int[]{f.deployX,f.deployY};}}
                else{try(HuntVision v=new HuntVision(p->Files.newInputStream(assets.resolve(p)),(b,r)->"")){Frame f=v.observe(base,Mode.GIANT);Page expected=c.name.equals("giant_search")?Page.SEARCH:Page.FORMATION;Kind k=expected==Page.SEARCH?Kind.SEARCH:Kind.DEPLOY;if(f.page!=expected||f.points[k.ordinal()][0]<=0)throw new AssertionError("Invalid hunt baseline");baseline=f;reference=f.points[k.ordinal()];}}
                for(double scale:new double[]{.98,1.02}){
                    Bitmap image=change(base,c,scale);unchangedOutside(base,image,c);boolean ok;double error;String detail;
                    if(c.bear){try(Matcher m=new Matcher(new Config(assets,layout,image.getWidth(),image.getHeight()),new CounterOcr())){m.frame(image);BearFlow.Frame f=m.observe();error=Math.hypot(f.deployX-reference[0],f.deployY-reference[1]);ok=f.formation&&f.deploy&&error<=12;detail="formation="+f.formation+" deploy="+f.deploy;}}
                    else{try(HuntVision v=new HuntVision(p->Files.newInputStream(assets.resolve(p)),(b,r)->"")){Frame f=v.observe(image,Mode.GIANT);Frame before=(Frame)baseline;Kind k=c.name.equals("giant_search")?Kind.SEARCH:Kind.DEPLOY;int[] point=f.points[k.ordinal()];error=Math.hypot(point[0]-reference[0],point[1]-reference[1]);ok=f.page==before.page&&error<=12;detail="page="+f.page;if(k==Kind.SEARCH)ok&=f.category==Mode.GIANT.ordinal();}}
                    checks++;if(!ok)failures++;
                    String name=c.name+"-"+scale;ImageIO.write(image.image,"png",output.resolve(name+".png").toFile());
                    report.printf(Locale.ROOT,"%s\t%.2f\t%s\t%.3f\t%s%n",c.name,scale,ok?"PASS":"FAIL",error,detail);report.flush();
                    System.out.printf(Locale.ROOT,"%s %s error=%.3f %s%n",ok?"PASS":"FAIL",name,error,detail);
                }
            }
        }
        System.out.println("LOCAL TEXT SCALE: "+checks+" checks, "+failures+" failures; only label interiors changed; no OCR/gestures");if(failures>0)System.exit(1);
    }
}
