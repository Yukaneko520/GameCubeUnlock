package com.yuka.gamecube.tools.hooks.demo;

import android.content.Context;
import com.yuka.gamecube.tools.compat.VisualLayout;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Named positive support predicates, effective only while native UI is being built or clicked. */
final class DemonstrationCapabilities {
    static void install(DemonstrationHooks demo) throws Throwable {
        var r=demo.r;int sdk=r.adapter.baseline().sdk();String raw;
        try(var stream=r.context().createPackageContext("com.yuka.gamecube.tools",0).getAssets()
                .open("demonstration-capabilities.json")) {
            raw=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        var rows=new JSONObject(raw).getJSONArray(Integer.toString(sdk));
        for(int i=0;i<rows.length();i++) {
            var row=rows.getJSONObject(i);var parameters=row.getJSONArray("parameters");
            var types=new Class<?>[parameters.length()];
            for(int j=0;j<types.length;j++) types[j]=switch(parameters.getString(j)) {
                case "Context" -> Context.class;case "String" -> String.class;
                default -> throw new IllegalArgumentException("Unsupported demonstration parameter");
            };
            demo.predicate(r.adapter.commonType(),row.getString("method"),true,types);
        }
        var image=VisualLayout.forSdk(sdk);
        demo.predicate(image.controller(),image.highFps(),false,String.class);
        demo.scopeView("com.vivo.gameassistant.gamefilter.widget.GameFilterSettingView");
        String presenter=com.yuka.gamecube.tools.compat.DemonstrationLayout.forSdk(sdk).presenter();
        var item=r.type(com.yuka.gamecube.tools.compat.DemonstrationLayout.ITEM);
        r.hook(presenter,sdk==36?"c":"d",void.class,demo::ui,item,android.view.View.class);
        r.deoptimize(r.adapter.commonType());
        r.deoptimize(r.adapter.owner().type());
        r.log("feature=demonstration stage=capabilities_bound count="+rows.length()+" scope=native_ui_only");
    }
    private DemonstrationCapabilities() {}
}
