package cn.local.winterbear;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** User-controlled folding; status changes never show/hide the window or expand it. */
public final class OverlayFold {
    private final LinearLayout body;
    private final TextView handle;
    private final Button fold;
    private final CharSequence expandedTitle;
    private final String compactTitle;
    private final int expandedWidth,compactWidth;
    private final IntConsumer resize;
    private boolean collapsed;

    public OverlayFold(Context context,LinearLayout root,TextView handle,String label,
                       int expandedWidth,int compactWidth,int touchHeight,IntConsumer resize){
        this.handle=handle;this.expandedTitle=handle.getText();compactTitle=label+"\n展开";
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
    public static void fitText(TextView view,int height,int maxSp){
        int min=Math.max(8,height/6),max=Math.max(min+1,Math.round(maxSp*view.getResources().getDisplayMetrics().scaledDensity));
        view.setAutoSizeTextTypeUniformWithConfiguration(min,max,1,android.util.TypedValue.COMPLEX_UNIT_PX);
    }
    public void setCollapsed(boolean value){
        if(value==collapsed)return;collapsed=value;
        body.setVisibility(value?View.GONE:View.VISIBLE);fold.setVisibility(value?View.GONE:View.VISIBLE);
        handle.setText(value?compactTitle:expandedTitle);
        handle.setContentDescription(value?compactTitle+"；拖动移动":expandedTitle);
        resize.accept(value?compactWidth:expandedWidth);
    }
}
