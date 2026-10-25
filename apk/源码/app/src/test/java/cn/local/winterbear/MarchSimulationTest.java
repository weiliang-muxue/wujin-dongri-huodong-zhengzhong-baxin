package cn.local.winterbear;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

/** Counter-driven synthetic sessions; does not claim to execute Android OCR or real gestures. */
public class MarchSimulationTest {
    private void run(int seed,boolean dry,boolean permanentlyFull,boolean report) throws Exception {
        Random random=new Random(seed);MarchFlow flow=MarchFlowTest.flow();flow.dry=dry;
        long now=1000,end=1801000,releaseAt=46000;
        int used=6,screen=0,deployed=0,selected=0,scrolls=0;
        int[] counts=new int[6];List<Integer> sequence=new ArrayList<>();
        while(flow.running){
            // The model varies actual capacity, independently of any fixed 324-second timer.
            // Return at ANY page, including between deploy and list: the original recording did this.
            if(!permanentlyFull&&now>=releaseAt){used=Math.max(0,used-1-random.nextInt(3));releaseAt=now+20000+random.nextInt(60000);}
            BearFlow.Frame frame=screen==0?MarchFlowTest.map(used):screen==1?MarchFlowTest.list():MarchFlowTest.deploy();
            if(screen==1){frame.rows.clear();frame.rows.add(new BearFlow.Row(0,800,500,false));frame.rows.add(new BearFlow.Row(1,800,900,true));}
            BearFlow.Decision action=flow.next(frame,now);
            if(now==end){assertEquals(BearFlow.Kind.PAUSE,action.kind);break;}
            assertNotEquals("Unexpected pause: "+action.message,BearFlow.Kind.PAUSE,action.kind);
            if(action.kind==BearFlow.Kind.WAIT){now=Math.min(end,now+1100+random.nextInt(101));continue;}
            assertFalse(dry);assertFalse(permanentlyFull);
            switch(action.kind){
                case NAVIGATE: assertEquals(0,screen);assertTrue(used<6);screen=1;break;
                case JOIN: assertEquals(1,screen);assertTrue(used<6);assertEquals(900,action.y);screen=2;break;
                case PRESET: selected=(action.x-100)/100;assertTrue(selected>=0&&selected<6);break;
                case DEPLOY:
                    assertTrue("Cannot dispatch when full",used<6);used++;deployed++;counts[selected]++;
                    assertEquals((deployed-1)%6,selected);sequence.add(selected+1);screen=1;break;
                case BACK:screen=0;break;
                case SCROLL:scrolls++;break;
                default:fail(action.kind.toString());
            }
            assertTrue(used>=0&&used<=6);
            flow.complete(true,now+70);now=Math.min(end,now+1550+random.nextInt(101));
        }
        assertEquals(end,now);assertFalse(flow.running);
        if(dry||permanentlyFull){assertEquals(0,deployed);assertEquals(0,scrolls);}
        else {assertTrue("Returns sooner than 324s must be used",deployed>36);assertTrue(deployed-flow.submitted<=1);}
        if(report){
            Path directory=Paths.get(System.getProperty("simulationReportDir"),"live");Files.createDirectories(directory);
            String name=permanentlyFull?"always_full":dry?"dry":"live_capacity";
            String json="{\"scenario\":\""+name+"\",\"virtualSeconds\":1800,\"deployClicks\":"+deployed+",\"submissionsReturnedToList\":"+flow.submitted+",\"byPreset\":"+Arrays.toString(counts)+",\"overCapacity\":0,\"realOcrTested\":false,\"realDeviceTested\":false}";
            Files.write(directory.resolve(name+".json"),json.getBytes(StandardCharsets.UTF_8));
            System.out.println("LIVE SIM "+name+": 1800s; deployed="+deployed+"; submitted="+flow.submitted+"; own="+Arrays.toString(counts));
        }
    }
    @Test public void liveCapacityThirtyMinutes() throws Exception{run(1,false,false,true);}
    @Test public void fullForThirtyMinutesNeverDispatches() throws Exception{run(2,false,true,true);}
    @Test public void dryRunForThirtyMinutesNeverClicks() throws Exception{run(3,true,false,true);}
    @Test public void variedReturnTimesAcrossThirtySessions() throws Exception{for(int i=0;i<30;i++)run(100+i,false,false,false);System.out.println("LIVE SIM 30 varied-capacity sessions passed");}
}
