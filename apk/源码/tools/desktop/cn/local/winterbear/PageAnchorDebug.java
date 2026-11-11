package cn.local.winterbear;
import android.graphics.*;
import java.nio.file.*;
import java.util.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import cn.local.automation.*;
/** Diagnostic entry point for a failed full-page fixture. */
public final class PageAnchorDebug {
 @SuppressWarnings("unchecked")
 public static void main(String[] args)throws Exception{
  System.load(args[0]);Core.setNumThreads(1);
  try(HuntVision v=new HuntVision(p->Files.newInputStream(Path.of(args[1]).resolve(p)),(b,r)->"")){
   var field=HuntVision.class.getDeclaredField("templates");field.setAccessible(true);
   Map<String,Mat> templates=(Map<String,Mat>)field.get(v);
   for(int n=2;n<args.length;n++){
    Bitmap b=BitmapFactory.decodeFile(args[n]);Mat g=PageSweepCheck.mat(b,false),small=new Mat();Imgproc.resize(g,small,new Size(540,Math.round(b.getHeight()*540./b.getWidth())),0,0,Imgproc.INTER_AREA);g.release();
    System.out.println(args[n]);
    for(String key:new String[]{"rally_title","rally_start","rally_action","level_label","formation","deploy"}){
     var matches=VisualLocator.findAdaptive(small,templates.get(key),new org.opencv.core.Rect(0,0,small.cols(),small.rows()),.88,4,true);
     for(var m:matches)System.out.printf(Locale.ROOT,"%s x=%.1f y=%.1f scale=%.3f score=%.4f%n",key,m.x,m.y,m.width/(double)templates.get(key).cols(),m.confidence);
    }small.release();
   }
  }
 }
}
