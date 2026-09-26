package ru.mdc.displaycontroller.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mdc.displaycontroller.BuildConfig

class ProductCapabilitiesTest {
    @Test
    fun releaseSafetyProfile_isReadOnly() {
        assertFalse(BuildConfig.CAN_WRITE)
        assertFalse(BuildConfig.OBD_WRITE)
        assertFalse(BuildConfig.OEM_WRITE)
        assertFalse(BuildConfig.RAW_CAN_WRITE)
        assertFalse(BuildConfig.UNKNOWN_BINDER_CALL)
        assertFalse(BuildConfig.UNKNOWN_BROADCAST_SEND)
        assertFalse(BuildConfig.DEVICE_NODE_WRITE)
        assertTrue(ProductCapabilities.isReadOnlyProfile())
    }
}
