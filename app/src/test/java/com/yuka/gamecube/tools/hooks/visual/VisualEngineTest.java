package com.yuka.gamecube.tools.hooks.visual;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisualEngineTest {
    private static class Device implements VisualEngine.Device {
        int mode=1,filter=0; boolean modeFails,effectFails,staysBypass,synthetic,readUnsupported;
        final ArrayList<Integer> writes=new ArrayList<>();
        public VisualEngine.Reply read(int type,int count) {
            if(type==56) return new VisualEngine.Reply(0,new int[]{mode});
            if(readUnsupported) return new VisualEngine.Reply(-1,new int[0]);
            return new VisualEngine.Reply(0,new int[]{synthetic?74:filter});
        }
        public int write(int type,int[] values) {
            writes.add(type);
            if(type==56) { if(modeFails) return -1; if(!staysBypass) mode=values[0]; }
            else { if(effectFails) return -1; filter=values[1]; }
            return 0;
        }
    }
    private VisualCommands.Command oldMovie() { return new VisualCommands.Command(267,new int[]{3,12}); }
    @Test public void activatesAndReadsNativeModeBeforeSendingEffect() throws Throwable {
        Device d=new Device();var result=new VisualEngine(d).apply(oldMovie());
        assertEquals(VisualEngine.State.VERIFIED,result.state());
        assertEquals(java.util.List.of(56,267),d.writes);assertEquals(0,d.mode);assertEquals(12,d.filter);
    }
    @Test public void bypassDespiteSuccessfulModeCallDoesNotEmitFilter() throws Throwable {
        Device d=new Device();d.staysBypass=true;var result=new VisualEngine(d).apply(oldMovie());
        assertEquals(VisualEngine.State.FAILED,result.state());assertEquals("mode_not_active",result.stage());
        assertEquals(java.util.List.of(56),d.writes);
    }
    @Test public void rejectedModeDoesNotEmitFilter() throws Throwable {
        Device d=new Device();d.modeFails=true;
        assertEquals("mode_write",new VisualEngine(d).apply(oldMovie()).stage());
        assertEquals(java.util.List.of(56),d.writes);
    }
    @Test public void unknownNativeModeIsNotOverridden() throws Throwable {
        Device d=new Device();d.mode=255;
        assertEquals("mode_unsupported",new VisualEngine(d).apply(oldMovie()).stage());
        assertTrue(d.writes.isEmpty());
    }
    @Test public void aReadbackOnlyMarkerIsNotVerificationEvenIfWriteSucceeded() throws Throwable {
        Device d=new Device();d.synthetic=true;var result=new VisualEngine(d).apply(oldMovie());
        assertEquals(0,result.code());assertNotEquals(VisualEngine.State.VERIFIED,result.state());
    }
    @Test public void rejectedEffectRestoresOriginalMode() throws Throwable {
        Device d=new Device();d.effectFails=true;
        assertEquals("effect_write",new VisualEngine(d).apply(oldMovie()).stage());
        assertEquals(1,d.mode);assertEquals(java.util.List.of(56,267,56),d.writes);
    }
    @Test public void neverTreatsJavaPqMarkerAsAppliedPreset() throws Throwable {
        Device d=new Device();d.synthetic=true;
        var result=new VisualEngine(d).apply(oldMovie());
        assertEquals(VisualEngine.State.FAILED,result.state());assertEquals("java_pq_only",result.stage());
    }
    @Test public void unsupportedGetterDoesNotTurnRequestIntoVerifiedSuccess() throws Throwable {
        Device d=new Device();d.readUnsupported=true;
        assertEquals(VisualEngine.State.UNVERIFIED,new VisualEngine(d).apply(oldMovie()).state());
    }
    @Test public void customParametersAreCopiedAndBounded() {
        int[] values={0,128,50,10};var c=new VisualCommands.Command(74,values);values[3]=64;
        assertEquals(10,c.values()[3]);c.values()[3]= -64;assertEquals(10,c.values()[3]);
        assertThrows(IllegalArgumentException.class,()->new VisualCommands.Command(74,new int[]{0,256,50,0}));
        assertFalse(VisualCommands.positive(267,new int[]{3,0}));
        assertFalse(VisualCommands.positive(74,new int[]{0,128,50,0}));
    }
    @Test public void modeResetDuringEffectIsNotReportedAsApplied() throws Throwable {
        Device d=new Device() {
            @Override public int write(int type,int[] values) {
                int code=super.write(type,values);if(type==267) mode=1;return code;
            }
        };
        var result=new VisualEngine(d).apply(oldMovie());
        assertEquals(VisualEngine.State.FAILED,result.state());assertEquals("mode_changed",result.stage());
    }
    @Test public void originalGameCubeProfileAndFilterCommandsRemainAccepted() {
        for(int effect:new int[]{10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26})
            assertTrue(VisualCommands.positive(267,new int[]{3,effect}));
    }
}
