package cn.local.bullseye;
import android.accessibilityservice.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import com.google.mlkit.vision.text.Text;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class GameService extends AccessibilityService {
    public static volatile GameService instance;
    public volatile boolean running=false,paused=false;volatile boolean busy=false;
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();final Object tapLock=new Object();
    Config config;Vision vision;Bitmap home,board;WindowManager wm;LinearLayout floating;TextView status;Button pauseButton;
    int remaining,rounds,cost=1,pending=-1,retries=0,failures=0;boolean starting=false,inRound=false;long lastAction=0,lastKnown=0;Set<Integer> visited=new HashSet<>();
    boolean allowInitialBoard=true,needsCountSync=false;
    int exitSeen=0,exitAttempts=0;
    @Override protected void onServiceConnected(){instance=this;vision=new Vision();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){pause("系统中断，已暂停");}
    @Override public void onDestroy(){stopRun("服务已关闭");instance=null;worker.shutdownNow();if(vision!=null)vision.close();super.onDestroy();}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public void begin(Config c){if(running||busy){Toast.makeText(this,"上次运行尚未结束，请稍后再开始",Toast.LENGTH_LONG).show();return;}config=c;if(home!=null)home.recycle();if(board!=null)board.recycle();home=BitmapFactory.decodeFile(new File(getFilesDir(),"home.png").toString());board=BitmapFactory.decodeFile(new File(getFilesDir(),"board.png").toString());if(home==null||board==null){Toast.makeText(this,"请重新上传两张截图",Toast.LENGTH_LONG).show();return;}
        remaining=c.count;rounds=0;pending=-1;starting=false;inRound=false;allowInitialBoard=true;needsCountSync=false;exitSeen=0;exitAttempts=0;visited.clear();retries=0;failures=0;running=true;paused=false;lastKnown=SystemClock.uptimeMillis();lastAction=lastKnown;showFloating();update("5 秒后开始");main.postDelayed(this::tick,5000);
    }
    void showFloating(){
        floating=new LinearLayout(this);floating.setOrientation(LinearLayout.VERTICAL);floating.setPadding(dp(8),dp(4),dp(8),dp(4));floating.setBackgroundColor(Color.rgb(237,247,240));
        TextView version=new TextView(this);version.setText(R.string.app_version_label);version.setTextColor(Color.rgb(20,70,45));version.setTextSize(13);version.setTypeface(null,Typeface.BOLD);floating.addView(version);
        status=new TextView(this);status.setTextColor(Color.rgb(20,70,45));status.setTextSize(12);floating.addView(status);
        LinearLayout buttons=new LinearLayout(this);pauseButton=new Button(this);pauseButton.setText("暂停");pauseButton.setAllCaps(false);pauseButton.setMinHeight(dp(48));pauseButton.setOnClickListener(v->{if(paused){paused=false;lastKnown=SystemClock.uptimeMillis();lastAction=lastKnown;update("继续识别");}else pause("已暂停");});buttons.addView(pauseButton);
        Button stop=new Button(this);stop.setText("停止");stop.setAllCaps(false);stop.setMinHeight(dp(48));stop.setOnClickListener(v->stopRun("已停止"));buttons.addView(stop);floating.addView(buttons);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.END;p.y=dp(48);wm.addView(floating,p);
    }
    void update(String message){getSharedPreferences("progress",0).edit().putInt("remaining",remaining).putInt("rounds",rounds).putString("state",message).apply();main.post(()->{if(status!=null)status.setText("剩余 "+remaining+" · "+message);if(pauseButton!=null)pauseButton.setText(paused?"继续":"暂停");});}
    void pause(String message){synchronized(tapLock){paused=true;}update(message);}
    public void stopRun(String message){synchronized(tapLock){running=false;paused=false;}main.removeCallbacksAndMessages(null);if(floating!=null){try{wm.removeView(floating);}catch(Exception ignored){}floating=null;status=null;pauseButton=null;}getSharedPreferences("progress",0).edit().putInt("remaining",remaining).putInt("rounds",rounds).putString("state",message).apply();Toast.makeText(this,message+"，剩余 "+remaining+" 张",Toast.LENGTH_LONG).show();}
    void next(){main.postDelayed(this::tick,700);}
    void tick(){
        if(!running)return;if(paused||busy||SystemClock.uptimeMillis()-lastAction<2400){next();return;}busy=true;
        takeScreenshot(Display.DEFAULT_DISPLAY,worker,new TakeScreenshotCallback(){
            @Override public void onSuccess(ScreenshotResult s){Bitmap hw=null,frame=null;try{hw=Bitmap.wrapHardwareBuffer(s.getHardwareBuffer(),s.getColorSpace());if(hw==null)throw new Exception("无法读取屏幕");frame=hw.copy(Bitmap.Config.ARGB_8888,false);process(frame);failures=0;}catch(Exception e){pause("识别异常："+e.getMessage());}finally{if(frame!=null)frame.recycle();if(hw!=null)hw.recycle();s.getHardwareBuffer().close();busy=false;if(running)next();}}
            @Override public void onFailure(int error){busy=false;if(++failures>=3)pause("无法读取屏幕，请检查权限");if(running)next();}
        });
    }
    boolean tap(PointF point){synchronized(tapLock){if(!running||paused)return false;Path p=new Path();p.moveTo(point.x*config.width,point.y*config.height);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,70)).build();lastAction=SystemClock.uptimeMillis();return dispatchGesture(g,new GestureResultCallback(){@Override public void onCancelled(GestureDescription d){pause("点击被中断，请确认后继续");}},main);}}
    double match(Bitmap frame,Bitmap ref,PointF p,float radius){Bitmap a=Vision.patch(frame,p,radius),b=Vision.patch(ref,p,radius);try{return Vision.similarity(a,b);}finally{a.recycle();b.recycle();}}
    void process(Bitmap frame)throws Exception {
        if(!running||paused)return;if(frame.getWidth()!=config.width||frame.getHeight()!=config.height){pause("屏幕方向或尺寸改变，请恢复原方向");return;}
        PointF exit=vision.dismissPoint(frame,config.exitRect);if(!running||paused)return;
        if(exit!=null){lastKnown=SystemClock.uptimeMillis();if(++exitSeen<2)return;if(exitAttempts>=3){exitAttempts=0;pause("结算退出没有响应，请检查画面后继续");return;}if(tap(exit)){exitSeen=0;exitAttempts++;update("结算完成，自动返回首页");}return;}exitSeen=0;
        Bitmap roi=Vision.crop(frame,config.region);List<Text.Line> lines;try{lines=vision.text(roi);}finally{roi.recycle();}if(!running||paused)return;
        for(Text.Line line:lines){String t=line.getText().replace(" ","");if(t.contains("点击任意位置")||t.contains("点击屏幕退出")){Rect r=line.getBoundingBox();tap(new PointF(config.region[0]+r.exactCenterX()/config.width,config.region[1]+r.exactCenterY()/config.height));lastKnown=SystemClock.uptimeMillis();return;}}
        if(match(frame,home,config.start,.025f)>.68){
            exitAttempts=0;
            allowInitialBoard=false;
            lastKnown=SystemClock.uptimeMillis();if(inRound){inRound=false;rounds++;pending=-1;visited.clear();}
            Bitmap countImage=Vision.crop(frame,config.countRect);Integer actual;try{actual=vision.number(countImage);}finally{countImage.recycle();}if(needsCountSync&&actual==null){pause("当前轮已结束，但券数未读清，请保持券数可见后继续");return;}if(actual!=null)remaining=Math.min(remaining,actual);needsCountSync=false;
            if(remaining<=0){main.post(()->stopRun("设定次数已用完"));return;}
            cost=config.multiplier;
            if(remaining<cost){main.post(()->stopRun("剩余券数不足所选倍率，请将游戏和助手都改为 1 倍后重新开始"));return;}
            if(starting&&++retries>3){pause("开始按钮没有响应，请检查游戏");return;}
            if(tap(config.start)){starting=true;update("按所选 "+cost+" 倍开始下一轮");}return;
        }
        List<Integer> available=new ArrayList<>();for(int i=0;i<config.targets.size();i++)if(match(frame,board,config.targets.get(i),.018f)>.72)available.add(i);
        if(!available.isEmpty()){
            lastKnown=SystemClock.uptimeMillis();if(starting){starting=false;inRound=true;remaining=Math.max(0,remaining-cost);retries=0;}
            if(!inRound){if(allowInitialBoard&&available.size()==12){inRound=true;allowInitialBoard=false;needsCountSync=true;update("继续已开启的一轮，结束后读取券数");}else{pause("请回到首页或完整的 12 靶子页再运行");return;}}
            if(pending>=0){if(!available.contains(pending)){visited.add(pending);pending=-1;retries=0;}else if(retries++<2){tap(config.targets.get(pending));return;}else{pause("靶子没有响应，请检查画面");return;}}
            available.removeAll(visited);if(!available.isEmpty()){int i=available.get(new Random().nextInt(available.size()));if(tap(config.targets.get(i))){pending=i;update("第 "+(rounds+1)+" 轮 · 靶子 "+(i+1));}}
        }else if(SystemClock.uptimeMillis()-lastKnown>18000)pause("画面不匹配，已暂停");
    }
}
