package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import com.yuka.gamecube.tools.hooks.HookLog;
import com.yuka.gamecube.tools.hooks.Reflect;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Hawkeye color override inside the original controller; never changes user color preferences. */
public final class SystemVisualHooks {
    private static final String TYPE="com.android.server.display.color.displayenhance.HawkeyeDisplayEnhanceController";
    private final XposedModule module;
    private final ClassLoader loader;
    private SuperControlRuntime r;
    private Object controller;
    private Method activate;
    private final ThreadLocal<Boolean> restoring=ThreadLocal.withInitial(()->false);
    private final VisualColorLease lease=new VisualColorLease();
    private SystemVisualHooks(XposedModule module,ClassLoader loader) { this.module=module;this.loader=loader; }
    public static SystemVisualHooks install(XposedModule module,ClassLoader loader) throws Throwable {
        var hooks=new SystemVisualHooks(module,loader);
        Class<?> type=Class.forName(TYPE,false,loader);
        Class<?> color=Class.forName("com.android.server.display.color.colormanager.VivoColorManagerService",false,loader);
        var ctor=type.getDeclaredConstructor(color,Context.class);
        module.hook(ctor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            Object result=chain.proceed();
            try { hooks.ready(chain.getThisObject(),(Context)chain.getArg(1)); }
            catch(Throwable failure) { HookLog.write(module,"system","feature=visual stage=color_initialization_failed",failure); }
            return result;
        });
        HookLog.write(module,"system","feature=visual stage=await_hawkeye_controller",null);
        return hooks;
    }
    private synchronized void ready(Object owner,Context context) throws Throwable {
        if(r!=null) return;
        var runtime=new SuperControlRuntime(module,loader,context);
        controller=owner;r=runtime;
        try {
            activate=r.method(TYPE,"onDisplayEnhanceHawkeyeActivated",Boolean.class);
            Reflect.field(r.type(TYPE),"mAppObserver");
            Reflect.field(r.type(TYPE),"mGameGamutWhiteList");
            Handler handler=(Handler)Reflect.get(owner,"mHandler");
            if(handler==null) throw new IllegalStateException("Hawkeye owner handler missing");
            bind();
            runtime.start(handler,(old,next)->{
                try {
                    String pkg=current(),previous=lease.packageName();
                    cleanup();
                    if(enabled() && (selected(pkg) || (!previous.isEmpty() && pkg.equals(previous)))) activate.invoke(controller,true);
                } catch(Throwable failure) { r.error("visual_color_config",failure); }
            },"visual");
        } catch(Throwable failure) { runtime.close();r=null;throw failure; }
    }
    private boolean selected(String pkg) {
        android.os.PowerManager power=r.context.getSystemService(android.os.PowerManager.class);
        return power!=null && power.isInteractive() && r.policy.visual(pkg)
                && r.policy.snapshot.rule(pkg).visual.route()==1
                && r.policy.visual(pkg,com.yuka.gamecube.tools.hooks.visual.VisualForeground.primaryUid(loader,pkg));
    }
    private String current() throws Throwable {
        Object observer=Reflect.get(controller,"mAppObserver");
        Object value=Reflect.call(observer,"getForegroundAppActivityObserver");
        return value instanceof String pkg?pkg:"";
    }
    private boolean enabled() {
        return Settings.System.getInt(r.context.getContentResolver(),"gamecube_hawkeye_effective",0)==1;
    }
    private int color() { return Settings.System.getInt(r.context.getContentResolver(),"vivo_display_color_mode",-1); }
    private void bind() throws Throwable {
        r.hook(TYPE,"getPlatformGameSdr2HdrState",boolean.class,chain->{
            Object stock=chain.proceed();String pkg=(String)chain.getArg(1);
            return restoring.get() || selected(pkg) || Boolean.TRUE.equals(stock);
        },int.class,String.class);
        r.hook(TYPE,"getForegroundAppColorMode",int.class,chain->{
            Object stock=chain.proceed();String pkg=(String)chain.getArg(0);
            return selected(pkg)?r.policy.snapshot.rule(pkg).visual.colorMode():stock;
        },String.class);
        r.hook(TYPE,"onDisplayEnhanceHawkeyeActivated",void.class,chain->{
            if(restoring.get()) return chain.proceed();
            if(!r.policy.snapshot.enabled && lease.packageName().isEmpty()) return chain.proceed();
            boolean on=Boolean.TRUE.equals(chain.getArg(0));String pkg=current();
            boolean restore=!on && lease.matches(color());
            restoring.set(restore);
            Object gamut=Reflect.get(controller,"mGameGamutWhiteList");
            boolean swapped=on && selected(pkg) && gamut instanceof java.util.List<?> list && list.contains(pkg);
            if(swapped) {
                var local=new java.util.ArrayList<>((java.util.List<?>)gamut);
                local.removeIf(pkg::equals);
                // A stock gamut override would otherwise replace the user's explicitly selected mode.
                Reflect.set(controller,"mGameGamutWhiteList",local);
            }
            Object result;
            try { result=chain.proceed(); }
            finally { restoring.set(false);if(swapped) Reflect.set(controller,"mGameGamutWhiteList",gamut); }
            if(on && selected(pkg)) {
                int actual=color(),requested=r.policy.snapshot.rule(pkg).visual.colorMode();
                lease.claim(pkg,requested,actual);
                r.log("visual",pkg,"color_result","requested="+requested+" actual="+actual
                        +" matched="+(actual==requested)+" route=oem_hawkeye pixel_effect=unmeasured");
            } else if(!on) lease.clear();
            return result;
        },Boolean.class);
        r.hook(TYPE,"onForegroundPackageChanged",void.class,chain->{
            String pkg=(String)chain.getArg(0);
            try { if(!lease.packageName().isEmpty() && !lease.packageName().equals(pkg)) cleanup(); }
            catch(Throwable failure) { r.error("visual_color_foreground_cleanup",failure); }
            Object result=chain.proceed();
            try { if(enabled() && selected(pkg)) activate.invoke(controller,true); }
            catch(Throwable failure) { r.error("visual_color_foreground_apply",failure); }
            return result;
        },String.class);
        r.hook(TYPE,"onScreenStateChanged",void.class,chain->{
            Object result=chain.proceed();
            try {
                if((Integer)chain.getArg(0)!=2) cleanup();
                else if(enabled() && selected(current())) activate.invoke(controller,true);
            } catch(Throwable failure) { r.error("visual_color_screen",failure); }
            return result;
        },int.class);
        for(Method caller:new Method[]{activate,r.method(TYPE,"onForegroundPackageChanged",String.class)})
            r.log("visual","","color_deopt","method="+caller+" result="+module.deoptimize(caller));
    }
    private void cleanup() throws Throwable {
        if(lease.packageName().isEmpty()) return;
        try {
            if(lease.matches(color())) {
                restoring.set(true);
                r.original(activate,controller,false);
                r.log("visual",lease.packageName(),"color_restored","previous="+lease.mode()+" actual="+color());
            } else r.log("visual",lease.packageName(),"color_restore_skipped","reason=external_color_change");
        } finally { restoring.set(false);lease.clear(); }
    }
}
