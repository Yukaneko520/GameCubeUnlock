package com.yuka.gamecube.tools.hooks;

import com.yuka.gamecube.tools.hooks.thermal.BenchmarkForeground;
import org.junit.Test;
import static org.junit.Assert.*;

public class BenchmarkForegroundTest {
    @Test public void oldExitCannotClearNewGameAndDeathHandlesMissingPackage() {
        var f=new BenchmarkForeground();
        f.foreground(10001,true,1,"com.example.a");
        f.foreground(10002,true,2,"com.example.b");
        f.foreground(10001,false,1,"com.example.a");
        assertEquals("com.example.b",f.current().pkg());
        f.death(10001,2,"com.example.b");assertNotNull(f.current());
        f.death(10002,2,null);assertNull(f.current());
    }
    @Test public void unknownForegroundAndScreenOffRemoveAdmission() {
        var f=new BenchmarkForeground();
        f.foreground(10001,true,1,"com.example.a");
        f.foreground(10001,true,1,null);assertNull(f.current());
        f.foreground(10001,true,1,"com.example.a");f.clear();assertNull(f.current());
        f.foreground(10001,false,1,"com.example.a");assertNull(f.current());
    }
    @Test public void screenOffReleasesAdmissionButKeepsARevalidatableCandidate() {
        var f=new BenchmarkForeground();
        f.foreground(10001,true,1,"com.example.a");f.remember();var candidate=f.current();
        f.suspend();assertNull(f.current());assertEquals(candidate,f.candidate());
        f.foreground(10001,false,1,"com.example.a");
        assertTrue(f.resume(candidate,true,true,true));assertEquals(candidate,f.current());
    }
    @Test public void wakeCannotRestoreWrongProcessOrDisabledLockedSleepingSession() {
        var f=new BenchmarkForeground();f.foreground(10001,true,1,"com.example.a");f.remember();
        var candidate=f.current();f.suspend();
        assertFalse(f.resume(candidate,false,true,true));
        assertFalse(f.resume(candidate,true,false,true));
        assertFalse(f.resume(candidate,true,true,false));
        assertFalse(f.resume(new BenchmarkForeground.Session(10001,2,"com.example.a"),true,true,true));
        assertFalse(f.resume(new BenchmarkForeground.Session(10002,1,"com.example.a"),true,true,true));
        assertFalse(f.resume(new BenchmarkForeground.Session(10001,1,"com.example.b"),true,true,true));
        assertNull(f.current());
        f.death(10001,1,null);assertNull(f.candidate());assertFalse(f.resume(candidate,true,true,true));
    }
    @Test public void staleWakeCannotReplaceANewerForegroundAndClearWithdrawsBoth() {
        var f=new BenchmarkForeground();f.foreground(10001,true,1,"com.example.a");f.remember();
        var candidate=f.current();f.suspend();f.foreground(10002,true,2,"com.example.b");
        assertFalse(f.resume(candidate,true,true,true));assertEquals("com.example.b",f.current().pkg());
        f.remember();var newer=f.current();f.suspend();
        assertTrue(f.resume(newer,true,true,true));assertEquals("com.example.b",f.current().pkg());
        f.clear();assertNull(f.current());assertNull(f.candidate());
    }
}
