package com.rasven.android.product.call

/** Debug/instrumentation-only gateway injection. Release has no override path. */
internal object RasvenRuntimeOutgoingCallGateway {
    @Volatile
    private var qaOverride: RasvenOutgoingCallGateway? = null

    fun current(): RasvenOutgoingCallGateway = qaOverride ?: RasvenIax2OutgoingCallGateway

    internal fun installQaOverride(gateway: RasvenOutgoingCallGateway?) {
        qaOverride = gateway
    }
}
