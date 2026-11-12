package cn.local.winterbear;

import android.graphics.*;
import java.nio.file.*;
import org.opencv.core.Core;
import static cn.local.winterbear.HuntFlow.*;

/** Runs detected pages into the real flow, including the seasonal-tab transition. */
public final class SearchTransitionCheck {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        System.load(args[0]);Core.setNumThreads(1);int steps=0;
        for(Mode mode:Mode.values()){
            try(HuntVision vision=new HuntVision(p->Files.newInputStream(Path.of(args[1]).resolve(p)),
                    (b,r)->mode==Mode.GIANT?"8":"30")){
                HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,100));
                flow.start(0);flow.phase=Phase.SEARCH;long now=2000;
                // The submitted recording opened the drawer with Beast selected.
                if(mode==Mode.GIANT){
                    Bitmap image=BitmapFactory.decodeFile(args[3]);Frame frame=vision.observe(image,mode);image.recycle();
                    require(frame.category==Mode.BEAST.ordinal(),"Recording selected category");
                    Action action=flow.next(frame,now);require(action.kind==Kind.CATEGORY,"Recording must choose Giant");
                    int[] point=frame.points[Kind.CATEGORY.ordinal()];require(point[0]>frame.width*.5,"Giant moved right of centre");
                    flow.complete(true,now);now+=2000;steps++;
                    // Resume must observe the new selection, not reuse the old slot.
                    flow.suspend(now,false);flow.resume(now+1000);now+=2000;
                }
                String prefix=mode==Mode.GIANT?"giant":"beast";
                String[] images=mode==Mode.GIANT?new String[]{prefix+"_search",prefix+"_detail","rally",prefix+"_formation",prefix+"_formation"}
                        :new String[]{prefix+"_search",prefix+"_detail",prefix+"_formation",prefix+"_formation"};
                Kind[] actions=mode==Mode.GIANT?new Kind[]{Kind.SEARCH,Kind.TARGET,Kind.LAUNCH,Kind.PRESET,Kind.DEPLOY}
                        :new Kind[]{Kind.SEARCH,Kind.TARGET,Kind.PRESET,Kind.DEPLOY};
                for(int i=0;i<images.length;i++){
                    Bitmap image=BitmapFactory.decodeFile(Path.of(args[2],images[i]+".png").toString());
                    Frame frame=vision.observe(image,mode);image.recycle();Action action=flow.next(frame,now);
                    require(action.kind==actions[i],mode+" "+images[i]+" page="+frame.page+" action="+action.kind);
                    int[] point=action.kind==Kind.PRESET?frame.flags[action.preset]:frame.points[action.kind.ordinal()];
                    require(frame.contains(point[0],point[1]),"Missing actual click target "+action.kind);
                    flow.complete(true,now);now+=2000;steps++;
                    System.out.println("PASS "+mode+" "+images[i]+" -> "+action.kind+" "+point[0]+","+point[1]);
                }
                require(flow.phase==Phase.VERIFY&&flow.submitted==0&&flow.cansUsed==0,"Dispatch awaits game acknowledgement");
            }
        }
        System.out.println("SEARCH TRANSITION PASS: "+steps+" detected steps; annotated level OCR; no gestures/server acknowledgement");
    }
}
