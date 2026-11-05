package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.SparseArray;
import com.yuka.gamecube.tools.compat.SuperControlLayout;
import com.yuka.gamecube.tools.hooks.Reflect;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/** Overlays native getter results only during the verified owner's synchronous apply call. */
public final class SuperTouchHooks {
    private static final String REQUESTER="com.vivo.gamewatch.common.RefreshRateRequester";
    private final SuperControlRuntime r;
    private final SuperControlLayout names;
    private final ThreadLocal<Session> applying=new ThreadLocal<>();
    private volatile Object monitor;
    private SuperTouchEnrollment enrollment;
    record Check(int uid,int procUid,String uidSource,Object userSwitch,boolean api,boolean interactive,boolean selected) {}
    private record Session(String pkg,int pid,BackendPolicy policy,boolean extend) {}
    private SuperTouchHooks(XposedModule module,ClassLoader loader,Context context,int sdk) {
        r=new SuperControlRuntime(module,loader,context);names=SuperControlLayout.forSdk(sdk);
    }
    public static SuperTouchHooks install(XposedModule module,ClassLoader loader,Context context,int sdk) throws Throwable {
        var hooks=new SuperTouchHooks(module,loader,context,sdk);
        try { hooks.bind();hooks.r.start(new Handler(Looper.getMainLooper()),hooks::changed);return hooks; }
        catch(Throwable failure) { hooks.r.close();throw failure; }
    }
    private void bind() throws Throwable {
        Class<?> row=r.type(names.monitor()+"$b");
        for(String field:new String[]{"a","b","e"}) Reflect.field(row,field);
        for(String field:new String[]{"b","d","k"}) Reflect.field(r.type(names.monitor()),field);
        Reflect.field(r.type(names.pacing()),names.switchMap());
        Method enabled=r.method(names.pacing(),names.enabled(),String.class);
        Method flags=r.method(names.pacing(),names.flags(),String.class);
        if(enabled.getReturnType()!=boolean.class || flags.getReturnType()!=int.class)
            throw new NoSuchMethodException("Super Touch getter returns");
        r.hook(enabled,chain->{Object stock=chain.proceed();return extension(chain)?true:stock;});
        r.hook(flags,chain->{int stock=(Integer)chain.proceed();return extension(chain)?SuperTouchState.flags(stock):stock;});
        r.hook(names.baseFlags(),names.baseMethod(),int.class,chain->{
            int stock=(Integer)chain.proceed();return extension(chain)?SuperTouchState.base(stock):stock;
        },String.class,int.class);
        r.hook(names.pacing(),names.valid(),boolean.class,chain->{
            Object result=chain.proceed();Session session=applying.get();
            if(session!=null) r.log("superTouch",session.pkg,"pacing_gate","pid="+session.pid+
                    " display_fps="+chain.getArg(0)+" result="+result+" extension="+session.extend);
            return result;
        },int.class,String.class);
        Method dispatch=r.method(names.monitor(),names.dispatch(),row);
        if(dispatch.getReturnType()!=void.class) throw new NoSuchMethodException("Super Touch dispatcher return");
        r.hook(dispatch,chain->dispatch(chain,enabled,flags));
        enrollment=new SuperTouchEnrollment(this,r,names);enrollment.install();
        for(Method caller:new Method[]{dispatch,r.method(names.pacing(),names.valid(),int.class,String.class),
                r.method(names.monitor(),names.adjust(),row,int.class,int.class,boolean.class)})
            r.log("superTouch","","deopt","method="+caller+" result="+r.module.deoptimize(caller));
        monitor=Reflect.staticCall(r.type(names.monitor()),names.instance());
        r.log("superTouch","","bindings_ready","sdk="+android.os.Build.VERSION.SDK_INT+
                " flags=0x"+Integer.toHexString(SuperTouchState.FLAGS)+" owner_ready="+(monitor!=null)+
                " feature_supported="+Reflect.staticCall(r.type(REQUESTER),"isFeatureSupported")+
                " frame_api="+Reflect.get(r.type(REQUESTER),"FRAME_PACING_SUPPORTED"));
        try { requestTrace(); } catch(Throwable failure) { r.error("touch_request_trace_unavailable",failure); }
    }
    private boolean extension(XposedInterface.Chain chain) {
        Session session=applying.get();
        return session!=null && session.extend && session.policy==r.policy && session.pkg.equals(chain.getArg(0));
    }
    private Object dispatch(XposedInterface.Chain chain,Method enabled,Method flags) throws Throwable {
        Object owner=chain.getThisObject(),row=chain.getArg(0);monitor=owner;
        synchronized(r.gate) {
            Session previous=applying.get(),next=null;
            try {
                String pkg=(String)Reflect.get(row,"a");int pid=(Integer)Reflect.get(row,"b");
                BackendPolicy policy=r.policy;
                if(policy.control(pkg,"superTouch")) {
                    Object pacing=Reflect.get(owner,"k");
                    boolean bound=pid>0 && ((SparseArray<?>)Reflect.get(owner,"b")).get(pid)==row;
                    Check check=check(pkg,pid,bound,enrollment.uid(pkg,pid,row));
                    boolean nativeEnabled=(Boolean)r.original(enabled,pacing,pkg);
                    int nativeFlags=(Integer)r.original(flags,null,pkg);
                    next=new Session(pkg,pid,policy,SuperTouchState.needsExtension(check.selected,nativeEnabled,nativeFlags));
                    r.log("superTouch",pkg,"dispatch_begin","pid="+pid+" uid="+check.uid+" bound="+bound+
                            " proc_uid="+check.procUid+" uid_source="+check.uidSource+
                            " installed_uid="+policy.installedUid(pkg)+" interactive="+check.interactive+
                            " switch="+check.userSwitch+" api="+check.api+" native_enabled="+nativeEnabled+
                            " native_flags=0x"+Integer.toHexString(nativeFlags)+" extension="+next.extend+
                            " previous_handle="+Reflect.get(row,"e"));
                }
            } catch(Throwable failure) { r.error("touch_session_closed",failure); }
            if(next==null) applying.remove();else applying.set(next);
            try { return chain.proceed(); }
            finally {
                if(previous==null) applying.remove();else applying.set(previous);
            }
        }
    }
    Check check(String pkg,int pid,boolean bound,Integer eventUid) throws Throwable {
        if(pid<=0||!r.policy.control(pkg,"superTouch")) return new Check(-1,-1,"none",null,false,false,false);
        int procUid=(Integer)Reflect.staticCall(android.os.Process.class,"getUidForPid",pid);
        int uid=SuperTouchState.resolveUid(procUid,eventUid);
        Map<?,?> switches=(Map<?,?>)Reflect.get(r.type(names.pacing()),names.switchMap());
        Object userSwitch=switches==null?null:switches.get(pkg);
        boolean api=Boolean.TRUE.equals(Reflect.get(r.type(REQUESTER),"FRAME_PACING_SUPPORTED"))
                && (Boolean)Reflect.staticCall(r.type("android.os.SystemProperties"),"getBoolean","persist.debug.frame_pacing.enabled",true);
        PowerManager power=r.context.getSystemService(PowerManager.class);boolean interactive=power!=null&&power.isInteractive();
        boolean selected=SuperTouchState.eligible(r.policy,pkg,uid,bound&&SuperTouchState.matchesEvent(uid,eventUid),userSwitch,api,interactive);
        return new Check(uid,procUid,procUid>=0?"proc":eventUid!=null?"oem_process_observer":"unavailable",userSwitch,api,interactive,selected);
    }
    private void changed(BackendPolicy old,BackendPolicy next) {
        Object owner=monitor;if(owner==null) return;
        try {
            ((Handler)Reflect.get(owner,"d")).post(()-> {
                synchronized(owner) { try {
                    enrollment.reconcile(owner,"config");
                    SparseArray<?> rows=(SparseArray<?>)Reflect.get(owner,"b");
                    r.log("superTouch","","active_records","count="+rows.size());
                    for(int i=0;i<rows.size();i++) {
                        String pkg=(String)Reflect.get(rows.valueAt(i),"a");
                        if(old.control(pkg,"superTouch")==r.policy.control(pkg,"superTouch")) continue;
                        Reflect.call(owner,names.refresh(),pkg);
                        r.log("superTouch",pkg,"reconcile","selected="+r.policy.control(pkg,"superTouch")+" cleanup=oem_refresh");
                    }
                } catch(Throwable failure) { r.error("touch_reconcile",failure); } }
            });
        } catch(Throwable failure) { r.error("touch_reconcile_schedule",failure); }
    }
    private void requestTrace() throws Throwable {
        for(Method method:r.type(REQUESTER).getDeclaredMethods()) {
            if(!method.getName().equals("acquire") || method.getReturnType()!=long.class) continue;
            r.hook(method,chain->{
                Session session=applying.get();
                Object result=chain.proceed();
                if(session!=null) r.log("superTouch",session.pkg,"request_returned","pid="+session.pid+
                        " extension="+session.extend+" args="+Arrays.toString(chain.getArgs().toArray())+
                        " handle="+result+" native_effect=unverified");
                return result;
            });
        }
    }
}
