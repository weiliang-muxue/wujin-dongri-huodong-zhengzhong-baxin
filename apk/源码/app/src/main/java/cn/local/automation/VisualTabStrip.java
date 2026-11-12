package cn.local.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import org.opencv.core.*;

/** Finds named tabs anywhere in a visible row, independent of index or neighbours. */
public final class VisualTabStrip {
    private VisualTabStrip(){}

    public static final class Tab {
        public final Point label;
        public final boolean selected;
        Tab(Point label,boolean selected){this.label=label;this.selected=selected;}
    }

    public static Map<String,Tab> find(Mat image,Map<String,Mat> labels,Rect row,
                                       Mat leftCorner,Mat rightCorner,double threshold){
        Map<String,Tab> found=new LinkedHashMap<>();
        for(Map.Entry<String,Mat> entry:labels.entrySet()){
            VisualLocator.Match match=VisualLocator.uniqueAdaptive(image,entry.getValue(),row,threshold,true);
            if(match==null)continue;
            Point label=VisualAnchor.from(match,entry.getValue());
            // The border belongs to this detected tile, never to the closest
            // known name. An event/resource tab may be selected instead.
            boolean left=corner(image,leftCorner,label,-51,threshold);
            boolean right=corner(image,rightCorner,label,51,threshold);
            found.put(entry.getKey(),new Tab(label,left||right));
        }
        return found;
    }

    private static boolean corner(Mat image,Mat template,Point label,double offset,double threshold){
        double q=VisualAnchor.scale(label);
        // A neighbouring tile's corner can be close, and a tiny false match
        // can resemble the border. Bind both scale and distance to this label.
        return VisualLocator.findAdaptive(image,template,VisualAnchor.near(label,offset-25,-90,offset+25,-42),threshold,1,true,
                m->Math.abs((m.x-label.x)/q-offset)<=12&&Math.abs((m.y-label.y)/q+66)<=14
                    &&m.width/(double)template.cols()>=q*.8&&m.width/(double)template.cols()<=q*1.2).size()==1;
    }
}
