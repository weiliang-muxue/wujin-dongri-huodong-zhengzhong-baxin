package cn.local.winterbear;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Screenshot editor only: its gestures never reach the game underneath. */
final class SelectionView extends View {
    private final Bitmap image;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF display=new RectF();
    private boolean pointMode, selected;
    private float startX,startY,endX,endY,scale;
    SelectionView(Context c,Bitmap image) {
        super(c);this.image=image;setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription("游戏截图取样区域。拖动框选图像，或按提示点选按钮位置");
    }
    void mode(boolean point) {pointMode=point;selected=false;invalidate();}
    void suggestPoint(int x,int y){pointMode=true;startX=endX=x;startY=endY=y;selected=true;invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Ui.BG);
        scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());
        float w=image.getWidth()*scale,h=image.getHeight()*scale;
        display.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xffffffff);canvas.drawBitmap(image,null,display,paint);
        if(!selected)return;
        paint.setColor(Ui.ACCENT);paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setStyle(Paint.Style.STROKE);
        float x=display.left+endX*scale,y=display.top+endY*scale;
        if(pointMode) {
            canvas.drawCircle(x,y,Ui.dp(getContext(),12),paint);
            canvas.drawLine(x-Ui.dp(getContext(),18),y,x+Ui.dp(getContext(),18),y,paint);
            canvas.drawLine(x,y-Ui.dp(getContext(),18),x,y+Ui.dp(getContext(),18),paint);
        } else {
            Rect r=rect();canvas.drawRect(display.left+r.left*scale,display.top+r.top*scale,display.left+r.right*scale,display.top+r.bottom*scale,paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!isEnabled()||scale<=0)return false;
        float x=Math.max(0,Math.min(image.getWidth()-1,(event.getX()-display.left)/scale));
        float y=Math.max(0,Math.min(image.getHeight()-1,(event.getY()-display.top)/scale));
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if(!display.contains(event.getX(),event.getY()))return false;
                getParent().requestDisallowInterceptTouchEvent(true);startX=x;startY=y;endX=x;endY=y;selected=true;break;
            case MotionEvent.ACTION_MOVE: endX=x;endY=y;break;
            case MotionEvent.ACTION_UP: endX=x;endY=y;performClick();break;
            case MotionEvent.ACTION_CANCEL: selected=false;break;
        }
        invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    Point point(){return new Point(Math.round(endX),Math.round(endY));}
    Rect rect(){return new Rect((int)Math.min(startX,endX),(int)Math.min(startY,endY),Math.min(image.getWidth(),(int)Math.max(startX,endX)),Math.min(image.getHeight(),(int)Math.max(startY,endY)));}
    boolean valid(){return selected&&(pointMode||(rect().width()>=12&&rect().height()>=12));}
}
