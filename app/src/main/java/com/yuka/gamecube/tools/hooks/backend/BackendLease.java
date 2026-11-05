package com.yuka.gamecube.tools.hooks.backend;

/** Only a successful observed native PP enable can create ownership. Caller serializes access. */
final class BackendLease {
    record Session(Object controller,String pkg,int user,int pp,java.util.Map<String,Integer> competing) {
        Session { competing=java.util.Map.copyOf(competing); }
        Session(Object controller,String pkg,int user,int pp,int pipeline,int vnss) {
            this(controller,pkg,user,pp,java.util.Map.of("mVSRPipelineRunning",pipeline,"mVNSSRunning",vnss));
        }
        boolean ppOnly() { return controller!=null && pkg!=null && user==0 && pp>0 && !competing.isEmpty()
                && competing.values().stream().allMatch(value->value==0); }
        boolean same(Session s) {
            return s!=null && controller==s.controller && java.util.Objects.equals(pkg,s.pkg)
                    && user==s.user && pp==s.pp && competing.equals(s.competing);
        }
    }
    record Token(long generation,Session session) {}
    private long generation;
    private int inFlight;
    private Token owned;
    long begin() { inFlight++; return ++generation; }
    Token current() { return owned; }
    void complete(boolean success,boolean enable,String extended,Session actual) {
        complete(generation,success,enable,extended,actual);
    }
    void complete(long call,boolean success,boolean enable,String extended,Session actual) {
        inFlight--;
        if(call!=generation) { owned=null; return; }
        if(success && enable && actual.ppOnly() && actual.pkg.equals(extended)) {
            owned=new Token(generation,actual);
        } else if(owned!=null) {
            owned=owned.session.same(actual)?new Token(generation,actual):null;
        }
    }
    boolean canClose(Token token,Session actual,boolean stock) {
        return inFlight==0 && token!=null && token==owned && token.generation==generation && !stock
                && actual.ppOnly() && token.session.same(actual);
    }
}
