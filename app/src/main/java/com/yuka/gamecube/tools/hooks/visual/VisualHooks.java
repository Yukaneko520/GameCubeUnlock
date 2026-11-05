package com.yuka.gamecube.tools.hooks.visual;

import android.content.Context;
import android.widget.Toast;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import io.github.libxposed.api.XposedInterface;

/** Per-game admission and command overrides; original UI and thermal decisions remain owners. */
public final class VisualHooks implements ConfigListener {
    private final HookRuntime r;
    private final VisualAccess a;
    private final VisualNative nativeIo;
    private final ThreadLocal<Ticket> dispatch=new ThreadLocal<>();
    private Object controller,bean;
    private String owner="",refresh="";
    private long failures;
    private static final class Ticket {
        final ConfigSnapshot config;final String pkg;final Object bean;final int pid;
        boolean enteredStrategy,emitted,failed;
        String failure="unknown";
        Ticket(ConfigSnapshot config,String pkg,Object bean,int pid) { this.config=config;this.pkg=pkg;this.bean=bean;this.pid=pid; }
    }
    private VisualHooks(HookRuntime r) throws Throwable { this.r=r;a=new VisualAccess(r);nativeIo=new VisualNative(r); }
    public static void install(HookRuntime r) throws Throwable { new VisualHooks(r).bind(); }
    private boolean selected(String pkg) { return r.snapshot().allows(pkg,"visual"); }
    private boolean eligible(String pkg) throws Throwable {
        return selected(pkg) && pkg.equals(r.currentPackage())
                && VisualForeground.primaryUid(r.loader,pkg)>=10000 && a.supported();
    }
    private boolean valid(Ticket t) {
        return r.snapshot()==t.config && t.pkg.equals(r.currentPackage()) && t.config.allows(t.pkg,"visual") && t.pid==pid();
    }
    private int pid() {
        try { var o=r.adapter.owner();return (Integer)Reflect.call(Reflect.staticCall(r.type(o.type()),o.instance()),o.pid()); }
        catch(Throwable unavailable) { return -1; }
    }
    private void bind() throws Throwable {
        r.hook(a.n.controller(),a.n.highFps(),boolean.class,chain->{
            String pkg=(String)chain.getArg(0);Object stock=chain.proceed();
            if(eligible(pkg)) {
                boolean blocked=VisualOverlay.blocksGeneral(true,r.snapshot().rule(pkg).visual,(Boolean)stock,r.snapshot().demonstrates(pkg));
                r.event("visual",pkg,"filter_admission","stock_high_fps_block="+stock+" general_filter_blocked="+blocked);
                return blocked;
            }
            return stock;
        },String.class);
        r.hook(a.n.controller(),a.n.open(),void.class,this::open,a.openArgs());
        Class<?>[] closeArgs=a.n.sdk()==36?new Class<?>[]{Context.class,a.bean,String.class,boolean.class}
                :new Class<?>[]{Context.class,a.bean,String.class};
        r.hook(a.n.controller(),a.n.close(),void.class,chain->{
            try { return chain.proceed(); }
            finally { if(owner.equals(chain.getArg(2))) clear(false); }
        },closeArgs);
        r.hook(a.n.controller(),a.n.foreground(),void.class,chain->{
            if(!(Boolean)chain.getArg(0) || !owner.equals(chain.getArg(1))) clear(true);
            return chain.proceed();
        },boolean.class,String.class);
        r.hook(a.n.controller(),a.n.destroy(),void.class,chain->{
            try { clear(true); } catch(Throwable failure) { r.error("visual_destroy_cleanup",failure); }
            return chain.proceed();
        });
        r.hook(a.n.strategy(),"f",void.class,chain->{
            Ticket t=dispatch.get();if(t==null || !valid(t)) return chain.proceed();
            t.enteredStrategy=true;
            var tuning=t.config.rule(t.pkg).visual;
            if(tuning.route()==1) { a.enabled(true);t.emitted=true;return null; }
            return chain.proceed();
        },a.openArgs());
        r.hook(a.n.helper(),a.n.writer(),boolean.class,chain->{
            Ticket t=dispatch.get();if(t==null || !valid(t)) return chain.proceed();
            int type=(Integer)chain.getArg(1);int[] values=(int[])chain.getArg(2);
            if(values==null || !VisualCommands.positive(type,values)) return chain.proceed();
            return emit(t,new VisualCommands.Command(type,values));
        },Context.class,int.class,int[].class);
        if(a.n.light()!=null) for(String gate:new String[]{a.n.light(),a.n.night()})
            r.hook(r.adapter.commonType(),gate,boolean.class,chain->{
                Object stock=chain.proceed();String pkg=r.currentPackage();
                return Boolean.TRUE.equals(stock) || (selected(pkg) && VisualForeground.primaryUid(r.loader,pkg)>=10000 && a.supported());
            });
        // Original one-line support checks and strategy calls may be AOT-inlined into these paths.
        for(var method:new java.lang.reflect.Method[]{r.method(a.n.controller(),a.n.open(),a.openArgs()),
                r.method(a.n.strategy(),"f",a.openArgs()),a.foreground,
                r.method(a.n.sdk()==34?"f8.a":a.n.sdk()==35?"i8.a":"l9.a","c",a.openArgs())})
            r.log("feature=visual stage=deopt method="+method+" result="+r.module.deoptimize(method));
        String view="com.vivo.gameassistant.gamefilter.widget.GameFilterSettingView";
        Class<?> button=r.type("com.originui.widget.components.switches.VMoveBoolButton");
        r.method(view,"setAllLight",boolean.class);
        r.hook(view,"g",void.class,chain->{
            long before=failures;Object result=chain.proceed();
            if(failures!=before) Reflect.call(chain.getThisObject(),"setAllLight",false);
            return result;
        },button,boolean.class);
        r.module.deoptimize(r.method(view,a.n.sdk()==34?"j":a.n.sdk()==35?"r":"p"));
        if(a.n.sdk()==34) r.module.deoptimize(r.method(view,"k",boolean.class,android.view.View.class));
        else r.module.deoptimize(r.method(a.n.sdk()==35?"h8.f":"com.vivo.gameassistant.gamefilter.b","b"));
        r.addListener(this);
        new VisualOverlay(r,a,this::eligible).bind();
        r.log("feature=visual stage=bindings sdk="+a.n.sdk()+" controller="+a.n.controller()+" backend=iris");
    }
    private Object open(XposedInterface.Chain chain) throws Throwable {
        String pkg=(String)chain.getArg(2);Object value=chain.getArg(1);
        if(value==null || !selected(pkg) || !pkg.equals(r.currentPackage()) || !a.supported()) return chain.proceed();
        if(!a.quality(value) && !a.filter(value)) return chain.proceed();
        int uid=VisualForeground.primaryUid(r.loader,pkg),pid=pid();
        if(uid<10000 || pid<=0) {
            r.event("visual",pkg,"session_declined","reason=foreground_identity_unavailable uid="+uid+" pid="+pid);
            return chain.proceed();
        }
        if(!owner.isEmpty() && !owner.equals(pkg)) clear(true);
        controller=chain.getThisObject();bean=value;owner=pkg;
        Ticket t=new Ticket(r.snapshot(),pkg,value,pid);
        r.event("visual",pkg,"session","uid="+uid+" pid="+pid+" tuning="+t.config.rule(pkg).visual);
        try { nativeIo.begin(pkg,()->valid(t),()->pkg.equals(r.currentPackage())
                && r.snapshot().allows(pkg,"visual") && t.pid==pid()); }
        catch(Throwable failure) { r.error("visual_session_start",failure);clear(false);return chain.proceed(); }
        Ticket before=dispatch.get();dispatch.set(t);
        Object result;
        try { result=chain.proceed(); }
        finally { dispatch.set(before); }
        // Unknown games can lack a tailored command, but still have a real generic SDR2HDR mode.
        if(valid(t) && t.enteredStrategy && !t.emitted && a.quality(value) && t.config.rule(pkg).visual.route()==0
                && android.provider.Settings.System.getInt(r.context().getContentResolver(),"gamecube_hawkeye_effective",0)==1)
            emit(t,new VisualCommands.Command(267,new int[]{3,3}));
        if(t.failed && valid(t)) {
            failures++;
            try {
                a.close(controller,value,pkg);
                Reflect.call(value,"setEnableVisualEnhancement",false);Reflect.call(value,"setEnableUniversalFilter",false);
                Toast.makeText(r.context(),"Image enhancement failed: "+t.failure,Toast.LENGTH_LONG).show();
            } finally { clear(false); }
        }
        return result;
    }
    private boolean emit(Ticket t,VisualCommands.Command command) throws Throwable {
        t.emitted=true;
        try {
            var result=nativeIo.apply(command);t.failed|=result.state()==VisualEngine.State.FAILED;
            if(result.state()==VisualEngine.State.FAILED) t.failure=result.stage()+" rc="+result.code();
            return !t.failed;
        } catch(Throwable failure) {
            t.failed=true;t.failure=failure.getClass().getSimpleName();r.error("visual_dispatch",failure);return false;
        }
    }
    private void clear(boolean close) throws Throwable {
        try { if(close && controller!=null && bean!=null && !owner.isEmpty() && owner.equals(r.currentPackage()))
            a.close(controller,bean,owner); }
        finally { nativeIo.release();controller=null;bean=null;owner=""; }
    }
    @Override public void beforeChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        if(!owner.isEmpty() && (!old.rule(owner).visual.equals(next.rule(owner).visual)
                || old.enabled!=next.enabled || old.rule(owner).enabled!=next.rule(owner).enabled)) {
            refresh=owner;Object previous=controller;clear(true);controller=previous;
        }
    }
    @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        String pkg=refresh;refresh="";
        if(controller!=null && !pkg.isEmpty() && pkg.equals(r.currentPackage())) {
            Object previous=controller;controller=null;a.foreground.invoke(previous,true,pkg);
        }
    }
}
