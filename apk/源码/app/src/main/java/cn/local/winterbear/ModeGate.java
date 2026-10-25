package cn.local.winterbear;

/** One screen controller owns gestures at a time. Switching invalidates old ownership. */
public final class ModeGate {
    public enum Mode { NONE, BEAR, BULLSEYE }
    private Mode mode=Mode.NONE;
    private long epoch;
    public synchronized long select(Mode next){mode=next;return ++epoch;}
    public synchronized boolean owns(Mode expected,long token){return mode==expected&&epoch==token;}
    public synchronized boolean is(Mode expected){return mode==expected;}
}
