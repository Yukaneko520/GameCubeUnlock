package com.yuka.gamecube.tools.hooks.graphics;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class NativeLibraryDigestTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void eachRomRequiresItsOwnNativeBinary() {
        assertEquals("732f0eaecd724f9bfa7ff43c5f6c18f7d88b6caf7e3c96931df09a8a0f950958",NativeLibraryDigest.expected(35));
        assertEquals("3eba69fb97bda37f0bd1559c8d77c40798f8073a54971b30ddbdd2e1cb5adb9a",NativeLibraryDigest.expected(36));
        assertNotEquals(NativeLibraryDigest.expected(35),NativeLibraryDigest.expected(36));
        assertThrows(IllegalArgumentException.class,()->NativeLibraryDigest.expected(34));
        assertThrows(IllegalArgumentException.class,()->NativeLibraryDigest.expected(37));
    }
    @Test public void extractedAndCompressedApkEntryUseIdenticalBytes() throws Exception {
        var library=folder.newFile("libVSR.so"); Files.writeString(library.toPath(),"abc");
        var apk=folder.newFile("GameLib.apk");
        try(var zip=new ZipOutputStream(new FileOutputStream(apk))) {
            zip.putNextEntry(new ZipEntry("lib/arm64-v8a/libVSR.so"));zip.write("abc".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        String expected="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertEquals(expected,NativeLibraryDigest.sha256(library.getAbsolutePath()));
        assertEquals(expected,NativeLibraryDigest.sha256(apk.getAbsolutePath()+"!/lib/arm64-v8a/libVSR.so"));
    }
    @Test public void unrelatedRelativeAndOtherAbiEntriesAreRejected() throws Exception {
        assertThrows(Exception.class,()->NativeLibraryDigest.sha256("libVSR.so"));
        assertThrows(Exception.class,()->NativeLibraryDigest.sha256(folder.newFile("other.so").getAbsolutePath()));
        assertThrows(Exception.class,()->NativeLibraryDigest.sha256(folder.newFile("base.apk").getAbsolutePath()+"!/lib/x86/libVSR.so"));
    }
    @Test public void concurrentNativeInstallationMustRetryRatherThanClosePermanently() {
        assertTrue(NativeSrHooks.installPending(0));
        assertTrue(NativeSrHooks.installPending(2));
        for(int terminal:new int[]{1,-1,-2,-3,-4,-5}) assertFalse(NativeSrHooks.installPending(terminal));
    }
}
