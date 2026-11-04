package cn.local.winterbear;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.Switch;
import java.io.*;
import java.util.*;
import static cn.local.winterbear.HuntFlow.*;

/** Dedicated emulator only: real bundled ML Kit and app views, no game gestures. */
public final class HuntDeviceCheck extends Instrumentation {
    private void report(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    private void labels(View v,Set<String> text){if(v instanceof TextView)text.add(((TextView)v).getText().toString());if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)labels(g.getChildAt(i),text);}}
    private Switch canSwitch(View view){if(view instanceof Switch)return (Switch)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Switch found=canSwitch(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private void screen(Class<?> type,boolean giant,String name,String... expected)throws Exception{
        Activity a=startActivitySync(new Intent(getTargetContext(),type).putExtra("giant",giant).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        Set<String> text=new HashSet<>();runOnMainSync(()->labels(a.getWindow().getDecorView(),text));for(String label:expected)require(text.contains(label),"UI missing "+label);
        Bitmap screenshot=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
        if(type==HuntActivity.class){runOnMainSync(()->{Switch toggle=canSwitch(a.getWindow().getDecorView());require(toggle!=null&&!toggle.isChecked(),"Cans should start disabled");toggle.setChecked(true);});}
        runOnMainSync(a::finish);
        if(type==HuntActivity.class){Activity fresh=startActivitySync(new Intent(getTargetContext(),type).putExtra("giant",giant).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();runOnMainSync(()->{require(!canSwitch(fresh.getWindow().getDecorView()).isChecked(),"New task retained can settings");fresh.finish();});}
        report("HUNT UI PASS: "+name);
    }
    private void foldCheck()throws Exception{
        Activity a=startActivitySync(new Intent(getTargetContext(),ModeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        LinearLayout[] root={null};TextView[] title={null},status={null};OverlayFold[] folding={null};View[] parent={null};int[] resizes={0};
        runOnMainSync(()->{
            FrameLayout canvas=new FrameLayout(a);canvas.setBackgroundColor(Ui.BG);a.setContentView(canvas);
            root[0]=Ui.column(a);root[0].setBackground(Ui.shape(a,Ui.CARD,12));title[0]=Ui.text(a,"巨兽 ⠿ 拖动",14,Ui.ACCENT);root[0].addView(title[0]);
            status[0]=Ui.text(a,"运行中",12,Ui.TEXT);root[0].addView(status[0],new LinearLayout.LayoutParams(-1,40));
            canvas.addView(root[0],new FrameLayout.LayoutParams(260,-2));parent[0]=canvas;
            folding[0]=new OverlayFold(a,root[0],title[0],"巨兽",260,72,48,w->{resizes[0]++;ViewGroup.LayoutParams p=root[0].getLayoutParams();p.width=w;root[0].setLayoutParams(p);});
        });waitForIdleSync();int[] initial={0};runOnMainSync(()->{initial[0]=root[0].getHeight();folding[0].setCollapsed(true);});waitForIdleSync();
        runOnMainSync(()->{require(root[0].getWidth()==72&&root[0].getHeight()<initial[0],"Fold did not reduce bounds");for(int i=0;i<200;i++){status[0].setText("暂停 / 识别状态 "+i);status[0].setVisibility(View.VISIBLE);folding[0].setCollapsed(true);}});waitForIdleSync();
        runOnMainSync(()->{require(folding[0].isCollapsed()&&root[0].getWidth()==72&&root[0].getVisibility()==View.VISIBLE&&root[0].getParent()==parent[0]&&resizes[0]==1,"Status update expanded/recreated floating view");});
        Bitmap shot=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"ui-folded.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
        runOnMainSync(()->title[0].performClick());waitForIdleSync();runOnMainSync(()->{require(!folding[0].isCollapsed()&&root[0].getWidth()==260&&resizes[0]==2,"Tap did not restore panel");a.finish();});
        report("OVERLAY FOLD PASS: shrink, tap-expand, same view retained through 200 status updates");
    }
    @Override public void onStart(){Bundle output=new Bundle();try{
        screen(ModeActivity.class,true,"ui-modes","打熊","正中靶心","巨兽集结","野兽集结");
        screen(HuntActivity.class,true,"ui-giant","巨兽集结","目标等级  8","巨兽同时派出  1 队");
        screen(HuntActivity.class,false,"ui-beast","野兽集结","目标等级  30","自动使用库存罐头");
        screen(PermissionActivity.class,true,"ui-permissions","屏幕操作权限","被“敏感权限 / 受限设置”拦住","找不到助手，或设置打不开");
        foldCheck();
        require(org.opencv.android.OpenCVLoader.initLocal(),"OpenCV initialization");
        File fixtures=new File(getTargetContext().getFilesDir(),"hunt-fixtures");
        try(CounterOcr ocr=new CounterOcr();HuntVision v=new HuntVision(getTargetContext().getAssets()::open,ocr::text)){
            String[] names={"giant_search","beast_search","giant_detail","beast_detail","map_one","giant_formation","energy"};
            for(String name:names){
                Bitmap b=BitmapFactory.decodeFile(new File(fixtures,name+".png").getAbsolutePath());require(b!=null,"Missing fixture "+name);
                boolean ok=false;Frame f=null;
                try{for(int retry=0;retry<4&&!ok;retry++){
                    f=v.observe(b,name.startsWith("beast")?Mode.BEAST:Mode.GIANT);
                    if(name.equals("energy"))ok=f.page==Page.ENERGY&&f.stock==232&&f.energy==15;
                    else if(name.equals("map_one"))ok=f.page==Page.MAP&&f.used==1;
                    else if(name.endsWith("formation"))ok=f.page==Page.FORMATION&&f.points[Kind.DEPLOY.ordinal()][0]>0;
                    else ok=f.level==(name.startsWith("beast")?30:8);
                    if(!ok)report("OCR retry "+name+" level="+f.level+" used="+f.used+" stock="+f.stock+" energy="+f.energy);
                }}finally{b.recycle();}
                require(ok,"Real OCR failed "+name);report("HUNT REAL OCR PASS: "+name);
            }
        }
        output.putString("stream","PASS: 7 actual bundled OCR fixtures; 4 app screens; no accessibility or game interaction\n");finish(Activity.RESULT_OK,output);
    }catch(Throwable e){output.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,output);}}
}
