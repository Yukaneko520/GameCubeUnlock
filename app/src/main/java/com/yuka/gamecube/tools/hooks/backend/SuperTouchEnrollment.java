package com.yuka.gamecube.tools.hooks.backend;

import android.util.SparseArray;
import com.yuka.gamecube.tools.compat.SuperControlLayout;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.lang.reflect.Method;

/** Opens game admission only inside the OEM monitor callback, never for other GameWatch consumers. */
final class SuperTouchEnrollment {
    private static final class Admission {
        final String pkg;final boolean allowed;boolean extended;
        Admission(String pkg,boolean allowed) { this.pkg=pkg;this.allowed=allowed; }
    }
    private final SuperTouchHooks owner;
    private final SuperControlRuntime r;
    private final SuperControlLayout names;
    private final ThreadLocal<Admission> admitting=new ThreadLocal<>();
    private final SuperTouchSessions sessions=new SuperTouchSessions();
    SuperTouchEnrollment(SuperTouchHooks owner,SuperControlRuntime r,SuperControlLayout names) {
        this.owner=owner;this.r=r;this.names=names;
    }
    void install() throws Throwable {
        r.hook(names.gameList(),names.gameMembership(),boolean.class,chain->{
            Object stock=chain.proceed();Admission admission=admitting.get();
            if(!Boolean.TRUE.equals(stock)&&admission!=null&&admission.allowed&&admission.pkg.equals(chain.getArg(0))) {
                admission.extended=true;return true;
            }
            return stock;
        },String.class);
        Method foreground=r.method(names.monitor(),names.foreground(),int.class,boolean.class,int.class,String.class);
        r.hook(foreground,chain->{
            Object monitor=chain.getThisObject();
            synchronized(monitor) { synchronized(r.gate) {
                int uid=(Integer)chain.getArg(0),pid=(Integer)chain.getArg(2);
                boolean entered=(Boolean)chain.getArg(1);String pkg=(String)chain.getArg(3);
                sessions.foreground(pkg,pid,uid,entered);
                Object before=rows(monitor).get(pid);
                boolean owned=sessions.owns(pkg,pid,before);
                var check=entered?owner.check(pkg,pid,true,uid):null;
                boolean allowed=owned&&!entered || entered&&check.selected();
                Admission previous=admitting.get(),admission=new Admission(pkg,allowed);
                admitting.set(admission);
                try {
                    Object result=chain.proceed();Object after=rows(monitor).get(pid);
                    if(!entered&&sessions.owns(pkg,pid,after)) {
                        // A newly changed OEM blacklist must not strand an extension's existing request.
                        Reflect.call(monitor,names.remove(),pid);after=rows(monitor).get(pid);
                        r.log("superTouch",pkg,"enrollment_released","pid="+pid+" reason=foreground_exit cleanup=oem_remove");
                    }
                    if(entered&&admission.extended&&before==null&&after!=null) sessions.claim(pkg,pid,after);
                    if(entered&&after!=null&&pkg!=null&&r.policy.verified&&Integer.valueOf(uid).equals(r.policy.installedUid(pkg)))
                        sessions.observe(pkg,pid,uid,after);
                    if(r.policy.control(pkg,"superTouch")||owned) r.log("superTouch",pkg,"enrollment",
                            "pid="+pid+" event_uid="+uid+" entered="+entered+" admission="+allowed+
                            " membership_extended="+admission.extended+" row_before="+(before!=null)+" row_after="+(after!=null)+
                            " owned="+sessions.owns(pkg,pid,after)+" check="+check);
                    return result;
                } finally { if(previous==null)admitting.remove();else admitting.set(previous); }
            } }
        });
        r.hook(names.monitor(),names.remove(),r.type(names.monitor()+"$b"),chain->{
            synchronized(chain.getThisObject()) {
                Object result=chain.proceed();sessions.removed((Integer)chain.getArg(0));return result;
            }
        },int.class);
        r.hook(names.monitor(),names.refresh(),void.class,chain->{
            synchronized(chain.getThisObject()) { reconcile(chain.getThisObject(),"oem_refresh");return chain.proceed(); }
        },String.class);
        r.hook(names.monitor(),"onProcessDied",void.class,chain->{
            synchronized(chain.getThisObject()) {sessions.died((Integer)chain.getArg(1));return chain.proceed();}
        },int.class,int.class,String.class,String.class);
        var deathForward=r.method(names.monitor(),names.deathForward(),int.class,int.class,String.class);
        r.log("superTouch","","death_deopt","method="+deathForward+" result="+r.module.deoptimize(deathForward));
        r.log("superTouch","","enrollment_deopt","method="+foreground+" result="+r.module.deoptimize(foreground));
    }
    void reconcile(Object monitor,String reason) {
        synchronized(monitor) { synchronized(r.gate) { try {
            for(int pid:sessions.pids()) {
                Object row=rows(monitor).get(pid);
                if(row==null) {sessions.removed(pid);continue;}
                String pkg=(String)Reflect.get(row,"a");
                if(!sessions.owns(pkg,pid,row)) {sessions.disown(pid);continue;}
                if(!owner.check(pkg,pid,true,sessions.uid(pkg,pid,row)).selected()) {
                    // Original removal releases only the request attached to this exact owned record.
                    Reflect.call(monitor,names.remove(),pid);
                    r.log("superTouch",pkg,"enrollment_released","pid="+pid+" reason="+reason+" cleanup=oem_remove");
                }
            }
            var current=sessions.foreground();
            if(current==null||rows(monitor).get(current.pid())!=null) return;
            if(!owner.check(current.pkg(),current.pid(),true,current.uid()).selected()) return;
            // Replay the actual latest callback; do not fabricate a foreground package or private request.
            Reflect.call(monitor,names.foreground(),current.uid(),true,current.pid(),current.pkg());
            r.log("superTouch",current.pkg(),"enrollment_seed","pid="+current.pid()+" reason="+reason);
        } catch(Throwable failure) {r.error("touch_enrollment_reconcile",failure);} } }
    }
    Integer uid(String pkg,int pid,Object row) { return sessions.uid(pkg,pid,row); }
    private SparseArray<?> rows(Object monitor) throws Exception { return (SparseArray<?>)Reflect.get(monitor,"b"); }
}
