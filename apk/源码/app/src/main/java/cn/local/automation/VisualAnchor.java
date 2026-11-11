package cn.local.automation;
import org.opencv.core.*;
/** A detected landmark carries its content scale into every related search area. */
public final class VisualAnchor extends Point {
    public final double scale;
    public final double scaleUncertainty;
    public VisualAnchor(double x,double y,double scale){this(x,y,scale,0);}
    public VisualAnchor(double x,double y,double scale,double uncertainty){super(x,y);this.scale=scale;this.scaleUncertainty=uncertainty;}
    public static VisualAnchor from(VisualLocator.Match match,Mat template){
        // A one-pixel raster/width estimate error becomes several pixels at a
        // distant control. Carry that uncertainty into search bounds, while
        // retaining the same visual confidence required for the actual control.
        return new VisualAnchor(match.x,match.y,match.width/(double)template.cols(),1.0/template.cols());
    }
    public static double scale(Point point){return point instanceof VisualAnchor?((VisualAnchor)point).scale:1;}
    public static VisualAnchor at(Point source,double x,double y){return new VisualAnchor(x,y,scale(source),source instanceof VisualAnchor?((VisualAnchor)source).scaleUncertainty:0);}
    public static Rect near(Point point,double left,double top,double right,double bottom){
        double scale=scale(point);
        double error=point instanceof VisualAnchor?((VisualAnchor)point).scaleUncertainty:0;
        double low=Math.max(.01,scale-error),high=scale+error;
        int l=(int)Math.floor(point.x+Math.min(left*low,left*high)),t=(int)Math.floor(point.y+Math.min(top*low,top*high));
        int r=(int)Math.ceil(point.x+Math.max(right*low,right*high)),b=(int)Math.ceil(point.y+Math.max(bottom*low,bottom*high));
        return new Rect(l,t,Math.max(0,r-l),Math.max(0,b-t));
    }
}
