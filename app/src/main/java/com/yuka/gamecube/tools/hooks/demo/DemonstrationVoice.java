package com.yuka.gamecube.tools.hooks.demo;

import android.content.Context;
import android.content.res.Resources;
import com.yuka.gamecube.tools.compat.DemonstrationLayout;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.ArrayList;
import java.util.List;

/** Union native voice presets for display; retain audio permission and DSP command handling. */
final class DemonstrationVoice {
    static void install(DemonstrationHooks demo) throws Throwable {
        var r=demo.r;int sdk=r.adapter.baseline().sdk();var n=DemonstrationLayout.voice(sdk);
        var api=r.adapter.owner();String controller="com.vivo.gameassistant.changevoice.a";
        String entity="com.vivo.gameassistant.entity.VoiceItemEntity",info="com.vivo.gameassistant.entity.VoiceInfo";
        demo.predicate(r.adapter.commonType(),n.hardware(),true,Context.class);
        demo.predicate(n.privacyType(),n.privacy(),true,Context.class);
        demo.predicate(sdk==34?"m7.d":sdk==35?"l7.d":"t8.d","f",true,Context.class);
        r.method(api.type(),n.initialize());Reflect.field(r.type(controller),n.info());
        r.method(n.reader(),"e",Context.class,String.class);
        var defaultInfo=r.type(info).getDeclaredConstructor();defaultInfo.setAccessible(true);
        r.hook(api.type(),n.get(),r.type(controller),chain->{
            Object stock=chain.proceed();if(!demo.inUi()) return stock;
            Object owner=chain.getThisObject();
            if(stock==null) { Reflect.call(owner,n.initialize());stock=r.original((java.lang.reflect.Method)chain.getExecutable(),owner); }
            if(stock!=null && Reflect.get(stock,n.info())==null) {
                Object saved=Reflect.staticCall(r.type(n.reader()),"e",r.context(),r.currentPackage());
                if(!demo.inUi()) return stock;
                Reflect.set(stock,n.info(),saved==null?defaultInfo.newInstance():saved);
            }
            return stock;
        });
        var factory=r.method(n.data(),"b",String.class,Resources.class);
        var constructor=r.type(entity).getDeclaredConstructor(String.class,String.class,int.class,int.class,int.class);
        constructor.setAccessible(true);
        String[][] legacy={
                {"2","game_jarvis_voice","jarvis_icon","small_jarvis_icon","jarvis_voice_bg"},
                {"3","game_female_to_male_voice","female_to_male_icon","small_female_to_male_icon","female_to_male_voice_bg"},
                {"4","game_batman_voice","batman_icon","small_batman_icon","batman_voice_bg"},
                {"5","game_dead_men_voice","dead_men_icon","small_dead_men_icon","common_voice_bg"},
                {"6","game_child_voice","child_icon","small_child_icon","common_voice_bg"}};
        var strings=r.type("com.vivo.gameassistant.R$string");var drawables=r.type("com.vivo.gameassistant.R$drawable");
        if(sdk!=36) for(var row:legacy) {
            Reflect.field(strings,row[1]);for(int i=2;i<row.length;i++) Reflect.field(drawables,row[i]);
        }
        r.hook(n.data(),n.list(),List.class,chain->{
            Object stock=chain.proceed();if(!demo.inUi()) return stock;
            var previews=new ArrayList<Object>();Resources resources=r.context().getResources();
            for(int id=-1;id<=21;id++) {
                if(id<2 && id!=-1 || id>6 && id<10) continue;
                Object voice=r.original(factory,chain.getThisObject(),Integer.toString(id),resources);
                if(voice==null && sdk!=36 && id>=2 && id<=6) {
                    var row=legacy[id-2];
                    voice=constructor.newInstance(resources.getString((Integer)Reflect.get(strings,row[1])),row[0],
                            Reflect.get(drawables,row[2]),Reflect.get(drawables,row[3]),Reflect.get(drawables,row[4]));
                }
                if(voice!=null) previews.add(voice);
            }
            @SuppressWarnings("unchecked") var shown=(List<Object>)stock;
            var result=DemonstrationPolicy.visibleItems(shown,previews,value->{
                try { return Reflect.call(value,"getVoiceType"); }
                catch(Throwable failure) { throw new IllegalStateException("Invalid native voice item",failure); }
            });
            r.event("demonstration",r.currentPackage(),"voice_presets","count="+result.size()+" cache_unchanged=true dsp_effect_unverified=true");
            return result;
        });
        for(String view:new String[]{"ChangeVoiceHomeView","ChangeVoiceGridView","DefaultVoiceView","RecognizeVoiceView"})
            demo.scopeView("com.vivo.gameassistant.changevoice."+view);
        r.deoptimize(n.data());r.deoptimize(controller);r.module.deoptimize(r.method(api.type(),n.initialize()));
        r.log("feature=demonstration stage=voice_mod_bound sdk="+sdk);
    }
    private DemonstrationVoice() {}
}
