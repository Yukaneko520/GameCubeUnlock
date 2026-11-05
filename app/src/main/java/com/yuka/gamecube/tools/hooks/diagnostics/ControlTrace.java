package com.yuka.gamecube.tools.hooks.diagnostics;

import android.content.Context;
import android.provider.Settings;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.util.Arrays;

final class ControlTrace {
    private ControlTrace() {}
    static void install(HookRuntime r) throws Throwable {
        var api=com.yuka.gamecube.tools.compat.SuperControlLayout.forSdk(r.adapter.baseline().sdk());
        writer(r,api.ordinaryWriter(),"f","touch",-1,false,
                "vts_game_para_adjust",Context.class,int.class,int.class,int.class);
        writer(r,api.ordinaryWriter(),"e","gyro",1,false,
                "filter_gyro_parameter",Context.class,String.class,int.class,int.class);
        writer(r,api.touchHelper(),"i","superTouch",1,true,
                "vivo_sync_game_switch_status",Context.class,String.class,boolean.class);
        if(api.gyroHelper()!=null) writer(r,api.gyroHelper(),"k","superGyro",1,false,
                "vivo_game_gyro_dealy_promotion",Context.class,String.class,int.class,int.class);
    }
    private static void writer(HookRuntime r,String owner,String method,String feature,int pkgArgument,
                               boolean secure,String key,Class<?>...types) throws Throwable {
        r.hook(owner,method,void.class,chain -> {
            String pkg=pkgArgument<0?r.currentPackage():(String)chain.getArg(pkgArgument);
            Object result=chain.proceed();
            try {
                if (!r.snapshot().apps.containsKey(pkg)) return result;
                Object[] parameters=new Object[types.length-1];
                for (int i=1;i<types.length;i++) parameters[i-1]=chain.getArg(i);
                Context context=(Context)chain.getArg(0);
                String observed=secure?Settings.Secure.getString(context.getContentResolver(),key)
                        :Settings.System.getString(context.getContentResolver(),key);
                String extra=feature.equals("superGyro")?" antiShake="+Settings.System.getString(
                        context.getContentResolver(),"vivo_game_gyro_anti_shake_promotion"):"";
                r.event(feature,pkg,"writer_returned", "args="+Arrays.toString(parameters)
                        +" setting="+key+" observed="+observed+extra+" backend_ack=unknown");
            } catch (Throwable error) { r.error("control_diagnostics",error); }
            return result;
        },types);
    }
}
