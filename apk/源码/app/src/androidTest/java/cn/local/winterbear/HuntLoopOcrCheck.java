package cn.local.winterbear;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import java.io.File;
import java.util.concurrent.TimeUnit;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;

/** Actual bundled OCR on the numeric crops exported by the complete-map replay. */
public final class HuntLoopOcrCheck extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int checked=0,withinDeadline=0;
        try(CounterOcr ocr=new CounterOcr()){
            java.lang.reflect.Field field=CounterOcr.class.getDeclaredField("reader");field.setAccessible(true);
            TextRecognizer reader=(TextRecognizer)field.get(ocr);
            File folder=new File(getTargetContext().getFilesDir(),"loop-clocks");
            File[] files=folder.listFiles((dir,name)->name.startsWith("clock-")&&name.endsWith(".png"));
            if(files==null||files.length==0)throw new AssertionError("Missing clock crops");
            for(File file:files){
                int expected=Integer.parseInt(file.getName().split("-")[1]);Bitmap b=BitmapFactory.decodeFile(file.getPath());
                try{
                    // Same crop/resize and bundled reader, but a test-only wait
                    // for software CPU emulation. This measures accuracy, not
                    // the production 1600ms deadline or phone performance.
                    int w=Math.max(240,b.getWidth());
                    Bitmap scaled=Bitmap.createScaledBitmap(b,w,Math.max(48,Math.round(b.getHeight()*w/(float)b.getWidth())),true);
                    String text;
                    try{text=Tasks.await(reader.process(InputImage.fromBitmap(scaled,0)),60,TimeUnit.SECONDS).getText();}
                    finally{if(scaled!=b)scaled.recycle();}
                    int actual=CountdownDigits.clockText(text);
                    if(actual!=expected)throw new AssertionError(file.getName()+" expected="+expected+" OCR="+text);
                    String bounded=ocr.text(b,new Rect(0,0,b.getWidth(),b.getHeight()));
                    if(!bounded.isEmpty()){
                        if(CountdownDigits.clockText(bounded)!=expected)throw new AssertionError("Bounded OCR conflict: "+bounded);
                        withinDeadline++;
                    }
                    checked++;Bundle progress=new Bundle();progress.putString("stream","BUNDLED CLOCK OCR PASS: "+file.getName()+" "+actual+"\n");sendStatus(0,progress);
                }finally{b.recycle();}
            }
            result.putString("stream","BUNDLED CLOCK OCR PASS: "+checked+" actual numeric crops; no annotated OCR answers; software-emulator 1600ms responses="+withinDeadline+"/"+checked+"; accuracy test allows 60s, not a phone latency claim\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
