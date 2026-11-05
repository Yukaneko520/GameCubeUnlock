package com.yuka.gamecube.tools.hooks.backend;

import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Correlates original asynchronous payloads, without using equals() or keeping an unbounded log queue. */
final class ControlTickets {
    record Ticket(long id,String pkg,long revision,String branch,int gear,boolean owned) {}
    private static final AtomicLong sequence=new AtomicLong();
    private static final class Pending {
        final Ticket ticket;int attempts;
        Pending(Ticket ticket) { this.ticket=ticket; }
    }
    private final IdentityHashMap<Object,Pending> pending=new IdentityHashMap<>();
    static Ticket create(String pkg,long revision,String branch,int gear,boolean owned) {
        return new Ticket(sequence.incrementAndGet(),pkg,revision,branch,gear,owned);
    }
    synchronized boolean put(Object payload,Ticket ticket) {
        if(payload==null || ticket==null) return false;
        if(pending.size()>=128 && !pending.containsKey(payload)) return false;
        pending.put(payload,new Pending(ticket));return true;
    }
    synchronized Ticket take(Object payload) { Pending value=pending.remove(payload);return value==null?null:value.ticket; }
    synchronized Ticket attempt(Object payload) {
        Pending value=pending.get(payload);
        if(value==null) return null;
        if(++value.attempts>=3) pending.remove(payload); // The pinned OEM factory performs at most three attempts.
        return value.ticket;
    }
    synchronized int size() { return pending.size(); }
    static boolean suppress(Ticket ticket,boolean active) {
        return ticket!=null && ticket.owned && ticket.gear>0 && !active;
    }
}
