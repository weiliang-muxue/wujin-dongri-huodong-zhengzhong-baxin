package cn.local.winterbear;

import android.graphics.*;
import java.nio.file.*;

/** Actual counter pixels through both map pipelines. No supplied OCR answers. */
public final class PixelCounterReplayCheck {
    public static void main(String[] a)throws Exception{
        System.load(a[0]);org.opencv.core.Core.setNumThreads(1);
        Path assets=Path.of(a[1]),layout=Path.of(a[2]);int checks=0,failures=0;
        int[] expected={3,6,1,4};
        for(int n=0;n<4;n++){
            Bitmap source=BitmapFactory.decodeFile(a[3+n]);
            for(int variant=0;variant<9;variant++){
                double zoom=variant<5?new double[]{.9,.98,1,1.02,1.1}[variant]:1;
                int dx=zoom>1?(int)Math.ceil(source.getWidth()*(zoom-1)/2):0;
                int width=variant<5?source.getWidth():new int[]{720,1080,1152,1344}[variant-5];
                Bitmap image=variant<5?PageSweepCheck.changed(source,zoom,dx,0,false):Bitmap.createScaledBitmap(source,width,(int)Math.round(source.getHeight()*width/(double)source.getWidth()),true);
                int bear=-1,hunt=-1;boolean map=false;
                Rect[] region={null};CounterOcr count=new CounterOcr(p->Files.newInputStream(assets.resolve(p)));
                Matcher.Numbers traced=new Matcher.Numbers(){public int read(Bitmap b,Rect r){region[0]=r;return count.read(b,r);}public void close(){count.close();}};
                try(Matcher matcher=new Matcher(new Config(assets,layout,image.getWidth(),image.getHeight()),traced)){matcher.frame(image);BearFlow.Frame f=matcher.observe();bear=f.marchUsed;map=f.map;}
                try(CounterOcr numbers=new CounterOcr(p->Files.newInputStream(assets.resolve(p)));HuntVision vision=new HuntVision(p->Files.newInputStream(assets.resolve(p)),numbers::text)){
                    HuntFlow.Frame f=vision.observe(image,HuntFlow.Mode.GIANT);hunt=f.used;
                }
                boolean ok=map&&bear==expected[n]&&hunt==expected[n];checks++;if(!ok)failures++;
                System.out.println((ok?"PASS ":"FAIL ")+Path.of(a[3+n]).getFileName()+" content="+zoom+" width="+width+" expected="+expected[n]+" bear="+bear+" hunt="+hunt);
                if(!ok&&region[0]!=null){Rect r=region[0];Bitmap crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height());
                    for(int height:new int[]{64,96,128}){Bitmap enlarged=Bitmap.createScaledBitmap(crop,Math.round(crop.getWidth()*height/(float)crop.getHeight()),height,true);System.out.println("  diagnostic height="+height+" pixel="+count.read(enlarged,new Rect(0,0,enlarged.getWidth(),height)));}
                    if(a.length>7)javax.imageio.ImageIO.write(crop.image,"png",Path.of(a[7],"failed-"+n+"-"+variant+".png").toFile());
                }
                if(image!=source)image.recycle();
            }source.recycle();
        }
        System.out.println("ACTUAL PIXEL COUNTER REPLAY: checks="+checks+" failures="+failures+"; two production map pipelines; no ML Kit or supplied OCR values");
        if(failures>0)System.exit(1);
    }
}
