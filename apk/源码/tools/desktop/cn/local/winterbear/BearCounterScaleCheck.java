package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.nio.file.Path;
import org.opencv.core.Core;

/** Real map pixels; annotated number bounds test the production bear OCR crop, not ML Kit. */
public final class BearCounterScaleCheck {
    private static Rect transformed(Rect r,Bitmap base,double scale,int dx,int dy){
        double x=PageSweepCheck.tx(base,scale,dx);
        double y=PageSweepCheck.ty(base,scale,dy);
        return new Rect((int)Math.floor(r.left*scale+x),(int)Math.floor(r.top*scale+y),
                (int)Math.ceil(r.right*scale+x),(int)Math.ceil(r.bottom*scale+y));
    }
    private static boolean contains(Rect crop,Rect ink){
        return crop.left<=ink.left+2&&crop.top<=ink.top+2
                &&crop.right>=ink.right-2&&crop.bottom>=ink.bottom-2;
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);
        Path assets=Path.of(args[1]),layout=Path.of(args[2]);int checks=0,failures=0;
        String[] names={"huawei-map","xiaomi-returning"};
        Rect[] ink={new Rect(323,623,380,649),new Rect(304,594,357,617)};
        int[] count={6,4};
        for(int i=0;i<names.length;i++){
            Bitmap base=BitmapFactory.decodeFile(args[3+i]);final int expectedCount=count[i];
            if(base==null)throw new IllegalArgumentException("Missing fixture: "+args[3+i]);
            for(double zoom:new double[]{.9,.98,1,1.02,1.1}){
                int dx=zoom>1?(int)Math.ceil(base.getWidth()*(zoom-1)/2):0;
                Bitmap image=PageSweepCheck.changed(base,zoom,dx,0,false);
                Rect expectedInk=transformed(ink[i],base,zoom,dx,0);
                Rect[] observed={null};
                Matcher.Numbers annotated=new Matcher.Numbers(){
                    public int read(Bitmap b,Rect r){observed[0]=new Rect(r.left,r.top,r.right,r.bottom);return contains(r,expectedInk)?expectedCount:-1;}
                    public void close(){}
                };
                try(Matcher matcher=new Matcher(new Config(assets,layout,image.getWidth(),image.getHeight()),annotated)){
                    matcher.frame(image);BearFlow.Frame f=matcher.observe();
                    boolean ok=f.map&&!f.marchHidden&&f.marchUsed==expectedCount&&observed[0]!=null;
                    checks++;if(!ok)failures++;
                    Rect r=observed[0];
                    System.out.println((ok?"PASS ":"FAIL ")+names[i]+" scale="+zoom+" map="+f.map+" count="+f.marchUsed
                            +" crop="+(r==null?"none":r.left+","+r.top+","+r.right+","+r.bottom)
                            +" expected="+expectedInk.left+","+expectedInk.top+","+expectedInk.right+","+expectedInk.bottom);
                    if(!ok)for(String key:new String[]{"march_anchor","march_anchor_core"})for(Matcher.Hit hit:matcher.find(key,new Rect(0,0,image.getWidth()*45/100,image.getHeight()/2),1))
                        System.out.println("  "+key+" x="+hit.x+" y="+hit.y+" width="+hit.width+" height="+hit.height+" confidence="+hit.confidence);
                }finally{image.recycle();}
            }
            base.recycle();
        }
        System.out.println("BEAR COUNTER SCALE: checks="+checks+" failures="+failures+"; annotated OCR crop coverage only");
        if(failures>0)System.exit(1);
    }
}
