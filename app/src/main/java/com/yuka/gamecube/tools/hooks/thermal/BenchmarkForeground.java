package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.core.ConfigSnapshot;

/** One foreground owner; delayed exits from a different process must not revoke the new session. */
public final class BenchmarkForeground {
    public record Session(int uid,int pid,String pkg) {}
    private Session current,candidate;
    public Session current() { return current; }
    public Session candidate() { return candidate; }
    public void clear() { current=null;candidate=null; }
    public void remember() { candidate=current; }
    public void suspend() { current=null; }
    public boolean resume(Session observed,boolean allowed,boolean interactive,boolean unlocked) {
        if(!allowed || !interactive || !unlocked || candidate==null || !candidate.equals(observed)
                || current!=null && !current.equals(observed)) return false;
        current=observed;return true;
    }
    public void foreground(int uid,boolean entered,int pid,String pkg) {
        if(entered) {
            current=pid>0 && ConfigSnapshot.validPackage(pkg)?new Session(uid,pid,pkg):null;
        } else if(current!=null && current.uid==uid && current.pid==pid
                && (pkg==null || current.pkg.equals(pkg))) current=null;
    }
    public void death(int uid,int pid,String pkg) {
        foreground(uid,false,pid,pkg);
        if(candidate!=null && candidate.uid==uid && candidate.pid==pid && (pkg==null || candidate.pkg.equals(pkg))) candidate=null;
    }
}
