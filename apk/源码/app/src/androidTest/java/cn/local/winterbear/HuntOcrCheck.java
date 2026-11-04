package cn.local.winterbear;
import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import java.io.File;

/** Diagnostic entry point for the actual bundled recognizer, never injects expected OCR text. */
public final class HuntOcrCheck extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();try(CounterOcr ocr=new CounterOcr()){
        Bitmap b=BitmapFactory.decodeFile(new File(getTargetContext().getFilesDir(),"hunt-fixtures/reported-level.png").getAbsolutePath());
        if(b==null)throw new AssertionError("Missing private reported-level fixture");
        StringBuilder log=new StringBuilder();
        for(int i=0;i<3;i++){
            log.append("old full Chinese line: ").append(ocr.text(b,new Rect(400,1738,730,1792))).append('\n');
            log.append("isolated numeric level: ").append(ocr.text(b,new Rect(484,1736,516,1780))).append('\n');
        }
        b.recycle();out.putString("stream",log.toString());finish(Activity.RESULT_OK,out);
    }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}
