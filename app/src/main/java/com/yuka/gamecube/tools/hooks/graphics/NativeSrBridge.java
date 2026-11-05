package com.yuka.gamecube.tools.hooks.graphics;

final class NativeSrBridge {
    static void load() { System.loadLibrary("gamecube_tuning"); }
    static native boolean selectTarget(int sdk);
    static native void metadata(String pkg,String build);
    static native String candidate();
    static native int install(String path,String sha256);
    static native int status();
    static native void configure(boolean enabled,int algorithm,int width,int source,
                                 double sharpness,double dark,double white,long revision);
    private NativeSrBridge() {}
}
