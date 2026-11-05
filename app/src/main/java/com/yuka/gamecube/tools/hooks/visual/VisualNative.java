package com.yuka.gamecube.tools.hooks.visual;

import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Calls original Vivo APIs; logs native failures separately from Java-only PQ state. */
final class VisualNative implements VisualEngine.Device {
    private final HookRuntime r;
    private final Object manager;
    private final Method get,set;
    private String pkg="";
    private long request;
    private long operation;
    private int baselineMode= -1;
    private boolean touched;
    private BooleanSupplier valid=()->false;
    private BooleanSupplier session=()->false;
    VisualNative(HookRuntime r) throws Throwable {
        this.r=r;
        manager=Reflect.staticCall(r.type("vivo.common.AbsVivoDisplayEnhanceManager"),"getInstance");
        if(manager==null) throw new IllegalStateException("Display enhancement manager unavailable");
        get=Reflect.method(manager.getClass(),"getDisplayEnhanceConfigure",int.class,int[].class,int.class);
        set=Reflect.method(manager.getClass(),"setDisplayEnhanceConfigure",int.class,int[].class,int.class);
        if(get.getReturnType()!=int.class || set.getReturnType()!=int.class)
            throw new NoSuchMethodException("Unexpected display command return type");
    }
    void begin(String target,BooleanSupplier authorized,BooleanSupplier ownerSession) throws Throwable {
        if(!pkg.isEmpty() && !pkg.equals(target)) release();
        valid=authorized;
        session=ownerSession;
        if(pkg.isEmpty()) {
            pkg=target;request++;
            var mode=read(56,1);baselineMode=mode.code()==0&&mode.values().length==1?mode.values()[0]:-1;
        }
    }
    boolean owned() { return !pkg.isEmpty(); }
    VisualEngine.Result apply(VisualCommands.Command command) throws Throwable {
        if(!valid.getAsBoolean()) throw new IllegalStateException("Stale visual request");
        request++;touched=true;
        var result=new VisualEngine(this).apply(command);
        log("effect_result","state="+result.state()+" step="+result.stage()+" code="+result.code()
                +" requested_type="+command.type()+" requested="+Arrays.toString(command.values())
                +" actual="+Arrays.toString(result.actual()));
        return result;
    }
    @Override public VisualEngine.Reply read(int type,int count) throws Throwable {
        log("read_begin","type="+type+" count="+count);
        int[] values=new int[count];int code=(Integer)get.invoke(manager,type,values,count);
        log("read","type="+type+" code="+code+" values="+Arrays.toString(values));
        return new VisualEngine.Reply(code,values);
    }
    @Override public int write(int type,int[] values) throws Throwable {
        if(!valid.getAsBoolean()) throw new IllegalStateException("Visual authorization withdrawn");
        log("write_begin","type="+type+" values="+Arrays.toString(values));
        int code=(Integer)set.invoke(manager,type,values.clone(),values.length);
        log("write","type="+type+" code="+code+" values="+Arrays.toString(values));return code;
    }
    @Override public void settle() throws InterruptedException { Thread.sleep(20); }
    void release() throws Throwable {
        if(pkg.isEmpty()) return;
        try {
            // Original OFF resets the filter first. Never bypass an active MEMC/another effect.
            if(touched && baselineMode==1 && session.getAsBoolean()) {
                var mode=read(56,1);var effect=read(267,1);var memc=read(258,1);
                if(value(mode,0) && value(effect,0) && value(memc,0)) {
                    int code=(Integer)set.invoke(manager,56,new int[]{1},1);
                    log("restore_mode","code="+code+" baseline=1");
                } else log("restore_mode_skipped","reason=mode_or_effect_owner_changed");
            }
        } finally { pkg="";baselineMode= -1;touched=false;valid=()->false;session=()->false; }
    }
    private static boolean value(VisualEngine.Reply reply,int expected) {
        return reply.code()==0 && reply.values().length==1 && reply.values()[0]==expected;
    }
    private void log(String stage,String detail) {
        r.log("feature=visual pkg="+pkg+" request="+request+" op="+(++operation)+" stage="+stage+" "+detail);
    }
}
