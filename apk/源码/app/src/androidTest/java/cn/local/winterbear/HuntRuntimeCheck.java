package cn.local.winterbear;
import android.graphics.*;
import java.io.*;
import static cn.local.winterbear.HuntFlow.*;

/** Actual Android/OpenCV visual replay. Numeric reads are annotated here, not phone OCR claims. */
public final class HuntRuntimeCheck {
    static int checks;
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static Bitmap resized(Bitmap source,int w,int h,boolean center){
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);float s=w/(float)source.getWidth();
        if(center){c.translate(0,(h-source.getHeight()*s)/2);c.scale(s,s);c.drawBitmap(source,0,0,p);}
        else{
            int half=source.getHeight()/2,scaledHalf=Math.round(half*s);
            c.drawBitmap(source,new Rect(0,0,source.getWidth(),half),new Rect(0,0,w,scaledHalf),p);
            c.drawBitmap(source,new Rect(0,half,source.getWidth(),source.getHeight()),new Rect(0,h-scaledHalf,w,h),p);
        }return result;
    }
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.out.flush();System.exit(1);}}
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assets=new File(args[2]);FeedbackVision.Assets provider=p->new FileInputStream(new File(assets,p));
        String[] names={"map","giant_search","giant_detail","rally","giant_formation","map_one","beast_search","beast_detail","beast_formation","low_energy","energy"};
        Page[] expected={Page.MAP,Page.SEARCH,Page.GIANT,Page.RALLY,Page.FORMATION,Page.MAP,Page.SEARCH,Page.BEAST,Page.FORMATION,Page.FORMATION,Page.ENERGY};
        for(int i=0;i<names.length;i++){
            final String name=names[i];final int index=i;
            Bitmap original=BitmapFactory.decodeFile(new File(fixtures,name+".png").getAbsolutePath());require(original!=null,name);
            for(int[] size:new int[][]{{1080,2400},{1152,2376},{1260,2800},{1344,2772},{1080,2374},{720,1600}}){
                Bitmap image=resized(original,size[0],size[1],name.equals("energy")||name.equals("rally"));
                if(name.equals("beast_detail")){
                    // This floating card crosses the synthetic top/bottom split. Keep the
                    // entire card intact at the screen center, then test its located button.
                    double s=size[0]/(double)original.getWidth(),mid=(size[1]-original.getHeight()*s)/2;
                    new Canvas(image).drawBitmap(original,new Rect(0,560,original.getWidth(),1320),new Rect(0,(int)Math.round(560*s+mid),size[0],(int)Math.round(1320*s+mid)),new Paint(Paint.FILTER_BITMAP_FLAG));
                }
                try(HuntVision vision=new HuntVision(provider,(b,r)->{
                    double x=r.left*540./b.getWidth();
                    if(name.contains("formation")||name.equals("low_energy"))throw new AssertionError("Formation must not call numeric OCR: "+name);
                    if(name.equals("energy"))return x<100?"232":"15/200";
                    if(name.equals("map"))return "";
                    if(name.equals("map_one"))return "1/6";
                    return name.startsWith("beast")?"30":"8";
                })){
                    Frame f=vision.observe(image,name.startsWith("beast")?Mode.BEAST:Mode.GIANT);
                    require(f.page==expected[index],name+" "+size[0]+" expected "+expected[index]+" got "+f.page);
                    if(f.page==Page.MAP){require(f.used==(name.equals("map")?0:1),"map count");require(f.points[Kind.OPEN.ordinal()][0]>0,"search entry");}
                    if(f.page==Page.SEARCH){require(f.category==(name.startsWith("beast")?1:0),name+" selection="+f.category);require(f.level==(name.startsWith("beast")?30:8),"level");}
                    if(f.page==Page.FORMATION){for(boolean flag:f.presets)require(flag,"missing flag "+name);require(f.points[Kind.DEPLOY.ordinal()][0]>0,"deploy button");}
                    if(f.page==Page.GIANT||f.page==Page.BEAST)require(f.level==(name.startsWith("beast")?30:8),"detail level");
                    if(f.page==Page.RALLY||f.page==Page.SEARCH||f.page==Page.FORMATION||f.page==Page.GIANT||f.page==Page.BEAST){int[] back=f.points[Kind.BACK.ordinal()];require(back[0]>0&&back[0]<image.getWidth()&&back[1]>0&&back[1]<image.getHeight(),"game return target "+name);}
                    if(f.page==Page.ENERGY){require(f.stock==232&&f.energy==15,"energy numbers "+f.stock+"/"+f.energy);int[] use=f.points[Kind.USE.ordinal()];require(use[0]>0,"only green use");require(f.points[Kind.CLOSE.ordinal()][0]>0,"close energy");}
                    checks++;System.out.println("HUNT IMAGE PASS: "+name+" "+size[0]+"x"+size[1]);System.out.flush();
                }finally{image.recycle();}
            }original.recycle();
        }
        // Preserve the exact label shapes but turn the owned-stock use button orange:
        // matching its text alone must never allow the paid button path.
        Bitmap energy=BitmapFactory.decodeFile(new File(fixtures,"energy.png").getAbsolutePath()).copy(Bitmap.Config.ARGB_8888,true);
        Canvas c=new Canvas(energy);Paint p=new Paint();p.setColor(Color.rgb(240,150,30));c.drawRect(790,1135,822,1170,p);
        try(HuntVision v=new HuntVision(provider,(b,r)->"232")){Frame f=v.observe(energy,Mode.GIANT);require(f.page==Page.ENERGY&&f.points[Kind.USE.ordinal()][0]==0,"orange button must not be used");checks++;}finally{energy.recycle();}
        for(Rect missing:new Rect[]{new Rect(350,900,750,1040),new Rect(300,1520,800,1710)}){
            Bitmap rally=BitmapFactory.decodeFile(new File(fixtures,"rally.png").getAbsolutePath()).copy(Bitmap.Config.ARGB_8888,true);
            Paint blank=new Paint();blank.setColor(Color.BLACK);new Canvas(rally).drawRect(missing,blank);
            try(HuntVision v=new HuntVision(provider,(b,r)->"8")){require(v.observe(rally,Mode.GIANT).page!=Page.RALLY,"rally requires title AND launch button");checks++;}finally{rally.recycle();}
        }
        require(HuntVision.number("232")==232&&HuntVision.number("300钻石")==-1&&HuntVision.level("等级30 雪豹")==30,"strict numeric parsers");
        System.out.println("ANDROID HUNT PASS: "+checks+" visual checks; numeric OCR annotated; no gestures; wide screens synthetic");
    }
}
