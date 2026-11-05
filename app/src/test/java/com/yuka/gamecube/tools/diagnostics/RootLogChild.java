package com.yuka.gamecube.tools.diagnostics;

/** Real child process used to exercise pipe drainage, stdin EOF and timeout handling. */
public final class RootLogChild {
    public static void main(String[] args) throws Exception {
        switch(args[0]) {
            case "eof" -> {
                while(System.in.read()!=-1) {}
                System.out.println("hook record after stdin EOF");
            }
            case "large" -> System.out.print("x".repeat(200_000));
            case "failure" -> {System.err.println("captured stderr detail");System.exit(7);}
            case "timeout" -> {System.out.println("record before timeout");System.out.flush();Thread.sleep(20000);}
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
