package android.graphics;
import java.awt.image.BufferedImage;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
/** Desktop test image adapter only. Android tests use real Bitmap. */
public final class Bitmap {
    public final BufferedImage image;
    public Bitmap(BufferedImage image){this.image=image;}
    public int getWidth(){return image.getWidth();}
    public int getHeight(){return image.getHeight();}
    public void getPixels(int[] data,int offset,int stride,int x,int y,int w,int h){image.getRGB(x,y,w,h,data,offset,stride);}
    public int getPixel(int x,int y){return image.getRGB(x,y);}
    public void recycle(){}
    public static Bitmap createBitmap(Bitmap source,int x,int y,int w,int h){return new Bitmap(source.image.getSubimage(x,y,w,h));}
    public static Bitmap createScaledBitmap(Bitmap source,int w,int h,boolean filter){
        Mat rgba=new Mat(),scaled=new Mat();org.opencv.android.Utils.bitmapToMat(source,rgba);
        try{Imgproc.resize(rgba,scaled,new Size(w,h),0,0,filter?Imgproc.INTER_LINEAR:Imgproc.INTER_NEAREST);
            byte[] data=new byte[w*h*4];scaled.get(0,0,data);int[] rgb=new int[w*h];
            for(int i=0;i<rgb.length;i++)rgb[i]=0xff000000|((data[i*4]&255)<<16)|((data[i*4+1]&255)<<8)|(data[i*4+2]&255);
            BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,w,h,rgb,0,w);return new Bitmap(image);
        }finally{rgba.release();scaled.release();}
    }
}
