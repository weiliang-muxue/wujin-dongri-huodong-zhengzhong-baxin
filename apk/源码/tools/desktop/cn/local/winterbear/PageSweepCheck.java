package cn.local.winterbear;
import android.graphics.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static cn.local.winterbear.HuntFlow.*;
/** Runs unchanged production page recognizers with desktop pixel decoding adapters; no OCR/gestures. */
public final class PageSweepCheck {
    static int checks,failures;static PrintWriter report;static Path output;
    static final double[] scales={1,.98,1.02,.90,1.10,.94};
    static Bitmap changed(Bitmap base,double scale,int dx,int dy,boolean jpeg)throws Exception{
        BufferedImage image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g=image.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        java.awt.geom.AffineTransform t=new java.awt.geom.AffineTransform(scale,0,0,scale,tx(base,scale,dx),ty(base,scale,dy));g.drawImage(base.image,t,null);g.dispose();
        if(jpeg){ImageWriter writer=ImageIO.getImageWritersByFormatName("jpeg").next();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ImageOutputStream stream=new MemoryCacheImageOutputStream(bytes)){writer.setOutput(stream);ImageWriteParam p=writer.getDefaultWriteParam();p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);p.setCompressionQuality(.80f);writer.write(null,new IIOImage(image,null,null),p);}finally{writer.dispose();}
            image=ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));}
        return new Bitmap(image);
    }
    static double tx(Bitmap base,double scale,int dx){return base.getWidth()*(1-scale)/2+dx;}
    static double ty(Bitmap base,double scale,int dy){return (base.getHeight()-40)*(1-scale)+dy;}
    static void record(String name,boolean ok,String detail,Bitmap image)throws Exception{
        checks++;if(!ok){failures++;ImageIO.write(image.image,"png",output.resolve("failed-"+name+".png").toFile());}
        report.printf("%s\t%s\t%s%n",name,ok?"PASS":"FAIL",detail);report.flush();System.out.println((ok?"PASS ":"FAIL ")+name+" "+detail);
    }
    static Mat mat(Bitmap image,boolean color){Mat rgba=new Mat(),result=new Mat();org.opencv.android.Utils.bitmapToMat(image,rgba);Imgproc.cvtColor(rgba,result,color?Imgproc.COLOR_RGBA2RGB:Imgproc.COLOR_RGBA2GRAY);rgba.release();return result;}
    static double error(int[] actual,int[] expected,Bitmap base,double scale,int dx,int dy){return Math.hypot(actual[0]-(expected[0]*scale+tx(base,scale,dx)),actual[1]-(expected[1]*scale+ty(base,scale,dy)));}
    static void expected(Properties p,String key,int x,int y,Bitmap base,double scale,int dx,int dy){p.setProperty(key,(x*scale+tx(base,scale,dx))+","+(y*scale+ty(base,scale,dy)));}
    static void export(String name,Bitmap image,Properties p)throws Exception{
        Path dir=output.resolve("native-fixtures");Files.createDirectories(dir);ImageIO.write(image.image,"png",dir.resolve(name+".png").toFile());
        try(var stream=Files.newOutputStream(dir.resolve(name+".properties"))){p.store(stream,"Expected geometry from the source frame, before recognizer execution");}
    }
    static void hunt(Path assets,Path fixtures)throws Exception{
        String[][] cases={{"giant_search","SEARCH"},{"giant_detail","GIANT"},{"rally","RALLY"},{"giant_formation","FORMATION"},{"beast_search","SEARCH"},{"beast_detail","BEAST"},{"beast_formation","FORMATION"},{"energy","ENERGY"},{"huawei-giant","GIANT"},{"huawei-formation","FORMATION"},{"too-late","ERROR"},{"reported-overflow","ERROR"}};
        try(HuntVision vision=new HuntVision(path->Files.newInputStream(assets.resolve(path)),(bitmap,region)->"")){
            for(String[] item:cases){if("errors".equals(System.getProperty("section"))&&!item[1].equals("ERROR"))continue;Bitmap base=BitmapFactory.decodeFile(fixtures.resolve(item[0]+".png").toString());Mode mode=item[0].startsWith("beast")?Mode.BEAST:Mode.GIANT;
                Frame reference=vision.observe(base,mode);Page expected=Page.valueOf(item[1]);
                for(double scale:scales){int dx=scale==.94?18:0,dy=scale==.94?-28:0;Bitmap image=changed(base,scale,dx,dy,scale==.94);Frame f=vision.observe(image,mode);
                    boolean ok=reference.page==expected&&f.page==expected;double max=0;
                    for(Kind k:new Kind[]{Kind.SEARCH,Kind.CATEGORY,Kind.LESS,Kind.MORE,Kind.TARGET,Kind.LAUNCH,Kind.DEPLOY,Kind.USE,Kind.CLOSE,Kind.DISMISS}){
                        int[] p=reference.points[k.ordinal()];if(p[0]>0){double e=error(f.points[k.ordinal()],p,base,scale,dx,dy);max=Math.max(max,e);ok&=e<=12;}}
                    int flags=0;for(int i=0;i<6;i++)if(reference.presets[i]){flags++;double e=error(f.flags[i],reference.flags[i],base,scale,dx,dy);max=Math.max(max,e);ok&=f.presets[i]&&e<=12;}
                    if(expected==Page.FORMATION)ok&=flags==6;
                    int expectedLevel=expected==Page.GIANT?8:expected==Page.BEAST?30:-1;
                    if(expectedLevel>0)ok&=reference.level==expectedLevel&&f.level==expectedLevel;
                    Properties p=new Properties();p.setProperty("type","hunt");p.setProperty("page",expected.name());p.setProperty("level",Integer.toString(expectedLevel));
                    for(Kind k:new Kind[]{Kind.SEARCH,Kind.CATEGORY,Kind.LESS,Kind.MORE,Kind.TARGET,Kind.LAUNCH,Kind.DEPLOY,Kind.USE,Kind.CLOSE,Kind.DISMISS})if(reference.points[k.ordinal()][0]>0)expected(p,"point."+k,reference.points[k.ordinal()][0],reference.points[k.ordinal()][1],base,scale,dx,dy);
                    for(int i=0;i<6;i++)if(reference.presets[i])expected(p,"flag."+i,reference.flags[i][0],reference.flags[i][1],base,scale,dx,dy);
                    export(item[0]+"-"+scale,image,p);
                    record(item[0]+"-"+scale,ok,"page="+f.page+" expected="+expected+" level="+f.level+" flags="+flags+" max_error="+String.format(Locale.ROOT,"%.2f",max),image);
                }
            }
        }
    }
    static void bear(Path assets,Path fixtures)throws Exception{
        for(String name:new String[]{"video-315","huawei-bear-list","xiaomi-list","video-170"}){
            Bitmap base=BitmapFactory.decodeFile(fixtures.resolve(name+".png").toString());
            boolean negative=name.equals("xiaomi-list"),formation=name.equals("video-170");
            Config config=new Config(assets,output.resolve("layout.properties"),base.getWidth(),base.getHeight());
            try(Matcher vision=new Matcher(config,new CounterOcr())){
                vision.frame(base);BearFlow.Frame reference=vision.observe();
                // Preserve the rightmost timer digits in the enlargement cases.
                // A separate clipped-timer case below verifies abstention.
                for(double scale:scales){int dx=scale>1?-24:scale==.94?18:0,dy=scale==.94?-28:0;Bitmap image=changed(base,scale,dx,dy,scale==.94);
                    vision.frame(image);BearFlow.Frame f=vision.observe();double max=0;
                    long before=reference.rows.stream().filter(r->r.available).count(),after=f.rows.stream().filter(r->r.available).count();
                    boolean ok;
                    if(formation){
                        ok=f.formation&&f.deploy;max=error(new int[]{f.deployX,f.deployY},new int[]{reference.deployX,reference.deployY},base,scale,dx,dy);
                        for(int i=0;i<6;i++){max=Math.max(max,error(new int[]{f.presetX[i],f.presetY[i]},new int[]{reference.presetX[i],reference.presetY[i]},base,scale,dx,dy));ok&=reference.savedPreset[i]&&f.savedPreset[i];}
                    }else{
                        int expected=name.equals("video-315")?3:1;
                        ok=reference.rallyList&&f.rallyList&&before==after&&(negative?f.rows.isEmpty():before==expected);
                        for(BearFlow.Row original:reference.rows)if(original.available){double best=Double.POSITIVE_INFINITY;BearFlow.Row nearest=null;
                            for(BearFlow.Row found:f.rows)if(found.available){double e=error(new int[]{found.x,found.y},new int[]{original.x,original.y},base,scale,dx,dy);if(e<best){best=e;nearest=found;}}max=Math.max(max,best);
                            ok&=nearest!=null&&nearest.secondsLeft==original.secondsLeft;}
                    }
                    ok&=max<=12;
                    Properties p=new Properties();p.setProperty("type","bear");p.setProperty("formation",Boolean.toString(formation));p.setProperty("rows",Long.toString(before));
                    if(formation){expected(p,"deploy",reference.deployX,reference.deployY,base,scale,dx,dy);for(int i=0;i<6;i++)expected(p,"flag."+i,reference.presetX[i],reference.presetY[i],base,scale,dx,dy);}
                    else{int i=0;for(BearFlow.Row original:reference.rows)if(original.available){expected(p,"row."+i,original.x,original.y,base,scale,dx,dy);p.setProperty("time."+i,Integer.toString(original.secondsLeft));i++;}}
                    export(name+"-"+scale,image,p);
                    record(name+"-"+scale,ok,"list="+f.rallyList+" formation="+f.formation+" deploy="+f.deploy+" before="+before+" after="+after+" times="+f.rows.stream().map(r->r.secondsLeft).toList()+" max_error="+max,image);
                }
                if(name.equals("video-315")){
                    Bitmap clipped=changed(base,1.1,0,0,false);vision.frame(clipped);BearFlow.Frame f=vision.observe();
                    boolean ok=f.rallyList&&!f.rows.isEmpty()&&f.rows.get(0).secondsLeft<0;
                    record("video-315-clipped-timer",ok,"clipped first timer must stay unknown",clipped);
                }
            }
        }
    }
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);output=Path.of(args[3]);
        try(PrintWriter writer=new PrintWriter(Files.newBufferedWriter(output.resolve("pages.tsv")))){report=writer;report.println("case\tresult\tdetail");if(!"bear".equals(System.getProperty("section")))hunt(Path.of(args[1]),Path.of(args[2]));if(!"errors".equals(System.getProperty("section")))bear(Path.of(args[1]),Path.of(args[2]));}
        System.out.println("PAGE SWEEP: checks="+checks+" failures="+failures+"; desktop Bitmap adapter; production recognizers; OCR unavailable");if(failures>0)System.exit(1);
    }
}
