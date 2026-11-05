package com.yuka.gamecube.tools.hooks.profiles;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Controller c/d are builders; only factory-returned f objects are shown dialogs. Main thread only. */
final class SrDialogs {
    private final Map<Object, List<WeakReference<Object>>> shown = new WeakHashMap<>();

    boolean capture(Object controller, Object builder, Object dialog) throws Throwable {
        if (dialog == null) return false;
        if (builder != ProfileAccess.get(controller, "c") && builder != ProfileAccess.get(controller, "d")) return false;
        List<WeakReference<Object>> dialogs = shown.computeIfAbsent(controller, ignored -> new ArrayList<>());
        boolean captured = false;
        var iterator = dialogs.iterator();
        while (iterator.hasNext()) {
            Object existing = iterator.next().get();
            if (existing == null) iterator.remove();
            else if (existing == dialog) captured = true;
        }
        if (captured) return true;
        dialogs.add(new WeakReference<>(dialog));
        return true;
    }

    void cancel(Object controller) throws Throwable {
        List<WeakReference<Object>> dialogs = shown.get(controller);
        if (dialogs != null) {
            var iterator = dialogs.iterator();
            while (iterator.hasNext()) {
                Object dialog = iterator.next().get();
                if (dialog != null) ProfileAccess.call(dialog, "dismiss");
                iterator.remove();
            }
            shown.remove(controller);
        }
        ProfileAccess.set(controller, "c", null);
        ProfileAccess.set(controller, "d", null);
    }
}
