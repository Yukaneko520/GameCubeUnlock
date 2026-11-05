package com.yuka.gamecube.tools.hooks.diagnostics;

import android.content.Context;
import android.provider.Settings;
import com.yuka.gamecube.tools.hooks.HookRuntime;

final class DisplayTrace {
    private DisplayTrace() {}
    static void install(HookRuntime r) throws Throwable {
        NativeMetrics metrics=new NativeMetrics(r);
        r.hook("com.vivo.gameassistant.framedisplay.a","e",float.class,chain -> {
            Object result=chain.proceed();
            metrics.rememberNative(r.currentPackage(),((Number)result).floatValue());
            return result;
        },boolean.class);
        installWriters(r,"com.vivo.common.utils.l0",metrics);
        r.hook("com.vivo.common.utils.l0","c",boolean.class,chain -> {
            Object result=chain.proceed();
            try {
                String pkg=r.currentPackage();
                if (r.snapshot().apps.containsKey(pkg)) {
                    Context context=(Context)chain.getArg(0);
                    r.event("sr",pkg,"writer_returned:"+chain.getArg(2),"return="+result+" pid="+chain.getArg(1)
                            +" observed="+Settings.Secure.getString(context.getContentResolver(),"gamecube_super_resolution")
                            +" backend_ack=unknown");
                }
            } catch (Throwable error) { r.error("sr_diagnostics",error); }
            return result;
        },Context.class,int.class,int.class);
    }
    static void installWriters(HookRuntime r,String owner,NativeMetrics metrics) throws Throwable {
        for (String method : new String[]{"a","b"}) {
            String key=method.equals("b")?"gamecube_frame_interpolation":"gamecube_frame_interpolation_for_sr";
            r.hook(owner,method,void.class,chain -> {
                Object result=chain.proceed();
                try {
                    String pkg=r.currentPackage();
                    if (!r.snapshot().apps.containsKey(pkg)) return result;
                    Context context=(Context)chain.getArg(0);
                    String command=Settings.System.getString(context.getContentResolver(),key);
                    String state=(Boolean)chain.getArg(1)?"enable":"disable";
                    boolean valid=!method.equals("b") || !(Boolean)chain.getArg(1)
                            || (Integer)chain.getArg(2)>=0 && (Integer)chain.getArg(5)>1;
                    r.event("frame",pkg,"writer_returned:"+key+":"+state,
                            "args="+java.util.Arrays.asList(chain.getArg(1),chain.getArg(2),chain.getArg(3),
                                    chain.getArg(4),chain.getArg(5))+" observed="+command
                                    +" native_args_valid="+valid+" backend_ack=unknown");
                    if (method.equals("b") && valid && metrics!=null) metrics.afterCommand(pkg,command,r.snapshot().revision);
                } catch (Throwable error) { r.error("frame_diagnostics",error); }
                return result;
            },Context.class,boolean.class,int.class,int.class,int.class,int.class);
        }
    }
}
