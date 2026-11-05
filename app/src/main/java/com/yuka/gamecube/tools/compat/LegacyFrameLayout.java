package com.yuka.gamecube.tools.compat;

/** Pinned pre-OS6 controller lifecycle; OS4 profiles have the original eight fields. */
public record LegacyFrameLayout(boolean os4) {
    public static LegacyFrameLayout forSdk(int sdk) {
        if(sdk!=34 && sdk!=35) throw new IllegalArgumentException("Unsupported legacy frame SDK: "+sdk);
        return new LegacyFrameLayout(sdk==34);
    }
    public String bind() { return os4?"n0":"F0"; }
    public String cool() { return os4?"N":"Z"; }
    public String hot() { return os4?"O":"X"; }
    public String disable() { return os4?"L":"V"; }
    public String state() { return os4?"V":"l0"; }
    public String view() { return os4?"m0":"D0"; }
    public String recover() { return os4?"R":"h0"; }
    public String dispose() { return os4?"W":"m0"; }
    public String stop() { return os4?"F":"L"; }
    public String schedule() { return os4?"e0":"u0"; }
    public String change() { return os4?"G":"M"; }
    public String strategyField() { return os4?"p":"r"; }
    public String menuCaller() { return os4?"z0":"x0"; }
    public String pidRevised() { return os4?"i1":"f1"; }
    public String lookupPid() { return os4?"Q":"P"; }
    public String serviceInit() { return os4?"g":"h"; }
    public String utils() { return os4?"mb.n0":"com.vivo.gameassistant.utils.z0"; }
    public String writer() { return os4?"e7.n":"a7.t"; }
    public String dispatch() { return os4?"k":"l"; }
    public String lookupProfile() { return os4?"o":"s"; }
}
