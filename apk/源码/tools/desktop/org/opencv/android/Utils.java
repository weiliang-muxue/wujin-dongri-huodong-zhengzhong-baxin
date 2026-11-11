package org.opencv.android;
import android.graphics.Bitmap;
import org.opencv.core.*;
public final class Utils {
    public static void bitmapToMat(Bitmap image,Mat target){
        int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
        byte[] rgba=new byte[w*h*4];for(int i=0;i<pixels.length;i++){int rgb=pixels[i];rgba[i*4]=(byte)(rgb>>16);rgba[i*4+1]=(byte)(rgb>>8);rgba[i*4+2]=(byte)rgb;rgba[i*4+3]=(byte)255;}
        target.create(h,w,CvType.CV_8UC4);target.put(0,0,rgba);
    }
}
