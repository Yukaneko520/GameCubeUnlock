package com.yuka.gamecube.tools.hooks.backend;

/** Owns only a 1 -> 0 admission change; never edits QoS caches or CPU limits. Owner-thread confined. */
final class FlaAdmission {
    interface Io {
        String read() throws Throwable;
        int write(String value) throws Throwable;
    }
    record Result(String stage,String before,String after,Integer nativeResult,boolean owned) {
        boolean confirmed() { return nativeResult==null || nativeResult>=0 && before!=null && after!=null
                && after.equals(stage.equals("disabled")?"0":"1"); }
    }
    private boolean owned;
    void resumeOwned() { owned=true; }
    boolean owned() { return owned; }
    static boolean requested(BackendPolicy policy) {
        return policy.verified && policy.snapshot.enabled && policy.snapshot.disableFlaGlobal;
    }
    Result reconcile(boolean disable,Io io) throws Throwable {
        if(!disable && !owned) return new Result("inactive",null,null,null,false);
        String before=flag(io.read());
        if(disable) {
            if(before.equals("0")) return new Result(owned?"held":"already_off",before,before,null,owned);
            // Retain cleanup responsibility if the native write succeeds but its response/readback fails.
            owned=true;
            int result=io.write("0");
            String after=flag(io.read());
            return new Result("disabled",before,after,result,owned);
        }
        if(before.equals("1")) {
            owned=false;
            return new Result("superseded",before,before,null,false);
        }
        int result=io.write("1");
        String after=flag(io.read());
        if(after.equals("1")) owned=false;
        return new Result("restored",before,after,result,owned);
    }
    private static String flag(String raw) {
        String value=raw==null?"":raw.trim();
        if(!value.equals("0") && !value.equals("1")) throw new IllegalStateException("Unexpected FLA flag: "+value);
        return value;
    }
}
