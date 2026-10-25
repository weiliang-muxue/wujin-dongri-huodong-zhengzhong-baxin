package cn.local.bullseye;

/** A queued callback may only resume the session that created it. */
final class SessionGuard {
    static boolean canResume(long expected,long current,boolean running,boolean paused,boolean ownsMode){
        return expected==current&&running&&paused&&ownsMode;
    }
}
