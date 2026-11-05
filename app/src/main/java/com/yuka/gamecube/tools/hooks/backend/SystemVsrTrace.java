package com.yuka.gamecube.tools.hooks.backend;

import android.os.Binder;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;

/** Optional, original-first observations. A callback attempt is not a renderer acknowledgement. */
final class SystemVsrTrace {
    private SystemVsrTrace() {}
    static void install(XposedModule module,Object service,Predicate<String> selected,IntPredicate callerSelected,BooleanSupplier selectionKnown,
                        Consumer<String> log,BiConsumer<String,Throwable> error) {
        Class<?> type=service.getClass();
        boolean legacy=android.os.Build.VERSION.SDK_INT==35;
        if(legacy) observe(module,type,"isVSRPostProcessingWhitelistApp",boolean.class,chain->{
            int uid=Binder.getCallingUid(),pid=Binder.getCallingPid();Object result=chain.proceed();
            if(callerSelected.test(uid) || !selectionKnown.getAsBoolean()) log.accept("stage=client_discovery route=os5_pp uid="+uid+
                    " client_pid="+pid+" result="+result+" selected="+callerSelected.test(uid));
            return result;
        },log,error);
        else observe(module,type,"isWhitelistApp",boolean.class,chain-> {
            int uid=Binder.getCallingUid(),pid=Binder.getCallingPid();
            Object result=chain.proceed();
            if((Integer)chain.getArg(0)==102)
                log.accept("stage=client_discovery function=102 uid="+uid+" client_pid="+pid+" result="+result
                        +" selected="+callerSelected.test(uid)+" selection_pending="+!selectionKnown.getAsBoolean());
            return result;
        },log,error,int.class);
        observe(module,type,"registerCallback",void.class,chain-> {
            int uid=Binder.getCallingUid(),pid=Binder.getCallingPid();
            Object result=chain.proceed();
            if(callerSelected.test(uid) || !selectionKnown.getAsBoolean()) log.accept("stage=client_register_returned uid="+uid+" client_pid="+pid
                    +" callback_nonnull="+(chain.getArg(0)!=null)+" registration_ack=unverified");
            return result;
        },log,error,callbackType(type,log,error));
        observe(module,type,"getGameLibPath",String.class,chain-> {
            int uid=Binder.getCallingUid(),pid=Binder.getCallingPid(); Object result=chain.proceed();
            if(callerSelected.test(uid) || !selectionKnown.getAsBoolean())
                log.accept("stage=client_library uid="+uid+" client_pid="+pid+" path="+result);
            return result;
        },log,error);
        observe(module,type,"reportResult",void.class,chain-> {
            int uid=Binder.getCallingUid(),pid=Binder.getCallingPid(); Object result=chain.proceed();
            if(callerSelected.test(uid) || !selectionKnown.getAsBoolean()) log.accept("stage=native_report uid="+uid+" client_pid="+pid
                    +" code="+chain.getArg(0)+" type="+chain.getArg(2)+" message="+bounded(chain.getArg(1)));
            return result;
        },log,error,int.class,String.class,int.class);
        observe(module,type,"dispatchVSREnabled",void.class,chain-> {
            Object result=chain.proceed(); String pkg=(String)chain.getArg(0);
            if(selected.test(pkg)) log.accept("stage=callback_queued pkg="+pkg+" user="+chain.getArg(1)
                    +" client_pid="+chain.getArg(2)+" value="+chain.getArg(3)+" type="+chain.getArg(4));
            return result;
        },log,error,String.class,int.class,int.class,int.class,int.class);
        try {
            Class<?> record=Class.forName(type.getName()+"$CallbackRecord",false,type.getClassLoader());
            var pkgField=record.getDeclaredField("mPackageName"); pkgField.setAccessible(true);
            var pidField=record.getDeclaredField("mPid"); pidField.setAccessible(true);
            observe(module,record,"onVSREnabled",void.class,chain-> {
                String pkg=(String)pkgField.get(chain.getThisObject());
                Object result=chain.proceed();
                if(selected.test(pkg)) log.accept("stage=callback_attempt_returned pkg="+pkg+" client_pid="
                        +pidField.get(chain.getThisObject())+" value="+chain.getArg(0)+" type="+chain.getArg(1)+" render=unverified");
                return result;
            },log,error,int.class,int.class);
        } catch(Throwable failure) { error.accept("diagnostic_callback_record",failure); }
    }
    private static Class<?> callbackType(Class<?> type,Consumer<String> log,BiConsumer<String,Throwable> error) {
        try { return Class.forName("com.vivo.game.IGameManagerCallback",false,type.getClassLoader()); }
        catch(Throwable failure) { error.accept("diagnostic_callback_type",failure); return null; }
    }
    private static void observe(XposedModule module,Class<?> type,String name,Class<?> result,
                                XposedInterface.Hooker observer,Consumer<String> log,
                                BiConsumer<String,Throwable> error,Class<?>...args) {
        try {
            Method method=type.getDeclaredMethod(name,args);
            if(method.getReturnType()!=result) throw new NoSuchMethodException(name+" return type");
            method.setAccessible(true);
            module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(observer);
            log.accept("diagnostic installed method="+type.getName()+"."+name);
        } catch(Throwable failure) { error.accept("diagnostic_install:"+name,failure); }
    }
    private static String bounded(Object value) {
        String text=String.valueOf(value).replace('\n',' ').replace('\r',' ');
        return text.length()>512?text.substring(0,512)+"[truncated]":text;
    }
}
