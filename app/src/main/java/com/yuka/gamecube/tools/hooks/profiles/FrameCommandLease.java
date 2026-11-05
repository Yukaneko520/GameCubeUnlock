package com.yuka.gamecube.tools.hooks.profiles;

/** OS5 OFF is global. Close it only while the exact command emitted by our controller still owns the setting. */
final class FrameCommandLease {
    private Object controller,profile;
    private String command;
    private int pid;
    static boolean matches(String command,int index,int pid) {
        if(command==null || command.length()>128 || pid<=0 || index<0) return false;
        String[] fields=command.split(":",-1);if(fields.length!=5) return false;
        try {
            return fields[0].equals("1") && Integer.parseInt(fields[1])==index && Integer.parseInt(fields[2])==pid
                    && Integer.parseInt(fields[3])>0 && Integer.parseInt(fields[4])>=Integer.parseInt(fields[3]);
        } catch(NumberFormatException invalid) {return false;}
    }
    synchronized void observe(Object controller,Object profile,int index,int pid,String command) {
        if(controller==null || profile==null || !matches(command,index,pid)) {clear();return;}
        this.controller=controller;this.profile=profile;this.pid=pid;this.command=command;
    }
    synchronized boolean owns(Object controller,Object profile,int pid,String current) {
        return this.controller==controller && this.profile==profile && this.pid==pid && command!=null && command.equals(current);
    }
    synchronized void clear() {controller=null;profile=null;command=null;pid=0;}
}
