package com.yuka.gamecube.tools.hooks.backend;

import org.junit.Test;
import static org.junit.Assert.*;

public class RrmFieldTest {
    static class Parent { private int mUid=10001; }
    static class Child extends Parent {}
    static class WrongShadow extends Parent { private String mUid="wrong"; }
    static class StaticField { private static int mUid; }
    @Test public void inheritedProcessFieldsResolveButWrongTypesAndStaticFieldsReject() throws Exception {
        assertEquals(10001,RrmAccess.requestField(Child.class,"mUid",int.class).getInt(new Child()));
        assertThrows(NoSuchFieldException.class,()->RrmAccess.requestField(Child.class,"mUid",long.class));
        assertThrows(NoSuchFieldException.class,()->RrmAccess.requestField(WrongShadow.class,"mUid",int.class));
        assertThrows(NoSuchFieldException.class,()->RrmAccess.requestField(StaticField.class,"mUid",int.class));
        assertThrows(NoSuchFieldException.class,()->RrmAccess.requestField(Child.class,"missing",int.class));
    }
}
