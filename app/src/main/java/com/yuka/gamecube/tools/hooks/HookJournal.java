package com.yuka.gamecube.tools.hooks;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Bounded app-private fallback when Android/LSPosed log sinks do not retain hook records. */
public final class HookJournal implements AutoCloseable {
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS",Locale.ROOT);
    private final Supplier<File> directory;
    private final String name;
    private final int maxBytes;
    private final ThreadPoolExecutor writer;
    public HookJournal(Supplier<File> directory,String name,int maxBytes) {
        if(name==null || !name.matches("gamecube-tools-[a-z]+\\.log") || maxBytes<256)
            throw new IllegalArgumentException("Invalid hook journal destination");
        this.directory=directory;this.name=name;this.maxBytes=maxBytes;
        writer=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),task->{
            Thread thread=new Thread(task,"GameCubeToolsJournal");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.DiscardOldestPolicy());
        writer.allowCoreThreadTimeOut(true);
    }
    public void record(long time,int pid,int tid,char level,String message,Throwable error) {
        try { writer.execute(()->{
            try {
                String text=message;
                if(error!=null) {
                    StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));
                    text+="\n"+stack;
                }
                String prefix=FORMAT.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
                        +String.format(Locale.ROOT," %5d %5d %c GameCubeUnlock: ",pid,tid,level);
                StringBuilder lines=new StringBuilder();
                for(String line:text.split("\\R")) lines.append(prefix).append(line).append('\n');
                byte[] bytes=lines.toString().getBytes(StandardCharsets.UTF_8);
                if(bytes.length>maxBytes) {
                    int keep=(maxBytes-64)/8;
                    String head=lines.substring(0,Math.min(keep,lines.length()));
                    String tail=lines.substring(Math.max(0,lines.length()-keep));
                    bytes=(head+"\n[record truncated]\n"+tail).getBytes(StandardCharsets.UTF_8);
                }
                File folder=directory.get();
                File active=new File(folder,name),previous=new File(folder,name+".old");
                if(active.length()+bytes.length>maxBytes && active.isFile())
                    Files.move(active.toPath(),previous.toPath(),StandardCopyOption.REPLACE_EXISTING);
                if(active.createNewFile()) {
                    active.setReadable(false,false);active.setWritable(false,false);
                    active.setReadable(true,true);active.setWritable(true,true);
                }
                Files.write(active.toPath(),bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            } catch(Throwable unavailable) { /* Logging must not interrupt an OEM controller. */ }
        }); } catch(RuntimeException closed) { /* Host shutdown can race the last callback. */ }
    }
    @Override public void close() {
        writer.shutdown();
        try { writer.awaitTermination(5,TimeUnit.SECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
