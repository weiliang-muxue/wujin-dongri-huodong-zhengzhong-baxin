package cn.local.winterbear;

import android.graphics.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.opencv.core.Core;

/** Production recognition on masked feedback screenshots; annotated numbers, no game input. */
public final class FeedbackLatencyRuntimeCheck {
 public static void main(String[] args)throws Exception {
  System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");Core.setNumThreads(1);
  File assets=new File(args[1]),work=new File(args[3]);
  int checks=0;
  for(String line:Files.readAllLines(java.nio.file.Paths.get(args[2],"cases.tsv"))){
   if(line.isBlank()||line.startsWith("#"))continue;
   String[] fields=line.split("\t");int count=Integer.parseInt(fields[2]);
   Bitmap source=BitmapFactory.decodeFile(new File(args[2],fields[0]).getAbsolutePath());
   CompatibilityRuntimeCheck.require(source!=null,"Missing "+fields[0]);
   Bitmap image=source.copy(Bitmap.Config.ARGB_8888,true);source.recycle();
   Paint paint=new Paint();paint.setColor(Color.BLACK);
   new Canvas(image).drawRect(420,180,image.getWidth(),Integer.parseInt(fields[4]),paint);
   int[] calls={0};Matcher.Numbers numbers=new Matcher.Numbers(){public int read(Bitmap b,Rect r){calls[0]++;return count;}public void close(){}};
   Config config=CompatibilityRuntimeCheck.config(assets,new File(work,fields[0]),image.getWidth(),image.getHeight());
   try(Matcher matcher=new Matcher(config,numbers)){
    for(int pass=0;pass<2;pass++){
     calls[0]=0;long start=System.nanoTime();matcher.frame(image);BearFlow.Frame f=matcher.observe();
     double elapsed=(System.nanoTime()-start)/1e6;
     boolean list=fields[1].equals("list");
     CompatibilityRuntimeCheck.require(list?f.rallyList:f.map,"Wrong page "+fields[0]);
     if(!list){
      CompatibilityRuntimeCheck.require(f.marchHidden==(count<0),"March presence "+fields[0]);
      CompatibilityRuntimeCheck.require(calls[0]==(count<0?0:1),"Unexpected OCR requests "+fields[0]);
     }else{
      String[] timers=fields[3].split(",");
      CompatibilityRuntimeCheck.require(f.rows.size()>=timers.length,"Rows missing "+fields[0]);
      for(int n=0;n<timers.length;n++)CompatibilityRuntimeCheck.require(f.rows.get(n).secondsLeft==Integer.parseInt(timers[n]),"Timer mismatch "+fields[0]+" row="+n);
     }
     System.out.printf(Locale.ROOT,"FEEDBACK pass=%d ms=%.1f page=%s ocr=%d file=%s%n",pass,elapsed,list?"list":"map",calls[0],fields[0]);System.out.flush();checks++;
    }
   }finally{image.recycle();}
  }
  System.out.println("ANDROID FEEDBACK LATENCY PASS: "+checks+"; actual Bitmap/OpenCV; numbers annotated; emulator timing is not phone timing");
 }
}
