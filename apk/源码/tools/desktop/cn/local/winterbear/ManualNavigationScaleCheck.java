package cn.local.winterbear;

import android.graphics.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.opencv.core.Core;

/** Unchanged manual calibration must survive small content rescaling and new top padding. */
public final class ManualNavigationScaleCheck {
    private static int checks,failures;
    private static Path output;
    private static Bitmap load(Path fixtures,String name){
        Bitmap b=BitmapFactory.decodeFile(fixtures.resolve(name+".png").toString());
        if(b==null)throw new IllegalArgumentException("Missing "+name);return b;
    }
    private static void sample(Bitmap image,Path dir,String name,int x,int y,int w,int h)throws Exception{
        ImageIO.write(image.image.getSubimage(x,y,w,h),"png",dir.resolve(name+".png").toFile());
    }
    private static Config config(Path assets,Path layout,Bitmap image,Path samples)throws Exception{
        System.setProperty("custom.samples",samples.toString());
        Config c=new Config(assets,layout,image.getWidth(),image.getHeight());c.p.values.put("manualCalibration",1);return c;
    }
    private static void position(Config c,String name,int x,int y){
        c.p.values.put(name+"X",x);c.p.values.put(name+"Y",y);c.p.values.put(name+"Scale",1);
    }
    private static void record(String name,boolean ok,String detail,Bitmap image)throws Exception{
        checks++;if(!ok){failures++;ImageIO.write(image.image,"png",output.resolve("failed-"+name+".png").toFile());}
        System.out.println((ok?"PASS ":"FAIL ")+name+" "+detail);
    }
    private static Bitmap padded(Bitmap source){
        BufferedImage image=new BufferedImage(source.getWidth(),source.getHeight()+128,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.drawImage(source.image,0,64,null);g.dispose();return new Bitmap(image);
    }
    private static Bitmap blank(int w,int h){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB));}
    private static void paste(Bitmap target,Bitmap sample,int x,int y,boolean gray){
        for(int sy=0;sy<sample.getHeight();sy++)for(int sx=0;sx<sample.getWidth();sx++){
            int p=sample.getPixel(sx,sy);if(gray){int v=(int)Math.round(((p>>16)&255)*.299+((p>>8)&255)*.587+(p&255)*.114);p=0xff000000|v<<16|v<<8|v;}
            target.image.setRGB(x+sx,y+sy,p);
        }
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);
        Path assets=Path.of(args[1]),layout=Path.of(args[2]),fixtures=Path.of(args[3]);output=Path.of(args[4]);
        Path listSamples=output.resolve("list-samples"),formationSamples=output.resolve("formation-samples");
        Files.createDirectories(listSamples);Files.createDirectories(formationSamples);
        Bitmap list=load(fixtures,"video-315"),originalFormation=load(fixtures,"video-170"),formation=padded(originalFormation);
        sample(list,listSamples,"header",140,279,190,48);sample(list,listSamples,"rally_tab",160,415,85,58);
        sample(list,listSamples,"back",15,267,102,80);
        // The manual source already has a 64px inset. No test recalibrates these
        // saved coordinates after transforming the next screenshot.
        sample(formation,formationSamples,"formation",112,677,140,48);
        sample(formation,formationSamples,"deploy",772,2334,122,70);
        int[][] flags={{97,489},{210,489},{321,491},{434,492},{546,491},{653,489}};
        for(int i=0;i<6;i++)sample(formation,formationSamples,"preset_marker"+i,flags[i][0]-19,flags[i][1]-19,38,38);
        for(int variant=0;variant<4;variant++){
            double zoom=variant==1?.98:variant==2?1.02:1;int dy=variant==3?64:0;
            for(boolean troop:new boolean[]{false,true}){
                Bitmap base=troop?formation:list,image=PageSweepCheck.changed(base,zoom,0,dy,false);
                Config c=config(assets,layout,image,troop?formationSamples:listSamples);
                if(troop){position(c,"formation",182,701);position(c,"deploy",833,2369);for(int i=0;i<6;i++){c.p.values.put("presetX"+i,flags[i][0]);c.p.values.put("presetY"+i,flags[i][1]);c.p.values.put("preset_marker"+i+"Scale",1);}}
                else{position(c,"header",235,303);position(c,"rally_tab",202,444);position(c,"back",66,307);}
                try(Matcher m=new Matcher(c,new CounterOcr())){
                    m.frame(image);BearFlow.Frame f=m.observe();boolean ok;double error=0;int saved=0;
                    if(troop){
                        ok=f.formation&&f.deploy;
                        for(int i=0;i<6;i++){if(f.savedPreset[i])saved++;ok&=f.savedPreset[i];error=Math.max(error,PageSweepCheck.error(new int[]{f.presetX[i],f.presetY[i]},flags[i],base,zoom,0,dy));}
                        error=Math.max(error,PageSweepCheck.error(new int[]{f.deployX,f.deployY},new int[]{833,2369},base,zoom,0,dy));
                    }else{
                        ok=f.header&&f.rallyList&&f.rows.stream().filter(r->r.available).count()==3;
                        error=Math.max(PageSweepCheck.error(new int[]{f.backX,f.backY},new int[]{66,307},base,zoom,0,dy),PageSweepCheck.error(new int[]{f.rallyX,f.rallyY},new int[]{202,444},base,zoom,0,dy));
                    }
                    record((troop?"formation":"list")+"-"+zoom+"-"+dy,ok&&error<=12,"header="+f.header+" list="+f.rallyList+" formation="+f.formation+" deploy="+f.deploy+" flags="+saved+" max_error="+String.format(Locale.ROOT,"%.2f",error),image);
                }finally{image.recycle();}
            }
        }
        Bitmap header=BitmapFactory.decodeFile(listSamples.resolve("header.png").toString());
        Bitmap deploy=BitmapFactory.decodeFile(assets.resolve("reference/deploy.png").toString());
        for(int variant=0;variant<3;variant++){
            Bitmap image=blank(1080,2400);Config c=config(assets,layout,image,listSamples);position(c,"header",235,303);
            String key="header";Rect region=new Rect(0,0,810,1200);
            if(variant==0){paste(image,header,140,500,false);paste(image,header,440,700,false);}
            else if(variant==1)paste(image,header,850,500,false);
            else{key="deploy";region=new Rect(540,1200,1080,2400);paste(image,deploy,730,1700,true);}
            try(Matcher m=new Matcher(c,new CounterOcr())){m.frame(image);record("negative-"+variant,m.locate(key,region)==null,"duplicate title / outside region / gray deploy must be rejected",image);}
            finally{image.recycle();}
        }
        System.out.println("MANUAL NAVIGATION: checks="+checks+" failures="+failures+"; real manual crops, no recalibration; synthetic negative canvases; no OCR/gestures");
        if(failures>0)System.exit(1);
    }
}
