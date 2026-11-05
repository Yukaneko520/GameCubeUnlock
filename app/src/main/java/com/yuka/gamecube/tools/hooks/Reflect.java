package com.yuka.gamecube.tools.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public final class Reflect {
    private Reflect() {}
    public static Field field(Class<?> type,String name) throws NoSuchFieldException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch(NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    public static Method method(Class<?> type,String name,Class<?>...args) throws NoSuchMethodException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
            try { Method m=c.getDeclaredMethod(name,args); m.setAccessible(true); return m; }
            catch(NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    public static Object get(Object obj,String name) throws ReflectiveOperationException {
        return field(obj instanceof Class<?> c?c:obj.getClass(),name).get(obj instanceof Class<?>?null:obj);
    }
    public static void set(Object obj,String name,Object value) throws ReflectiveOperationException {
        field(obj instanceof Class<?> c?c:obj.getClass(),name).set(obj instanceof Class<?>?null:obj,value);
    }
    public static Object call(Object receiver,String name,Object...args) throws Throwable {
        return invoke(receiver.getClass(),receiver,name,args);
    }
    public static Object staticCall(Class<?> type,String name,Object...args) throws Throwable { return invoke(type,null,name,args); }
    private static final Map<Class<?>,Class<?>> BOX=Map.of(boolean.class,Boolean.class,int.class,Integer.class,long.class,Long.class,
            float.class,Float.class,double.class,Double.class,short.class,Short.class,byte.class,Byte.class,char.class,Character.class);
    private static Object invoke(Class<?> type,Object receiver,String name,Object[] args) throws Throwable {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) for(Method m:c.getDeclaredMethods()) {
            if(!m.getName().equals(name)||m.getParameterCount()!=args.length) continue;
            Class<?>[] params=m.getParameterTypes(); boolean match=true;
            for(int i=0;i<args.length;i++) {
                if(args[i]==null) { if(params[i].isPrimitive()) match=false; }
                else if(!BOX.getOrDefault(params[i],params[i]).isInstance(args[i])) match=false;
            }
            if(!match) continue;
            m.setAccessible(true);
            try { return m.invoke(receiver,args); } catch(InvocationTargetException e) { throw e.getCause(); }
        }
        throw new NoSuchMethodException(type.getName()+"."+name+"/"+args.length);
    }
}
