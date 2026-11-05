package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.os.Handler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Set;

/** Exact member preflight does not initialize the native controller. Reads start after systemReady. */
final class BackendAccess {
    private static final String OWNER="com.vivo.services.game.sr.VSRController";
    final Method ready,predicate,update,alternative;
    private final BackendLayout layout;
    private final HashMap<String,Field> fields=new HashMap<>();
    private final Field topUser;
    BackendAccess(ClassLoader loader,int sdk) throws ReflectiveOperationException {
        layout=BackendLayout.forSdk(sdk);
        Class<?> type=Class.forName(OWNER,false,loader);
        ready=method(type,"systemReady",void.class);
        predicate=method(type,"isVSRPostProcessingWhitelistApp",boolean.class,String.class);
        alternative=method(type,layout.alternativePredicate(),boolean.class,String.class);
        update=method(type,"updateGameVSRState",boolean.class,int.class,int[].class,int.class,boolean.class);
        field(type,"SUPPORT_SW_VSR",boolean.class,true);
        field(type,"SUPPORT_VSR_POST_PROCESSING",boolean.class,true);
        for(String name:layout.competingCapabilities()) field(type,name,boolean.class,true);
        field(type,"mContext",Context.class,false);
        field(type,"mH",Class.forName(OWNER+"$H",false,loader),false);
        Class<?> service=Class.forName("com.vivo.services.game.GameManagerService",false,loader);
        field(type,"mService",service,false);
        topUser=exact(service,"mTopFullscreenAppUserId",int.class,false);
        for(String n:new String[]{"mCurVSRUserId","mVSRPostProcessingRunning"})
            field(type,n,int.class,false);
        for(String n:layout.competingState()) field(type,n,int.class,false);
        field(type,"mCurVSRPackage",String.class,false);
        Class<?> arraySet=Class.forName("android.util.ArraySet",false,loader);
        if(layout.disabledPackages()!=null) field(type,layout.disabledPackages(),arraySet,false);
        field(type,"mVSRFailedApp",arraySet,false);
    }
    private static Method method(Class<?> type,String name,Class<?> result,Class<?>...args) throws ReflectiveOperationException {
        Method method=type.getDeclaredMethod(name,args);
        if(method.getReturnType()!=result || !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(name);
        method.setAccessible(true); return method;
    }
    private static Field exact(Class<?> type,String name,Class<?> expected,boolean isStatic) throws ReflectiveOperationException {
        Field field=type.getDeclaredField(name);
        if(field.getType()!=expected || Modifier.isStatic(field.getModifiers())!=isStatic) throw new NoSuchFieldException(name);
        field.setAccessible(true); return field;
    }
    private void field(Class<?> type,String name,Class<?> expected,boolean isStatic) throws ReflectiveOperationException {
        fields.put(name,exact(type,name,expected,isStatic));
    }
    Object get(String name,Object receiver) throws IllegalAccessException { return fields.get(name).get(receiver); }
    boolean sw() throws IllegalAccessException { return fields.get("SUPPORT_SW_VSR").getBoolean(null); }
    boolean pp() throws IllegalAccessException { return fields.get("SUPPORT_VSR_POST_PROCESSING").getBoolean(null); }
    boolean ppOnly() throws IllegalAccessException {
        if(!sw() || !pp()) return false;
        for(String name:layout.competingCapabilities())
            if(fields.get(name).getBoolean(null)) return false;
        return true;
    }
    String capabilities() throws IllegalAccessException {
        StringBuilder result=new StringBuilder();
        var names=new java.util.ArrayList<>(java.util.List.of("SUPPORT_SW_VSR","SUPPORT_VSR_POST_PROCESSING"));
        names.addAll(layout.competingCapabilities());
        for(String name:names)
            result.append(name).append('=').append(fields.get(name).getBoolean(null)).append(' ');
        return result.toString();
    }
    int user(Object receiver) throws IllegalAccessException { return topUser.getInt(get("mService",receiver)); }
    boolean disabled(Object receiver,String pkg) throws IllegalAccessException {
        return (layout.disabledPackages()!=null && contains(get(layout.disabledPackages(),receiver),pkg))
                || contains(get("mVSRFailedApp",receiver),pkg+",0");
    }
    private static boolean contains(Object value,String key) {
        if(!(value instanceof Set<?> set)) throw new IllegalStateException("Native set unavailable");
        synchronized(set) { return set.contains(key); }
    }
    BackendLease.Session session(Object receiver) throws IllegalAccessException {
        var competing=new java.util.LinkedHashMap<String,Integer>();
        for(String name:layout.competingState()) competing.put(name,fields.get(name).getInt(receiver));
        return new BackendLease.Session(receiver,(String)get("mCurVSRPackage",receiver),
                fields.get("mCurVSRUserId").getInt(receiver),fields.get("mVSRPostProcessingRunning").getInt(receiver),
                competing);
    }
    Context context(Object receiver) throws IllegalAccessException { return (Context)get("mContext",receiver); }
    Handler handler(Object receiver) throws IllegalAccessException { return (Handler)get("mH",receiver); }
}
