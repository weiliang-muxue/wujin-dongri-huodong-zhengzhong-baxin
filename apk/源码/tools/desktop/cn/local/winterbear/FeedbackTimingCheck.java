package cn.local.winterbear;

import android.graphics.*;
import java.nio.file.*;
import java.util.*;
import org.opencv.core.Core;

/** Real feedback frames, including the runtime overlay mask; OCR calls are counted, not simulated. */
public final class FeedbackTimingCheck {
 public static void main(String[] args)throws Exception {
  System.load(args[0]);Core.setNumThreads(1);
  Path assets=Path.of(args[1]),layout=Path.of(args[2]);
  List<String[]> cases=new ArrayList<>();Path folder=Path.of("");
  if(args[3].endsWith(".tsv")){
   Path manifest=Path.of(args[3]);folder=manifest.getParent();
   for(String line:Files.readAllLines(manifest))if(!line.isBlank()&&!line.startsWith("#"))cases.add(line.split("\t"));
  }else for(int i=3;i<args.length;i++)cases.add(new String[]{args[i]});
  int checks=0;
  for(String[] item:cases) {
   Path file=folder.resolve(item[0]);Bitmap image=BitmapFactory.decodeFile(file.toString());
   // Supplied screenshots have the expanded panel in the upper right. The
   // service masks it before matching; never let its own words become evidence.
   var g=image.image.createGraphics();g.setColor(java.awt.Color.BLACK);
   g.fillRect(420,180,image.getWidth()-420,item.length>4?Integer.parseInt(item[4])-180:305);g.dispose();
   int[] calls={0};Matcher.Numbers numbers=new Matcher.Numbers(){public int read(Bitmap b,Rect r){calls[0]++;return -1;}public void close(){}};
   try(Matcher matcher=new Matcher(new Config(assets,layout,image.getWidth(),image.getHeight()),numbers)){
    for(int pass=0;pass<2;pass++){
     long start=System.nanoTime();calls[0]=0;matcher.frame(image);BearFlow.Frame f=matcher.observe();
     System.out.printf(Locale.ROOT,"%s pass=%d ms=%.1f map=%s hidden=%s list=%s ocr=%d timers=%s%n",file.getFileName(),pass,(System.nanoTime()-start)/1e6,f.map,f.marchHidden,f.rallyList,calls[0],f.rows.stream().map(r->Integer.toString(r.secondsLeft)).toList());
     if(item.length>1&&Boolean.parseBoolean(System.getProperty("verifyFeedback","true"))){
      boolean list=item[1].equals("list"),hidden=Integer.parseInt(item[2])<0;
      if(list?!f.rallyList:!f.map||f.marchHidden!=hidden||calls[0]!=(hidden?0:1))throw new AssertionError("Page/OCR calls: "+file);
      if(list){String[] timers=item[3].split(",");
       if(f.rows.size()<timers.length)throw new AssertionError("Missing rows: "+file);
       for(int n=0;n<timers.length;n++)if(f.rows.get(n).secondsLeft!=Integer.parseInt(timers[n]))throw new AssertionError("Timer: "+file+" row="+n);
      }checks++;
     }
    }
   }image.recycle();
  }
  System.out.println("FEEDBACK CHECKS: "+checks+"; desktop adapter; OCR calls counted, not executed");
 }
}
