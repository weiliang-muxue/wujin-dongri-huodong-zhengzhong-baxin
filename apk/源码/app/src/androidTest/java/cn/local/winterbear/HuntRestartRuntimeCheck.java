package cn.local.winterbear;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.TextView;
import cn.local.automation.VisualLocator;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import static cn.local.winterbear.HuntFlow.*;

/** Replays the restart recording and actual controller lifecycle without sending game input. */
public final class HuntRestartRuntimeCheck {
    private static int controllerChecks,imageChecks;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object get(Object target,String name)throws Exception{
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private static void set(Object target,String name,Object value)throws Exception{
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private static View text(View root,String label){
        if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                View found=text(group.getChildAt(i),label);if(found!=null)return found;
            }
        }
        return null;
    }
    private static void controllers()throws Exception{
        Looper.prepareMainLooper();
        Class<?> threadClass=Class.forName("android.app.ActivityThread");
        Object thread=threadClass.getMethod("systemMain").invoke(null);
        Context system=(Context)threadClass.getMethod("getSystemContext").invoke(thread);
        Class<?> managerType=Class.forName("android.view.IWindowManager");
        Object manager=Proxy.newProxyInstance(managerType.getClassLoader(),new Class[]{managerType},(proxy,method,values)->{
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==int.class)return 0;
            return null;
        });
        Field managerField=Class.forName("android.view.WindowManagerGlobal").getDeclaredField("sWindowManagerService");
        managerField.setAccessible(true);managerField.set(null,manager);
        int[] updates={0};
        WindowManager windows=(WindowManager)Proxy.newProxyInstance(WindowManager.class.getClassLoader(),new Class[]{WindowManager.class},(proxy,method,values)->{
            if(method.getName().endsWith("WindowMetrics"))return new WindowMetrics(new Rect(0,0,620,900),WindowInsets.CONSUMED);
            if(method.getName().equals("updateViewLayout"))updates[0]++;
            return null;
        });
        Context context=new ContextThemeWrapper(system,android.R.style.Theme_Material){
            @Override public Object getSystemService(String name){return Context.WINDOW_SERVICE.equals(name)?windows:super.getSystemService(name);}
        };
        Field configurations=ViewConfiguration.class.getDeclaredField("sConfigurations");configurations.setAccessible(true);
        @SuppressWarnings("unchecked") SparseArray<ViewConfiguration> cached=(SparseArray<ViewConfiguration>)configurations.get(null);
        cached.put((int)(100*context.getResources().getDisplayMetrics().density),new ViewConfiguration());
        Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);
        for(Mode mode:Mode.values()){
            BearService host=new BearService();attach.invoke(host,context);
            HuntController controller=new HuntController(host);
            try{
                for(int cycle=0;cycle<3;cycle++){
                    Options options=new Options(mode,mode==Mode.GIANT?8:30,1,100,32);
                    controller.prepare(options);
                    HuntFlow previous=(HuntFlow)get(controller,"flow");
                    require(previous.nextPreset==5&&!previous.running,"Prepared task lost selected preset");controllerChecks++;
                    View oldPanel=(View)get(controller,"panel"),oldRun=(View)get(controller,"run");
                    View oldEnd=text(oldPanel,"结束");OverlayFold oldFold=(OverlayFold)get(controller,"folding");
                    previous.start(1000);previous.cansUsed=98;previous.submitted=7;
                    previous.marches.observe(new HuntMarches.Row[0],1000,false);
                    previous.marches.observe(new HuntMarches.Row[]{new HuntMarches.Row(0,180,HuntMarches.Type.RALLY)},2000,true);
                    set(controller,"started",true);set(controller,"dragging",true);set(controller,"busy",true);
                    set(controller,"target","com.tencent.mm");long oldToken=controller.preparationToken();
                    controller.stop();
                    require(!controller.hasTask()&&!(boolean)get(controller,"dragging")&&!(boolean)get(controller,"busy")
                            &&get(controller,"layout")==null&&get(controller,"folding")==null,"End retained runtime/overlay state");controllerChecks++;
                    controller.prepare(options);
                    HuntFlow fresh=(HuntFlow)get(controller,"flow");
                    require(fresh!=previous&&fresh.phase==Phase.MAP&&fresh.cansUsed==0&&fresh.submitted==0
                            &&fresh.marches.active()==0&&fresh.nextPreset==5&&!fresh.running,"Second task inherited previous session");controllerChecks++;
                    Object panel=get(controller,"panel");int beforeUpdates=updates[0];
                    oldRun.performClick();oldEnd.performClick();oldFold.setCollapsed(true);controller.beginIfPrepared(oldToken);
                    require(get(controller,"flow")==fresh&&get(controller,"panel")==panel&&!fresh.running
                            &&updates[0]==beforeUpdates,"Old controls or delayed start affected the second task");controllerChecks++;
                    controller.stop();
                }
            }finally{controller.destroy();}
        }
        System.out.println("ANDROID HUNT RESTART CONTROLLER PASS: "+controllerChecks+" checks across both modes and three end/reopen cycles; test WindowManager; no game gestures");
        System.out.flush();
    }
    private static Bitmap load(File fixtures,String name){
        Bitmap bitmap=BitmapFactory.decodeFile(new File(fixtures,name+".png").getPath());require(bitmap!=null,"Missing "+name);return bitmap;
    }
    private static Frame observe(Bitmap bitmap,FeedbackVision.Assets assets,Mode mode){
        try(HuntVision vision=new HuntVision(assets,(image,region)->mode==Mode.GIANT?"8":"30")){
            return vision.observe(bitmap,mode);
        }
    }
    private static void search(Bitmap bitmap,FeedbackVision.Assets assets,Mode mode,String label){
        Frame frame=observe(bitmap,assets,mode);
        require(frame.page==Page.SEARCH&&frame.category==mode.ordinal()&&frame.level==(mode==Mode.GIANT?8:30),label+" search not identified");
        require(frame.width==bitmap.getWidth()&&frame.height==bitmap.getHeight(),"Lost captured dimensions");
        HuntFlow flow=new HuntFlow(new Options(mode,frame.level,1,0,32));flow.start(0);flow.phase=Phase.SEARCH;
        Action action=flow.next(frame,2000);int[] point=frame.points[action.kind.ordinal()];
        require(action.kind==Kind.SEARCH&&frame.contains(point[0],point[1]),label+" valid search rejected");
        require(point[1]>frame.height*.9,"Wrong search control");
        Rect floatingActivity=new Rect(0,0,frame.width,(int)(frame.height*.7));
        require(!floatingActivity.contains(point[0],point[1]),"Small-window regression fixture is not outside app bounds");
        imageChecks++;System.out.println("RESTART IMAGE PASS: "+label+" search="+point[0]+","+point[1]+" frame="+frame.width+"x"+frame.height);System.out.flush();
    }
    private static void legacyMiss(Bitmap bitmap,File assets)throws Exception{
        Bitmap template=load(new File(assets,"hunt"),"search");
        Mat rgba=new Mat(),small=new Mat(),gray=new Mat(),templateRgba=new Mat(),templateGray=new Mat();
        try{
            int rows=Math.round(bitmap.getHeight()*540f/bitmap.getWidth());
            Utils.bitmapToMat(bitmap,rgba);Imgproc.resize(rgba,small,new Size(540,rows),0,0,Imgproc.INTER_AREA);
            Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
            Utils.bitmapToMat(template,templateRgba);Imgproc.cvtColor(templateRgba,templateGray,Imgproc.COLOR_RGBA2GRAY);
            org.opencv.core.Rect area=new org.opencv.core.Rect(70,rows/2,430,rows-rows/2);
            require(VisualLocator.unique(gray,templateGray,area,.88,true)==null,"Expected original recording miss not reproduced");
            VisualLocator.Match found=VisualLocator.uniqueAdaptive(gray,templateGray,area,.88,true);
            require(found!=null&&found.confidence>=.88,"Adaptive scale recovery failed");
            System.out.println("RESTART OLD MATCH MISS REPRODUCED; adaptive confidence="+found.confidence);
        }finally{template.recycle();rgba.release();small.release();gray.release();templateRgba.release();templateGray.release();}
    }
    private static void images(String[] args)throws Exception{
        System.load(args[0]+"/libc++_shared.so");System.load(args[0]+"/libopencv_java4.so");
        File fixtures=new File(args[1]),assetDir=new File(args[2]);
        int firstSize=args.length>3?Integer.parseInt(args[3]):0;
        int[][] sizes={{620,1280},{1152,2376},{1344,2772},{1248,2004},{1440,2160}};
        require(firstSize>=0&&firstSize<sizes.length,"Invalid restart size index");
        FeedbackVision.Assets assets=path->new FileInputStream(new File(assetDir,path));
        Bitmap map=load(fixtures,"restart-map"),search=load(fixtures,"restart-search"),paused=load(fixtures,"restart-paused-search");
        try{
            if(firstSize==0)legacyMiss(search,assetDir);
            for(int sizeIndex=firstSize;sizeIndex<sizes.length;sizeIndex++){
                int[] size=sizes[sizeIndex];
                Bitmap movedMap=HuntRuntimeCheck.resized(map,size[0],size[1],false);
                try{
                    Frame frame=observe(movedMap,assets,Mode.GIANT);require(frame.page==Page.MAP&&frame.used==0,"Restart map not recognized");
                    HuntFlow flow=new HuntFlow(new Options(Mode.GIANT,8,1,0,32));flow.start(0);flow.next(frame,1000);
                    Action action=flow.next(frame,2000);int[] point=frame.points[action.kind.ordinal()];
                    require(action.kind==Kind.OPEN&&frame.contains(point[0],point[1]),"Fresh task cannot open search");imageChecks++;
                }finally{movedMap.recycle();}
                for(Bitmap original:new Bitmap[]{search,paused}){
                    Bitmap moved=HuntRuntimeCheck.resized(original,size[0],size[1],false);
                    try{search(moved,assets,Mode.GIANT,(original==search?"collapsed":"expanded")+" "+size[0]);}finally{moved.recycle();}
                }
            }
            Bitmap duplicate=search.copy(Bitmap.Config.ARGB_8888,true);
            try{
                Frame frame=observe(search,assets,Mode.GIANT);int[] point=frame.points[Kind.SEARCH.ordinal()];
                Rect from=new Rect(point[0]-36,point[1]-22,point[0]+36,point[1]+22),to=new Rect(from);to.offset(0,-100);
                new Canvas(duplicate).drawBitmap(search,from,to,null);
                require(observe(duplicate,assets,Mode.GIANT).page!=Page.SEARCH,"Duplicate Search accepted");imageChecks++;
            }finally{duplicate.recycle();}
        }finally{map.recycle();search.recycle();paused.recycle();}
        for(Mode mode:Mode.values()){
            Bitmap legacy=load(fixtures,mode==Mode.GIANT?"giant_search":"beast_search");
            try{search(legacy,assets,mode,"legacy "+mode);}finally{legacy.recycle();}
        }
        Bitmap blank=Bitmap.createBitmap(620,1280,Bitmap.Config.ARGB_8888);
        try{require(observe(blank,assets,Mode.GIANT).page==Page.UNKNOWN,"Blank screen accepted");imageChecks++;}finally{blank.recycle();}
        Mat empty=new Mat();
        try{require(VisualLocator.uniqueAdaptive(empty,empty,new org.opencv.core.Rect(),.88,true)==null,"Empty template accepted");}finally{empty.release();}
        System.out.println("ANDROID HUNT RESTART IMAGE PASS: "+imageChecks+" checks; original recording plus synthetic sizes; numeric OCR annotated; no game gestures");
    }
    public static void main(String[] args){
        try{controllers();if(args.length>0)images(args);}catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
}
