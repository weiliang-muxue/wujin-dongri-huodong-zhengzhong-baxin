package cn.local.winterbear;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/** Reads a complete white x/6 in an anchored crop, without reading scenery behind it. */
final class MarchCounterVision {
    private final CountdownDigits digits;
    MarchCounterVision(FeedbackVision.Assets assets){digits=HuntGlyphs.load(assets,"march_digits");}

    int read(Bitmap image,Rect region){
        if(region.left<0||region.top<0||region.right>image.getWidth()||region.bottom>image.getHeight()
                ||region.width()<12||region.height()<12)return -1;
        // Normalize small crops before binarizing. Thresholding a 15 px digit
        // first loses its antialiased strokes on lower resolution displays.
        Bitmap crop=Bitmap.createBitmap(image,region.left,region.top,region.width(),region.height());
        int h=Math.max(96,crop.getHeight()),w=Math.round(crop.getWidth()*h/(float)crop.getHeight());
        Bitmap normalized=Bitmap.createScaledBitmap(crop,w,h,true);int[] pixels=new int[w*h];
        try{normalized.getPixels(pixels,0,w,0,0,w,h);}
        finally{if(normalized!=image)normalized.recycle();if(crop!=normalized&&crop!=image)crop.recycle();}
        int value=-1,agreement=0;
        for(int threshold:new int[]{170,190,210}){
            int candidate=read(pixels,w,h,threshold);
            if(candidate<0)continue;
            if(value>=0&&value!=candidate)return -1;
            value=candidate;agreement++;
        }
        return agreement>=2?value:-1;
    }

    private int read(int[] pixels,int w,int h,int threshold){
        boolean[] ink=new boolean[pixels.length];
        for(int i=0;i<pixels.length;i++){
            int r=Color.red(pixels[i]),g=Color.green(pixels[i]),b=Color.blue(pixels[i]);
            int min=Math.min(r,Math.min(g,b)),max=Math.max(r,Math.max(g,b));
            ink[i]=min>=threshold&&max-min<55;
        }
        List<CountdownDigits.Glyph> parts=new ArrayList<>();
        boolean[] seen=new boolean[ink.length];int[] queue=new int[ink.length];
        for(int start=0;start<ink.length;start++)if(ink[start]&&!seen[start]){
            int l=w,r=0,t=h,b=0,head=0,tail=0;queue[tail++]=start;seen[start]=true;
            while(head<tail){int p=queue[head++],x=p%w,y=p/w;l=Math.min(l,x);r=Math.max(r,x+1);t=Math.min(t,y);b=Math.max(b,y+1);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=w||yy>=h)continue;
                    int next=yy*w+xx;if(ink[next]&&!seen[next]){seen[next]=true;queue[tail++]=next;}
                }
            }
            if(b-t>=Math.max(7,h*.20)&&r-l>=2)parts.add(new CountdownDigits.Glyph(l,r,t,b));
        }
        parts.sort(java.util.Comparator.comparingInt(g->g.left));
        int answer=-1,matches=0;
        for(int i=0;i+2<parts.size();i++){
            CountdownDigits.Glyph a=parts.get(i),slash=parts.get(i+1),b=parts.get(i+2);
            int height=Math.max(a.height(),b.height());
            if(Math.abs(a.height()-b.height())>height*.22||Math.abs(a.top-b.top)>height*.18
                    ||Math.abs(a.bottom-b.bottom)>height*.18||slash.height()<height*.75||slash.height()>height*1.25
                    ||Math.abs((slash.top+slash.bottom)-(a.top+a.bottom))>height*.30
                    ||slash.left<a.right||b.left<slash.right||slash.left-a.right>height*.35||b.left-slash.right>height*.35
                    ||!slash(slash,ink,w))continue;
            int numerator=digits.digit(digits.normalize(a,ink,w)),denominator=digits.digit(digits.normalize(b,ink,w));
            if(numerator<0||numerator>6||denominator!=6)continue;
            // An adjacent digit on the same baseline makes e.g. 13/6 invalid.
            boolean extra=false;
            for(int j=0;j<parts.size();j++)if(j<i||j>i+2){CountdownDigits.Glyph g=parts.get(j);
                if(Math.abs(g.top-a.top)<height*.2&&Math.abs(g.bottom-a.bottom)<height*.2
                        &&((g.right<=a.left&&a.left-g.right<height*.4)||(g.left>=b.right&&g.left-b.right<height*.4)))extra=true;
            }
            if(extra)continue;
            answer=numerator;matches++;
        }
        return matches==1?answer:-1;
    }

    private static boolean slash(CountdownDigits.Glyph g,boolean[] ink,int w){
        if(g.width()<g.height()*.15||g.width()>g.height()*.65)return false;
        double top=0,bottom=0;int nt=0,nb=0;
        for(int y=g.top;y<g.bottom;y++)for(int x=g.left;x<g.right;x++)if(ink[y*w+x]){
            if(y<g.top+g.height()/3){top+=x;nt++;}
            if(y>=g.bottom-g.height()/3){bottom+=x;nb++;}
        }
        return nt>0&&nb>0&&top/nt-bottom/nb>=g.width()*.35;
    }
}
