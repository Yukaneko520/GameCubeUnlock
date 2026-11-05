package com.yuka.gamecube.tools.hooks.backend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import com.yuka.gamecube.tools.hooks.HookLog;
import com.yuka.gamecube.tools.hooks.Reflect;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.util.Map;

/** Extends only the foreground package policy, leaving the OEM gyro state/gear logic intact. */
public final class SuperGyroHooks {
    static final String GYRO="com.vivo.sensor.gyro.GameModeGyroEnhance";
    static final String AUTO="com.vivo.sensor.brightness.info.AutoInfoManager";
    static final String LIST="com.vivo.sensor.brightness.info.AutoList";
    private final XposedModule module;
    private final ClassLoader loader;
    private final ThreadLocal<ControlTickets.Ticket> dispatch=new ThreadLocal<>();
    SuperControlRuntime r;
    Object manager;
    private Handler handler;
    private volatile String owned="";
    private SuperGyroHooks(XposedModule module,ClassLoader loader) { this.module=module;this.loader=loader; }
    public static SuperGyroHooks install(XposedModule module,ClassLoader loader) throws Throwable {
        var hooks=new SuperGyroHooks(module,loader);
        var ctor=Class.forName(AUTO,false,loader).getDeclaredConstructor(Context.class);
        module.hook(ctor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            Object result=chain.proceed();
            try { hooks.ready(chain.getThisObject(),(Context)chain.getArg(0)); }
            catch(Throwable failure) { HookLog.write(module,"system_server","feature=superGyro stage=initialization_closed",failure); }
            return result;
        });
        HookLog.write(module,"system_server","feature=superGyro stage=await_sensor_initialization",null);
        return hooks;
    }
    private synchronized void ready(Object auto,Context context) throws Throwable {
        if(r!=null) return;
        var runtime=new SuperControlRuntime(module,loader,context);
        manager=Reflect.get(auto,"mGameModeGyroEnhance");
        handler=(Handler)Reflect.get(runtime.type(AUTO),"mHandler");
        if(manager==null || handler==null) throw new IllegalStateException("Sensor owner not ready");
        r=runtime;
        try {
            bind();
            try { new SuperGyroTrace(this,dispatch).install(); }
            catch(Throwable failure) { r.error("gyro_native_trace_unavailable",failure); }
            IntentFilter screen=new IntentFilter();screen.addAction(Intent.ACTION_SCREEN_ON);screen.addAction(Intent.ACTION_SCREEN_OFF);
            context.registerReceiver(new BroadcastReceiver() {
                public void onReceive(Context c,Intent i) { handler.post(()->reconcile("screen_state")); }
            },screen,Context.RECEIVER_NOT_EXPORTED);
            r.start(handler,(old,next)->reconcile("config"));
        } catch(Throwable failure) { runtime.close();r=null;throw failure; }
    }
    private void bind() throws Throwable {
        for(String field:new String[]{"mCurrentPackageName","mPackageNameUidMapping","mAutoList",
                "mGameCubeStatus","mGameSdkGyroStatus","mGameGryoDealyPromotionStatus","mGameGryoAntiShakePromotionStatus"})
            Reflect.field(r.type(GYRO),field);
        r.hook(LIST,"isGameGryoPromotionWhiteList",boolean.class,chain->{
            Object stock=chain.proceed();String pkg=(String)chain.getArg(0);
            boolean extended=!Boolean.TRUE.equals(stock)&&active(pkg);
            try { if(pkg!=null&&(r.policy.snapshot.apps.containsKey(pkg)||pkg.equals(owned))) {
                Map<?,?> uids=(Map<?,?>)Reflect.get(manager,"mPackageNameUidMapping");
                PowerManager power=r.context.getSystemService(PowerManager.class);
                r.log("superGyro",pkg,"whitelist","stock="+stock+" extension="+extended+
                        " effective="+(Boolean.TRUE.equals(stock)||extended)+" current_pkg="+current()+
                        " cached_uid="+uids.get(pkg)+" installed_uid="+r.policy.installedUid(pkg)+
                        " interactive="+(power!=null&&power.isInteractive())+
                        " cube="+Reflect.get(manager,"mGameCubeStatus")+" sdk="+Reflect.get(manager,"mGameSdkGyroStatus"));
            } } catch(Throwable failure) { r.error("gyro_admission_trace",failure); }
            return extended?true:stock;
        },String.class);
        for(String name:new String[]{"notifyGameGyroDelayPromotionInfo","notifyGameGyroShakePromotionInfo"}) {
            r.hook(GYRO,name,void.class,chain->request(chain,name.contains("Delay")?"delay":"anti_shake"),int.class);
            r.log("superGyro","","deopt","method="+name+" result="+module.deoptimize(r.method(GYRO,name,int.class)));
        }
        r.hook(GYRO,"NotifyProcessInfo",void.class,chain->{
            String previous=current();Object result=chain.proceed();String current=current();
            if(!owned.isEmpty() && !owned.equals(current)) owned="";
            if(r.policy.snapshot.apps.containsKey(previous)||r.policy.snapshot.apps.containsKey(current))
                r.log("superGyro",current,"foreground","previous="+previous+" callback_uid="+chain.getArg(1)+" foreground="+chain.getArg(2));
            return result;
        },String.class,int.class,boolean.class);
        for(var method:new java.lang.reflect.Method[]{r.method(GYRO,"handleGameGyroPromotion",String.class),
                r.method(GYRO,"NotifyProcessInfo",String.class,int.class,boolean.class),r.method(GYRO,"SetGameSdkGyroInfo",int.class),
                r.method(AUTO,"handleGameGyroDelayPromotion"),r.method(AUTO,"handleGameGyroAntiShakePromotion")})
            r.log("superGyro","","deopt","method="+method+" result="+module.deoptimize(method));
    }
    String current() {
        try { return (String)Reflect.get(manager,"mCurrentPackageName"); }
        catch(Throwable failure) { return ""; }
    }
    boolean active(String pkg) {
        try {
            BackendPolicy policy=r.policy;
            if(!policy.control(pkg,"superGyro") || !pkg.equals(current())) return false;
            Map<?,?> uids=(Map<?,?>)Reflect.get(manager,"mPackageNameUidMapping");
            PowerManager power=r.context.getSystemService(PowerManager.class);
            return policy.controlSession(pkg,"superGyro",current(),uids.get(pkg),power!=null&&power.isInteractive());
        } catch(Throwable failure) { r.error("gyro_admission_closed",failure);return false; }
    }
    private boolean stock(String pkg) throws Throwable {
        return Boolean.TRUE.equals(r.original(r.method(LIST,"isGameGryoPromotionWhiteList",String.class),
                Reflect.get(manager,"mAutoList"),pkg));
    }
    private Object request(XposedInterface.Chain chain,String branch) throws Throwable {
        synchronized(r.gate) {
            String pkg=current();
            if(!r.policy.snapshot.apps.containsKey(pkg) && owned.isEmpty()) return chain.proceed();
            int gear=(Integer)chain.getArg(0);
            boolean nativeSupport=stock(pkg),extension=!nativeSupport&&active(pkg);
            var ticket=ControlTickets.create(pkg,r.policy.snapshot.revision,branch,gear,extension);
            var previous=dispatch.get();dispatch.set(ticket);
            try {
                r.log("superGyro",pkg,"request","id="+ticket.id()+" branch="+branch+" gear="+gear+
                        " native_whitelist="+nativeSupport+" extension="+extension+
                        " cube="+Reflect.get(manager,"mGameCubeStatus")+" sdk="+Reflect.get(manager,"mGameSdkGyroStatus"));
                Object result=chain.proceed();
                String field=branch.equals("delay")?"mGameGryoDealyPromotionStatus":"mGameGryoAntiShakePromotionStatus";
                int stored=(Integer)Reflect.get(manager,field);
                if(extension && stored>0) owned=pkg;
                r.log("superGyro",pkg,"request_returned","id="+ticket.id()+" branch="+branch+" requested="+gear+" stored="+stored+
                        " backend_dispatch_is_not_physical_measurement=true");
                return result;
            } finally { if(previous==null)dispatch.remove();else dispatch.set(previous); }
        }
    }
    private void reconcile(String reason) {
        synchronized(r.gate) { try {
            String pkg=current();
            if(!r.policy.control(pkg,"superGyro") && !pkg.equals(owned)) return;
            // Original handler closes active gears when the effective whitelist is withdrawn.
            Reflect.call(manager,"handleGameGyroPromotion",pkg);
            if(!active(pkg)) owned="";
            r.log("superGyro",pkg,"reconcile","reason="+reason+" selected="+active(pkg)+" cleanup=oem_gear_handler");
        } catch(Throwable failure) { r.error("gyro_reconcile",failure); } }
    }
}
