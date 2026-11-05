package com.yuka.gamecube.tools.hooks.backend;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Only original foreground callbacks identify a seed; ownership follows the exact native row. */
final class SuperTouchSessions {
    record Foreground(String pkg,int pid,int uid) {}
    private record Owned(String pkg,Object row) {}
    private record Observed(String pkg,int uid,Object row) {}
    private final HashMap<Integer,Owned> owned=new HashMap<>();
    private final HashMap<Integer,Observed> observed=new HashMap<>();
    private Foreground foreground;
    synchronized void foreground(String pkg,int pid,int uid,boolean entered) {
        if(entered) foreground=pid>0&&pkg!=null?new Foreground(pkg,pid,uid):null;
        else if(foreground!=null&&foreground.pid==pid&&Objects.equals(foreground.pkg,pkg)) foreground=null;
    }
    synchronized Foreground foreground() { return foreground; }
    synchronized void claim(String pkg,int pid,Object row) {
        if(pkg!=null&&pid>0&&row!=null) owned.put(pid,new Owned(pkg,row));
    }
    synchronized boolean owns(String pkg,int pid,Object row) {
        Owned value=owned.get(pid);
        return value!=null&&row!=null&&value.row==row&&value.pkg.equals(pkg);
    }
    synchronized void observe(String pkg,int pid,int uid,Object row) {
        if(pkg!=null&&pid>0&&uid>=10000&&uid<100000&&row!=null) observed.put(pid,new Observed(pkg,uid,row));
    }
    synchronized Integer uid(String pkg,int pid,Object row) {
        Observed value=observed.get(pid);
        return value!=null&&row!=null&&value.row==row&&value.pkg.equals(pkg)?value.uid:null;
    }
    synchronized List<Integer> pids() { return List.copyOf(owned.keySet()); }
    synchronized void disown(int pid) { owned.remove(pid); }
    synchronized void removed(int pid) { owned.remove(pid);observed.remove(pid); }
    synchronized void died(int pid) {
        removed(pid);if(foreground!=null&&foreground.pid==pid)foreground=null;
    }
}
