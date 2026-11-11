package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.opencv.core.Core;

/** app_process entry: native-lib-dir, assets-dir, native-fixtures-dir, report-file. */
public final class MarchTimerScaleRuntimeCheck {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        try{run(args);}catch(Throwable error){error.printStackTrace(System.out);System.out.flush();System.exit(1);}
    }
    private static void run(String[] args)throws Exception{
        require(args.length==4,"Expected native-lib-dir assets-dir native-fixtures-dir report-file");
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");Core.setNumThreads(1);
        checkResampling();
        File assets=new File(args[1]),fixtures=new File(args[2]),reportFile=new File(args[3]);
        File parent=reportFile.getParentFile();if(parent!=null)require(parent.isDirectory()||parent.mkdirs(),"Cannot create report directory");
        Method read=HuntGlyphs.class.getDeclaredMethod("adaptiveTimer",Bitmap.class);read.setAccessible(true);
        String[] variants={"original","blank","missing-left-half","missing-right-half","low-contrast"};
        int positive=0,negative=0,failures=0;Set<String> seen=new HashSet<>();
        try(HuntGlyphs glyphs=new HuntGlyphs(path->new FileInputStream(new File(assets,path)));
            BufferedReader manifest=new BufferedReader(new InputStreamReader(new FileInputStream(new File(fixtures,"timers.tsv")),StandardCharsets.UTF_8));
            PrintWriter report=new PrintWriter(new OutputStreamWriter(new FileOutputStream(reportFile),StandardCharsets.UTF_8))){
            require("file\tseconds\tsource\tscale_percent\trow\tclock".equals(manifest.readLine()),"Unexpected timer manifest header");
            report.println("file\tvariant\texpected\tactual\tresult");
            for(String line;(line=manifest.readLine())!=null;){
                if(line.isEmpty())continue;String[] fields=line.split("\t",-1);
                require(fields.length==6,"Malformed timer annotation: "+line);
                String name=fields[0];require(name.matches("[a-z0-9-]+\\.png")&&seen.add(name),"Invalid/duplicate fixture: "+name);
                int expected=Integer.parseInt(fields[1]);require(expected>=0,"Unknown annotated clock: "+name);
                String clock=String.format(Locale.ROOT,"%02d:%02d:%02d",expected/3600,expected/60%60,expected%60);
                require(clock.equals(fields[5]),"Seconds/clock annotation mismatch: "+name);
                Bitmap original=BitmapFactory.decodeFile(new File(fixtures,name).getAbsolutePath());
                require(original!=null&&original.getWidth()==180&&original.getHeight()==38,"Invalid clock strip: "+name);
                try{
                    for(int variant=0;variant<variants.length;variant++){
                        Bitmap input=variant==0?original:corrupt(original,variant);int actual;
                        try{actual=(Integer)read.invoke(glyphs,input);}finally{if(input!=original)input.recycle();}
                        int wanted=variant==0?expected:-1;boolean ok=actual==wanted;if(!ok)failures++;
                        if(variant==0)positive++;else negative++;
                        String result=name+"\t"+variants[variant]+"\t"+wanted+"\t"+actual+"\t"+(ok?"PASS":"FAIL");
                        report.println(result);if(!ok)System.out.println("ANDROID MARCH TIMER FAIL: "+result);
                    }
                    report.flush();
                }finally{original.recycle();}
            }
        }
        require(positive==39&&negative==156,"Incomplete fixture set: "+positive+" positives / "+negative+" negatives");
        require(failures==0,"Native march timer failures: "+failures+"; report="+reportFile);
        System.out.println("ANDROID MARCH TIMER SCALE PASS: 195 checks; 39 annotated clocks; 156 corrupt clocks rejected; real Android Bitmap; no gestures");System.out.flush();
    }
    private static void checkResampling()throws Exception{
        Method resize=HuntGlyphs.class.getDeclaredMethod("resampleTimerGray",byte[].class,int.class,int.class,int.class,int.class);resize.setAccessible(true);
        byte[] ramp={0,50,100,(byte)200,(byte)230,(byte)255};
        require(Arrays.equals(ramp,(byte[])resize.invoke(null,ramp,3,2,3,2)),"Resampling identity changed pixels");
        for(int[] shape:new int[][]{{1,1,13,7,173},{2,3,7,4,0},{3,2,4,7,255},{7,4,3,9,91}}){
            byte[] input=new byte[shape[0]*shape[1]];Arrays.fill(input,(byte)shape[4]);byte[] actual=(byte[])resize.invoke(null,input,shape[0],shape[1],shape[2],shape[3]);
            require(actual.length==shape[2]*shape[3],"Resampling output dimensions");for(byte pixel:actual)require((pixel&255)==shape[4],"Constant/edge resampling changed pixels");
        }
        byte[] corners={0,100,(byte)200,(byte)255};
        require(Arrays.equals((byte[])resize.invoke(null,corners,2,2,3,3),new byte[]{0,50,100,100,(byte)139,(byte)178,(byte)200,(byte)228,(byte)255}),"Bilinear centres/edge clamping");
        require(Arrays.equals((byte[])resize.invoke(null,corners,2,2,1,1),new byte[]{(byte)139}),"Downsampling midpoint rounding");
        byte[] row=(byte[])resize.invoke(null,new byte[]{0,(byte)255},2,1,3,5);
        for(int y=0;y<5;y++)require(row[y*3]==0&&(row[y*3+1]&255)==128&&(row[y*3+2]&255)==255,"Single-row interpolation/clamping");
        System.out.println("ANDROID MARCH TIMER RESAMPLE PASS: 8 boundary/constant/interpolation checks");System.out.flush();
    }
    private static Bitmap corrupt(Bitmap original,int variant){
        Bitmap result=original.copy(Bitmap.Config.ARGB_8888,true);require(result!=null,"Cannot copy timer bitmap");
        int width=result.getWidth(),height=result.getHeight();
        if(variant==4){
            int[] pixels=new int[width*height];result.getPixels(pixels,0,width,0,0,width,height);
            for(int i=0;i<pixels.length;i++)pixels[i]=Color.rgb(Color.red(pixels[i])/3,Color.green(pixels[i])/3,Color.blue(pixels[i])/3);
            result.setPixels(pixels,0,width,0,0,width,height);
        }else{
            Paint paint=new Paint();paint.setColor(Color.BLACK);Canvas canvas=new Canvas(result);
            if(variant==1)canvas.drawRect(0,0,width,height,paint);
            else if(variant==2)canvas.drawRect(0,0,width/2,height,paint);
            else if(variant==3)canvas.drawRect(width/2,0,width,height,paint);
            else throw new IllegalArgumentException("Unknown corruption variant");
        }
        return result;
    }
}
