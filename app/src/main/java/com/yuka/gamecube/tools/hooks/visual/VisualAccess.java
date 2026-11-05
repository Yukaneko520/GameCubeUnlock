package com.yuka.gamecube.tools.hooks.visual;

import android.content.Context;
import com.yuka.gamecube.tools.compat.VisualLayout;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.lang.reflect.Method;

final class VisualAccess {
    static final String BEAN="com.vivo.gameassistant.gamefilter.bean.GameFilterBean";
    final HookRuntime r;
    final VisualLayout n;
    final Class<?> bean;
    final Method helper,iris,flag,foreground,close,current;
    VisualAccess(HookRuntime r) throws Exception {
        this.r=r;n=VisualLayout.forSdk(r.adapter.baseline().sdk());bean=r.type(BEAN);
        helper=r.method(n.helper(),n.instance());iris=r.method(n.helper(),n.iris());
        flag=r.method(n.helper(),n.flag(),boolean.class,Context.class);
        foreground=r.method(n.controller(),n.foreground(),boolean.class,String.class);
        current=r.method(n.controller(),n.bean(),boolean.class);
        close=n.sdk()==36?r.method(n.controller(),n.close(),Context.class,bean,String.class,boolean.class)
                :r.method(n.controller(),n.close(),Context.class,bean,String.class);
        for(String getter:new String[]{"isEnableVisualEnhancement","isEnableUniversalFilter"})
            if(r.method(BEAN,getter).getReturnType()!=boolean.class) throw new NoSuchMethodException(getter);
        for(String setter:new String[]{"setEnableVisualEnhancement","setEnableUniversalFilter"}) r.method(BEAN,setter,boolean.class);
        if(iris.getReturnType()!=boolean.class || current.getReturnType()!=bean)
            throw new NoSuchMethodException("Unexpected visual helper/bean declaration");
    }
    Class<?>[] openArgs() {
        return n.sdk()==34?new Class<?>[]{Context.class,bean,String.class}
                :new Class<?>[]{Context.class,bean,String.class,boolean.class,boolean.class};
    }
    boolean quality(Object value) throws Throwable { return Boolean.TRUE.equals(Reflect.call(value,"isEnableVisualEnhancement")); }
    boolean filter(Object value) throws Throwable { return Boolean.TRUE.equals(Reflect.call(value,"isEnableUniversalFilter")); }
    boolean supported() throws Throwable { return Boolean.TRUE.equals(r.original(iris,r.original(helper,null))); }
    void enabled(boolean value) throws Throwable { r.original(flag,r.original(helper,null),value,r.context()); }
    void close(Object controller,Object value,String pkg) throws Throwable {
        if(n.sdk()==36) r.original(close,controller,r.context(),value,pkg,true);
        else r.original(close,controller,r.context(),value,pkg);
    }
}
