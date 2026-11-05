package cn.local.winterbear;

import android.graphics.*;
import java.io.*;
import java.util.Arrays;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import static cn.local.winterbear.HuntFlow.*;

/** Replay the Xiaomi task-return screenshots through the shipped pixel readers. */
public final class HuntReturnRuntimeCheck {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static final String[] NAMES={"xiaomi-rally","xiaomi-returning","xiaomi-returned-toast","xiaomi-returned"};
    static final HuntMarches.Type[][] TYPES={
        {HuntMarches.Type.MEAT,HuntMarches.Type.RETURN,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.RALLY},
        {HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.RETURN},
        null,
        {HuntMarches.Type.MEAT,HuntMarches.Type.COAL,HuntMarches.Type.IRON,HuntMarches.Type.WOOD}};
    /** Original images exercise the full detector; variants focus on the shared
     * panel reader, avoiding unrelated full-screen dialog searches under ART. */
    static Frame rowsOnly(HuntVision vision,Bitmap image){
        double scale=image.getWidth()/540.;Mat rgba=new Mat(),small=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(image,rgba);Imgproc.resize(rgba,small,new Size(540,Math.round(image.getHeight()/scale)),0,0,Imgproc.INTER_AREA);Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
            Frame frame=new Frame();frame.page=Page.MAP;vision.observeMarchPanel(image,scale,gray,frame);return frame;
        }finally{rgba.release();small.release();gray.release();}
    }
    public static void main(String[] args){try{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        FeedbackVision.Assets assets=p->new FileInputStream(new File(args[2],p));
        int checks=0;
        for(int variant=0;variant<8;variant++){
            int[][] sizes={{1080,2400},{1152,2376},{1260,2800},{1344,2772},{1080,2374},{720,1600},{1080,2400},{1080,2400}};
            int[] size=sizes[variant];Frame[] frames=new Frame[4];
            for(int i=0;i<NAMES.length;i++){
                final String name=NAMES[i];
                Bitmap source=BitmapFactory.decodeFile(new File(args[1],name+".jpg").getPath());require(source!=null,name);
                Bitmap image=HuntRuntimeCheck.resized(source,size[0],size[1],false);
                // Short-screen top/bottom composition overlaps at mid-screen.
                // Preserve the complete panel instead of erasing its fifth row.
                double scale=size[0]/(double)source.getWidth();
                new Canvas(image).drawBitmap(source,new Rect(0,560,390,1110),new Rect(0,(int)Math.round(560*scale),(int)Math.round(390*scale),(int)Math.round(1110*scale)),new Paint(Paint.FILTER_BITMAP_FLAG));source.recycle();
                if(variant==6){
                    // Synthetic clock occlusion: the row icons still identify
                    // all four gatherers, so timers are not a release gate.
                    Paint p=new Paint();p.setColor(Color.BLACK);Canvas c=new Canvas(image);
                    for(int y:new int[]{707,799,891,983,1075})c.drawRect(90,y-13,295,y+13,p);
                }
                if(variant==7){
                    // Move only the march panel, keeping the map controls intact.
                    Bitmap panel=Bitmap.createBitmap(image,0,560,390,550);Canvas c=new Canvas(image);Paint p=new Paint();p.setColor(Color.rgb(70,105,160));c.drawRect(0,560,430,1150,p);c.drawBitmap(panel,20,590,null);panel.recycle();
                }
                try(HuntVision vision=new HuntVision(assets,(b,r)->name.equals("xiaomi-rally")?"5/6":"4/6")){
                    Frame f=frames[i]=variant==0?vision.observe(image,Mode.GIANT):rowsOnly(vision,image);
                    require(f.page==Page.MAP&&f.used==(i==0?5:4),name+" map/count variant="+variant);
                    if(TYPES[i]!=null){
                        require(f.marches!=null&&f.marches.length==TYPES[i].length,name+" complete rows variant="+variant);
                        for(int j=0;j<TYPES[i].length;j++)require(f.marches[j].type==TYPES[i][j],name+" row="+j+" type="+f.marches[j].type+" variant="+variant);
                    }
                    checks++;System.out.println("RETURN IMAGE PASS: "+name+" "+size[0]+"x"+size[1]+" variant="+variant+" rows="+(f.marches==null?"occluded":f.marches.length));System.out.flush();
                }finally{image.recycle();}
            }
            for(Mode mode:Mode.values()){
                HuntFlow flow=new HuntFlow(new Options(mode,mode==Mode.GIANT?8:30,1,100));flow.start(0);
                flow.marches.observe(Arrays.copyOf(frames[0].marches,4),0,false);flow.phase=Phase.VERIFY;
                flow.next(frames[0],1000);require(flow.submitted==1&&flow.marches.active()==1,"dispatch not recorded");
                flow.next(frames[1],101000);require(flow.next(frames[1],102000).kind==Kind.WAIT&&flow.marches.active()==1,"must wait for actual hunt return");
                require(flow.next(frames[2],134000).kind==Kind.WAIT,"toast must not cause a click");
                Action next=flow.next(frames[3],145000);
                require(next.kind==Kind.OPEN&&flow.marches.active()==0,mode+" failed to refill after return: "+next.message);
                if(variant==0)require(frames[3].points[Kind.OPEN.ordinal()][0]>0,"missing actual search target");
            }
        }
        System.out.println("ANDROID RETURN PASS: "+checks+" image checks (4 full detector, 28 panel reader); both hunt modes refill after return; total count annotated, row types read from pixels; no gestures");
    }catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
}
