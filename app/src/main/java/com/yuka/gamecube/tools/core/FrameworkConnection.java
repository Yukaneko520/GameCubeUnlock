package com.yuka.gamecube.tools.core;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FrameworkConnection {
    public static volatile XposedService service;
    private static boolean started;
    private static final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private FrameworkConnection() {}
    public static synchronized void start() {
        if(started) return; started=true;
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            public void onServiceBind(XposedService value) { service=value; notifyListeners(); }
            public void onServiceDied(XposedService value) { if(service==value) service=null; notifyListeners(); }
        });
    }
    public static void add(Runnable listener) { listeners.add(listener); }
    public static void remove(Runnable listener) { listeners.remove(listener); }
    private static void notifyListeners() { for(Runnable r:listeners) r.run(); }
}
