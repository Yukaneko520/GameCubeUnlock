package com.yuka.gamecube.tools.hooks.thermal;

/** One queued OEM render command: stock work is unchanged until this option actually touches it. */
public final class ThermalPending {
    public final Object owner;
    public final ThermalSessions.Session session;
    public final long revision;
    private final boolean bypassRender;
    private volatile boolean invalidated;
    public ThermalPending(Object owner,ThermalSessions.Session session,long revision,boolean bypassRender) {
        this.owner=owner;this.session=session;this.revision=revision;this.bypassRender=bypassRender;
    }
    public void invalidate() { invalidated=true; }
    public boolean blocked(ThermalSessions sessions,boolean selectedNow,Object request,Object currentRequest) {
        if(invalidated) return true;
        return bypassRender ? !selectedNow || !sessions.current(session) || request!=currentRequest : selectedNow;
    }
}
