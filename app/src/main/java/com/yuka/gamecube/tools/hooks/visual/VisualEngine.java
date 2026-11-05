package com.yuka.gamecube.tools.hooks.visual;

import java.util.Arrays;

/** Result-aware Iris dispatch; synthetic Java PQ status is never accepted as native success. */
public final class VisualEngine {
    public interface Device {
        Reply read(int type,int count) throws Throwable;
        int write(int type,int[] values) throws Throwable;
        default void settle() throws Throwable {}
    }
    public record Reply(int code,int[] values) {
        public Reply { values=values==null?new int[0]:values.clone(); }
        @Override public int[] values() { return values.clone(); }
    }
    public enum State { VERIFIED,UNVERIFIED,FAILED }
    public record Result(State state,String stage,int code,int[] actual) {
        public Result { actual=actual.clone(); }
        @Override public int[] actual() { return actual.clone(); }
    }
    private final Device device;
    public VisualEngine(Device device) { this.device=device; }
    private static boolean one(Reply r,int expected) {
        return r.code()==0 && r.values.length==1 && r.values[0]==expected;
    }
    public Result apply(VisualCommands.Command command) throws Throwable {
        Reply before=device.read(56,1);
        if(before.code()!=0 || before.values.length!=1) return result(State.FAILED,"mode_read",before);
        if(before.values[0]!=0 && before.values[0]!=1) return result(State.FAILED,"mode_unsupported",before);
        if(before.values[0]!=0) {
            int code=device.write(56,new int[]{0});
            if(code!=0) return new Result(State.FAILED,"mode_write",code,new int[0]);
            device.settle();
        }
        Reply active=device.read(56,1);
        if(!one(active,0)) return result(State.FAILED,"mode_not_active",active);
        int code=device.write(command.type(),command.values());
        if(code!=0) {
            rollbackMode(before.values[0]);
            return new Result(State.FAILED,"effect_write",code,new int[0]);
        }
        device.settle();
        Reply read=device.read(command.type(),command.type()==74?4:1);
        int[] expected=command.type()==74?command.values():new int[]{command.values()[1]};
        if(read.code()==0 && !Arrays.equals(read.values,expected)) {
            device.settle();read=device.read(command.type(),command.type()==74?4:1);
        }
        if(read.code()==0 && Arrays.equals(read.values,expected)) {
            Reply mode=device.read(56,1);
            if(!one(mode,0)) return result(State.FAILED,"mode_changed",mode);
            return result(State.VERIFIED,"readback_matched",read);
        }
        // A getter may be unsupported or may replace raw 0 with Java-only PQ marker 74.
        if(read.code()==0) return result(State.FAILED,one(read,74)?"java_pq_only":"effect_mismatch",read);
        return result(State.UNVERIFIED,"readback_unavailable",read);
    }
    private Result result(State state,String stage,Reply r) { return new Result(state,stage,r.code(),r.values); }
    private void rollbackMode(int before) throws Throwable {
        if(before!=0 && one(device.read(56,1),0)) device.write(56,new int[]{before});
    }
}
