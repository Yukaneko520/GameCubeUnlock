package com.yuka.gamecube.tools.hooks.backend;

import android.os.Handler;
import android.content.Context;
import android.util.Pair;
import android.view.Display;
import android.view.WindowManager;
import java.lang.reflect.*;
import java.util.*;

/** Exact descriptor preflight; static OEM access happens only after RMS.initialize returns. */
final class RrmAccess {
    final Method initialize,getRequest,applyRequest,cancelRequest;
    final Constructor<?> constructor;
    private final Map<String,Field> fields=new HashMap<>();
    private final Method managerInstance,displayManagerInstance,physicalDisplay,wmsInstance,attrs,
            getCurrent,setCurrent,getFocus,getDisplayId,screenOn,supportedModes,windowMode,
            processFocus,processForeground,processAlive,windowVisible,windows,adjusterInstance,updateConfig;
    RrmAccess(ClassLoader loader,com.yuka.gamecube.tools.compat.RomAdapter.Display api) throws Exception {
        Class<?> manager=type(loader,api.prefix()+"WindowRequestManager");
        Class<?> request=type(loader,manager.getName()+"$WindowRequest");
        Class<?> record=type(loader,manager.getName()+"$WindowRecord");
        Class<?> param=type(loader,manager.getName()+"$WindowFpsParam");
        Class<?> display=type(loader,api.prefix()+"PhysicalDisplay");
        Class<?> displays=type(loader,api.prefix()+"PhysicalDisplayManager");
        Class<?> win=type(loader,"com.android.server.wm.WindowState");
        Class<?> process=type(loader,api.process());
        Class<?> wms=type(loader,api.wms());
        initialize=method(type(loader,"com.vivo.services.rms.RmsInjectorImpl"),"initialize",void.class,false,Context.class);
        getRequest=method(manager,"getWindowRequest",request,false,display,record,int.class,win,int.class);
        applyRequest=method(manager,"applyRequest",void.class,false,int.class);
        windows=method(manager,"getWindowsByDisplay",ArrayList.class,false,int.class);
        Class<?> adjuster=type(loader,api.prefix()+"RefreshRateAdjuster");
        adjusterInstance=method(adjuster,"getInstance",adjuster,true);
        updateConfig=method(adjuster,"requestUpdateActiveConfig",void.class,false);
        cancelRequest=method(request,"cancel",void.class,false);
        constructor=request.getDeclaredConstructor(display,record,Pair.class,int.class,param); constructor.setAccessible(true);
        managerInstance=method(manager,"getInstance",manager,true);
        displayManagerInstance=method(displays,"getInstance",displays,true);
        physicalDisplay=method(displays,"getPhysicalDisplay",display,false,int.class);
        wmsInstance=method(wms,"getInstance",wms,true);
        attrs=method(wms,"getAttrs",WindowManager.LayoutParams.class,false,win);
        getCurrent=method(display,"getWindowRequest",request,false);
        setCurrent=method(display,"setWindowRequest",void.class,false,request);
        getFocus=method(display,"getFocusWindow",win,false);
        getDisplayId=method(display,"getDisplayId",int.class,false);
        screenOn=method(display,"isScreenOn",boolean.class,false);
        supportedModes=method(display,"getSupportedModes",ArrayList.class,false);
        windowMode=win.getMethod("getWindowingMode");
        if(windowMode.getReturnType()!=int.class) throw new NoSuchMethodException("getWindowingMode");
        windowMode.setAccessible(true);
        windowVisible=win.getMethod("isVisible");
        if(windowVisible.getReturnType()!=boolean.class) throw new NoSuchMethodException("isVisible");
        windowVisible.setAccessible(true);
        processFocus=method(process,"hasFocus",boolean.class,false);
        processForeground=method(process,"isFgActivity",boolean.class,false);
        processAlive=method(process,"isAlive",boolean.class,false);
        field(manager,"mHander",Handler.class);
        field(request,"owner",record);
        for(String name:new String[]{"reqFps","reqPriority","touchMinFps","touchMaxFps"}) field(request,name,int.class);
        field(request,"reqReason",String.class);
        field(record,"win",win); field(record,"client",process);
        field(record,"pkgName",String.class); field(record,"startingWindow",boolean.class);
        field(process,"mUid",int.class); field(process,"mPid",int.class); field(process,"mPkgName",String.class);
    }
    private static Class<?> type(ClassLoader loader,String name) throws Exception { return Class.forName(name,false,loader); }
    static Method method(Class<?> type,String name,Class<?> returns,boolean isStatic,Class<?>...args) throws Exception {
        Method m=type.getDeclaredMethod(name,args);
        if(m.getReturnType()!=returns || Modifier.isStatic(m.getModifiers())!=isStatic) throw new NoSuchMethodException(type.getName()+"."+name);
        m.setAccessible(true); return m;
    }
    private void field(Class<?> type,String name,Class<?> expected) throws Exception {
        Field f=requestField(type,name,expected);
        fields.put(name,f);
    }
    static Field requestField(Class<?> type,String name,Class<?> expected) throws Exception {
        Field f=com.yuka.gamecube.tools.hooks.Reflect.field(type,name);
        if(f.getType()!=expected || Modifier.isStatic(f.getModifiers())) throw new NoSuchFieldException(name);
        return f;
    }
    Object get(Object object,String name) throws Exception { return fields.get(name).get(object); }
    int number(Object object,String name) throws Exception { return fields.get(name).getInt(object); }
    static Object call(Method method,Object object,Object...args) throws Throwable {
        try { return method.invoke(object,args); }
        catch(InvocationTargetException error) { throw error.getCause(); }
    }
    Object manager() throws Throwable { return call(managerInstance,null); }
    Handler handler(Object manager) throws Exception { return (Handler)get(manager,"mHander"); }
    Object display() throws Throwable { return call(physicalDisplay,call(displayManagerInstance,null),0); }
    Object current(Object display) throws Throwable { return call(getCurrent,display); }
    Object focus(Object display) throws Throwable { return call(getFocus,display); }
    List<?> windows(Object manager) throws Throwable { return (List<?>)call(windows,manager,0); }
    void cancelCurrent(Object display) throws Throwable { call(setCurrent,display,(Object)null); }
    void updateConfig() throws Throwable { call(updateConfig,call(adjusterInstance,null)); }
    boolean selected(DisplayRatePolicy.Selection selection,Object manager,Object display) throws Throwable {
        if((Integer)call(getDisplayId,display)!=0 || !Boolean.TRUE.equals(call(screenOn,display)) || !supports144(display)) return false;
        // Global mode also covers animation gaps with no focused application window.
        if(selection.global()) return true;
        Object focus=focus(display);
        for(Object record:(List<?>)call(windows,manager,0)) {
            if(!selection.packages().containsKey((String)get(record,"pkgName"))) continue;
            if(DisplayRatePolicy.reason(selection,window(display,record,focus)).equals("requested")) return true;
        }
        return false;
    }
    private boolean supports144(Object display) throws Throwable {
        Object modes=call(supportedModes,display);
        if(modes instanceof List<?> list) for(Object item:list) if(item instanceof Display.Mode mode
                && Math.abs(mode.getRefreshRate()-144f)<0.1f) return true;
        return false;
    }
    DisplayRatePolicy.Window window(Object display,Object record,Object focus) throws Throwable {
        Object win=get(record,"win"),process=get(record,"client");
        String pkg=(String)get(record,"pkgName");
        WindowManager.LayoutParams lp=(WindowManager.LayoutParams)call(attrs,call(wmsInstance,null),win);
        boolean supports=supports144(display);
        return new DisplayRatePolicy.Window(pkg,number(process,"mUid"),lp==null?-1:lp.type,
                win==focus && Boolean.TRUE.equals(call(processFocus,process)),
                Boolean.TRUE.equals(call(processForeground,process)) && Boolean.TRUE.equals(call(windowVisible,win)),
                Boolean.TRUE.equals(call(processAlive,process)),
                DisplayRatePolicy.applicationMode((Integer)call(windowMode,win)),Boolean.TRUE.equals(get(record,"startingWindow")),
                pkg!=null && pkg.equals(get(process,"mPkgName")),(Integer)call(getDisplayId,display),
                Boolean.TRUE.equals(call(screenOn,display)),supports);
    }
    void request144(Object request) throws Exception {
        int fps=number(request,"reqFps"),priority=number(request,"reqPriority");
        int min=number(request,"touchMinFps"),max=number(request,"touchMaxFps");
        Object reason=get(request,"reqReason");
        try {
            fields.get("reqFps").setInt(request,144);
            fields.get("touchMinFps").setInt(request,144); fields.get("touchMaxFps").setInt(request,144);
            fields.get("reqReason").set(request,"reqFps");
            if(priority<0) fields.get("reqPriority").setInt(request,0);
        } catch(Exception error) {
            fields.get("reqFps").setInt(request,fps); fields.get("reqPriority").setInt(request,priority);
            fields.get("touchMinFps").setInt(request,min); fields.get("touchMaxFps").setInt(request,max);
            fields.get("reqReason").set(request,reason); throw error;
        }
    }
}
