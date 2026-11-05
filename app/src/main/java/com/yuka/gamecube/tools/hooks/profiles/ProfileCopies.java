package com.yuka.gamecube.tools.hooks.profiles;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProfileCopies {
    static final String FRAME = "com.vivo.common.frameintersupportlist.entity.a";
    private ProfileCopies() {}
    static Constructor<?> frameConstructor(ClassLoader loader) throws Exception {
        return frameConstructor(loader,FRAME);
    }
    static Constructor<?> frameConstructor(ClassLoader loader,String owner) throws Exception {
        if(owner.equals("q6.a")) return Class.forName(owner,false,loader).getDeclaredConstructor(String.class,
                String.class,String.class,int.class,boolean.class,List.class,List.class,Map.class);
        return Class.forName(owner, false, loader).getDeclaredConstructor(String.class, String.class,
                String.class, int.class, boolean.class, List.class, List.class, Map.class,
                double.class, double.class, int.class, int.class);
    }
    static Constructor<?> srConstructor(ClassLoader loader) throws Exception {
        return srConstructor(loader,"y7.e",false);
    }
    static Constructor<?> srConstructor(ClassLoader loader,String owner,boolean legacy) throws Exception {
        var args=new ArrayList<Class<?>>(List.of(String.class, String.class,
                String.class, int.class, int.class, int.class, int.class, int.class,
                double.class, double.class, double.class, double.class, int.class, int.class,
                int.class, int.class));
        if(!legacy) args.add(String.class);
        return Class.forName(owner,false,loader).getDeclaredConstructor(args.toArray(Class<?>[]::new));
    }
    static Object frame(Object donor, String target) throws Throwable {
        return frame(donor, target, frameConstructor(donor.getClass().getClassLoader(),
                donor.getClass().getName().equals("q6.a")?"q6.a":donor.getClass().getName().equals("h6.a")?"h6.a":FRAME));
    }
    static Object frame(Object donor, String target, Constructor<?> constructor) throws Throwable {
        if(constructor.getParameterCount()==8) return constructor.newInstance(target,
                read(donor,"f"),read(donor,"b"),read(donor,"e"),read(donor,"i"),
                triples((List<?>)read(donor,"a")),triples((List<?>)read(donor,"g")),tips((Map<?,?>)read(donor,"c")));
        return constructor.newInstance(target,
                read(donor, "g"), read(donor, "b"), read(donor, "f"), read(donor, "m"),
                triples((List<?>) read(donor, "a")), triples((List<?>) read(donor, "i")),
                tips((Map<?, ?>) read(donor, "d")), read(donor, "j"), read(donor, "c"),
                read(donor, "h"), read(donor, "l"));
    }
    static Object sr(Object donor, String target) throws Throwable {
        boolean legacy=donor.getClass().getName().equals("v6.e");
        return sr(donor, target, srConstructor(donor.getClass().getClassLoader(),legacy?"v6.e":"y7.e",legacy));
    }
    static Object sr(Object donor, String target, Constructor<?> constructor) throws Throwable {
        var args=new ArrayList<Object>(java.util.Arrays.asList(target,
                read(donor, "j"), read(donor, "a"), read(donor, "q"), read(donor, "d"),
                read(donor, "e"), read(donor, "k"), read(donor, "l"), read(donor, "n"),
                read(donor, "b"), read(donor, "o"), read(donor, "c"), read(donor, "f"),
                read(donor, "g"), read(donor, "h"), read(donor, "m")));
        if(constructor.getParameterCount()==17) args.add(read(donor,"r"));
        else if(constructor.getParameterCount()!=16) throw new IllegalArgumentException("Unknown SR constructor");
        return constructor.newInstance(args.toArray());
    }
    private static Object read(Object profile, String getter) throws Throwable {
        return ProfileAccess.call(profile, getter);
    }
    private static List<Object> triples(List<?> source) throws Throwable {
        if (source == null) return null;
        List<Object> copy = new ArrayList<>();
        for (Object triple : source) {
            copy.add(triple.getClass().getConstructor(int.class, int.class, int.class)
                    .newInstance(read(triple, "c"), read(triple, "a"), read(triple, "b")));
        }
        return copy;
    }
    private static Map<Object, Map<Object, Object>> tips(Map<?, ?> source) {
        if (source == null) return null;
        Map<Object, Map<Object, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() == null ? null
                    : new LinkedHashMap<>((Map<?, ?>) entry.getValue()));
        }
        return copy;
    }
}
