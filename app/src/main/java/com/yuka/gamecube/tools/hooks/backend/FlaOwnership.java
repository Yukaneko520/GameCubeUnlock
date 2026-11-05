package com.yuka.gamecube.tools.hooks.backend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** A kernel flag outlives system_server. Persist intent before writing, scoped to the kernel boot ID. */
final class FlaOwnership {
    interface BootId { String read() throws Throwable; }
    private final Path record;
    private final BootId boot;
    FlaOwnership(Path record,BootId boot) { this.record=record;this.boot=boot; }
    private String token() throws Throwable {
        String value=boot.read().trim();
        if(!UUID.fromString(value).toString().equals(value)) throw new IllegalStateException("Invalid kernel boot ID");
        return "v1 "+value;
    }
    boolean resume() throws Throwable {
        if(!Files.exists(record,LinkOption.NOFOLLOW_LINKS)) return false;
        if(!Files.isRegularFile(record,LinkOption.NOFOLLOW_LINKS) || Files.size(record)>64)
            throw new IllegalStateException("Invalid FLA ownership file");
        String saved=new String(Files.readAllBytes(record),StandardCharsets.UTF_8).trim();
        if(!saved.matches("v1 [0-9a-f-]{36}")) throw new IllegalStateException("Malformed FLA ownership record");
        if(saved.equals(token())) return true;
        Files.delete(record);return false;
    }
    void acquire() throws Throwable {
        String value=token();
        Files.createDirectories(record.getParent());
        Path temporary=record.resolveSibling(record.getFileName()+".new");
        for(Path path:new Path[]{record,temporary})
            if(Files.exists(path,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Non-regular FLA ownership file");
        Files.write(temporary,(value+"\n").getBytes(StandardCharsets.UTF_8));
        Files.move(temporary,record,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    void clear() throws Exception { Files.deleteIfExists(record); }
}
