package cn.local.winterbear;

/** The user's enabled switch and a connected service are different observations. */
final class ServiceConnectionState {
    enum State { READY, CONNECTING, DISABLED, UNKNOWN }
    static State of(boolean connected,Boolean enabled){
        if(connected)return State.READY;
        if(enabled==null)return State.UNKNOWN;
        return enabled?State.CONNECTING:State.DISABLED;
    }
}
