package com.yuka.gamecube.tools.hooks.general;

import java.util.List;

/** Candidate names alone never authorize a preset. Both checks use the current OEM values. */
final class AudioProfiles {
    interface Eligibility { boolean allows(String donor) throws Throwable; }
    interface Preset { Object resolve(String donor) throws Throwable; }

    static Selection resolve(List<String> candidates, Eligibility eligibility, Preset resolver) throws Throwable {
        for (String donor : candidates) {
            if (!eligibility.allows(donor)) continue;
            Object preset = resolver.resolve(donor);
            if (preset != null) return new Selection(donor, preset);
        }
        return null;
    }

    static final class Selection {
        final String donor;
        final Object preset;
        Selection(String donor, Object preset) { this.donor = donor; this.preset = preset; }
    }
    private AudioProfiles() {}
}
