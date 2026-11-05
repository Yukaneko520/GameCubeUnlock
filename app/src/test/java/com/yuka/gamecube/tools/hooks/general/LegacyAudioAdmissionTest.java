package com.yuka.gamecube.tools.hooks.general;

import static org.junit.Assert.*;
import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.Map;
import org.junit.Test;

public class LegacyAudioAdmissionTest {
    private static final String TARGET = "com.example.game", DONOR = "com.native.game";

    private ConfigSnapshot enabled(long revision) {
        AppRule rule = new AppRule(true, false, false, false, false, true, false, false, "", "", DONOR);
        return new ConfigSnapshot(true, 0, false, revision, Map.of(TARGET, rule));
    }

    private LegacyAudio.Admission admission(ConfigSnapshot snapshot) {
        return new LegacyAudio.Admission(TARGET, DONOR, "game_custom_sound_for_version_2",
                new Object(), snapshot, false);
    }

    @Test public void sameRevisionRefreshAcceptsFreshAdmissions() {
        ConfigSnapshot old = enabled(7), refreshed = enabled(7);
        LegacyAudio.Admission pending = admission(old);
        assertTrue(pending.canCommit(old, old));
        assertFalse(pending.canCommit(refreshed, refreshed));
        assertTrue(admission(refreshed).canCommit(refreshed, refreshed));
    }

    @Test public void removeAndReinstallAtSameRevisionCannotReviveOldAdmission() {
        ConfigSnapshot old = enabled(7);
        ConfigSnapshot removed = new ConfigSnapshot(true, 0, false, 7, Map.of());
        ConfigSnapshot reinstalled = enabled(7);
        assertFalse(admission(old).canCommit(removed, removed));
        assertFalse(admission(removed).canCommit(removed, removed));
        assertFalse(admission(old).canCommit(reinstalled, reinstalled));
        assertTrue(admission(reinstalled).canCommit(reinstalled, reinstalled));
    }

    @Test public void invalidDefaultsRejectHighRevisionPendingWorkAndAllowFreshRecovery() {
        ConfigSnapshot old = enabled(90), invalid = ConfigSnapshot.defaults(), recovered = enabled(90);
        assertFalse(admission(old).canCommit(invalid, invalid));
        assertFalse(admission(old).canCommit(recovered, recovered));
        assertTrue(admission(recovered).canCommit(recovered, recovered));
    }

    @Test public void nextSnapshotMustBePublishedBeforeAdmissionCommits() {
        ConfigSnapshot old = enabled(7), next = enabled(8);
        assertFalse(admission(old).canCommit(next, old));
        assertFalse(admission(next).canCommit(next, old));
        assertTrue(admission(next).canCommit(next, next));
    }

    @Test public void currentDonorAndEnabledBitsRemainRequired() {
        ConfigSnapshot snapshot = enabled(7);
        LegacyAudio.Admission wrongDonor = new LegacyAudio.Admission(TARGET, "com.other.game",
                "game_custom_sound_for_version_2", new Object(), snapshot, false);
        assertFalse(wrongDonor.canCommit(snapshot, snapshot));
        ConfigSnapshot disabled = snapshot.withGlobal(false, 0, false);
        assertFalse(admission(disabled).canCommit(disabled, disabled));
    }

    @Test public void automaticSelectorPinsResolvedDonorWithoutBecomingExplicit() {
        AppRule auto = new AppRule(true, false, false, false, false, true, false, false, "", "", "");
        ConfigSnapshot snapshot = new ConfigSnapshot(true, 0, false, 7, Map.of(TARGET, auto));
        LegacyAudio.Admission pin = admission(snapshot);
        assertTrue(pin.canCommit(snapshot, snapshot));
        assertFalse(pin.allowed(enabled(7)));
    }

    @Test public void sameSettingsNewGenerationDoesNotAuthorizeOldCommands() {
        ConfigSnapshot old = enabled(7), next = enabled(8);
        LegacyAudio.Admission pin = admission(old);
        assertTrue(pin.allowed(old));
        assertFalse(pin.allowed(next));
    }

    @Test public void revokedAdmissionCannotCommitAgain() {
        ConfigSnapshot snapshot = enabled(7);
        LegacyAudio.Admission pin = admission(snapshot);
        pin.revoked = true;
        assertFalse(pin.canCommit(snapshot, snapshot));
    }

    @Test public void cleanupOwnershipOnlyCarriesAcrossIdenticalNativeProfileSource() {
        ConfigSnapshot snapshot = enabled(7);
        Object preset = new Object(), source = new Object();
        LegacyAudio.Admission old = new LegacyAudio.Admission(TARGET, DONOR,
                "game_custom_sound_for_version_2", preset, snapshot, false, source);
        assertTrue(new LegacyAudio.Admission(TARGET, DONOR, old.function, preset,
                snapshot, false, source).sameProfile(old));
        assertFalse(new LegacyAudio.Admission(TARGET, DONOR, old.function, preset,
                snapshot, false, new Object()).sameProfile(old));
        assertFalse(new LegacyAudio.Admission(TARGET, DONOR, old.function, new Object(),
                snapshot, false, source).sameProfile(old));
        assertFalse(new LegacyAudio.Admission(TARGET, "com.other", old.function, preset,
                snapshot, false, source).sameProfile(old));
        assertFalse(new LegacyAudio.Admission(TARGET, DONOR, old.function, preset,
                snapshot, true, source).sameProfile(old));
    }
}
