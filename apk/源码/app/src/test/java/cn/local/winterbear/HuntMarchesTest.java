package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;

public class HuntMarchesTest {
    HuntMarches.Row[] rows(long at,int... ids){HuntMarches.Row[] r=new HuntMarches.Row[ids.length];for(int i=0;i<ids.length;i++)r[i]=new HuntMarches.Row(ids[i]*0x9e3779b97f4a7c15L,4000+ids[i]*900-(int)(at/1000));return r;}
    @Test public void otherFourThenOwnOne(){HuntMarches m=new HuntMarches();m.observe(rows(0,1,2,3,4),0,false);assertEquals(0,m.active());assertTrue(m.observe(rows(10000,1,2,3,4,5),10000,true));assertEquals(1,m.active());}
    @Test public void gatheringReturnNeverReleasesOwnedSlot(){HuntMarches m=new HuntMarches();m.observe(rows(0,1,2,3,4),0,false);m.observe(rows(10000,1,2,3,4,5),10000,true);m.observe(rows(20000,2,3,4,5),20000,false);assertEquals(1,m.active());m.observe(rows(30000,2,3,4),30000,false);assertEquals(0,m.active());}
    @Test public void changedRallyToReturnRetainsOwnership(){HuntMarches m=new HuntMarches();m.observe(rows(0,1),0,false);m.observe(rows(1000,1,2),1000,true);m.observe(rows(2000,1,3),2000,false);assertEquals(1,m.active());m.observe(rows(3000,1),3000,false);assertEquals(0,m.active());}
    @Test public void severalSameTimeRowsRemainConservative(){HuntMarches m=new HuntMarches();m.observe(rows(0,1,1),0,false);m.observe(rows(1000,1,1,2),1000,true);m.observe(rows(2000,1,2),2000,false);assertEquals(1,m.active());m.observe(rows(3000,1),3000,false);assertEquals(0,m.active());}
    @Test public void reorderedListDoesNotLoseOwnership(){HuntMarches m=new HuntMarches();m.observe(rows(0,1,2),0,false);m.observe(rows(1000,1,2,3),1000,true);m.observe(rows(2000,3,1,2),2000,false);assertEquals(1,m.active());m.observe(rows(3000,3,2),3000,false);assertEquals(1,m.active());}
    @Test public void unreadableRowsNeverReleaseQuota(){HuntMarches m=new HuntMarches();m.observe(rows(0),0,false);m.observe(rows(1000,1),1000,true);assertFalse(m.observe(null,2000,false));assertEquals(1,m.active());}
    @Test public void clickWithoutNewRowCannotConfirm(){HuntMarches m=new HuntMarches();m.observe(rows(0,1),0,false);assertFalse(m.observe(rows(1000,1),1000,true));assertEquals(0,m.active());}
    @Test public void unrelatedNewRowIsNotAutomaticallyOwned(){HuntMarches m=new HuntMarches();m.observe(rows(0,1),0,false);m.observe(rows(1000,1,2),1000,false);assertEquals(0,m.active());}
    @Test public void allSixTrackedThenReleased(){HuntMarches m=new HuntMarches();m.observe(rows(0),0,false);for(int n=1;n<=6;n++){int[] ids=new int[n];for(int i=0;i<n;i++)ids[i]=i+1;assertTrue(m.observe(rows(n*1000,ids),n*1000,true));assertEquals(n,m.active());}m.observe(rows(10000),10000,false);assertEquals(0,m.active());}
}
