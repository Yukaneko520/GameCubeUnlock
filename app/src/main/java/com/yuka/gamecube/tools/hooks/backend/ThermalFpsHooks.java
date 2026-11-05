package com.yuka.gamecube.tools.hooks.backend;

import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
import com.yuka.gamecube.tools.hooks.Reflect;
import com.yuka.gamecube.tools.hooks.thermal.ThermalRequestState;

/** Owner monitor/handler protects native request ownership; all other Power/FixRate clients stay stock. */
final class ThermalFpsHooks {
    private final ThermalWatchHooks w;
    private volatile Object owner;
    ThermalFpsHooks(ThermalWatchHooks w) { this.w=w; }
    Object lock() { Object monitor=owner;return monitor==null?this:monitor; }
    void install() throws Throwable {
        Class<?> requester=w.type(w.names.fpsOwner()+"$a");
        for(String name:new String[]{"a","b","c","d",w.names.fpsHandle(),w.names.fpsRow(),w.names.fpsNextRow()}) Reflect.field(requester,name);
        Reflect.field(w.type(w.names.fpsOwner()),"a");Reflect.field(w.type(w.names.fpsOwner()),"d");
        Reflect.method(w.type(w.names.fpsOwner()),w.names.fpsRelease(),long.class,requester);
        Reflect.method(w.type(w.names.fpsOwner()),w.names.fpsRefresh(),int.class);
        w.hook(w.names.fpsOwner(),w.names.fpsEnter(),void.class,chain->{
            owner=chain.getThisObject();int pid=(Integer)chain.getArg(2);String pkg=(String)chain.getArg(3);
            // Foreground events are authoritative even when no thermal row exists for this package.
            if((Boolean)chain.getArg(1)) w.sessions.enter(pid,pkg);else w.sessions.exit(pid,pkg);
            w.foreground((Integer)chain.getArg(0),(Boolean)chain.getArg(1),pid,pkg);
            return chain.proceed();
        },int.class,boolean.class,int.class,String.class);
        w.hook(w.names.fpsOwner(),w.names.fpsDeath(),void.class,chain->{
            w.sessions.exit((Integer)chain.getArg(1),(String)chain.getArg(2));
            w.death((Integer)chain.getArg(0),(Integer)chain.getArg(1),(String)chain.getArg(2));return chain.proceed();
        },int.class,int.class,String.class);
        w.hook(w.names.fpsOwner(),w.names.fpsApply(),w.os5?void.class:long.class,chain->{
            Object request=chain.getArg(0);Object monitor=chain.getThisObject();
            synchronized(monitor) {
                if(selected(monitor,request)) {
                    release(monitor,request);
                    w.log("stage=thermal_suppressed branch=fps pkg="+Reflect.get(request,"a")+" pid="+Reflect.get(request,"b")
                            +" actual="+Reflect.get(request,"d")+" row="+Reflect.get(request,w.names.fpsRow())+" revision="+w.revision());
                    return w.os5?null:Long.valueOf(-1);
                }
                return chain.proceed();
            }
        },requester);
        w.hook(w.names.fpsOwner(),"handleMessage",boolean.class,chain->{
            Object monitor=chain.getThisObject();owner=monitor;
            synchronized(monitor) {
                Object request=requests(monitor).get(((Message)chain.getArg(0)).what);
                // Release before OEM overwrites the handle; OS6 has a 30-FPS branch that retains the old one.
                if(request!=null && selected(monitor,request)) release(monitor,request);
                Object result=chain.proceed();
                request=requests(monitor).get(((Message)chain.getArg(0)).what);
                if(request!=null && selected(monitor,request)) {
                    release(monitor,request);
                    // Do not report a thermal row as applied after its request was suppressed.
                    Reflect.set(request,w.names.fpsRow(),null);Reflect.set(request,w.names.fpsNextRow(),null);
                }
                return result;
            }
        },Message.class);
    }
    private SparseArray<?> requests(Object monitor) throws Throwable { return (SparseArray<?>)Reflect.get(monitor,"a"); }
    private boolean selected(Object monitor,Object request) throws Throwable {
        int pid=(Integer)Reflect.get(request,"b");String pkg=(String)Reflect.get(request,"a");
        return requests(monitor).get(pid)==request && w.allowed(pid,pkg);
    }
    private void release(Object monitor,Object request) throws Throwable {
        long handle=ThermalRequestState.release(monitor,request,w.names.fpsHandle(),w.names.fpsRelease());
        if(handle>0) {
            w.log("stage=release_existing_limit pkg="+Reflect.get(request,"a")+" pid="+Reflect.get(request,"b")+" handle="+handle);
        }
    }
    void reconcile(BackendPolicy old,BackendPolicy next) {
        Object monitor=owner;if(monitor==null) return;
        try {
            ((Handler)Reflect.get(monitor,"d")).post(()->{
                synchronized(monitor) { try {
                    SparseArray<?> map=requests(monitor);
                    for(int i=0;i<map.size();i++) {
                        Object request=map.valueAt(i);String pkg=(String)Reflect.get(request,"a");int pid=(Integer)Reflect.get(request,"b");
                        if(!w.changed(old,next,pkg)) continue;
                        if(selected(monitor,request)) release(monitor,request);
                        ThermalRequestState.invalidateSelection(request,w.names.fpsRow(),w.names.fpsNextRow());
                        Reflect.call(monitor,w.names.fpsRefresh(),pid);
                        w.log("stage="+(w.allowed(pid,pkg)?"thermal_suppressed":"restore_stock")+" branch=fps pkg="+pkg+" pid="+pid+" actual="+Reflect.get(request,"d"));
                    }
                } catch(Throwable error) { w.error("fps_reconcile",error); } }
            });
        } catch(Throwable error) { w.error("fps_schedule",error); }
    }
}
