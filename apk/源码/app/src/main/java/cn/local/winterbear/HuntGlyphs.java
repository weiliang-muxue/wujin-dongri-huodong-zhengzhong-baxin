package cn.local.winterbear;

import android.graphics.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Small game-font readers. Level digits are isolated from the Chinese target name. */
final class HuntGlyphs implements AutoCloseable {
    private final CountdownDigits levels,timers;
    private final MarchRowVision icons;
    HuntGlyphs(FeedbackVision.Assets assets){levels=load(assets,"level_digits");timers=load(assets,"march_digits");icons=new MarchRowVision(assets);}
    private static CountdownDigits load(FeedbackVision.Assets assets,String name){
        CountdownDigits d=new CountdownDigits();
        try(InputStream in=assets.open("hunt/"+name+".png")){
            Bitmap b=BitmapFactory.decodeStream(in);
            try{
                if(b==null||b.getWidth()!=240||b.getHeight()%32!=0)throw new IllegalArgumentException(name);
                int[] p=new int[768];
                for(int y=0;y<b.getHeight();y+=32)for(int n=0;n<10;n++){
                    b.getPixels(p,0,24,n*24,y,24,32);byte[] sample=new byte[768];int ink=0;
                    for(int i=0;i<768;i++)if((p[i]&255)>128){sample[i]=1;ink++;}
                    if(ink>0)d.samples[n].add(sample);
                }
            }finally{if(b!=null)b.recycle();}
        }catch(Exception e){throw new IllegalStateException("数字素材不可用："+name,e);}
        return d;
    }
    static Bitmap region(Bitmap image,double scale,int l,int t,int r,int b,int width,int height){
        int left=Math.max(0,(int)Math.round(l*scale)),top=Math.max(0,(int)Math.round(t*scale));
        int right=Math.min(image.getWidth(),(int)Math.round(r*scale)),bottom=Math.min(image.getHeight(),(int)Math.round(b*scale));
        if(right<=left||bottom<=top)return null;
        Bitmap crop=Bitmap.createBitmap(image,left,top,right-left,bottom-top);
        Bitmap result=Bitmap.createScaledBitmap(crop,width,height,true);
        if(result!=crop)crop.recycle();return result;
    }
    int level(Bitmap image,double scale,int rightOfLabel,int middle,HuntVision.Numbers ocr){
        return level(image,scale,rightOfLabel,middle,1,ocr);
    }
    int level(Bitmap image,double scale,int rightOfLabel,int middle,double contentScale,HuntVision.Numbers ocr){
        Bitmap crop=region(image,scale,rightOfLabel,(int)Math.floor(middle-13*contentScale),(int)Math.ceil(rightOfLabel+64*contentScale),(int)Math.ceil(middle+13*contentScale),128,52);
        if(crop==null)return -1;
        try{
            int[] p=new int[128*52];crop.getPixels(p,0,128,0,0,128,52);
            boolean[] ink=new boolean[p.length];int[] cols=new int[128];
            for(int i=0;i<p.length;i++){
                int r=Color.red(p[i]),g=Color.green(p[i]),b=Color.blue(p[i]);
                if(r<145&&g>r*1.15&&b>g*1.1){ink[i]=true;cols[i%128]++;}
            }
            List<CountdownDigits.Glyph> glyphs=new ArrayList<>();
            for(int x=0;x<128;){
                if(cols[x]<2){x++;continue;}int l=x;while(x<128&&cols[x]>=2)x++;
                int top=52,bottom=0;for(int y=0;y<52;y++)for(int j=l;j<x;j++)if(ink[y*128+j]){top=Math.min(top,y);bottom=Math.max(bottom,y+1);}
                if(bottom-top<14)continue;
                if(x-l>(bottom-top)*.90)break; // The following Chinese name is outside the numeric field.
                glyphs.add(new CountdownDigits.Glyph(l,x,top,bottom));
            }
            if(glyphs.isEmpty()||glyphs.size()>2)return -1;
            int value=0;boolean known=true;
            for(CountdownDigits.Glyph g:glyphs){int n=levels.digit(levels.normalize(g,ink,128));if(n<0)known=false;else value=value*10+n;}
            if(known&&value>0)return value;
            // OCR fallback receives only the number, never "等级…目标名字".
            CountdownDigits.Glyph first=glyphs.get(0),last=glyphs.get(glyphs.size()-1);
            Rect number=new Rect(Math.max(0,first.left-2),Math.max(0,Math.min(first.top,last.top)-3),Math.min(128,last.right+2),Math.min(52,Math.max(first.bottom,last.bottom)+3));
            return HuntVision.number(ocr.text(crop,number));
        }finally{crop.recycle();}
    }
    HuntMarches.Row[] marches(Bitmap image,double scale,int header,int count,int dx,org.opencv.core.Mat grayImage){
        if(count==0)return new HuntMarches.Row[0];
        if(count<0||count>6)return null;
        List<MarchRowVision.Marker> markers=icons.find(grayImage,header,dx);
        if(markers.size()>count)return null; // Header/list are in different animation frames.
        if(markers.size()==count){
            HuntMarches.Row[] result=new HuntMarches.Row[count];int last=header;
            for(int i=0;i<count;i++){
                MarchRowVision.Marker m=markers.get(i);
                if(m.y-last<(i==0?28:35)||m.y-last>65)return null;
                result[i]=new HuntMarches.Row(icon(image,scale,m.y,dx),timer(image,scale,m.y,dx),m.type);last=m.y;
            }
            return result;
        }
        // Unknown icon styles retain the previous timer reader. Known resource
        // anchors still prevent those rows from inheriting a returned hunt slot.
        int top=header+25,height=Math.min(345,(int)(image.getHeight()/scale)-top);
        if(height<32)return null;
        Bitmap strip=region(image,scale,55+dx,top,145+dx,top+height,180,height*2);
        if(strip==null)return null;
        try{
            int[] p=new int[180*height*2];strip.getPixels(p,0,180,0,0,180,height*2);
            byte[] gray=new byte[p.length];for(int i=0;i<p.length;i++)gray[i]=(byte)Math.min(Color.red(p[i]),Math.min(Color.green(p[i]),Color.blue(p[i])));
            List<HuntMarches.Row> rows=new ArrayList<>();int lastY=-100;
            byte[] band=new byte[180*32];
            for(int y=0;y<=height*2-32;y+=2){
                System.arraycopy(gray,y*180,band,0,band.length);int seconds=timers.readSmall(band,180,32);if(seconds<0)continue;
                int min=32,max=0;for(int i=0;i<band.length;i++)if((band[i]&255)>190){min=Math.min(min,i/180);max=Math.max(max,i/180);}
                int center=top+(y+(min+max)/2)/2;
                if(rows.isEmpty()?center-header<35||center-header>65:center-lastY<35||center-lastY>65)continue;
                HuntMarches.Type type=HuntMarches.Type.UNKNOWN;
                for(MarchRowVision.Marker m:markers)if(Math.abs(m.y-(center-9))<10){type=m.type;break;}
                rows.add(new HuntMarches.Row(icon(image,scale,center-9,dx),seconds,type));lastY=center;
                if(rows.size()==count)return rows.toArray(new HuntMarches.Row[0]);
                y+=60;
            }
            return null; // A partially read list never releases an owned slot.
        }finally{strip.recycle();}
    }
    private int timer(Bitmap image,double scale,int center,int dx){
        Bitmap strip=region(image,scale,55+dx,center+1,145+dx,center+20,180,38);
        if(strip==null)return -1;
        try{return adaptiveTimer(strip);}finally{strip.recycle();}
    }
    private int adaptiveTimer(Bitmap strip){
        int originalWidth=strip.getWidth(),originalHeight=strip.getHeight();byte[] gray=timerGray(strip);
        int direct=timers.readSmall(gray,originalWidth,originalHeight);if(direct>=0)return direct;
        // Small fonts change shape when a display scale moves a stroke across a pixel.
        // Retry nearby sampling sizes with the same glyph confidence and HH:MM:SS
        // validation. Require independent sizes to agree; ambiguity stays unknown.
        int value=-1,agree=0;
        int radius=Math.max(1,Math.round(originalWidth*.045f));
        // Visit every nearby normalized size so a useful sampling phase is not skipped.
        for(int width=Math.max(1,originalWidth-radius);width<=originalWidth+radius;width++){
            if(width==originalWidth)continue;
            int height=Math.max(1,Math.round(originalHeight*width/(float)originalWidth));
            byte[] sampled=resampleTimerGray(gray,originalWidth,originalHeight,width,height);
            int candidate=timers.readSmall(sampled,width,height);
            if(candidate<0)continue;
            if(value>=0&&value!=candidate)return -1;
            value=candidate;agree++;
        }
        return agree>=2?value:-1;
    }
    private static byte[] timerGray(Bitmap strip){
        int width=strip.getWidth(),height=strip.getHeight();int[] p=new int[width*height];byte[] gray=new byte[p.length];
        strip.getPixels(p,0,width,0,0,width,height);
        for(int i=0;i<p.length;i++)gray[i]=(byte)Math.min(Color.red(p[i]),Math.min(Color.green(p[i]),Color.blue(p[i])));
        return gray;
    }
    /** Pixel-centre bilinear sampling with identical integer rounding on Android/JVM. */
    private static byte[] resampleTimerGray(byte[] source,int sw,int sh,int tw,int th){
        byte[] output=new byte[tw*th];long xd=2L*tw,yd=2L*th,den=xd*yd;
        for(int y=0;y<th;y++){
            long yn=(2L*y+1)*sh-th;int rawY=(int)Math.floorDiv(yn,yd);long fy=yn-rawY*yd;
            int y0=Math.max(0,Math.min(sh-1,rawY)),y1=Math.max(0,Math.min(sh-1,rawY+1));
            for(int x=0;x<tw;x++){
                long xn=(2L*x+1)*sw-tw;int rawX=(int)Math.floorDiv(xn,xd);long fx=xn-rawX*xd;
                int x0=Math.max(0,Math.min(sw-1,rawX)),x1=Math.max(0,Math.min(sw-1,rawX+1));
                long top=(source[y0*sw+x0]&255)*(xd-fx)+(source[y0*sw+x1]&255)*fx;
                long bottom=(source[y1*sw+x0]&255)*(xd-fx)+(source[y1*sw+x1]&255)*fx;
                output[y*tw+x]=(byte)((top*(yd-fy)+bottom*fy+den/2)/den);
            }
        }
        return output;
    }
    public void close(){icons.close();}
    private long icon(Bitmap image,double scale,int center,int dx){
        Bitmap b=region(image,scale,12+dx,center-13,39+dx,center+14,8,8);if(b==null)return 0;
        try{
            int[] p=new int[64],light=new int[64];b.getPixels(p,0,8,0,0,8,8);int sum=0;
            for(int i=0;i<64;i++){light[i]=Color.red(p[i])+Color.green(p[i])+Color.blue(p[i]);sum+=light[i];}
            long bits=0;for(int i=0;i<64;i++)if(light[i]*64>=sum)bits|=1L<<i;return bits;
        }finally{b.recycle();}
    }
}
