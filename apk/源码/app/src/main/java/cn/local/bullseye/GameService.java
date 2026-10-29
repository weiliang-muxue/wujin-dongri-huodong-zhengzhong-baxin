package cn.local.bullseye;
import android.accessibilityservice.*;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import cn.local.winterbear.BearService;
import cn.local.winterbear.R;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import android.text.TextUtils;
import com.google.mlkit.vision.text.Text;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class GameService extends ContextWrapper {
    public static volatile GameService instance;
    final BearService host;volatile long generation;String targetPackage="";
    public GameService(BearService host){super(host);this.host=host;onServiceConnected();}
    void takeScreenshot(int display,Executor executor,TakeScreenshotCallback callback){host.takeScreenshot(display,executor,callback);}
    boolean dispatchGesture(GestureDescription gesture,GestureResultCallback callback,Handler handler){return host.dispatchGesture(gesture,callback,handler);}
    public volatile boolean running=false,paused=false;volatile boolean busy=false,captureInFlight=false,gestureInFlight=false;
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();final Object tapLock=new Object();
    Config config;Vision vision;Bitmap home,board;WindowManager wm;LinearLayout floating;TextView status;Button pauseButton;
    int remaining,rounds,pending=-1,retries=0,failures=0;boolean starting=false,inRound=false;long lastAction=0,lastKnown=0;Set<Integer> visited=new HashSet<>();
    boolean allowInitialBoard=true;final TicketPolicy tickets=new TicketPolicy();
    int exitSeen=0,exitAttempts=0;
    WindowManager.LayoutParams floatingParams;
    volatile FloatingGuard.Box floatingBounds;
    volatile long overlayRevision;long frameRevision=-1,moveId;
    volatile boolean dragging;
    List<FloatingGuard.Box> protectedAreas=Collections.emptyList();
    protected void onServiceConnected(){instance=this;vision=new Vision();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
    public void onAccessibilityEvent(AccessibilityEvent e){}
    public void onInterrupt(){pause("系统中断，已暂停");}
    public void onDestroy(){stopRun("服务已关闭");instance=null;worker.shutdownNow();if(vision!=null)vision.close();}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public void begin(Config c){if(running||busy){Toast.makeText(this,"上次运行尚未结束，请稍后再开始",Toast.LENGTH_LONG).show();return;}host.activateBullseye();generation++;targetPackage="";config=c;if(home!=null)home.recycle();if(board!=null)board.recycle();home=BitmapFactory.decodeFile(new File(getFilesDir(),"home.png").toString());board=BitmapFactory.decodeFile(new File(getFilesDir(),"board.png").toString());if(home==null||board==null){Toast.makeText(this,"请重新上传两张截图",Toast.LENGTH_LONG).show();return;}
        float[] xs=new float[c.targets.size()],ys=new float[c.targets.size()];
        for(int i=0;i<xs.length;i++){xs[i]=c.targets.get(i).x;ys[i]=c.targets.get(i).y;}
        protectedAreas=FloatingGuard.protectedAreas(c.width,c.height,c.countRect,c.exitRect,c.start.x,c.start.y,xs,ys);
        floatingBounds=null;dragging=true;overlayRevision++;frameRevision=-1;moveId++;
        remaining=c.count;tickets.reset();rounds=0;pending=-1;starting=false;inRound=false;allowInitialBoard=true;exitSeen=0;exitAttempts=0;visited.clear();retries=0;failures=0;running=true;paused=false;lastKnown=SystemClock.uptimeMillis();lastAction=lastKnown;showFloating();update("5 秒后开始");main.postDelayed(this::tick,5000);
    }
    void showFloating(){
        floating=new LinearLayout(this);floating.setOrientation(LinearLayout.VERTICAL);floating.setPadding(dp(8),dp(4),dp(8),dp(4));floating.setBackgroundColor(Color.rgb(237,247,240));
        TextView version=new TextView(this);version.setText("正中靶心  ⠿ 拖动");version.setGravity(Gravity.CENTER_VERTICAL);version.setMinHeight(dp(48));version.setTextColor(Color.rgb(20,70,45));version.setTextSize(13);version.setTypeface(null,Typeface.BOLD);floating.addView(version);
        status=new TextView(this);status.setTextColor(Color.rgb(20,70,45));status.setTextSize(12);status.setMaxLines(2);status.setEllipsize(TextUtils.TruncateAt.END);floating.addView(status,new LinearLayout.LayoutParams(-1,dp(40)));
        LinearLayout buttons=new LinearLayout(this);pauseButton=new Button(this);pauseButton.setText("暂停");pauseButton.setAllCaps(false);pauseButton.setMinHeight(dp(48));
        pauseButton.setOnClickListener(v->{if(paused){final long resumeToken=generation;worker.execute(()->{synchronized(tapLock){if(!SessionGuard.canResume(resumeToken,generation,running,paused,host.modes.is(cn.local.winterbear.ModeGate.Mode.BULLSEYE)))return;tickets.reset();lastKnown=SystemClock.uptimeMillis();lastAction=lastKnown;paused=false;update("继续识别");}});}else pause("已暂停");});
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(48),1);left.rightMargin=dp(8);buttons.addView(pauseButton,left);
        Button stop=new Button(this);stop.setText("停止");stop.setAllCaps(false);stop.setMinHeight(dp(48));stop.setOnClickListener(v->stopRun("已停止"));buttons.addView(stop,new LinearLayout.LayoutParams(0,dp(48),1));floating.addView(buttons);
        int width=Math.min(dp(240),wm.getCurrentWindowMetrics().getBounds().width()-dp(24));
        floatingParams=new WindowManager.LayoutParams(width,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        floatingParams.gravity=Gravity.TOP|Gravity.LEFT;floatingParams.x=dp(12);floatingParams.y=dp(48);
        final long token=generation,initialMove=moveId;
        floating.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(token==generation)publishFloatingBounds();});
        version.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int x,y;long move;public boolean onTouch(View v,MotionEvent e){
            if(token!=generation||floating==null)return true;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){synchronized(tapLock){dragging=true;overlayRevision++;move=++moveId;}sx=e.getRawX();sy=e.getRawY();x=floatingParams.x;y=floatingParams.y;return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){Rect r=wm.getCurrentWindowMetrics().getBounds();floatingParams.x=Math.max(0,Math.min(r.width()-floating.getWidth(),x+(int)(e.getRawX()-sx)));floatingParams.y=Math.max(0,Math.min(r.height()-floating.getHeight(),y+(int)(e.getRawY()-sy)));wm.updateViewLayout(floating,floatingParams);return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){final long finishedMove=move;floating.post(()->settleFloating(token,finishedMove));v.performClick();return true;}return true;}});
        wm.addView(floating,floatingParams);floating.post(()->settleFloating(token,initialMove));
    }
    // Only the main thread reads Views. Workers consume immutable screen-pixel bounds.
    void publishFloatingBounds(){
        if(floating==null||floating.getWidth()==0||floating.getHeight()==0)return;
        int[] xy=new int[2];floating.getLocationOnScreen(xy);
        FloatingGuard.Box next=new FloatingGuard.Box(xy[0],xy[1],xy[0]+floating.getWidth(),xy[1]+floating.getHeight());
        synchronized(tapLock){if(!next.same(floatingBounds)){floatingBounds=next;overlayRevision++;}}
    }
    void settleFloating(long token,long move){
        if(token!=generation||!FloatingGuard.current(move,moveId,false)||floating==null)return;
        publishFloatingBounds();if(floatingBounds==null){floating.postOnAnimation(()->settleFloating(token,move));return;}
        WindowMetrics metrics=wm.getCurrentWindowMetrics();Rect r=metrics.getBounds();
        Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        FloatingGuard.Box clear=FloatingGuard.place(floatingBounds,new FloatingGuard.Box(r.left+insets.left,r.top+insets.top,r.right-insets.right,r.bottom-insets.bottom),protectedAreas,dp(8));
        if(clear!=null&&!clear.same(floatingBounds)){
            floatingParams.x+=clear.left-floatingBounds.left;floatingParams.y+=clear.top-floatingBounds.top;
            final View moved=floating;
            moved.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
                @Override public boolean onPreDraw(){moved.getViewTreeObserver().removeOnPreDrawListener(this);finishFloatingMove(token,move);return true;}
            });
            wm.updateViewLayout(floating,floatingParams);return;
        }
        finishFloatingMove(token,move);
    }
    void finishFloatingMove(long token,long move){
        if(token!=generation||!FloatingGuard.current(move,moveId,false)||floating==null)return;
        publishFloatingBounds();synchronized(tapLock){dragging=false;}
        if(FloatingGuard.blocked(floatingBounds,protectedAreas,dp(8)))pause("悬浮条挡住游戏，请拖到空白处后继续");
    }
    boolean frameCurrent(){return running&&!paused&&FloatingGuard.current(frameRevision,overlayRevision,dragging);}
    void update(String message){getSharedPreferences("progress",0).edit().putInt("remaining",remaining).putInt("rounds",rounds).putString("state",message).apply();main.post(()->{if(status!=null){String text="首页余券 "+remaining+" · "+message;if(!text.contentEquals(status.getText()))status.setText(text);status.setContentDescription(text);}if(pauseButton!=null)pauseButton.setText(paused?"继续":"暂停");});}
    void pause(String message){synchronized(tapLock){paused=true;overlayRevision++;}update(message);}
    public void stopRun(String message){boolean wasRunning;synchronized(tapLock){wasRunning=running;generation++;running=false;paused=false;dragging=false;floatingBounds=null;overlayRevision++;}main.removeCallbacksAndMessages(null);gestureInFlight=false;if(!captureInFlight)busy=false;if(floating!=null){try{wm.removeView(floating);}catch(Exception ignored){}floating=null;status=null;pauseButton=null;}getSharedPreferences("progress",0).edit().putInt("remaining",remaining).putInt("rounds",rounds).putString("state",message).apply();if(wasRunning)Toast.makeText(this,message+"，剩余 "+remaining+" 张",Toast.LENGTH_LONG).show();}
    void next(){main.postDelayed(this::tick,700);}
    void tick(){
        if(!running||!host.modes.is(cn.local.winterbear.ModeGate.Mode.BULLSEYE))return;if(paused||busy||dragging||floatingBounds==null||SystemClock.uptimeMillis()-lastAction<2400){next();return;}
        String foreground=host.foregroundPackage();
        if(foreground.isEmpty()||foreground.equals(getPackageName())||foreground.startsWith("com.android.")||(!targetPackage.isEmpty()&&!foreground.equals(targetPackage))){pause("已离开游戏，请回游戏后继续");next();return;}
        if(targetPackage.isEmpty())targetPackage=foreground;
        publishFloatingBounds();
        if(FloatingGuard.blocked(floatingBounds,protectedAreas,dp(8))){pause("悬浮条挡住游戏，请拖到空白处后继续");next();return;}
        busy=true;captureInFlight=true;
        final long token=generation,captureRevision=overlayRevision;
        final FloatingGuard.Box mask=floatingBounds.padded(dp(8));
        // Keep controls visible. Only the private analysis bitmap is masked; never the screen.
        try{takeScreenshot(Display.DEFAULT_DISPLAY,worker,new TakeScreenshotCallback(){
            @Override public void onSuccess(ScreenshotResult s){Bitmap hw=null,frame=null;try{
                hw=Bitmap.wrapHardwareBuffer(s.getHardwareBuffer(),s.getColorSpace());if(hw==null)throw new Exception("无法读取屏幕");
                if(token==generation&&running&&FloatingGuard.current(captureRevision,overlayRevision,dragging)){
                    frame=hw.copy(Bitmap.Config.ARGB_8888,true);Paint paint=new Paint();paint.setColor(Color.BLACK);
                    new Canvas(frame).drawRect(mask.left,mask.top,mask.right,mask.bottom,paint);
                    frameRevision=captureRevision;process(frame);failures=0;
                }
            }catch(Exception e){if(token==generation)pause("识别异常："+e.getMessage());}finally{
                if(frame!=null)frame.recycle();if(hw!=null)hw.recycle();s.getHardwareBuffer().close();captureInFlight=false;busy=false;if(token==generation&&running)next();
            }}
            @Override public void onFailure(int error){captureInFlight=false;busy=false;if(token!=generation)return;if(++failures>=3)pause("无法读取屏幕，请检查权限");if(running)next();}
        });}catch(Exception e){captureInFlight=false;busy=false;pause("无法读取屏幕："+e.getMessage());next();}
    }
    boolean tap(PointF point){synchronized(tapLock){if(!frameCurrent()||!host.modes.is(cn.local.winterbear.ModeGate.Mode.BULLSEYE)||!host.foregroundPackage().equals(targetPackage))return false;if(floatingBounds==null||floatingBounds.padded(dp(8)).contains(point.x*config.width,point.y*config.height)){pause("悬浮条挡住点击位置，请拖开后继续");return false;}Path p=new Path();p.moveTo(point.x*config.width,point.y*config.height);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,70)).build();lastAction=SystemClock.uptimeMillis();final long token=generation;gestureInFlight=true;
        boolean accepted=dispatchGesture(g,new GestureResultCallback(){
          private void finish(){if(token==generation)gestureInFlight=false;}
          @Override public void onCompleted(GestureDescription d){finish();}
          @Override public void onCancelled(GestureDescription d){finish();if(token==generation&&running)pause("点击被中断，请确认后继续");}
        },main);if(!accepted){gestureInFlight=false;pause("点击未被系统接受");}return accepted;}}
    double match(Bitmap frame,Bitmap ref,PointF p,float radius){Bitmap a=Vision.patch(frame,p,radius),b=Vision.patch(ref,p,radius);try{return Vision.similarity(a,b);}finally{a.recycle();b.recycle();}}
    void process(Bitmap frame)throws Exception {
        if(!frameCurrent())return;if(frame.getWidth()!=config.width||frame.getHeight()!=config.height){pause("屏幕方向或尺寸改变，请恢复原方向");return;}
        PointF exit=vision.dismissPoint(frame,config.exitRect);if(!frameCurrent())return;
        if(exit!=null){lastKnown=SystemClock.uptimeMillis();if(++exitSeen<2)return;if(exitAttempts>=3){exitAttempts=0;pause("结算退出没有响应，请检查画面后继续");return;}if(tap(exit)){exitSeen=0;exitAttempts++;update("结算完成，自动返回首页");}return;}exitSeen=0;
        Bitmap roi=Vision.crop(frame,config.region);List<Text.Line> lines;try{lines=vision.text(roi);}finally{roi.recycle();}if(!frameCurrent())return;
        for(Text.Line line:lines){String t=line.getText().replace(" ","");if(t.contains("点击任意位置")||t.contains("点击屏幕退出")){Rect r=line.getBoundingBox();tap(new PointF(config.region[0]+r.exactCenterX()/config.width,config.region[1]+r.exactCenterY()/config.height));lastKnown=SystemClock.uptimeMillis();return;}}
        if(match(frame,home,config.start,.025f)>.68){
            exitAttempts=0;
            allowInitialBoard=false;
            lastKnown=SystemClock.uptimeMillis();if(inRound){inRound=false;rounds++;pending=-1;visited.clear();tickets.reset();}
            Bitmap countImage=Vision.crop(frame,config.countRect);Integer actual;try{actual=vision.number(countImage);}finally{countImage.recycle();}
            if(!frameCurrent())return;
            TicketPolicy.Action action=tickets.read(actual);
            if(action==TicketPolicy.Action.WAIT){update("正在核对首页实际券数");return;}
            if(action==TicketPolicy.Action.READ_ERROR){pause("首页券数暂时读不清，请保持右上角券数可见后继续");return;}
            remaining=tickets.remaining;
            if(action==TicketPolicy.Action.DONE){final long doneToken=generation;main.post(()->{if(doneToken==generation&&running)stopRun("首页确认余券为 0，已全部用完");});return;}
            if(action==TicketPolicy.Action.CHECK_BUTTON){
                Vision.MultiplierButton button=Vision.lowBalanceButton(frame,config);
                action=tickets.button(button==null?0:button.value);
                if(action==TicketPolicy.Action.WAIT){update("余券不足 5 张，正在确认右下角按钮");return;}
                if(action==TicketPolicy.Action.BUTTON_ERROR){pause("右下角倍率按钮被遮挡或样式不匹配，请保持按钮可见后继续");return;}
                if(action==TicketPolicy.Action.SWITCH_ERROR){pause("切换 1 倍没有响应，请检查游戏后继续");return;}
                if(action==TicketPolicy.Action.SWITCH){if(tap(button.point)){tickets.switched();update("正在切换 1 倍，等待画面确认");}return;}
            }
            if(starting&&++retries>3){retries=0;pause("开始按钮没有响应，请检查游戏");return;}
            if(tap(config.start)){starting=true;tickets.started();update("按游戏当前倍率开始，回首页后更新余券");}return;
        }
        List<Integer> available=new ArrayList<>();for(int i=0;i<config.targets.size();i++)if(match(frame,board,config.targets.get(i),.018f)>.72)available.add(i);
        if(!available.isEmpty()){
            lastKnown=SystemClock.uptimeMillis();if(starting){starting=false;inRound=true;retries=0;}
            if(!inRound){if(allowInitialBoard&&available.size()==12){inRound=true;allowInitialBoard=false;update("继续已开启的一轮，结束后读取券数");}else{pause("请回到首页或完整的 12 靶子页再运行");return;}}
            if(pending>=0){if(!available.contains(pending)){visited.add(pending);pending=-1;retries=0;}else if(retries++<2){tap(config.targets.get(pending));return;}else{pause("靶子没有响应，请检查画面");return;}}
            available.removeAll(visited);if(!available.isEmpty()){int i=available.get(new Random().nextInt(available.size()));if(tap(config.targets.get(i))){pending=i;update("第 "+(rounds+1)+" 轮 · 靶子 "+(i+1));}}
        }else if(SystemClock.uptimeMillis()-lastKnown>18000)pause("画面不匹配，已暂停");
    }
}
