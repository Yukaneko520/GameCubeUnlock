package com.yuka.gamecube.tools.hooks.graphics;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/** Worker-only; supports both extracted libraries and linker APK!/lib paths. */
final class NativeLibraryDigest {
    static final String TARGET="3eba69fb97bda37f0bd1559c8d77c40798f8073a54971b30ddbdd2e1cb5adb9a";
    static String expected(int sdk) {
        return switch(sdk) {
            case 35 -> "732f0eaecd724f9bfa7ff43c5f6c18f7d88b6caf7e3c96931df09a8a0f950958";
            case 36 -> TARGET;
            default -> throw new IllegalArgumentException("Native SR baseline unknown: "+sdk);
        };
    }
    static String sha256(String path) throws Exception {
        if(path==null || path.length()>4096) throw new IOException("Invalid library path");
        int split=path.indexOf("!/");
        if(split>=0) {
            String archive=path.substring(0,split),entry=path.substring(split+2);
            if(!new File(archive).isAbsolute() || !entry.equals("lib/arm64-v8a/libVSR.so")) throw new IOException("Unexpected APK library entry");
            try(ZipFile zip=new ZipFile(archive)) {
                var item=zip.getEntry(entry);
                if(item==null) throw new IOException("Library not present in APK");
                try(InputStream input=zip.getInputStream(item)) { return digest(input); }
            }
        }
        File file=new File(path);
        if(!file.isAbsolute() || !file.getName().equals("libVSR.so")) throw new IOException("Unexpected library file");
        try(InputStream input=new FileInputStream(file)) { return digest(input); }
    }
    private static String digest(InputStream input) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[16384];
        long total=0;
        for(int n;(n=input.read(buffer))>=0;) {
            total+=n;
            if(total>64_000_000) throw new IOException("Library exceeds verified size limit");
            sha.update(buffer,0,n);
        }
        return HexFormat.of().formatHex(sha.digest());
    }
    private NativeLibraryDigest() {}
}
