package com.yuka.gamecube.tools.hooks.backend;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlaKernelTest {
    @Test public void reviewedUnameReleaseIsAccepted() {
        FlaAccess.requireKernel("6.1.145-android14-11-maybe-dirty");
    }
    @Test public void unknownOrMissingReleaseCannotAuthorizeTheFlagWrite() {
        for(String release:new String[]{null,"","6.1.145","6.1.124-android14-11-maybe-dirty",
                "6.1.145-android14-11-maybe-dirty-other"," 6.1.145-android14-11-maybe-dirty"})
            assertThrows(IllegalStateException.class,()->FlaAccess.requireKernel(release));
    }
}
