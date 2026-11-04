package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;
import static cn.local.winterbear.HuntFlow.*;

public class HuntFlowTest {
    long now=1000;
    HuntFlow create(Mode m,int cap,int cans){HuntFlow f=new HuntFlow(new Options(m,m==Mode.GIANT?8:30,cap,cans,60));f.start(now);return f;}
    Frame map(int n){Frame f=new Frame();f.page=Page.MAP;f.used=n;if(n>=0){f.marches=new HuntMarches.Row[n];for(int i=0;i<n;i++)f.marches[i]=new HuntMarches.Row(i*0x9e3779b97f4a7c15L,3600+i*1800-(int)((now+2000)/1000));}return f;}
    static Frame search(Mode m,int n){Frame f=new Frame();f.page=Page.SEARCH;f.category=m.ordinal();f.level=n;return f;}
    static Frame form(){Frame f=new Frame();f.page=Page.FORMATION;java.util.Arrays.fill(f.presets,true);f.knownTroops=f.validTroops=true;return f;}
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
    @Test public void unrelatedReturnDoesNotFreeTaskQuota(){HuntFlow f=create(Mode.GIANT,1,0);enter(f,4);click(f,search(Mode.GIANT,8),Kind.SEARCH);click(f,page(Page.GIANT,8),Kind.TARGET);click(f,page(Page.RALLY,-1),Kind.LAUNCH);click(f,form(),Kind.PRESET);click(f,form(),Kind.DEPLOY);see(f,map(5));assertEquals(1,f.marches.active());Frame reduced=map(5);reduced.used=4;reduced.marches=java.util.Arrays.copyOfRange(reduced.marches,1,5);see(f,reduced);assertEquals(Kind.WAIT,see(f,reduced).kind);assertEquals(1,f.marches.active());}
    @Test public void invalidMarchCountNeverAssumesFree(){HuntFlow f=create(Mode.BEAST,6,0);assertEquals(Kind.WAIT,see(f,map(-1)).kind);now+=20000;assertEquals(Kind.STOP,see(f,map(-1)).kind);}
    @Test public void staleFrameCannotClick(){HuntFlow f=create(Mode.BEAST,6,0);Frame a=map(0);a.age=4001;assertEquals(Kind.WAIT,see(f,a).kind);assertEquals(Phase.MAP,f.phase);}
    @Test public void levelAdjustmentsAreObserved(){HuntFlow f=create(Mode.GIANT,1,0);enter(f,0);click(f,search(Mode.GIANT,7),Kind.MORE);click(f,search(Mode.GIANT,8),Kind.SEARCH);}
    @Test public void cannotSearchWrongCategory(){HuntFlow f=create(Mode.GIANT,1,0);enter(f,0);click(f,search(Mode.BEAST,8),Kind.CATEGORY);assertEquals(Phase.SEARCH,f.phase);}
    @Test public void stuckLevelPausesInsteadOfClickingForever(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);for(int i=0;i<3;i++)click(f,search(Mode.BEAST,29),Kind.MORE);assertEquals(Kind.STOP,see(f,search(Mode.BEAST,29)).kind);}
    @Test public void wrongTargetLevelNeverAttacked(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);click(f,search(Mode.BEAST,30),Kind.SEARCH);assertEquals(Kind.STOP,see(f,page(Page.BEAST,29)).kind);}
    @Test public void wrongTargetTypeNeverAttacked(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);click(f,search(Mode.BEAST,30),Kind.SEARCH);assertEquals(Kind.WAIT,see(f,page(Page.GIANT,30)).kind);}
    @Test public void unreadableLevelEventuallyPauses(){HuntFlow f=create(Mode.BEAST,1,0);enter(f,0);assertEquals(Kind.WAIT,see(f,search(Mode.BEAST,-1)).kind);now+=20000;assertEquals(Kind.STOP,see(f,search(Mode.BEAST,-1)).kind);}
    @Test public void failedGestureDoesNotAdvance(){HuntFlow f=create(Mode.BEAST,1,0);see(f,map(0));assertEquals(Kind.OPEN,see(f,map(0)).kind);f.complete(false,now);assertFalse(f.running);assertEquals(0,f.submitted);}
    @Test public void emptyTroopsSelectNextPreset(){HuntFlow f=create(Mode.BEAST,1,0);toFormation(f);Frame a=form();a.validTroops=false;see(f,a);Action next=see(f,form());assertEquals(Kind.PRESET,next.kind);assertEquals(1,next.preset);}
    @Test public void unknownTroopsNeverDeploy(){HuntFlow f=create(Mode.BEAST,1,0);toFormation(f);Frame a=form();a.knownTroops=false;assertEquals(Kind.WAIT,see(f,a).kind);}
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
    @Test public void elapsedSessionStops(){HuntFlow f=create(Mode.GIANT,6,0);now+=3600001;assertEquals(Kind.STOP,see(f,map(0)).kind);}
}
