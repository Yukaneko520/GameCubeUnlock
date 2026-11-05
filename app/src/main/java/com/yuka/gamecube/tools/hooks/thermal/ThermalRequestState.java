package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.hooks.Reflect;

public final class ThermalRequestState {
    private ThermalRequestState() {}
    public static long release(Object owner,Object request) throws Throwable {
        return release(owner,request,"m","l");
    }
    public static long release(Object owner,Object request,String handleField,String method) throws Throwable {
        long handle=(Long)Reflect.get(request,handleField);
        if(handle>0) {
            Reflect.call(owner,method,handle,request);
            Reflect.set(request,handleField,-1L); // A failed OEM release must leave the handle available for retry.
        }
        return handle;
    }
    public static void invalidateSelection(Object request) throws Throwable {
        invalidateSelection(request,"o","p");
    }
    public static void invalidateSelection(Object request,String row,String nextRow) throws Throwable {
        Reflect.set(request,row,null);Reflect.set(request,nextRow,null);
        Reflect.set(request,"c",-1); // Force the OEM selector to reevaluate its real sensor value.
    }
}
