package cn.local.winterbear;

import android.graphics.*;
import java.io.*;
import java.util.*;
import org.opencv.core.Core;
import static cn.local.winterbear.HuntFlow.*;

/** Dynamic category layouts through actual Android Bitmap and APK native code. */
public final class SearchLayoutRuntimeCheck {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static double[] coordinates(String value){
        String[] parts=value.split(",");double[] result=new double[parts.length];
        for(int i=0;i<parts.length;i++)result[i]=Double.parseDouble(parts[i]);return result;
    }
    public static void main(String[] args){try{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");Core.setNumThreads(1);int checks=0;
        final Properties[] annotation={null};
        try(HuntVision vision=new HuntVision(p->new FileInputStream(new File(args[2],p)),(b,r)->{
            double[] box=coordinates(annotation[0].getProperty("digits"));
            return r.left<=box[0]+2&&r.top<=box[1]+2&&r.right>=box[2]-2&&r.bottom>=box[3]-2?annotation[0].getProperty("level"):"";
        })){
            String[] cases={"event-present-1.0","event-present-0.98","event-removed-1.0","giant-first-1.02","event-selected","giant-selected","plus-enabled-1.0","minus-disabled-1.02"};
            Mode[] modes={Mode.GIANT,Mode.BEAST,Mode.GIANT,Mode.BEAST,Mode.GIANT,Mode.GIANT,Mode.BEAST,Mode.BEAST};
            for(int index=0;index<cases.length;index++){
                String name=cases[index];
                if(args.length>3&&!name.equals(args[3]))continue;
                Properties p=new Properties();try(var in=new FileInputStream(new File(args[1],name+".properties"))){p.load(in);}annotation[0]=p;
                Bitmap image=BitmapFactory.decodeFile(new File(args[1],name+".png").getPath());require(image!=null,"Missing "+name);
                try{
                    {
                        Mode mode=modes[index];
                        long started=System.nanoTime();Frame frame=vision.observe(image,mode);
                        int category=Integer.parseInt(p.getProperty("category"));
                        require(frame.page==Page.SEARCH&&frame.category==category,name+" page/selected="+frame.page+"/"+frame.category);
                        double[] point=coordinates(p.getProperty(mode==Mode.GIANT?"giant":"beast"));int[] actual=frame.points[Kind.CATEGORY.ordinal()];
                        require(Math.hypot(actual[0]-point[0],actual[1]-point[1])<=8*image.getWidth()/540.,name+" target="+mode);
                        HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,0));flow.start(0);flow.phase=Phase.SEARCH;
                        require(flow.next(frame,1000).kind==(category==mode.ordinal()?Kind.SEARCH:Kind.CATEGORY),name+" action="+mode);
                        checks++;System.out.println("ANDROID SEARCH PASS: "+name+" "+mode+" emulator_ms="+(System.nanoTime()-started)/1e6);System.out.flush();
                    }
                }finally{image.recycle();}
            }
        }
        System.out.println("ANDROID SEARCH LAYOUT PASS: "+checks+"; annotated OCR; no gestures or physical-phone timing claim");
    }catch(Throwable t){t.printStackTrace(System.out);System.exit(1);}}
}
