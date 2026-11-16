package cn.local.winterbear;

import android.graphics.*;
import java.io.*;
import java.util.*;

/** Native Bitmap reads of private recorded crops plus explicitly synthetic negatives. */
public final class MarchCounterRuntimeCheck {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static int read(MarchCounterVision reader,Bitmap b){return reader.read(b,new Rect(0,0,b.getWidth(),b.getHeight()));}
    public static void main(String[] args){try{
        File cases=new File(args[0]),assets=new File(args[1]);int checks=0;
        MarchCounterVision reader=new MarchCounterVision(p->new FileInputStream(new File(assets,p)));
        try(BufferedReader manifest=new BufferedReader(new FileReader(new File(cases,"counts.tsv")))){
            for(String line;(line=manifest.readLine())!=null;){
                String[] values=line.split("\\t");Bitmap source=BitmapFactory.decodeFile(new File(cases,values[0]).getPath());int expected=Integer.parseInt(values[1]);
                require(source!=null,"Missing recorded crop "+values[0]);
                for(float zoom:new float[]{.67f,.9f,.98f,1,1.02f,1.1f,1.25f}){
                    Bitmap scaled=Bitmap.createScaledBitmap(source,Math.round(source.getWidth()*zoom),Math.round(source.getHeight()*zoom),true);
                    int actual=read(reader,scaled);require(actual==expected,values[0]+" scale="+zoom+" expected="+expected+" actual="+actual);checks++;
                    if(scaled!=source)scaled.recycle();
                }
                source.recycle();System.out.println("NATIVE PIXEL PASS: "+values[0]+" seven scales");System.out.flush();
            }
        }
        Bitmap source=BitmapFactory.decodeFile(new File(cases,"three.png").getPath());
        for(int variant=0;variant<6;variant++){
            int height=variant==4?source.getHeight()*2:source.getHeight();
            Bitmap test=Bitmap.createBitmap(source.getWidth(),height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(test);canvas.drawColor(Color.BLACK);
            Paint paint=new Paint();paint.setColor(Color.BLACK);
            if(variant!=0)canvas.drawBitmap(source,0,0,null);
            if(variant==1)canvas.drawRect(29,0,44,height,paint); // missing slash
            if(variant==2)canvas.drawRect(43,0,test.getWidth(),height,paint); // missing denominator
            if(variant==3)canvas.drawRect(0,0,30,height,paint); // missing numerator
            if(variant==4)canvas.drawBitmap(source,0,source.getHeight(),null); // ambiguous two counters
            if(variant==5)canvas.drawRect(0,0,test.getWidth(),27,paint); // clipped top half
            require(read(reader,test)==-1,"Synthetic negative accepted: "+variant);test.recycle();checks++;
        }
        long[] times=new long[200];for(int i=0;i<times.length;i++){long start=System.nanoTime();require(read(reader,source)==3,"Repeated reading changed");times[i]=System.nanoTime()-start;}
        Arrays.sort(times);source.recycle();
        System.out.println("ANDROID ACTUAL COUNTER PASS: "+checks+" cases, 200 repeated reads; p50="+times[100]/1e6+"ms p95="+times[190]/1e6+"ms (software emulator only); no supplied OCR values or game gestures");
    }catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}}
}
