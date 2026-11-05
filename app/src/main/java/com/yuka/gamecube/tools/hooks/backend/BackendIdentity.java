package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import com.yuka.gamecube.tools.core.Compatibility;
import com.yuka.gamecube.tools.core.DevicePolicy;
import java.io.File;
import java.lang.reflect.Method;
import com.yuka.gamecube.tools.compat.TargetBaseline;
import com.yuka.gamecube.tools.compat.TargetProfiles;
import com.yuka.gamecube.tools.compat.ArtifactIdentity;

/** Worker-only baseline verification; never calls the shell-capable property helper. */
final class BackendIdentity {
    private final TargetBaseline baseline=TargetProfiles.descriptors(Build.VERSION.SDK_INT);
    private volatile boolean dirty=true;
    private boolean gameCubeMatch;

    void invalidate() { dirty=true; }
    boolean verify(Context context,java.util.function.Consumer<String> log) throws Exception {
        Method get=Class.forName("android.os.SystemProperties",false,Context.class.getClassLoader())
                .getMethod("get",String.class);
        String model=(String)get.invoke(null,"ro.vivo.product.model"),display=(String)get.invoke(null,"persist.vivo.displayic.ver");
        if(!baseline.deviceMatches(Build.MODEL,model,Build.VERSION.SDK_INT,display)) {
            log.accept("identity reject reason=device model="+Build.MODEL+" product="+model+" sdk="+Build.VERSION.SDK_INT+" displayic="+display);
            return false;
        }
        if(dirty) {
            // Mark before I/O. A package event racing this read leaves dirty=true for the next event job.
            dirty=false; gameCubeMatch=false;
            String rejected=ArtifactIdentity.rejectReason(context,baseline);
            gameCubeMatch=rejected.isEmpty();
            if(!gameCubeMatch) log.accept("identity baseline="+baseline.id()+" reject="+rejected);
        }
        log.accept("identity baseline="+baseline.id()+" dependencies="+gameCubeMatch+" model="+Build.MODEL+" product="+model);
        return gameCubeMatch;
    }
    static boolean userApplication(ApplicationInfo info) {
        return info.uid>=10000 && info.uid<100000
                && (info.flags & (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0;
    }
}
