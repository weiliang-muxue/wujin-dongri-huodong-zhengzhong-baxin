package cn.local.winterbear;

/** Brief system overlays suspend actions; an actual app switch stops after a bounded grace period. */
public final class ForegroundGate {
    private long missingAt=-1;
    public boolean timedOut(String target,String observed,long now){
        // A missing root or temporary system surface gives no evidence that the
        // user switched apps. Wait for a fresh foreground observation.
        if(observed==null||observed.isEmpty()){reset();return false;}
        return timedOut(target.equals(observed),now);
    }
    boolean timedOut(boolean targetForeground,long now){
        if(targetForeground){missingAt=-1;return false;}
        if(missingAt<0)missingAt=now;
        return now-missingAt>=3000;
    }
    public void reset(){missingAt=-1;}
}
