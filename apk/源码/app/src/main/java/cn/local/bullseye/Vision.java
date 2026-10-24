package cn.local.bullseye;
import android.graphics.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Vision implements AutoCloseable {
    final TextRecognizer reader=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    public List<Text.Line> text(Bitmap b)throws Exception {
        Text result=Tasks.await(reader.process(InputImage.fromBitmap(b,0)),15,TimeUnit.SECONDS);
        List<Text.Line> lines=new ArrayList<>();for(Text.TextBlock block:result.getTextBlocks())lines.addAll(block.getLines());return lines;
    }
    public Integer number(Bitmap b)throws Exception {
        Bitmap big=Bitmap.createScaledBitmap(b,Math.max(160,b.getWidth()*3),Math.max(90,b.getHeight()*3),true);
        try{for(Text.Line l:text(big)){String s=l.getText().replace("O","0").replace("o","0").replace(" ","");if(s.matches("\\d{1,5}"))return Integer.parseInt(s);}}finally{if(big!=b)big.recycle();}
        int w=b.getWidth(),h=b.getHeight();int[] pixels=new int[w*h];b.getPixels(pixels,0,w,0,0,w,h);return TicketDigits.single(pixels,w,h);
    }
    public static Bitmap crop(Bitmap b,float[] r){int x=Math.max(0,(int)(r[0]*b.getWidth())),y=Math.max(0,(int)(r[1]*b.getHeight()));int w=Math.max(1,Math.min(b.getWidth(),(int)(r[2]*b.getWidth()))-x),h=Math.max(1,Math.min(b.getHeight(),(int)(r[3]*b.getHeight()))-y);return Bitmap.createBitmap(b,x,y,w,h);}
    public static Bitmap patch(Bitmap b,PointF p,float radius){return crop(b,new float[]{Math.max(0,p.x-radius),Math.max(0,p.y-radius),Math.min(1,p.x+radius),Math.min(1,p.y+radius)});}
    public static class MultiplierButton {
        public final int value;public final PointF point;
        MultiplierButton(int value,PointF point){this.value=value;this.point=point;}
    }
    /** Invoked only when the confirmed home balance is 1..4. */
    public static MultiplierButton lowBalanceButton(Bitmap frame,Config c){
        int w=frame.getWidth(),h=frame.getHeight();int[] pixels=new int[w*h];frame.getPixels(pixels,0,w,0,0,w,h);
        LowBalanceControl.Hit hit=LowBalanceControl.find(pixels,w,h,c.start.x,c.start.y,c.targets.get(1).x-c.targets.get(0).x,c.targets.get(4).y-c.targets.get(0).y);
        return hit==null?null:new MultiplierButton(hit.value,new PointF(hit.x,hit.y));
    }
    public static float[] exitRegion(List<PointF> ps){float dx=ps.get(1).x-ps.get(0).x,dy=ps.get(4).y-ps.get(0).y,y=ps.get(8).y;return new float[]{Math.max(0,ps.get(0).x-.75f*dx),Math.max(0,y+.5f*dy),Math.min(1,ps.get(3).x+.75f*dx),Math.min(1,y+1.65f*dy)};}
    public PointF dismissPoint(Bitmap frame,float[] region)throws Exception {
        Bitmap clipped=crop(frame,region);float scale=Math.max(1,Math.min(3,1200f/clipped.getWidth()));Bitmap big=Bitmap.createScaledBitmap(clipped,Math.round(clipped.getWidth()*scale),Math.round(clipped.getHeight()*scale),true);
        try{StringBuilder message=new StringBuilder();for(Text.Line line:text(big))message.append(line.getText());String s=message.toString().replaceAll("\\s+","");if(s.contains("点击")&&(s.contains("任意")||s.contains("空白")||s.contains("退出")||s.contains("关闭")))return new PointF((region[0]+region[2])/2,(region[1]+region[3])/2);return null;}finally{if(big!=clipped)big.recycle();clipped.recycle();}
    }
    public static double similarity(Bitmap a,Bitmap b){
        Bitmap aa=Bitmap.createScaledBitmap(a,32,32,true),bb=Bitmap.createScaledBitmap(b,32,32,true);int[] pa=new int[1024],pb=new int[1024];aa.getPixels(pa,0,32,0,0,32,32);bb.getPixels(pb,0,32,0,0,32,32);
        double sx=0,sy=0,sxx=0,syy=0,sxy=0,abs=0;
        for(int i=0;i<pa.length;i++){double x=(Color.red(pa[i])+Color.green(pa[i])+Color.blue(pa[i]))/3.,y=(Color.red(pb[i])+Color.green(pb[i])+Color.blue(pb[i]))/3.;sx+=x;sy+=y;sxx+=x*x;syy+=y*y;sxy+=x*y;abs+=Math.abs(x-y);}
        if(aa!=a)aa.recycle();if(bb!=b)bb.recycle();if(sx<sy*.6)return 0;
        double den=Math.sqrt(Math.max(0,(sxx-sx*sx/1024)*(syy-sy*sy/1024)));return den<1?1-abs/1024/255:(sxy-sx*sy/1024)/den;
    }
    static class Blob {float x,y,d;int area;Blob(float x,float y,float d,int area){this.x=x;this.y=y;this.d=d;this.area=area;}}
    static List<Blob> blobs(Bitmap original,boolean green){return blobs(original,green?1:0);}
    static List<Blob> blobs(Bitmap original,int color){
        float scale=Math.min(1,1200f/Math.max(original.getWidth(),original.getHeight()));int w=Math.max(1,Math.round(original.getWidth()*scale)),h=Math.max(1,Math.round(original.getHeight()*scale));Bitmap b=Bitmap.createScaledBitmap(original,w,h,true);
        int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);byte[] mask=new byte[px.length];float[] hsv=new float[3];
        for(int i=0;i<px.length;i++){Color.colorToHSV(px[i],hsv);boolean match=color==1?hsv[0]>70&&hsv[0]<180&&hsv[1]>.4&&hsv[2]>.4:color==2?((hsv[0]>70&&hsv[0]<180)||(hsv[0]>240&&hsv[0]<320))&&hsv[1]>.32&&hsv[2]>.25:(hsv[0]<28||hsv[0]>330)&&hsv[1]>.33&&hsv[2]>.29;if(match)mask[i]=1;}
        if(b!=original)b.recycle();int[] queue=new int[px.length];List<Blob> out=new ArrayList<>();
        for(int i=0;i<mask.length;i++)if(mask[i]==1){int head=0,tail=1,minX=i%w,maxX=minX,minY=i/w,maxY=minY;queue[0]=i;mask[i]=0;
            while(head<tail){int q=queue[head++],x=q%w,y=q/w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);int[] ns={x>0?q-1:-1,x+1<w?q+1:-1,y>0?q-w:-1,y+1<h?q+w:-1};for(int n:ns)if(n>=0&&mask[n]==1){mask[n]=0;queue[tail++]=n;}}
            float bw=maxX-minX+1,bh=maxY-minY+1;
            if(tail>(color==1?8:45)&&bw/bh>.6&&bw/bh<1.6&&bw>4)out.add(new Blob((minX+maxX)/2f/w,(minY+maxY)/2f/h,Math.max(bw,bh)/scale,tail));
        }
        out.sort((a,c)->Integer.compare(c.area,a.area));List<Blob> dedup=new ArrayList<>();for(Blob p:out){boolean near=false;for(Blob q:dedup)if(Math.hypot((p.x-q.x)*original.getWidth(),(p.y-q.y)*original.getHeight())<Math.min(p.d,q.d)*.35){near=true;break;}if(!near)dedup.add(p);if(dedup.size()>=160)break;}return dedup;
    }
    public static List<PointF> targets(Bitmap b){
        List<Blob> ps=blobs(b,false);List<PointF> best=new ArrayList<>();double bestError=1e9;int w=b.getWidth(),h=b.getHeight();
        for(Blob p:ps)for(Blob right:ps){float dx=(right.x-p.x)*w;if(dx<p.d*.85||dx>p.d*3||Math.abs(right.y-p.y)*h>p.d*.3)continue;
            for(Blob down:ps){float dy=(down.y-p.y)*h;if(dy<p.d*.85||dy>p.d*3||Math.abs(down.x-p.x)*w>p.d*.3)continue;
                Set<Blob> used=new HashSet<>();List<PointF> grid=new ArrayList<>();double error=0;
                for(int r=0;r<3;r++)for(int c=0;c<4;c++){double min=1e9;Blob hit=null;for(Blob q:ps){double d=Math.hypot((q.x-p.x)*w-c*dx,(q.y-p.y)*h-r*dy);if(d<min){min=d;hit=q;}}used.add(hit);grid.add(new PointF(hit.x,hit.y));error+=min;}
                if(used.size()==12&&error<p.d*1.5&&error<bestError){best=grid;bestError=error;}
            }
        }return best;
    }
    static PointF center(Text.Line line,float[] region,int w,int h){Rect r=line.getBoundingBox();return new PointF(region[0]+r.exactCenterX()/w,region[1]+r.exactCenterY()/h);}
    public Config analyze(Bitmap home,Bitmap board)throws Exception {
        if(home.getWidth()!=board.getWidth()||home.getHeight()!=board.getHeight())throw new Exception("两张图片尺寸不同，请上传同一手机的全屏原图");
        Config c=new Config();c.width=home.getWidth();c.height=home.getHeight();c.targets=targets(board);if(c.targets.size()!=12)throw new Exception("第二张图未识别到完整的 3 行 × 4 列靶子，请重传尚未射击的全屏原图");
        float dx=c.targets.get(1).x-c.targets.get(0).x,dy=c.targets.get(4).y-c.targets.get(0).y;
        c.region=new float[]{Math.max(0,c.targets.get(0).x-.75f*dx),Math.max(0,c.targets.get(0).y-3.9f*dy),Math.min(1,c.targets.get(3).x+.75f*dx),Math.min(1,c.targets.get(8).y+.9f*dy)};
        Bitmap roi=crop(home,c.region);List<Text.Line> lines=text(roi);roi.recycle();
        for(Text.Line l:lines)if(l.getText().contains("开始")&&center(l,c.region,c.width,c.height).y>c.region[1]+(c.region[3]-c.region[1])*.6)c.start=center(l,c.region,c.width,c.height);
        if(c.start==null)throw new Exception("第一张图未识别到开始射击按钮，请重传活动首页原图");
        float gw=c.region[2]-c.region[0],gh=c.region[3]-c.region[1];Integer count=null;
        for(Blob p:blobs(home,true)){if(p.x<c.region[0]+gw*.65||p.x>c.region[2]||p.y<c.region[1]+gh*.12||p.y>c.region[1]+gh*.42||p.d>gw*c.width*.09)continue;
            float[] r={p.x-gw*.14f,p.y-gh*.023f,p.x-gw*.025f,p.y+gh*.023f};Bitmap n=crop(home,r);Integer value=number(n);n.recycle();if(value!=null){count=value;c.countRect=r;break;}}
        if(count==null)for(Text.Line l:lines){PointF p=center(l,c.region,c.width,c.height);String s=l.getText().trim();if(s.matches("\\d{1,5}")&&p.x>c.region[0]+gw*.65&&p.y>c.region[1]+gh*.12&&p.y<c.region[1]+gh*.42){count=Integer.parseInt(s);c.countRect=new float[]{p.x-.04f,p.y-.023f,p.x+.04f,p.y+.023f};break;}}
        if(count==null)throw new Exception("第一张图的券数未识别清楚，请上传未压缩的全屏原图");c.count=count;c.exitRect=exitRegion(c.targets);return c;
    }
    public void close(){reader.close();}
}
