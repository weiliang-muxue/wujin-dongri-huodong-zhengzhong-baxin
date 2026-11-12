package cn.local.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Shared pixel matcher. Activities supply landmarks/relationships, never guessed tap points. */
public final class VisualLocator {
    private VisualLocator(){}
    public static final class Match {
        public final double x,y,confidence;
        public final int width,height;
        Match(double x,double y,double confidence,int width,int height){this.x=x;this.y=y;this.confidence=confidence;this.width=width;this.height=height;}
    }
    public static List<Match> find(Mat image,Mat template,Rect area,double threshold,int limit,boolean soften){
        RecognitionWorker.checkCancelled();
        List<Match> found=new ArrayList<>();
        int l=Math.max(0,area.x),t=Math.max(0,area.y),r=Math.min(image.cols(),area.x+area.width),b=Math.min(image.rows(),area.y+area.height);
        if(image.empty()||template.empty()||r-l<template.cols()||b-t<template.rows()||limit<1)return found;
        // A flat patch has undefined normalized correlation and must not match every pixel.
        MatOfDouble mean=new MatOfDouble(),deviation=new MatOfDouble();
        try{Core.meanStdDev(template,mean,deviation);if(deviation.toArray()[0]<1)return found;}
        finally{mean.release();deviation.release();}
        Mat roi=image.submat(t,b,l,r),scores=new Mat();
        try{
            Imgproc.matchTemplate(roi,template,scores,Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult first=Core.minMaxLoc(scores);
            if(soften&&Double.isFinite(first.maxVal)&&first.maxVal>=.78&&first.maxVal<threshold){
                Mat a=new Mat(),c=new Mat();
                try{Imgproc.GaussianBlur(roi,a,new Size(3,3),1);Imgproc.GaussianBlur(template,c,new Size(3,3),1);Imgproc.matchTemplate(a,c,scores,Imgproc.TM_CCOEFF_NORMED);threshold=Math.max(.91,threshold);}
                finally{a.release();c.release();}
            }
            for(int n=0;n<limit;n++){
                Core.MinMaxLocResult m=Core.minMaxLoc(scores);
                if(!Double.isFinite(m.maxVal)||m.maxVal<threshold)break;
                int x=(int)m.maxLoc.x,y=(int)m.maxLoc.y;
                found.add(new Match(l+x+template.cols()/2.,t+y+template.rows()/2.,m.maxVal,template.cols(),template.rows()));
                Mat peak=scores.submat(Math.max(0,y-template.rows()/2),Math.min(scores.rows(),y+template.rows()/2+1),Math.max(0,x-template.cols()/2),Math.min(scores.cols(),x+template.cols()/2+1));
                try{peak.setTo(new Scalar(-1));}finally{peak.release();}
            }
        }finally{roi.release();scores.release();}
        return found;
    }
    public static Match unique(Mat image,Mat template,Rect area,double threshold,boolean soften){
        List<Match> found=find(image,template,area,threshold,2,soften);return found.size()==1?found.get(0):null;
    }
    public static List<Match> findAdaptive(Mat image,Mat template,Rect area,double threshold,int limit,boolean soften){
        return findAdaptive(image,template,area,threshold,limit,soften,null);
    }
    /** Coarse image-pyramid search followed by local, pixel-width scale refinement. */
    public static List<Match> findAdaptive(Mat image,Mat template,Rect area,double threshold,int limit,boolean soften,Predicate<Match> accepted){
        List<Match> result=new ArrayList<>();
        if(image.empty()||template.empty()||limit<1)return result;
        int candidates=accepted==null?limit:Math.max(8,limit*4);
        for(Match match:find(image,template,area,threshold,candidates,soften)){
            if(accepted==null||accepted.test(match))result.add(match);
            if(result.size()==limit)break;
        }
        if(result.size()>=limit)return result;
        int left=Math.max(0,area.x),top=Math.max(0,area.y);
        int right=Math.min(image.cols(),area.x+area.width),bottom=Math.min(image.rows(),area.y+area.height);
        if(right<=left||bottom<=top)return result;
        MatOfDouble mean=new MatOfDouble(),deviation=new MatOfDouble();
        try{
            Core.meanStdDev(template,mean,deviation);
            if(deviation.toArray()[0]<1)return result;
        }finally{mean.release();deviation.release();}
        Rect region=new Rect(left,top,right-left,bottom-top);
        // Some shipped preset markers are only 12 px wide at reference scale.
        // A 12 px minimum silently prohibited their smaller, still readable
        // variants. Preserve at least 8 px; confidence/color/page checks remain.
        double minimum=Math.max(.5,8.0/Math.min(template.cols(),template.rows()));
        double maximum=Math.min(2,Math.min(region.width/(double)template.cols(),region.height/(double)template.rows()));
        if(maximum<minimum)return result;
        // A wide text strip loses correlation much sooner than a square icon:
        // the same relative scale error displaces its end characters farther.
        // Keep proposal spacing proportional to the template's short/long edge,
        // so fine scale refinement is reached instead of rejecting every seed.
        double aspectStep=.1*Math.min(template.cols(),template.rows())/Math.max(template.cols(),template.rows());
        int levels=Math.max(1,(int)Math.ceil(Math.log(maximum/minimum)/Math.log1p(aspectStep)));
        double ratio=Math.pow(maximum/minimum,1.0/levels);
        int seedLimit=Math.max(3,Math.min(10,limit+1));
        List<Match> seeds=new ArrayList<>();
        double reduction=Math.max(.5,Math.min(1,16.0/Math.min(template.cols(),template.rows())));
        Mat roi=image.submat(region),coarse=new Mat();
        try{
            Imgproc.resize(roi,coarse,new Size(),reduction,reduction,Imgproc.INTER_AREA);
            double scaleX=coarse.cols()/(double)region.width,scaleY=coarse.rows()/(double)region.height;
            for(int level=0;level<=levels;level++){
                double scale=minimum*Math.pow(ratio,level);
                Mat sample=new Mat();
                try{
                    Imgproc.resize(template,sample,new Size(Math.max(1,Math.round(template.cols()*scale*scaleX)),Math.max(1,Math.round(template.rows()*scale*scaleY))),0,0,Imgproc.INTER_AREA);
                    // These are proposals only. Every accepted location is
                    // checked again at full resolution using the caller's threshold.
                    for(Match match:find(coarse,sample,new Rect(0,0,coarse.cols(),coarse.rows()),Math.min(.55,threshold-.20),seedLimit,false)){
                        Match seed=new Match(left+match.x/scaleX,top+match.y/scaleY,match.confidence,
                                (int)Math.round(template.cols()*scale),(int)Math.round(template.rows()*scale));
                        merge(seeds,seed,true);
                    }
                }finally{sample.release();}
            }
        }finally{roi.release();coarse.release();}
        seeds.sort(Comparator.comparingDouble((Match match)->match.confidence).reversed());
        for(int index=0;index<Math.min(seedLimit,seeds.size());index++){
            Match seed=seeds.get(index);
            if(result.stream().anyMatch(match->match.confidence>=.97&&sameLocation(match,seed)))continue;
            // A coarse pixel represents multiple source pixels. Include that
            // rounding uncertainty when refining small buttons at full size.
            int uncertainty=(int)Math.ceil(2/reduction);
            int first=Math.max((int)Math.ceil(template.cols()*minimum),(int)Math.floor(seed.width/ratio)-uncertainty);
            int last=Math.min((int)Math.floor(template.cols()*maximum),(int)Math.ceil(seed.width*ratio)+uncertainty);
            int marginY=(int)Math.ceil(last*template.rows()/(double)template.cols());
            int x1=Math.max(left,(int)Math.floor(seed.x-last)),y1=Math.max(top,(int)Math.floor(seed.y-marginY));
            int x2=Math.min(right,(int)Math.ceil(seed.x+last)),y2=Math.min(bottom,(int)Math.ceil(seed.y+marginY));
            Rect local=new Rect(x1,y1,Math.max(0,x2-x1),Math.max(0,y2-y1));
            int step=Math.max(1,(last-first)/16),bestWidth=first;
            double bestScore=-1;
            Set<Integer> visited=new HashSet<>();
            for(int width=first;width<=last;width+=step){
                visited.add(width);
                double score=refine(image,template,local,width,threshold,soften,accepted,candidates,result);
                if(score>bestScore){bestScore=score;bestWidth=width;}
            }
            for(int width=Math.max(first,bestWidth-step);width<=Math.min(last,bestWidth+step);width++){
                if(visited.add(width))refine(image,template,local,width,threshold,soften,accepted,candidates,result);
            }
        }
        result.sort(Comparator.comparingDouble((Match match)->match.confidence).reversed());
        return new ArrayList<>(result.subList(0,Math.min(limit,result.size())));
    }
    private static double refine(Mat image,Mat template,Rect area,int width,double threshold,boolean soften,Predicate<Match> accepted,int candidates,List<Match> result){
        int height=Math.max(1,(int)Math.round(width*template.rows()/(double)template.cols()));
        double score=refineShape(image,template,area,width,height,threshold,soften,accepted,candidates,result);
        // Screen rasterization rounds the two axes independently. A one-pixel
        // height difference can erase a thin glyph after whole-frame resizing,
        // even when its width is correct. Verify these neighbours at the same
        // confidence/color thresholds; never relax acceptance for a new model.
        if(score>=.70&&score<threshold){
            if(height>8)score=Math.max(score,refineShape(image,template,area,width,height-1,threshold,soften,accepted,candidates,result));
            score=Math.max(score,refineShape(image,template,area,width,height+1,threshold,soften,accepted,candidates,result));
        }
        return score;
    }
    private static double refineShape(Mat image,Mat template,Rect area,int width,int height,double threshold,boolean soften,Predicate<Match> accepted,int candidates,List<Match> result){
        Mat adjusted=new Mat();
        try{
            Imgproc.resize(template,adjusted,new Size(width,height),0,0,width<template.cols()?Imgproc.INTER_AREA:Imgproc.INTER_CUBIC);
            List<Match> peak=find(image,adjusted,area,0,1,false);
            if(peak.isEmpty())return -1;
            double score=peak.get(0).confidence;
            if(score>=threshold||(soften&&score>=.78)){
                for(Match match:find(image,adjusted,area,threshold,candidates,soften))
                    if(accepted==null||accepted.test(match))merge(result,match,false);
            }
            return score;
        }finally{adjusted.release();}
    }
    private static void merge(List<Match> matches,Match candidate,boolean coarse){
        for(int index=0;index<matches.size();index++){
            Match existing=matches.get(index);
            double radius=coarse?Math.max(existing.width,candidate.width)*.5:
                    Math.min(Math.min(existing.width,existing.height),Math.min(candidate.width,candidate.height))*.35;
            if(Math.hypot(existing.x-candidate.x,existing.y-candidate.y)<=Math.max(3,radius)||(!coarse&&sameLocation(existing,candidate))){
                if(candidate.confidence>existing.confidence)matches.set(index,candidate);
                return;
            }
        }
        matches.add(candidate);
    }
    private static boolean sameLocation(Match first,Match second){
        double width=Math.min(first.x+first.width/2.0,second.x+second.width/2.0)
                -Math.max(first.x-first.width/2.0,second.x-second.width/2.0);
        double height=Math.min(first.y+first.height/2.0,second.y+second.height/2.0)
                -Math.max(first.y-first.height/2.0,second.y-second.height/2.0);
        return width>0&&height>0&&width*height>=.65*Math.min(first.width*first.height,second.width*second.height);
    }
    public static Match uniqueAdaptive(Mat image,Mat template,Rect area,double threshold,boolean soften){
        List<Match> found=findAdaptive(image,template,area,threshold,2,soften);
        return found.size()==1?found.get(0):null;
    }
    /** Color validation follows the detected template size, including resized buttons. */
    public static boolean sameColor(Mat image,Mat reference,Match match,double maximumDifference){
        if(image.empty()||reference.empty()||image.channels()!=3||reference.channels()!=3)return false;
        int left=(int)Math.round(match.x-match.width/2.0),top=(int)Math.round(match.y-match.height/2.0);
        if(left<0||top<0||left+match.width>image.cols()||top+match.height>image.rows())return false;
        Mat actual=image.submat(new Rect(left,top,match.width,match.height)),expected=new Mat(),difference=new Mat();
        try{
            Imgproc.resize(reference,expected,new Size(match.width,match.height),0,0,Imgproc.INTER_AREA);
            Scalar referenceColor=Core.mean(expected),actualColor=Core.mean(actual);
            double expectedChroma=chroma(referenceColor);
            if(expectedChroma>15){
                int dominant=0;
                for(int channel=1;channel<3;channel++)if(referenceColor.val[channel]>referenceColor.val[dominant])dominant=channel;
                double strongest=Math.max(actualColor.val[0],Math.max(actualColor.val[1],actualColor.val[2]));
                if(chroma(actualColor)<expectedChroma*.5||strongest-actualColor.val[dominant]>expectedChroma*.25)return false;
            }
            Core.absdiff(actual,expected,difference);Scalar mean=Core.mean(difference);
            return (mean.val[0]+mean.val[1]+mean.val[2])/3<=maximumDifference;
        }finally{actual.release();expected.release();difference.release();}
    }
    private static double chroma(Scalar color){
        return Math.max(color.val[0],Math.max(color.val[1],color.val[2]))
                -Math.min(color.val[0],Math.min(color.val[1],color.val[2]));
    }
}
