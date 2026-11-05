package com.yuka.gamecube.tools.hooks.overlay;

import com.yuka.gamecube.tools.compat.RomAdapter;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import com.yuka.gamecube.tools.hooks.visual.VisualForeground;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.List;

/** Let the original daemon open/close its overlay for selected applications. */
public final class OverlayEnrollment implements ConfigListener {
    private final HookRuntime r;
    private final RomAdapter.Enrollment n;
    private final Method games,instance,master,top,dispatch,current,pid,pairing;
    private final ThreadLocal<String> excluded=new ThreadLocal<>();
    private volatile WeakReference<Object> manager=new WeakReference<>(null);
    private long evaluations;
    private OverlayEnrollment(HookRuntime r) throws Throwable {
        this.r=r;n=r.adapter.enrollment();
        games=r.method(n.data(),n.games());instance=r.method(n.data(),n.instance());master=r.method(n.data(),n.master());
        top=r.method(n.manager(),n.top(),String.class);dispatch=r.method(n.manager(),n.dispatch(),int.class,String.class);
        current=r.method(n.manager(),n.current());pid=r.method(n.manager(),n.pid());
        pairing=n.pairing()==null?null:r.method(n.manager(),n.pairing());
        Reflect.field(r.type(n.manager()),"d");
        r.method("com.vivo.gameassistant.FuncParam",n.parameterPackage());
        r.method("com.vivo.gameassistant.FuncParam",n.parameterPid());
    }
    public static void install(HookRuntime r) throws Throwable { new OverlayEnrollment(r).bind(); }
    @SuppressWarnings("unchecked") private List<String> stock() throws Throwable {
        return (List<String>)r.original(games,r.original(instance,null));
    }
    @SuppressWarnings("unchecked") private void bind() throws Throwable {
        r.hook(n.data(),n.games(),List.class,chain->{
            List<String> original=(List<String>)chain.proceed();
            List<String> result=OverlayAdmission.extend(original,r.snapshot(),excluded.get());
            if(result!=original) r.event("overlay","","runtime_list","stock_count="+(original==null?0:original.size())
                    +" effective_count="+result.size()+" stored_selection_changed=false");
            return result;
        });
        r.hook(n.manager(),n.top(),void.class,chain->{
            manager=new WeakReference<>(chain.getThisObject());return chain.proceed();
        },String.class);
        r.hook(n.manager(),n.dispatch(),void.class,chain->{
            Object owner=chain.getThisObject();manager=new WeakReference<>(owner);evaluations++;
            String pkg=(String)r.original(current,owner),previous=excluded.get();
            int user=(Integer)Reflect.get(owner,"d"),livePid=(Integer)r.original(pid,owner);
            boolean primary=OverlayAdmission.primaryUser(user);
            if(!primary || livePid<=0) excluded.set(pkg);
            try {
                List<String> original=stock();
                boolean nativeMember=original!=null && original.contains(pkg);
                boolean selected=OverlayAdmission.selected(r.snapshot(),pkg) && primary && livePid>0;
                boolean enabled=(Boolean)r.original(master,r.original(instance,null));
                if(selected || nativeMember || r.snapshot().apps.containsKey(pkg))
                    r.event("overlay",pkg,"foreground_route","stock_member="+nativeMember+" selected="+selected
                            +" primary_user="+primary+" ultra_game_mode="+enabled
                            +" effective_member="+(nativeMember||selected)+" pid="+livePid);
                return chain.proceed();
            } finally { excluded.set(previous); }
        },int.class,String.class);
        r.hook(n.manager(),n.handoff(),void.class,chain->{
            Object value=chain.getArg(0),owner=chain.getThisObject();
            if(value==null) return chain.proceed();
            String pkg=(String)Reflect.call(value,n.parameterPackage());
            List<String> original=stock();
            if(original!=null && original.contains(pkg)) return chain.proceed();
            int requested=(Integer)Reflect.call(value,n.parameterPid()),live=(Integer)r.original(pid,owner);
            boolean allowed=OverlayAdmission.handoff(r.snapshot(),pkg,(String)r.original(current,owner),requested,live,
                    (Integer)Reflect.get(owner,"d"),(Boolean)r.original(master,r.original(instance,null)));
            r.event("overlay",pkg,"ui_handoff","allowed="+allowed+" requested_pid="+requested+" live_pid="+live);
            return allowed?chain.proceed():null;
        },r.type("com.vivo.gameassistant.FuncParam"));
        for(Method method:new Method[]{top,dispatch,r.method(n.manager(),n.forward(),String.class),
                r.method(n.manager(),n.startup()),r.method(n.manager(),n.died(),int.class,String.class)})
            r.log("feature=overlay stage=deopt method="+method+" result="+r.module.deoptimize(method));
        // The final UI handoff can be inlined into either original message handler.
        for(Class<?> child:r.type(n.manager()).getDeclaredClasses())
            if(android.os.Handler.class.isAssignableFrom(child))
                r.log("feature=overlay stage=handler_deopt type="+child.getName()+" result="
                        +r.module.deoptimize(Reflect.method(child,"handleMessage",android.os.Message.class)));
        r.addListener(this);
        r.log("feature=overlay stage=bindings sdk="+r.adapter.baseline().sdk()+" data="+n.data()+" games="+n.games());
    }
    @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        Object owner=manager.get();if(owner==null) return;
        String pkg=(String)r.original(current,owner);
        if(OverlayAdmission.selected(old,pkg)==OverlayAdmission.selected(next,pkg)) return;
        List<String> original=stock();
        if(original!=null && original.contains(pkg)) return;
        if(VisualForeground.primaryUid(r.loader,pkg)<10000 || (pairing!=null && (Boolean)r.original(pairing,owner))) return;
        long before=evaluations;
        // The original top callback retains its task/window guards and resolves the live PID.
        Reflect.call(owner,n.top(),pkg);
        if(evaluations==before && pkg.equals(r.original(current,owner))) {
            int livePid=(Integer)r.original(pid,owner);
            if(livePid>0) Reflect.call(owner,n.dispatch(),livePid,pkg);
        }
        r.event("overlay",pkg,"config_reconcile","selected="+OverlayAdmission.selected(next,pkg)+" native_route=true");
    }
}
