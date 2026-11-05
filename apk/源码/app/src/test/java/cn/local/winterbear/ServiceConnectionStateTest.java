package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
import static cn.local.winterbear.ServiceConnectionState.State.*;
public class ServiceConnectionStateTest {
    @Test public void enabledButDisconnectedIsNotReportedAsDisabled(){assertEquals(CONNECTING,ServiceConnectionState.of(false,true));}
    @Test public void connectionArrivingLaterBecomesReady(){assertEquals(CONNECTING,ServiceConnectionState.of(false,true));assertEquals(READY,ServiceConnectionState.of(true,true));}
    @Test public void disconnectedServiceKeepsUsersEnabledSetting(){assertEquals(READY,ServiceConnectionState.of(true,true));assertEquals(CONNECTING,ServiceConnectionState.of(false,true));}
    @Test public void missingSettingIsNotMistakenForDenial(){assertEquals(UNKNOWN,ServiceConnectionState.of(false,null));assertEquals(READY,ServiceConnectionState.of(true,null));}
    @Test public void disabledSwitchRequiresUserAction(){assertEquals(DISABLED,ServiceConnectionState.of(false,false));}
}
