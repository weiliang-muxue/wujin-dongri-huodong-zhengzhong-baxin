package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/** Each timer is read above its own green +, with glyph proportions unchanged across phones. */
final class CountdownVision {
    private final CountdownDigits digits=new CountdownDigits();
    CountdownVision(FeedbackVision.Assets assets){
        try(InputStream in=assets.open("feedback/timer_digits.png")){
            Bitmap atlas=BitmapFactory.decodeStream(in);
            if(atlas==null||atlas.getWidth()!=CountdownDigits.W*10||atlas.getHeight()%CountdownDigits.H!=0)throw new IllegalArgumentException("倒计时模板无效");
            try{
                int[] pixels=new int[CountdownDigits.W*CountdownDigits.H];
                for(int row=0;row<atlas.getHeight()/CountdownDigits.H;row++)for(int d=0;d<10;d++){
                    atlas.getPixels(pixels,0,CountdownDigits.W,d*CountdownDigits.W,row*CountdownDigits.H,CountdownDigits.W,CountdownDigits.H);
                    byte[] sample=new byte[pixels.length];int ink=0;
                    for(int i=0;i<pixels.length;i++)if((pixels[i]&255)>128){sample[i]=1;ink++;}
                    if(ink>0)digits.samples[d].add(sample);
                }
            }finally{atlas.recycle();}
        }catch(Exception e){throw new IllegalStateException("无法读取集结倒计时模板",e);}
    }
    int read(Mat gray,int screenWidth,int joinY,double scale){
        return read(gray,screenWidth,(int)Math.round(screenWidth*.89),joinY,scale);
    }
    int read(Mat gray,int screenWidth,int joinX,int joinY,double scale){
        int left=Math.max(0,(int)((joinX-screenWidth*.10)*scale)),right=Math.min(gray.cols(),(int)((joinX+screenWidth*.08)*scale));
        int top=Math.max(0,(int)((joinY-screenWidth*.27)*scale)),bottom=Math.min(gray.rows(),(int)Math.ceil((joinY-screenWidth*.21)*scale));
        // Padding can cross a screen edge while all six digits are still visible.
        // The digit reader itself rejects genuinely clipped or incomplete timers.
        if(right<=left||bottom<=top)return -1;
        Mat crop=gray.submat(new Rect(left,top,right-left,bottom-top));
        try{byte[] pixels=new byte[(int)crop.total()];crop.get(0,0,pixels);return digits.read(pixels,crop.cols(),crop.rows());}
        finally{crop.release();}
    }
}
