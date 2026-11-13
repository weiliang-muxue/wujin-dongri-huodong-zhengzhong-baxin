package cn.local.winterbear;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Insets;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import cn.local.bullseye.FloatingGuard;
import cn.local.bullseye.FloatingGuard.Box;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import cn.local.automation.RecognitionWorker;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.lang.ref.WeakReference;

public final class BearService extends AccessibilityService {
    private static WeakReference<BearService> reference=new WeakReference<>(null);
    public static BearService current(){return reference.get();}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final RecognitionWorker worker=new RecognitionWorker();
    private final MarchFlow flow=new MarchFlow();
    private final GestureGate gestureGate=new GestureGate();
    private final ForegroundGate foregroundGate=new ForegroundGate();
    private final OverlayRecovery overlayRecovery=new OverlayRecovery();
    public final ModeGate modes=new ModeGate();
    private cn.local.bullseye.GameService bullseye;
    private HuntController hunt;
    private TaskChooser taskChooser;
    private String taskSelectionTarget="";
    private final ArrayDeque<String> log=new ArrayDeque<>();
    private final Runnable tick=this::cycle;
    private Config config;
    private WindowManager windows;
    private LinearLayout bubble,picker,controls;
    private OverlayFold bubbleFold;
    private WindowManager.LayoutParams bubbleParams;
    private TextView statusLabel;
    private Button runButton,setupButton,moreButton;
    private Box bubbleBounds;
    private long overlayRevision;
    private boolean dragging,overlayMoving,bearRunStarted;
    private Matcher matcher;
    private String status="准备就绪",captureMode="";
    private int captureSlot,generation,captureId;
    private boolean capturing,destroyed,checkingMap,manualMapFallback;
    private boolean startAfterMapCheck;
    private int mapNavigationSteps;
    private final LiveMapCheck previewCheck=new LiveMapCheck();
    private Bitmap selectionBitmap;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();config=new Config(this);windows=getSystemService(WindowManager.class);
        reference=new WeakReference<>(this);bullseye=new cn.local.bullseye.GameService(this);hunt=new HuntController(this);taskChooser=new TaskChooser(this);setStatus("服务已开启 · 选择活动后开始");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // A heads-up notification can temporarily replace the active accessibility root.
        // The cycle and gesture gates suspend all actions until the actual game is foreground again.
    }
    @Override public void onInterrupt(){pause("服务被系统中断");if(bullseye!=null)bullseye.onInterrupt();if(hunt!=null)hunt.pause("服务被系统中断");}
    @Override public void onDestroy() {
        destroyed=true;pause("服务已关闭");if(taskChooser!=null)taskChooser.dismiss();if(bullseye!=null)bullseye.onDestroy();if(hunt!=null)hunt.destroy();captureId++;removePicker();removeBubble();
        main.removeCallbacksAndMessages(null);worker.shutdown();if(current()==this)reference.clear();super.onDestroy();
    }
    String status(){return status;}
    String logs(){return String.join("\n",log);}
    private void setStatus(String value) {
        if(!value.equals(status)||log.isEmpty()) {
            status=value;
            log.addLast(new SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date())+"  "+value);
            while(log.size()>240)log.removeFirst();
        }
        if(statusLabel!=null&&!value.contentEquals(statusLabel.getText()))statusLabel.setText(value);
        updateControls();
    }
    private void updateControls(){
        if(bubbleFold!=null)bubbleFold.updateStatus(flow.running,checkingMap||capturing?"识别中":bearRunStarted||flow.canResume(SystemClock.elapsedRealtime())?"已暂停":"待开始",status);
        if(moreButton!=null)moreButton.setVisibility(!captureMode.isEmpty()&&!flow.running&&!checkingMap&&!capturing?View.VISIBLE:View.GONE);
        if(runButton!=null){
            String label=flow.running?"暂停":checkingMap?"读取中":captureMode.equals("auto_map")?"连接":!captureMode.isEmpty()?"取样":config.dry()?"识别":flow.canResume(SystemClock.elapsedRealtime())?"继续":"开始";
            if(!label.contentEquals(runButton.getText()))runButton.setText(label);
            runButton.setEnabled(!checkingMap);runButton.setAlpha(checkingMap?.55f:1f);
        }
        if(setupButton!=null){
            setupButton.setText(!captureMode.isEmpty()?(manualMapFallback?"改为手动框选行军":"取消这一步"):!config.mapVerified()?"连接 / 检查地图":!config.rallyReady()?"下一步：学习巨熊按钮":"重新检查地图");
            setupButton.setEnabled(!checkingMap);setupButton.setAlpha(checkingMap?.55f:1f);
        }
    }
    private String activePackage() {
        return ForegroundProbe.read(this);
    }
    void pause(String reason) {
        flow.suspend(SystemClock.elapsedRealtime(),gestureGate.inFlight());
        generation++;captureId++;capturing=false;checkingMap=false;previewCheck.reset();gestureGate.cancel();worker.cancel();main.removeCallbacks(tick);setStatus(reason);
        if(bubble!=null&&picker==null&&!destroyed)bubble.setVisibility(View.VISIBLE);
        Matcher previous=matcher;matcher=null;
        if(previous!=null)worker.dispose(previous::close);
    }
    private boolean interactive() { return getSystemService(PowerManager.class).isInteractive(); }
    public String foregroundPackage(){return activePackage();}
    public void activateBullseye(){pause("已切换正中靶心");flow.stop();removePicker();removeBubble();if(hunt!=null)hunt.stop();modes.select(ModeGate.Mode.BULLSEYE);}
    public void stopAllModes(){startAfterMapCheck=false;pause("已返回活动选择");flow.stop();removePicker();removeBubble();if(bullseye!=null)bullseye.stopRun("已切换活动");if(hunt!=null)hunt.stop();modes.select(ModeGate.Mode.NONE);}
    private void activateBear(){if(bullseye!=null)bullseye.stopRun("已切换打熊");if(hunt!=null)hunt.stop();modes.select(ModeGate.Mode.BEAR);}
    void prepareHunt(HuntFlow.Options options){pause("已切换狩猎");flow.stop();removePicker();removeBubble();if(bullseye!=null)bullseye.stopRun("已切换活动");hunt.prepare(options);}
    void pauseHunt(){if(hunt!=null)hunt.pauseForSettings();}
    boolean hasHuntTask(){return hunt!=null&&hunt.hasTask();}
    boolean matchesHuntOptions(HuntFlow.Options options){return hunt!=null&&hunt.matchesOptions(options);}
    public void showTaskPicker(){if(!destroyed&&taskChooser!=null)taskChooser.show();}
    void pauseForTaskSelection(){
        String foreground=activePackage();
        if(!foreground.isEmpty()&&!foreground.equals(getPackageName()))taskSelectionTarget=foreground;
        pause("选择任务");
        if(hunt!=null)hunt.pause("选择任务");
        if(bullseye!=null)bullseye.pauseForTaskSelection();
    }
    int bearPresetSelection(){return config.presetSelection();}
    void startBearInGame(int presetSelection){
        if(!PresetOrder.valid(presetSelection))throw new IllegalArgumentException("请至少选择一个有效编队");
        stopAllModes();
        config.setPresetSelection(presetSelection);
        if(config.mapVerified()&&config.validate()==null){
            prepareRun(false);
            final int token=generation;
            main.postDelayed(()->startInGameWhenReady(token,this::start,0),350);
        }else{
            prepareCapture("auto_map",0);startAfterMapCheck=true;
            final int token=generation;
            main.postDelayed(()->startInGameWhenReady(token,this::captureNow,0),350);
        }
    }
    void startHuntInGame(HuntFlow.Options options){
        stopAllModes();prepareHunt(options);
        final int token=generation;
        final long prepared=hunt.preparationToken();
        main.postDelayed(()->startInGameWhenReady(token,()->hunt.beginIfPrepared(prepared),0,
                ()->hunt.preparationToken()==prepared),350);
    }
    void startBullseyeInGame(){
        stopAllModes();
        final int token=generation;
        main.postDelayed(()->startInGameWhenReady(token,()->startSavedBullseye(token,0),0),350);
    }
    private void startInGameWhenReady(int token,Runnable action,int attempt){
        startInGameWhenReady(token,action,attempt,()->true);
    }
    private void startInGameWhenReady(int token,Runnable action,int attempt,BooleanSupplier stillRequested){
        if(token!=generation||destroyed||!stillRequested.getAsBoolean())return;
        String foreground=activePackage();
        boolean gameReady=!foreground.isEmpty()&&!foreground.equals(getPackageName())
                &&(taskSelectionTarget.isEmpty()||taskSelectionTarget.equals(foreground));
        if(capturing||!gameReady){
            if(attempt<20)main.postDelayed(()->startInGameWhenReady(token,action,attempt+1,stillRequested),250);
            else taskChooser.message("等待游戏画面","请回到游戏后重新选择任务。");
            return;
        }
        action.run();
    }
    private void startSavedBullseye(int token,int attempt){
        if(token!=generation||destroyed)return;
        if(bullseye.awaitingCapture()&&attempt<20){
            main.postDelayed(()->startSavedBullseye(token,attempt+1),250);return;
        }
        String error=bullseye.startSavedTask();
        if(error!=null)taskChooser.message("正中靶心",error);
    }
    @Override public void onTaskRemoved(Intent rootIntent){if(hunt!=null)hunt.stop();super.onTaskRemoved(rootIntent);}
    private void start() {
        if(picker!=null||capturing||checkingMap){setStatus("请先完成当前识别或取样");return;}
        String error=config.validate();if(error!=null){setStatus(error);return;}
        String foreground=activePackage();
        if(!foreground.isEmpty()&&!foreground.equals(config.target())){setStatus("请先切回目标游戏");return;}
        if(!interactive()){setStatus("屏幕未点亮");return;}
        if(!PresetOrder.valid(config.presetSelection())){setStatus("请至少选择一个编队");return;}
        activateBear();
        try { matcher=new Matcher(config); } catch(Exception e){setStatus("图像组件无法启动："+e.getMessage());return;}
        generation++;flow.dry=config.dry();flow.maxMinutes=config.minutes();flow.nextPreset=config.p.getInt("nextPreset",0);
        flow.setPresets(config.presetSelection());
        for(int i=0;i<Config.SLOTS;i++){
            flow.presetX[i]=config.p.getInt("presetX"+i,0);flow.presetY[i]=config.p.getInt("presetY"+i,0);
        }
        long now=SystemClock.elapsedRealtime();
        boolean resuming=flow.canResume(now);
        matcher.recoverToMap(!resuming);
        if(resuming)flow.resume(now);else flow.start(now);
        bearRunStarted=true;
        foregroundGate.reset();
        overlayRecovery.reset();
        if(controls!=null)controls.setVisibility(View.GONE);
        if(moreButton!=null)moreButton.setVisibility(View.GONE);
        int session=generation;
        main.postDelayed(()->{if(session==generation&&flow.running)pause("已达到本次运行时长");},flow.remainingMillis(SystemClock.elapsedRealtime()));
        setStatus(flow.dry?"只识别模式 · 检查行军 x/6":resuming?"继续当前跟车流程":"开始检查实际行军空位");cycle();
    }
    void showBubble() {
        if(destroyed)return;
        if(bubble!=null){bubble.setVisibility(View.VISIBLE);return;}
        WindowMetrics metrics=windows.getCurrentWindowMetrics();
        Rect screenBounds=metrics.getBounds();
        Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        OverlayFold.Controls ui=new OverlayFold.Controls(this,screenBounds.width(),insets.top,"打熊",
                ()->{if(flow.running)pause("已手动暂停");else if(!captureMode.isEmpty())captureNow();else start();},
                ()->{pause("已结束");flow.stop();removeBubble();});
        bubble=ui.panel;statusLabel=ui.status;runButton=ui.run;TextView handle=ui.title;
        statusLabel.setText(status);
        moreButton=Ui.button(this,bubble,"更多 / 收起",false,()->{controls.setVisibility(controls.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});
        controls=Ui.column(this);bubble.addView(controls);
        controls.setVisibility(View.GONE);
        setupButton=Ui.button(this,controls,"连接 / 检查地图",false,()->{
            if(manualMapFallback){prepareCapture("march",0);return;}
            if(!captureMode.isEmpty()){captureMode="";pause("已取消准备；可点“设置 / 说明”继续");return;}
            prepareCapture(config.mapVerified()&&!config.rallyReady()?"rally":"auto_map",0);
        });
        Ui.button(this,controls,"设置 / 说明",false,()->{pause("已打开助手设置");startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));});
        Ui.button(this,controls,"关闭悬浮窗",false,()->{pause("已关闭悬浮窗");removeBubble();});
        bubbleParams=new WindowManager.LayoutParams(ui.width,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;
        bubbleParams.x=Math.max(0,Math.min(screenBounds.width()-ui.width,config.p.getInt("bubbleX",(int)(screenBounds.width()*.4))));
        bubbleParams.y=Math.max(insets.top,Math.min(screenBounds.height()-ui.titleHeight,config.p.getInt("bubbleY",insets.top)));
        handle.setOnTouchListener(new View.OnTouchListener(){float startX,startY;int x,y;boolean moved;
            public boolean onTouch(View view,MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){dragging=true;overlayRevision++;moved=false;startX=event.getRawX();startY=event.getRawY();x=bubbleParams.x;y=bubbleParams.y;return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
                    moved|=Math.hypot(event.getRawX()-startX,event.getRawY()-startY)>ViewConfiguration.get(BearService.this).getScaledTouchSlop();
                    Rect bounds=windows.getCurrentWindowMetrics().getBounds();
                    bubbleParams.x=Math.max(0,Math.min(bounds.width()-bubble.getWidth(),x+(int)(event.getRawX()-startX)));
                    bubbleParams.y=Math.max(0,Math.min(bounds.height()-bubble.getHeight(),y+(int)(event.getRawY()-startY)));
                    windows.updateViewLayout(bubble,bubbleParams);return true;
                }
                if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){overlayRevision++;dragging=false;config.p.edit().putInt("bubbleX",bubbleParams.x).putInt("bubbleY",bubbleParams.y).apply();if(!moved&&event.getActionMasked()==MotionEvent.ACTION_UP)view.performClick();return true;}return true;
            }});
        bubbleFold=new OverlayFold(this,bubble,handle,"打熊",ui.width,ui.compactWidth,ui.titleHeight,width->{overlayRevision++;bubbleParams.width=width;bubbleParams.x=Math.min(bubbleParams.x,windows.getCurrentWindowMetrics().getBounds().width()-width);windows.updateViewLayout(bubble,bubbleParams);},this::showTaskPicker);
        windows.addView(bubble,bubbleParams);
        bubble.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->publishBubbleBounds());
        updateControls();
    }
    private void removeBubble(){overlayRevision++;dragging=false;overlayMoving=false;bearRunStarted=false;bubbleBounds=null;bubbleFold=null;if(bubble!=null){windows.removeView(bubble);bubble=null;statusLabel=null;runButton=null;setupButton=null;moreButton=null;}}
    private int overlayDp(int value){
        Rect screen=windows.getCurrentWindowMetrics().getBounds();
        return Math.round(value*BearOverlayGuard.panelScale(screen.width(),screen.height(),getResources().getDisplayMetrics().density));
    }
    private void publishBubbleBounds(){
        if(bubble==null||bubble.getWidth()==0||bubble.getHeight()==0)return;
        int[] xy=new int[2];bubble.getLocationOnScreen(xy);
        Box next=new Box(xy[0],xy[1],xy[0]+bubble.getWidth(),xy[1]+bubble.getHeight());
        if(!next.same(bubbleBounds)){bubbleBounds=next;overlayRevision++;}
    }
    private boolean prepareAnalysisOverlay(){
        if(bubble==null){showBubble();return false;}
        if(dragging){overlayRecovery.reset();return false;}
        if(overlayMoving){recoverAnalysisOverlay();return false;}
        if(moreButton.getVisibility()!=View.GONE||controls.getVisibility()!=View.GONE){moreButton.setVisibility(View.GONE);controls.setVisibility(View.GONE);overlayRevision++;return false;}
        // The start button has just collapsed the settings. Wait for the smaller layout.
        if(bubble.isLayoutRequested()){recoverAnalysisOverlay();return false;}
        publishBubbleBounds();if(bubbleBounds==null)return false;
        Rect screen=windows.getCurrentWindowMetrics().getBounds();
        Insets inset=windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        List<Box> areas=BearOverlayGuard.areas(screen.width(),screen.height());
        // Respect custom anchors as well as the reference layout.
        for(String key:new String[]{"header","formation","back","world","alliance","war","rally_tab","deploy","march_anchor"}){
            int x=config.p.getInt(key+"X",0),y=config.p.getInt(key+"Y",0);
            if(x>0&&y>0)areas.add(new Box(x-overlayDp(20),y-overlayDp(16),x+overlayDp(20),y+overlayDp(16)));
        }
        Rect count=config.counterRect();if(count.width()>0)areas.add(new Box(count.left,count.top,count.right,count.bottom));
        for(int i=0;i<6;i++){int x=config.p.getInt("presetX"+i,0),y=config.p.getInt("presetY"+i,0);if(x>0&&y>0)areas.add(new Box(x-20,y-20,x+20,y+20));}
        Box clear=FloatingGuard.place(bubbleBounds,new Box(screen.left+inset.left,screen.top+inset.top,screen.right-inset.right,screen.bottom-inset.bottom),areas,overlayDp(8));
        if(clear==null){
            if(bubbleFold!=null&&!bubbleFold.isCollapsed()){bubbleFold.setCollapsed(true);return false;}
            pause("当前屏幕没有可用浮窗位置，请退出分屏后继续");return false;
        }
        if(!clear.same(bubbleBounds)){
            if(recoverAnalysisOverlay())return false;
            bubbleParams.x+=clear.left-bubbleBounds.left;bubbleParams.y+=clear.top-bubbleBounds.top;overlayRevision++;overlayMoving=true;
            final View moved=bubble;
            moved.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                @Override public boolean onPreDraw(){moved.getViewTreeObserver().removeOnPreDrawListener(this);if(bubble==moved){publishBubbleBounds();overlayMoving=false;}return true;}
            });
            windows.updateViewLayout(bubble,bubbleParams);return false;
        }
        overlayRecovery.reset();
        return true;
    }
    private boolean recoverAnalysisOverlay(){
        OverlayRecovery.Action action=overlayRecovery.blocked(SystemClock.elapsedRealtime(),bubbleFold!=null&&bubbleFold.isCollapsed());
        if(action==OverlayRecovery.Action.STOP){pause("浮窗位置未稳定，请拖开后继续");return true;}
        if(action==OverlayRecovery.Action.COLLAPSE&&bubbleFold!=null){
            overlayMoving=false;bubbleFold.setCollapsed(true);setStatus("浮窗已收起，继续查找");return true;
        }
        return false;
    }
    void prepareCapture(String mode,int slot){
        startAfterMapCheck=false;
        if(picker!=null||capturing||checkingMap){setStatus("请先完成或取消当前操作");return;}
        activateBear();pause("准备连接游戏");flow.stop();captureMode=mode;captureSlot=slot;manualMapFallback=false;
        if(mode.equals("auto_map"))config.p.edit().putBoolean("dry",true).apply();
        setStatus(mode.equals("auto_map")?"打开真实游戏的野外地图，再点“连接游戏”。没有行军栏也可以":mode.equals("rally")?"打开有巨熊和绿色＋的集结页，再点“截取当前页面”":"打开对应游戏页面，再点“截取当前页面” · "+captureLabel(mode,slot));showBubble();
    }
    void prepareRun(boolean dry){
        startAfterMapCheck=false;
        pause(dry?"回到野外地图，点“试识别”；不会点击游戏":"回到野外地图，点“开始打熊”");
        flow.stop();
        captureMode="";manualMapFallback=false;config.p.edit().putBoolean("dry",dry).apply();showBubble();updateControls();
    }
    private String captureLabel(String mode,int slot){
        return Config.captureLabel(mode,slot);
    }
    private void captureNow(){
        if(captureMode.isEmpty()){setStatus("请先点“设置”，选择要录入的项目");return;}
        if(capturing||picker!=null||checkingMap)return;
        pause("正在截图…");
        String app=activePackage();
        if(app.isEmpty()||app.equals(getPackageName())||app.startsWith("com.android.")||app.equals("android")){
            setStatus("请先打开真实游戏，再点悬浮窗顶部的识别或截取按钮");return;
        }
        if(config.target().isEmpty()&&!captureMode.equals("header")&&!captureMode.equals("march")&&!captureMode.equals("auto_map")){setStatus("请先连接游戏地图");return;}
        if(!config.target().isEmpty()&&!app.equals(config.target())){setStatus("当前不是已绑定的游戏；可在设置清除后重新绑定");return;}
        if(captureMode.equals("auto_map")){
            checkingMap=true;mapNavigationSteps=0;manualMapFallback=false;setStatus("正在读取真实地图 1/2…");
            readMapFrame(generation,app,new LiveMapCheck(),0);return;
        }
        int token=generation;String mode=captureMode;int slot=captureSlot;
        screenshot(bitmap->{
            if(token!=generation||!activePackage().equals(app)){bitmap.recycle();setStatus("截图期间页面已切换，请重新取样");return;}
            if(config.width()!=0&&(config.width()!=bitmap.getWidth()||config.height()!=bitmap.getHeight())){
                bitmap.recycle();setStatus("分辨率已改变，请清除旧模板后重新取样");return;
            }
            openPicker(bitmap,mode,slot,app);
        },true,()->{});
    }
    private void readMapFrame(int token,String app,LiveMapCheck check,int attempt){
        if(token!=generation||!checkingMap||destroyed)return;
        if(!interactive()||!activePackage().equals(app)){finishMapCheck("游戏已切到后台，请回地图后重试",false);return;}
        if(!prepareAnalysisOverlay()){if(checkingMap)main.postDelayed(()->readMapFrame(token,app,check,attempt),250);return;}
        final long frameRevision=overlayRevision;
        final long capturedAt=SystemClock.elapsedRealtime();
        final boolean returnToMap=startAfterMapCheck;
        screenshot(bitmap->{
            if(token!=generation||!checkingMap||destroyed){bitmap.recycle();return;}
            final int w=bitmap.getWidth(),h=bitmap.getHeight();
            worker.submit(()->{
                BearFlow.Frame frame=null;String failure=null;boolean reference=false;
                try{
                    boolean changed=config.width()!=0&&(config.width()!=w||config.height()!=h);
                    if(changed&&!config.canAdaptReference())throw new IllegalStateException("手动取样尺寸已变化，请重新取样");
                    Config detection=config;
                    if(!config.marchReady()||changed){
                        detection=Config.referenceProbe(this,w,h);reference=true;
                    }
                    try(Matcher reader=new Matcher(detection)){reader.recoverToMap(returnToMap);reader.frame(bitmap);frame=reader.observe();}
                }catch(CancellationException e){return;}
                catch(Exception e){failure=e.getMessage()==null?"地图识别失败，请重试或手动框选行军":e.getMessage();}
                final BearFlow.Frame result=frame;final String error=failure;final boolean referenceUsed=reference;
                main.post(()->{
                    if(token!=generation||!checkingMap||destroyed)return;
                    if(!BearOverlayGuard.current(frameRevision,overlayRevision,dragging)){main.postDelayed(()->readMapFrame(token,app,check,attempt),250);return;}
                    if(!interactive()||!activePackage().equals(app)){finishMapCheck("游戏已切到后台，请回地图后重试",false);return;}
                    if(error!=null){finishMapCheck(error,true);return;}
                    if(result.navigationBack){
                        returnForMapCheck(result,w,h,token,app,check,attempt,capturedAt);return;
                    }
                    if(check.accept(app,w,h,result.map,result.marchUsed,result.marchHidden,capturedAt,SystemClock.elapsedRealtime())){
                        try{
                            // Merge only after real screenshots match; keep existing manual samples and switches.
                            if(config.width()!=0&&(config.width()!=w||config.height()!=h))config.adaptReferenceLayout(w,h);
                            else config.fillMissingReferenceLayout(w,h);
                            if(referenceUsed&&!config.marchReady())throw new IllegalStateException("地图布局保存失败，请手动取样");
                            config.markMapVerified(app,w,h,result.marchUsed);
                            checkingMap=false;manualMapFallback=false;captureMode="";config.p.edit().putBoolean("dry",false).apply();
                            setStatus(result.marchHidden?"已连接真实地图 · 未显示行军栏，运行时可直接尝试加入":"已连接游戏 · 实际读到 "+result.marchUsed+"/6，可开始跟车");
                            if(startAfterMapCheck){
                                startAfterMapCheck=false;
                                main.postDelayed(()->{if(token==generation&&!destroyed)start();},250);
                            }
                        }catch(Exception e){finishMapCheck("保存连接失败，请重试或手动取样",true);}
                        return;
                    }
                    if(attempt>=3){finishMapCheck("未确认真实行军数字。请显示野外地图的行军栏后重试，或手动框选",true);return;}
                    setStatus(result.marchHidden?"已识别地图，无行军栏，正在再次确认…":result.map&&result.marchUsed>=0?"实际读到 "+result.marchUsed+"/6，正在再次确认…":"正在识别真实游戏地图…");
                    main.postDelayed(()->readMapFrame(token,app,check,attempt+1),900);
                });
            },bitmap::recycle);
        },false,()->main.postDelayed(()->readMapFrame(token,app,check,attempt),250));
    }
    private void finishMapCheck(String message,boolean manual){checkingMap=false;startAfterMapCheck=false;manualMapFallback=manual;setStatus(message);}
    private void returnForMapCheck(BearFlow.Frame frame,int w,int h,int token,String app,LiveMapCheck check,int attempt,long capturedAt){
        if(SystemClock.elapsedRealtime()-capturedAt>4000){
            if(attempt>=3){finishMapCheck("返回页面识别超时，请回野外后点连接",false);return;}
            main.postDelayed(()->readMapFrame(token,app,check,attempt+1),250);return;
        }
        if(frame.backX<=0||frame.backY<=0||frame.backX>=w||frame.backY>=h||mapNavigationSteps>=6){finishMapCheck("未能返回地图，请回野外后点连接",false);return;}
        if(!BearOverlayGuard.inputClear(bubbleBounds,frame.backX,frame.backY,frame.backX,frame.backY,overlayDp(8))){
            if(bubbleFold!=null&&!bubbleFold.isCollapsed())bubbleFold.setCollapsed(true);
            else if(attempt>=3){finishMapCheck("请将浮窗移开返回按钮后重试",false);return;}
            main.postDelayed(()->readMapFrame(token,app,check,attempt+1),250);return;
        }
        mapNavigationSteps++;setStatus("返回地图，连接打熊");
        Path path=new Path();path.moveTo(frame.backX,frame.backY);long ticket=gestureGate.begin();
        try{
            boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,70)).build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){
                    if(token!=generation||!checkingMap||!gestureGate.isActive(ticket))return;
                    gestureGate.finish(ticket);main.postDelayed(()->readMapFrame(token,app,check,attempt),850);
                }
                @Override public void onCancelled(GestureDescription g){if(token==generation&&gestureGate.isActive(ticket)){gestureGate.cancel();finishMapCheck("返回触控被打断，请重新连接",false);}}
            },main);
            if(!accepted){gestureGate.cancel();finishMapCheck("系统未接受返回操作，请重新连接",false);}
            main.postDelayed(()->{if(token==generation&&checkingMap&&gestureGate.isActive(ticket)){gestureGate.cancel();finishMapCheck("返回响应超时，请重新连接",false);}},3000);
        }catch(Exception e){gestureGate.cancel();finishMapCheck("返回操作未完成，请重新连接",false);}
    }
    private void screenshot(Consumer<Bitmap> success,boolean sample,Runnable retry){
        if(capturing||destroyed)return;
        capturing=true;int id=++captureId;final int requestGeneration=generation;
        final long revision=overlayRevision;final Box mask=bubbleBounds==null?null:bubbleBounds.padded(overlayDp(8));
        // Only an explicit one-off template capture hides controls. Runtime analysis never does.
        if(sample&&bubble!=null)bubble.setVisibility(View.INVISIBLE);
        main.postDelayed(()->{
            if(destroyed||id!=captureId)return;
            if(requestGeneration!=generation){finishScreenshot(sample);return;}
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                    public void onSuccess(ScreenshotResult result){
                        Bitmap copy=null,wrapped=null;
                        try(HardwareBuffer buffer=result.getHardwareBuffer()){
                            wrapped=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                            if(wrapped!=null&&id==captureId&&!destroyed)copy=wrapped.copy(Bitmap.Config.ARGB_8888,!sample);
                        } catch(Exception e){ }
                        finally {if(wrapped!=null)wrapped.recycle();}
                        if(id!=captureId||destroyed){if(copy!=null)copy.recycle();return;}
                        finishScreenshot(sample);
                        if(requestGeneration!=generation){if(copy!=null)copy.recycle();return;}
                        publishBubbleBounds();
                        if(copy==null){pause("无法读取画面，请重新开启服务后重试");return;}
                        if(!sample){
                            if(mask==null||!BearOverlayGuard.current(revision,overlayRevision,dragging)){copy.recycle();retry.run();return;}
                            Paint paint=new Paint();paint.setColor(Color.BLACK);
                            new Canvas(copy).drawRect(mask.left,mask.top,mask.right,mask.bottom,paint);
                        }
                        success.accept(copy);
                    }
                    public void onFailure(int code){
                        if(id!=captureId||destroyed)return;finishScreenshot(sample);
                        if(requestGeneration==generation)pause("截图失败（"+code+"），请确认屏幕已解锁且游戏允许截图");
                    }
                });
            } catch(Exception e){finishScreenshot(sample);if(requestGeneration==generation)pause("截图不可用："+e.getClass().getSimpleName());}
        },sample?120:0);
        main.postDelayed(()->{
            if(capturing&&id==captureId){captureId++;finishScreenshot(sample);if(requestGeneration==generation)pause("截图超时，请重新开启服务");}
        },5000);
    }
    private void finishScreenshot(boolean sample){capturing=false;if(sample&&bubble!=null)bubble.setVisibility(View.VISIBLE);}
    private void cycle(){
        if(!flow.running||destroyed||!modes.is(ModeGate.Mode.BEAR))return;
        if(flow.expired(SystemClock.elapsedRealtime())){pause("已达到本次运行时长");return;}
        if(!interactive()){pause("屏幕关闭，已暂停");return;}
        if(!gameAvailable())return;
        if(capturing||flow.pending()){main.postDelayed(tick,700);return;}
        if(!prepareAnalysisOverlay()){if(flow.running)main.postDelayed(tick,250);return;}
        final long frameRevision=overlayRevision;
        final int token=generation;final Matcher current=matcher;final long snapshotTime=SystemClock.elapsedRealtime();
        screenshot(bitmap->{
            if(token!=generation||!flow.running){bitmap.recycle();return;}
            if(bitmap.getWidth()!=config.width()||bitmap.getHeight()!=config.height()){
                bitmap.recycle();pause("画面尺寸改变，请恢复取样时的方向与分辨率");return;
            }
            worker.submit(()->{
                BearFlow.Frame observed=null;String error=null;
                try{current.frame(bitmap);observed=current.observe();RecognitionWorker.checkCancelled();}catch(CancellationException e){return;}catch(Exception e){
                    String stage=current==null?"初始化":current.stage();
                    android.util.Log.e("WinterBear",stage+"失败",e);
                    String detail=e.getMessage();
                    if(detail!=null&&detail.length()>160)detail=detail.substring(0,160);
                    error=stage+"："+e.getClass().getSimpleName()+(detail==null?"":" · "+detail);
                }
                final BearFlow.Frame result=observed;final String failure=error;
                main.post(()->{
                    if(token!=generation||!flow.running||destroyed)return;
                    publishBubbleBounds();
                    if(!BearOverlayGuard.current(frameRevision,overlayRevision,dragging)){main.postDelayed(tick,250);return;}
                    if(failure!=null){pause("识别异常："+failure);return;}
                    if(SystemClock.elapsedRealtime()-snapshotTime>4000){pause("识别耗时过长，已暂停以避免使用旧画面点击");return;}
                    if(!interactive()){pause("屏幕关闭，已暂停");return;}
                    if(!gameAvailable())return;
                    if(previewCheck.accept(config.target(),config.width(),config.height(),result.map,result.marchUsed,result.marchHidden,snapshotTime,SystemClock.elapsedRealtime()))config.markMapVerified(config.target(),config.width(),config.height(),result.marchUsed);
                    result.ageMillis=SystemClock.elapsedRealtime()-snapshotTime;
                    BearFlow.Decision decision=flow.next(result,SystemClock.elapsedRealtime());setStatus(decision.message);
                    if(config.p.getInt("nextPreset",0)!=flow.nextPreset)config.p.edit().putInt("nextPreset",flow.nextPreset).apply();
                    if(decision.kind==BearFlow.Kind.PAUSE){pause(decision.message);return;}
                    if(decision.kind==BearFlow.Kind.WAIT){main.postDelayed(tick,800);return;}
                    gesture(decision,token);
                });
            },bitmap::recycle);
        },false,()->main.postDelayed(tick,250));
    }
    private void gesture(BearFlow.Decision decision,int token){
        // Every action is gated again after image recognition to avoid clicking another app.
        if(token!=generation||!flow.running)return;
        if(!activePackage().equals(config.target())){
            flow.deferPending();
            if(gameAvailable())main.postDelayed(tick,250);
            return;
        }
        if(flow.expired(SystemClock.elapsedRealtime())){pause("已达到本次运行时长");return;}
        Path path=new Path();long duration;float x1,y1,x2,y2;
        if(decision.kind==BearFlow.Kind.SCROLL){
            float[] swipe=BearOverlayGuard.nextPagePath(config.width(),config.height(),bubbleBounds,overlayDp(8));
            if(swipe==null){deferObstructedGesture();return;}
            x1=swipe[0];y1=swipe[1];x2=swipe[2];y2=swipe[3];
            path.moveTo(x1,y1);path.lineTo(x2,y2);duration=400;
        }else{
            if(decision.x<=0||decision.y<=0||decision.x>=config.width()||decision.y>=config.height()){pause("点击位置无效，请重新取样");return;}
            x1=x2=decision.x;y1=y2=decision.y;path.moveTo(x1,y1);duration=70;
        }
        if(dragging||!BearOverlayGuard.inputClear(bubbleBounds,x1,y1,x2,y2,overlayDp(8))){deferObstructedGesture();return;}
        GestureDescription description=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
        final long ticket=gestureGate.begin();
        try{
            boolean accepted=dispatchGesture(description,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription gesture){finish(true);}
                @Override public void onCancelled(GestureDescription gesture){finish(false);}
                private void finish(boolean ok){
                    if(token!=generation||!flow.running||!gestureGate.isActive(ticket))return;
                    if(!ok){pause("触控被打断，恢复后确认当前画面");return;}
                    gestureGate.finish(ticket);
                    flow.complete(true,SystemClock.elapsedRealtime());
                    if(config.p.getInt("nextPreset",0)!=flow.nextPreset)config.p.edit().putInt("nextPreset",flow.nextPreset).apply();
                    if(!flow.running&&flow.expired(SystemClock.elapsedRealtime()))pause("已达到本次运行时长");
                    else if(!ok)pause("触控被打断，已暂停；未确认上车");else main.postDelayed(tick,850);
                }
            },main);
            if(!accepted){gestureGate.cancel();pause("系统未接受触控操作，已暂停");}
            main.postDelayed(()->{if(token==generation&&flow.running&&gestureGate.isActive(ticket))pause("触控响应超时，恢复后确认当前画面");},3000);
        }catch(Exception e){pause("触控失败："+e.getClass().getSimpleName());}
    }
    private void deferObstructedGesture(){
        flow.deferPending();
        if(!dragging&&bubbleFold!=null&&!bubbleFold.isCollapsed())bubbleFold.setCollapsed(true);
        setStatus("调整浮窗后继续查找");
        main.postDelayed(tick,250);
    }
    private boolean gameAvailable(){
        String foreground=activePackage();
        boolean target=foreground.equals(config.target());
        if(foregroundGate.timedOut(config.target(),foreground,SystemClock.elapsedRealtime())){pause("已切换应用，请回游戏后继续");return false;}
        if(!target){setStatus("等待游戏窗口恢复");main.postDelayed(tick,500);return false;}
        return true;
    }
    private void openPicker(Bitmap bitmap,String mode,int slot,String app){
        selectionBitmap=bitmap;
        if(bubble!=null)bubble.setVisibility(View.GONE);
        picker=Ui.column(this);picker.setBackgroundColor(Ui.BG);
        picker.setFocusableInTouchMode(true);
        picker.setOnKeyListener((view,key,event)->{
            if(key==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP){removePicker();setStatus("已取消取样");}return true;}return false;
        });
        Insets bars=windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        picker.setPadding(Ui.dp(this,12),bars.top+Ui.dp(this,8),Ui.dp(this,12),bars.bottom+Ui.dp(this,8));
        TextView heading=Ui.text(this,captureLabel(mode,slot),20,Ui.ACCENT);picker.addView(heading);
        TextView instruction=Ui.text(this,"",15,Ui.TEXT);instruction.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));picker.addView(instruction);
        SelectionView selection=new SelectionView(this,bitmap);picker.addView(selection,new LinearLayout.LayoutParams(-1,0,1));
        final int[] step={0};final Rect[] bearSample={null};final Button[] saveButton={null};
        Runnable instructions=()->{
            if(mode.equals("rally")){
                instruction.setText(step[0]==0?"1 / 2  框选一行巨熊头像或固定标记，避开玩家名与倒计时；随后尝试自动找绿色＋":"2 / 2  点选这个巨熊队列同一行的绿色加入按钮");
                selection.mode(step[0]==1);
            }else if(mode.equals("march")){
                instruction.setText(step[0]==0?"1 / 2  只框选地图左侧固定的“行军”文字，不要包含数字":"2 / 2  只框选完整的 x/6 数字，如 5/6，稍留边距；不要包含“行军”文字");selection.mode(false);
            }else{
                instruction.setText(mode.equals("preset")?"点选顶部对应预设的白色旗帜中心；请保持编队栏在最左端。会同时保存旗帜样本。":"框选“"+captureLabel(mode,slot)+"”的固定文字或图标，避开数字、倒计时和角标");
                selection.mode(mode.equals("preset"));
            }
        };instructions.run();
        saveButton[0]=Ui.button(this,picker,"使用选区 / 下一步",true,()->{
            if(!selection.valid()){toast("请先点选位置，或框选至少 12 × 12 像素的区域");return;}
            try{
                if(mode.equals("march")&&step[0]==0){validateCrop(bitmap,selection.rect());bearSample[0]=selection.rect();step[0]=1;instructions.run();return;}
                if(mode.equals("rally")&&step[0]==0){
                    validateCrop(bitmap,selection.rect());bearSample[0]=selection.rect();step[0]=1;instructions.run();
                    suggestJoin(bitmap,bearSample[0],selection,instruction,saveButton[0]);return;
                }
                if(mode.equals("march")){
                    Rect count=selection.rect();validateCrop(bitmap,count);
                    if(count.width()>bitmap.getWidth()*.25||count.height()>bitmap.getHeight()*.1)throw new IllegalArgumentException("数字区域过大，请只框选 x/6");
                    config.saveCrop("march_anchor",bitmap,bearSample[0]);
                    config.p.edit().putInt("countLeft",count.left).putInt("countTop",count.top).putInt("countRight",count.right).putInt("countBottom",count.bottom).apply();
                    config.p.edit().putBoolean("mapVerified",false).apply();
                }else if(mode.equals("rally")){
                    Rect bear=bearSample[0];Point join=selection.point();
                    if(Math.abs(join.y-bear.centerY())>bitmap.getHeight()*.35)
                        throw new IllegalArgumentException("加入按钮与巨熊标记应属于同一行，请重新取样");
                    config.p.edit().putBoolean("rallySampleReady",false).apply();
                    config.saveCrop("bear_row",bitmap,bear);
                    int radius=Math.max(14,bitmap.getWidth()/45);
                    Rect joinRect=new Rect(Math.max(0,join.x-radius),Math.max(0,join.y-radius),
                        Math.min(bitmap.getWidth(),join.x+radius),Math.min(bitmap.getHeight(),join.y+radius));
                    config.saveCrop("join_row",bitmap,joinRect);
                    config.p.edit().putInt("rowJoinDx",join.x-bear.centerX()).putInt("rowJoinDy",join.y-bear.centerY())
                        .putBoolean("rallySampleReady",true).apply();
                }else if(mode.equals("preset")){
                    Point point=selection.point();int radius=Math.max(16,bitmap.getWidth()/55);
                    Rect marker=new Rect(Math.max(0,point.x-radius),Math.max(0,point.y-radius),Math.min(bitmap.getWidth(),point.x+radius),Math.min(bitmap.getHeight(),point.y+radius));
                    validateCrop(bitmap,marker);config.saveCrop("preset_marker"+slot,bitmap,marker);
                    config.p.edit().putInt("presetX"+slot,point.x).putInt("presetY"+slot,point.y).apply();
                }else{
                    validateCrop(bitmap,selection.rect());config.saveCrop(mode,bitmap,selection.rect());
                }
                config.p.edit().putString("target",app).putInt("width",bitmap.getWidth()).putInt("height",bitmap.getHeight()).apply();
                String message=captureLabel(mode,slot)+" 已保存 · 可返回游戏继续";
                captureMode="";removePicker();
                if(mode.equals("march")){
                    prepareCapture("auto_map",0);setStatus("行军区域已保存 · 回到地图点“连接游戏”，自动核对数字");
                }else if(mode.equals("rally")){
                    config.p.edit().putBoolean("dry",true).apply();setStatus("巨熊与绿色＋已保存 · 回野外地图点“试识别”；正式跟车在设置中开启");
                }else setStatus(message);
            }catch(Exception e){toast(e.getMessage()==null?"保存失败，请重新取样":e.getMessage());}
        });
        Ui.button(this,picker,"取消取样",false,()->{removePicker();setStatus("已取消取样");});
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        windows.addView(picker,params);
        picker.requestFocus();
    }
    private void suggestJoin(Bitmap source,Rect bear,SelectionView selection,TextView instruction,Button save){
        if(!config.has("join_reference"))return;
        Bitmap copy=source.copy(Bitmap.Config.ARGB_8888,false);
        if(copy==null)return;
        LinearLayout currentPicker=picker;int token=generation;
        save.setEnabled(false);selection.setEnabled(false);instruction.setText("正在自动寻找同行绿色＋…");
        worker.submit(()->{
            Matcher.Hit found=null;
            try(Matcher reader=new Matcher(config)){reader.frame(copy);found=reader.joinBeside(bear);}catch(CancellationException e){return;}catch(Exception ignored){}
            final Matcher.Hit hit=found;
            main.post(()->{
                if(token!=generation||picker!=currentPicker||destroyed)return;
                save.setEnabled(true);selection.setEnabled(true);save.setText("保存巨熊与加入按钮");
                if(hit!=null){selection.suggestPoint(hit.x,hit.y);instruction.setText("2 / 2  已标出同行绿色＋，位置正确就保存；也可点选修正");}
                else instruction.setText("2 / 2  没有确认唯一的绿色＋，请点选这个巨熊同一行的加入按钮");
            });
        },copy::recycle);
    }
    private void validateCrop(Bitmap bitmap,Rect r){
        if(r.width()>bitmap.getWidth()*.85||r.height()>bitmap.getHeight()*.4)throw new IllegalArgumentException("选区过大，请只框选固定文字或图标");
        double sum=0,squares=0;int n=0;
        for(int y=r.top;y<r.bottom;y+=Math.max(1,r.height()/20))for(int x=r.left;x<r.right;x+=Math.max(1,r.width()/30)){
            int pixel=bitmap.getPixel(x,y);int gray=(((pixel>>16)&255)*77+((pixel>>8)&255)*150+(pixel&255)*29)>>8;
            sum+=gray;squares+=gray*gray;n++;
        }
        if(n==0||squares/n-(sum/n)*(sum/n)<36)throw new IllegalArgumentException("选区缺少明显图案，请框选清晰文字或图标");
    }
    private void removePicker(){
        if(picker!=null){windows.removeView(picker);picker=null;}
        if(selectionBitmap!=null){selectionBitmap.recycle();selectionBitmap=null;}
        if(bubble!=null&&!destroyed)bubble.setVisibility(View.VISIBLE);
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
