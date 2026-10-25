package cn.local.winterbear;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Only screen geometry is used; names and OCR text are never inputs. */
final class RallyOrder {
    static final class Detection {
        final int x,y,joinX,joinY;
        final boolean available;
        final double confidence;
        Detection(int x,int y,int joinX,int joinY,boolean available,double confidence) {
            this.x=x;this.y=y;this.joinX=joinX;this.joinY=joinY;this.available=available;this.confidence=confidence;
        }
    }
    static List<BearFlow.Row> rows(List<Detection> input,int duplicateDistance) {
        List<Detection> ranked=new ArrayList<>(input);
        ranked.sort(Comparator.comparingDouble((Detection d)->d.confidence).reversed());
        List<Detection> unique=new ArrayList<>();
        for(Detection d:ranked) {
            boolean duplicate=false;
            for(Detection previous:unique) {
                if(Math.abs(previous.y-d.y)<=duplicateDistance&&Math.abs(previous.x-d.x)<=duplicateDistance) {duplicate=true;break;}
            }
            if(!duplicate)unique.add(d);
        }
        unique.sort(Comparator.comparingInt((Detection d)->d.y).thenComparingInt(d->d.x));
        List<BearFlow.Row> result=new ArrayList<>();
        for(int i=0;i<unique.size();i++) {
            Detection d=unique.get(i);
            // Unavailable rows still occupy their visible position; later rows are not renumbered.
            result.add(new BearFlow.Row(i,d.joinX,d.joinY,d.available));
        }
        return result;
    }
}
