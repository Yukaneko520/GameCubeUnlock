package com.yuka.gamecube.tools.compat;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TargetProfiles {
    public static final TargetBaseline OS4=new TargetBaseline("os4-pd2352-w10",34,Set.of("V2352A","V2352GA"),1200713,"12.0.07.13",
            "9d62f22077c3d025fe4c9b28128e5abb45b95953ecd658bcae8a30660a5a26cb",
            Map.of("framework.jar","3ad4ba86f3e85a53c97095d2cd004d40c9b23b4f8e4b48868bad5c7bbfe1c5bc",
                    "services.jar","20542d419e5a9310ab3ad04eafa588ad78ae5d9a78d428cc74ef7ad768997c76",
                    "vivo-framework.jar","a0224c02fe4e19129d0feba497e5fa7141ca69df768b92ab968d4ea787cfdbd5",
                    "vivo-services.jar","6f9c79fd33a28ca27b21501e05b615b877522b2eb351eb7350fa13fdf1d15fdc"),
            Map.of("com.vivo.gamewatch","559f297389fcf51e49d7e6c71997a3de0ff3a6e6470689ea07061ee9efbe986e"),false,30079,"3.0.0.79");
    public static final TargetBaseline OS5=new TargetBaseline("os5-pd2352-w10",35,Set.of("V2352A","V2352GA"),1300502,"13.0.05.02",
            "5e64d36a165a215bbcdc3e331c4d394054bc38d11c7ea50aebb1ee66cc16f94b",
            Map.of("framework.jar","b9c649750e06055adda3b7f998e662f7e805cef9f3094a70731014d897aede6e",
                    "services.jar","6302555d7360ea640cc6fddec2554fd7397d3d2359824a4987cb34774da90abe",
                    "vivo-framework.jar","3d6799d5192f917ba33dbb97439c3b622bca3f2b72ffb276c76a9722662308b2",
                    "vivo-services.jar","e9220962195120fe732683be60e180d8f32b730f26aded413fa94670135d7a8a"),
            Map.of("com.vivo.gamewatch","67230964539b119c884b869a72e508db5441ec9adf129f812a809922b0df07ae",
                    "com.vivo.gamelib","314dd941d3e421be203ed99757dd5c97474d54aa42bb77130786e4e1f4bba191"),true,250003,"25.0.0.03");
    public static final TargetBaseline OS6=new TargetBaseline("os6-pd2352",36,Set.of("V2352A","V2352GA"),1401000,"14.0.10.00_de",
            "924a0700a9c64ac2f2f3af90197ec1907c021a9094312a8f8b2255efc8cc3c7e",
            Map.of("framework.jar","7c53ecb914cdd83faa33204b982f4d04a9ddbab8fbe6324e6b78901e862f2f27",
                    "services.jar","a4b484442119a82dd60a50634f0b1d0304f281d8724a8e9b41a44aea3020ae72",
                    "vivo-framework.jar","3ca2051c975f30e51abe1390a2a4a722d442d351c9ce1b1a485a2199a685585d",
                    "vivo-services.jar","83a8b91877d232392144a5b41a505d0ed2b853df318fddd03ee08188867ec81e"),
            Map.of("com.vivo.gamewatch","d3315ea8f3a3a1b5a9781972851968b5dad70ea2e3b8c85bad718db5089b962c",
                    "com.vivo.gamelib","569eb50d11e83a4fa1350fc0c0640432a91e6aa37599748f6132381067aa9911"),true,250006,"25.0.0.06");
    public static final List<TargetBaseline> ALL=List.of(OS4,OS5,OS6);
    private TargetProfiles() {}
    public static TargetBaseline device(String model,String product,int sdk,String displayIc) {
        return ALL.stream().filter(b->b.deviceMatches(model,product,sdk,displayIc)).findFirst().orElse(null);
    }
    /** Selects descriptors only; runtime policy still requires complete artifact verification. */
    public static TargetBaseline descriptors(int sdk) {
        return ALL.stream().filter(b->b.sdk()==sdk).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported SDK: "+sdk));
    }
}
