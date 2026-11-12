package cn.local.winterbear;

import android.graphics.*;
import java.nio.file.*;
import java.util.*;
import org.opencv.core.Core;
import static cn.local.winterbear.HuntFlow.*;

/** Private screenshots + independent annotations; OCR is a crop oracle, not a model test. */
public final class SearchLayoutCheck {
    static int checks,failures;
    static Properties expected;
    static double[] numbers(String value){return Arrays.stream(value.split(",")).mapToDouble(Double::parseDouble).toArray();}
    static String read(Bitmap b,Rect r){
        if(expected.getProperty("digits")==null)return "";
        double[] p=numbers(expected.getProperty("digits"));
        return r.left<=p[0]+2&&r.top<=p[1]+2&&r.right>=p[2]-2&&r.bottom>=p[3]-2?expected.getProperty("level"):"";
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);
        try(HuntVision vision=new HuntVision(p->Files.newInputStream(Path.of(args[1]).resolve(p)),SearchLayoutCheck::read);
            var report=new java.io.PrintWriter(Files.newBufferedWriter(Path.of(args[3])))){
            for(Path annotation:Files.list(Path.of(args[2])).filter(p->p.toString().endsWith(".properties")).sorted().toList()){
                expected=new Properties();try(var input=Files.newInputStream(annotation)){expected.load(input);}
                Bitmap bitmap=BitmapFactory.decodeFile(annotation.toString().replace(".properties",".png"));
                for(Mode mode:Mode.values()){
                    long start=System.nanoTime();Frame f=vision.observe(bitmap,mode);double elapsed=(System.nanoTime()-start)/1e6;
                    boolean ok=f.page.name().equals(expected.getProperty("page","SEARCH"));
                    double error=0;String action="none";
                    if(f.page==Page.SEARCH){
                        int category=Integer.parseInt(expected.getProperty("category"));
                        ok&=f.category==category;
                        String destination=expected.getProperty(mode==Mode.GIANT?"giant":"beast");
                        int[] actual=f.points[Kind.CATEGORY.ordinal()];
                        if(destination==null)ok&=actual[0]==0&&actual[1]==0;
                        else{double[] p=numbers(destination);error=Math.hypot(actual[0]-p[0],actual[1]-p[1]);ok&=error<=8*bitmap.getWidth()/540.;}
                        if(category==mode.ordinal())ok&=f.level==Integer.parseInt(expected.getProperty("level"));
                        HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,0));flow.start(0);flow.phase=Phase.SEARCH;
                        Action next=flow.next(f,1000);action=next.kind.name();
                        Kind wanted=destination==null?Kind.WAIT:category==mode.ordinal()?Kind.SEARCH:Kind.CATEGORY;
                        ok&=next.kind==wanted;
                    }
                    checks++;if(!ok)failures++;
                    String line=String.format(Locale.ROOT,"%s %s %s page=%s category=%d level=%d action=%s error=%.2f ms=%.1f",ok?"PASS":"FAIL",annotation.getFileName(),mode,f.page,f.category,f.level,action,error,elapsed);
                    report.println(line);report.flush();System.out.println(line);
                }
                bitmap.recycle();
            }
        }
        System.out.println("SEARCH LAYOUT: "+checks+" checks, "+failures+" failures; annotated OCR only; no phone timing claim");
        if(failures>0)System.exit(1);
    }
}
