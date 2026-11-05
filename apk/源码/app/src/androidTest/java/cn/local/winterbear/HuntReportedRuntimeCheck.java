package cn.local.winterbear;
import android.graphics.*;
import java.io.*;
import static cn.local.winterbear.HuntFlow.*;

/** Regression for the user's actual 4 gathering marches and stuck level-8 target. */
public final class HuntReportedRuntimeCheck {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        FeedbackVision.Assets assets=p->new FileInputStream(new File(args[2],p));int checks=0;
        String[] names=args.length>3?(args[3].equals("maps")?new String[]{"reported-map","map_one"}:new String[]{"map_one"}):new String[]{"reported-map","reported-level","giant_detail","beast_detail","map_one"};
        for(String name:names){
            Bitmap original=BitmapFactory.decodeFile(new File(args[1],name+".png").getPath());require(original!=null,name);
            for(int[] size:new int[][]{{1080,2400},{1152,2376},{1260,2800},{1344,2772},{1080,2374},{720,1600}}){
                Bitmap image=HuntRuntimeCheck.resized(original,size[0],size[1],false);
                if(name.equals("beast_detail")){
                    double s=size[0]/(double)original.getWidth(),mid=(size[1]-original.getHeight()*s)/2;
                    new Canvas(image).drawBitmap(original,new Rect(0,560,original.getWidth(),1320),new Rect(0,(int)Math.round(560*s+mid),size[0],(int)Math.round(1320*s+mid)),new Paint(Paint.FILTER_BITMAP_FLAG));
                }
                try(HuntVision v=new HuntVision(assets,(b,r)->{
                    if(name.equals("reported-map"))return "4/6";
                    if(name.equals("map_one"))return "1/6";
                    throw new AssertionError("Target number must come from real glyphs, not mocked OCR: "+name);
                })){
                    Frame f=(name.equals("reported-map")||name.equals("map_one"))&&size[0]!=1080?HuntReturnRuntimeCheck.rowsOnly(v,image):v.observe(image,name.startsWith("beast")?Mode.BEAST:Mode.GIANT);
                    if(name.equals("map_one")){
                        require(f.page==Page.MAP&&f.marches!=null&&f.marches.length==1,"Actual newly dispatched row "+size[0]+"x"+size[1]);
                        require(f.marches[0].seconds==179||(f.marches[0].seconds<0&&f.marches[0].type==HuntMarches.Type.RALLY),"Actual rally countdown="+f.marches[0].seconds);
                        HuntFlow flow=new HuntFlow(new Options(Mode.GIANT,8,1,0));flow.start(0);
                        flow.marches.observe(new HuntMarches.Row[0],0,false);flow.phase=Phase.VERIFY;
                        flow.next(f,2000);require(flow.submitted==1&&flow.marches.active()==1,"Actual new row not recorded");
                        flow.next(f,3000);require(flow.next(f,4000).kind==Kind.WAIT,"One owned giant should fill quota 1");
                    }else if(name.equals("reported-map")){
                        require(f.page==Page.MAP&&f.used==4&&f.marches!=null&&f.marches.length==4,"4 gathering rows "+size[0]+"x"+size[1]+" page="+f.page+" used="+f.used+" rows="+(f.marches==null?"unreadable":f.marches.length));
                        int[] expected={20268,19144,19115,20543};
                        for(int i=0;i<4;i++){
                            HuntMarches.Row row=f.marches[i];
                            require(row.seconds<0?row.type.resource():Math.abs(row.seconds-expected[i])<=1,"timer "+i+"="+row.seconds);
                            if(row.seconds<0)System.out.println("COUNTDOWN UNKNOWN, RESOURCE ICON CONFIRMED: row="+i+" "+size[0]+"x"+size[1]);
                        }
                        HuntFlow flow=new HuntFlow(new Options(Mode.GIANT,8,1,0));flow.start(0);flow.next(f,1000);
                        require(flow.next(f,2000).kind==Kind.OPEN,"4 gathering rows blocked 1 giant task");
                    }else{
                        int level=name.startsWith("beast")?30:8;require(f.level==level,name+" actual level="+f.level+" "+size[0]+"x"+size[1]);
                        HuntFlow flow=new HuntFlow(new Options(name.startsWith("beast")?Mode.BEAST:Mode.GIANT,level,1,0));flow.start(0);flow.phase=Phase.RESULT;
                        require(flow.next(f,2000).kind==Kind.TARGET,"Verified actual level did not advance");
                        HuntFlow wrong=new HuntFlow(new Options(flow.options.mode,level-1,1,0));wrong.start(0);wrong.phase=Phase.RESULT;
                        require(wrong.next(f,2000).kind==Kind.STOP,"Different level was attacked");
                    }
                    checks++;System.out.println("REPORTED IMAGE PASS: "+name+" "+size[0]+"x"+size[1]);System.out.flush();
                }finally{image.recycle();}
            }original.recycle();
        }
        System.out.println("ANDROID REPORTED PASS: "+checks+" real glyph/row checks; unknown countdown requires a known icon; only total march count annotated; no gestures");
    }
}
