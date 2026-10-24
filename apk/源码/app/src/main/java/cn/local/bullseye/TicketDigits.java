package cn.local.bullseye;
import java.util.*;

/** Strict fallback for isolated white 0/1 glyphs missed by text detection. */
public final class TicketDigits {
    public static Integer single(int[] pixels,int w,int h){
        boolean[] white=new boolean[w*h],seen=new boolean[w*h];
        for(int i=0;i<pixels.length;i++){int p=pixels[i],r=(p>>16)&255,g=(p>>8)&255,b=p&255;
            int lo=Math.min(r,Math.min(g,b)),hi=Math.max(r,Math.max(g,b));white[i]=lo>190&&hi-lo<55;}
        int[] queue=new int[w*h];List<int[]> components=new ArrayList<>();
        for(int i=0;i<pixels.length;i++)if(white[i]&&!seen[i]){
            int head=0,tail=1,x1=i%w,x2=x1,y1=i/w,y2=y1;seen[i]=true;queue[0]=i;
            while(head<tail){int q=queue[head++],x=q%w,y=q/w;x1=Math.min(x1,x);x2=Math.max(x2,x);y1=Math.min(y1,y);y2=Math.max(y2,y);
                int[] ns={x>0?q-1:-1,x+1<w?q+1:-1,y>0?q-w:-1,y+1<h?q+w:-1};for(int n:ns)if(n>=0&&white[n]&&!seen[n]){seen[n]=true;queue[tail++]=n;}}
            if(y2-y1+1>h*.18f&&tail>8)components.add(new int[]{x1,y1,x2,y2,tail});
        }
        if(components.size()!=1)return null;
        int[] c=components.get(0);int bw=c[2]-c[0]+1,bh=c[3]-c[1]+1;float aspect=bw/(float)bh;
        boolean one=false;
        if(aspect>.2f&&aspect<.55f){
            int longest=0;for(int x=c[0];x<=c[2];x++){int n=0;for(int y=c[1];y<=c[3];y++)if(white[y*w+x])n++;longest=Math.max(longest,n);}
            one=longest/(float)bh>.85f&&c[4]/(float)(bw*bh)>.35f;
        }
        if(!one&&(aspect<=.4f||aspect>=1.05f))return null;
        // Count dark holes wholly inside the white digit's bounding box.
        Arrays.fill(seen,false);List<int[]> holes=new ArrayList<>();
        for(int y=c[1];y<=c[3];y++)for(int x=c[0];x<=c[2];x++){
            int i=y*w+x;if(white[i]||seen[i])continue;
            int head=0,tail=1,x1=x,x2=x,y1=y,y2=y;boolean edge=false;queue[0]=i;seen[i]=true;
            while(head<tail){int q=queue[head++],xx=q%w,yy=q/w;x1=Math.min(x1,xx);x2=Math.max(x2,xx);y1=Math.min(y1,yy);y2=Math.max(y2,yy);
                if(xx==c[0]||xx==c[2]||yy==c[1]||yy==c[3])edge=true;
                int[] ns={xx>c[0]?q-1:-1,xx<c[2]?q+1:-1,yy>c[1]?q-w:-1,yy<c[3]?q+w:-1};for(int n:ns)if(n>=0&&!white[n]&&!seen[n]){seen[n]=true;queue[tail++]=n;}}
            if(!edge)holes.add(new int[]{x1,y1,x2,y2});
        }
        if(one&&holes.isEmpty())return 1;
        if(holes.size()!=1||aspect<=.4f)return null;int[] hole=holes.get(0);float hh=(hole[3]-hole[1]+1)/(float)bh;
        if(hh>.3f&&hh<.85f&&Math.abs((hole[0]+hole[2]-c[0]-c[2])/2f)<bw*.15f&&Math.abs((hole[1]+hole[3]-c[1]-c[3])/2f)<bh*.14f)return 0;
        return null;
    }
}
