package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Looper;
import cn.local.automation.RecognitionWorker;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual service cancellation and Android Bitmap/OpenCV replay; never sends game input. */
public final class TaskSwitchRuntimeCheck {
    private static Object get(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Bitmap load(String path){Bitmap b=BitmapFactory.decodeFile(path);require(b!=null,"Missing "+path);return b;}
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
    private static void run(String[] args)throws Exception{
        Looper.prepareMainLooper();
        BearService service=new BearService();
        RecognitionWorker worker=(RecognitionWorker)get(service,"worker");
        CountDownLatch entered=new CountDownLatch(1),released=new CountDownLatch(1),next=new CountDownLatch(1);
        AtomicInteger releases=new AtomicInteger();
        try{
            set(service,"generation",7);set(service,"captureId",11);set(service,"capturing",true);
            worker.submit(()->{entered.countDown();while(true){RecognitionWorker.checkCancelled();Thread.yield();}},()->{releases.incrementAndGet();released.countDown();});
            require(entered.await(10,TimeUnit.SECONDS),"Recognition never began");
            service.pause("切换任务");
            require((int)get(service,"generation")==8&&(int)get(service,"captureId")==12&&!(boolean)get(service,"capturing"),"Old screenshot callback not invalidated");
            require(released.await(10,TimeUnit.SECONDS)&&releases.get()==1,"Old frame not released once");
            worker.submit(next::countDown,()->{});
            require(next.await(10,TimeUnit.SECONDS),"Old work blocked new recognition");
            System.out.println("ANDROID SWITCH CANCELLATION PASS: real BearService.pause; frame invalidation, cancellation, single release, next job");System.out.flush();
        }finally{worker.shutdown();worker.awaitTermination(10,TimeUnit.SECONDS);}
        if(args.length==0)return;
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File assets=new File(args[1]);Bitmap list=load(args[2]);
        try{
            Config config=CompatibilityRuntimeCheck.config(assets,new File(args[3]),list.getWidth(),list.getHeight());
            Matcher.Numbers noOcr=new Matcher.Numbers(){public int read(Bitmap b,Rect r){return -1;}public void close(){}};
            try(Matcher bear=new Matcher(config,noOcr)){
                bear.frame(list);BearFlow.Frame f=bear.observe();
                require(f.rallyList&&f.rows.size()>=2,"Latest recording not a bear list");
                require(f.rows.get(0).secondsLeft==41&&f.rows.get(1).secondsLeft==55,"Latest recording timers");
                MarchFlow flow=new MarchFlow();flow.dry=false;flow.setPresets(63);flow.start(0);
                for(int i=0;i<6;i++){flow.presetX[i]=i*80+80;flow.presetY[i]=300;}
                BearFlow.Frame map=new BearFlow.Frame();map.map=true;map.marchUsed=1;map.shortcutX=500;map.shortcutY=600;
                flow.next(map,1000);require(flow.next(map,2000).kind==BearFlow.Kind.NAVIGATE,"Synthetic map setup");flow.complete(true,2000);
                require(flow.next(f,4000).kind==BearFlow.Kind.JOIN,"Latest list does not produce JOIN");
                System.out.println("ANDROID LATEST RECORDING PASS: real Bitmap/OpenCV; bear list, 41/55 seconds, JOIN");System.out.flush();
            }
            try(HuntVision hunt=new HuntVision(p->new FileInputStream(new File(assets,p)),(b,r)->"")){
                HuntFlow.Frame f=hunt.observe(list,HuntFlow.Mode.GIANT);
                require(f.page==HuntFlow.Page.ALLIANCE&&f.points[HuntFlow.Kind.BACK.ordinal()][0]>0,"Bear list cannot hand off to hunt");
                System.out.println("ANDROID SWITCH PIXELS PASS: latest bear list -> hunt BACK; no phone timing or live-game claim");System.out.flush();
            }
        }finally{list.recycle();}
    }
}
