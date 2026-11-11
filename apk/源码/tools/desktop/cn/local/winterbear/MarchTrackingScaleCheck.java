package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.nio.file.*;
import java.io.PrintWriter;
import java.util.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Real Xiaomi frame sequence; annotated clocks, pixel identities and task ownership. */
public final class MarchTrackingScaleCheck {
    static final String[] NAMES={"xiaomi-rally","xiaomi-returning","xiaomi-returned"};
    // Visually annotated from 15:12:00, 15:13:41 and 15:14:25 screenshots.
    static final int[][] SECONDS={{22,66,14,28881,143},{30100,28797,28780,31},{30056,28753,28736,41}};
    static final HuntMarches.Type[][] TYPES={
        {HuntMarches.Type.MEAT,HuntMarches.Type.RETURN,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.RALLY},
        {HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.RETURN},
        {HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.WOOD}
    };
    static final double[] SCALES={1,.99,1.01};
    static final long[] TIMES={0,101000,145000};
    // Pixel crop annotations for these three known fixtures after each transform.
    // Clock expectations above are manual readings, never copied from the recognizer.
    static final int[][][] TIMER_CENTERS={
        {{345,391,437,483,529},{354,399,445,490,536},{337,383,430,476,523}},
        {{345,391,437,482},{354,400,445,489},{337,384,430,476}},
        {{345,391,437,483},{354,400,445,490},{337,384,430,476}}
    };
    static void exportTimers(Bitmap image,int phase,int scaleIndex,Path output,PrintWriter manifest)throws Exception{
        if(image.getWidth()!=1080)throw new IllegalArgumentException("Clock crop annotations require the original 1080px Xiaomi fixtures");
        int dx=new int[]{0,2,1}[scaleIndex],percent=(int)Math.round(SCALES[scaleIndex]*100);
        for(int row=0;row<SECONDS[phase].length;row++){
            int center=TIMER_CENTERS[phase][scaleIndex][row];
            Bitmap strip=HuntGlyphs.region(image,2,55+dx,center+1,145+dx,center+20,180,38);
            if(strip==null)throw new AssertionError("Missing clock crop");
            String name=NAMES[phase]+"-scale-"+percent+"-row-"+(row+1)+".png";
            try{if(!javax.imageio.ImageIO.write(strip.image,"png",output.resolve(name).toFile()))throw new AssertionError("PNG export failed: "+name);}
            finally{strip.recycle();}
            int seconds=SECONDS[phase][row];
            manifest.println(name+"\t"+seconds+"\t"+NAMES[phase]+"\t"+percent+"\t"+(row+1)+"\t"+String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));
        }
        manifest.flush();
    }
    static HuntMarches.Row[] read(HuntVision vision,Bitmap image){
        Mat gray=PageSweepCheck.mat(image,false),small=new Mat();
        try{
            Imgproc.resize(gray,small,new Size(540,Math.round(image.getHeight()*540./image.getWidth())),0,0,Imgproc.INTER_AREA);
            HuntFlow.Frame f=new HuntFlow.Frame();vision.observeMarchPanel(image,image.getWidth()/540.,small,f);return f.marches;
        }finally{gray.release();small.release();}
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);Path assets=Path.of(args[1]),fixtures=Path.of(args[2]),output=Path.of(args[3]);
        Files.createDirectories(output);Path nativeFixtures=output.resolve("native-fixtures");Files.createDirectories(nativeFixtures);
        int checks=0,failed=0;HuntMarches tracking=new HuntMarches();
        try(PrintWriter report=new PrintWriter(Files.newBufferedWriter(output.resolve("march-tracking.tsv")));
            PrintWriter manifest=new PrintWriter(Files.newBufferedWriter(nativeFixtures.resolve("timers.tsv")))){
            report.println("case\tresult\tseconds\tidentity_hamming\taccepted\tactive");
            manifest.println("file\tseconds\tsource\tscale_percent\trow\tclock");
            for(int phase=0;phase<NAMES.length;phase++){
                final int count=TYPES[phase].length;Bitmap base=BitmapFactory.decodeFile(fixtures.resolve(NAMES[phase]+".jpg").toString());HuntMarches.Row[] reference=null;
                // Only the total march count is annotated; row types/clocks/hashes come from actual pixels.
                try(HuntVision vision=new HuntVision(p->Files.newInputStream(assets.resolve(p)),(b,r)->count+"/6")){
                    for(int si=0;si<SCALES.length;si++){
                        double scale=SCALES[si];int dx=scale>1?(int)Math.ceil(base.getWidth()*(scale-1)/2):0;
                        Bitmap image=PageSweepCheck.changed(base,scale,dx,0,false);HuntMarches.Row[] rows=read(vision,image);boolean ok=rows!=null&&rows.length==count;
                        exportTimers(image,phase,si,nativeFixtures,manifest);
                        List<Integer> actual=new ArrayList<>(),distances=new ArrayList<>();
                        if(ok){
                            if(reference==null)reference=rows;
                            for(int i=0;i<count;i++){
                                actual.add(rows[i].seconds);int difference=Long.bitCount(rows[i].icon^reference[i].icon);distances.add(difference);
                                ok&=rows[i].type==TYPES[phase][i]&&rows[i].seconds==SECONDS[phase][i];
                                // Production tracks resources by their known type. Only non-resource
                                // identities use the eight-bit hash tolerance; still report every hash.
                                if(!rows[i].type.resource())ok&=difference<=8;
                            }
                        }
                        long at=TIMES[phase]+si*500L;boolean accepted=false;
                        if(rows!=null){
                            if(phase==0&&si==0){
                                // Establish the four external teams, then confirm the newly visible rally row.
                                ok&=tracking.observe(Arrays.copyOf(rows,rows.length-1),at-1,false)&&tracking.active()==0;
                                accepted=tracking.observe(rows,at,true);
                            }else accepted=tracking.observe(rows,at,false);
                        }
                        int expectedActive=phase<2?1:0;ok&=accepted&&tracking.active()==expectedActive;
                        checks++;if(!ok)failed++;String name=NAMES[phase]+"-"+scale;
                        String detail=name+"\t"+(ok?"PASS":"FAIL")+"\t"+actual+"\t"+distances+"\t"+accepted+"\t"+tracking.active();report.println(detail);report.flush();System.out.println(detail);
                        image.recycle();
                    }
                }finally{base.recycle();}
            }
        }
        System.out.println("MARCH TRACKING SCALE: "+checks+" checks, "+failed+" failures; annotated total counts only; actual pixel clocks and identities; no OCR or device claims");
        System.out.println("MARCH TIMER FIXTURES: 39 annotated clock strips -> "+nativeFixtures);
        if(failed>0)System.exit(1);
    }
}
