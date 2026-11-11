package cn.local.winterbear;
import android.graphics.*;
/** OCR is deliberately unavailable in the desktop page tests. */
final class CounterOcr implements Matcher.Numbers {
 public int read(Bitmap b,Rect r){return -1;}
 public void close(){}
}
