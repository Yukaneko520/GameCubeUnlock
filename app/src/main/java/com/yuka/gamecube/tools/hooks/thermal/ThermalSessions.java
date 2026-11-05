package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.concurrent.ConcurrentHashMap;

/** OEM foreground/death events define lifetime; object identity distinguishes recycled PIDs. */
public final class ThermalSessions {
    public static final class Session {
        public final int pid; public final String pkg;
        private Session(int pid,String pkg) { this.pid=pid;this.pkg=pkg; }
    }
    private final ConcurrentHashMap<Integer,Session> live=new ConcurrentHashMap<>();
    public void enter(int pid,String pkg) {
        if(pid>0 && ConfigSnapshot.validPackage(pkg)) live.compute(pid,(key,old)->old!=null && old.pkg.equals(pkg)?old:new Session(pid,pkg));
    }
    public void exit(int pid,String pkg) { live.computeIfPresent(pid,(key,old)->old.pkg.equals(pkg)?null:old); }
    public Session get(int pid) { return live.get(pid); }
    public boolean current(Session ticket) { return ticket!=null && live.get(ticket.pid)==ticket; }
    public boolean allows(ConfigSnapshot config,int pid,String pkg) {
        Session session=live.get(pid);
        return session!=null && session.pkg.equals(pkg) && config.allows(pkg,"thermalBypass");
    }
}
