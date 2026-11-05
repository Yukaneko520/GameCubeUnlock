package com.yuka.gamecube.tools.hooks.demo;

import android.content.Context;
import com.yuka.gamecube.tools.compat.DemonstrationLayout;
import com.yuka.gamecube.tools.hooks.Reflect;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.json.JSONObject;

/** Populate original native items with native text/icons; no OEM list is persisted. */
final class DemonstrationCatalog {
    static void install(DemonstrationHooks demo) throws Throwable {
        var r=demo.r;int sdk=r.adapter.baseline().sdk();var n=DemonstrationLayout.forSdk(sdk);
        var item=r.type(DemonstrationLayout.ITEM);var type=r.type(DemonstrationLayout.TYPE);
        var constructor=item.getDeclaredConstructor(type);constructor.setAccessible(true);
        String raw;
        var moduleContext=r.context().createPackageContext("com.yuka.gamecube.tools",0);
        try(var stream=moduleContext.getAssets().open("demonstration-ui.json")) {
            raw=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        JSONObject rows=new JSONObject(raw).getJSONObject(Integer.toString(sdk));
        var title=r.method(DemonstrationLayout.ITEM,n.title(),int.class);
        var enabled=r.method(DemonstrationLayout.ITEM,n.enabled(),boolean.class);
        var badge=r.method(DemonstrationLayout.ITEM,n.badge(),boolean.class);
        r.hook(DemonstrationLayout.ITEM,sdk==34?"o":sdk==35?"n":"q",boolean.class,chain->{
            Object stock=chain.proceed();return demo.active()?true:stock;
        });
        var strings=r.type("com.vivo.gameassistant.R$string");var drawables=r.type("com.vivo.gameassistant.R$drawable");
        Reflect.field(drawables,"ic_game_small_window");
        r.method(DemonstrationLayout.ITEM,n.itemType());
        r.method(DemonstrationLayout.ITEM,n.iconOn(),int.class);r.method(DemonstrationLayout.ITEM,n.iconOff(),int.class);
        if(sdk==36) r.method("r9.e","a",Context.class,item);
        for(var keys=rows.keys();keys.hasNext();) {
            var row=rows.getJSONObject(keys.next());Reflect.field(strings,row.getString("title"));
            for(String key:new String[]{"on","off"}) if(row.has(key)) Reflect.field(drawables,row.getString(key));
        }
        r.hook(n.creator(),n.create(),void.class,chain->{
            Object stock=chain.proceed();if(!demo.active()) return stock;
            var config=r.snapshot();String pkg=r.currentPackage();
            Context context=(Context)chain.getArg(0);
            @SuppressWarnings("unchecked") List<Object> list=(List<Object>)chain.getArg(5);
            @SuppressWarnings("unchecked") List<Object> other=(List<Object>)chain.getArg(6);
            var visible=DemonstrationPolicy.visibleItems(other,list,value->{
                try { Object kind=Reflect.call(value,n.itemType());return kind instanceof Enum<?> e?e.name():null; }
                catch(Throwable failure) { throw new IllegalStateException("Invalid native menu item",failure); }
            });
            var seen=new HashSet<String>();
            for(Object value:visible) {
                Object kind=Reflect.call(value,n.itemType());
                if(kind instanceof Enum<?> e) seen.add(e.name());
            }
            int added=0;
            for(Object kind:type.getEnumConstants()) {
                String name=((Enum<?>)kind).name();if(seen.contains(name) || !rows.has(name)) continue;
                JSONObject row=rows.getJSONObject(name);Object value=constructor.newInstance(kind);
                title.invoke(value,Reflect.get(strings,row.getString("title")));
                enabled.invoke(value,true);badge.invoke(value,false);
                if(sdk==36) Reflect.staticCall(r.type("r9.e"),"a",context,value);
                else for(String key:new String[]{"on","off"}) {
                    String resource=row.optString(key,"ic_game_small_window");
                    Reflect.call(value,key.equals("on")?n.iconOn():n.iconOff(),Reflect.get(drawables,resource));
                }
                visible.add(value);seen.add(name);added++;
            }
            if(config!=r.snapshot() || !pkg.equals(r.currentPackage()) || !demo.active()) return stock;
            other.clear();other.addAll(visible);list.clear();
            r.event("demonstration",r.currentPackage(),"catalog","entries="+seen.size()+" added="+added+" hardware_effect_unverified=true");
            return stock;
        },Context.class,r.type("android.multidisplay.MultiDisplayManager"),r.type(n.utility()),boolean.class,boolean.class,List.class,List.class);
        var presenter=new java.util.concurrent.atomic.AtomicReference<WeakReference<Object>>(new WeakReference<>(null));
        r.hook(n.presenter(),n.load(),void.class,chain->{
            presenter.set(new WeakReference<>(chain.getThisObject()));return chain.proceed();
        });
        r.addListener(new ConfigListener() {
            @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
                String pkg=r.currentPackage();
                if(old.demonstrationMode==next.demonstrationMode && old.demonstrates(pkg)==next.demonstrates(pkg)) return;
                Object live=presenter.get().get();
                if(live!=null) { Reflect.call(live,n.load());r.event("demonstration",pkg,"catalog_reload","enabled="+next.demonstrates(pkg)); }
            }
        });
        r.deoptimize(n.creator());r.deoptimize(n.presenter());
        r.log("feature=demonstration stage=catalog_bound sdk="+sdk+" entries="+rows.length());
    }
    private DemonstrationCatalog() {}
}
