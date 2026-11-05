package com.yuka.gamecube.tools.hooks.profiles;

import org.junit.Test;
import static org.junit.Assert.*;

public class SrDialogsTest {
    @Test public void cancelsShownFactoryResultRatherThanBuilderOrNewDialog() throws Throwable {
        SrDialogs dialogs = new SrDialogs();
        Controller controller = new Controller();
        controller.c = new Builder(); controller.d = new Builder();
        int before = Builder.creations;
        ActualDialog first = controller.c.c(), second = controller.d.c();
        dialogs.capture(controller, controller.c, first);
        dialogs.capture(controller, controller.d, second);
        dialogs.cancel(controller);
        assertEquals(1, first.dismissals); assertEquals(1, second.dismissals);
        assertEquals(1, first.listenersCalled); assertEquals(1, second.listenersCalled);
        assertEquals(before + 2, Builder.creations);
        assertNull(controller.c); assertNull(controller.d);
    }
    @Test public void unrelatedDialogIsNotOwnedAndUnshownBuilderDoesNotCreateDialog() throws Throwable {
        SrDialogs dialogs = new SrDialogs();
        Controller controller = new Controller(); controller.c = new Builder();
        assertFalse(dialogs.capture(controller, controller.c, null));
        Builder unrelated = new Builder(); ActualDialog nativeDialog = unrelated.c();
        assertFalse(dialogs.capture(controller, unrelated, nativeDialog));
        int before = Builder.creations;
        dialogs.cancel(controller);
        assertEquals(before, Builder.creations); assertEquals(0, nativeDialog.dismissals);
    }
    @Test public void failedDismissRetainsBuilderAndActualHandleForRetry() throws Throwable {
        SrDialogs dialogs = new SrDialogs();
        Controller controller = new Controller(); controller.c = new Builder();
        ActualDialog shown = controller.c.c(); shown.fail = true;
        dialogs.capture(controller, controller.c, shown);
        try { dialogs.cancel(controller); fail("dismiss failure must retain ownership"); }
        catch (IllegalStateException expected) { assertNotNull(controller.c); }
        shown.fail = false; dialogs.cancel(controller);
        assertNull(controller.c); assertEquals(1, shown.dismissals);
    }
    @Test public void replacedBuilderStillClosesEarlierShownDialogAndDuplicateCaptureIsIdempotent() throws Throwable {
        SrDialogs dialogs = new SrDialogs();
        Controller controller = new Controller(); controller.c = new Builder();
        ActualDialog old = controller.c.c();
        dialogs.capture(controller, controller.c, old);
        dialogs.capture(controller, controller.c, old);
        controller.c = new Builder(); ActualDialog next = controller.c.c();
        dialogs.capture(controller, controller.c, next);
        dialogs.cancel(controller);
        assertEquals(1, old.dismissals); assertEquals(1, next.dismissals);
    }
    // APK contract: controller c/d hold g builders; g.c creates a fresh f extends Dialog.
    public static final class Controller { private Builder c, d; }
    public static final class Builder {
        static int creations;
        ActualDialog c() { creations++; return new ActualDialog(); }
    }
    public static class DialogBase {
        int dismissals, listenersCalled; boolean fail;
        public void dismiss() {
            if (fail) throw new IllegalStateException("retry");
            dismissals++; listenersCalled++;
        }
    }
    public static final class ActualDialog extends DialogBase {}
}
