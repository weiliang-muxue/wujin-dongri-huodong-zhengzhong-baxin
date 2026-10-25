package cn.local.bullseye;
import org.junit.Test;
import static org.junit.Assert.*;
public class SessionGuardTest {
    @Test public void resumesOnlyItsPausedSession(){assertTrue(SessionGuard.canResume(4,4,true,true,true));}
    @Test public void rejectsQueuedResumeAfterRestart(){assertFalse(SessionGuard.canResume(4,5,true,true,true));}
    @Test public void rejectsStopAndModeSwitch(){assertFalse(SessionGuard.canResume(4,4,false,true,true));assertFalse(SessionGuard.canResume(4,4,true,true,false));}
    @Test public void repeatedContinueDoesNotResetActiveSession(){assertFalse(SessionGuard.canResume(4,4,true,false,true));}
}
