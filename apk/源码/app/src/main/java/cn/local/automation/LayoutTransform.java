package cn.local.automation;

/** Pixel-space scale and translation derived from two observed anchors. */
public final class LayoutTransform {
    public final double scale,dx,dy;
    public LayoutTransform(double scale,double dx,double dy){
        if(!Double.isFinite(scale)||!Double.isFinite(dx)||!Double.isFinite(dy)||scale<=0)throw new IllegalArgumentException("Invalid layout");
        this.scale=scale;this.dx=dx;this.dy=dy;
    }
    public double x(double x){return x*scale+dx;}
    public double y(double y){return y*scale+dy;}
    public double inverseX(double x){return (x-dx)/scale;}
    public double inverseY(double y){return (y-dy)/scale;}
    public static LayoutTransform between(double ax,double ay,double bx,double by,double u,double v,double p,double q,double tolerance){
        if(!Double.isFinite(ax+ay+bx+by+u+v+p+q)||!Double.isFinite(tolerance)||tolerance<0)return null;
        double sx=bx-ax,sy=by-ay,length=sx*sx+sy*sy;
        if(length<400)return null;
        double scale=((p-u)*sx+(q-v)*sy)/length;
        if(scale<.5||scale>2)return null;
        LayoutTransform t=new LayoutTransform(scale,u-scale*ax,v-scale*ay);
        return Math.hypot(t.x(bx)-p,t.y(by)-q)<=tolerance?t:null;
    }
    public boolean agrees(double x,double y,double observedX,double observedY,double tolerance){return Math.hypot(x(x)-observedX,y(y)-observedY)<=tolerance;}
}
