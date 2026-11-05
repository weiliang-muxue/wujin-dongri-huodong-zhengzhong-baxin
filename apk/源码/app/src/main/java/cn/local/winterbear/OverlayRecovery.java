package cn.local.winterbear;

/** Bounds retries across placement and frame invalidation, including OEM clamping. */
final class OverlayRecovery {
    enum Action { WAIT, COLLAPSE, STOP }
    private long since=-1;
    void reset(){since=-1;}
    Action blocked(long now,boolean collapsed){
        if(since<0)since=now;
        long elapsed=now-since;
        if(elapsed>=6000)return Action.STOP;
        return !collapsed&&elapsed>=1200?Action.COLLAPSE:Action.WAIT;
    }
}
