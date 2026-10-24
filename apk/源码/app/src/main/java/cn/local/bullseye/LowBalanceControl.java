package cn.local.bullseye;
import java.util.*;

/** Detect the low-balance control using disc shape/color and red/white cost text. */
public final class LowBalanceControl {
    public static final class Hit {
        public final int value;public final float x,y;
        Hit(int value,float x,float y){this.value=value;this.x=x;this.y=y;}
    }
    private static void hsv(int pixel,float[] out){
        float r=((pixel>>16)&255)/255f,g=((pixel>>8)&255)/255f,b=(pixel&255)/255f;
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0;
        if(d!=0){if(max==r)h=60*((g-b)/d);else if(max==g)h=60*(2+(b-r)/d);else h=60*(4+(r-g)/d);if(h<0)h+=360;}
        out[0]=h;out[1]=max==0?0:d/max;out[2]=max;
    }
    public static Hit find(int[] pixels,int width,int height,float sx,float sy,float dx,float dy){
        float[] r={Math.max(0,sx+.65f*dx),Math.max(0,sy-.38f*dy),Math.min(1,sx+1.9f*dx),Math.min(1,sy+.55f*dy)};
        int x0=(int)(r[0]*width),y0=(int)(r[1]*height);
        int w=Math.max(0,(int)(r[2]*width)-x0),h=Math.max(0,(int)(r[3]*height)-y0);
        if(w==0||h==0)return null;
        int[] px=new int[w*h];for(int y=0;y<h;y++)System.arraycopy(pixels,(y0+y)*width+x0,px,y*w,w);
        byte[] mask=new byte[px.length];float[] hsv=new float[3];
        for(int i=0;i<px.length;i++){
            hsv(px[i],hsv);
            if(hsv[0]>=70&&hsv[0]<=180&&hsv[1]>=105/255f&&hsv[2]>=95/255f)mask[i]=1;
            else if(hsv[0]>=240&&hsv[0]<=320&&hsv[1]>=85/255f&&hsv[2]>=65/255f)mask[i]=5;
        }
        List<Hit> found=new ArrayList<>();int[] queue=new int[px.length];
        for(int i=0;i<mask.length;i++)if(mask[i]!=0){
            byte color=mask[i];int head=0,tail=1,minX=i%w,maxX=minX,minY=i/w,maxY=minY;queue[0]=i;mask[i]=0;
            while(head<tail){int q=queue[head++],x=q%w,y=q/w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                int[] ns={x>0?q-1:-1,x+1<w?q+1:-1,y>0?q-w:-1,y+1<h?q+w:-1};
                for(int n:ns)if(n>=0&&mask[n]==color){mask[n]=0;queue[tail++]=n;}}
            float bw=maxX-minX+1,bh=maxY-minY+1;
            if(bw<=.25f*dx*width||bw>=.9f*dx*width||bw/bh<=.7f||bw/bh>=1.4f||tail/(bw*bh)<.5f)continue;
            // A disc occupies its bounding-box center; reject hollow outlines.
            int central=0,total=0;float cx=(minX+maxX)/2f,cy=(minY+maxY)/2f;
            for(int q=0;q<tail;q++){int x=queue[q]%w,y=queue[q]/w;if(Math.abs(x-cx)<bw*.25f&&Math.abs(y-cy)<bh*.25f)central++;}
            total=Math.max(1,Math.round(bw*bh*.25f));if(central/(float)total<.35f)continue;
            found.add(new Hit(color,((int)(r[0]*width)+minX+bw/2)/width,((int)(r[1]*height)+minY+bh/2)/height));
        }
        if(found.size()!=1)return null;
        Hit button=found.get(0);
        float[] costRect={Math.max(0,sx-.08f*dx),Math.max(0,sy+.10f*dy),Math.min(1,sx+.40f*dx),Math.min(1,sy+.38f*dy)};
        int cx0=(int)(costRect[0]*width),cy0=(int)(costRect[1]*height),cw=(int)(costRect[2]*width)-cx0,ch=(int)(costRect[3]*height)-cy0;
        if(cw<=0||ch<=0)return null;
        int[] costPixels=new int[cw*ch];for(int y=0;y<ch;y++)System.arraycopy(pixels,(cy0+y)*width+cx0,costPixels,y*cw,cw);
        int red=0,white=0;for(int pixel:costPixels){hsv(pixel,hsv);
            if((hsv[0]<30||hsv[0]>330)&&hsv[1]>110/255f&&hsv[2]>95/255f)red++;
            int lo=Math.min(((pixel>>16)&255),Math.min(((pixel>>8)&255),(pixel&255))),hi=Math.max(((pixel>>16)&255),Math.max(((pixel>>8)&255),(pixel&255)));
            if(lo>195&&hi-lo<65)white++;}
        float redRatio=red/(float)costPixels.length,whiteRatio=white/(float)costPixels.length;
        if(button.value==5&&redRatio>.06f)return button;
        if(button.value==1&&redRatio<.04f&&whiteRatio>.03f)return button;
        return null;
    }
}
