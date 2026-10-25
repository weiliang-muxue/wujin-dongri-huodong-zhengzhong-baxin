package cn.local.winterbear;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private Config config;
    private TextView status;
    private boolean checkingOcr,advancedOpen,teamsOpen,lastServiceState;
    private ScrollView page;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        BearService service=BearService.current();
        boolean active=service!=null;
        if(lastServiceState!=active){render();lastServiceState=active;}
        if(status!=null)status.setText(service==null?"服务尚未开启，请完成第 1 步":service.status());
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state){super.onCreate(state);config=new Config(this);}
    @Override public void onResume(){
        super.onResume();
        BearService service=BearService.current();
        if(service!=null)service.pause("已回到助手；游戏操作处于暂停状态");
        render();handler.removeCallbacks(refresh);handler.post(refresh);
    }
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void render(){
        int oldY=page==null?0:page.getScrollY();
        page=new ScrollView(this);page.setFillViewport(true);page.setBackgroundColor(Ui.BG);
        LinearLayout content=Ui.column(this);int gutter=Ui.dp(this,20);content.setPadding(gutter,gutter,gutter,gutter);
        page.addView(content);setContentView(page);
        page.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(b.left,b.top,b.right,b.bottom);return insets;
        });
        Ui.button(this,content,"返回活动选择",false,()->{startActivity(new Intent(this,ModeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
        content.addView(Ui.text(this,"打熊",28,Ui.TEXT));
        boolean serviceOn=BearService.current()!=null;lastServiceState=serviceOn;
        boolean connected=config.mapVerified();
        LinearLayout live=Ui.card(this,content,connected?"准备好了":"首次准备",null);
        status=Ui.text(this,serviceOn?BearService.current().status():"先允许助手读取画面和点击",15,Ui.ACCENT);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);live.addView(status);
        if(!serviceOn){Ui.button(this,live,"开启权限",true,this::openAccessibility);}
        else if(!connected){
            note(live,"打开游戏的野外地图，点悬浮窗「连接游戏」。");
            Ui.button(this,live,"去连接游戏",true,this::connectMap);
        }else{
            note(live,"打开游戏地图，点悬浮窗「开始打熊」。");
            Ui.button(this,live,"去游戏打熊",true,()->chooseMode(false));
            Ui.button(this,live,"试识别",false,()->chooseMode(true));
        }
        LinearLayout settings=Ui.card(this,content,"编队与时长","默认使用前 6 个已保存预设；本次最多 "+config.minutes()+" 分钟。下一次使用第 "+(config.p.getInt("nextPreset",0)+1)+" 个。");
        Ui.button(this,settings,teamsOpen?"收起调整":"调整编队与时长",false,()->{teamsOpen=!teamsOpen;render();});
        if(teamsOpen){
            Ui.button(this,settings,"本次时长："+config.minutes()+" 分钟",false,()->number("本次运行分钟数（1–30）","minutes",config.minutes(),1,30));
            Ui.button(this,settings,"下次使用第 "+(config.p.getInt("nextPreset",0)+1)+" 个预设",false,()->number("下次预设编号（1–6）","nextPreset",config.p.getInt("nextPreset",0)+1,1,6));
            note(settings,"旗帜栏保持最左端。位置已载入不代表英雄已回城；正式出征时仍会检查画面。");
            for(int i=0;i<6;i++){
                final int slot=i;
                Switch enabled=new Switch(this);enabled.setText(config.label(i));enabled.setTextSize(16);enabled.setTextColor(Ui.TEXT);enabled.setMinHeight(Ui.dp(this,52));
                enabled.setChecked(config.enabled(i));settings.addView(enabled);
                enabled.setOnCheckedChangeListener((v,on)->{
                    if(on&&!config.ready(slot)){enabled.setChecked(false);toast("先连接游戏自动载入位置，或在高级设置录入旗帜");return;}
                    config.p.edit().putBoolean("enabled"+slot,on).apply();
                });
            }
        }
        LinearLayout advanced=Ui.card(this,content,"高级设置与排查","只有自动连接失败、画面错位或数字读不清时才需要。");
        Ui.button(this,advanced,advancedOpen?"收起高级设置":"展开高级设置",false,()->{advancedOpen=!advancedOpen;render();});
        if(advancedOpen){
            Ui.button(this,advanced,"无障碍被“受限设置”拦住",false,this::permissionHelp);
            Ui.button(this,advanced,"手动框选行军文字与数字",false,()->calibrate("march",0));
            Ui.button(this,advanced,"补录实际巨熊与绿色＋",false,()->calibrate("rally",0));
            Ui.button(this,advanced,"检查内置测试图片（无需打开游戏）",false,this::checkOcr);
            note(advanced,"此项只识别 APK 内的固定 5/6 图片。它不会读取游戏，结果也不会用于判断是否上车。");
            Ui.button(this,advanced,"重新载入参考布局（1080 × 2400）",false,()->new AlertDialog.Builder(this)
                .setTitle("重新载入布局").setMessage("会替换入口、地图、出征页和前六个旗帜的参考取样，恢复只识别。已有巨熊样本保留；之后需重新连接游戏。")
                .setNegativeButton("取消",null).setPositiveButton("载入",(d,w)->{
                    try{config.importReferenceLayout();render();toast("布局已载入，尚未验证游戏。请点“连接游戏地图”");}
                    catch(Exception e){message("无法载入",e.getMessage());}
                }).show());
            sampleButton(advanced,"header","集结页标题",0);
            sampleButton(advanced,"deploy","出征按钮",0);
            for(String[] nav:Config.NAV)sampleButton(advanced,nav[0],nav[1],0);
            for(int i=0;i<6;i++)sampleButton(advanced,"preset","第 "+(i+1)+" 个预设旗帜",i);
            Ui.button(this,advanced,"匹配阈值："+Math.round(config.threshold()*100)+"%",false,()->number("匹配阈值（80–98）","threshold",Math.round(config.threshold()*100),80,98));
            Ui.button(this,advanced,"清除取样，重新连接游戏",false,()->new AlertDialog.Builder(this).setTitle("清除取样")
                .setMessage("会清除本机已录入的页面、巨熊与预设位置，恢复首次准备。")
                .setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{config.clearCalibration();render();}).show());
        }
        Ui.button(this,content,"遇到问题？",false,this::showHelp);
        Ui.button(this,content,"查看运行记录",false,()->{BearService service=BearService.current();message("运行记录",service==null?"本次尚无记录":service.logs());});
        ScrollView current=page;current.post(()->current.scrollTo(0,oldY));
    }
    private void note(LinearLayout parent,String text){TextView v=Ui.text(this,text,15,Ui.MUTED);v.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));parent.addView(v);}
    private void step(LinearLayout parent,String title,String detail){TextView v=Ui.text(this,title,18,Ui.TEXT);v.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,4));parent.addView(v);parent.addView(Ui.text(this,detail,15,Ui.MUTED));}
    private void sampleButton(LinearLayout parent,String mode,String label,int slot){Ui.button(this,parent,"手动取样："+label,false,()->calibrate(mode,slot));}
    private void message(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show();}
    private BearService service(){BearService svc=BearService.current();if(svc==null)openAccessibility();return svc;}
    private void connectMap(){BearService svc=service();if(svc==null)return;svc.prepareCapture("auto_map",0);moveTaskToBack(true);toast("打开真实游戏的野外地图，再点悬浮窗“连接游戏”");}
    private void calibrate(String mode,int slot){BearService svc=service();if(svc==null)return;svc.prepareCapture(mode,slot);moveTaskToBack(true);}
    private void chooseMode(boolean dry){
        BearService svc=service();if(svc==null)return;
        if(!config.mapVerified()){connectMap();return;}
        if(!dry){
            String error=config.automaticError();
            if(error!=null){
                new AlertDialog.Builder(this).setTitle("还需完成准备").setMessage(error)
                    .setNegativeButton("返回",null).setPositiveButton(config.rallyReady()?"查看高级设置":"学习巨熊",(d,w)->{
                        if(!config.rallyReady())calibrate("rally",0);
                        else {advancedOpen=true;render();}
                    }).show();return;
            }
        }
        svc.prepareRun(dry);moveTaskToBack(true);
        toast(dry?"回游戏地图，点“试识别”":"回游戏地图，点“开始打熊”");
    }
    private void openAccessibility(){
        new AlertDialog.Builder(this).setTitle("允许截图与点击")
            .setMessage("助手需要无障碍服务来读取游戏画面、显示悬浮窗和执行你启动的跟车操作。截图只在手机本地处理。\n\n到系统设置找到“无尽冬日助手（打熊 / 正中靶心）”并手动开启。若显示“受限设置”，可点下方帮助。")
            .setNegativeButton("暂不",null).setNeutralButton("受限设置帮助",(d,w)->permissionHelp())
            .setPositiveButton("前往开启",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }
    private void permissionHelp(){
        new AlertDialog.Builder(this).setTitle("无障碍被限制怎么办")
            .setMessage("1. 打开“应用信息”。\n2. 点右上角三个点 ⋮，选择“允许受限设置”，按系统提示确认。\n3. 回无障碍设置开启“无尽冬日助手（打熊 / 正中靶心）”。\n\n小米页面若没有该选项，点系统提示中的“了解如何获取权限”。不需要把普通权限列表全部打开。")
            .setNegativeButton("返回",null).setNeutralButton("无障碍设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setPositiveButton("打开应用信息",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show();
    }
    private void showHelp(){
        String[] titles={"第一次怎么用 / 地图为什么只需一步","以后每次怎么开打","行军数、红色 X、绿色＋有什么区别","无法识别 / 手动取样","无障碍打不开 / 受限设置","样本测试为什么不用打开游戏"};
        new AlertDialog.Builder(this).setTitle("使用说明与常见问题").setItems(titles,(d,which)->{
            switch(which){
                case 0:message(titles[which],"① 手动开启无障碍服务。\n② 点“连接游戏地图”，打开真实野外地图，点悬浮窗“连接游戏”。没有行军栏也可以连接，无需手动展开。\n③ 固定的“等级1 变异巨熊”、绿色＋和前六个预设位置会自动载入，正常情况不用手动取样。\n\n选择“去游戏打熊”，回地图点开始。出征后继续留在列表加入下一队；本批空位用完、全部预设暂不可用或信息过期时才回地图。");break;
                case 1:message(titles[which],"提前保存好前 6 个预设，旗帜栏保持最左端。游戏自带的自动加入可以保留。\n\n打开助手 → 去游戏打熊 → 回野外地图 → 悬浮窗开始。运行时自动收起多余按钮，暂停键始终可见。\n\n按 1→6 使用预设；空兵、超编会换下一个，不改你的预设配置。即使只有 1 个士兵，只要游戏显示编队有效，也按保存的配置使用。\n\n默认本次 30 分钟。可调整剩余分钟数。暂停后重新开始会重新计时，手动操作游戏前请先暂停。");break;
                case 2:message(titles[which],"行军 x/6：自己的总行军数量。0～5 表示还有位置，6 表示已满。\n红色 X：进入联盟集结列表的快捷入口，它本身不能证明有空队列。\n绿色＋：这一行别人的巨熊集结可以尝试加入，灰色或满队跳过。\n\n同一张地图截图可以同时找行军数和红色入口。进入列表、选编队和出征后，助手会继续自动截取新画面，不需要你逐张截图。\n\n自己的预设按 1→6 轮换。行军数只说明总空位，不能保证某个预设的英雄已返回。");break;
                case 3:message(titles[which],"连接失败：确认是真实野外地图，不要停在相册或截图预览。正常情况不需要展开左侧队列。\n\n地图标记不匹配时，才需要在有行军栏的画面手动框选“行军”和完整 x/6。\n\n明明有巨熊但识别不到：在高级设置补录巨熊固定标记和绿色＋。预设位置不匹配时补录对应旗帜。参考布局对应 1080×2400。\n\n已知的“士兵超出出征上限”提示会关闭后换预设。集结满员、发车或进入详情会返回列表换队；未知弹窗不会乱点。\n\n“已提交”表示点出征后回到了列表，不保证服务器已经上车成功；不会再要求地图数字必须净增加。");break;
                case 4:permissionHelp();break;
                default:message(titles[which],"“检查内置测试图片”读取的是应用内固定的 5/6 图片，用来检查手机的数字识别功能。不开游戏也能运行。\n\n它不会读取当前游戏，不会绑定游戏，不会证明还有空位，也不会开启点击。\n\n首页“连接游戏地图”和“只看实时识别”读取的才是当前画面。");
            }
        }).setNegativeButton("关闭",null).show();
    }
    private void number(String title,String key,int value,int min,int max){
        EditText field=new EditText(this);field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setText(String.valueOf(value));field.setSelectAllOnFocus(true);field.setContentDescription(title);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(field).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int next;try{next=Integer.parseInt(field.getText().toString().trim());}catch(Exception e){field.setError("请输入数字");return;}
            if(next<min||next>max){field.setError("范围为 "+min+"–"+max);return;}
            if(key.equals("threshold"))config.p.edit().putFloat(key,next/100f).apply();
            else config.p.edit().putInt(key,key.equals("nextPreset")?next-1:next).apply();
            dialog.dismiss();render();
        }));dialog.show();
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void checkOcr(){
        if(checkingOcr)return;checkingOcr=true;
        LinearLayout panel=Ui.column(this);panel.setPadding(Ui.dp(this,24),Ui.dp(this,8),Ui.dp(this,24),Ui.dp(this,8));
        TextView result=Ui.text(this,"正在读取下方固定测试图片…",16,Ui.TEXT);panel.addView(result);
        ImageView sample=new ImageView(this);sample.setContentDescription("应用内置测试图片，数字为 5/6");sample.setAdjustViewBounds(true);
        try(java.io.InputStream in=getAssets().open("reference/counter_sample.png")){
            sample.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(in,null));
        }catch(Exception ignored){}
        LinearLayout.LayoutParams imageSize=new LinearLayout.LayoutParams(Ui.dp(this,120),Ui.dp(this,90));imageSize.topMargin=Ui.dp(this,12);panel.addView(sample,imageSize);
        note(panel,"测试对象：应用内置图片。\n不会读取游戏，也不代表当前行军空位。");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("内置图片测试 · 非实时").setView(panel).setPositiveButton("关闭",null).show();
        new Thread(()->{
            int used=-1;
            try(CounterOcr ocr=new CounterOcr();java.io.InputStream in=getAssets().open("reference/counter_sample.png")){
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(in);
                try{used=ocr.read(bitmap,new android.graphics.Rect(0,0,bitmap.getWidth(),bitmap.getHeight()));}finally{bitmap.recycle();}
            }catch(Exception ignored){}
            final int count=used;
            runOnUiThread(()->{checkingOcr=false;if(!isFinishing()&&!isDestroyed()&&dialog.isShowing())result.setText(count==5?"测试通过：内置图片读到 5/6。":"未正确读出测试图片，请重试。实时识别仍需在游戏地图检查。");});
        },"counter-self-check").start();
    }
}
