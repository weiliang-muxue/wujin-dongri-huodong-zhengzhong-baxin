package cn.local.winterbear;
import android.graphics.*;
import java.nio.file.*;
import java.util.*;
import org.opencv.core.*;
public final class BearAnchorDebug {
 public static void main(String[] args)throws Exception{
  System.load(args[0]);Core.setNumThreads(1);
  for(int n=3;n<args.length;n++){
   Bitmap b=BitmapFactory.decodeFile(args[n]);
   try(Matcher m=new Matcher(new Config(Path.of(args[1]),Path.of(args[2]),b.getWidth(),b.getHeight()),new CounterOcr())){
    m.frame(b);var f=m.observe();System.out.println(args[n]+" presets="+Arrays.toString(f.savedPreset)+" x="+Arrays.toString(f.presetX)+" y="+Arrays.toString(f.presetY));
    for(var row:f.rows)System.out.println("row "+row.x+","+row.y+" available="+row.available+" seconds="+row.secondsLeft);
    for(String key:new String[]{"formation","bear_label","bear_reference","join_reference","preset_marker0","preset_marker1"})
     for(var h:m.find(key,null,8))System.out.println(key+" "+h.x+","+h.y+" "+h.width+"x"+h.height+" "+h.confidence);
   }
  }
 }
}
