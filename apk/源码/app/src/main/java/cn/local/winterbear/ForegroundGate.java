package cn.local.winterbear;

/** Brief system overlays suspend actions; an actual app switch stops after a bounded grace period. */
final class ForegroundGate {
    private long missingAt=-1;
    boolean timedOut(boolean targetForeground,long now){
        if(targetForeground){missingAt=-1;return false;}
        if(missingAt<0)missingAt=now;
        return now-missingAt>=3000;
    }
    void reset(){missingAt=-1;}
}
