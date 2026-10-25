package cn.local.winterbear;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveMapCheckTest {
    @Test public void readableFullCountDoesNotConfirmHiddenBarSample(){LiveMapCheck c=new LiveMapCheck();assertFalse(c.accept("game",1080,2400,true,6,true,1000,1100));assertFalse(c.accept("game",1080,2400,true,-1,true,1600,1700));assertTrue(c.accept("game",1080,2400,true,-1,true,2200,2300));}
    @Test public void hiddenMarchBarStillValidatesTwoRealMapFrames(){LiveMapCheck c=new LiveMapCheck();assertFalse(c.accept("game",1080,2400,true,-1,true,1000,1100));assertTrue(c.accept("game",1080,2400,true,-1,true,1600,1700));}
    @Test public void hiddenFlagWithoutMapCannotValidate(){LiveMapCheck c=new LiveMapCheck();assertFalse(c.accept("game",1080,2400,false,-1,true,1000,1100));assertFalse(c.accept("game",1080,2400,false,-1,true,1600,1700));}
    private boolean read(LiveMapCheck c,String app,int w,int used,long at){return c.accept(app,w,2400,true,used,at,at+100);}
    @Test public void allValidCountsNeedTwoRealReadings(){
        for(int used=0;used<=6;used++){
            LiveMapCheck c=new LiveMapCheck();assertFalse(read(c,"game",1080,used,1000));assertTrue(read(c,"game",1080,used,1600));
        }
    }
    @Test public void unknownCountResetsEarlierEvidence(){LiveMapCheck c=new LiveMapCheck();read(c,"game",1080,5,1000);assertFalse(read(c,"game",1080,-1,1600));assertFalse(read(c,"game",1080,5,2200));}
    @Test public void switchingAppsRequiresTwoNewReadings(){LiveMapCheck c=new LiveMapCheck();read(c,"a",1080,5,1000);assertFalse(read(c,"b",1080,5,1600));assertTrue(read(c,"b",1080,5,2200));}
    @Test public void changingDimensionsRequiresTwoNewReadings(){LiveMapCheck c=new LiveMapCheck();read(c,"game",1080,5,1000);assertFalse(read(c,"game",720,5,1600));assertTrue(read(c,"game",720,5,2200));}
    @Test public void readingsMustBeSeparated(){LiveMapCheck c=new LiveMapCheck();read(c,"game",1080,5,1000);assertFalse(read(c,"game",1080,5,1200));assertTrue(read(c,"game",1080,5,1600));}
    @Test public void staleScreenshotCannotValidate(){LiveMapCheck c=new LiveMapCheck();read(c,"game",1080,5,1000);assertFalse(c.accept("game",1080,2400,true,5,1600,6001));assertFalse(read(c,"game",1080,5,7000));}
    @Test public void numbersWithoutMapAnchorCannotValidate(){LiveMapCheck c=new LiveMapCheck();read(c,"game",1080,5,1000);assertFalse(c.accept("game",1080,2400,false,5,1600,1700));assertFalse(read(c,"game",1080,5,2200));}
    @Test public void changingCountRequiresNewConfirmation(){LiveMapCheck c=new LiveMapCheck();read(c,"game",1080,5,1000);assertFalse(read(c,"game",1080,6,1600));assertTrue(read(c,"game",1080,6,2200));}
    @Test public void longGapCannotReuseFirstReading(){LiveMapCheck c=new LiveMapCheck();read(c,"game",1080,5,1000);assertFalse(read(c,"game",1080,5,8000));assertTrue(read(c,"game",1080,5,8600));}
    @Test public void missingForegroundAppNeverValidates(){LiveMapCheck c=new LiveMapCheck();assertFalse(read(c,"",1080,5,1000));assertFalse(read(c,"",1080,5,1600));assertFalse(read(c,null,1080,5,2200));}
}
