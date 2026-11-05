package com.yuka.gamecube.tools.core;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.yuka.gamecube.tools.compat.ArtifactIdentity;
import com.yuka.gamecube.tools.compat.TargetBaseline;
import com.yuka.gamecube.tools.compat.TargetProfiles;

public final class Compatibility {
    public record Result(TargetBaseline baseline,String reason) {
        public boolean allowed() { return baseline!=null && reason.isEmpty(); }
        public String message() {
            if(allowed()) return "Compatible: "+baseline.id()+" • GameCube "+baseline.gameCubeVersion()
                    +" • GameWatch "+baseline.gameWatchVersion();
            return "Unsupported: "+reason;
        }
    }
    private Compatibility() {}
    public static String diagnosticReason(String check) {
        if(check.startsWith("Compatible:")) return "compatible";
        if(check.startsWith("Unsupported:")) return check.substring("Unsupported:".length()).trim();
        return "identity_unverified";
    }
    public static String property(String key) throws Exception {
        try { return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,key); }
        catch(ReflectiveOperationException | LinkageError e) {
            Process p=new ProcessBuilder("/system/bin/getprop",key).redirectErrorStream(true).start();
            String s=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
            if(p.waitFor()!=0) throw new IllegalStateException("Cannot read "+key); return s;
        }
    }
    public static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(file)) { byte[] buffer=new byte[65536]; int n; while((n=in.read(buffer))!=-1) digest.update(buffer,0,n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static Result verify(Context context) {
        try {
            TargetBaseline baseline=TargetProfiles.device(Build.MODEL,property("ro.vivo.product.model"),
                    Build.VERSION.SDK_INT,property("persist.vivo.displayic.ver"));
            if(baseline==null) return new Result(null,"device_baseline_unknown");
            if(android.os.Process.myUid()/100000!=0) return new Result(null,"primary_user_required");
            return new Result(baseline,ArtifactIdentity.rejectReason(context,baseline));
        } catch(Exception e) { return new Result(null,"identity_unavailable:"+e.getClass().getSimpleName()); }
    }
    public static String check(Context context) { return verify(context).message(); }
}
