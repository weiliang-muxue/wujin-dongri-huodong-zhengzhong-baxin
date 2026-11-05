package cn.local.winterbear;

import android.graphics.*;
import cn.local.automation.VisualLocator;
import java.io.InputStream;
import java.util.*;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/** Shared hunt row anchors: resource icons remain resources while travelling. */
final class MarchRowVision implements AutoCloseable {
    static final class Marker {
        final HuntMarches.Type type;final int x,y;final double confidence;
        Marker(HuntMarches.Type type,VisualLocator.Match m){this.type=type;x=(int)Math.round(m.x);y=(int)Math.round(m.y);confidence=m.confidence;}
    }
    private final Map<HuntMarches.Type,Mat> templates=new EnumMap<>(HuntMarches.Type.class);
    MarchRowVision(FeedbackVision.Assets assets){
        try{
            for(HuntMarches.Type type:HuntMarches.Type.values()){
                if(type==HuntMarches.Type.UNKNOWN)continue;
                try(InputStream in=assets.open("hunt/march_"+type.name().toLowerCase(Locale.ROOT)+".png")){
                    Bitmap bitmap=BitmapFactory.decodeStream(in);Mat rgba=new Mat(),gray=new Mat();
                    try{if(bitmap==null)throw new IllegalArgumentException(type.name());Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);templates.put(type,gray);}
                    finally{rgba.release();if(bitmap!=null)bitmap.recycle();if(!templates.containsKey(type))gray.release();}
                }
            }
        }catch(Exception e){close();throw new IllegalStateException("行军图标素材不可用",e);}
    }
    List<Marker> find(Mat gray,int header,int dx){
        List<Marker> candidates=new ArrayList<>(),found=new ArrayList<>();
        org.opencv.core.Rect area=new org.opencv.core.Rect(dx+5,header+23,43,335);
        for(Map.Entry<HuntMarches.Type,Mat> e:templates.entrySet())
            for(VisualLocator.Match m:VisualLocator.find(gray,e.getValue(),area,.88,6,true))candidates.add(new Marker(e.getKey(),m));
        candidates.sort(Comparator.comparingDouble((Marker m)->m.confidence).reversed());
        for(Marker m:candidates){boolean duplicate=false;for(Marker old:found)if(Math.abs(old.y-m.y)<20){duplicate=true;break;}if(!duplicate)found.add(m);}
        found.sort(Comparator.comparingInt(m->m.y));return found;
    }
    public void close(){for(Mat m:templates.values())m.release();templates.clear();}
}
