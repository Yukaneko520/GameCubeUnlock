package com.yuka.gamecube.tools.hooks.profiles;

import org.junit.Test;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class ProfilePolicyTest {
    @Test public void offAndNativeTargetsPreserveOriginal() {
        assertFalse(ProfilePolicy.canClone(false, true, false, "game.a", "game.b", true));
        assertFalse(ProfilePolicy.canClone(true, true, true, "game.a", "game.b", true));
        assertTrue(ProfilePolicy.canClone(true, true, false, "game.a", "game.b", true));
    }
    @Test public void missingDonorAndHardwareNeverUnlock() {
        assertFalse(ProfilePolicy.canClone(true, false, false, "a", "b", true));
        assertFalse(ProfilePolicy.canClone(true, true, false, "a", "b", false));
        assertFalse(ProfilePolicy.canClone(true, true, false, "a", "", true));
        assertFalse(ProfilePolicy.canClone(true, true, false, "a", "a", true));
    }
    @Test public void revokeRemovesOnlyOwnedIdentityEvenWhenValuesEqual() {
        String nativeEntry = new String("same"), clone = new String("same");
        Set<String> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        owned.add(clone);
        List<String> effective = List.of(nativeEntry, clone);
        List<String> result = ProfilePolicy.nativeEntries(effective, owned);
        assertEquals(1, result.size());
        assertSame(nativeEntry, result.get(0));
        assertEquals(2, effective.size());
    }
    @Test public void delayedWorkRequiresSamePackagePidRevisionAndProfile() {
        Object profile = new Object();
        ProfilePolicy.Ticket ticket = new ProfilePolicy.Ticket("a", 10, 5, profile);
        assertTrue(ticket.matches("a", 10, 5, profile));
        assertFalse(ticket.matches("b", 10, 5, profile));
        assertFalse(ticket.matches("a", 11, 5, profile));
        assertFalse(ticket.matches("a", 10, 6, profile));
        assertFalse(ticket.matches("a", 10, 5, new Object()));
        assertFalse(new ProfilePolicy.Ticket("a", 0, 5, profile).matches("a", 0, 5, profile));
    }
    @Test public void cacheInvalidatesForSnapshotIdentityReplacementAndInPlaceAppend() {
        Object snapshot = new Object(), list = new Object();
        ProfilePolicy.CacheStamp stamp = new ProfilePolicy.CacheStamp(snapshot, list, 9);
        assertTrue(stamp.matches(snapshot, list, 9));
        assertFalse(stamp.matches(new Object(), list, 9));
        assertFalse(stamp.matches(snapshot, new Object(), 9));
        assertFalse(stamp.matches(snapshot, list, 10));
    }
    @Test public void removalAndReplacementCloseOldIdentityButUnchangedSourceKeepsSession() {
        Object old = new String("profile"), replacement = new String("profile");
        assertTrue(ProfilePolicy.retiredProfiles(Map.of("game", old), Map.of("game", old)).isEmpty());
        assertSame(old, ProfilePolicy.retiredProfiles(Map.of("game", old), Map.of("game", replacement)).get(0));
        assertSame(old, ProfilePolicy.retiredProfiles(Map.of("game", old), Map.of()).get(0));
        assertTrue(ProfilePolicy.retiredProfiles(Map.of(), Map.of("game", replacement)).isEmpty());
    }
}
