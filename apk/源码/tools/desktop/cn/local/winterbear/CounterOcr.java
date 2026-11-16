package cn.local.winterbear;
import android.graphics.*;
/** Bundled ML Kit is unavailable here; the optional pixel counter is production code. */
final class CounterOcr implements Matcher.Numbers,AutoCloseable {
 private final MarchCounterVision counter;
 CounterOcr(){counter=null;}
 CounterOcr(FeedbackVision.Assets assets){counter=new MarchCounterVision(assets);}
 public int read(Bitmap b,Rect r){return counter==null?-1:counter.read(b,r);}
 String text(Bitmap b,Rect r){int value=read(b,r);return value<0?"":value+"/6";}
 public void close(){}
}
