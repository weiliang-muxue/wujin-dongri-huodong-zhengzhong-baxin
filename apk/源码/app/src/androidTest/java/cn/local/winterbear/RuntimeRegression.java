package cn.local.winterbear;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import java.io.File;
import java.util.regex.Pattern;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** Runs the same flow tests on Android ART, plus real OpenCV / bundled ML Kit on private fixtures.
 * No gestures, accessibility changes, game launches, or network calls are made. */
public final class RuntimeRegression extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void report(String message){Bundle status=new Bundle();status.putString("stream",message+"\n");sendStatus(0,status);}
    @Override public void onStart(){
        Bundle output=new Bundle();
        try{
            System.setProperty("simulationReportDir",new File(getTargetContext().getCacheDir(),"simulation-report").getAbsolutePath());
            boolean reproduced=false;
            try{Pattern.compile(TroopCount.COUNTER.pattern(),0x100).matcher("179,810/179,810").matches();}
            catch(IllegalArgumentException expected){reproduced=true;report("OLD BUG REPRODUCED: "+expected);}
            require(reproduced,"Expected Android's unsupported regex flag failure");
            Result tests=JUnitCore.runClasses(
                cn.local.bullseye.FloatingGuardTest.class,cn.local.bullseye.SessionGuardTest.class,
                ModeGateTest.class,MarchCountTest.class,MarchFlowTest.class,RallyOrderTest.class,
                GestureGateTest.class,MarchSimulationTest.class,LiveMapCheckTest.class,
                TroopCountTest.class,ForegroundGateTest.class);
            for(Failure failure:tests.getFailures())report(failure.getTrace());
            require(tests.wasSuccessful(),"Android JUnit failures: "+tests.getFailureCount());
            report("ART JUNIT PASSED: "+tests.getRunCount());

            File fixtures=new File(getTargetContext().getFilesDir(),"runtime-regression");
            Config config=Config.referenceProbe(getTargetContext());
            int[] seconds={-1,170,250,270,315,358,411,691,718,751,771,780,816,820};
            try(Matcher matcher=new Matcher(config)){
                for(int second:seconds){
                    String name=second<0?"reported-formation.png":"video-"+second+".png";
                    Bitmap bitmap=BitmapFactory.decodeFile(new File(fixtures,name).getAbsolutePath());
                    require(bitmap!=null,"Missing fixture: "+name);
                    BearFlow.Frame frame;
                    try{matcher.frame(bitmap);frame=matcher.observe();}finally{bitmap.recycle();}
                    if(second<0){
                        require(frame.formation&&frame.deploy,"Reported formation page not recognized");
                        require(frame.troops!=null,"Reported troop OCR unreadable");
                        require(frame.troops.selected==179810&&frame.troops.capacity==179810&&frame.troops.usable(),"Reported troop count mismatch");
                    }
                    if(second==250||second==270)require(frame.map&&frame.marchHidden,"Hidden march panel at "+second);
                    if(second==358)require(frame.troopOverflow,"Overflow dialog missed");
                    if(second==691)require(!frame.rallyList,"Detail page mistaken for list");
                    if(second==315||second==411||second==771||second==780||second==816)require(frame.rallyList&&!frame.rows.isEmpty(),"Rally list at "+second);
                    if(second==411||second==771)for(BearFlow.Row row:frame.rows)require(row.y>1000,"Gray first plus at "+second);
                    if(second==816)for(BearFlow.Row row:frame.rows)require(row.y<1800,"Gray third plus");
                    if(second==170||second==718||second==751||second==820){
                        require(frame.deploy,"Deploy button at "+second);
                        for(boolean saved:frame.savedPreset)require(saved,"Missing preset at "+second);
                    }
                    if(second==820)require(frame.troops!=null&&frame.troops.selected==1&&frame.troops.capacity==139400,"1/139400 OCR");
                    report("VISION PASSED: "+name+" troops="+(frame.troops==null?"unknown":frame.troops.selected+"/"+frame.troops.capacity));
                }
            }
            output.putString("stream","PASS: old exception reproduced; "+tests.getRunCount()+" Android tests; 14 actual OpenCV/ML Kit fixtures.\n");
            finish(Activity.RESULT_OK,output);
        }catch(Throwable error){
            output.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,output);
        }
    }
}
