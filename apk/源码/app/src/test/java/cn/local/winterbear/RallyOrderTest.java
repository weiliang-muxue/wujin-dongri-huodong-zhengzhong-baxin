package cn.local.winterbear;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RallyOrderTest {
    private RallyOrder.Detection detection(int x,int y,boolean available,double confidence) {
        return new RallyOrder.Detection(x,y,800,y+30,available,confidence);
    }
    @Test public void positionTakesPrecedenceOverMatchConfidence() {
        List<BearFlow.Row> rows=RallyOrder.rows(Arrays.asList(
            detection(100,900,true,.99),detection(100,300,true,.89),detection(100,600,true,.95)),20);
        assertEquals(3,rows.size());
        assertEquals(330,rows.get(0).y);assertEquals(630,rows.get(1).y);assertEquals(930,rows.get(2).y);
        for(int i=0;i<3;i++)assertEquals(i,rows.get(i).order);
    }
    @Test public void fullRowsKeepTheirPositionInTheList() {
        List<BearFlow.Row> rows=RallyOrder.rows(Arrays.asList(
            detection(100,300,false,.99),detection(100,600,true,.96)),20);
        assertEquals(2,rows.size());assertFalse(rows.get(0).available);
        assertEquals(1,rows.get(1).order);assertTrue(rows.get(1).available);
    }
    @Test public void overlappingDetectionsDoNotCreateExtraQueueNumbers() {
        List<BearFlow.Row> rows=RallyOrder.rows(Arrays.asList(
            detection(100,300,true,.98),detection(105,306,true,.91),detection(100,600,true,.95)),20);
        assertEquals(2,rows.size());assertEquals(330,rows.get(0).y);assertEquals(630,rows.get(1).y);
        assertEquals(1,rows.get(1).order);
    }
    @Test public void noBearMatchesProduceNoQueues() {
        assertTrue(RallyOrder.rows(Collections.emptyList(),20).isEmpty());
    }
}
