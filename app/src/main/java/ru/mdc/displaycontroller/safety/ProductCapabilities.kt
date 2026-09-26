package ru.mdc.displaycontroller.safety

import ru.mdc.displaycontroller.BuildConfig

object ProductCapabilities {
    const val SAFETY_PROFILE: String = "READ_ONLY_1_0_1"

    val canWrite: Boolean get() = BuildConfig.CAN_WRITE
    val obdWrite: Boolean get() = BuildConfig.OBD_WRITE
    val oemWrite: Boolean get() = BuildConfig.OEM_WRITE
    val rawCanWrite: Boolean get() = BuildConfig.RAW_CAN_WRITE
    val unknownBinderCall: Boolean get() = BuildConfig.UNKNOWN_BINDER_CALL
    val unknownBroadcastSend: Boolean get() = BuildConfig.UNKNOWN_BROADCAST_SEND
    val deviceNodeWrite: Boolean get() = BuildConfig.DEVICE_NODE_WRITE

    fun isReadOnlyProfile(): Boolean =
        BuildConfig.MDC_SAFETY_PROFILE == SAFETY_PROFILE &&
            !canWrite &&
            !obdWrite &&
            !oemWrite &&
            !rawCanWrite &&
            !unknownBinderCall &&
            !unknownBroadcastSend &&
            !deviceNodeWrite
}
