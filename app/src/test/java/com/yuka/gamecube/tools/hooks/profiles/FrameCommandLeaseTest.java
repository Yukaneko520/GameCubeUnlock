package com.yuka.gamecube.tools.hooks.profiles;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameCommandLeaseTest {
    @Test public void globalOffCannotCloseAnotherPidControllerProfileOrNewPayload() {
        Object controller=new Object(),profile=new Object();var lease=new FrameCommandLease();
        lease.observe(controller,profile,3,45,"1:3:45:60:120");
        assertTrue(lease.owns(controller,profile,45,"1:3:45:60:120"));
        assertFalse(lease.owns(controller,profile,46,"1:3:46:60:120"));
        assertFalse(lease.owns(new Object(),profile,45,"1:3:45:60:120"));
        assertFalse(lease.owns(controller,new Object(),45,"1:3:45:60:120"));
        assertFalse(lease.owns(controller,profile,45,"1:3:45:45:90"));
        lease.observe(null,null,-1,0,"0:-1:0:0:0");
        assertFalse(lease.owns(controller,profile,45,"1:3:45:60:120"));
    }
    @Test public void malformedDisabledOrUnboundPayloadsNeverCreateOwnership() {
        for(String command:new String[]{"","1:3:45:0:120","0:3:45:60:120","1:3:45:90:60","1:3:46:60:120",
                "1:4:45:60:120","1:3:45:60:120:extra","1:3:45:60:NaN"})
            assertFalse(command,FrameCommandLease.matches(command,3,45));
        assertFalse(FrameCommandLease.matches(null,3,45));
        assertTrue(FrameCommandLease.matches("1:3:45:48:144",3,45));
    }
}
