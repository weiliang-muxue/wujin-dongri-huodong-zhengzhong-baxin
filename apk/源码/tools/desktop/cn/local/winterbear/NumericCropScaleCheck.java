package cn.local.winterbear;
import android.graphics.*;
import java.nio.file.*;
import static cn.local.winterbear.HuntFlow.*;
/** Checks OCR input geometry against annotated digit boxes, not OCR accuracy. */
public final class NumericCropScaleCheck {
 public static void main(String[] args)throws Exception{
  System.load(args[0]);org.opencv.core.Core.setNumThreads(1);int checks=0,failures=0;
  for(String name:new String[]{"giant_search","beast_search","energy"}){
   Bitmap base=BitmapFactory.decodeFile(Path.of(args[2],name+".png").toString());
   // Digit bounds annotated on the 540 x 1200 reference frame, excluding padding.
   int[][] boxes=name.equals("energy")?new int[][]{{264,377,326,393},{84,594,109,607}}:new int[][]{{447,1023,464,1039}};
   String[] values=name.equals("energy")?new String[]{"15/200","232"}:new String[]{name.startsWith("giant")?"8":"30"};
   for(double scale:PageSweepCheck.scales){
    int dx=scale==.94?18:0,dy=scale==.94?-28:0;
    Bitmap image=PageSweepCheck.changed(base,scale,dx,dy,scale==.94);boolean[] read=new boolean[boxes.length];
    HuntVision.Numbers ocr=(b,r)->{
     for(int i=0;i<boxes.length;i++){
      int[] box=boxes[i];double k=base.getWidth()/540.*scale,x=PageSweepCheck.tx(base,scale,dx),y=PageSweepCheck.ty(base,scale,dy);
      if(r.left<=box[0]*k+x+2&&r.top<=box[1]*k+y+2&&r.right>=box[2]*k+x-2&&r.bottom>=box[3]*k+y-2){read[i]=true;return values[i];}
     }return "";
    };
    try(HuntVision v=new HuntVision(p->Files.newInputStream(Path.of(args[1]).resolve(p)),ocr)){
     Frame f=v.observe(image,name.startsWith("beast")?Mode.BEAST:Mode.GIANT);
     boolean ok=name.equals("energy")?f.page==Page.ENERGY&&f.energy==15&&f.stock==232:f.page==Page.SEARCH&&f.level==Integer.parseInt(values[0]);
     for(boolean found:read)ok&=found;
     checks++;if(!ok)failures++;System.out.println((ok?"PASS ":"FAIL ")+name+" crop scale="+scale+" level="+f.level+" energy="+f.energy+" stock="+f.stock);
    }
   }
  }
  System.out.println("NUMERIC CROP SCALE: checks="+checks+" failures="+failures+"; annotated text, no OCR accuracy claim");if(failures>0)System.exit(1);
 }
}
