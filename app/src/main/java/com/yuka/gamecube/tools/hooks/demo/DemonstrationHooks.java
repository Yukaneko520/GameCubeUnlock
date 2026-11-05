package com.yuka.gamecube.tools.hooks.demo;

import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import com.yuka.gamecube.tools.hooks.visual.VisualForeground;
import java.util.HashSet;
import java.util.Set;

/** UI admission only in assistantui; no framework, property, native or saved-game mutation. */
public final class DemonstrationHooks {
    final HookRuntime r;
    private final Set<String> functions=new HashSet<>();
    private final ThreadLocal<Integer> uiDepth=ThreadLocal.withInitial(()->0);
    private DemonstrationHooks(HookRuntime r) { this.r=r; }
    public static void install(HookRuntime r) throws Throwable {
        var demo=new DemonstrationHooks(r);
        r.installFamily("demonstration membership",demo::membership);
        r.installFamily("demonstration catalog",()->DemonstrationCatalog.install(demo));
        r.installFamily("demonstration controls",()->DemonstrationControls.install(demo));
        r.installFamily("demonstration voice mod",()->DemonstrationVoice.install(demo));
        if(r.adapter.baseline().sdk()==36) r.installFamily("demonstration Q chip",()->DemonstrationQChip.install(demo));
        r.installFamily("demonstration capabilities",()->DemonstrationCapabilities.install(demo));
    }
    boolean active(String pkg) {
        return r.snapshot().demonstrates(pkg) && pkg.equals(r.currentPackage())
                && VisualForeground.primaryUid(r.loader,pkg)>=10000;
    }
    boolean active() { return active(r.currentPackage()); }
    boolean inUi() { return uiDepth.get()>0 && active(); }
    Object ui(io.github.libxposed.api.XposedInterface.Chain chain) throws Throwable {
        int before=uiDepth.get();uiDepth.set(before+1);
        try { return chain.proceed(); }
        finally { if(before==0) uiDepth.remove();else uiDepth.set(before); }
    }
    void scopeView(String name) throws Throwable {
        Class<?> type=r.type(name);
        for(var constructor:type.getDeclaredConstructors()) r.hook(constructor,this::ui);
        for(var method:type.getDeclaredMethods())
            if(method.getName().equals("onAttachedToWindow") || method.getName().equals("onClick")) r.hook(method,this::ui);
        r.deoptimize(name);
    }
    void predicate(String owner,String method,boolean value,Class<?>...args) throws Throwable {
        r.hook(owner,method,boolean.class,chain->{
            Object stock=chain.proceed();return inUi()?value:stock;
        },args);
    }
    private void membership() throws Throwable {
        var api=r.adapter.support();var config=r.type(api.config());
        String getter=r.adapter.baseline().sdk()==34?"d":"getFuncName";
        var functionType=r.type("com.vivo.common.supportlist.pojo.ConfiguredFunction");
        for(Object value:functionType.getEnumConstants()) functions.add((String)Reflect.call(value,getter));
        for(String method:new String[]{api.function(),api.membership(),api.scoped()}) {
            boolean scoped=method.equals(api.scoped());
            r.hook(api.evaluator(),method,boolean.class,chain->{
                Object stock=chain.proceed();String pkg=(String)chain.getArg(scoped?0:1);
                if(!active(pkg)) return stock;
                Object source=chain.getArg(scoped?1:2);
                String function=scoped?(source==null?null:(String)Reflect.call(source,"a")):(String)chain.getArg(0);
                if(!functions.contains(function)) return stock;
                boolean allowed=!function.contains("black_list") && !function.equals("game_filter_high_fps_limit");
                r.event("demonstration",pkg,"function_admission","function="+function+" original="+stock+" effective="+allowed+" ui_only=true");
                return allowed;
            },scoped?new Class<?>[]{String.class,config}:new Class<?>[]{String.class,String.class,config});
        }
        r.log("feature=demonstration stage=membership_bound functions="+functions.size()+" backend_policy_unchanged=true");
    }
}
