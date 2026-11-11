package android.graphics;
import java.io.*;
import javax.imageio.ImageIO;
public final class BitmapFactory {
    public static Bitmap decodeStream(InputStream input){try{return new Bitmap(ImageIO.read(input));}catch(IOException e){throw new IllegalArgumentException(e);}}
    public static Bitmap decodeFile(String path){try(InputStream input=new FileInputStream(path)){return decodeStream(input);}catch(IOException e){return null;}}
}
