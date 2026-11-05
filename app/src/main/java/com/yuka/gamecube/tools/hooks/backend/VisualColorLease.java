package com.yuka.gamecube.tools.hooks.backend;

/** Own only the requested, actually selected game color; never claim an OEM fallback. */
final class VisualColorLease {
    private String pkg="";
    private int mode= -1;
    String packageName() { return pkg; }
    int mode() { return mode; }
    boolean claim(String target,int requested,int actual) {
        clear();
        if(target==null || target.isEmpty() || requested<270 || requested>273 || actual!=requested) return false;
        pkg=target;mode=actual;return true;
    }
    boolean matches(int actual) { return !pkg.isEmpty() && actual==mode; }
    void clear() { pkg="";mode= -1; }
}
