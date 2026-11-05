package com.yuka.gamecube.tools.hooks.overlay;

import android.provider.Settings;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class MonsterHooks implements ConfigListener {
    private final HookRuntime r;
    private final MonsterSession session=new MonsterSession();
    private final Set<Object> bars=Collections.newSetFromMap(new WeakHashMap<>());
    private final ThreadLocal<Integer> disconnectDepth=ThreadLocal.withInitial(()->0);
    private MonsterAccess a;
    private volatile boolean withdrawing;

    MonsterHooks(HookRuntime runtime) { r=runtime; }
    void install() throws Throwable {
        a=new MonsterAccess(r);
        r.event("monsterPlus","","capability","supported="+a.supported+" admission=per_game_oem_cooler_gate");
        if(!a.supported) return;
        bindHooks();
        lifecycle();
        for(var caller:a.callers)
            r.log("feature=monsterPlus stage=deopt method="+caller+" result="+r.module.deoptimize(caller));
        r.addListener(this);
    }
    private boolean admitted() { return !withdrawing && session.allows(r.snapshot(),a.supported); }
    private void bindHooks() throws Throwable {
        r.hook(a.api.info(),"a",boolean.class,chain-> {
            Object physical=chain.proceed();
            return Boolean.TRUE.equals(physical) || admitted();
        });
        r.hook(MonsterAccess.BAR,a.api.barBind(),void.class,chain-> {
            bars.add(chain.getThisObject());
            boolean stock=(Boolean)chain.getArg(0), enabled=stock || admitted();
            Object result=chain.proceed(new Object[]{enabled});
            r.event("monsterPlus",session.pkg(),"ui_bind","stockConnection="+stock+" admitted="+enabled);
            return result;
        },boolean.class);
        r.hook(a.api.info(),a.infoSetter,void.class,chain-> {
            int depth=disconnectDepth.get();
            boolean guard=Boolean.FALSE.equals(chain.getArg(0)) && admitted();
            if(guard) disconnectDepth.set(depth+1);
            try { return chain.proceed(); }
            finally { if(guard) { if(depth==0) disconnectDepth.remove();else disconnectDepth.set(depth); } }
        },boolean.class);
        r.hook(a.api.helper(),a.api.flag(),void.class,chain-> {
            boolean requested=(Boolean)chain.getArg(0);
            if(MonsterSession.protectDisconnectClear(requested,disconnectDepth.get(),admitted())) {
                r.event("monsterPlus",session.pkg(),"physical_disconnect_retained","virtualAdmission=true");
                return null;
            }
            Object result=chain.proceed();
            trace("oem_flag_"+requested,"requested="+requested);
            return result;
        },boolean.class);
        r.hook(a.api.controller(),a.api.disconnect(),void.class,chain->
                chain.proceed(new Object[]{Boolean.TRUE.equals(chain.getArg(0)) || admitted()}),boolean.class);
        r.hook(a.api.helper(),a.api.shift(),void.class,chain-> {
            Object mode=chain.getArg(0);
            Object result=chain.proceed();
            trace("oem_request_"+mode,"requestedMode="+mode);
            return result;
        },r.type(MonsterAccess.MODE));
    }
    private void lifecycle() throws Throwable {
        r.hook(a.api.controller(),a.api.enter(),void.class,chain-> {
            Object owner=chain.getThisObject();
            session.enter(owner,(String)chain.getArg(0));
            try {
                Object result=chain.proceed();
                refreshBars();trace("foreground","virtualAdmission="+admitted());
                return result;
            } catch(Throwable failure) { session.exit(owner);throw failure; }
        },String.class);
        r.hook(a.api.controller(),a.api.exit(),void.class,chain-> {
            String pkg=session.pkg();
            // Stop virtual admission before OEM background cleanup restores the previous mode.
            session.exit(chain.getThisObject());
            try { return chain.proceed(); }
            finally {
                refreshBars();
                r.event("monsterPlus",pkg,"background","virtualAdmission=false cleanup=oem");
            }
        });
    }
    @Override public void beforeChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        if(!session.allows(old,a.supported) || session.allows(next,a.supported) || a.physical()) return;
        withdrawing=true;
        try {
            Object controller=session.controller();
            if(controller!=null) a.connection(controller,false);
            trace("withdraw","cleanup=oem_disconnect");
        } finally { withdrawing=false; }
    }
    @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        try {
            if(!session.allows(old,a.supported) && session.allows(next,a.supported) && !a.physical()) {
                Object controller=session.controller();
                if(controller!=null) a.connection(controller,true);
                trace("admission_granted","transition=oem_cooler_admission");
            }
        } finally { refreshBars(); }
    }
    private void refreshBars() {
        try {
            boolean physical=a.physical();
            for(Object view:new ArrayList<>(bars)) a.bind(view,physical);
        } catch(Throwable failure) { r.error("monsterPlus_ui_refresh",failure); }
    }
    private void trace(String stage,String detail) {
        try {
            int flag=Settings.System.getInt(r.context().getContentResolver(),"game_plus_mode_key",0);
            Object helper=r.original(a.helper,null);
            Object cached=Reflect.call(helper,"n");
            r.event("monsterPlus",session.pkg(),stage,detail+" physicalConnection="+a.physical()
                    +" virtualAdmission="+admitted()+" settingsFlag="+flag+" cachedFlag="+cached
                    +" performanceVerified=false");
        } catch(Throwable failure) { r.error("monsterPlus_trace_"+stage,failure); }
    }
}
