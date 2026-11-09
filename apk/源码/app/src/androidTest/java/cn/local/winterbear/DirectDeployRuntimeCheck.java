package cn.local.winterbear;

import android.graphics.*;
import java.io.*;

/** Actual formation images must drive preset -> deploy with no numeric OCR calls. */
public final class DirectDeployRuntimeCheck {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
    static void hunt(Bitmap image,FeedbackVision.Assets assets){
        HuntVision.Numbers noOcr=(b,r)->{throw new AssertionError("Formation called numeric OCR: "+r);};
        try(HuntVision reader=new HuntVision(assets,noOcr)){
            for(HuntFlow.Mode mode:HuntFlow.Mode.values()){
                HuntFlow.Frame frame=reader.observe(image,mode);
                require(frame.page==HuntFlow.Page.FORMATION,"Hunt formation not recognized");
                int[] tap=frame.points[HuntFlow.Kind.DEPLOY.ordinal()];
                require(tap[0]>image.getWidth()*.55&&tap[1]>image.getHeight()*.90,"Wrong deploy button");
                for(int preset=0;preset<6;preset++){
                    HuntFlow flow=new HuntFlow(new HuntFlow.Options(mode,mode==HuntFlow.Mode.GIANT?8:30,1,0,1<<preset));
                    HuntFlow.Frame map=new HuntFlow.Frame();map.page=HuntFlow.Page.MAP;map.used=0;map.marches=new HuntMarches.Row[0];
                    flow.start(0);flow.next(map,1000);
                    require(flow.next(map,2000).kind==HuntFlow.Kind.OPEN,"Preset selection did not reach search");flow.complete(true,2100);
                    flow.phase=HuntFlow.Phase.FORMATION;
                    HuntFlow.Action selected=flow.next(frame,3100);
                    require(selected.kind==HuntFlow.Kind.PRESET&&selected.preset==preset,"Wrong selected hunt preset");
                    int[] flag=frame.flags[selected.preset];
                    require(flag[0]>0&&flag[0]<image.getWidth()&&flag[1]>0&&flag[1]<image.getHeight(),"Invalid preset coordinates");
                    if(preset>0)require(flag[0]>frame.flags[preset-1][0],"Preset flags out of order");
                    flow.complete(true,3200);
                    require(flow.next(frame,4200).kind==HuntFlow.Kind.DEPLOY,"Troop-free hunt deploy blocked");
                    require(flow.next(frame,4500).kind==HuntFlow.Kind.WAIT,"Repeated pending deploy");flow.complete(true,4600);
                    require(flow.next(frame,6000).kind==HuntFlow.Kind.WAIT&&flow.submitted==0,"Click alone must not count as dispatch");
                }
            }
        }
    }
    static void bear(Bitmap image,File assets,File work)throws Exception{
        Config config=CompatibilityRuntimeCheck.config(assets,work,image.getWidth(),image.getHeight());
        Matcher.Numbers noOcr=new Matcher.Numbers(){
            public int read(Bitmap b,Rect r){throw new AssertionError("Bear formation called numeric OCR: "+r);}
            public void close(){}
        };
        try(Matcher reader=new Matcher(config,noOcr)){
            reader.frame(image);BearFlow.Frame frame=reader.observe();
            require(frame.formation&&frame.deploy,"Bear formation not recognized");
            for(int preset=0;preset<6;preset++){
                MarchFlow flow=MarchFlowTest.flow();flow.setPresets(1<<preset);flow.start(1000);
                for(int i=0;i<6;i++){flow.presetX[i]=config.p.getInt("presetX"+i,0);flow.presetY[i]=config.p.getInt("presetY"+i,0);}
                MarchFlowTest.enter(flow,4);flow.next(MarchFlowTest.list(),3000);flow.complete(true,3100);
                BearFlow.Decision selected=flow.next(frame,4400);
                require(selected.kind==BearFlow.Kind.PRESET&&selected.x==frame.presetX[preset]&&selected.y==frame.presetY[preset],"Wrong selected bear preset");flow.complete(true,4500);
                BearFlow.Decision tap=flow.next(frame,5300);
                require(tap.kind==BearFlow.Kind.DEPLOY&&tap.x==frame.deployX&&tap.y==frame.deployY,"Troop-free bear deploy blocked");
                require(flow.next(frame,5350).kind==BearFlow.Kind.WAIT,"Repeated pending bear deploy");flow.complete(true,5400);
                require(flow.next(frame,6700).kind==BearFlow.Kind.WAIT&&flow.submitted==0,"Unconfirmed bear dispatch counted");
            }
        }
    }
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assets=new File(args[2]),work=new File(args[3]);int checks=0;
        FeedbackVision.Assets provider=p->new FileInputStream(new File(assets,p));
        Bitmap reported=BitmapFactory.decodeFile(new File(fixtures,"reported-deploy.png").getPath());require(reported!=null,"Missing reported image");
        for(int[] size:new int[][]{{1080,2400},{1152,2376},{1260,2800},{1344,2772},{1080,2374},{720,1600}}){
            for(boolean hidden:new boolean[]{false,true}){
                Bitmap image=HuntRuntimeCheck.resized(reported,size[0],size[1],false);
                try{
                    if(hidden){Paint p=new Paint();p.setColor(Color.BLACK);float s=size[0]/540f;new Canvas(image).drawRect(45*s,250*s,190*s,285*s,p);}
                    hunt(image,provider);checks++;System.out.println("DIRECT DEPLOY PASS: reported "+size[0]+"x"+size[1]+" hiddenTroops="+hidden);System.out.flush();
                }finally{image.recycle();}
            }
        }reported.recycle();
        for(String name:new String[]{"beast_formation","video-170","video-820"}){
            Bitmap original=BitmapFactory.decodeFile(new File(fixtures,name+".png").getPath());require(original!=null,name);
            for(int[] size:new int[][]{{1080,2400},{1152,2376}}){
                Bitmap image=HuntRuntimeCheck.resized(original,size[0],size[1],false);
                try{
                    if(name.equals("beast_formation"))hunt(image,provider);else bear(image,assets,new File(work,name+"-"+size[0]));
                    checks++;System.out.println("DIRECT DEPLOY PASS: "+name+" "+size[0]+"x"+size[1]);System.out.flush();
                }finally{image.recycle();}
            }original.recycle();
        }
        System.out.println("ANDROID DIRECT DEPLOY PASS: "+checks+" image checks; all six presets in all three modes; zero formation numeric OCR; no gestures");
    }
}
