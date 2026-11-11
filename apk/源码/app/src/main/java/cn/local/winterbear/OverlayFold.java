package cn.local.winterbear;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** User-controlled folding; status changes never show/hide the window or expand it. */
public final class OverlayFold {
    /** All activities use the giant-hunt control sizes and the same collapsed tab. */
    public static final class Controls {
        public final LinearLayout panel;
        public final TextView title,status;
        public final Button run;
        public final int width,compactWidth,titleHeight;
        public final float scale;

        public Controls(Context context,int screenWidth,int topInset,String label,Runnable toggle,Runnable end){
            scale=cn.local.bullseye.FloatingGuard.controlScale(screenWidth,topInset,context.getResources().getDisplayMetrics().density);
            width=Math.round(screenWidth*.57f);
            compactWidth=Math.round(72*scale);
            titleHeight=Math.round(44*scale);
            panel=Ui.column(context);
            panel.setPadding(6,4,6,4);
            panel.setBackground(Ui.shape(context,Ui.CARD,12));
            title=Ui.text(context,label+" ⠿",12,Ui.ACCENT);
            title.setGravity(Gravity.CENTER);
            panel.addView(title,new LinearLayout.LayoutParams(-1,titleHeight));
            LinearLayout actions=new LinearLayout(context);
            run=Ui.button(context,actions,"开始",true,toggle);
            Button stop=Ui.button(context,actions,"结束",false,end);
            for(Button button:new Button[]{run,stop}){
                button.setMinHeight(0);
                button.setMinimumHeight(0);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setPadding(0,0,0,0);
                button.setSingleLine(true);
                fitText(button,Math.round(32*scale),12);
                button.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));
            }
            panel.addView(actions,new LinearLayout.LayoutParams(-1,Math.round(32*scale)));
            status=Ui.text(context,"",11,Ui.TEXT);
            status.setMaxLines(2);
            status.setEllipsize(TextUtils.TruncateAt.END);
            fitText(status,Math.round(30*scale),11);
            panel.addView(status,new LinearLayout.LayoutParams(-1,Math.round(30*scale)));
        }
    }
    private final LinearLayout body;
    private final TextView handle;
    private final Button fold;
    private final Button switchTask;
    private final CharSequence expandedTitle;
    private final String label;
    private String compactStatus="待开始";
    private final int expandedWidth,compactWidth;
    private final IntConsumer resize;
    private boolean collapsed;

    public OverlayFold(Context context,LinearLayout root,TextView handle,String label,
                       int expandedWidth,int compactWidth,int touchHeight,IntConsumer resize){
        this(context,root,handle,label,expandedWidth,compactWidth,touchHeight,resize,null);
    }
    public OverlayFold(Context context,LinearLayout root,TextView handle,String label,
                       int expandedWidth,int compactWidth,int touchHeight,IntConsumer resize,Runnable chooseTask){
        this.handle=handle;this.expandedTitle=handle.getText();this.label=label;
        this.expandedWidth=expandedWidth;this.compactWidth=compactWidth;this.resize=resize;
        ((ViewGroup)handle.getParent()).removeView(handle);
        body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
        while(root.getChildCount()>0){View child=root.getChildAt(0);root.removeViewAt(0);body.addView(child);}
        LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
        handle.setMinHeight(0);handle.setMinimumHeight(0);handle.setGravity(Gravity.CENTER);
        handle.setMaxLines(2);
        // A compact window may scale below the system density. Keep the usual
        // maximum text size, but let its minimum follow the actual touch area.
        fitText(handle,touchHeight,14);
        handle.setContentDescription(label+"，拖动移动");
        heading.addView(handle,new LinearLayout.LayoutParams(0,touchHeight,1));
        switchTask=new Button(context);
        switchTask.setText("切换");switchTask.setAllCaps(false);
        switchTask.setTextColor(handle.getCurrentTextColor());
        switchTask.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        switchTask.setMinHeight(0);switchTask.setMinimumHeight(0);
        switchTask.setMinWidth(0);switchTask.setMinimumWidth(0);switchTask.setPadding(0,0,0,0);
        switchTask.setSingleLine(true);fitText(switchTask,touchHeight,12);
        switchTask.setContentDescription("切换活动任务");
        if(chooseTask!=null){
            heading.addView(switchTask,new LinearLayout.LayoutParams(touchHeight,touchHeight));
            switchTask.setOnClickListener(v->chooseTask.run());
        }
        fold=new Button(context);fold.setText("收起");fold.setAllCaps(false);fold.setTextSize(12);
        fold.setTextColor(handle.getCurrentTextColor());fold.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        fold.setMinHeight(0);fold.setMinimumHeight(0);fold.setMinWidth(0);fold.setMinimumWidth(0);fold.setPadding(0,0,0,0);
        fold.setSingleLine(true);fitText(fold,touchHeight,12);
        heading.addView(fold,new LinearLayout.LayoutParams(touchHeight,touchHeight));
        root.addView(heading,new LinearLayout.LayoutParams(-1,touchHeight));root.addView(body);
        fold.setOnClickListener(v->setCollapsed(true));
        handle.setOnClickListener(v->{if(collapsed)setCollapsed(false);});
    }
    public boolean isCollapsed(){return collapsed;}
    public void updateStatus(boolean running,String idleStatus,String message){
        String next=idleStatus;
        if(running){
            next="运行中";
            if(message.contains("等待")){
                if(message.contains("回城"))next="等待回城";
                else if(message.contains("空位"))next="等待空位";
                else if(message.contains("窗口")||message.contains("前台"))next="等待游戏";
            }
        }
        if(next.equals(compactStatus))return;
        compactStatus=next;
        if(collapsed)renderTitle();
    }
    private void renderTitle(){
        CharSequence title=collapsed?label+"\n"+compactStatus:expandedTitle;
        if(!title.toString().contentEquals(handle.getText()))handle.setText(title);
        handle.setContentDescription(collapsed?label+"，"+compactStatus+"，点按展开，拖动移动":label+"，拖动移动");
    }
    public static void fitText(TextView view,int height,int maxSp){
        int min=Math.max(8,height/6),max=Math.max(min+1,Math.round(maxSp*view.getResources().getDisplayMetrics().scaledDensity));
        view.setAutoSizeTextTypeUniformWithConfiguration(min,max,1,android.util.TypedValue.COMPLEX_UNIT_PX);
    }
    public void setCollapsed(boolean value){
        if(value==collapsed)return;collapsed=value;
        body.setVisibility(value?View.GONE:View.VISIBLE);fold.setVisibility(value?View.GONE:View.VISIBLE);
        switchTask.setVisibility(value?View.GONE:View.VISIBLE);
        renderTitle();
        resize.accept(value?compactWidth:expandedWidth);
    }
}
