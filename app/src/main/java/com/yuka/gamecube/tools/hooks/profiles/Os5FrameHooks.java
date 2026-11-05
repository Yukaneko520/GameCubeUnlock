package com.yuka.gamecube.tools.hooks.profiles;

import android.content.Context;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;

final class Os5FrameHooks {
    static final String CONTROLLER="com.vivo.gameassistant.frameinterpolation.f";
    static void install(HookRuntime r) throws Throwable {
        var n=com.yuka.gamecube.tools.compat.LegacyFrameLayout.forSdk(r.adapter.baseline().sdk());
        var layout=r.adapter.frameProfile();
        ProfileAccess hooks=new ProfileAccess(r);ProfileStore profiles=new ProfileStore(r,true);
        Os5FrameSessions sessions=new Os5FrameSessions(r,profiles);boolean[] installed={false};
        try {
            profiles.preflight();
            Class<?> state=r.type(FrameHooks.STATE),reason=r.type(FrameHooks.REASON);
            for(String f:new String[]{"d","e","h","i","j",n.strategyField()}) ProfileAccess.field(r.type(CONTROLLER),f);
            for(String m:new String[]{n.bind(),n.cool(),n.hot(),n.disable(),n.dispose()}) r.method(CONTROLLER,m);
            r.method(CONTROLLER,n.state(),state,reason);r.method(CONTROLLER,n.view(),state);
            r.method(CONTROLLER,n.recover(),state,reason,Integer.class);
            ProfileAccess.field(r.type(layout.strategy()),"b");
            r.method(profiles.utils(),n.pidRevised(),String.class);
            r.method(profiles.utils(),n.lookupPid(),Context.class,String.class);
            var menuCaller=r.method(profiles.utils(),n.menuCaller(),String.class);
            r.log("feature=frame stage=deopt method="+menuCaller+" result="+r.module.deoptimize(menuCaller));
            var dispatch=r.method(layout.strategy(),n.dispatch(),int.class,int.class,int.class,int.class,state);
            r.log("feature=frame stage=deopt method="+dispatch+" result="+r.module.deoptimize(dispatch));
            hooks.hook(ProfileStore.SERVICE,n.serviceInit(),void.class,chain->{Object result=chain.proceed();sessions.afterChange();return result;});
            hooks.hook(layout.values(),n.lookupProfile(),r.type(layout.entity()),chain->{profiles.reconcile();return chain.proceed();},String.class);
            hooks.hook(profiles.utils(),profiles.frameSupport(),boolean.class,chain->{
                profiles.reconcile();Object result=chain.proceed();String pkg=(String)chain.getArg(0);
                if(r.snapshot().apps.containsKey(pkg)) r.event("frame",pkg,"menu_gate",
                        "baseline="+r.adapter.baseline().id()+" cache="+r.adapter.frameProfile().cache()+" strategy="+layout.strategy()+"/"+layout.values()+" result="+result+" donated="+(profiles.donated(pkg)!=null));
                return result;
            },String.class);
            hooks.hook(CONTROLLER,n.bind(),boolean.class,chain->{sessions.prepare(chain.getThisObject());return chain.proceed();});
            hooks.hook(CONTROLLER,n.stop(),void.class,chain->{sessions.cancel(chain.getThisObject());return chain.proceed();});
            hooks.hook(CONTROLLER,n.schedule(),void.class,chain->{
                Object c=chain.getThisObject();var ticket=sessions.prepare(c);
                if(ticket==null) return sessions.touched(r.currentPackage())?null:chain.proceed();
                sessions.schedule(c,(Integer)chain.getArg(0),chain.getArg(1),chain.getArg(2),ticket);return null;
            },int.class,state,reason);
            hooks.hook(CONTROLLER,n.change(),boolean.class,chain->{
                String pkg=(String)chain.getArg(1);boolean close="CLOSE".equals(String.valueOf(chain.getArg(0)));
                var ticket=sessions.prepare(chain.getThisObject());
                if(!close && sessions.touched(pkg) && (ticket==null || !sessions.valid(ticket))) return false;
                var old=sessions.applying.get();sessions.applying.set(ticket);
                try {return chain.proceed();} finally {if(old==null)sessions.applying.remove();else sessions.applying.set(old);}
            },state,String.class,reason);
            hooks.hook(layout.strategy(),n.dispatch(),void.class,chain->{
                var ticket=sessions.applying.get();if(ticket==null) return chain.proceed();
                if(!sessions.valid(ticket)) return null;
                boolean revised=(Boolean)r.original(r.method(profiles.utils(),n.pidRevised(),String.class),null,ticket.pkg);
                if(revised && (Integer)r.original(r.method(profiles.utils(),n.lookupPid(),Context.class,String.class),null,r.context(),ticket.pkg)!=ticket.pid) {
                    r.log("feature=frame baseline="+r.adapter.baseline().id()+" stage=pid_changed waiting_for_oem_rebind=true pkg="+ticket.pkg);return null;
                }
                return chain.proceed();
            },int.class,int.class,int.class,int.class,state);
            hooks.hook(profiles.utils(),n.pidRevised(),boolean.class,chain->sessions.applying.get()!=null?false:chain.proceed(),String.class);
            hooks.hook(n.writer(),"b",void.class,chain->{
                var ticket=sessions.applying.get();boolean enable=(Boolean)chain.getArg(1);
                if(enable && ticket!=null && (!sessions.valid(ticket)||(Integer)chain.getArg(3)!=ticket.pid)) return null;
                synchronized(sessions.writerLock) {
                    Object result=chain.proceed();sessions.observe(enable,(Integer)chain.getArg(2),(Integer)chain.getArg(3));return result;
                }
            },Context.class,boolean.class,int.class,int.class,int.class,int.class);
            hooks.hook(CONTROLLER,n.dispose(),void.class,chain->{sessions.release(chain.getThisObject());return chain.proceed();});
            r.addListener(new ConfigListener() {
                @Override public void beforeChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {if(installed[0])sessions.beforeChange(old,next);}
                @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {if(installed[0])sessions.afterChange();}
            });
            installed[0]=true;
        } catch(Throwable e) {installed[0]=false;hooks.close();profiles.restore();throw e;}
    }
}
