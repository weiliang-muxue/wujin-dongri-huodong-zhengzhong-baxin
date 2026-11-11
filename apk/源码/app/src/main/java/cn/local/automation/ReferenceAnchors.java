package cn.local.automation;

import java.util.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Learns unchanged, distinctive landmarks from two reference pages, then relocates them. */
public final class ReferenceAnchors implements AutoCloseable {
    private static final class Anchor {
        final Mat patch; final double x,y,texture;
        Anchor(Mat patch,double x,double y,double texture){this.patch=patch;this.x=x;this.y=y;this.texture=texture;}
    }
    private final List<Anchor> anchors=new ArrayList<>();
    public ReferenceAnchors(Mat first,Mat second,Rect area,List<Rect> excluded){
        List<Anchor> candidates=new ArrayList<>();
        for(int y=Math.max(0,area.y);y+28<=Math.min(first.rows(),area.y+area.height);y+=24)
            for(int x=Math.max(0,area.x);x+40<=Math.min(first.cols(),area.x+area.width);x+=32){
                Rect box=new Rect(x,y,40,28);boolean skip=false;
                for(Rect r:excluded)if(x<r.x+r.width&&x+40>r.x&&y<r.y+r.height&&y+28>r.y){skip=true;break;}
                if(skip)continue;
                Mat a=first.submat(box),b=second.submat(box),score=new Mat();MatOfDouble mean=new MatOfDouble(),sd=new MatOfDouble();
                try{
                    Core.meanStdDev(a,mean,sd);double texture=sd.toArray()[0];
                    if(texture<22)continue;
                    Imgproc.matchTemplate(b,a,score,Imgproc.TM_CCOEFF_NORMED);
                    if(score.get(0,0)[0]<.96)continue;
                    candidates.add(new Anchor(a.clone(),x+20,y+14,texture));
                }finally{a.release();b.release();score.release();mean.release();sd.release();}
            }
        candidates.sort((a,b)->Double.compare(b.texture,a.texture));
        Rect all=new Rect(0,0,first.cols(),first.rows());
        for(Anchor a:candidates){
            boolean near=anchors.stream().anyMatch(b->Math.hypot(a.x-b.x,a.y-b.y)<65);
            if(anchors.size()<10&&!near&&VisualLocator.unique(first,a.patch,all,.90,false)!=null)anchors.add(a);
            else a.patch.release();
        }
    }
    public LayoutTransform locate(Mat frame){
        List<Anchor> source=new ArrayList<>();List<VisualLocator.Match> observed=new ArrayList<>();
        Rect all=new Rect(0,0,frame.cols(),frame.rows());
        // Most frames preserve the reference scale after width normalization.
        // A geometrically consistent set of distinct landmarks already locates
        // that page; only missing/inconsistent sets need the scale pyramid.
        for(Anchor a:anchors){
            VisualLocator.Match m=VisualLocator.unique(frame,a.patch,all,.89,true);
            if(m!=null){source.add(a);observed.add(m);}
        }
        LayoutTransform exact=consistent(source,observed);
        if(exact!=null)return exact;
        source.clear();observed.clear();
        for(Anchor a:anchors){
            VisualLocator.Match m=VisualLocator.uniqueAdaptive(frame,a.patch,all,.89,true);
            if(m!=null){source.add(a);observed.add(m);}
        }
        return consistent(source,observed);
    }
    private static LayoutTransform consistent(List<Anchor> source,List<VisualLocator.Match> observed){
        LayoutTransform best=null;int support=0;
        for(int i=0;i<source.size();i++)for(int j=i+1;j<source.size();j++){
            Anchor a=source.get(i),b=source.get(j);VisualLocator.Match u=observed.get(i),v=observed.get(j);
            LayoutTransform candidate=LayoutTransform.between(a.x,a.y,b.x,b.y,u.x,u.y,v.x,v.y,3);
            if(candidate==null)continue;
            int n=0;for(int k=0;k<source.size();k++){
                Anchor anchor=source.get(k);VisualLocator.Match match=observed.get(k);
                double iconScale=match.width/(double)anchor.patch.cols();
                if(Math.abs(iconScale-candidate.scale)<=.1*candidate.scale
                        &&candidate.agrees(anchor.x,anchor.y,match.x,match.y,4*Math.max(1,candidate.scale)))n++;
            }
            if(n>support){support=n;best=candidate;}
        }
        return support>=3?best:null;
    }
    @Override public void close(){for(Anchor a:anchors)a.patch.release();anchors.clear();}
}
