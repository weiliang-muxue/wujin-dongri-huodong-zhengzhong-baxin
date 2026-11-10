package cn.local.winterbear;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForegroundWindowsTest {
    private static final String OWN="cn.local.winterbear",GAME="com.tencent.mm";
    private ForegroundWindows.Window app(String name,int layer){return new ForegroundWindows.Window(name,layer,true,false,false,true);}
    private ForegroundWindows.Window overlay(String name,int layer,boolean own,boolean active){return new ForegroundWindows.Window(name,layer,false,own,active,false);}
    private String read(String root,ForegroundWindows.Window... windows){return ForegroundWindows.select(OWN,root,Arrays.asList(windows));}
    @Test public void touchingOurControlsDoesNotHideTheGame(){assertEquals(GAME,read(OWN,app(GAME,1),overlay(OWN,4,true,true)));}
    @Test public void temporarilyMissingRootUsesObservedGameWindow(){assertEquals(GAME,read("",app(GAME,1),overlay(OWN,4,true,true)));}
    @Test public void normalGameRootStillWorks(){assertEquals(GAME,read(GAME,app(GAME,1)));}
    @Test public void actualAppSwitchIsObservedEvenWithOurControlsActive(){assertEquals("com.android.settings",read(OWN,app(GAME,1),app("com.android.settings",2),overlay(OWN,4,true,true)));}
    @Test public void assistantSettingsWindowDoesNotCountAsOurOverlay(){assertEquals(OWN,read(OWN,app(GAME,1),app(OWN,2)));}
    @Test public void notificationShadeBlocksInput(){assertEquals("",read("com.android.systemui",app(GAME,1),overlay("com.android.systemui",5,false,true)));}
    @Test public void anotherActiveOverlayIsNotIgnored(){assertEquals("",read(OWN,app(GAME,1),overlay("other.assistant",5,false,true)));}
    @Test public void inactiveSystemDecorDoesNotBlockGame(){assertEquals(GAME,read(GAME,app(GAME,1),overlay("com.android.systemui",5,false,false)));}
    @Test public void staleRootAndNewAppWindowCannotAllowOldGameClick(){assertEquals("",read(GAME,app("other.app",3)));}
    @Test public void missingTopWindowOwnerDoesNotFallBackToGame(){assertEquals("",read(GAME,app(GAME,1),app("",3)));}
    @Test public void unavailableWindowListUsesCurrentRootWithoutCaching(){assertEquals(GAME,read(GAME));assertEquals("",read(""));}
    @Test public void ownRootWithoutApplicationWindowsIsTemporarilyUnknown(){
        assertEquals("",read(OWN));
        assertEquals("",read(OWN,overlay(OWN,4,true,true)));
    }
    @Test public void explicitAssistantActivityRemainsAnAppSwitch(){
        assertEquals(OWN,read(OWN,app(OWN,2)));
        assertEquals(OWN,read(OWN,app(OWN,2),overlay(OWN,4,true,true)));
    }
    @Test public void unavailableWindowListStillReportsAnExplicitDifferentRoot(){
        assertEquals("com.android.settings",read("com.android.settings"));
    }
}
