package android.graphics;
public final class Rect {
    public int left,top,right,bottom;
    public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
    public int width(){return right-left;}
    public int height(){return bottom-top;}
    public int centerX(){return (left+right)/2;}
    public int centerY(){return (top+bottom)/2;}
    public void offset(int x,int y){left+=x;right+=x;top+=y;bottom+=y;}
}
