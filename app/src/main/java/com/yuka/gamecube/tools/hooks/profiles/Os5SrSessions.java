package com.yuka.gamecube.tools.hooks.profiles;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.lang.ref.WeakReference;
import java.util.*;

/** OS5 owns w:strategy, j/m:disposables, t:observer; these are not the OS6 field layout. */
final class Os5SrSessions {
    final HookRuntime r;
    final ProfileStore store;
    final ThreadLocal<ProfilePolicy.Ticket> applying=new ThreadLocal<>(),cleaning=new ThreadLocal<>();
    final ThreadLocal<Object> showing=new ThreadLocal<>();
    private final Map<Object,ProfilePolicy.Ticket> tickets=new WeakHashMap<>();
    private final Map<Object,Object> originalStrategies=new WeakHashMap<>();
    private final Map<Object,Boolean> active=new WeakHashMap<>();
    private final Map<Object,List<WeakReference<Object>>> dialogs=new WeakHashMap<>();
    private final Set<String> touched=new HashSet<>();
    private WeakReference<Object> current=new WeakReference<>(null);
    private final MainThreadGate main;
    Os5SrSessions(HookRuntime runtime,ProfileStore profiles) {
        r=runtime;store=profiles;Handler handler=new Handler(Looper.getMainLooper());
        main=new MainThreadGate(()->Looper.myLooper()==Looper.getMainLooper(),handler::post,e->r.error("sr_os5_main",e));
        store.beforeRemoval=profile->{
            for(Object controller:tickets.keySet().toArray()) if(tickets.get(controller).profile==profile) cleanup(controller);
        };
    }
    private boolean ready() { return main.check(this::afterChange); }
    int pid() throws Throwable {
        var owner=r.adapter.owner();Object instance=ProfileAccess.call(r.type(owner.type()),owner.instance());
        return (Integer)r.original(r.method(owner.type(),owner.pid()),instance);
    }
    boolean valid(ProfilePolicy.Ticket ticket) throws Throwable {
        return ticket!=null && ticket.matches(r.currentPackage(),pid(),r.snapshot().revision,store.donated(ticket.pkg));
    }
    boolean owns(Object c) { return ready() && tickets.containsKey(c); }
    boolean touched(String pkg) { return ready() && touched.contains(pkg); }
    boolean active(Object c) { return ready() && active.containsKey(c); }
    void mark(Object c,boolean enabled,boolean extreme) {
        if(!ready()) return;if(enabled) active.put(c,extreme);else active.remove(c);
    }
    ProfilePolicy.Ticket prepare(Object c,String pkg) throws Throwable {
        if(!ready()) return null;current=new WeakReference<>(c);store.reconcile();
        Object profile=store.donated(pkg);int pid=pid();var old=tickets.get(c);
        if(old!=null && (!old.pkg.equals(pkg) || old.pid!=pid || old.profile!=profile)) cleanup(c);
        if(profile==null || !pkg.equals(r.currentPackage()) || pid<=0) return null;
        if(old!=null && old.matches(pkg,pid,r.snapshot().revision,profile)) return old;
        Object strategy=ProfileAccess.get(c,"w");
        if(strategy==null || strategy.getClass()!=r.type(Os5SrHooks.STRATEGY)) {
            if(strategy!=null) originalStrategies.putIfAbsent(c,strategy);
            strategy=r.type(Os5SrHooks.STRATEGY).getDeclaredConstructor().newInstance();ProfileAccess.set(c,"w",strategy);
        }
        ProfileAccess.call(strategy,"n",profile);ProfileAccess.set(c,"k",pkg);
        var ticket=new ProfilePolicy.Ticket(pkg,pid,r.snapshot().revision,profile);tickets.put(c,ticket);touched.add(pkg);
        ProfileAccess.call(c,"A0");ProfileAccess.call(c,"z0");
        r.event("sr",pkg,"bound","baseline=os5 pid="+pid+" "+NativeProfileResolver.describe(profile,false));return ticket;
    }
    void capture(Object shown) {
        Object c=showing.get();if(c==null || shown==null || !owns(c)) return;
        dialogs.computeIfAbsent(c,k->new ArrayList<>()).add(new WeakReference<>(shown));
    }
    private void cancel(Object c) throws Throwable {
        ProfileAccess.dispose(c,"j");ProfileAccess.dispose(c,"m");
        var shown=dialogs.remove(c);if(shown!=null) for(var ref:shown) {Object dialog=ref.get();if(dialog!=null) ProfileAccess.call(dialog,"dismiss");}
        ProfileAccess.set(c,"c",null);
    }
    void cleanup(Object c) throws Throwable {
        if(!ready()) return;var old=tickets.get(c);if(old==null) return;
        cancel(c);Object strategy=ProfileAccess.get(c,"w");ProfileAccess.call(strategy,"n",old.profile);ProfileAccess.set(c,"k",old.pkg);
        Object previous=store.retainForCleanup(old.profile);cleaning.set(old);
        try {
            Context context=(Context)ProfileAccess.get(c,"a");
            boolean sameForeground=old.pkg.equals(r.currentPackage()) && old.pid==pid();
            boolean supported=Boolean.TRUE.equals(r.original(r.method(store.utils(),store.srSupport(),Context.class,String.class),null,context,old.pkg));
            if(sameForeground) r.original(r.method(Os5SrHooks.CONTROLLER,"v0",String.class,boolean.class),c,old.pkg,false);
            else r.event("sr",old.pkg,"cleanup_global_off_skipped","baseline=os5 reason=foreground_changed native_lifecycle_owns_old_session=true");
            if(sameForeground && active.containsKey(c)) {
                String setter=Boolean.TRUE.equals(active.get(c))?"V0":"a1";
                r.original(r.method(Os5SrHooks.CONTROLLER,setter,Context.class,String.class,boolean.class,boolean.class),
                        c,context,old.pkg,false,false);
                if(active.containsKey(c)) throw new IllegalStateException("OS5 SR close rejected; retaining profile");
            }
            ProfileAccess.call(c,"i1");if(sameForeground) ProfileAccess.call(c,"k1");
            Object observer=ProfileAccess.get(c,"t");
            if((!supported || !sameForeground) && observer instanceof android.database.ContentObserver value) context.getContentResolver().unregisterContentObserver(value);
            ProfileAccess.set(c,"t",null);ProfileAccess.set(c,"b",false);ProfileAccess.set(c,"l",false);
        } finally { cleaning.remove();store.restoreAfterCleanup(previous); }
        ProfileAccess.call(strategy,"n",(Object)null);
        Object original=originalStrategies.remove(c);if(original!=null) ProfileAccess.set(c,"w",original);
        tickets.remove(c);active.remove(c);
        r.event("sr",old.pkg,"cleanup","baseline=os5 pid="+old.pid+" revision="+old.revision);
    }
    void release(Object c) throws Throwable {cleanup(c);if(current.get()==c)current=new WeakReference<>(null);}
    void beforeChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        if(!ready()) return;
        for(Object c:tickets.keySet().toArray()) {
            var ticket=tickets.get(c);
            if(!next.allows(ticket.pkg,"sr") || !old.rule(ticket.pkg).srDonor.equals(next.rule(ticket.pkg).srDonor)
                    || !old.rule(ticket.pkg).tuning.sameSr(next.rule(ticket.pkg).tuning)) cleanup(c);
        }
    }
    void afterChange() throws Throwable {
        if(!ready()) return;store.reconcile();Object c=current.get();if(c!=null) prepare(c,r.currentPackage());
    }
}
