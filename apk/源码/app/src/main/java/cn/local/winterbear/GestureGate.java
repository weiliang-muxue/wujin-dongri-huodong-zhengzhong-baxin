package cn.local.winterbear;

/** Each callback and timeout belongs to exactly one dispatched gesture. */
final class GestureGate {
    private long next,active;
    long begin() { active=++next;return active; }
    boolean finish(long ticket) {
        if(ticket==0||ticket!=active)return false;
        active=0;return true;
    }
    void cancel() { active=0; }
}
