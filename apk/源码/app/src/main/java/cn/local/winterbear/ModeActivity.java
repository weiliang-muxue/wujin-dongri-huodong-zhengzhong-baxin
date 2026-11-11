package cn.local.winterbear;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.*;

public final class ModeActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.column(this);int pad=Ui.dp(this,24);root.setPadding(pad,pad,pad,pad);scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        root.addView(Ui.text(this,"无尽冬日助手",28,Ui.TEXT));
        root.addView(Ui.text(this,"选择本次活动",17,Ui.MUTED));
        Button bear=Ui.button(this,root,"打熊",true,()->open(MainActivity.class));bear.setMinHeight(Ui.dp(this,96));bear.setTextSize(24);
        Button target=Ui.button(this,root,"正中靶心",true,()->open(cn.local.bullseye.MainActivity.class));target.setMinHeight(Ui.dp(this,96));target.setTextSize(24);
        Button giant=Ui.button(this,root,"巨兽集结",true,()->startActivity(new Intent(this,HuntActivity.class).putExtra("giant",true)));giant.setMinHeight(Ui.dp(this,80));giant.setTextSize(24);
        Button beast=Ui.button(this,root,"野兽集结",true,()->startActivity(new Intent(this,HuntActivity.class).putExtra("giant",false)));beast.setMinHeight(Ui.dp(this,80));beast.setTextSize(24);
        root.addView(Ui.text(this,"2.1.10",12,Ui.MUTED));
    }
    @Override public void onResume(){super.onResume();BearService s=BearService.current();if(s!=null)s.stopAllModes();}
    private void open(Class<?> type){startActivity(new Intent(this,type));}
}
