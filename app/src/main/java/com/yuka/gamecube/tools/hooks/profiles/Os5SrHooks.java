package com.yuka.gamecube.tools.hooks.profiles;

import android.content.Context;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;

final class Os5SrHooks {
    static final String CONTROLLER="com.vivo.gameassistant.superresolution.b";
    static final String STRATEGY="com.vivo.gameassistant.superresolution.d";
    static void install(HookRuntime r) throws Throwable {
        ProfileAccess hooks=new ProfileAccess(r);ProfileStore store=new ProfileStore(r,false);
        Os5SrSessions sessions=new Os5SrSessions(r,store);boolean[] installed={false};
        try {
            store.preflight();
            for(String f:new String[]{"a","b","c","j","k","l","m","t","w"}) ProfileAccess.field(r.type(CONTROLLER),f);
            r.type(STRATEGY).getDeclaredConstructor();
            r.method("com.vivo.gameassistant.superresolution.a","n",r.type("v6.e"));
            for(String m:new String[]{"A0","z0","i1","k1","R0"}) r.method(CONTROLLER,m);
            // Z0 consumes strategy getters; v0 and both setters consume the hooked support/command helpers.
            for(String name:new String[]{"Z0","a1","V0"}) {
                var caller=r.method(CONTROLLER,name,Context.class,String.class,boolean.class,boolean.class);
                r.log("feature=sr stage=deopt method="+caller+" result="+r.module.deoptimize(caller));
            }
            var foreground=r.method(CONTROLLER,"v0",String.class,boolean.class);
            r.log("feature=sr stage=deopt method="+foreground+" result="+r.module.deoptimize(foreground));
            Class<?> builder=r.type("com.originui.widget.dialog.g"),dialog=r.type("com.originui.widget.dialog.f");
            if(!android.app.Dialog.class.isAssignableFrom(dialog)) throw new NoSuchMethodException("OS5 shown SR dialog");
            hooks.hook("com.vivo.gameassistant.utils.o","n",dialog,chain->{Object shown=chain.proceed();sessions.capture(shown);return shown;},
                    builder,int.class,android.content.DialogInterface.OnDismissListener.class);
            for(String name:new String[]{"f1","c1"}) {
                Class<?>[] args=name.equals("f1")?new Class<?>[]{int.class,int.class,int.class}:
                        new Class<?>[]{int.class,int.class,int.class,boolean.class};
                hooks.hook(CONTROLLER,name,void.class,chain->{
                    Object old=sessions.showing.get();sessions.showing.set(chain.getThisObject());
                    try { return chain.proceed(); } finally { if(old==null)sessions.showing.remove();else sessions.showing.set(old); }
                },args);
            }
            hooks.hook(ProfileStore.SERVICE,"i",void.class,chain->{Object result=chain.proceed();sessions.afterChange();return result;});
            hooks.constructor(CONTROLLER,chain->{store.reconcile();return chain.proceed();},Context.class);
            hooks.hook(store.utils(),store.srSupport(),boolean.class,chain->{
                if(sessions.cleaning.get()==null) store.reconcile();Object result=chain.proceed();
                String pkg=(String)chain.getArg(1);
                if(r.snapshot().apps.containsKey(pkg)) r.event("sr",pkg,"menu_gate","baseline=os5 result="+result+" cache=j donated="+(store.donated(pkg)!=null));
                return result;
            },Context.class,String.class);
            var owner=r.adapter.owner();
            hooks.hook(owner.type(),owner.pid(),int.class,chain->{var old=sessions.cleaning.get();return old==null?chain.proceed():old.pid;});
            hooks.hook(STRATEGY,"f",int.class,chain->{
                var old=sessions.cleaning.get();Object profile=old!=null?old.profile:store.donated(r.currentPackage());
                return profile!=null && ProfileAccess.get(chain.getThisObject(),"b")==profile?ProfileAccess.call(profile,"f"):chain.proceed();
            });
            hooks.hook(CONTROLLER,"v0",void.class,chain->{
                Object c=chain.getThisObject();String pkg=(String)chain.getArg(0);
                if(!(Boolean)chain.getArg(1) && sessions.owns(c)) {sessions.cleanup(c);return null;}
                var ticket=sessions.prepare(c,pkg);if(ticket==null && sessions.owns(c)) return null;return chain.proceed();
            },String.class,boolean.class);
            for(String method:new String[]{"a1","V0"}) hooks.hook(CONTROLLER,method,method.equals("a1")?boolean.class:void.class,chain->{
                Object c=chain.getThisObject();boolean enable=(Boolean)chain.getArg(2);String pkg=(String)chain.getArg(1);
                if(sessions.cleaning.get()!=null) return sessions.active(c)?chain.proceed():method.equals("a1")?false:null;
                var ticket=sessions.prepare(c,pkg);
                if(enable && (sessions.touched(pkg)||r.snapshot().allows(pkg,"sr")) && !store.stockSrSupport(pkg) && !sessions.valid(ticket))
                    return method.equals("a1")?false:null;
                var old=sessions.applying.get();sessions.applying.set(ticket);
                try { return chain.proceed(); } finally { if(old==null)sessions.applying.remove();else sessions.applying.set(old); }
            },Context.class,String.class,boolean.class,boolean.class);
            hooks.hook(CONTROLLER,"Z0",boolean.class,chain->{
                var ticket=sessions.applying.get();boolean enable=(Boolean)chain.getArg(2);
                if(enable && ticket!=null && !sessions.valid(ticket)) return false;
                Object result=chain.proceed();
                if(Boolean.TRUE.equals(result) && (ticket!=null||sessions.cleaning.get()!=null)) {
                    sessions.mark(chain.getThisObject(),enable,(Boolean)chain.getArg(3));
                    r.event("sr",(String)chain.getArg(1),"command","baseline=os5 enabled="+enable+" accepted="+result+" render=unverified");
                }
                return result;
            },Context.class,String.class,boolean.class,boolean.class);
            hooks.hook("a7.t","a",void.class,chain->{
                var ticket=sessions.cleaning.get()!=null?sessions.cleaning.get():sessions.applying.get();
                if((Boolean)chain.getArg(1) && ticket!=null && (!sessions.valid(ticket)||(Integer)chain.getArg(2)!=ticket.pid)) return null;
                return chain.proceed();
            },Context.class,boolean.class,int.class,int.class,int.class,int.class);
            for(String method:new String[]{"U0","W0"}) {
                boolean high=method.equals("W0");
                Class<?>[] args=high?new Class<?>[]{Context.class,String.class,boolean.class}:new Class<?>[]{boolean.class};
                hooks.hook(CONTROLLER,method,void.class,chain->{
                    if(sessions.cleaning.get()!=null) return chain.proceed();
                    Object c=chain.getThisObject();String pkg=(String)ProfileAccess.get(c,"k");boolean enabled=(Boolean)chain.getArg(high?2:0);
                    var ticket=sessions.applying.get();
                    if(enabled && (sessions.touched(pkg)||r.snapshot().allows(pkg,"sr"))) {
                        ticket=sessions.prepare(c,pkg);if(!store.stockSrSupport(pkg) && !sessions.valid(ticket)) return null;
                    }
                    var previous=sessions.applying.get();sessions.applying.set(ticket);
                    try {return chain.proceed();} finally {if(previous==null)sessions.applying.remove();else sessions.applying.set(previous);}
                },args);
            }
            hooks.hook(CONTROLLER,"R0",void.class,chain->{sessions.release(chain.getThisObject());return chain.proceed();});
            r.addListener(new ConfigListener() {
                @Override public void beforeChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {if(installed[0])sessions.beforeChange(old,next);}
                @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {if(installed[0])sessions.afterChange();}
            });
            installed[0]=true;
        } catch(Throwable e) {installed[0]=false;hooks.close();store.restore();throw e;}
    }
}
