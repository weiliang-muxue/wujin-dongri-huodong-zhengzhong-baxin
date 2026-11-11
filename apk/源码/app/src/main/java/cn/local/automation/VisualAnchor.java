package cn.local.automation;
import org.opencv.core.*;
/** A detected landmark carries its content scale into every related search area. */
public final class VisualAnchor extends Point {
    public final double scale;
    public VisualAnchor(double x,double y,double scale){super(x,y);this.scale=scale;}
    public static VisualAnchor from(VisualLocator.Match match,Mat template){
        return new VisualAnchor(match.x,match.y,match.width/(double)template.cols());
    }
    public static double scale(Point point){return point instanceof VisualAnchor?((VisualAnchor)point).scale:1;}
    public static Rect near(Point point,double left,double top,double right,double bottom){
        double scale=scale(point);
        int l=(int)Math.floor(point.x+left*scale),t=(int)Math.floor(point.y+top*scale);
        int r=(int)Math.ceil(point.x+right*scale),b=(int)Math.ceil(point.y+bottom*scale);
        return new Rect(l,t,Math.max(0,r-l),Math.max(0,b-t));
    }
}
