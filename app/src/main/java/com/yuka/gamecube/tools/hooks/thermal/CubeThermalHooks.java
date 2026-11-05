package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.compat.ThermalLayout;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.*;

/** Changes thermal decisions only; OEM callbacks retain sampling, user selection and recovery. */
public final class CubeThermalHooks implements ConfigListener {
    private final HookRuntime r;
    private final ThermalLayout names;
    private final ThermalGate gate;
    private final boolean os5;
    private CubeThermalHooks(HookRuntime r) {
        this.r=r;gate=new ThermalGate(r); os5=r.adapter.baseline().sdk()==35; names=ThermalLayout.forSdk(r.adapter.baseline().sdk());
    }
    public static void install(HookRuntime r) throws Throwable {
        var hooks=new CubeThermalHooks(r);
        if(hooks.names.os4()) {
            hooks.bind(hooks.names.frame(),hooks.names.frameHot(),false,int.class);
            hooks.bind(hooks.names.frame(),hooks.names.frameCool(),true,int.class);
            r.method(hooks.names.frame(),hooks.names.frameTick(),Long.class);
            Reflect.field(r.type(hooks.names.frame()),"i");
            hooks.gate.deopt(hooks.names.frame());
            r.addListener(hooks);return;
        }
        hooks.bind(hooks.names.frame(),hooks.names.frameHot(),false,double.class);
        hooks.bind(hooks.names.frame(),hooks.names.frameCool(),true,double.class);
        Class<?>[] srArgs=hooks.os5?new Class<?>[]{double.class,int.class}:new Class<?>[]{int.class};
        hooks.bind(hooks.names.sr(),hooks.names.srHot(),false,srArgs);
        hooks.bind(hooks.names.sr(),hooks.names.srCool(),true,srArgs);
        // Preflight the exact recovery callbacks too, before registering a live config listener.
        r.method(hooks.names.frame(),hooks.names.frameTick(),Long.class);
        r.method(hooks.names.sr(),hooks.names.srTick(),hooks.os5?new Class<?>[]{Long.class}:new Class<?>[]{double.class,double.class});
        Reflect.field(r.type(hooks.names.frame()),"i"); Reflect.field(r.type(hooks.names.sr()),"k");
        r.method(hooks.names.sr(),hooks.os5?"I0":"g1");
        hooks.gate.deopt(hooks.names.frame());hooks.gate.deopt(hooks.names.sr());
        r.addListener(hooks);
    }
    private void bind(String owner,String method,boolean recovery,Class<?>...args) throws Throwable {
        boolean frame=owner.equals(names.frame());
        r.hook(owner,method,boolean.class,chain->{
            Object result=chain.proceed(); // OS6 SR also refreshes the cached real temperature here.
            Object controller=chain.getThisObject();
            String pkg=(String)Reflect.get(controller,frame?"i":"k");
            int pid=activePid(pkg);
            if(pid>0) {
                if(!frame) {
                    int mode=(Integer)chain.getArg(os5?1:0);
                    if((mode!=1 && mode!=2) || !(Boolean)Reflect.call(controller,os5?"I0":"g1")) return result;
                }
                boolean extreme=!frame && (Integer)chain.getArg(os5?1:0)==2;
                String hot=frame?"f":extreme?"i":"g",cool=frame?"g":extreme?"j":"h";
                if(os5 && !frame) { hot=extreme?"h":"f";cool=extreme?"i":"g"; }
                r.event("thermal",pkg,"thermal_suppressed_"+(frame?"memc":"sr"),"branch="+(frame?"cube_memc":"cube_sr")+" pid="+pid
                        +" decision="+method+" original="+result+" actual="+(frame||os5?chain.getArg(0):Reflect.get(controller,"C"))
                        +" thresholds="+Reflect.get(controller,hot)+"/"+Reflect.get(controller,cool)
                        +" mode="+(frame?Reflect.get(controller,names.os4()?"n":"p"):chain.getArg(os5?1:0))+" recovery="+recovery);
                return recovery;
            }
            return result;
        },args);
    }
    private int activePid(String pkg) throws Throwable {
        var session=gate.foreground(pkg);
        return gate.valid(session)?session.pid():0;
    }
    @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        String pkg=r.currentPackage();
        if(!old.allows(pkg,"thermalBypass") && !next.allows(pkg,"thermalBypass")) return;
        if(old.allows(pkg,"thermalBypass")==next.allows(pkg,"thermalBypass") && old.allows(pkg,"frame")==next.allows(pkg,"frame")
                && old.allows(pkg,"sr")==next.allows(pkg,"sr")) return;
        var owner=r.adapter.owner(); Object service=Reflect.staticCall(r.type(owner.type()),owner.instance());
        Object frame=Reflect.call(service,names.frameGetter()),sr=names.srGetter()==null?null:Reflect.call(service,names.srGetter());
        if(frame!=null && pkg.equals(Reflect.get(frame,"i"))) Reflect.call(frame,names.frameTick(),Long.valueOf(0));
        if(sr!=null && pkg.equals(Reflect.get(sr,"k"))) {
            if(os5) Reflect.call(sr,names.srTick(),Long.valueOf(0));
            else {
                // Obtain a fresh real reading, never pass a fabricated cooldown temperature.
                Object helper=Reflect.staticCall(r.type("com.vivo.common.utils.TemperatureHelper"),"g",r.context());
                double[] temperatures=(double[])Reflect.call(helper,"f");
                if(temperatures.length<2) throw new IllegalStateException("Real thermal readings unavailable");
                Reflect.call(sr,names.srTick(),temperatures[0],temperatures[1]);
            }
        }
        r.event("thermal",pkg,next.allows(pkg,"thermalBypass")?"restore_requested_mode":"restore_stock","branch=gamecube real_temperature=true");
    }
}
