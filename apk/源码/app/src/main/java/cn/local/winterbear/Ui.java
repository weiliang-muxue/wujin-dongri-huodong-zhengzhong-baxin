package cn.local.winterbear;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG=Color.rgb(14,24,36),CARD=Color.rgb(23,39,55),TEXT=Color.rgb(236,243,250),
        MUTED=Color.rgb(173,192,211),ACCENT=Color.rgb(113,216,203),DANGER=Color.rgb(255,171,160),BORDER=Color.rgb(51,76,97);
    static int dp(Context c,float value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
    static TextView text(Context c,String text,int size,int color) {
        TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(color);
        v.setLineSpacing(dp(c,3),1);return v;
    }
    static LinearLayout column(Context c) { LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v; }
    static LinearLayout card(Context c,LinearLayout parent,String title,String note) {
        LinearLayout v=column(c);v.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));v.setBackground(shape(c,CARD,20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(c,16);parent.addView(v,lp);
        TextView h=text(c,title,20,TEXT);h.setTypeface(null,Typeface.BOLD);v.addView(h);
        if(note!=null) { TextView n=text(c,note,14,MUTED);n.setPadding(0,dp(c,8),0,dp(c,8));v.addView(n); }
        return v;
    }
    static Button button(Context c,LinearLayout parent,String label,boolean primary,Runnable action) {
        Button b=new Button(c);b.setText(label);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(c,52));
        b.setTextColor(primary?BG:TEXT);b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3371d8cb),shape(c,primary?ACCENT:BORDER,12),null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,8);parent.addView(b,lp);
        b.setOnClickListener(v->action.run());return b;
    }
}
