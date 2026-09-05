package ru.mdc.displaycontroller;
import org.junit.Test;
import static org.junit.Assert.*;
public class SafetyProfileTest {
    @Test public void canWriteIsAlwaysFalse() { assertFalse(BuildConfig.CAN_WRITE); }
    @Test public void safetyProfileIsReadOnly() { assertEquals("READ_ONLY_1_0_1", BuildConfig.SAFETY_PROFILE); }
}
