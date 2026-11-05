package com.yuka.gamecube.tools.hooks;

import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HookJournalTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void writesCallerThreadtimeAndFullExceptionWithoutAndroidLog() throws Exception {
        var folder=temporary.newFolder();
        try(var journal=new HookJournal(()->folder,"gamecube-tools-assistantui.log",512_000)) {
            journal.record(1_700_000_000_000L,123,456,'E',"feature=visual step=mode_write rc=-257",
                    new IllegalStateException("native refused"));
        }
        String text=Files.readString(folder.toPath().resolve("gamecube-tools-assistantui.log"));
        assertTrue(text.contains("  123   456 E GameCubeUnlock: feature=visual step=mode_write rc=-257"));
        assertTrue(text.contains("IllegalStateException: native refused"));assertTrue(text.contains("HookJournalTest.java:"));
    }
    @Test public void rotatesOnlyItsOwnTwoBoundedFilesAndKeepsLatestRecord() throws Exception {
        var folder=temporary.newFolder();
        try(var journal=new HookJournal(()->folder,"gamecube-tools-assistantui.log",512)) {
            for(int i=0;i<8;i++) journal.record(1000+i,1,2,'I',"record="+i+" "+"x".repeat(200),null);
        }
        var active=folder.toPath().resolve("gamecube-tools-assistantui.log");
        var previous=folder.toPath().resolve("gamecube-tools-assistantui.log.old");
        assertTrue(Files.size(active)<=512);assertTrue(Files.size(previous)<=512);
        assertTrue(Files.readString(active).contains("record=7"));assertEquals(2,folder.list().length);
    }
    @Test public void oversizeUnicodeRecordRemainsReadableAndBounded() throws Exception {
        var folder=temporary.newFolder();
        try(var journal=new HookJournal(()->folder,"gamecube-tools-daemon.log",256)) {
            journal.record(1000,1,2,'I',"Tiếng Việt ".repeat(200),null);
        }
        var active=folder.toPath().resolve("gamecube-tools-daemon.log");
        assertTrue(Files.size(active)<=256);assertTrue(Files.readString(active).contains("[record truncated]"));
    }
    @Test public void invalidDestinationIsRejectedAndIoFailureDoesNotEscapeHook() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new HookJournal(()->temporary.getRoot(),"../other.log",512));
        try(var journal=new HookJournal(()->{throw new SecurityException("denied");},"gamecube-tools-daemon.log",256)) {
            journal.record(1000,1,2,'I',"write unavailable",null);
        }
    }
}
