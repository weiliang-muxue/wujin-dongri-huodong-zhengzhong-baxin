package cn.local.winterbear;

import java.util.regex.Pattern;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** ART-only entry point for app_process, independent of launcher / package-manager startup. */
public final class RuntimeCoreCheck {
    public static void main(String[] args){
        System.setProperty("simulationReportDir",args[0]);
        boolean reproduced=false;
        try{Pattern.compile(TroopCount.COUNTER.pattern(),0x100).matcher("179,810/179,810").matches();}
        catch(IllegalArgumentException expected){reproduced=true;System.out.println("OLD BUG REPRODUCED: "+expected);}
        if(!reproduced)throw new AssertionError("Expected Android regex flag failure");
        TroopCount reported=TroopCount.parse("179,810/179,810");
        if(reported==null||reported.selected!=179810||reported.capacity!=179810||!reported.usable())
            throw new AssertionError("Reported formation count failed on Android");
        if(TroopCount.parse("")!=null||TroopCount.parse("5/6")!=null)
            throw new AssertionError("Unknown/march count was accepted as troop count");
        System.out.println("FIX VERIFIED ON ART: 179810/179810 usable; unreadable count stays unknown");
        Result tests=JUnitCore.runClasses(
            cn.local.bullseye.FloatingGuardTest.class,cn.local.bullseye.SessionGuardTest.class,
            ModeGateTest.class,MarchCountTest.class,MarchFlowTest.class,RallyOrderTest.class,
            GestureGateTest.class,MarchSimulationTest.class,LiveMapCheckTest.class,
            TroopCountTest.class,ForegroundGateTest.class);
        for(Failure failure:tests.getFailures())System.out.println(failure.getTrace());
        System.out.println("ART JUNIT: "+tests.getRunCount()+" tests, "+tests.getFailureCount()+" failures");
        if(!tests.wasSuccessful())throw new AssertionError("Android regression failure");
    }
}
