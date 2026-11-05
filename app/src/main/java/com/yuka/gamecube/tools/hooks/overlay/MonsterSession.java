package com.yuka.gamecube.tools.hooks.overlay;

import com.yuka.gamecube.tools.core.ConfigSnapshot;

/** Admission belongs to one foreground controller, not a persistent Bluetooth or system flag. */
final class MonsterSession {
    private record Entry(Object controller,String pkg) {}
    private volatile Entry current;

    void enter(Object controller,String pkg) {
        current=new Entry(controller,pkg==null?"":pkg);
    }
    void exit(Object controller) {
        Entry value=current;
        if(value!=null && value.controller()==controller) current=null;
    }
    String pkg() { Entry value=current;return value==null?"":value.pkg(); }
    Object controller() { Entry value=current;return value==null?null:value.controller(); }
    boolean allows(ConfigSnapshot config,boolean supported) {
        Entry value=current;
        return supported && value!=null && config.allows(value.pkg(),"monsterPlus");
    }
    static boolean protectDisconnectClear(boolean requested,int disconnectDepth,boolean admitted) {
        return !requested && disconnectDepth>0 && admitted;
    }
}
