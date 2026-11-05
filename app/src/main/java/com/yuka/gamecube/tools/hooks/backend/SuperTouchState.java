package com.yuka.gamecube.tools.hooks.backend;

/** OEM 0x82f|0x82f values packed by the two pinned GameWatch parsers. */
final class SuperTouchState {
    static final int FLAGS=0x082f082f;
    static boolean eligible(BackendPolicy policy,String pkg,int uid,boolean bound,Object userSwitch,
                            boolean api,boolean interactive) {
        return bound && api && interactive && Integer.valueOf(1).equals(userSwitch)
                && policy.control(pkg,"superTouch") && Integer.valueOf(uid).equals(policy.installedUid(pkg));
    }
    static int base(int stock) { return stock|1; }
    static int flags(int stock) { return stock|FLAGS; }
    static boolean needsExtension(boolean eligible,boolean nativeEnabled,int nativeFlags) {
        return eligible && (!nativeEnabled || nativeFlags==0);
    }
    static boolean matchesEvent(int liveUid,Integer eventUid) { return eventUid==null||eventUid==liveUid; }
    static int resolveUid(int procUid,Integer observerUid) {
        if(procUid>=0) return procUid;
        return observerUid!=null&&observerUid>=10000&&observerUid<100000?observerUid:-1;
    }
    private SuperTouchState() {}
}
