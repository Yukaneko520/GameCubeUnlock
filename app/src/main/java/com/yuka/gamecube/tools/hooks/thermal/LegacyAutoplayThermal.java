package com.yuka.gamecube.tools.hooks.thermal;

import android.content.Context;
import android.os.SystemClock;
import com.yuka.gamecube.tools.compat.AutoplayThermalLayout;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Reuse native view/lifecycle methods past the inline heat check; never forge a temperature. */
final class LegacyAutoplayThermal {
    private final HookRuntime r;
    private final ThermalGate gate;
    private final AutoplayThermalLayout n;
    private final Map<Object,ThermalGate.Session> starting=Collections.synchronizedMap(new WeakHashMap<>());
    private LegacyAutoplayThermal(HookRuntime r) { this.r=r;gate=new ThermalGate(r);n=AutoplayThermalLayout.forSdk(r.adapter.baseline().sdk()); }
    static void install(HookRuntime r) throws Throwable { new LegacyAutoplayThermal(r).bind(); }
    private void bind() throws Throwable {
        Class<?> view=r.type(n.view()),callback=r.type(n.view()+"$g");
        var constructor=view.getDeclaredConstructor(Context.class,boolean.class);
        r.method(r.adapter.commonType(),n.legacyPanel(),String.class);
        r.method(n.view(),n.callback(),callback);r.method(n.view(),n.battery(),int.class,int.class);
        r.method(n.view(),n.start());r.method(n.view(),n.close());
        for(String name:new String[]{"a","d","e","f","b"}) Reflect.field(r.type(n.controller()),name);
        Reflect.field(r.type("com.vivo.gameassistant.AssistantUIService"),n.contextField());
        Reflect.field(view,n.packageField());Reflect.field(view,n.pidField());
        for(String task:new String[]{"$h","$i"}) Reflect.field(r.type(n.view()+task),"a");
        r.hook(n.controller(),n.enter(),void.class,chain->{
            Object owner=chain.getThisObject();var session=gate.foreground();
            int temperature=(Integer)Reflect.get(owner,"f"),battery=(Integer)Reflect.get(owner,"d"),plugged=(Integer)Reflect.get(owner,"e");
            if(!ThermalFeaturePolicy.legacyAutoplayEntry(temperature,battery,plugged,gate.valid(session))) return chain.proceed();
            Context context=(Context)Reflect.get(r.type("com.vivo.gameassistant.AssistantUIService"),n.contextField());
            if(context==null) return chain.proceed();
            Object created=null,previous=Reflect.get(owner,"a");
            try {
                Object common=Reflect.staticCall(r.type(r.adapter.commonType()),r.adapter.support().commonInstance());
                boolean legacy=(Boolean)Reflect.call(common,n.legacyPanel(),"PD1824");
                created=constructor.newInstance(context,legacy);starting.put(created,session);
                Reflect.call(created,n.callback(),owner);Reflect.call(created,n.battery(),battery,plugged);
                if(!gate.valid(session)) { Reflect.call(created,n.close());starting.remove(created);return null; }
                Reflect.set(owner,"a",created);Reflect.call(created,n.start());
                Reflect.set(owner,"b",SystemClock.elapsedRealtime());
                gate.decision(session,"autoplay_inline_entry",temperature,"allowed battery="+battery+" plugged="+plugged);
                return null;
            } catch(Throwable failure) {
                if(created!=null) {
                    starting.remove(created);
                    try { Reflect.call(created,n.close()); } catch(Throwable cleanup) { r.error("thermal_autoplay_cleanup",cleanup); }
                }
                Reflect.set(owner,"a",previous);throw failure;
            }
        });
        r.hook(n.view()+"$h","b",void.class,chain->{
            Object owner=Reflect.get(chain.getThisObject(),"a");Integer temperature=(Integer)chain.getArg(0);
            var session=gate.session((String)Reflect.get(owner,n.packageField()),(Integer)Reflect.get(owner,n.pidField()));
            if(temperature!=null && temperature>41 && gate.valid(session)) {
                gate.decision(session,"autoplay_running_heat",temperature,"suppressed");return null;
            }
            return chain.proceed();
        },Integer.class);
        r.hook(n.view()+"$i","a",Boolean.class,chain->{
            Object owner=Reflect.get(chain.getThisObject(),"a");var session=starting.get(owner);
            if(session!=null && (!gate.valid(session) || gate.foreground(session.pkg())==null)) return false;
            Object result=chain.proceed();
            if(session!=null && !ready(owner,session,Boolean.TRUE.equals(result))) return false;
            return result;
        },Void[].class);
        r.hook(n.view()+"$i","b",void.class,chain->{
            Object owner=Reflect.get(chain.getThisObject(),"a");var session=starting.remove(owner);
            if(session!=null && !ready(owner,session,Boolean.TRUE.equals(chain.getArg(0)))) {
                Reflect.call(owner,n.close());
                r.event("thermal",session.pkg(),"autoplay_init_closed","pid="+session.pid()+" callback_success="+chain.getArg(0)+" observed_pkg="
                        +Reflect.get(owner,n.packageField())+" observed_pid="+Reflect.get(owner,n.pidField())+" native_view_closed=true");return null;
            }
            return chain.proceed();
        },Boolean.class);
        gate.deopt(n.controller());gate.deopt(n.view());
        r.log("feature=thermal stage=feature_bindings feature_name=autoplay_inline owner="+n.controller());
    }
    private boolean ready(Object owner,ThermalGate.Session session,boolean initialized) throws Throwable {
        return ThermalFeaturePolicy.autoplayReady(initialized,gate.valid(session),
                session.pkg().equals(Reflect.get(owner,n.packageField())),session.pid()==(Integer)Reflect.get(owner,n.pidField()));
    }
}
