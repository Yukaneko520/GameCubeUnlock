package com.yuka.gamecube.tools.core;

public final class DevicePolicy {
    public static final String APK_SHA256=com.yuka.gamecube.tools.compat.TargetProfiles.OS6.gameCubeSha();
    public static final String TARGET="com.vivo.gamecube";
    private DevicePolicy() {}
    public static boolean deviceAllowed(String publicModel,String internalModel,int sdk,String displayIc) {
        return com.yuka.gamecube.tools.compat.TargetProfiles.device(publicModel,internalModel,sdk,displayIc)!=null;
    }
    public static boolean processAllowed(String process) { return (TARGET+":assistantui").equals(process)||(TARGET+":daemon").equals(process); }
    public static boolean apkAllowed(long code,String name,String hash) {
        return com.yuka.gamecube.tools.compat.TargetProfiles.OS6.apkMatches(code,name,hash);
    }
}
