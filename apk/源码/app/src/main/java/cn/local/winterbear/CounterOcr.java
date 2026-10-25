package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.TimeUnit;

/** Bundled Latin model reads only the calibrated x/6 rectangle; never uploads screenshots. */
final class CounterOcr implements AutoCloseable {
    private final TextRecognizer reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    int read(Bitmap screenshot,Rect region){
        return MarchCount.used(text(screenshot,region));
    }
    TroopCount troops(Bitmap screenshot,Rect region){return TroopCount.parse(text(screenshot,region));}
    private String text(Bitmap screenshot,Rect region){
        if(region.width()<12||region.height()<12||region.left<0||region.top<0||region.right>screenshot.getWidth()||region.bottom>screenshot.getHeight())return "";
        Bitmap crop=Bitmap.createBitmap(screenshot,region.left,region.top,region.width(),region.height());
        int w=Math.max(240,crop.getWidth());
        Bitmap scaled=Bitmap.createScaledBitmap(crop,w,Math.max(48,Math.round(crop.getHeight()*w/(float)crop.getWidth())),true);
        if(crop!=scaled&&crop!=screenshot)crop.recycle();
        boolean submitted=false;
        try {
            Task<Text> task=reader.process(InputImage.fromBitmap(scaled,0));
            task.addOnCompleteListener(Runnable::run,t->{if(scaled!=screenshot)scaled.recycle();});
            submitted=true;
            return Tasks.await(task,1600,TimeUnit.MILLISECONDS).getText();
        }catch(Exception e){return "";}
        finally{if(!submitted&&scaled!=screenshot&&!scaled.isRecycled())scaled.recycle();}
    }
    @Override public void close(){reader.close();}
}
