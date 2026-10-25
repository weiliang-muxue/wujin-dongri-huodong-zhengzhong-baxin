package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
import static cn.local.winterbear.BearFlow.Kind;

public class MarchFlowTest {
    static void atVerify(MarchFlow f){enter(f,3);f.next(list(),3000);f.complete(true,3100);f.next(deploy(),4400);f.complete(true,4500);f.next(deploy(),5300);f.complete(true,5400);}
    static BearFlow.Frame overflow(){BearFlow.Frame e=new BearFlow.Frame();e.troopOverflow=true;e.dialogOkX=550;e.dialogOkY=1500;return e;}
    @Test public void persistentOverflowDoesNotSkipSeveralPresets(){MarchFlow f=flow();atVerify(f);assertEquals(Kind.NAVIGATE,f.next(overflow(),6700).kind);f.complete(true,6800);assertEquals(Kind.WAIT,f.next(overflow(),8100).kind);assertEquals(200,f.next(deploy(),9200).x);}
    @Test public void cursorIsReservedBeforePauseAfterDeploy(){MarchFlow f=flow();atVerify(f);assertEquals(1,f.nextPreset);f.stop();f.start(7000);assertEquals(1,f.nextPreset);}
    @Test public void returningFromDetailHasBoundedBackAttempts(){MarchFlow f=flow();enter(f,3);f.next(list(),3000);f.complete(true,3100);BearFlow.Frame detail=list();detail.rallyList=false;f.next(detail,4400);for(int i=0;i<6;i++){assertEquals(Kind.BACK,f.next(detail,5500+i*1200).kind);f.complete(true,5600+i*1200);}assertEquals(Kind.PAUSE,f.next(detail,14000).kind);}
    @Test public void stuckOverflowRetriesOnceThenStops(){MarchFlow f=flow();atVerify(f);f.next(overflow(),6700);f.complete(true,6800);assertEquals(Kind.NAVIGATE,f.next(overflow(),9800).kind);f.complete(true,9900);assertEquals(Kind.WAIT,f.next(overflow(),11000).kind);assertEquals(Kind.PAUSE,f.next(overflow(),14700).kind);assertEquals(1,f.nextPreset);}
    @Test public void failedDeployGestureDoesNotReserveNextPreset(){MarchFlow f=flow();enter(f,3);f.next(list(),3000);f.complete(true,3100);f.next(deploy(),4400);f.complete(true,4500);assertEquals(Kind.DEPLOY,f.next(deploy(),5300).kind);f.complete(false,5400);assertFalse(f.running);assertEquals(0,f.nextPreset);}
    @Test public void failedRowStaysExcludedAfterAnotherJoin(){MarchFlow f=flow();enter(f,3);f.next(list(),3000);f.complete(true,3100);f.next(list(),9200);BearFlow.Frame other=list();other.rows.add(new BearFlow.Row(1,900,1400));assertEquals(1400,f.next(other,10000).y);f.complete(true,10100);f.next(deploy(),11200);f.complete(true,11300);f.next(deploy(),12200);f.complete(true,12300);f.next(other,13400);assertEquals(1400,f.next(other,14500).y);}
    @Test public void unexpectedMapRechecksBeforeContinuing(){for(MarchFlow.Phase p:new MarchFlow.Phase[]{MarchFlow.Phase.FIND,MarchFlow.Phase.FORMATION,MarchFlow.Phase.SEND}){MarchFlow f=flow();enter(f,3);f.phase=p;assertEquals(Kind.WAIT,f.next(map(6),3000).kind);assertEquals(MarchFlow.Phase.MAP,f.phase);assertEquals(Kind.WAIT,f.next(map(6),4000).kind);assertEquals(Kind.WAIT,f.next(map(6),5000).kind);}}
    static MarchFlow flow(){MarchFlow f=new MarchFlow();f.dry=false;for(int i=0;i<6;i++){f.enabled[i]=true;f.presetX[i]=100+i*100;f.presetY[i]=150;}f.start(1000);return f;}
    static BearFlow.Frame map(int used){BearFlow.Frame f=new BearFlow.Frame();f.map=true;f.marchUsed=used;f.shortcutX=950;f.shortcutY=800;return f;}
    static BearFlow.Frame hidden(){BearFlow.Frame f=map(-1);f.marchHidden=true;return f;}
    static BearFlow.Frame list(){BearFlow.Frame f=new BearFlow.Frame();f.header=true;f.rallyList=true;f.backX=60;f.backY=300;f.rows.add(new BearFlow.Row(0,900,700));return f;}
    static BearFlow.Frame deploy(){BearFlow.Frame f=new BearFlow.Frame();f.formation=true;f.deploy=true;f.deployX=800;f.deployY=2200;f.backX=60;f.backY=300;f.troops=new TroopCount(152810,152810);java.util.Arrays.fill(f.savedPreset,true);return f;}
    static void enter(MarchFlow f,int used){assertEquals(Kind.WAIT,f.next(map(used),1000).kind);assertEquals(Kind.NAVIGATE,f.next(map(used),1600).kind);f.complete(true,1700);}
    static void sendFromList(MarchFlow f,long t,int preset){
        assertEquals(Kind.JOIN,f.next(list(),t).kind);f.complete(true,t+100);
        BearFlow.Decision select=f.next(deploy(),t+1400);assertEquals(Kind.PRESET,select.kind);assertEquals(100+preset*100,select.x);f.complete(true,t+1500);
        assertEquals(Kind.DEPLOY,f.next(deploy(),t+2300).kind);f.complete(true,t+2400);
        assertEquals(Kind.WAIT,f.next(list(),t+3700).kind);
    }
    @Test public void zeroThroughFiveAllowAfterTwoReadings(){for(int n=0;n<6;n++){MarchFlow f=flow();enter(f,n);}}
    @Test public void fullCapacityNeverClicks(){MarchFlow f=flow();for(int t=1000;t<30000;t+=1000)assertEquals(Kind.WAIT,f.next(map(6),t).kind);}
    @Test public void mapWithoutMarchBarAllowsAttemptWithoutExpand(){MarchFlow f=flow();assertEquals(Kind.WAIT,f.next(hidden(),1000).kind);assertEquals(Kind.NAVIGATE,f.next(hidden(),1600).kind);}
    @Test public void unreadableVisibleCounterDoesNotBecomeHidden(){MarchFlow f=flow();assertEquals(Kind.WAIT,f.next(map(-1),1000).kind);assertEquals(Kind.PAUSE,f.next(map(-1),16000).kind);}
    @Test public void otherScreensWithoutMarchBarNeverCountAsMap(){MarchFlow f=flow();BearFlow.Frame x=new BearFlow.Frame();assertEquals(Kind.WAIT,f.next(x,1000).kind);assertEquals(Kind.PAUSE,f.next(x,17000).kind);}
    @Test public void changingReadingNeedsNewConfirmation(){MarchFlow f=flow();f.next(map(4),1000);assertEquals(Kind.WAIT,f.next(map(5),1600).kind);assertEquals(Kind.NAVIGATE,f.next(map(5),2200).kind);}
    @Test public void unknownResetsCountEvidence(){MarchFlow f=flow();f.next(map(5),1000);f.next(map(-1),1600);assertEquals(Kind.WAIT,f.next(map(5),2200).kind);}
    @Test public void returningTroopAllowsImmediateRecheck(){MarchFlow f=flow();f.next(map(6),1000);f.next(map(6),1600);assertEquals(Kind.WAIT,f.next(map(5),2200).kind);assertEquals(Kind.NAVIGATE,f.next(map(5),2800).kind);}
    @Test public void consecutiveRalliesStayInListAndAdvancePresets(){MarchFlow f=flow();enter(f,3);sendFromList(f,3000,0);assertEquals(MarchFlow.Phase.FIND,f.phase);sendFromList(f,8000,1);assertEquals(MarchFlow.Phase.FIND,f.phase);sendFromList(f,13000,2);assertEquals(MarchFlow.Phase.RETURN_MAP,f.phase);assertEquals(3,f.submitted);assertEquals(3,f.nextPreset);}
    @Test public void onlyOneFreeSlotReturnsAfterOneAttempt(){MarchFlow f=flow();enter(f,5);sendFromList(f,3000,0);assertEquals(MarchFlow.Phase.RETURN_MAP,f.phase);assertEquals(Kind.BACK,f.next(list(),8000).kind);}
    @Test public void simultaneousReturnDoesNotPauseOrRepeatSamePreset(){MarchFlow f=flow();enter(f,5);sendFromList(f,3000,0);f.next(list(),8000);f.complete(true,8100);f.next(map(5),9400);f.next(map(5),10500);assertEquals(Kind.NAVIGATE,f.next(map(5),11100).kind);assertEquals(1,f.nextPreset);}
    @Test public void sixWrapsToOne(){MarchFlow f=flow();f.nextPreset=5;enter(f,4);sendFromList(f,3000,5);sendFromList(f,8000,0);assertEquals(1,f.nextPreset);}
    @Test public void disabledPresetsAreSkipped(){MarchFlow f=flow();f.enabled[0]=false;enter(f,5);sendFromList(f,3000,1);}
    @Test public void missingPresetMarkerWaitsBrieflyThenPauses(){MarchFlow f=flow();enter(f,5);f.next(list(),3000);f.complete(true,3100);BearFlow.Frame d=deploy();d.savedPreset[0]=false;assertEquals(Kind.WAIT,f.next(d,4400).kind);assertEquals(Kind.PAUSE,f.next(d,10400).kind);}
    @Test public void transientMissingPresetDoesNotStopTheSession(){MarchFlow f=flow();enter(f,5);f.next(list(),3000);f.complete(true,3100);BearFlow.Frame d=deploy();d.savedPreset[0]=false;assertEquals(Kind.WAIT,f.next(d,4400).kind);assertEquals(Kind.PRESET,f.next(deploy(),5500).kind);assertTrue(f.running);}
    @Test public void readableFullCounterOverridesMissingAnchor(){MarchFlow f=flow();BearFlow.Frame m=hidden();m.marchUsed=6;for(int t=1000;t<10000;t+=1000)assertEquals(Kind.WAIT,f.next(m,t).kind);assertEquals(MarchFlow.Phase.MAP,f.phase);}
    @Test public void readableCounterLimitsBatchEvenWhenAnchorMissing(){MarchFlow f=flow();BearFlow.Frame m=hidden();m.marchUsed=5;f.next(m,1000);assertEquals(Kind.NAVIGATE,f.next(m,1600).kind);f.complete(true,1700);sendFromList(f,3000,0);assertEquals(MarchFlow.Phase.RETURN_MAP,f.phase);}
    @Test public void canJoinEighthOtherRally(){MarchFlow f=flow();enter(f,5);BearFlow.Frame s=list();s.rows.clear();for(int i=0;i<8;i++)s.rows.add(new BearFlow.Row(i,800,400+i*100,i==7));assertEquals(1100,f.next(s,3000).y);}
    @Test public void staleCapacityReturnsBeforeNewJoin(){MarchFlow f=flow();enter(f,5);assertEquals(Kind.WAIT,f.next(list(),92000).kind);assertEquals(Kind.BACK,f.next(list(),93300).kind);}
    @Test public void capacityBecomingFullCancelsNavigation(){MarchFlow f=flow();enter(f,5);assertEquals(Kind.WAIT,f.next(map(6),3000).kind);assertEquals(MarchFlow.Phase.MAP,f.phase);}
    @Test public void absentShortcutWaits(){MarchFlow f=flow();BearFlow.Frame m=map(5);m.shortcutX=0;f.next(m,1000);assertEquals(Kind.WAIT,f.next(m,1600).kind);assertTrue(f.running);}
    @Test public void allianceFallbackSequence(){MarchFlow f=flow();BearFlow.Frame m=map(5);m.shortcutX=0;m.allianceX=800;m.allianceY=2300;f.next(m,1000);assertEquals(800,f.next(m,1600).x);f.complete(true,1700);BearFlow.Frame w=new BearFlow.Frame();w.warX=400;w.warY=700;assertEquals(400,f.next(w,3000).x);f.complete(true,3100);BearFlow.Frame tab=new BearFlow.Frame();tab.rallyX=200;tab.rallyY=400;assertEquals(200,f.next(tab,4400).x);f.complete(true,4500);assertEquals(Kind.JOIN,f.next(list(),5800).kind);}
    @Test public void cityUsesWildernessButton(){MarchFlow f=flow();BearFlow.Frame c=new BearFlow.Frame();c.worldX=980;c.worldY=2350;assertEquals(980,f.next(c,1000).x);}
    @Test public void nativeAutoJoinDoesNotBlockBear(){MarchFlow f=flow();enter(f,3);BearFlow.Frame s=list();s.autoJoin=true;assertEquals(Kind.JOIN,f.next(s,3000).kind);}
    @Test public void dryModeNeverClicksEvenWithoutMarchBar(){MarchFlow f=flow();f.dry=true;for(int t=1000;t<20000;t+=1000){assertEquals(Kind.WAIT,f.next(hidden(),t).kind);assertEquals(Kind.WAIT,f.next(list(),t).kind);}assertFalse(f.pending());}
    @Test public void failedGestureDoesNotAdvance(){MarchFlow f=flow();enter(f,5);f.next(list(),3000);f.complete(false,3100);assertFalse(f.running);assertEquals(0,f.nextPreset);}
    @Test public void lateCallbackCannotResume(){MarchFlow f=flow();enter(f,5);f.next(list(),3000);f.stop();f.complete(true,3100);assertFalse(f.running);assertFalse(f.pending());}
    @Test public void conflictingAnchorsStop(){MarchFlow f=flow();BearFlow.Frame s=map(5);s.header=true;assertEquals(Kind.PAUSE,f.next(s,1000).kind);}
    @Test public void deadlineStopsEveryPhase(){for(MarchFlow.Phase p:MarchFlow.Phase.values()){MarchFlow f=flow();f.phase=p;assertEquals(Kind.PAUSE,f.next(map(5),1801000).kind);assertFalse(f.running);}}
    @Test public void startsInListByCheckingMapOnce(){MarchFlow f=flow();assertEquals(Kind.WAIT,f.next(list(),1000).kind);assertEquals(Kind.BACK,f.next(list(),2200).kind);}
    @Test public void departingRallyDetailReturnsToListInsteadOfPausing(){MarchFlow f=flow();enter(f,4);f.next(list(),3000);f.complete(true,3100);BearFlow.Frame d=list();d.rallyList=false;assertEquals(Kind.WAIT,f.next(d,4400).kind);assertEquals(Kind.BACK,f.next(d,5500).kind);f.complete(true,5600);BearFlow.Frame s=list();s.rows.add(new BearFlow.Row(1,900,1400));assertEquals(1400,f.next(s,7000).y);}
    @Test public void fullRaceSkipsRowWithoutAdvancingPreset(){MarchFlow f=flow();enter(f,4);f.next(list(),3000);f.complete(true,3100);assertEquals(Kind.WAIT,f.next(list(),9200).kind);BearFlow.Frame s=list();s.rows.add(new BearFlow.Row(1,900,1400));assertEquals(1400,f.next(s,10000).y);assertEquals(0,f.nextPreset);}
    @Test public void zeroTroopsUsesNextPresetWithoutDeploy(){MarchFlow f=flow();enter(f,4);f.next(list(),3000);f.complete(true,3100);f.next(deploy(),4400);f.complete(true,4500);BearFlow.Frame d=deploy();d.troops=new TroopCount(0,150000);assertEquals(Kind.WAIT,f.next(d,5300).kind);assertEquals(200,f.next(d,6400).x);}
    @Test public void tinyButExplicitlySavedArmyIsAllowed(){MarchFlow f=flow();enter(f,4);f.next(list(),3000);f.complete(true,3100);f.next(deploy(),4400);f.complete(true,4500);BearFlow.Frame d=deploy();d.troops=new TroopCount(1,139400);assertEquals(Kind.DEPLOY,f.next(d,5300).kind);}
    @Test public void overflowBeforeSendSkipsPreset(){MarchFlow f=flow();enter(f,4);f.next(list(),3000);f.complete(true,3100);f.next(deploy(),4400);f.complete(true,4500);BearFlow.Frame d=deploy();d.troops=new TroopCount(160000,150000);assertEquals(Kind.WAIT,f.next(d,5300).kind);assertEquals(200,f.next(d,6400).x);}
    @Test public void recordedOverflowDialogIsDismissedAndNextPresetSelected(){MarchFlow f=flow();enter(f,5);f.next(list(),3000);f.complete(true,3100);f.next(deploy(),4400);f.complete(true,4500);f.next(deploy(),5300);f.complete(true,5400);BearFlow.Frame e=new BearFlow.Frame();e.troopOverflow=true;e.dialogOkX=550;e.dialogOkY=1500;assertEquals(550,f.next(e,6700).x);f.complete(true,6800);assertEquals(200,f.next(deploy(),8100).x);assertEquals(0,f.submitted);}
    @Test public void unknownPopupIsNotDismissed(){MarchFlow f=flow();enter(f,5);f.next(list(),3000);f.complete(true,3100);BearFlow.Frame popup=new BearFlow.Frame();assertEquals(Kind.WAIT,f.next(popup,5000).kind);assertEquals(Kind.PAUSE,f.next(popup,21000).kind);}
    @Test public void uncertainDeployNeverClicksDeployTwice(){MarchFlow f=flow();enter(f,3);f.next(list(),3000);f.complete(true,3100);f.next(deploy(),4400);f.complete(true,4500);f.next(deploy(),5300);f.complete(true,5400);assertEquals(Kind.WAIT,f.next(deploy(),13000).kind);assertEquals(Kind.BACK,f.next(deploy(),14000).kind);assertEquals(1,f.nextPreset);}
    @Test public void recordedFailuresRecoverInOneContinuousSession(){
        MarchFlow f=flow();f.next(hidden(),1000);assertEquals(Kind.NAVIGATE,f.next(hidden(),1600).kind);f.complete(true,1700);
        BearFlow.Frame s=list();s.autoJoin=true;s.rows.clear();s.rows.add(new BearFlow.Row(0,900,700,false));s.rows.add(new BearFlow.Row(1,900,1400));
        assertEquals(1400,f.next(s,3000).y);f.complete(true,3100);
        BearFlow.Frame d=deploy();d.savedPreset[0]=false;assertEquals(Kind.WAIT,f.next(d,4400).kind);
        assertEquals(100,f.next(deploy(),5500).x);f.complete(true,5600);
        d=deploy();d.troops=new TroopCount(0,139400);assertEquals(Kind.WAIT,f.next(d,6400).kind);
        assertEquals(200,f.next(deploy(),7500).x);f.complete(true,7600);
        assertEquals(Kind.DEPLOY,f.next(deploy(),8400).kind);f.complete(true,8500);
        assertEquals(Kind.NAVIGATE,f.next(overflow(),9800).kind);f.complete(true,9900);
        assertEquals(Kind.WAIT,f.next(overflow(),11000).kind);
        assertEquals(300,f.next(deploy(),12100).x);f.complete(true,12200);
        assertEquals(Kind.DEPLOY,f.next(deploy(),13000).kind);f.complete(true,13100);
        assertEquals(Kind.WAIT,f.next(s,14400).kind);assertEquals(MarchFlow.Phase.FIND,f.phase);
        assertEquals(Kind.JOIN,f.next(s,15500).kind);f.complete(true,15600);
        BearFlow.Frame detail=list();detail.rallyList=false;
        assertEquals(Kind.WAIT,f.next(detail,16900).kind);assertEquals(Kind.BACK,f.next(detail,18000).kind);f.complete(true,18100);
        s.rows.add(new BearFlow.Row(2,900,2100));assertEquals(2100,f.next(s,19400).y);f.complete(true,19500);
        assertEquals(400,f.next(deploy(),20800).x);f.complete(true,20900);
        assertEquals(Kind.DEPLOY,f.next(deploy(),21700).kind);f.complete(true,21800);
        assertEquals(Kind.WAIT,f.next(s,23100).kind);
        assertTrue(f.running);assertEquals(MarchFlow.Phase.FIND,f.phase);assertEquals(4,f.nextPreset);assertEquals(2,f.submitted);
    }
}
