package com.yuka.gamecube.tools.hooks.profiles;

import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.lang.ref.WeakReference;
import java.util.*;

final class Os5FrameSessions {
    final HookRuntime r;
    final ProfileStore profiles;
    final com.yuka.gamecube.tools.compat.LegacyFrameLayout names;
    final Object writerLock=new Object();
    final ThreadLocal<ProfilePolicy.Ticket> applying=new ThreadLocal<>();
    private final Map<Object,ProfilePolicy.Ticket> tickets=new WeakHashMap<>();
    private final Map<Object,Runnable> pending=new WeakHashMap<>();
    private final Set<String> touched=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final FrameCommandLease lease=new FrameCommandLease();
    private WeakReference<Object> current=new WeakReference<>(null);
    private final MainThreadGate gate;
    Os5FrameSessions(HookRuntime runtime,ProfileStore rows) {
        r=runtime;profiles=rows;names=com.yuka.gamecube.tools.compat.LegacyFrameLayout.forSdk(r.adapter.baseline().sdk());gate=new MainThreadGate(()->Looper.myLooper()==Looper.getMainLooper(),main::post,e->r.error("frame_os5_main",e));
        profiles.beforeRemoval=row->{for(Object c:tickets.keySet().toArray()) if(tickets.get(c).profile==row) cleanup(c);};
    }
    private boolean ready() {return gate.check(this::afterChange);}
    boolean owns(Object c) {return ready() && tickets.containsKey(c);}
    boolean touched(String pkg) {return touched.contains(pkg);}
    int pid() throws Throwable {var a=r.adapter.owner();return (Integer)ProfileAccess.call(ProfileAccess.call(r.type(a.type()),a.instance()),a.pid());}
    boolean valid(ProfilePolicy.Ticket t) throws Throwable {
        return t!=null && t.matches(r.currentPackage(),pid(),r.snapshot().revision,profiles.donated(t.pkg));
    }
    ProfilePolicy.Ticket prepare(Object c) throws Throwable {
        if(!ready()) return null;current=new WeakReference<>(c);
        profiles.reconcile();
        String pkg=r.currentPackage();int pid=pid();Object row=profiles.donated(pkg);var old=tickets.get(c);
        if(old!=null && (!old.pkg.equals(pkg)||old.pid!=pid||old.profile!=row)) cleanup(c);
        if(row==null || pid<=0) return null;
        if(old!=null && old.matches(pkg,pid,r.snapshot().revision,row)) return old;
        var ticket=new ProfilePolicy.Ticket(pkg,pid,r.snapshot().revision,row);tickets.put(c,ticket);touched.add(pkg);
        r.original(r.method(Os5FrameHooks.CONTROLLER,names.bind()),c);
        ProfileAccess.call(c,names.cool());ProfileAccess.call(c,names.hot());
        r.event("frame",pkg,"bound","baseline="+r.adapter.baseline().id()+" cache="+r.adapter.frameProfile().cache()+" pid="+pid+" "+NativeProfileResolver.describe(row,true));
        return ticket;
    }
    void cancel(Object c) throws Throwable {
        Runnable task=pending.remove(c);if(task!=null) main.removeCallbacks(task);ProfileAccess.dispose(c,"d");
    }
    void cleanup(Object c) throws Throwable {
        if(!ready()) return;var old=tickets.get(c);if(old==null) return;
        cancel(c);ProfileAccess.dispose(c,"e");
        Object strategy=ProfileAccess.get(c,names.strategyField());
        if(strategy!=null && strategy.getClass()==r.type(r.adapter.frameProfile().strategy())) ProfileAccess.dispose(strategy,"b");
        String command;
        synchronized(writerLock) {
            command=Settings.System.getString(r.context().getContentResolver(),"gamecube_frame_interpolation");
            if(lease.owns(c,old.profile,old.pid,command)) {
                ProfileAccess.call(c,names.disable());
                String after=Settings.System.getString(r.context().getContentResolver(),"gamecube_frame_interpolation");
                if(command.equals(after)) throw new IllegalStateException("Legacy MEMC OFF did not replace owned command");
            }
        }
        Object close=ProfileAccess.get(r.type(FrameHooks.STATE),"CLOSE"),reason=ProfileAccess.get(r.type(FrameHooks.REASON),"DISABLE");
        ProfileAccess.call(c,names.state(),close,reason);ProfileAccess.call(c,names.view(),close);ProfileAccess.set(c,"h",null);
        tickets.remove(c);r.event("frame",old.pkg,"cleanup","baseline="+r.adapter.baseline().id()+" pid="+old.pid+" observed_command="+command);
    }
    void release(Object c) throws Throwable {cleanup(c);if(current.get()==c) current=new WeakReference<>(null);}
    void observe(boolean enable,int index,int pid) throws Throwable {
        if(Looper.myLooper()!=Looper.getMainLooper()) {lease.clear();return;}
        String command=Settings.System.getString(r.context().getContentResolver(),"gamecube_frame_interpolation");
        if(!enable && command!=null && command.startsWith("1:")) return; // Failed OFF must retain ownership for retry.
        Object c=current.get();var t=applying.get();
        lease.observe(enable&&t!=null?c:null,t==null?null:t.profile,index,pid,command);
        if(t!=null) r.event("frame",t.pkg,"writer","baseline="+r.adapter.baseline().id()+" enabled="+enable+" pid="+pid+" command="+command+" backend_ack=unverified");
    }
    void schedule(Object c,int delay,Object state,Object reason,ProfilePolicy.Ticket t) throws Throwable {
        cancel(c);
        Runnable task=()->{
            pending.remove(c);try {
                if(tickets.get(c)==t && valid(t)) r.original(r.method(Os5FrameHooks.CONTROLLER,names.recover(),r.type(FrameHooks.STATE),
                        r.type(FrameHooks.REASON),Integer.class),c,state,reason,0);
            } catch(Throwable e) {r.error("frame_os5_recovery",e);}
        };
        pending.put(c,task);main.postDelayed(task,java.util.concurrent.TimeUnit.SECONDS.toMillis(Math.max(0L,delay)));
    }
    void beforeChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        if(!ready()) return;
        for(Object c:tickets.keySet().toArray()) {
            var t=tickets.get(c);cancel(c);
            if(!next.allows(t.pkg,"frame")||!old.rule(t.pkg).frameDonor.equals(next.rule(t.pkg).frameDonor)
                    || !old.rule(t.pkg).tuning.sameFrame(next.rule(t.pkg).tuning)) cleanup(c);
        }
    }
    void afterChange() throws Throwable {if(!ready())return;profiles.reconcile();Object c=current.get();if(c!=null)prepare(c);}
}
