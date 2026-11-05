package cn.local.winterbear;

import android.graphics.*;
import java.io.*;
import static cn.local.winterbear.HuntFlow.*;

/** Metamorphic layout tests: move game content, preserving pixels and button relationships. */
public final class AdaptiveRuntimeCheck {
    static int checks;
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Bitmap load(File dir,String name){Bitmap b=BitmapFactory.decodeFile(new File(dir,name+".png").getPath());require(b!=null,name);return b;}
    static Bitmap shifted(Bitmap original,int dx,int dy){
        Bitmap b=Bitmap.createBitmap(original.getWidth(),original.getHeight()+dy+Math.round(original.getWidth()*32f/540),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.drawColor(Color.BLACK);c.drawBitmap(original,dx,dy,null);return b;
    }
    static void point(int[] before,int[] after,int dx,int dy,String name){
        require(before[0]>0&&after[0]>0,name+" missing point");
        require(Math.abs(after[0]-before[0]-dx)<=6&&Math.abs(after[1]-before[1]-dy)<=6,name+" stale coordinates: "+java.util.Arrays.toString(before)+" -> "+java.util.Arrays.toString(after));
    }
    static HuntVision.Numbers numbers(String name,int dx){return (b,r)->{
        if(name.contains("formation")||name.equals("low_energy"))throw new AssertionError("Formation OCR");
        if(name.equals("energy"))return (r.left-dx)*540./b.getWidth()<100?"232":"15/200";
        if(name.equals("map"))return "";
        if(name.equals("map_one"))return "1/6";
        return name.startsWith("beast")?"30":"8";
    };}
    static void hunt(File fixtures,FeedbackVision.Assets assets)throws Exception{
        String[] names={"map","giant_search","giant_detail","rally","giant_formation","map_one","beast_search","beast_detail","beast_formation","low_energy","energy"};
        for(String name:names){
            Bitmap original=load(fixtures,name);Mode mode=name.startsWith("beast")?Mode.BEAST:Mode.GIANT;
            Frame base;try(HuntVision v=new HuntVision(assets,numbers(name,0))){base=v.observe(original,mode);}
            require(base.page!=Page.UNKNOWN,name+" baseline unknown");
            for(int direction:new int[]{-1,1}){
                int dx=Math.round(original.getWidth()*12f/540)*direction,dy=Math.round(original.getWidth()*48f/540);
                Bitmap moved=shifted(original,dx,dy);
                try(HuntVision v=new HuntVision(assets,numbers(name,dx))){
                    Frame f=v.observe(moved,mode);require(f.page==base.page,name+" shifted page "+f.page+" expected "+base.page);
                    Kind key=f.page==Page.MAP?Kind.OPEN:f.page==Page.SEARCH?Kind.SEARCH:f.page==Page.RALLY?Kind.LAUNCH:f.page==Page.FORMATION?Kind.DEPLOY:f.page==Page.ENERGY?Kind.USE:Kind.TARGET;
                    point(base.points[key.ordinal()],f.points[key.ordinal()],dx,dy,name+" "+key);
                    if(f.page==Page.FORMATION)for(int i=0;i<6;i++){require(f.presets[i],name+" missing preset "+i);point(base.flags[i],f.flags[i],dx,dy,name+" preset "+i);}
                    if(f.page==Page.SEARCH){require(f.level==base.level&&f.category==base.category,name+" search values");point(base.points[Kind.CATEGORY.ordinal()],f.points[Kind.CATEGORY.ordinal()],dx,dy,name+" category");}
                    if(f.page==Page.GIANT||f.page==Page.BEAST)require(f.level==base.level,name+" level");
                    if(f.page==Page.ENERGY){require(f.energy==15&&f.stock==232,"Moved energy OCR region");point(base.points[Kind.CLOSE.ordinal()],f.points[Kind.CLOSE.ordinal()],dx,dy,"close energy");}
                    if(f.page==Page.MAP){require(f.used==base.used,"Moved march header");if(name.equals("map_one"))require(f.marches!=null&&f.marches.length==1,"Moved real march timer");}
                    checks++;System.out.println("ADAPTIVE PASS: "+name+" dx="+dx+" dy="+dy);System.out.flush();
                }finally{moved.recycle();}
            }
            original.recycle();
        }
        Bitmap original=load(fixtures,"giant_detail"),ambiguous=original.copy(Bitmap.Config.ARGB_8888,true);
        try(HuntVision v=new HuntVision(assets,numbers("giant_detail",0))){
            Frame f=v.observe(original,Mode.GIANT);int[] p=f.points[Kind.TARGET.ordinal()];float s=original.getWidth()/540f;
            Rect source=new Rect(p[0]-Math.round(65*s),p[1]-Math.round(17*s),p[0]+Math.round(65*s),p[1]+Math.round(17*s));
            Rect dest=new Rect(source);dest.offset(Math.round(140*s),0);new Canvas(ambiguous).drawBitmap(original,source,dest,null);
            require(v.observe(ambiguous,Mode.GIANT).page!=Page.GIANT,"Duplicate rally action accepted");checks++;
        }finally{original.recycle();ambiguous.recycle();}
    }
    static BearFlow.Frame bear(Bitmap image,File assets,File work)throws Exception{
        Config config=CompatibilityRuntimeCheck.config(assets,work,image.getWidth(),image.getHeight());
        try(Matcher v=new Matcher(config,new Matcher.Numbers(){public int read(Bitmap b,Rect r){return -1;}public void close(){}})){v.frame(image);return v.observe();}
    }
    static void run(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assets=new File(args[2]),work=new File(args[3]);
        if(args.length<5||!args[4].equals("bear"))hunt(fixtures,p->new FileInputStream(new File(assets,p)));
        for(String name:new String[]{"huawei-bear-list","video-315","video-170"}){
            Bitmap source=load(fixtures,name),original=HuntRuntimeCheck.resized(source,1152,2376,false);source.recycle();
            BearFlow.Frame base=bear(original,assets,new File(work,name+"-base"));
            for(int direction:new int[]{-1,1}){
                int dx=24*direction,dy=102;Bitmap moved=shifted(original,dx,dy);
                try{
                    BearFlow.Frame f=bear(moved,assets,new File(work,name+direction));
                    if(name.equals("video-170")){
                        require(f.formation&&f.deploy,"Moved bear formation missing");
                        point(new int[]{base.deployX,base.deployY},new int[]{f.deployX,f.deployY},dx,dy,"bear deploy");
                        require(f.savedPreset[0],"Moved bear preset missing");
                        point(new int[]{base.presetX[0],base.presetY[0]},new int[]{f.presetX[0],f.presetY[0]},dx,dy,"bear preset");
                    }else{
                        require(f.rallyList&&base.rallyList,"Moved bear list missing");
                        BearFlow.Row first=base.rows.stream().filter(r->r.available).findFirst().orElseThrow(()->new AssertionError("Missing baseline green plus"));
                        BearFlow.Row target=f.rows.stream().filter(r->r.available&&Math.abs(r.y-first.y-dy)<10).findFirst().orElseThrow(()->new AssertionError("Missing shifted green plus"));
                        point(new int[]{first.x,first.y},new int[]{target.x,target.y},dx,dy,"bear green plus");require(target.secondsLeft==first.secondsLeft,"Timer no longer paired with its own rally: "+first.secondsLeft+" -> "+target.secondsLeft+" at "+target.x+","+target.y);
                    }
                    checks++;System.out.println("ADAPTIVE PASS: bear "+name+" dx="+dx);System.out.flush();
                }finally{moved.recycle();}
            }original.recycle();
        }
        System.out.println("ANDROID ADAPTIVE PASS: "+checks+" shifted-layout image checks; real matched coordinates; synthetic padding; no gestures");
    }
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
}
