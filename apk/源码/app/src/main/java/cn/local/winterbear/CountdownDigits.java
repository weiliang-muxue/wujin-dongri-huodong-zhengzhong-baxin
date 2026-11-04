package cn.local.winterbear;

import java.util.ArrayList;
import java.util.List;

/** Small, local HH:MM:SS reader. Unknown/ambiguous glyphs never imply spare time. */
final class CountdownDigits {
    static final int W=24,H=32;
    final List<byte[]>[] samples;
    @SuppressWarnings("unchecked") CountdownDigits(){samples=new List[10];for(int i=0;i<10;i++)samples[i]=new ArrayList<>();}
    static boolean canJoin(int seconds,long ageMillis){return seconds>=10&&ageMillis>=0&&ageMillis<=4000&&seconds*1000L-ageMillis>=10000;}
    static int seconds(int[] d){
        if(d.length!=6)return -1;
        for(int n:d)if(n<0||n>9)return -1;
        int minutes=d[2]*10+d[3],seconds=d[4]*10+d[5];
        return minutes<60&&seconds<60?(d[0]*10+d[1])*3600+minutes*60+seconds:-1;
    }
    static final class Glyph {
        final int left,right,top,bottom;
        Glyph(int l,int r,int t,int b){left=l;right=r;top=t;bottom=b;}
        int width(){return right-left;}int height(){return bottom-top;}
    }
    int read(byte[] gray,int width,int height){
        return read(gray,width,height,false,190);
    }
    int readSmall(byte[] gray,int width,int height){
        int value=-1;
        for(int threshold:new int[]{150,170,190,210}){
            int candidate=read(gray,width,height,true,threshold);
            if(candidate>=0){if(value>=0&&value!=candidate)return -1;value=candidate;}
        }
        return value;
    }
    private int read(byte[] gray,int width,int height,boolean smallFont,int threshold){
        if(width<70||height<16||gray.length!=width*height)return -1;
        boolean[] mask=new boolean[gray.length];int[] columns=new int[width];
        for(int i=0;i<gray.length;i++)if((gray[i]&255)>threshold){mask[i]=true;columns[i%width]++;}
        int columnMin=smallFont?2:Math.max(2,Math.round(width*4f/195));
        List<Glyph> groups=new ArrayList<>();
        for(int x=0;x<width;){
            if(columns[x]<columnMin){x++;continue;}
            int left=x;while(x<width&&columns[x]>=columnMin)x++;
            int top=height,bottom=-1;
            for(int y=0;y<height;y++)for(int j=left;j<x;j++)if(mask[y*width+j]){top=Math.min(top,y);bottom=Math.max(bottom,y);}
            if(x-left>=(smallFont?1:Math.max(1,Math.round(width/100f)))&&bottom-top+1>=Math.max(6,height/8))groups.add(new Glyph(left,x,top,bottom+1));
        }
        if(groups.size()!=8||!colon(groups.get(2),mask,width)||!colon(groups.get(5),mask,width))return -1;
        int[] digits=new int[6];int k=0;
        for(int i:new int[]{0,1,3,4,6,7})digits[k++]=digit(normalize(groups.get(i),mask,width));
        return seconds(digits);
    }
    private boolean colon(Glyph g,boolean[] mask,int width){
        if(g.width()>g.height()/2)return false;
        int runs=0;boolean previous=false;
        for(int y=g.top;y<g.bottom;y++){
            boolean ink=false;for(int x=g.left;x<g.right;x++)ink|=mask[y*width+x];
            if(ink&&!previous)runs++;previous=ink;
        }
        return runs==2;
    }
    byte[] normalize(Glyph g,boolean[] mask,int width){
        byte[] result=new byte[W*H];int scaled=Math.min(W,Math.round(g.width()*H/(float)g.height())),offset=(W-scaled)/2;
        if(scaled<1)return result;
        for(int y=0;y<H;y++)for(int x=0;x<scaled;x++)if(mask[(g.top+y*g.height()/H)*width+g.left+x*g.width()/scaled])result[y*W+offset+x]=1;
        return result;
    }
    int digit(byte[] image){
        double best=1,second=1;int value=-1;
        for(int d=0;d<10;d++){
            double score=1;
            for(byte[] sample:samples[d]){int distance=0;for(int i=0;i<image.length;i++)distance+=Math.abs(image[i]-sample[i]);score=Math.min(score,distance/(double)image.length);}
            if(score<best){second=best;best=score;value=d;}else second=Math.min(second,score);
        }
        return best<=.20&&second-best>=.04?value:-1;
    }
}
