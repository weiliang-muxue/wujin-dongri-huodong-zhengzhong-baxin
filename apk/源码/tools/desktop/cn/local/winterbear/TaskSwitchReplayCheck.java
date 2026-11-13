package cn.local.winterbear;

import android.graphics.*;
import java.nio.file.*;
import org.opencv.core.Core;
import static cn.local.winterbear.HuntFlow.*;

/** Private recorded pixels + production recognizers/flows. No game input or server acknowledgement. */
public final class TaskSwitchReplayCheck {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    static Bitmap image(Path p){Bitmap b=BitmapFactory.decodeFile(p.toString());require(b!=null,"Missing "+p);return b;}
    public static void main(String[] a)throws Exception{
        System.load(a[0]);Core.setNumThreads(1);Path assets=Path.of(a[1]),fixtures=Path.of(a[2]),layout=Path.of(a[3]);
        try(HuntVision hunt=new HuntVision(p->Files.newInputStream(assets.resolve(p)),(b,r)->"")){
            for(String name:new String[]{"huawei-bear-list.png","xiaomi-list.png"}){
                Bitmap b=image(fixtures.resolve(name));Frame f=hunt.observe(b,Mode.GIANT);b.recycle();
                require(f.page==Page.ALLIANCE&&f.points[Kind.BACK.ordinal()][0]>0,"Cannot leave bear list: "+name);
                for(Mode mode:Mode.values()){
                    HuntFlow flow=new HuntFlow(new Options(mode,8,1,0));flow.start(0);
                    require(flow.next(f,1000).kind==Kind.BACK,"Bear -> "+mode);flow.complete(true,1000);
                    require(flow.phase==Phase.MAP&&flow.submitted==0,"Old activity leaked into new flow");
                }
                System.out.println("PASS bear -> hunt: "+name+" -> BACK");
            }
        }
        for(String name:new String[]{"giant_search.png","giant_detail.png","rally.png","giant_formation.png","energy.png","too-late.png"}){
            Bitmap b=image(fixtures.resolve(name));Config c=new Config(assets,layout,b.getWidth(),b.getHeight());
            try(Matcher bear=new Matcher(c,new CounterOcr())){
                bear.recoverToMap(true);bear.frame(b);BearFlow.Frame f=bear.observe();
                require(f.navigationBack&&!f.map&&f.backX>0&&f.backY>0,"Cannot leave hunt page: "+name);
                MarchFlow flow=new MarchFlow();flow.dry=false;flow.start(0);
                require(flow.next(f,1000).kind==BearFlow.Kind.BACK,"Hunt -> bear must navigate first");
                flow.complete(true,1000);require(flow.submitted==0,"Switch sent troops");
            }finally{b.recycle();}
            System.out.println("PASS hunt -> bear: "+name+" -> BACK");
        }
        // Real supplied map/list pair. Only map digit OCR is annotated here;
        // green buttons, bear labels and countdown glyphs use production vision.
        Bitmap map=image(Path.of(a[5])),list=image(Path.of(a[4]));
        Config config=new Config(assets,layout,map.getWidth(),map.getHeight());
        try(Matcher bear=new Matcher(config,new Matcher.Numbers(){public int read(Bitmap b,Rect r){return 1;}public void close(){}})){
            bear.frame(map);BearFlow.Frame m=bear.observe();require(m.map&&m.shortcutX>0,"Latest Xiaomi map/entry");
            MarchFlow flow=new MarchFlow();flow.dry=false;flow.setPresets(63);
            for(int i=0;i<6;i++){flow.presetX[i]=i*80+80;flow.presetY[i]=300;}flow.start(0);
            flow.next(m,1000);require(flow.next(m,2000).kind==BearFlow.Kind.NAVIGATE,"Map -> red rally entry");flow.complete(true,2000);
            // Recordings may be compressed to another size; preserve screenshot coordinates.
            try(Matcher listReader=new Matcher(new Config(assets,layout,list.getWidth(),list.getHeight()),new CounterOcr())){
                long t=System.nanoTime();listReader.frame(list);BearFlow.Frame l=listReader.observe();
                require(l.rallyList,"Latest recorded rally page");
                require(l.rows.size()>=2&&l.rows.get(0).secondsLeft==41&&l.rows.get(1).secondsLeft==55,"Latest recorded timers "+l.rows.stream().map(r->r.secondsLeft).toList());
                require(flow.next(l,4000).kind==BearFlow.Kind.JOIN,"Latest recording must join the visible bear");
                System.out.printf("PASS latest recording -> JOIN; desktop %.0f ms; timers=%s%n",(System.nanoTime()-t)/1e6,l.rows.stream().map(r->r.secondsLeft).toList());
            }
        }finally{map.recycle();list.recycle();}
        System.out.println("TASK SWITCH REPLAY PASS; annotated map counter; no phone or live-game claim");
    }
}
