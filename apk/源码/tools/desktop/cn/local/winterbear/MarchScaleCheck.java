package cn.local.winterbear;
import android.graphics.*;
import java.nio.file.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
/** Real return-list pixels; total counts annotated, all row types/timers read from pixels. */
public final class MarchScaleCheck {
 public static void main(String[] args)throws Exception{
  System.load(args[0]);Core.setNumThreads(1);int checks=0,failures=0;
  for(String name:new String[]{"xiaomi-rally","xiaomi-returning","xiaomi-returned"}){
   Bitmap base=BitmapFactory.decodeFile(Path.of(args[2],name+".jpg").toString());
   try(HuntVision v=new HuntVision(p->Files.newInputStream(Path.of(args[1]).resolve(p)),(b,r)->name.equals("xiaomi-rally")?"5/6":"4/6")){
    HuntMarches.Type[] expected=name.equals("xiaomi-rally")?new HuntMarches.Type[]{HuntMarches.Type.MEAT,HuntMarches.Type.RETURN,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.RALLY}:new HuntMarches.Type[]{HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,name.equals("xiaomi-returning")?HuntMarches.Type.RETURN:HuntMarches.Type.WOOD};
    for(double scale:PageSweepCheck.scales){
     // Keep the left-hand icons visible when enlarging; a clipped resource
     // icon cannot be expected to identify its row type.
     int dx=scale>1?(int)Math.ceil(base.getWidth()*(scale-1)/2):scale==.94?18:0;
     Bitmap image=PageSweepCheck.changed(base,scale,dx,scale==.94?-28:0,scale==.94);
     Mat g=PageSweepCheck.mat(image,false),small=new Mat();Imgproc.resize(g,small,new Size(540,Math.round(image.getHeight()*540./image.getWidth())),0,0,Imgproc.INTER_AREA);g.release();
     HuntFlow.Frame f=new HuntFlow.Frame();v.observeMarchPanel(image,image.getWidth()/540.,small,f);small.release();
     boolean ok=f.marches!=null&&expected.length==f.marches.length;
     if(ok)for(int i=0;i<expected.length;i++)ok&=expected[i]==f.marches[i].type;
     checks++;if(!ok)failures++;
     System.out.println((ok?"PASS ":"FAIL ")+name+" scale="+scale+" rows="+(f.marches==null?"null":java.util.Arrays.toString(java.util.Arrays.stream(f.marches).map(r->r.type).toArray())));
    }
   }
  }
  System.out.println("MARCH SCALE: checks="+checks+" failures="+failures);if(failures>0)System.exit(1);
 }
}
