package cn.local.winterbear;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;

/** Opens supported system settings; never grants privileges or changes security settings. */
public final class PermissionActivity extends Activity {
    private TextView status;
    private boolean waitingForSettings,leftForSettings;
    private final Handler handler=new Handler();
    public static void open(Activity activity){activity.startActivity(new Intent(activity,PermissionActivity.class));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.column(this);int p=Ui.dp(this,20);root.setPadding(p,p,p,p);scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});
        Ui.button(this,root,"返回",false,this::finish);
        root.addView(Ui.text(this,"屏幕操作权限",26,Ui.TEXT));
        status=Ui.text(this,"",16,Ui.ACCENT);root.addView(status);
        root.addView(Ui.text(this,"用于识别游戏画面、显示控制浮窗和点击。画面在本机处理。只有你启动任务后才操作，可随时暂停或结束。",15,Ui.TEXT));
        Ui.button(this,root,"打开无障碍设置",true,this::accessibility);
        Ui.button(this,root,"被“敏感权限 / 受限设置”拦住",false,this::restrictionHelp);
        Ui.button(this,root,"找不到助手，或设置打不开",false,this::capabilityHelp);
        root.addView(Ui.text(this,"无须额外授予通讯录、存储、定位或后台弹窗权限。",14,Ui.MUTED));
    }
    @Override public void onResume(){
        super.onResume();
        if(BearService.current()!=null)status.setText("已开启，可以返回活动开始。");
        else if(waitingForSettings&&leftForSettings)status.setText("尚未连接。若开关被锁住，请先查看下方“敏感权限 / 受限设置”。");
        else status.setText("由你在系统中开启“无尽冬日助手”。");
        if(waitingForSettings&&leftForSettings)waitingForSettings=false;
    }
    @Override public void onPause(){if(waitingForSettings)leftForSettings=true;super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private boolean launch(Intent intent){
        try{
            waitingForSettings=true;leftForSettings=false;startActivity(intent);
            handler.postDelayed(()->{if(!isFinishing()&&waitingForSettings&&!leftForSettings){waitingForSettings=false;status.setText("系统没有打开设置入口，请查看“设置打不开”。");}},1500);
            return true;
        }catch(android.content.ActivityNotFoundException|SecurityException e){waitingForSettings=false;return false;}
    }
    private void accessibility(){
        // Optional system route; not part of the public SDK on every Android version.
        Intent detail=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(this,BearService.class));
        if(!launch(detail)&&!launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))capabilityHelp();
    }
    private void restrictionHelp(){
        new AlertDialog.Builder(this).setTitle("先解除系统的前置限制")
            .setMessage("这与无障碍开关是两件事。系统限制未解除时，无障碍可能无法开启。\n\n安卓：在本应用信息页查看“允许受限设置”，或系统提示中的“去允许应用获取权限”。\n\nHarmonyOS 6 / 7 的卓易通应用：在系统设置 → 隐私和安全 → 高级 → 兼容应用管控中心，查看本应用是否被管控。是否允许解除，以系统显示为准。\n\n助手不能自行解除系统限制，也不能通过更改签名保证去掉拦截。")
            .setNegativeButton("返回",null).setNeutralButton("系统设置",(d,w)->{if(!launch(new Intent(Settings.ACTION_SETTINGS)))capabilityHelp();})
            .setPositiveButton("本应用信息",(d,w)->{if(!launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))))capabilityHelp();}).show();
    }
    private void capabilityHelp(){
        boolean registered=false;
        try{
            AccessibilityManager manager=getSystemService(AccessibilityManager.class);
            if(manager!=null)for(AccessibilityServiceInfo info:manager.getInstalledAccessibilityServiceList()){
                if(info.getResolveInfo()!=null&&info.getResolveInfo().serviceInfo!=null&&getPackageName().equals(info.getResolveInfo().serviceInfo.packageName))registered=true;
            }
        }catch(RuntimeException ignored){}
        new AlertDialog.Builder(this).setTitle("检查系统支持")
            .setMessage((registered?"系统已列出助手服务。":"当前系统暂未列出助手服务；这不等于用户拒绝了权限。")
                +"\n\n可以打开系统无障碍列表检查。若卓易通中找不到该服务，或点设置后仍返回助手，需要确认兼容环境是否提供无障碍、截屏和跨应用点击能力。仅允许“后台弹窗”不能替代这些能力。")
            .setNegativeButton("返回",null).setPositiveButton("系统无障碍列表",(d,w)->{if(!launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))status.setText("系统未提供可打开的无障碍入口。请从手机系统设置检查。");}).show();
    }
}
