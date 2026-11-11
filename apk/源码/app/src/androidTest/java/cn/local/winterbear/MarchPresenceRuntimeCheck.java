package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import static cn.local.winterbear.HuntFlow.*;

/** Real Xiaomi panel with only its heading occluded; missing is never an empty roster. */
public final class MarchPresenceRuntimeCheck {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}

    private static Bitmap transformed(Bitmap source,float scale,boolean covered){
        Bitmap image=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(image);c.drawColor(Color.BLACK);
        // Same bottom-anchored content zoom as the desktop return fixtures. The
        // 101% case offsets the expansion so the leftmost row icons stay visible.
        int dx=scale>1?(int)Math.ceil(source.getWidth()*(scale-1)/2):0;
        c.translate(source.getWidth()*(1-scale)/2+dx,(source.getHeight()-40)*(1-scale));
        c.scale(scale,scale);c.drawBitmap(source,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));
        if(covered){
            // Source annotation: arrow/"行军" occupy x16..210, y574..640.
            // Keep the visible 4/6 at x300+, all four icons and their clocks intact.
            Paint mask=new Paint();mask.setColor(Color.BLACK);c.drawRect(16,574,210,640,mask);
        }
        return image;
    }

    private static void retained(Frame visible,Frame covered,float scale){
        for(Mode mode:Mode.values())for(HuntMarches.Type type:new HuntMarches.Type[]{HuntMarches.Type.RETURN,HuntMarches.Type.RALLY}){
            HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,0));flow.start(0);
            HuntMarches.Row[] external=Arrays.copyOf(visible.marches,3),owned=visible.marches.clone();
            // RETURN uses the actual fourth row. RALLY explicitly varies only
            // the prior flow state; it is not claimed to come from this image.
            if(type==HuntMarches.Type.RALLY)owned[3]=new HuntMarches.Row(owned[3].icon,1,type);
            require(flow.marches.observe(external,0,false),"External roster "+scale);
            require(flow.marches.observe(owned,1000,true)&&flow.marches.active()==1,"Owned roster "+scale);
            flow.submitted=1;
            for(long time:new long[]{5000,7000}){
                Action action=flow.next(covered,time);
                require(action.kind==Kind.WAIT&&flow.marches.active()==1&&flow.submitted==1&&!flow.pending(),
                        "Covered heading released "+mode+" "+type+" scale="+scale+" action="+action.kind);
            }
        }
    }

    public static void main(String[] args){try{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        Bitmap source=BitmapFactory.decodeFile(new File(args[1],"xiaomi-returning.jpg").getPath());
        require(source!=null&&source.getWidth()==1080&&source.getHeight()==2400,"Expected annotated Xiaomi 1080x2400 returning fixture");
        FeedbackVision.Assets assets=p->new FileInputStream(new File(args[2],p));int checks=0;
        try{
            for(float scale:new float[]{.99f,1f,1.01f}){
                Frame[] frames=new Frame[2];
                for(int variant=0;variant<2;variant++){
                    boolean covered=variant==1;Bitmap image=transformed(source,scale,covered);
                    int[] reads={0};
                    // Only total count is annotated. Header presence and all row
                    // identities are evaluated by production Android/OpenCV readers.
                    try(HuntVision vision=new HuntVision(assets,(bitmap,region)->{reads[0]++;return "4/6";})){
                        Frame f=frames[variant]=HuntReturnRuntimeCheck.rowsOnly(vision,image);
                        require(f.page==Page.MAP,"Panel helper map context");
                        if(covered)require(f.marchPanelAbsent&&f.used==-1&&f.marches==null&&reads[0]==0,"Covered heading became count/rows scale="+scale);
                        else{
                            require(!f.marchPanelAbsent&&f.used==4&&f.marches!=null&&f.marches.length==4,"Visible heading/4 rows scale="+scale);
                            for(int i=0;i<4;i++)require(f.marches[i].type==HuntReturnRuntimeCheck.TYPES[1][i],"Wrong row type scale="+scale+" row="+i);
                        }
                        checks++;System.out.println("MARCH PRESENCE IMAGE PASS: scale="+scale+" heading="+(covered?"covered":"visible")+" used="+f.used);System.out.flush();
                    }finally{image.recycle();}
                }
                retained(frames[0],frames[1],scale);
            }
        }finally{source.recycle();}
        System.out.println("ANDROID MARCH PRESENCE PASS: "+checks+" image checks; 99/100/101% actual Xiaomi panel and heading-only occlusion; owned RETURN/RALLY retained in both modes; count annotated, no gestures");
    }catch(Throwable error){error.printStackTrace(System.out);System.out.flush();System.exit(1);}}
}
