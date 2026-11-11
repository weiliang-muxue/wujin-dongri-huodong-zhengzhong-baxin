package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import java.io.File;

/** Focused Android Bitmap/OpenCV manual navigation checks; no full list OCR or gestures. */
public final class ManualNavigationRuntimeCheck {
    private static final Matcher.Numbers NO_OCR=new Matcher.Numbers(){
        public int read(Bitmap bitmap,Rect rect){return -1;}
        public void close(){}
    };
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Bitmap load(File file){
        Bitmap result=BitmapFactory.decodeFile(file.getAbsolutePath());require(result!=null,"Missing "+file);return result;
    }
    private static Bitmap transform(Bitmap source,float scale,int dy){
        Bitmap image=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(image);c.drawColor(Color.BLACK);
        c.translate(source.getWidth()*(1-scale)/2,(source.getHeight()-40)*(1-scale)+dy);
        c.scale(scale,scale);c.drawBitmap(source,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));return image;
    }
    private static Rect region(String key,Bitmap image,Matcher.Hit header){
        int w=image.getWidth(),h=image.getHeight();
        if(key.equals("header"))return new Rect(0,0,w*3/4,h/2);
        if(key.equals("rally_tab"))return new Rect(0,header.y,w/2,Math.min(h,header.y+w/3));
        return new Rect(0,0,w/3,h/2);
    }
    public static void main(String[] args){
        try{run(args);}catch(Throwable error){error.printStackTrace(System.out);System.out.flush();System.exit(1);}
    }
    private static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assets=new File(args[2]),work=new File(args[3]);
        Bitmap source=load(new File(fixtures,"video-315.png"));int checks=0;
        try{
            Config c=CompatibilityRuntimeCheck.config(assets,new File(work,"manual-navigation"),source.getWidth(),source.getHeight());
            c.p.edit().putBoolean("manualCalibration",true).apply();
            String[] keys={"header","rally_tab","back"};Matcher.Hit[] expected=new Matcher.Hit[3];
            try(Matcher m=new Matcher(c,NO_OCR)){
                m.frame(source);expected[0]=m.locate("header",region("header",source,null));require(expected[0]!=null,"Baseline header");
                for(int i=1;i<keys.length;i++){expected[i]=m.locate(keys[i],region(keys[i],source,expected[0]));require(expected[i]!=null,"Baseline "+keys[i]);}
            }
            for(int variant=0;variant<4;variant++){
                float scale=variant==1?.98f:variant==2?1.02f:1;int dy=variant==3?64:0;Bitmap image=transform(source,scale,dy);
                try(Matcher m=new Matcher(c,NO_OCR)){
                    m.frame(image);Matcher.Hit header=m.locate("header",region("header",image,null));require(header!=null,"Manual header scale="+scale+" dy="+dy);
                    for(int i=0;i<keys.length;i++){
                        Matcher.Hit hit=i==0?header:m.locate(keys[i],region(keys[i],image,header));
                        require(hit!=null,"Manual "+keys[i]+" scale="+scale+" dy="+dy);
                        double x=expected[i].x*scale+source.getWidth()*(1-scale)/2,y=expected[i].y*scale+(source.getHeight()-40)*(1-scale)+dy;
                        require(Math.hypot(hit.x-x,hit.y-y)<=6,"Wrong coordinate "+keys[i]+" scale="+scale+" dy="+dy);
                        checks++;
                    }
                    System.out.println("MANUAL NAVIGATION PASS: scale="+scale+" dy="+dy+" header/tab/back");System.out.flush();
                }finally{image.recycle();}
            }
            Bitmap header=load(new File(assets,"reference/header.png")),deploy=load(new File(assets,"reference/deploy.png"));
            try{
                for(int variant=0;variant<3;variant++){
                    Bitmap image=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.BLACK);
                    String key="header";Rect search=region(key,image,null);
                    if(variant==0){canvas.drawBitmap(header,140,500,null);canvas.drawBitmap(header,440,700,null);}
                    else if(variant==1)canvas.drawBitmap(header,850,500,null);
                    else{
                        key="deploy";search=new Rect(source.getWidth()/2,source.getHeight()/2,source.getWidth(),source.getHeight());
                        ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0);Paint paint=new Paint();paint.setColorFilter(new ColorMatrixColorFilter(matrix));canvas.drawBitmap(deploy,730,1700,paint);
                    }
                    try(Matcher m=new Matcher(c,NO_OCR)){m.frame(image);require(m.locate(key,search)==null,"Duplicate/outside/gray target accepted: "+variant);checks++;}
                    finally{image.recycle();}
                    System.out.println("MANUAL NAVIGATION NEGATIVE PASS: "+variant);System.out.flush();
                }
            }finally{header.recycle();deploy.recycle();}
        }finally{source.recycle();}
        System.out.println("ANDROID MANUAL NAVIGATION PASS: "+checks+" checks; real screenshot transformations and explicit synthetic negatives; no OCR/gestures");
    }
}
