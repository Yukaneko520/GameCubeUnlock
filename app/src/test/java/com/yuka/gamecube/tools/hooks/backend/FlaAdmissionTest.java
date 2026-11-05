package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.ArrayList;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlaAdmissionTest {
    private static final class Flag implements FlaAdmission.Io {
        String value="1";
        int reads,result=1;
        boolean reject,failAfterWrite,throwOnWrite;
        final ArrayList<String> writes=new ArrayList<>();
        public String read() {
            reads++;
            if(failAfterWrite && !writes.isEmpty()) throw new IllegalStateException("read unavailable");
            return value;
        }
        public int write(String next) {
            writes.add(next);
            if(!reject) value=next;
            if(throwOnWrite) throw new IllegalStateException("native response unavailable");
            return result;
        }
    }
    @Test public void defaultOffDoesNotEvenReadNativeState() throws Throwable {
        var flag=new Flag();var admission=new FlaAdmission();
        assertTrue(admission.reconcile(false,flag).confirmed());
        assertEquals(0,flag.reads);assertTrue(flag.writes.isEmpty());
    }
    @Test public void ownsOnlyItsDisableAndReleasesOnOff() throws Throwable {
        var flag=new Flag();var admission=new FlaAdmission();
        assertTrue(admission.reconcile(true,flag).confirmed());assertTrue(admission.owned());
        assertTrue(admission.reconcile(true,flag).confirmed());assertEquals(java.util.List.of("0"),flag.writes);
        assertTrue(admission.reconcile(false,flag).confirmed());assertFalse(admission.owned());
        admission.reconcile(false,flag);assertEquals(java.util.List.of("0","1"),flag.writes);
    }
    @Test public void preexistingOffIsNotReenabledAndExternalRestoreIsRespected() throws Throwable {
        var flag=new Flag();flag.value="0";var admission=new FlaAdmission();
        admission.reconcile(true,flag);admission.reconcile(false,flag);
        assertTrue(flag.writes.isEmpty());assertFalse(admission.owned());
        flag.value="1";admission.reconcile(true,flag);flag.value="1";
        assertEquals("superseded",admission.reconcile(false,flag).stage());
        assertEquals(java.util.List.of("0"),flag.writes);
    }
    @Test public void mismatchedReadbackAndNativeErrorsNeverReportSuccess() throws Throwable {
        var flag=new Flag();var admission=new FlaAdmission();flag.reject=true;flag.result=-13;
        assertFalse(admission.reconcile(true,flag).confirmed());
        admission.reconcile(false,flag);assertFalse(admission.owned());
        flag.reject=false;flag.result=-5;
        assertFalse(admission.reconcile(true,flag).confirmed());assertTrue(admission.owned());
        flag.result=1;assertTrue(admission.reconcile(false,flag).confirmed());
    }
    @Test public void partialNativeFailureRetainsCleanupAndRestoreCanRetry() throws Throwable {
        var flag=new Flag();var admission=new FlaAdmission();flag.failAfterWrite=true;
        assertThrows(IllegalStateException.class,()->admission.reconcile(true,flag));assertTrue(admission.owned());
        flag.failAfterWrite=false;flag.reject=true;
        assertFalse(admission.reconcile(false,flag).confirmed());assertTrue(admission.owned());
        flag.reject=false;assertTrue(admission.reconcile(false,flag).confirmed());assertFalse(admission.owned());
        flag.throwOnWrite=true;
        assertThrows(IllegalStateException.class,()->admission.reconcile(true,flag));assertTrue(admission.owned());
        flag.throwOnWrite=false;admission.reconcile(false,flag);assertFalse(admission.owned());
    }
    @Test public void malformedReadIsRejectedBeforeAnyWrite() {
        var flag=new Flag();flag.value="";var admission=new FlaAdmission();
        assertThrows(IllegalStateException.class,()->admission.reconcile(true,flag));assertTrue(flag.writes.isEmpty());
    }
    @Test public void verifiedMasterAndGlobalFlagAreAllRequiredWithoutAppSelection() {
        var selected=ConfigSnapshot.defaults().withGlobal(true,0,false).withFlaDisabled(true);
        assertTrue(FlaAdmission.requested(new BackendPolicy(selected,true,Map.of())));
        assertFalse(FlaAdmission.requested(new BackendPolicy(selected,false,Map.of())));
        assertFalse(FlaAdmission.requested(new BackendPolicy(selected.withGlobal(false,0,false),true,Map.of())));
        assertFalse(FlaAdmission.requested(new BackendPolicy(selected.withFlaDisabled(false),true,Map.of())));
    }
}
