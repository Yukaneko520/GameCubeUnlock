package com.yuka.gamecube.tools.compat;

import android.content.Context;
import com.yuka.gamecube.tools.core.Compatibility;
import com.yuka.gamecube.tools.core.DevicePolicy;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/** Worker-only package/framework verification shared by manager, GameCube and system policy. */
public final class ArtifactIdentity {
    private static final ConcurrentHashMap<String,String> hashes=new ConcurrentHashMap<>();
    private ArtifactIdentity() {}
    private static String hash(File file) throws Exception {
        if(!file.isFile() || !file.canRead()) throw new IllegalStateException("Unreadable dependency: "+file.getName());
        String key=file.getAbsolutePath()+":"+file.length()+":"+file.lastModified();
        String known=hashes.get(key);
        if(known!=null) return known;
        String value=Compatibility.sha256(file); hashes.put(key,value); return value;
    }
    /** Empty means fully matched, otherwise the first exact dependency rejection. */
    public static String rejectReason(Context context,TargetBaseline baseline) throws Exception {
        var pm=context.getPackageManager();
        var cube=pm.getPackageInfo(DevicePolicy.TARGET,0);
        if(!baseline.apkMatches(cube.getLongVersionCode(),cube.versionName,hash(new File(cube.applicationInfo.sourceDir))))
            return "gamecube_apk_mismatch";
        for(var entry:baseline.frameworks().entrySet())
            if(!entry.getValue().equals(hash(new File("/system/framework",entry.getKey()))))
                return "framework_mismatch:"+entry.getKey();
        for(var entry:baseline.packages().entrySet()) {
            var dependency=pm.getPackageInfo(entry.getKey(),0);
            String digest=hash(new File(dependency.applicationInfo.sourceDir));
            if("com.vivo.gamewatch".equals(entry.getKey()) && !baseline.gameWatchMatches(
                    dependency.getLongVersionCode(),dependency.versionName,digest))
                return "gamewatch_apk_mismatch";
            if(!entry.getValue().equals(digest))
                return "package_mismatch:"+entry.getKey();
        }
        return "";
    }
}
