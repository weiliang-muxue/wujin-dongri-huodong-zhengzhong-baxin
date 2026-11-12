package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
import static cn.local.winterbear.HuntFlow.*;

public class HuntFlowTest {
    @Test public void huntTargetsUseTheirCapturedDisplayBounds(){
        Frame frame=new Frame();frame.width=1152;frame.height=2376;
        // A bottom search button remains on the captured display even when the
        // assistant's own floating Activity is only 900 pixels tall.
        assertTrue(frame.contains(576,2250));
        assertTrue(frame.contains(1,1));
        assertTrue(frame.contains(1151,2375));
        assertFalse(frame.contains(0,2250));
        assertFalse(frame.contains(576,0));
        assertFalse(frame.contains(1152,2250));
        assertFalse(frame.contains(576,2376));
        assertFalse(frame.contains(-1,200));
    }
    @Test public void unknownFrameDimensionsCannotAuthorizeATarget(){
        Frame frame=new Frame();assertFalse(frame.contains(10,10));
        frame.width=620;assertFalse(frame.contains(310,1200));
        frame.height=1280;assertTrue(frame.contains(310,1200));
    }
    @Test public void equalOptionsResumeTheSameTaskOnlyWhenEverySettingMatches(){
        Options original=new Options(Mode.GIANT,8,2,100,18);
        assertTrue(original.sameTask(new Options(Mode.GIANT,8,2,100,18)));
        assertFalse(original.sameTask(null));
        for(Options changed:new Options[]{new Options(Mode.BEAST,8,2,100,18),new Options(Mode.GIANT,7,2,100,18),
                new Options(Mode.GIANT,8,1,100,18),new Options(Mode.GIANT,8,2,99,18),new Options(Mode.GIANT,8,2,100,16)})
            assertFalse(original.sameTask(changed));
    }
    @Test public void submittedDeployInterruptedBeforeCallbackWaitsForMap(){
        for(Mode mode:Mode.values()){
            HuntFlow flow=create(mode,1,100);toFormation(flow);
            assertEquals(Kind.DEPLOY,see(flow,form()).kind);flow.suspend(now,true);
            now+=60000;flow.resume(now);
            assertEquals(Phase.VERIFY,flow.phase);assertEquals(Kind.WAIT,see(flow,form()).kind);
            see(flow,map(1));assertEquals(1,flow.submitted);assertEquals(1,flow.marches.active());
        }
    }
    @Test public void unsentDeployCanBeRetriedAfterPause(){
        HuntFlow flow=create(Mode.BEAST,1,0);toFormation(flow);
        assertEquals(Kind.DEPLOY,see(flow,form()).kind);flow.suspend(now,false);flow.resume(now);
        assertEquals(Kind.DEPLOY,see(flow,form()).kind);assertEquals(0,flow.submitted);
    }
    @Test public void interruptedPresetIsReselectedBeforeDeploy(){
        HuntFlow flow=create(Mode.BEAST,1,0);toFormation(flow);flow.phase=Phase.FORMATION;
        assertEquals(Kind.PRESET,see(flow,form()).kind);flow.suspend(now,true);flow.resume(now);
        click(flow,form(),Kind.PRESET);assertEquals(Kind.DEPLOY,see(flow,form()).kind);
    }
    @Test public void openAndSearchReconcileTheFreshPageAfterInterruption(){
        for(Mode mode:Mode.values())for(boolean moved:new boolean[]{false,true}){
            HuntFlow flow=create(mode,1,0);see(flow,map(0));
            assertEquals(Kind.OPEN,see(flow,map(0)).kind);flow.suspend(now,true);flow.resume(now);
            if(moved)click(flow,search(mode,flow.options.level),Kind.SEARCH);
            else{assertEquals(Kind.WAIT,see(flow,map(0)).kind);click(flow,map(0),Kind.OPEN);}

            flow=create(mode,1,0);enter(flow,0);
            assertEquals(Kind.SEARCH,see(flow,search(mode,flow.options.level)).kind);flow.suspend(now,true);flow.resume(now);
            if(moved)assertEquals(Kind.TARGET,see(flow,page(mode==Mode.GIANT?Page.GIANT:Page.BEAST,flow.options.level)).kind);
            else assertEquals(Kind.SEARCH,see(flow,search(mode,flow.options.level)).kind);
        }
    }
    @Test public void targetAndRallyReconcileBothOriginalAndDestinationPages(){
        for(Mode mode:Mode.values())for(boolean moved:new boolean[]{false,true}){
            HuntFlow flow=create(mode,1,0);enter(flow,0);click(flow,search(mode,flow.options.level),Kind.SEARCH);
            Frame target=page(mode==Mode.GIANT?Page.GIANT:Page.BEAST,flow.options.level);
            assertEquals(Kind.TARGET,see(flow,target).kind);flow.suspend(now,true);flow.resume(now);
            if(!moved)assertEquals(Kind.TARGET,see(flow,target).kind);
            else if(mode==Mode.GIANT)assertEquals(Kind.LAUNCH,see(flow,page(Page.RALLY,-1)).kind);
            else assertEquals(Kind.PRESET,see(flow,form()).kind);
        }
        for(boolean moved:new boolean[]{false,true}){
            HuntFlow flow=create(Mode.GIANT,1,0);enter(flow,0);click(flow,search(Mode.GIANT,8),Kind.SEARCH);click(flow,page(Page.GIANT,8),Kind.TARGET);
            assertEquals(Kind.LAUNCH,see(flow,page(Page.RALLY,-1)).kind);flow.suspend(now,true);flow.resume(now);
            assertEquals(moved?Kind.PRESET:Kind.LAUNCH,see(flow,moved?form():page(Page.RALLY,-1)).kind);
        }
    }
    @Test public void unsentCanClickReturnsItsReservation(){
        HuntFlow flow=create(Mode.GIANT,1,4);toFormation(flow);click(flow,form(),Kind.DEPLOY);
        assertEquals(Kind.USE,see(flow,energy(4,0)).kind);assertEquals(1,flow.cansUsed);
        flow.suspend(now,false);assertEquals(0,flow.cansUsed);now+=60000;flow.resume(now);
        for(int can=0;can<4;can++)click(flow,energy(4-can,can*10),Kind.USE);
        click(flow,energy(0,40),Kind.CLOSE);assertEquals(4,flow.cansUsed);
    }
    @Test public void uncertainCanClickRetainsReservationAndAcceptsDelayedResult(){
        HuntFlow flow=create(Mode.BEAST,1,4);toFormation(flow);click(flow,form(),Kind.DEPLOY);
        assertEquals(Kind.USE,see(flow,energy(4,0)).kind);flow.suspend(now,true);now+=60000;flow.resume(now);
        assertEquals(Kind.WAIT,see(flow,energy(4,0)).kind);assertEquals(1,flow.cansUsed);
        click(flow,energy(3,10),Kind.USE);assertEquals(2,flow.cansUsed);
    }
    @Test public void interruptedEnergyCloseRetriesWithoutSpendingAnotherBatch(){
        for(boolean dispatched:new boolean[]{false,true})for(boolean closed:new boolean[]{false,true}){
            HuntFlow flow=create(Mode.GIANT,1,100);toFormation(flow);click(flow,form(),Kind.DEPLOY);
            for(int can=0;can<4;can++)click(flow,energy(8-can,can*10),Kind.USE);
            assertEquals(Kind.CLOSE,see(flow,energy(4,40)).kind);flow.suspend(now,dispatched);flow.resume(now);
            if(closed)assertEquals(Kind.DEPLOY,see(flow,form()).kind);
            else{click(flow,energy(4,40),Kind.CLOSE);assertEquals(Kind.DEPLOY,see(flow,form()).kind);}
            assertEquals(4,flow.cansUsed);
        }
    }
    @Test public void longPausedDispatchGetsFreshVerificationTime(){
        HuntFlow flow=create(Mode.BEAST,1,0);toFormation(flow);click(flow,form(),Kind.DEPLOY);
        flow.suspend(now,false);now+=60000;flow.resume(now);
        Frame incomplete=map(1);incomplete.marches=null;
        assertEquals(Kind.WAIT,see(flow,incomplete).kind);assertTrue(flow.running);
        see(flow,map(1));assertEquals(1,flow.submitted);
    }
    @Test public void bothHuntsCycleOnlySelectedPresetsAcrossReturns(){
        for(Mode mode:Mode.values())for(int selection=1;selection<=63;selection++){
            now=1000;
            HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,6,100,selection));
            flow.start(now);
            int expected=0;
            for(int round=0;round<8;round++){
                while((selection&(1<<expected))==0)expected=(expected+1)%6;
                toFormation(flow);
                Action deploy=see(flow,form());
                assertEquals(Kind.DEPLOY,deploy.kind);assertEquals(expected,deploy.preset);
                flow.complete(true,now);see(flow,map(1));
                assertEquals(round+1,flow.submitted);
                expected=(expected+1)%6;
            }
        }
    }
    @Test public void chosenFormationAndMarchLimitAreIndependent(){
        for(Mode mode:Mode.values()){
            HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,0,1<<5));
            flow.start(now);toFormation(flow);
            Action deploy=see(flow,form());assertEquals(5,deploy.preset);assertEquals(Kind.DEPLOY,deploy.kind);
            flow.complete(true,now);see(flow,map(1));see(flow,map(1));
            assertEquals(Kind.WAIT,see(flow,map(1)).kind);assertEquals(1,flow.marches.active());
            assertEquals(5,flow.nextPreset);
        }
    }
    @Test public void pauseAndStaminaRefillKeepTheChosenFormation(){
        for(Mode mode:Mode.values()){
            HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,4,1<<3));
            flow.start(now);toFormation(flow);flow.stop();flow.resume(now);
            assertEquals(3,flow.nextPreset);click(flow,form(),Kind.DEPLOY);
            for(int can=0;can<4;can++)click(flow,energy(8-can,can*10),Kind.USE);
            click(flow,energy(4,40),Kind.CLOSE);
            Action deploy=see(flow,form());assertEquals(Kind.DEPLOY,deploy.kind);assertEquals(3,deploy.preset);
        }
    }
    @Test public void invalidSelectionCannotStartAHunt(){
        for(Mode mode:Mode.values())for(int selection:new int[]{0,-1,64,65}){
            assertThrows(IllegalArgumentException.class,()->new Options(mode,8,1,0,selection));
        }
    }
    @Test public void switchingTaskFromAnotherHuntPageReturnsBeforeSending(){
        for(Mode mode:Mode.values())for(Page oldPage:new Page[]{Page.SEARCH,Page.GIANT,Page.BEAST,Page.RALLY,Page.FORMATION}){
            HuntFlow flow=create(mode,1,100);
            assertEquals(Kind.BACK,see(flow,page(oldPage,-1)).kind);
            assertEquals(0,flow.submitted);assertEquals(0,flow.cansUsed);
        }
    }
    long now=1000;
    HuntFlow create(Mode m,int cap,int cans){HuntFlow f=new HuntFlow(new Options(m,m==Mode.GIANT?8:30,cap,cans));f.start(now);return f;}
    Frame map(int n){Frame f=new Frame();f.page=Page.MAP;f.used=n;if(n>=0){f.marches=new HuntMarches.Row[n];for(int i=0;i<n;i++)f.marches[i]=new HuntMarches.Row(i*0x9e3779b97f4a7c15L,3600+i*1800-(int)((now+2000)/1000));}return f;}
    static Frame search(Mode m,int n){Frame f=new Frame();f.width=540;f.height=1200;f.page=Page.SEARCH;f.category=m.ordinal();f.level=n;f.point(Kind.CATEGORY,300,950);return f;}
    static Frame form(){Frame f=new Frame();f.page=Page.FORMATION;java.util.Arrays.fill(f.presets,true);return f;}
    static Frame page(Page p,int level){Frame f=new Frame();f.page=p;f.level=level;return f;}
    static Frame energy(int stock,int stamina){Frame f=page(Page.ENERGY,-1);f.stock=stock;f.energy=stamina;return f;}
    Action see(HuntFlow f,Frame frame){now+=2000;return f.next(frame,now);}
    void click(HuntFlow f,Frame frame,Kind expected){Action a=see(f,frame);assertEquals(expected,a.kind);f.complete(true,now);}
    void enter(HuntFlow f,int used){assertEquals(Kind.WAIT,see(f,map(used)).kind);click(f,map(used),Kind.OPEN);}
    void toFormation(HuntFlow f){
        enter(f,0);click(f,search(f.options.mode,f.options.level),Kind.SEARCH);
        click(f,page(f.options.mode==Mode.GIANT?Page.GIANT:Page.BEAST,f.options.level),Kind.TARGET);
        if(f.options.mode==Mode.GIANT)click(f,page(Page.RALLY,-1),Kind.LAUNCH);
        click(f,form(),Kind.PRESET);
    }
    @Test public void giantUsesDefaultRallyThenFirstPreset(){HuntFlow f=create(Mode.GIANT,6,0);toFormation(f);assertEquals(0,f.nextPreset);click(f,form(),Kind.DEPLOY);assertEquals(0,f.submitted);see(f,map(1));assertEquals(1,f.submitted);assertEquals(1,f.nextPreset);}
    @Test public void beastBypassesRallyDialog(){HuntFlow f=create(Mode.BEAST,2,0);toFormation(f);assertEquals(Phase.SEND,f.phase);click(f,form(),Kind.DEPLOY);see(f,map(1));assertEquals(1,f.submitted);}
    @Test public void fourGatheringMarchesDoNotUseActivityQuota(){for(Mode mode:Mode.values()){HuntFlow f=create(mode,1,0);enter(f,4);assertEquals(0,f.marches.active());assertEquals(Phase.SEARCH,f.phase);}}
    @Test public void fullGameSlotsStillWait(){for(Mode mode:Mode.values()){HuntFlow f=create(mode,1,0);see(f,map(6));assertEquals(Kind.WAIT,see(f,map(6)).kind);assertEquals(Kind.WAIT,see(f,map(5)).kind);click(f,map(5),Kind.OPEN);}}
    @Test public void ownTaskReturnReopensSearch(){HuntFlow f=create(Mode.BEAST,1,0);toFormation(f);click(f,form(),Kind.DEPLOY);see(f,map(1));assertEquals(1,f.marches.active());see(f,map(1));assertEquals(Kind.WAIT,see(f,map(1)).kind);see(f,map(0));click(f,map(0),Kind.OPEN);assertEquals(0,f.marches.active());}
    Frame typedMap(HuntMarches.Type... types){Frame f=map(types.length);for(int i=0;i<types.length;i++)f.marches[i]=new HuntMarches.Row(0,-1,types[i]);return f;}
    @Test public void bothModesKeepRefillingWithGatherersAndUnchangedTotal(){
        for(Mode mode:Mode.values()){
            HuntFlow f=create(mode,1,100);
            Frame resources=typedMap(HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.WOOD);
            see(f,resources);click(f,resources,Kind.OPEN);
            for(int round=0;round<8;round++){
                click(f,search(mode,f.options.level),Kind.SEARCH);
                click(f,page(mode==Mode.GIANT?Page.GIANT:Page.BEAST,f.options.level),Kind.TARGET);
                if(mode==Mode.GIANT)click(f,page(Page.RALLY,-1),Kind.LAUNCH);
                Action preset=see(f,form());assertEquals(Kind.PRESET,preset.kind);assertEquals(round%6,preset.preset);f.complete(true,now);
                click(f,form(),Kind.DEPLOY);
                Frame launched=typedMap(HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.WOOD,HuntMarches.Type.RALLY);
                see(f,launched);assertEquals(round+1,f.submitted);assertEquals(1,f.marches.active());
                // A gatherer vanishes first, then this hunt returns while a new
                // resource march takes its place: both later frames remain 4/6.
                Frame returning=typedMap(HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.RETURN);
                see(f,returning);assertEquals(Kind.WAIT,see(f,returning).kind);assertEquals(1,f.marches.active());
                now+=60000;click(f,resources,Kind.OPEN);assertEquals(0,f.marches.active());
            }
        }
    }
    @Test public void unrelatedReturnDoesNotFreeTaskQuota(){HuntFlow f=create(Mode.GIANT,1,0);enter(f,4);click(f,search(Mode.GIANT,8),Kind.SEARCH);click(f,page(Page.GIANT,8),Kind.TARGET);click(f,page(Page.RALLY,-1),Kind.LAUNCH);click(f,form(),Kind.PRESET);click(f,form(),Kind.DEPLOY);see(f,map(5));assertEquals(1,f.marches.active());Frame reduced=map(5);reduced.used=4;reduced.marches=java.util.Arrays.copyOfRange(reduced.marches,1,5);see(f,reduced);assertEquals(Kind.WAIT,see(f,reduced).kind);assertEquals(1,f.marches.active());}
    @Test public void invalidMarchCountNeverAssumesFree(){HuntFlow f=create(Mode.BEAST,6,0);assertEquals(Kind.WAIT,see(f,map(-1)).kind);now+=20000;assertEquals(Kind.STOP,see(f,map(-1)).kind);}
    @Test public void staleFrameCannotClick(){HuntFlow f=create(Mode.BEAST,6,0);Frame a=map(0);a.age=4001;assertEquals(Kind.WAIT,see(f,a).kind);assertEquals(Phase.MAP,f.phase);}
    @Test public void levelAdjustmentsAreObserved(){HuntFlow f=create(Mode.GIANT,1,0);enter(f,0);click(f,search(Mode.GIANT,7),Kind.MORE);click(f,search(Mode.GIANT,8),Kind.SEARCH);}
    @Test public void cannotSearchWrongCategory(){HuntFlow f=create(Mode.GIANT,1,0);enter(f,0);click(f,search(Mode.BEAST,8),Kind.CATEGORY);assertEquals(Phase.SEARCH,f.phase);}
    @Test public void hiddenOrAmbiguousCategoryWaitsWithoutClickingAnotherTab(){
        for(Mode mode:Mode.values()){
            HuntFlow f=create(mode,1,100);enter(f,0);
            Frame unknown=search(mode,8);unknown.category=-1;unknown.point(Kind.CATEGORY,0,0);
            for(int i=0;i<4;i++)assertEquals(Kind.WAIT,see(f,unknown).kind);
            unknown.point(Kind.CATEGORY,420,950);click(f,unknown,Kind.CATEGORY);
            click(f,search(mode,f.options.level),Kind.SEARCH);
            assertEquals(0,f.cansUsed);assertEquals(0,f.submitted);
        }
    }
    @Test public void selectingAnEventNeverAuthorizesSearchEvenIfLevelMatches(){
        for(Mode mode:Mode.values()){
            HuntFlow f=create(mode,1,0);enter(f,0);Frame event=search(mode,f.options.level);event.category=-1;
            click(f,event,Kind.CATEGORY);assertEquals(Phase.SEARCH,f.phase);
            click(f,search(mode,f.options.level),Kind.SEARCH);
        }
    }
    @Test public void stuckLevelPausesInsteadOfClickingForever(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);for(int i=0;i<3;i++)click(f,search(Mode.BEAST,29),Kind.MORE);assertEquals(Kind.STOP,see(f,search(Mode.BEAST,29)).kind);}
    @Test public void wrongTargetLevelNeverAttacked(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);click(f,search(Mode.BEAST,30),Kind.SEARCH);assertEquals(Kind.STOP,see(f,page(Page.BEAST,29)).kind);}
    @Test public void wrongTargetTypeNeverAttacked(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);click(f,search(Mode.BEAST,30),Kind.SEARCH);assertEquals(Kind.WAIT,see(f,page(Page.GIANT,30)).kind);}
    @Test public void unreadableLevelEventuallyPauses(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);assertEquals(Kind.WAIT,see(f,search(Mode.BEAST,-1)).kind);now+=20000;assertEquals(Kind.STOP,see(f,search(Mode.BEAST,-1)).kind);}
    @Test public void failedGestureDoesNotAdvance(){HuntFlow f=create(Mode.BEAST,1,0);see(f,map(0));assertEquals(Kind.OPEN,see(f,map(0)).kind);f.complete(false,now);assertFalse(f.running);assertEquals(0,f.submitted);}
    @Test public void bothModesDeployAfterPresetWithoutTroopData(){for(Mode mode:Mode.values()){HuntFlow f=create(mode,1,0);toFormation(f);Action a=see(f,form());assertEquals(Kind.DEPLOY,a.kind);assertEquals(0,a.preset);assertEquals(0,f.submitted);assertEquals(Kind.WAIT,see(f,form()).kind);}}
    @Test public void errorBeforeDeployIsStillHandledInBothModes(){for(Mode mode:Mode.values()){HuntFlow f=create(mode,1,0);toFormation(f);click(f,page(Page.ERROR,-1),Kind.DISMISS);assertEquals(Phase.RECOVER,f.phase);assertEquals(0,f.submitted);}}
    @Test public void noDispatchCountWithoutMapIncrease(){HuntFlow f=create(Mode.BEAST,1,0);toFormation(f);click(f,form(),Kind.DEPLOY);for(int i=0;i<5;i++)assertEquals(Kind.WAIT,see(f,map(0)).kind);now+=20000;assertEquals(Kind.STOP,see(f,map(0)).kind);assertEquals(0,f.submitted);assertEquals(0,f.nextPreset);}
    @Test public void sixPresetsWrapWithConfirmedDispatches(){HuntFlow f=create(Mode.BEAST,6,0);for(int i=0;i<8;i++){toFormation(f);click(f,form(),Kind.DEPLOY);see(f,map(1));assertEquals((i+1)%6,f.nextPreset);}}
    @Test public void cansDisabledNeverUse(){HuntFlow f=create(Mode.GIANT,1,0);toFormation(f);click(f,form(),Kind.DEPLOY);assertEquals(Kind.STOP,see(f,energy(232,15)).kind);assertEquals(0,f.cansUsed);}
    @Test public void fourCansRequireFourAcknowledgements(){HuntFlow f=create(Mode.GIANT,1,100);toFormation(f);click(f,form(),Kind.DEPLOY);for(int i=0;i<4;i++)click(f,energy(232-i,15+i*10),Kind.USE);click(f,energy(228,55),Kind.CLOSE);assertEquals(4,f.cansUsed);assertEquals(Phase.SEND,f.phase);click(f,form(),Kind.DEPLOY);}
    @Test public void failedEnergyCloseCannotStartAnotherBatch(){HuntFlow f=create(Mode.GIANT,1,100);toFormation(f);click(f,form(),Kind.DEPLOY);for(int i=0;i<4;i++)click(f,energy(232-i,15+i*10),Kind.USE);click(f,energy(228,55),Kind.CLOSE);assertEquals(Kind.WAIT,see(f,energy(228,55)).kind);f.stop();f.resume(now);assertEquals(Kind.WAIT,see(f,energy(228,55)).kind);now+=11000;assertEquals(Kind.STOP,see(f,energy(228,55)).kind);assertEquals(4,f.cansUsed);}
    @Test public void lastTwoCansStopAt100(){HuntFlow f=create(Mode.BEAST,1,100);f.cansUsed=98;toFormation(f);click(f,form(),Kind.DEPLOY);click(f,energy(2,0),Kind.USE);click(f,energy(1,10),Kind.USE);click(f,energy(0,20),Kind.CLOSE);click(f,form(),Kind.DEPLOY);assertEquals(Kind.STOP,see(f,energy(200,0)).kind);assertEquals(100,f.cansUsed);}
    @Test public void stockBelowFourStopsWithoutPartialUse(){HuntFlow f=create(Mode.GIANT,1,100);toFormation(f);click(f,form(),Kind.DEPLOY);assertEquals(Kind.STOP,see(f,energy(3,15)).kind);assertEquals(0,f.cansUsed);}
    @Test public void unknownInventoryNeverUses(){HuntFlow f=create(Mode.BEAST,1,100);toFormation(f);click(f,form(),Kind.DEPLOY);assertEquals(Kind.WAIT,see(f,energy(-1,15)).kind);assertEquals(0,f.cansUsed);}
    @Test public void noRepeatedUseWhenResultUnchanged(){HuntFlow f=create(Mode.GIANT,1,100);toFormation(f);click(f,form(),Kind.DEPLOY);click(f,energy(232,15),Kind.USE);assertEquals(Kind.WAIT,see(f,energy(232,15)).kind);now+=11000;assertEquals(Kind.STOP,see(f,energy(232,15)).kind);assertEquals(1,f.cansUsed);}
    @Test public void pauseResumePreservesCanBudgetAndPendingResult(){HuntFlow f=create(Mode.GIANT,1,100);toFormation(f);click(f,form(),Kind.DEPLOY);click(f,energy(232,15),Kind.USE);f.stop();f.resume(now);assertEquals(Kind.WAIT,see(f,energy(232,15)).kind);assertEquals(1,f.cansUsed);click(f,energy(231,25),Kind.USE);assertEquals(2,f.cansUsed);}
    @Test public void newSessionResetsBudget(){HuntFlow f=create(Mode.GIANT,1,100);f.cansUsed=98;f.stop();assertEquals(0,create(Mode.GIANT,1,100).cansUsed);}
    @Test public void unexpectedEnergyPopupCannotConsume(){HuntFlow f=create(Mode.GIANT,1,100);assertEquals(Kind.STOP,see(f,energy(232,15)).kind);}
    @Test public void knownErrorReturnsMapWithoutClaimingSuccess(){HuntFlow f=create(Mode.GIANT,1,100);toFormation(f);click(f,form(),Kind.DEPLOY);click(f,page(Page.ERROR,-1),Kind.DISMISS);click(f,form(),Kind.BACK);see(f,map(0));assertEquals(0,f.submitted);assertEquals(0,f.nextPreset);}
    @Test public void bothModesContinueBeyondPreviousDurationLimits(){
        for(Mode mode:Mode.values())for(int minutes:new int[]{61,241,2880})for(int cans:new int[]{0,100}){
            HuntFlow f=create(mode,1,cans);now+=minutes*60000L;enter(f,0);assertTrue(f.running);
        }
    }
    @Test public void bothModesCanUseAll100CansAfterMoreThanAnHour(){
        for(Mode mode:Mode.values()){
            HuntFlow f=create(mode,1,100);long started=now;toFormation(f);click(f,form(),Kind.DEPLOY);
            for(int batch=0;batch<25;batch++){
                now+=4*60000L; // Simulated travel/combat consumes the previous batch's stamina.
                for(int can=0;can<4;can++)click(f,energy(200-f.cansUsed,can*10),Kind.USE);
                click(f,energy(200-f.cansUsed,40),Kind.CLOSE);
                assertEquals((batch+1)*4,f.cansUsed);click(f,form(),Kind.DEPLOY);
            }
            assertTrue(now-started>60*60000L);assertTrue(f.running);assertEquals(100,f.cansUsed);
            assertEquals(Kind.STOP,see(f,energy(100,0)).kind);assertEquals(100,f.cansUsed);
        }
    }
    @Test public void longPausePreservesRemainingAllowance(){
        for(Mode mode:Mode.values()){
            HuntFlow f=create(mode,1,100);f.cansUsed=98;f.stop();now+=8*60*60000L;f.resume(now);
            toFormation(f);click(f,form(),Kind.DEPLOY);
            click(f,energy(2,0),Kind.USE);click(f,energy(1,10),Kind.USE);click(f,energy(0,20),Kind.CLOSE);
            assertEquals(100,f.cansUsed);click(f,form(),Kind.DEPLOY);
            assertEquals(Kind.STOP,see(f,energy(200,0)).kind);
        }
    }
    @Test public void longSessionStillStopsForAnUnrecognizedPage(){
        for(Mode mode:Mode.values()){
            HuntFlow f=create(mode,1,100);now+=2*60*60000L;
            assertEquals(Kind.WAIT,see(f,page(Page.UNKNOWN,-1)).kind);now+=20000;
            assertEquals(Kind.STOP,see(f,page(Page.UNKNOWN,-1)).kind);
        }
    }
}
