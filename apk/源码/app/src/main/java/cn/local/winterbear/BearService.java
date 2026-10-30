package cn.local.winterbear;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
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
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.lang.ref.WeakReference;

public final class BearService extends AccessibilityService {
    private static WeakReference<BearService> reference=new WeakReference<>(null);
    public static BearService current(){return reference.get();}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final MarchFlow flow=new MarchFlow();
    private final GestureGate gestureGate=new GestureGate();
    private final ForegroundGate foregroundGate=new ForegroundGate();
    public final ModeGate modes=new ModeGate();
    private cn.local.bullseye.GameService bullseye;
    private final ArrayDeque<String> log=new ArrayDeque<>();
    private final Runnable tick=this::cycle;
    private Config config;
    private WindowManager windows;
    private LinearLayout bubble,picker,controls;
    private WindowManager.LayoutParams bubbleParams;
    private TextView statusLabel;
    private Button runButton,setupButton;
    private Matcher matcher;
    private String status="准备就绪",captureMode="";
    private int captureSlot,generation,captureId,scrollCount;
    private boolean capturing,destroyed,checkingMap,manualMapFallback;
    private final LiveMapCheck previewCheck=new LiveMapCheck();
    private Bitmap selectionBitmap;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();config=new Config(this);windows=getSystemService(WindowManager.class);
        reference=new WeakReference<>(this);bullseye=new cn.local.bullseye.GameService(this);setStatus("服务已开启 · 选择活动后开始");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // A heads-up notification can temporarily replace the active accessibility root.
        // The cycle and gesture gates suspend all actions until the actual game is foreground again.
    }
    @Override public void onInterrupt(){pause("服务被系统中断");if(bullseye!=null)bullseye.onInterrupt();}
    @Override public void onDestroy() {
        destroyed=true;pause("服务已关闭");if(bullseye!=null)bullseye.onDestroy();captureId++;removePicker();removeBubble();
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
        if(statusLabel!=null)statusLabel.setText(value);
        updateControls();
    }
    private void updateControls(){
        if(runButton!=null){
            runButton.setText(flow.running?"暂停":checkingMap?"正在读取地图…":captureMode.equals("auto_map")?"连接游戏":!captureMode.isEmpty()?"截取当前页面":config.dry()?"试识别":"开始打熊");
            runButton.setEnabled(!checkingMap);runButton.setAlpha(checkingMap?.55f:1f);
        }
        if(setupButton!=null){
            setupButton.setText(!captureMode.isEmpty()?(manualMapFallback?"改为手动框选行军":"取消这一步"):!config.mapVerified()?"连接 / 检查地图":!config.rallyReady()?"下一步：学习巨熊按钮":"重新检查地图");
            setupButton.setEnabled(!checkingMap);setupButton.setAlpha(checkingMap?.55f:1f);
        }
    }
    private String activePackage() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return "";
        CharSequence name=root.getPackageName();String value=name==null?"":name.toString();root.recycle();return value;
    }
    void pause(String reason) {
        generation++;checkingMap=false;previewCheck.reset();gestureGate.cancel();flow.stop();main.removeCallbacks(tick);setStatus(reason);
        if(bubble!=null&&picker==null&&!destroyed)bubble.setVisibility(View.VISIBLE);
        Matcher previous=matcher;matcher=null;
        if(previous!=null&&!worker.isShutdown())worker.execute(previous::close);
    }
    private boolean interactive() { return getSystemService(PowerManager.class).isInteractive(); }
    public String foregroundPackage(){return activePackage();}
    public void activateBullseye(){pause("已切换正中靶心");removePicker();removeBubble();modes.select(ModeGate.Mode.BULLSEYE);}
    public void stopAllModes(){pause("已返回活动选择");removePicker();removeBubble();if(bullseye!=null)bullseye.stopRun("已切换活动");modes.select(ModeGate.Mode.NONE);}
    private void activateBear(){if(bullseye!=null)bullseye.stopRun("已切换打熊");modes.select(ModeGate.Mode.BEAR);}
    private void start() {
        if(picker!=null||capturing||checkingMap){setStatus("请先完成当前识别或取样");return;}
        String error=config.validate();if(error!=null){setStatus(error);return;}
        if(!activePackage().equals(config.target())){setStatus("请先切回目标游戏的野外地图");return;}
        if(!interactive()){setStatus("屏幕未点亮");return;}
        activateBear();
        try { matcher=new Matcher(config); } catch(Exception e){setStatus("图像组件无法启动："+e.getMessage());return;}
        generation++;scrollCount=0;flow.dry=config.dry();flow.maxMinutes=config.minutes();flow.nextPreset=config.p.getInt("nextPreset",0);
        for(int i=0;i<Config.SLOTS;i++){
            flow.enabled[i]=config.enabled(i);
            flow.presetX[i]=config.p.getInt("presetX"+i,0);flow.presetY[i]=config.p.getInt("presetY"+i,0);
        }
        flow.start(SystemClock.elapsedRealtime());
        foregroundGate.reset();
        if(controls!=null)controls.setVisibility(View.GONE);
        int session=generation;
        main.postDelayed(()->{if(session==generation&&flow.running)pause("已达到本次运行时长");},flow.remainingMillis(SystemClock.elapsedRealtime()));
        setStatus(flow.dry?"只识别模式 · 检查行军 x/6":"开始检查实际行军空位");cycle();
    }
    void showBubble() {
        if(destroyed)return;
        if(bubble!=null){bubble.setVisibility(View.VISIBLE);return;}
        bubble=Ui.column(this);bubble.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,10));
        bubble.setBackground(Ui.shape(this,Ui.CARD,16));bubble.setElevation(Ui.dp(this,8));
        TextView handle=Ui.text(this,"打熊  ⠿ 拖动",14,Ui.ACCENT);handle.setMinHeight(Ui.dp(this,48));handle.setGravity(Gravity.CENTER_VERTICAL);bubble.addView(handle);
        statusLabel=Ui.text(this,status,13,Ui.TEXT);statusLabel.setMaxLines(4);bubble.addView(statusLabel);
        runButton=Ui.button(this,bubble,"试识别",true,()->{if(flow.running)pause("已手动暂停");else if(!captureMode.isEmpty())captureNow();else start();});
        Ui.button(this,bubble,"停止",false,()->{pause("已停止");removeBubble();});
        Ui.button(this,bubble,"更多 / 收起",false,()->{controls.setVisibility(controls.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});
        controls=Ui.column(this);bubble.addView(controls);
        setupButton=Ui.button(this,controls,"连接 / 检查地图",false,()->{
            if(manualMapFallback){prepareCapture("march",0);return;}
            if(!captureMode.isEmpty()){captureMode="";pause("已取消准备；可点“设置 / 说明”继续");return;}
            prepareCapture(config.mapVerified()&&!config.rallyReady()?"rally":"auto_map",0);
        });
        Ui.button(this,controls,"设置 / 说明",false,()->{pause("已打开助手设置");startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));});
        Ui.button(this,controls,"关闭悬浮窗",false,()->{pause("已关闭悬浮窗");removeBubble();});
        bubbleParams=new WindowManager.LayoutParams(Ui.dp(this,180),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;
        Rect screenBounds=windows.getCurrentWindowMetrics().getBounds();
        bubbleParams.x=Math.max(0,Math.min(screenBounds.width()-Ui.dp(this,180),config.p.getInt("bubbleX",(int)(screenBounds.width()*.38))));
        bubbleParams.y=Math.max(0,Math.min(screenBounds.height()-Ui.dp(this,350),config.p.getInt("bubbleY",(int)(screenBounds.height()*.45))));
        handle.setOnTouchListener(new View.OnTouchListener(){float startX,startY;int x,y;
            public boolean onTouch(View view,MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getRawX();startY=event.getRawY();x=bubbleParams.x;y=bubbleParams.y;return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
                    Rect bounds=windows.getCurrentWindowMetrics().getBounds();
                    bubbleParams.x=Math.max(0,Math.min(bounds.width()-bubble.getWidth(),x+(int)(event.getRawX()-startX)));
                    bubbleParams.y=Math.max(0,Math.min(bounds.height()-bubble.getHeight(),y+(int)(event.getRawY()-startY)));
                    windows.updateViewLayout(bubble,bubbleParams);return true;
                }
                if(event.getActionMasked()==MotionEvent.ACTION_UP){config.p.edit().putInt("bubbleX",bubbleParams.x).putInt("bubbleY",bubbleParams.y).apply();view.performClick();return true;}return true;
            }});
        windows.addView(bubble,bubbleParams);
        updateControls();
    }
    private void removeBubble(){if(bubble!=null){windows.removeView(bubble);bubble=null;statusLabel=null;runButton=null;setupButton=null;}}
    void prepareCapture(String mode,int slot){
        if(picker!=null||capturing||checkingMap){setStatus("请先完成或取消当前操作");return;}
        activateBear();pause("准备连接游戏");captureMode=mode;captureSlot=slot;manualMapFallback=false;
        if(mode.equals("auto_map"))config.p.edit().putBoolean("dry",true).apply();
        setStatus(mode.equals("auto_map")?"打开真实游戏的野外地图，再点“连接游戏”。没有行军栏也可以":mode.equals("rally")?"打开有巨熊和绿色＋的集结页，再点“截取当前页面”":"打开对应游戏页面，再点“截取当前页面” · "+captureLabel(mode,slot));showBubble();
    }
    void prepareRun(boolean dry){
        pause(dry?"回到野外地图，点“试识别”；不会点击游戏":"回到野外地图，点“开始打熊”");
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
            checkingMap=true;manualMapFallback=false;setStatus("正在读取真实地图 1/2…");
            readMapFrame(generation,app,new LiveMapCheck(),0);return;
        }
        int token=generation;String mode=captureMode;int slot=captureSlot;
        screenshot(bitmap->{
            if(token!=generation||!activePackage().equals(app)){bitmap.recycle();setStatus("截图期间页面已切换，请重新取样");return;}
            if(config.width()!=0&&(config.width()!=bitmap.getWidth()||config.height()!=bitmap.getHeight())){
                bitmap.recycle();setStatus("分辨率已改变，请清除旧模板后重新取样");return;
            }
            openPicker(bitmap,mode,slot,app);
        });
    }
    private void readMapFrame(int token,String app,LiveMapCheck check,int attempt){
        if(token!=generation||!checkingMap||destroyed)return;
        if(!interactive()||!activePackage().equals(app)){finishMapCheck("游戏已切到后台，请回地图后重试",false);return;}
        final long capturedAt=SystemClock.elapsedRealtime();
        screenshot(bitmap->{
            if(token!=generation||!checkingMap||destroyed){bitmap.recycle();return;}
            final int w=bitmap.getWidth(),h=bitmap.getHeight();
            worker.execute(()->{
                BearFlow.Frame frame=null;String failure=null;boolean reference=false;
                try{
                    if(config.width()!=0&&(config.width()!=w||config.height()!=h))throw new IllegalStateException("截图尺寸已变化，请在高级设置清除旧取样后重连");
                    Config detection=config;
                    if(!config.marchReady()){
                        if(w!=1080||h!=2400)throw new IllegalStateException("当前布局暂不能自动连接，请点“改为手动框选行军”");
                        detection=Config.referenceProbe(this);reference=true;
                    }
                    try(Matcher reader=new Matcher(detection)){reader.frame(bitmap);frame=reader.observe();}
                }catch(Exception e){failure=e.getMessage()==null?"地图识别失败，请重试或手动框选行军":e.getMessage();}
                finally{bitmap.recycle();}
                final BearFlow.Frame result=frame;final String error=failure;final boolean referenceUsed=reference;
                main.post(()->{
                    if(token!=generation||!checkingMap||destroyed)return;
                    if(!interactive()||!activePackage().equals(app)){finishMapCheck("游戏已切到后台，请回地图后重试",false);return;}
                    if(error!=null){finishMapCheck(error,true);return;}
                    if(check.accept(app,w,h,result.map,result.marchUsed,result.marchHidden,capturedAt,SystemClock.elapsedRealtime())){
                        try{
                            // Merge only after real screenshots match; keep existing manual samples and switches.
                            if(w==1080&&h==2400)config.fillMissingReferenceLayout();
                            if(referenceUsed&&!config.marchReady())throw new IllegalStateException("地图布局保存失败，请手动取样");
                            config.markMapVerified(app,w,h,result.marchUsed);
                            checkingMap=false;manualMapFallback=false;captureMode="";config.p.edit().putBoolean("dry",false).apply();
                            setStatus(result.marchHidden?"已连接真实地图 · 未显示行军栏，运行时可直接尝试加入":"已连接游戏 · 实际读到 "+result.marchUsed+"/6，可开始跟车");
                        }catch(Exception e){finishMapCheck("保存连接失败，请重试或手动取样",true);}
                        return;
                    }
                    if(attempt>=3){finishMapCheck("未确认真实行军数字。请显示野外地图的行军栏后重试，或手动框选",true);return;}
                    setStatus(result.marchHidden?"已识别地图，无行军栏，正在再次确认…":result.map&&result.marchUsed>=0?"实际读到 "+result.marchUsed+"/6，正在再次确认…":"正在识别真实游戏地图…");
                    main.postDelayed(()->readMapFrame(token,app,check,attempt+1),900);
                });
            });
        });
    }
    private void finishMapCheck(String message,boolean manual){checkingMap=false;manualMapFallback=manual;setStatus(message);}
    private void screenshot(Consumer<Bitmap> success){
        if(capturing||destroyed)return;
        capturing=true;int id=++captureId;
        if(bubble!=null)bubble.setVisibility(View.INVISIBLE);
        main.postDelayed(()->{
            if(destroyed||id!=captureId)return;
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                    public void onSuccess(ScreenshotResult result){
                        Bitmap copy=null,wrapped=null;
                        try(HardwareBuffer buffer=result.getHardwareBuffer()){
                            wrapped=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                            if(wrapped!=null&&id==captureId&&!destroyed)copy=wrapped.copy(Bitmap.Config.ARGB_8888,false);
                        } catch(Exception e){ }
                        finally {if(wrapped!=null)wrapped.recycle();}
                        if(id!=captureId||destroyed){if(copy!=null)copy.recycle();return;}
                        restoreAfterScreenshot();
                        if(copy==null)pause("无法读取画面，请重新开启服务后重试");else success.accept(copy);
                    }
                    public void onFailure(int code){
                        if(id!=captureId||destroyed)return;restoreAfterScreenshot();
                        pause("截图失败（"+code+"），请确认屏幕已解锁且游戏允许截图");
                    }
                });
            } catch(Exception e){restoreAfterScreenshot();pause("截图不可用："+e.getClass().getSimpleName());}
        },120);
        main.postDelayed(()->{
            if(capturing&&id==captureId){captureId++;restoreAfterScreenshot();pause("截图超时，请重新开启服务");}
        },5000);
    }
    private void restoreAfterScreenshot(){capturing=false;if(bubble!=null)bubble.setVisibility(View.VISIBLE);}
    private void cycle(){
        if(!flow.running||destroyed||!modes.is(ModeGate.Mode.BEAR))return;
        if(flow.expired(SystemClock.elapsedRealtime())){pause("已达到本次运行时长");return;}
        if(!interactive()){pause("屏幕关闭，已暂停");return;}
        if(!gameAvailable())return;
        if(capturing||flow.pending()){main.postDelayed(tick,700);return;}
        final int token=generation;final Matcher current=matcher;final long snapshotTime=SystemClock.elapsedRealtime();
        screenshot(bitmap->{
            if(token!=generation||!flow.running){bitmap.recycle();return;}
            if(bitmap.getWidth()!=config.width()||bitmap.getHeight()!=config.height()){
                bitmap.recycle();pause("画面尺寸改变，请恢复取样时的方向与分辨率");return;
            }
            worker.execute(()->{
                BearFlow.Frame observed=null;String error=null;
                try{current.frame(bitmap);observed=current.observe();}catch(Exception e){
                    String stage=current==null?"初始化":current.stage();
                    android.util.Log.e("WinterBear",stage+"失败",e);
                    String detail=e.getMessage();
                    if(detail!=null&&detail.length()>160)detail=detail.substring(0,160);
                    error=stage+"："+e.getClass().getSimpleName()+(detail==null?"":" · "+detail);
                }
                finally{bitmap.recycle();}
                final BearFlow.Frame result=observed;final String failure=error;
                main.post(()->{
                    if(token!=generation||!flow.running||destroyed)return;
                    if(failure!=null){pause("识别异常："+failure);return;}
                    if(SystemClock.elapsedRealtime()-snapshotTime>4000){pause("识别耗时过长，已暂停以避免使用旧画面点击");return;}
                    if(!interactive()){pause("屏幕关闭，已暂停");return;}
                    if(!gameAvailable())return;
                    if(previewCheck.accept(config.target(),config.width(),config.height(),result.map,result.marchUsed,result.marchHidden,snapshotTime,SystemClock.elapsedRealtime()))config.markMapVerified(config.target(),config.width(),config.height(),result.marchUsed);
                    BearFlow.Decision decision=flow.next(result,SystemClock.elapsedRealtime());setStatus(decision.message);
                    if(config.p.getInt("nextPreset",0)!=flow.nextPreset)config.p.edit().putInt("nextPreset",flow.nextPreset).apply();
                    if(decision.kind==BearFlow.Kind.PAUSE){pause(decision.message);return;}
                    if(decision.kind==BearFlow.Kind.WAIT){main.postDelayed(tick,800);return;}
                    gesture(decision,token);
                });
            });
        });
    }
    private void gesture(BearFlow.Decision decision,int token){
        // Every action is gated again after image recognition to avoid clicking another app.
        if(token!=generation||!flow.running||!activePackage().equals(config.target())){pause("点击前检测到页面切换，已暂停");return;}
        if(flow.expired(SystemClock.elapsedRealtime())){pause("已达到本次运行时长");return;}
        Path path=new Path();long duration;
        if(decision.kind==BearFlow.Kind.SCROLL){
            boolean back=(scrollCount++%4)>=2;
            path.moveTo(config.width()*.50f,config.height()*(back?.36f:.72f));
            path.lineTo(config.width()*.50f,config.height()*(back?.72f:.36f));duration=400;
        }else{
            if(decision.x<=0||decision.y<=0||decision.x>=config.width()||decision.y>=config.height()){pause("点击位置无效，请重新取样");return;}
            path.moveTo(decision.x,decision.y);duration=70;
        }
        // The bubble must not intercept a game tap at the same screen position.
        if(bubble!=null)bubble.setVisibility(View.INVISIBLE);
        GestureDescription description=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
        final long ticket=gestureGate.begin();
        try{
            boolean accepted=dispatchGesture(description,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription gesture){finish(true);}
                @Override public void onCancelled(GestureDescription gesture){finish(false);}
                private void finish(boolean ok){
                    if(token!=generation||!flow.running||!gestureGate.finish(ticket))return;
                    if(bubble!=null)bubble.setVisibility(View.VISIBLE);
                    flow.complete(ok,SystemClock.elapsedRealtime());
                    if(config.p.getInt("nextPreset",0)!=flow.nextPreset)config.p.edit().putInt("nextPreset",flow.nextPreset).apply();
                    if(!flow.running&&flow.expired(SystemClock.elapsedRealtime()))pause("已达到本次运行时长");
                    else if(!ok)pause("触控被打断，已暂停；未确认上车");else main.postDelayed(tick,850);
                }
            },main);
            if(!accepted){gestureGate.finish(ticket);if(bubble!=null)bubble.setVisibility(View.VISIBLE);flow.complete(false,SystemClock.elapsedRealtime());pause("系统未接受触控操作，已暂停");}
            main.postDelayed(()->{if(token==generation&&flow.running&&gestureGate.finish(ticket)) {if(bubble!=null)bubble.setVisibility(View.VISIBLE);pause("触控响应超时，已暂停");}},3000);
        }catch(Exception e){if(bubble!=null)bubble.setVisibility(View.VISIBLE);pause("触控失败："+e.getClass().getSimpleName());}
    }
    private boolean gameAvailable(){
        boolean target=activePackage().equals(config.target());
        if(foregroundGate.timedOut(target,SystemClock.elapsedRealtime())){pause("已离开目标游戏，自动暂停");return false;}
        if(!target){setStatus("临时遮挡，等待游戏回到前台");main.postDelayed(tick,500);return false;}
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
        worker.execute(()->{
            Matcher.Hit found=null;
            try(Matcher reader=new Matcher(config)){reader.frame(copy);found=reader.joinBeside(bear);}catch(Exception ignored){}
            finally{copy.recycle();}
            final Matcher.Hit hit=found;
            main.post(()->{
                if(token!=generation||picker!=currentPicker||destroyed)return;
                save.setEnabled(true);selection.setEnabled(true);save.setText("保存巨熊与加入按钮");
                if(hit!=null){selection.suggestPoint(hit.x,hit.y);instruction.setText("2 / 2  已标出同行绿色＋，位置正确就保存；也可点选修正");}
                else instruction.setText("2 / 2  没有确认唯一的绿色＋，请点选这个巨熊同一行的加入按钮");
            });
        });
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
