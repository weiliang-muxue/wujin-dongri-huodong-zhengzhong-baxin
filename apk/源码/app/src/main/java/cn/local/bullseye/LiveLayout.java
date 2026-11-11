package cn.local.bullseye;

import android.graphics.Bitmap;
import android.graphics.PointF;
import cn.local.automation.*;
import java.util.*;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Relocates the activity header, start button and target grid on every captured frame. */
public final class LiveLayout implements AutoCloseable {
    private final Config reference;
    private final ReferenceAnchors anchors;
    private final Mat start;
    private List<PointF> grid;
    private LayoutTransform gridAt;
    private int gridWidth,gridHeight;
    public static final class Result {
        public final Config config;public final boolean home;public final double scale;
        Result(Config config,boolean home,double scale){this.config=config;this.home=home;this.scale=scale;}
    }
    public LiveLayout(Config c,Bitmap home,Bitmap board){
        reference=c;
        Mat a=gray(home),b=gray(board);
        try{
            float gh=c.region[3]-c.region[1];
            Rect header=rect(new float[]{c.region[0],c.region[1]+gh*.08f,c.region[2],Math.min(c.targets.get(0).y,c.countRect[3]+gh*.05f)},a);
            Rect count=rect(c.countRect,a);
            anchors=new ReferenceAnchors(a,b,header,Collections.singletonList(new Rect(count.x-5,count.y-5,count.width+10,count.height+10)));
            int x=Math.round(c.start.x*a.cols()),y=Math.round(c.start.y*a.rows());
            Rect label=new Rect(Math.max(0,x-42),Math.max(0,y-13),84,26);
            label.width=Math.min(label.width,a.cols()-label.x);label.height=Math.min(label.height,a.rows()-label.y);
            Mat patch=a.submat(label);try{start=patch.clone();}finally{patch.release();}
        }finally{a.release();b.release();}
    }
    private static Mat gray(Bitmap b){
        Mat rgba=new Mat(),scaled=new Mat(),gray=new Mat();
        try{Utils.bitmapToMat(b,rgba);Imgproc.resize(rgba,scaled,new Size(540,Math.round(b.getHeight()*540f/b.getWidth())),0,0,Imgproc.INTER_AREA);Imgproc.cvtColor(scaled,gray,Imgproc.COLOR_RGBA2GRAY);return gray;}
        finally{rgba.release();scaled.release();}
    }
    private static Rect rect(float[] r,Mat m){int l=Math.max(0,Math.round(r[0]*m.cols())),t=Math.max(0,Math.round(r[1]*m.rows()));return new Rect(l,t,Math.max(0,Math.min(m.cols(),Math.round(r[2]*m.cols()))-l),Math.max(0,Math.min(m.rows(),Math.round(r[3]*m.rows()))-t));}
    private PointF map(PointF p,LayoutTransform t,int w,int h){double factor=w/540.;return new PointF((float)(t.x(p.x*540)*factor/w),(float)(t.y(p.y*reference.height*540./reference.width)*factor/h));}
    private float[] map(float[] r,LayoutTransform t,int w,int h){PointF a=map(new PointF(r[0],r[1]),t,w,h),b=map(new PointF(r[2],r[3]),t,w,h);return new float[]{Math.max(0,a.x),Math.max(0,a.y),Math.min(1,b.x),Math.min(1,b.y)};}
    public Result observe(Bitmap frame){
        Mat gray=gray(frame);
        try{
            LayoutTransform t=anchors.locate(gray);if(t==null)return null;
            int w=frame.getWidth(),h=frame.getHeight();Config c=new Config();c.width=w;c.height=h;c.count=reference.count;
            c.start=map(reference.start,t,w,h);c.region=map(reference.region,t,w,h);c.countRect=map(reference.countRect,t,w,h);
            if(c.countRect[2]<=c.countRect[0]||c.countRect[3]<=c.countRect[1])return null;
            // The header has already identified this page. Content may occupy
            // either half, and bottom safe areas can move the button independently.
            // Require a unique visual button across the visible frame.
            VisualLocator.Match button=VisualLocator.uniqueAdaptive(gray,start,new Rect(0,0,gray.cols(),gray.rows()),.87,true);
            List<PointF> found=button==null?Vision.targets(frame):Collections.emptyList();
            if(found.size()==12){grid=found;gridAt=t;gridWidth=w;gridHeight=h;}
            if(grid!=null){
                for(PointF p:grid){double x=gridAt.inverseX(p.x*540),y=gridAt.inverseY(p.y*gridHeight*540./gridWidth);
                    c.targets.add(new PointF((float)(t.x(x)/540),(float)(t.y(y)*w/540/h)));}
            }else for(PointF p:reference.targets)c.targets.add(map(p,t,w,h));
            c.exitRect=Vision.exitRegion(c.targets);
            if(button!=null)c.start=new PointF((float)(button.x/540),(float)(button.y*w/540/h));
            for(PointF p:c.targets)if(p.x<=0||p.x>=1||p.y<=0||p.y>=1)return null;
            return new Result(c,button!=null,t.scale);
        }finally{gray.release();}
    }
    @Override public void close(){anchors.close();start.release();}
}
