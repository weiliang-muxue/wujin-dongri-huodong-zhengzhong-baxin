package cn.local.winterbear;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import cn.local.bullseye.FloatingGuard.Box;
import org.opencv.android.OpenCVLoader;
import static cn.local.winterbear.HuntFlow.*;

/** Hosted by the single accessibility service; only the selected activity owns input. */
final class HuntController {
    private final BearService host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final WindowManager wm;
    private LinearLayout panel;private TextView status;private Button run;
    private OverlayFold folding;
    private final OverlayRecovery overlayRecovery=new OverlayRecovery();
    private final ForegroundGate foregroundGate=new ForegroundGate();
    private final GestureGate gestures=new GestureGate();
    private boolean gestureDispatched;
    private WindowManager.LayoutParams layout;private Box bounds;
    private HuntFlow flow;private HuntVision vision;private CounterOcr ocr;
    private long generation,revision,modeToken,captureId;private boolean busy,dragging,started,destroyed;
    private String target="";private ModeGate.Mode ownership;
    HuntController(BearService host){this.host=host;wm=host.getSystemService(WindowManager.class);}
    void prepare(Options options){
        if(matchesOptions(options)){pause("任务已暂停，回游戏继续");return;}
        stop();flow=new HuntFlow(options);ownership=options.mode==Mode.GIANT?ModeGate.Mode.GIANT:ModeGate.Mode.BEAST;
        modeToken=host.modes.select(ownership);show();update("回到野外地图，点开始");
    }
    boolean matchesOptions(Options options){return flow!=null&&owns()&&flow.options.sameTask(options);}
    long preparationToken(){return generation;}
    void beginIfPrepared(long token){if(token==generation&&flow!=null&&!flow.running)begin();}
    private boolean owns(){return !destroyed&&host.modes.owns(ownership,modeToken);}
    void pause(String message){
        if(flow!=null)flow.suspend(SystemClock.elapsedRealtime(),gestureDispatched);
        gestureDispatched=false;gestures.cancel();generation++;captureId++;busy=false;
        main.removeCallbacksAndMessages(null);update(message);
    }
    void stop(){
        pause("已结束");if(panel!=null){wm.removeView(panel);panel=null;status=null;run=null;bounds=null;}
        dragging=false;folding=null;layout=null;bounds=null;revision++;
        overlayRecovery.reset();foregroundGate.reset();ownership=null;modeToken=0;
        HuntVision old=vision;CounterOcr oldOcr=ocr;vision=null;ocr=null;
        if(!worker.isShutdown())worker.execute(()->{if(old!=null)old.close();if(oldOcr!=null)oldOcr.close();});
        flow=null;started=false;target="";
    }
    void destroy(){stop();destroyed=true;worker.shutdown();}
    boolean hasTask(){return flow!=null;}
    void pauseForSettings(){if(panel!=null)pause("已回到设置，任务暂停");}
    void begin(){
        if(!owns()||flow==null||flow.running)return;
        String app=host.foregroundPackage();
        if(app.isEmpty()||app.equals(host.getPackageName())||app.startsWith("com.android.")||app.equals("android")){update("先回到游戏的野外地图");return;}
        if(!target.isEmpty()&&!target.equals(app)){update("请回到本次绑定的游戏");return;}
        if(!host.getSystemService(PowerManager.class).isInteractive()){update("请先点亮屏幕");return;}
        target=app;
        if(vision==null)try{
            if(!OpenCVLoader.initLocal())throw new IllegalStateException("OpenCV");
            ocr=new CounterOcr();vision=new HuntVision(host.getAssets()::open,ocr::text);
        }catch(Exception e){update("识别组件未就绪，请重新打开助手");return;}
        generation++;overlayRecovery.reset();foregroundGate.reset();if(started)flow.resume(SystemClock.elapsedRealtime());else{flow.start(SystemClock.elapsedRealtime());started=true;}
        update("正在检查地图");tick();
    }
    private void update(String text){
        if(status!=null&&!text.contentEquals(status.getText()))status.setText(text);
        if(folding!=null)folding.updateStatus(flow!=null&&flow.running,started?"已暂停":"待开始",text);
        String label=flow!=null&&flow.running?"暂停":started?"继续":"开始";
        if(run!=null&&!label.contentEquals(run.getText()))run.setText(label);
    }
    private void show(){
        final HuntFlow shownFlow=flow;
        WindowMetrics metrics=wm.getCurrentWindowMetrics();Rect screen=metrics.getBounds();
        Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        String label=flow.options.mode==Mode.GIANT?"巨兽":"野兽";
        OverlayFold.Controls ui=new OverlayFold.Controls(host,screen.width(),insets.top,label,
                ()->{if(flow!=shownFlow||!owns())return;if(flow.running)pause("已暂停 · 已用罐头 "+flow.cansUsed);else begin();},
                ()->{if(flow==shownFlow&&owns())stop();});
        panel=ui.panel;status=ui.status;run=ui.run;TextView title=ui.title;
        layout=new WindowManager.LayoutParams(ui.width,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=(int)(screen.width()*.4);layout.y=(int)(screen.height()*.025);
        title.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int x,y;boolean moved;
            public boolean onTouch(View v,MotionEvent e){
                if(flow!=shownFlow||panel!=ui.panel)return false;
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){dragging=true;revision++;moved=false;sx=e.getRawX();sy=e.getRawY();x=layout.x;y=layout.y;return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){moved|=Math.hypot(e.getRawX()-sx,e.getRawY()-sy)>ViewConfiguration.get(host).getScaledTouchSlop();Rect r=wm.getCurrentWindowMetrics().getBounds();layout.x=Math.max(0,Math.min(r.width()-panel.getWidth(),x+(int)(e.getRawX()-sx)));layout.y=Math.max(0,Math.min(r.height()-panel.getHeight(),y+(int)(e.getRawY()-sy)));wm.updateViewLayout(panel,layout);return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){revision++;dragging=false;if(!moved&&e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();return true;}return true;
            }});
        folding=new OverlayFold(host,panel,title,label,ui.width,ui.compactWidth,ui.titleHeight,width->{if(flow!=shownFlow||panel!=ui.panel)return;revision++;layout.width=width;layout.x=Math.min(layout.x,wm.getCurrentWindowMetrics().getBounds().width()-width);wm.updateViewLayout(panel,layout);},()->{if(flow==shownFlow&&owns())host.showTaskPicker();});
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->publish());wm.addView(panel,layout);
    }
    private void publish(){if(panel==null||panel.getWidth()==0)return;int[] xy=new int[2];panel.getLocationOnScreen(xy);Box b=new Box(xy[0],xy[1],xy[0]+panel.getWidth(),xy[1]+panel.getHeight());if(!b.same(bounds)){bounds=b;revision++;}}
    private boolean safePosition(){
        if(panel==null)return false;
        if(dragging){overlayRecovery.reset();return false;}
        if(panel.isLayoutRequested()){recoverOverlay();return false;}
        publish();if(bounds==null){recoverOverlay();return false;}
        WindowMetrics metrics=wm.getCurrentWindowMetrics();Rect r=metrics.getBounds();
        Insets i=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        Box clear=HuntOverlayGuard.place(bounds,r.width(),r.height(),i.left,i.top,i.right,i.bottom);
        if(clear==null){
            if(folding!=null&&!folding.isCollapsed()){folding.setCollapsed(true);return false;}
            pause("当前屏幕没有可用浮窗位置，请退出分屏后继续");return false;
        }
        if(clear.same(bounds))return true;
        if(recoverOverlay())return false;
        int x=Math.max(0,layout.x+clear.left-bounds.left),y=Math.max(0,layout.y+clear.top-bounds.top);
        if(x!=layout.x||y!=layout.y){layout.x=x;layout.y=y;revision++;wm.updateViewLayout(panel,layout);}
        return false;
    }
    private boolean recoverOverlay(){
        OverlayRecovery.Action action=overlayRecovery.blocked(SystemClock.elapsedRealtime(),folding!=null&&folding.isCollapsed());
        if(action==OverlayRecovery.Action.STOP){pause("浮窗位置未稳定，请拖到顶部空白处后继续");return true;}
        if(action==OverlayRecovery.Action.COLLAPSE&&folding!=null){folding.setCollapsed(true);update("浮窗已收起，继续识别");return true;}
        update("正在调整浮窗位置");return false;
    }
    private void later(long delay){main.postDelayed(this::tick,delay);}
    private int displayRotation(){
        Display display=host.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        return display==null?-1:display.getRotation();
    }
    private boolean gameAvailable(){
        if(!host.getSystemService(PowerManager.class).isInteractive()){pause("屏幕已关闭，任务暂停");return false;}
        String foreground=host.foregroundPackage();
        if(foregroundGate.timedOut(target,foreground,SystemClock.elapsedRealtime())){pause("已切换应用，请回游戏后继续");return false;}
        if(!target.equals(foreground)){update("等待游戏窗口恢复");later(500);return false;}
        return true;
    }
    private void tick(){
        if(!owns()||flow==null||!flow.running||busy)return;
        if(!gameAvailable())return;
        if(!safePosition()){if(flow.running)later(250);return;}
        busy=true;long token=generation,viewRevision=revision,id=++captureId,at=SystemClock.elapsedRealtime();Box mask=bounds.padded(6);
        final int rotation=displayRotation();
        final HuntVision reader=vision;
        try{host.takeScreenshot(Display.DEFAULT_DISPLAY,host.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
            @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                Bitmap original=null,copy=null;
                try(HardwareBuffer buffer=result.getHardwareBuffer()){
                    original=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());if(original!=null)copy=original.copy(Bitmap.Config.ARGB_8888,true);
                }catch(Exception ignored){}finally{if(original!=null)original.recycle();}
                if(id!=captureId||token!=generation||!owns()){if(copy!=null)copy.recycle();return;}
                if(copy==null){busy=false;pause("截图读取失败，请重试");return;}
                Paint paint=new Paint();paint.setColor(Color.BLACK);new Canvas(copy).drawRect(mask.left,mask.top,mask.right,mask.bottom,paint);
                final Bitmap frame=copy;final Mode mode=flow.options.mode;
                worker.execute(()->{
                    Frame observation=null;String failure=null;
                    try{observation=reader.observe(frame,mode);}catch(Exception e){android.util.Log.e("WinterHunt","识别失败",e);failure="识别失败："+e.getClass().getSimpleName();}finally{frame.recycle();}
                    final Frame f=observation;final String error=failure;
                    main.post(()->{
                        if(id!=captureId||token!=generation||!owns())return;busy=false;
                        if(error!=null){pause(error);return;}
                        publish();if(dragging||revision!=viewRevision){
                            if(dragging)overlayRecovery.reset();else recoverOverlay();
                            if(flow.running)later(250);return;
                        }
                        if(!gameAvailable())return;
                        overlayRecovery.reset();
                        f.age=SystemClock.elapsedRealtime()-at;Action a=flow.next(f,SystemClock.elapsedRealtime());update(a.message);
                        if(a.kind==Kind.WAIT){later(800);return;}if(a.kind==Kind.STOP)return;
                        gesture(a,f,token,viewRevision,at,rotation);
                    });
                });
            }
            @Override public void onFailure(int code){if(id==captureId&&token==generation){busy=false;pause("截图失败（"+code+"）");}}
        });}catch(Exception e){busy=false;pause("无法读取游戏画面");}
        main.postDelayed(()->{if(id==captureId&&token==generation&&busy)pause("识别超时，已暂停");},6000);
    }
    private void gesture(Action a,Frame f,long token,long viewRevision,long captured,int rotation){
        if(token!=generation||!owns())return;
        if(!gameAvailable()){
            if(flow.running){flow.suspend(SystemClock.elapsedRealtime(),false);flow.resume(SystemClock.elapsedRealtime());}
            return;
        }
        if(dragging||revision!=viewRevision||rotation<0||rotation!=displayRotation()||SystemClock.elapsedRealtime()-captured>4000){pause("点击前画面已变化，请继续重试");return;}
        // Use the recognized game's back/dismiss target. Android Back can leave
        // the WeChat mini-game instead of returning to its map.
        int[] p=a.kind==Kind.PRESET?f.flags[a.preset]:f.points[a.kind.ordinal()];
        // Screenshot locations are display pixels. An assistant opened in a
        // floating Activity can have different WindowMetrics from the game.
        if(!f.contains(p[0],p[1])){
            android.util.Log.w("WinterHunt","Missing target "+a.kind+" at "+p[0]+","+p[1]+" in frame "+f.width+"x"+f.height);
            pause("按钮位置未读清，点继续重新识别");return;
        }
        if(bounds==null||bounds.padded(8).contains(p[0],p[1])){pause("悬浮条挡住按钮，请收起后继续");return;}
        busy=true;Path path=new Path();path.moveTo(p[0],p[1]);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,70)).build();
        long ticket=gestures.begin();
        try{
            gestureDispatched=true;
            boolean accepted=host.dispatchGesture(g,new AccessibilityService.GestureResultCallback(){
                @Override public void onCompleted(GestureDescription gesture){finish(true);}
                @Override public void onCancelled(GestureDescription gesture){finish(false);}
                private void finish(boolean ok){
                    if(token!=generation||!owns()||!gestures.finish(ticket))return;
                    busy=false;
                    if(ok){gestureDispatched=false;flow.complete(true,SystemClock.elapsedRealtime());later(900);}
                    else pause("触控中断，请检查游戏后继续");
                }
            },main);
            if(!accepted){gestureDispatched=false;pause("系统未接受点击");return;}
            main.postDelayed(()->{if(token==generation&&gestures.isActive(ticket))pause("触控超时，已暂停");},3000);
        }catch(Exception e){busy=false;pause("触控失败，请重试");}
    }
}
