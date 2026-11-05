package com.yuka.gamecube.tools.hooks.general;

import java.util.ArrayList;
import java.util.List;

final class RuleOverlay {
    private final RuleAccess a;

    RuleOverlay(RuleAccess access) { a = access; }

    Object build(String function, String target, Object config, boolean scoped,
                 String requiredDonor) throws Throwable {
        if (config == null || function == null || function.isEmpty() || target == null || target.isEmpty()) return null;
        if (scoped && !function.equals(RuleAccess.string(config, "a"))) return null;
        List<Object> hardware = a.select(config, "c", function, false, scoped);
        List<Object> software = a.select(config, "d", function, true, scoped);
        List<Object> games = a.select(config, "b", function, false, scoped);
        if (games.isEmpty()) return null;
        Object sameFunction = a.view(function, hardware, software, games);
        if (!Boolean.TRUE.equals(a.invoke(a.h, null, sameFunction))) return null;

        List<HookPolicy.Row> policyRows = new ArrayList<>();
        for (Object coupling : games) {
            Object game = RuleAccess.get(coupling, "c");
            String type = RuleAccess.string(game, "h");
            String base = RuleAccess.string(game, "g");
            if (!"white".equals(type) && !"black".equals(type)) return null;
            boolean white = "white".equals(type);
            // Reject malformed selected rows before allowing any new membership.
            if (base.isEmpty() || RuleAccess.string(game, "a").isEmpty()) return null;
            boolean nested = white && Boolean.TRUE.equals(a.invoke(a.l, null, coupling));
            policyRows.add(new HookPolicy.Row(base, white, nested));
        }
        int matched = HookPolicy.match(target, policyRows);
        if (matched == HookPolicy.DENY) return null;

        Object donor = null;
        String channel = null;
        if (matched >= 0) {
            donor = games.get(matched);
            channel = "#";
        } else {
            for (int i = 0; i < games.size(); i++) {
                HookPolicy.Row row = policyRows.get(i);
                if (!row.white || !row.nestedAllowed) continue;
                Object candidate = games.get(i);
                Object game = RuleAccess.get(candidate, "c");
                String donorPackage = RuleAccess.string(game, "g");
                if (requiredDonor != null && !requiredDonor.equals(donorPackage)) continue;
                String donorChannel = RuleAccess.string(game, "a");
                if (!Boolean.TRUE.equals(a.invoke(a.f, null, function, donorPackage, sameFunction))) continue;
                if (!Boolean.TRUE.equals(a.invoke(a.channel, null, target, donorChannel, target))) continue;
                donor = candidate;
                channel = donorChannel;
                break;
            }
        }
        if (donor == null) return null;
        Object cloned = a.cloneCoupling(donor, target, channel);
        List<Object> overlay = new ArrayList<>(games);
        overlay.add(matched >= 0 ? matched : overlay.size(), cloned);
        return a.view(function, hardware, software, overlay);
    }

    boolean donorEligible(String function, String target, String donor, Object config, boolean scoped) throws Throwable {
        if (donor == null || donor.isEmpty() || config == null) return false;
        List<Object> hardware = a.select(config, "c", function, false, scoped);
        List<Object> software = a.select(config, "d", function, true, scoped);
        List<Object> games = a.select(config, "b", function, false, scoped);
        boolean explicit = false;
        for (Object coupling : games) {
            Object game = RuleAccess.get(coupling, "c");
            if(!donor.equals(RuleAccess.string(game,"g"))) continue;
            boolean white="white".equals(RuleAccess.string(game,"h"));
            boolean nested=white && Boolean.TRUE.equals(a.invoke(a.l,null,coupling));
            boolean channel=nested && Boolean.TRUE.equals(a.invoke(a.channel,null,target,RuleAccess.string(game,"a"),target));
            explicit|=channel;
            a.runtime.event("donor",target,function+":"+donor,"white="+white+" nested_hardware_software="+nested+
                    " target_channel="+channel+" donor="+donor+" baseline="+a.runtime.adapter.baseline().id());
        }
        boolean allowed=explicit && Boolean.TRUE.equals(a.invoke(a.f, null, function, donor,
                a.view(function, hardware, software, games)));
        a.runtime.event("donor",target,function+":"+donor+":result","eligible_coupling="+explicit+" original_donor_gate="+allowed+
                " selected_function_rows="+games.size());
        return allowed;
    }
}
