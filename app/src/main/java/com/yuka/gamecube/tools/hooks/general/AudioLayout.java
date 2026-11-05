package com.yuka.gamecube.tools.hooks.general;

record AudioLayout(String controller,String command,String apply,String alternate,String resolverOwner,
                   String resolverMethod,String preset,String equalizer,String enhancer,String common,
                   String version,String capable,String region,String spatializer) {
    static AudioLayout forSdk(int sdk) {
        return switch(sdk) {
            case 34 -> new AudioLayout("b8.c","a","e","f",
                    "com.vivo.gameassistant.gamecustomsound.GameSoundSupportGame","b",
                    "com.vivo.gameassistant.gamecustomsound.GameSoundSupportGame","c8.b","c8.a","k","c","U","H",null);
            case 35 -> new AudioLayout("e8.c","a","e","f",
                    "com.vivo.gameassistant.gamecustomsound.GameSoundSupportGame","fromPkgName",
                    "com.vivo.gameassistant.gamecustomsound.GameSoundSupportGame","f8.b","f8.a","l","d","o0","P",null);
            case 36 -> new AudioLayout("com.vivo.gameassistant.gamecustomsound.c","T","Y","Z",
                    "com.vivo.gameassistant.gamecustomsound.e","a","com.vivo.gameassistant.gamecustomsound.d",
                    "j9.b","j9.a","p","e","J0","c0","t1");
            default -> throw new IllegalArgumentException("Unsupported audio SDK: "+sdk);
        };
    }
}
