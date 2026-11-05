package com.yuka.gamecube.tools.hooks.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FlaOwnershipTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String BOOT="11111111-1111-4111-8111-111111111111";
    private Path record() { return temp.getRoot().toPath().resolve("private/fla-owner"); }
    @Test public void noRecordDoesNotRequireNativeBootRead() throws Throwable {
        var state=new FlaOwnership(record(),()->{throw new AssertionError("unexpected JNI read");});
        assertFalse(state.resume());assertFalse(Files.exists(record()));
    }
    @Test public void frameworkRestartCanRestoreOwnedOffWithoutStealingForeignOff() throws Throwable {
        var state=new FlaOwnership(record(),()->BOOT);state.acquire();
        var afterRestart=new FlaOwnership(record(),()->BOOT);
        assertTrue(afterRestart.resume());
        var admission=new FlaAdmission();admission.resumeOwned();
        var flag=new FlaAdmission.Io() {
            String value="0";
            public String read() { return value; }
            public int write(String next) { value=next;return 1; }
        };
        assertTrue(admission.reconcile(false,flag).confirmed());assertEquals("1",flag.read());
        state.clear();assertFalse(afterRestart.resume());
    }
    @Test public void hardRebootInvalidatesRecordAndMalformedStateCannotAuthorizeWrites() throws Throwable {
        var state=new FlaOwnership(record(),()->BOOT);state.acquire();
        assertFalse(new FlaOwnership(record(),()->"00000000-0000-0000-0000-000000000000").resume());
        assertFalse(Files.exists(record()));
        Files.writeString(record(),"invalid");
        assertThrows(Exception.class,state::resume);
        assertThrows(Exception.class,()->new FlaOwnership(record(),()->"not-a-uuid").acquire());
    }
}
