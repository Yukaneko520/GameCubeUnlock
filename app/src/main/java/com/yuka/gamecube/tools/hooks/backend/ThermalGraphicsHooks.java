package com.yuka.gamecube.tools.hooks.backend;

import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import com.yuka.gamecube.tools.hooks.Reflect;
import com.yuka.gamecube.tools.hooks.thermal.ThermalPending;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Re-read user commands through OEM observers; never invent a cooled temperature or restore an old ON command. */
final class ThermalGraphicsHooks {
    private final ThermalWatchHooks w;
    private final Map<Object,Boolean> owners=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object,ThermalPending> delayed=Collections.synchronizedMap(new IdentityHashMap<>());
    ThermalGraphicsHooks(ThermalWatchHooks w) { this.w=w; }
    void install() throws Throwable {
        Class<?> request=w.type(w.names.memc()+"$c");
        for(String field:new String[]{"a","b","c","d","e"}) Reflect.field(request,field);
        for(String field:new String[]{w.names.memcCurrent(),w.names.memcBase(),w.names.memcFlag(),"c"}) Reflect.field(w.type(w.names.memc()),field);
        Reflect.method(w.type(w.names.memc()+"$a"),"onChange",boolean.class,android.net.Uri.class);
        Reflect.method(w.type(w.names.memc()+"$a"),"b",w.os5?new Class<?>[]{String.class,android.net.Uri.class,String.class,int.class}
                :new Class<?>[]{String.class,android.net.Uri.class,String.class});
        if(!w.os5) { Reflect.method(w.type(w.names.memc()),"R",String.class);Reflect.field(w.type(w.names.memc()),"f"); }
        w.hook(w.names.memc(),w.names.memcHot(),boolean.class,chain->{
            Object owner=chain.getThisObject();owners.put(owner,false);
            Object current=Reflect.get(owner,w.names.memcCurrent());
            int pid=current==null?0:(Integer)Reflect.get(current,"c");var session=w.sessions.get(pid);
            if(session!=null && w.allowed(pid,session.pkg)) {
                if(current==Reflect.get(owner,w.names.memcBase()))
                    delayed.put(current,new ThermalPending(owner,session,w.revision(),true));
                Reflect.set(owner,w.names.memcFlag(),false);
                w.log("stage=thermal_suppressed branch=watch_memc pkg="+session.pkg+" pid="+pid+" actual="+chain.getArg(0)
                        +" requested="+tuple(Reflect.get(owner,w.names.memcBase()))+" effective="+tuple(current)+" revision="+w.revision());
                return false;
            }
            Object before=current;Object result=chain.proceed();Object after=Reflect.get(owner,w.names.memcCurrent());
            if(Boolean.TRUE.equals(result) && after!=before && session!=null) {
                delayed.put(after,new ThermalPending(owner,session,w.revision(),false));
            }
            return result;
        },double.class,boolean.class);
        w.hook(w.names.memc(),w.names.memcDispatch(),void.class,chain->{
            Object requestArg=chain.getArg(0);
            if(blocked(requestArg)) return null;
            return chain.proceed();
        },request,boolean.class);
        // Guard both the synthetic bridge and native command body: OEM may insert another 300-ms delay.
        for(String render:w.os5?new String[]{"h0","p0"}:new String[]{"l0","u0"})
            w.hook(w.names.memc(),render,void.class,chain->{
                Object requestArg=chain.getArg(0);
                if(blocked(requestArg)) { delayed.remove(requestArg);return null; }
                try { return chain.proceed(); } finally { delayed.remove(requestArg); }
            },request,boolean.class);
        w.hook(w.names.memc(),w.os5?"n0":"s0",void.class,chain->{
            Object result=chain.proceed();Object owner=chain.getThisObject();
            // OEM has just removed all of this owner's tokenized render callbacks.
            synchronized(delayed) { delayed.entrySet().removeIf(e->e.getValue().owner==owner); }
            return result;
        });
        // Q3 is dormant on PD2352's verified display-IC baseline; bind its thermal-only branches too.
        for(String field:new String[]{w.names.q3Pid(),w.names.q3Package(),"c"}) Reflect.field(w.type(w.names.q3()),field);
        for(String method:new String[]{w.names.q3Memc(),w.names.q3Sr()}) {
            w.hook(w.names.q3(),method,boolean.class,chain->{
                Object owner=chain.getThisObject();owners.put(owner,true);
                int pid=(Integer)Reflect.get(owner,w.names.q3Pid());String pkg=(String)Reflect.get(owner,w.names.q3Package());
                if(w.allowed(pid,pkg)) {
                    w.log("stage=thermal_suppressed branch=q3 decision="+method+" pkg="+pkg+" pid="+pid+" actual="+chain.getArg(0));
                    return false;
                }
                return chain.proceed();
            },double.class,boolean.class);
        }
        Reflect.method(w.type(w.names.background()),"a");
    }
    private boolean blocked(Object request) throws Throwable {
        ThermalPending pending=delayed.get(request);
        if(pending==null) return false;
        boolean skip=pending.blocked(w.sessions,w.allowed(pending.session.pid,pending.session.pkg),request,
                Reflect.get(pending.owner,w.names.memcCurrent()));
        if(skip) w.log("stage=stale_session_skipped branch=watch_memc pkg="+pending.session.pkg+" pid="+pending.session.pid
                +" queuedRevision="+pending.revision+" revision="+w.revision());
        return skip;
    }
    private String tuple(Object request) throws Throwable {
        if(request==null) return "none";
        return Reflect.get(request,"a")+":"+Reflect.get(request,"b")+":"+Reflect.get(request,"c")+":"+Reflect.get(request,"d")+":"+Reflect.get(request,"e");
    }
    void reconcile(BackendPolicy old,BackendPolicy next) {
        // OFF/default never changes stock queued work. Only an actual option transition invalidates it.
        synchronized(delayed) {
            for(var pending:delayed.values()) if(w.changed(old,next,pending.session.pkg)) pending.invalidate();
        }
        try {
            ((Handler)Reflect.staticCall(w.type(w.names.background()),"a")).post(()->{
                Object[] active;synchronized(owners) { active=owners.keySet().toArray(); }
                for(Object owner:active) try {
                    boolean q3=owners.get(owner);
                    Object request=q3?null:Reflect.get(owner,w.names.memcCurrent());
                    int pid=q3?(Integer)Reflect.get(owner,w.names.q3Pid()):request==null?0:(Integer)Reflect.get(request,"c");
                    var session=w.sessions.get(pid);
                    if(session==null || !w.changed(old,next,session.pkg)) continue;
                    if(q3 && !session.pkg.equals(Reflect.get(owner,w.names.q3Package()))) continue;
                    ContentObserver observer=(ContentObserver)Reflect.get(owner,"c");
                    if(observer==null) continue;
                    if(q3) {
                        observer.onChange(false,Settings.System.getUriFor("gamecube_frame_interpolation"));
                        observer.onChange(false,Settings.System.getUriFor("gamecube_frame_interpolation_for_sr"));
                        observer.onChange(false,Settings.Secure.getUriFor("gamecube_super_resolution"));
                    } else replay(owner,observer);
                    w.log("stage="+(w.allowed(pid,session.pkg)?"restore_requested_mode":"restore_stock")
                            +" branch="+(q3?"q3":"watch_memc")+" pkg="+session.pkg+" pid="+pid+" source=current_oem_settings");
                } catch(Throwable error) { w.error("graphics_reconcile",error); }
            });
        } catch(Throwable error) { w.error("graphics_schedule",error); }
    }
    private void replay(Object owner,Object observer) throws Throwable {
        var resolver=w.context.getContentResolver();
        String frame=Settings.System.getString(resolver,"gamecube_frame_interpolation");
        String sr=Settings.System.getString(resolver,"gamecube_frame_interpolation_for_sr");
        int plus=Settings.System.getInt(resolver,"game_plus_mode_key",-1);
        if(!w.os5) { frame=(String)Reflect.call(owner,"R",frame);Reflect.set(owner,"f",plus); }
        if(frame==null && sr==null) return;
        // Already on the OEM owner handler: avoid posting an extra callback with an old Settings snapshot.
        for(String key:new String[]{"gamecube_frame_interpolation","gamecube_frame_interpolation_for_sr"}) {
            var uri=Settings.System.getUriFor(key);
            if(w.os5) Reflect.call(observer,"b",frame,uri,sr,plus);else Reflect.call(observer,"b",frame,uri,sr);
        }
    }
}
