package com.yuka.gamecube.tools.hooks.diagnostics;

import android.content.Context;
import com.yuka.gamecube.tools.hooks.HookRuntime;

/** Observes the original results only; diagnostic failure cannot withdraw functional hooks. */
public final class NativeDiagnostics {
    private NativeDiagnostics() {}
    public static void install(HookRuntime r, boolean assistant) {
        if(r.adapter.baseline().sdk()==34) {
            r.installFamily("diagnostics/native declarations",()->{
                for(String method:new String[]{"a0","s0","i0"}) gate(r,r.adapter.commonType(),method,-1);
                gate(r,r.adapter.commonType(),"U",-1,Context.class);
            });
            if(assistant) r.installFamily("diagnostics/control commands",()->ControlTrace.install(r));
            return;
        }
        if(r.adapter.baseline().sdk()==35) {
            r.installFamily("diagnostics/native declarations",()->{
                for(String m:new String[]{"x0","X0","Y0"}) gate(r,"com.vivo.common.a",m,-1);
                gate(r,"com.vivo.common.a","o0",-1,Context.class);
            });
            if(!assistant) return;
            r.installFamily("diagnostics/control commands",()->ControlTrace.install(r));
            r.installFamily("diagnostics/visibility",()->{
                gate(r,"com.vivo.gameassistant.utils.z0","w0",0,String.class);
                gate(r,"com.vivo.gameassistant.utils.z0","e1",1,Context.class,String.class);
                gate(r,"com.vivo.gameassistant.utils.z0","Y0",-1,Context.class);
            });
            r.installFamily("diagnostics/display commands",()->DisplayTrace.installWriters(r,"a7.t",null));
            return;
        }
        r.installFamily("diagnostics/native declarations", () -> {
            for (String method : new String[]{"H1", "F1", "W0", "E1"})
                gate(r,"com.vivo.common.a",method,-1);
            for (String method : new String[]{"D1", "J0"})
                gate(r,"com.vivo.common.a",method,-1,Context.class);
        });
        if (!assistant) return;
        r.installFamily("diagnostics/visibility", () -> {
            gate(r,"com.vivo.gameassistant.utils.e0","n0",0,String.class);
            gate(r,"com.vivo.gameassistant.utils.e0","U0",1,Context.class,String.class);
            gate(r,"com.vivo.gameassistant.utils.e0","k0",-1,Context.class);
        });
        r.installFamily("diagnostics/control commands", () -> ControlTrace.install(r));
        r.installFamily("diagnostics/display commands", () -> DisplayTrace.install(r));
        r.installFamily("diagnostics/VSR commands", () -> VsrTrace.install(r));
    }
    private static void gate(HookRuntime r,String owner,String method,int pkgArgument,Class<?>...args) throws Throwable {
        r.hook(owner,method,boolean.class,chain -> {
            Object result=chain.proceed();
            String pkg=pkgArgument<0?r.currentPackage():(String)chain.getArg(pkgArgument);
            if (r.snapshot().enabled)
                r.event("eligibility",pkg,"gate_returned:"+owner+"."+method,
                        "effective_result="+result+" hardware_effect=unverified");
            return result;
        },args);
    }
}
