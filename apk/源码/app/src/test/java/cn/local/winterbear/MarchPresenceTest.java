package cn.local.winterbear;

import org.junit.Test;
import static org.junit.Assert.*;
import static cn.local.winterbear.HuntFlow.*;
import static cn.local.winterbear.HuntMarches.Type.*;

public class MarchPresenceTest {
    private HuntFlow flow(Mode mode){
        HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,0));
        flow.start(0);return flow;
    }
    private Frame absent(){Frame frame=new Frame();frame.page=Page.MAP;frame.marchPanelAbsent=true;return frame;}
    private HuntMarches.Row row(int seconds,HuntMarches.Type type){return new HuntMarches.Row(1,seconds,type);}
    private void own(HuntFlow flow,HuntMarches.Row... rows){
        assertTrue(flow.marches.observe(new HuntMarches.Row[0],0,false));
        assertTrue(flow.marches.observe(rows,1000,true));
        flow.submitted=1;assertEquals(1,flow.marches.active());
    }

    @Test public void noPanelAtFreshStartCanSearchWithoutInventingACount(){
        for(Mode mode:Mode.values()){
            HuntFlow flow=flow(mode);Frame frame=absent();
            assertEquals(Kind.WAIT,flow.next(frame,1000).kind);
            assertEquals(Kind.OPEN,flow.next(frame,2000).kind);
            assertEquals(-1,frame.used);assertNull(frame.marches);assertEquals(0,flow.marches.active());
        }
    }
    @Test public void coveredTitleCannotReleaseARallyEvenAfterItsCountdown(){
        for(Mode mode:Mode.values()){
            HuntFlow flow=flow(mode);own(flow,row(1,RALLY));
            assertEquals(Kind.WAIT,flow.next(absent(),5000).kind);
            assertEquals(Kind.WAIT,flow.next(absent(),7000).kind);
            assertEquals(1,flow.marches.active());assertEquals(1,flow.submitted);
        }
    }
    @Test public void finalReturnReopensSearchOnlyAfterExpiryAndFreshConfirmation(){
        for(Mode mode:Mode.values()){
            HuntFlow flow=flow(mode);own(flow,row(10,RETURN));
            assertEquals(Kind.WAIT,flow.next(absent(),11000).kind); // rounded clock still has one second of margin
            assertEquals(Kind.WAIT,flow.next(absent(),12000).kind);
            assertEquals(1,flow.marches.active());
            assertEquals(Kind.OPEN,flow.next(absent(),13000).kind);
            assertEquals(0,flow.marches.active());assertEquals(1,flow.submitted);
        }
    }
    @Test public void missingPanelBeforeReturnDeadlineDoesNotPauseOrRelease(){
        HuntFlow flow=flow(Mode.GIANT);own(flow,row(60,RETURN));
        for(long now=2000;now<=62000;now+=2000){
            assertEquals(Kind.WAIT,flow.next(absent(),now).kind);
            assertTrue(flow.running);assertEquals(1,flow.marches.active());
        }
        assertEquals(Kind.OPEN,flow.next(absent(),64000).kind);
    }
    @Test public void returningTaskWithGatherersStillNeedsTheVisibleRoster(){
        HuntFlow flow=flow(Mode.GIANT);
        HuntMarches.Row gatherer=row(30000,WOOD);
        assertTrue(flow.marches.observe(new HuntMarches.Row[]{gatherer},0,false));
        assertTrue(flow.marches.observe(new HuntMarches.Row[]{gatherer,row(1,RETURN)},1000,true));
        assertEquals(Kind.WAIT,flow.next(absent(),5000).kind);
        assertEquals(Kind.WAIT,flow.next(absent(),7000).kind);assertEquals(1,flow.marches.active());
        Frame returned=new Frame();returned.page=Page.MAP;returned.used=1;returned.marches=new HuntMarches.Row[]{gatherer};
        assertEquals(Kind.WAIT,flow.next(returned,9000).kind);
        assertEquals(Kind.OPEN,flow.next(returned,11000).kind);assertEquals(0,flow.marches.active());
    }
    @Test public void unknownReturnClockCannotProveArrival(){
        HuntFlow flow=flow(Mode.BEAST);own(flow,row(-1,RETURN));
        assertEquals(Kind.WAIT,flow.next(absent(),100000).kind);
        assertEquals(Kind.WAIT,flow.next(absent(),102000).kind);assertEquals(1,flow.marches.active());
    }
    @Test public void repeatedCaptureCannotSupplyTwoAbsenceConfirmations(){
        HuntFlow flow=flow(Mode.BEAST);own(flow,row(1,RETURN));
        assertEquals(Kind.WAIT,flow.next(absent(),4000).kind);
        Frame repeated=absent();repeated.age=1000;
        assertEquals(Kind.WAIT,flow.next(repeated,5000).kind);assertEquals(1,flow.marches.active());
        assertEquals(Kind.OPEN,flow.next(absent(),6000).kind);
    }
    @Test public void visibleUnrecognizedPanelInterruptsAbsenceConfirmation(){
        HuntFlow flow=flow(Mode.GIANT);own(flow,row(1,RETURN));
        assertEquals(Kind.WAIT,flow.next(absent(),4000).kind);
        Frame unknown=new Frame();unknown.page=Page.MAP;
        assertEquals(Kind.WAIT,flow.next(unknown,5000).kind);
        assertEquals(Kind.WAIT,flow.next(absent(),6000).kind);assertEquals(1,flow.marches.active());
        assertEquals(Kind.OPEN,flow.next(absent(),7000).kind);
    }
    @Test public void staleCaptureAndResumeRequireNewAbsenceConfirmation(){
        HuntFlow flow=flow(Mode.GIANT);own(flow,row(1,RETURN));
        assertEquals(Kind.WAIT,flow.next(absent(),4000).kind);
        Frame stale=absent();stale.age=4001;assertEquals(Kind.WAIT,flow.next(stale,5000).kind);
        assertEquals(Kind.WAIT,flow.next(absent(),6000).kind);
        flow.stop();flow.resume(7000);
        assertEquals(Kind.WAIT,flow.next(absent(),8000).kind);
        assertEquals(Kind.OPEN,flow.next(absent(),9000).kind);
    }
    @Test public void missingPanelNeverConfirmsADispatch(){
        for(Mode mode:Mode.values()){
            HuntFlow flow=flow(mode);flow.marches.observe(new HuntMarches.Row[0],0,false);flow.phase=Phase.VERIFY;
            assertEquals(Kind.WAIT,flow.next(absent(),2000).kind);
            assertEquals(Kind.WAIT,flow.next(absent(),4000).kind);
            assertEquals(0,flow.submitted);assertEquals(0,flow.marches.active());assertEquals(Phase.VERIFY,flow.phase);
        }
    }
    @Test public void observedExternalMarchesDoNotBecomeEmptyOnTitleFailure(){
        HuntFlow flow=flow(Mode.BEAST);
        flow.marches.observe(new HuntMarches.Row[]{row(30000,MEAT)},0,false);
        assertEquals(Kind.WAIT,flow.next(absent(),2000).kind);
        assertEquals(Kind.WAIT,flow.next(absent(),4000).kind);
        assertEquals(0,flow.submitted);assertEquals(Phase.MAP,flow.phase);
    }
}
