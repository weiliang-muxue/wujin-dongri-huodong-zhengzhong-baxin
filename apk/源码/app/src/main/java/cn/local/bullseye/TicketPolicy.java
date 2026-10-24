package cn.local.bullseye;

/** Decisions based on fresh home-screen readings, never estimated deductions. */
public final class TicketPolicy {
    public enum Action { WAIT, READ_ERROR, DONE, START, CHECK_BUTTON, SWITCH, BUTTON_ERROR, SWITCH_ERROR }
    private Integer candidate;
    private int seen, misses, buttonMisses, switchAttempts;
    public int remaining;

    public void reset() { candidate=null;seen=0;misses=0;buttonMisses=0;switchAttempts=0; }
    public void started() { candidate=null;seen=0; }
    public Action read(Integer actual) {
        if(actual==null) {
            candidate=null;seen=0;
            return ++misses>=3?Action.READ_ERROR:Action.WAIT;
        }
        misses=0;seen=actual.equals(candidate)?seen+1:1;candidate=actual;
        if(seen<2)return Action.WAIT;
        remaining=actual;
        if(remaining==0)return Action.DONE;
        return remaining<5?Action.CHECK_BUTTON:Action.START;
    }
    public Action button(int value) {
        if(value!=1&&value!=5)return ++buttonMisses>=3?Action.BUTTON_ERROR:Action.WAIT;
        buttonMisses=0;
        if(value==5)return switchAttempts>=2?Action.SWITCH_ERROR:Action.SWITCH;
        switchAttempts=0;return Action.START;
    }
    public void switched() { switchAttempts++; }
}
