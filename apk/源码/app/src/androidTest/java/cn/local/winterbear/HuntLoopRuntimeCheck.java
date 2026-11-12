package cn.local.winterbear;

import android.graphics.*;
import java.io.*;
import static cn.local.winterbear.HuntFlow.*;

/** Same recording and occlusion fixtures through the actual APK Bitmap/OpenCV pipeline. */
public final class HuntLoopRuntimeCheck {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){try{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");int checks=0;
        final String[] clock={"04:38:14"};
        try(HuntVision vision=new HuntVision(p->new FileInputStream(new File(args[2],p)),(b,r)->b.getWidth()==180&&b.getHeight()==38?clock[0]:"5/6")){
            for(String name:new String[]{"map-2","map-8","zoom-0.98","zoom-1.02","covered-row","dimmed"}){
                clock[0]=name.equals("map-2")?"04:38:20":"04:38:14";
                Bitmap bitmap=BitmapFactory.decodeFile(new File(args[1],name+".png").getPath());require(bitmap!=null,"Missing "+name);
                long started=System.nanoTime();Frame f;
                try{f=vision.observe(bitmap,Mode.GIANT);}finally{bitmap.recycle();}
                if(name.equals("dimmed"))require(f.page!=Page.MAP,"Dimmed background classified as map");
                else{
                    require(f.page==Page.MAP&&f.used==5,"Full map recognition failed "+name);
                    if(name.equals("covered-row"))require(f.marches==null,"Hidden row was accepted");
                    else{
                        require(f.marches!=null&&f.marches.length==5,"Recording row recovery failed "+name);
                        for(Mode mode:Mode.values()){
                            HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,100));flow.start(0);flow.cansUsed=98;
                            flow.next(f,1000);require(flow.next(f,2000).kind==Kind.OPEN,"Could not start "+mode);
                            flow.suspend(2100,false);flow.resume(3000);
                            require(flow.next(f,4000).kind==Kind.OPEN&&flow.cansUsed==98,"Could not resume "+mode);
                        }
                    }
                }
                checks++;System.out.println("FULL PIPELINE IMAGE PASS: "+name+" page="+f.page+" rows="+(f.marches==null?"unknown":f.marches.length)+" emulator_ms="+(System.nanoTime()-started)/1e6);System.out.flush();
            }
        }
        System.out.println("ANDROID FULL LOOP PASS: "+checks+" images; production APK classes; count/fallback clock OCR annotated; no gestures or physical-phone timing claim");
    }catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
}
