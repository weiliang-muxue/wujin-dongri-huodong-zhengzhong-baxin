package cn.local.winterbear;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

/** Uses the connected service's WindowManager and accessibility overlay token. */
final class TaskChooser {
    private final BearService host;
    private final WindowManager windows;
    private ScrollView panel;

    TaskChooser(BearService host){
        this.host=host;
        windows=host.getSystemService(WindowManager.class);
    }

    void show(){
        host.pauseForTaskSelection();
        LinearLayout content=content("选择任务");
        Ui.button(host,content,"打熊",true,this::configureBear);
        Ui.button(host,content,"正中靶心",true,()->{dismiss();host.startBullseyeInGame();});
        Ui.button(host,content,"巨兽集结",true,()->configureHunt(HuntFlow.Mode.GIANT));
        Ui.button(host,content,"野兽集结",true,()->configureHunt(HuntFlow.Mode.BEAST));
        Ui.button(host,content,"取消",false,this::dismiss);
        show(content);
    }

    private LinearLayout content(String title){
        LinearLayout result=Ui.column(host);
        result.addView(Ui.text(host,title,22,Ui.TEXT));
        return result;
    }

    private void configureBear(){
        LinearLayout form=content("打熊");
        PresetPicker presets=new PresetPicker(host,host.bearPresetSelection(),null);
        form.addView(presets);
        Ui.button(host,form,"开始",true,()->{
            if(!presets.validateSelection())return;
            dismiss();
            host.startBearInGame(presets.selection());
        });
        Ui.button(host,form,"返回",false,this::show);
        show(form);
    }

    private void configureHunt(HuntFlow.Mode mode){
        LinearLayout form=content(mode==HuntFlow.Mode.GIANT?"巨兽集结":"野兽集结");
        EditText level=number(host,form,"目标等级",mode==HuntFlow.Mode.GIANT?8:30);
        EditText queues=number(host,form,"同时派出几队（1–6）",1);
        PresetPicker presets=new PresetPicker(host,PresetOrder.ALL,null);
        form.addView(presets);
        Switch cans=new Switch(host);
        cans.setText("自动使用库存罐头");cans.setTextColor(Ui.TEXT);
        cans.setMinHeight(Ui.dp(host,48));form.addView(cans);
        EditText cap=number(host,form,"本次罐头上限",100);
        cap.setEnabled(false);cap.setAlpha(.5f);
        cans.setOnCheckedChangeListener((button,on)->{cap.setEnabled(on);cap.setAlpha(on?1f:.5f);});
        Ui.button(host,form,"开始",true,()->{
            Integer target=read(level,1,99),count=read(queues,1,6);
            Integer allowance=cans.isChecked()?read(cap,1,9999):0;
            if(!presets.validateSelection()||target==null||count==null||allowance==null)return;
            dismiss();
            host.startHuntInGame(new HuntFlow.Options(mode,target,count,allowance,presets.selection()));
        });
        Ui.button(host,form,"返回",false,this::show);
        show(form);
    }

    static EditText number(Context context,LinearLayout parent,String label,int initial){
        parent.addView(Ui.text(context,label,15,Ui.TEXT));
        EditText input=new EditText(context);
        input.setTextColor(Ui.TEXT);input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(initial));input.setSelectAllOnFocus(true);
        input.setContentDescription(label);parent.addView(input);
        return input;
    }

    static Integer read(EditText field,int min,int max){
        try{
            int value=Integer.parseInt(field.getText().toString().trim());
            if(value>=min&&value<=max)return value;
        }catch(NumberFormatException ignored){}
        field.setError("请输入 "+min+"–"+max);return null;
    }

    void message(String title,String text){
        LinearLayout content=content(title);
        content.addView(Ui.text(host,text,15,Ui.TEXT));
        Ui.button(host,content,"返回任务选择",true,this::show);
        show(content);
    }

    private void show(LinearLayout content){
        dismiss();
        WindowMetrics metrics=windows.getCurrentWindowMetrics();
        Rect screen=metrics.getBounds();
        Insets bars=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        int width=Math.round((screen.width()-bars.left-bars.right)*.9f),padding=Ui.dp(host,20);
        content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width-2*padding),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int height=Math.min(content.getMeasuredHeight()+2*padding,
                Math.round((screen.height()-bars.top-bars.bottom)*.85f));
        panel=new ScrollView(host){
            @Override public boolean dispatchKeyEvent(KeyEvent event){
                if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
                    if(event.getAction()==KeyEvent.ACTION_UP)dismiss();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        panel.setPadding(padding,padding,padding,padding);
        panel.setBackground(Ui.shape(host,Ui.CARD,12));
        panel.setFocusableInTouchMode(true);
        panel.addView(content);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(width,height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.CENTER;
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        windows.addView(panel,params);
        panel.requestFocus();
    }

    void dismiss(){if(panel!=null){windows.removeView(panel);panel=null;}}
}
