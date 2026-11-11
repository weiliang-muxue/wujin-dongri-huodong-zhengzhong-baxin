package cn.local.winterbear;

import android.graphics.*;
import java.io.*;
import static cn.local.winterbear.HuntFlow.*;

/** Private November Huawei screenshots/recording frames, never bundled in the APK. */
public final class HuaweiRuntimeCheck {
    static int checks;
    static int first=0,last=20;
    static boolean selected(int index){return index>=first&&index<last;}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Bitmap load(File root,String name){Bitmap b=BitmapFactory.decodeFile(new File(root,name+".png").getPath());require(b!=null,name);return b;}
    static void blank(Bitmap b,float l,float t,float r,float bottom){Paint p=new Paint();p.setColor(Color.BLACK);new Canvas(b).drawRect(l,t,r,bottom,p);}
    static Frame observe(Bitmap b,FeedbackVision.Assets assets,Mode mode){
        try(HuntVision v=new HuntVision(assets,(image,region)->{throw new AssertionError("Unexpected numeric OCR: "+region);})){
            return v.observe(b,mode);
        }
    }
    static void detail(Bitmap b,FeedbackVision.Assets assets,String name){
        Frame f=observe(b,assets,Mode.GIANT);
        require(f.page==Page.GIANT&&f.level==8,name+" target="+f.page+" level="+f.level);
        HuntFlow flow=new HuntFlow(new Options(Mode.GIANT,8,1,0));flow.start(0);flow.phase=Phase.RESULT;
        require(flow.next(f,1000).kind==Kind.TARGET,name+" did not click rally");
        require(flow.next(f,1100).kind==Kind.WAIT,name+" duplicate rally");
        HuntFlow wrong=new HuntFlow(new Options(Mode.GIANT,7,1,0));wrong.start(0);wrong.phase=Phase.RESULT;
        require(wrong.next(f,1000).kind==Kind.STOP,name+" wrong level accepted");
        checks++;System.out.println("HUAWEI IMAGE PASS: "+name);System.out.flush();
    }
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assetDir=new File(args[2]);FeedbackVision.Assets assets=p->new FileInputStream(new File(assetDir,p));
        if(args.length>4&&args[4].equals("bear")){bearList(fixtures,assetDir,new File(args[3]));System.out.println("ANDROID HUAWEI BEAR PASS: 1");return;}
        if(args.length>4)first=Integer.parseInt(args[4]);
        if(args.length>5)last=Integer.parseInt(args[5]);
        require(first>=0&&first<last&&last<=20,"Invalid Huawei test slice");
        Bitmap original=load(fixtures,"huawei-giant");
        if(selected(0))detail(original,assets,"original giant, covered Search label");
        // Preserve the whole bottom target card when changing screen aspect ratio.
        int index=1;
        for(int[] size:new int[][]{{1080,2400},{1344,2772},{1080,2374},{1260,2800},{720,1600}}){
            if(!selected(index++))continue;
            Bitmap b=HuntRuntimeCheck.resized(original,size[0],size[1],false);
            try{detail(b,assets,"giant synthetic "+size[0]+"x"+size[1]);}finally{b.recycle();}
        }
        Bitmap missing=original.copy(Bitmap.Config.ARGB_8888,true);
        float s=missing.getWidth()/540f;
        // Search may be entirely hidden; it is not a target-card landmark.
        blank(missing,225*s,missing.getHeight()-64*s,315*s,missing.getHeight()-25*s);
        if(selected(6))detail(missing,assets,"Search entirely covered");missing.recycle();
        index=7;
        for(boolean removeLevel:new boolean[]{true,false}){
            if(!selected(index++))continue;
            Bitmap b=original.copy(Bitmap.Config.ARGB_8888,true);
            if(removeLevel)blank(b,200*s,815*s,240*s,841*s);
            else blank(b,190*s,985*s,350*s,1010*s);
            Frame f=observe(b,assets,Mode.GIANT);require(f.page!=Page.GIANT,"Incomplete target card accepted: level="+removeLevel);checks++;b.recycle();
        }
        original.recycle();
        Bitmap video=load(fixtures,"huawei-giant-video");if(selected(9))detail(video,assets,"giant recording frame (620px)");video.recycle();
        Bitmap formation=load(fixtures,"huawei-formation");
        index=10;
        for(int[] size:new int[][]{{1152,2376},{1080,2400},{1344,2772},{1080,2374},{1260,2800},{720,1600}}){
            if(!selected(index++))continue;
            Bitmap b=HuntRuntimeCheck.resized(formation,size[0],size[1],false);
            try{
                // Reuse the already observed BEAST frame; the helper still
                // checks both modes and all six preset -> deploy transitions.
                Frame f=DirectDeployRuntimeCheck.hunt(b,assets);
                for(int i=0;i<6;i++)require(f.presets[i],"Renamed preset "+(i+1)+" missing");
                checks++;System.out.println("HUAWEI IMAGE PASS: renamed presets "+size[0]+"x"+size[1]);System.out.flush();
            }finally{b.recycle();}
        }
        // Visible troop figures and user-editable names must not be prerequisites.
        Bitmap noNames=formation.copy(Bitmap.Config.ARGB_8888,true);s=noNames.getWidth()/540f;
        blank(noNames,40*s,250*s,200*s,285*s);blank(noNames,15*s,219*s,450*s,233*s);
        if(selected(16)){DirectDeployRuntimeCheck.hunt(noNames,assets);checks++;}noNames.recycle();
        Bitmap noFlags=formation.copy(Bitmap.Config.ARGB_8888,true);blank(noFlags,10*s,185*s,450*s,240*s);
        if(selected(17)){require(observe(noFlags,assets,Mode.BEAST).page!=Page.FORMATION,"Unidentified presets accepted");checks++;}noFlags.recycle();formation.recycle();
        Bitmap map=load(fixtures,"huawei-map-video");
        try{if(selected(18))try(HuntVision v=new HuntVision(assets,(b,r)->"")){
            Frame f=v.observe(map,Mode.BEAST);require(f.page==Page.MAP&&f.used==0,"Recording map not recognized");
            HuntFlow flow=new HuntFlow(new Options(Mode.BEAST,30,1,0));flow.start(0);flow.next(f,1000);
            require(flow.next(f,2000).kind==Kind.OPEN,"Map did not open search");checks++;
        }}finally{map.recycle();}
        if(selected(19))bearList(fixtures,assetDir,new File(args[3]));
        require(checks==last-first,"Huawei slice incomplete");
        System.out.println("ANDROID HUAWEI RANGE PASS: "+first+".."+last+" checks="+checks);
        System.out.println("ANDROID HUAWEI PASS: "+checks+" image checks; renamed presets; covered Search; actual level glyphs; no gestures");
    }
    static void bearList(File fixtures,File assetDir,File work)throws Exception{
        Config config=CompatibilityRuntimeCheck.config(assetDir,work,1152,2376);
        BearFlow.Frame bear=CompatibilityRuntimeCheck.read(config,new File(fixtures,"huawei-bear-list.png"),-1,false);
        if(bear.rows.isEmpty()){
            Bitmap image=load(fixtures,"huawei-bear-list");
            try(Matcher matcher=new Matcher(config,new Matcher.Numbers(){public int read(Bitmap b,Rect r){return -1;}public void close(){}})){
                matcher.frame(image);
                for(Matcher.Hit hit:matcher.find("bear_label",new Rect(0,380,518,2233),8)){
                    System.out.println("BEAR LABEL: "+hit.x+","+hit.y+" "+hit.width+"x"+hit.height+" confidence="+hit.confidence);
                    Matcher.Hit join=matcher.joinBeside(new Rect(hit.x-hit.width/2,hit.y-hit.height/2,hit.x+hit.width/2,hit.y+hit.height/2));
                    System.out.println("BEAR JOIN: "+(join==null?"missing":join.x+","+join.y+" confidence="+join.confidence));
                }
            }finally{image.recycle();}
        }
        require(bear.rallyList&&!bear.rows.isEmpty(),"Reported bear list missing: header="+bear.header+" list="+bear.rallyList+" rows="+bear.rows.size()+" deploy="+bear.deploy+" formation="+bear.formation);
        require(bear.rows.stream().anyMatch(r->r.available&&r.y<1000),"Visible green plus missing");
        require(bear.rows.stream().noneMatch(r->r.available&&r.y>1100&&r.y<1700),"Full grey plus accepted");
        require(bear.rows.stream().filter(r->r.available&&r.y<1000).allMatch(r->r.secondsLeft<0),"Clipped timer must not inherit the next rally's time");checks++;
    }
}
