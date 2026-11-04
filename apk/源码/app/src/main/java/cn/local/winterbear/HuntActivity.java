package cn.local.winterbear;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.text.InputType;
import android.view.WindowInsets;
import android.widget.*;

/** New task settings are session-only; leaving and starting a new task resets the can allowance. */
public final class HuntActivity extends Activity {
    private HuntFlow.Mode mode;private int level,limit=1,canLimit=100,minutes=60;private boolean cans,hadTask;
    @Override public void onCreate(Bundle state){super.onCreate(state);mode=getIntent().getBooleanExtra("giant",true)?HuntFlow.Mode.GIANT:HuntFlow.Mode.BEAST;level=mode==HuntFlow.Mode.GIANT?8:30;}
    @Override public void onResume(){
        super.onResume();BearService s=BearService.current();
        if(hadTask&&(s==null||!s.hasHuntTask())){level=mode==HuntFlow.Mode.GIANT?8:30;limit=1;canLimit=100;minutes=60;cans=false;hadTask=false;}
        if(s!=null)s.pauseHunt();render();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);LinearLayout root=Ui.column(this);int pad=Ui.dp(this,20);root.setPadding(pad,pad,pad,pad);scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});
        Ui.button(this,root,"返回活动选择",false,()->{BearService s=BearService.current();if(s!=null)s.stopAllModes();finish();});
        root.addView(Ui.text(this,mode==HuntFlow.Mode.GIANT?"巨兽集结":"野兽集结",28,Ui.TEXT));
        LinearLayout settings=Ui.card(this,root,"本次任务",null);
        Ui.button(this,settings,"目标等级  "+level,false,()->number("目标等级",level,1,99,n->level=n));
        String activity=mode==HuntFlow.Mode.GIANT?"巨兽":"野兽";
        Ui.button(this,settings,activity+"同时派出  "+limit+" 队",false,()->new AlertDialog.Builder(this).setTitle(activity+"同时派出的队数").setSingleChoiceItems(new String[]{"1 队","2 队","3 队","4 队","5 队","6 队"},limit-1,(d,n)->{limit=n+1;d.dismiss();render();}).show());
        settings.addView(Ui.text(this,"只计算本次任务队伍，回城后自动补队。总行军满 6 队时等待空位。",14,Ui.MUTED));
        Ui.button(this,settings,"运行时长  "+minutes+" 分钟",false,()->number("运行分钟数",minutes,1,240,n->minutes=n));
        LinearLayout energy=Ui.card(this,root,"体力补充",null);Switch on=new Switch(this);on.setText("自动使用库存罐头");on.setTextColor(Ui.TEXT);on.setMinHeight(Ui.dp(this,52));on.setChecked(cans);energy.addView(on);on.setOnCheckedChangeListener((v,b)->{cans=b;render();});
        if(cans){Ui.button(this,energy,"本次罐头上限  "+canLimit+" 个",false,()->number("本次最多使用罐头",canLimit,1,9999,n->canLimit=n));energy.addView(Ui.text(this,"每次补 4 个，最后按剩余额度补齐；库存不足本次数量时停止。",14,Ui.MUTED));}
        BearService service=BearService.current();
        Ui.button(this,root,service==null?"开启无障碍权限":"去游戏开始",true,()->{
            BearService s=BearService.current();if(s==null){PermissionActivity.open(this);return;}
            s.prepareHunt(new HuntFlow.Options(mode,level,limit,cans?canLimit:0,minutes));hadTask=true;moveTaskToBack(true);
            Toast.makeText(this,"回野外地图，点悬浮窗开始",Toast.LENGTH_LONG).show();
        });
        root.addView(Ui.text(this,"退出后下次重新设置。",13,Ui.MUTED));
    }
    private interface Save {void set(int value);}
    private void number(String title,int value,int min,int max,Save save){
        EditText field=new EditText(this);field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setText(String.valueOf(value));field.selectAll();
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(field).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{int n;try{n=Integer.parseInt(field.getText().toString().trim());}catch(Exception e){field.setError("请输入数字");return;}if(n<min||n>max){field.setError("范围 "+min+"–"+max);return;}save.set(n);d.dismiss();render();}));d.show();
    }
    @Override public void onBackPressed(){BearService s=BearService.current();if(s!=null)s.stopAllModes();super.onBackPressed();}
    @Override public void onDestroy(){if(isFinishing()&&hadTask){BearService s=BearService.current();if(s!=null)s.stopAllModes();}super.onDestroy();}
}
