package cn.local.winterbear;
import android.graphics.*;
import java.io.*;
import java.util.*;
import org.opencv.core.Core;
/** Replays transformed page pixels with the real Android Bitmap, Config and recognizers. */
public final class PageScaleRuntimeCheck {
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
 static double error(Properties p,String key,int x,int y){String[] v=p.getProperty(key).split(",");return Math.hypot(x-Double.parseDouble(v[0]),y-Double.parseDouble(v[1]));}
 public static void main(String[] args){try{
  System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");Core.setNumThreads(1);int checks=0;
  File assets=new File(args[1]),fixtures=new File(args[2]),work=new File(args[3]);
  for(int n=4;n<args.length;n++){
   String name=args[n];Properties p=new Properties();try(var in=new FileInputStream(new File(fixtures,name+".properties"))){p.load(in);}
   Bitmap image=BitmapFactory.decodeFile(new File(fixtures,name+".png").getPath());require(image!=null,name+" fixture");
   try{
    if(p.getProperty("type").equals("hunt")){
     try(HuntVision vision=new HuntVision(path->new FileInputStream(new File(assets,path)),(b,r)->"")){
      HuntFlow.Frame f=vision.observe(image,name.startsWith("beast")?HuntFlow.Mode.BEAST:HuntFlow.Mode.GIANT);
      require(f.page.name().equals(p.getProperty("page")),name+" page="+f.page);
      int level=Integer.parseInt(p.getProperty("level"));if(level>0)require(f.level==level,name+" level="+f.level);
      for(String key:p.stringPropertyNames())if(key.startsWith("point.")){int[] point=f.points[HuntFlow.Kind.valueOf(key.substring(6)).ordinal()];require(error(p,key,point[0],point[1])<=12,name+" "+key);}
      for(int i=0;i<6;i++)if(p.containsKey("flag."+i))require(f.presets[i]&&error(p,"flag."+i,f.flags[i][0],f.flags[i][1])<=12,name+" flag="+i);
     }
    }else{
     Config config=CompatibilityRuntimeCheck.config(assets,new File(work,name),image.getWidth(),image.getHeight());
     Matcher.Numbers annotated=new Matcher.Numbers(){public int read(Bitmap b,Rect r){return -1;}public void close(){}};
     try(Matcher matcher=new Matcher(config,annotated)){
      matcher.frame(image);BearFlow.Frame f=matcher.observe();
      if(Boolean.parseBoolean(p.getProperty("formation"))){
       require(f.formation&&f.deploy&&error(p,"deploy",f.deployX,f.deployY)<=12,name+" formation/deploy");
       for(int i=0;i<6;i++)require(f.savedPreset[i]&&error(p,"flag."+i,f.presetX[i],f.presetY[i])<=12,name+" flag="+i);
      }else{
       List<BearFlow.Row> available=new ArrayList<>();for(BearFlow.Row row:f.rows)if(row.available)available.add(row);
       require(f.rallyList&&available.size()==Integer.parseInt(p.getProperty("rows")),name+" list/available="+available.size());
       for(int i=0;i<available.size();i++){
        BearFlow.Row nearest=null;double best=Double.POSITIVE_INFINITY;for(BearFlow.Row row:available){double e=error(p,"row."+i,row.x,row.y);if(e<best){best=e;nearest=row;}}
        require(best<=12&&nearest.secondsLeft==Integer.parseInt(p.getProperty("time."+i)),name+" row/timer="+i);
       }
      }
     }
    }
    checks++;System.out.println("ANDROID PAGE SCALE PASS: "+name);System.out.flush();
   }finally{image.recycle();}
  }
  System.out.println("ANDROID PAGE SCALE COMPLETE: "+checks+" checks; no gestures; OCR omitted");
 }catch(Throwable t){t.printStackTrace(System.out);System.exit(1);}}
}
