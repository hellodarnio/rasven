package com.rasven.android.product.setup

/** Debug/test-only terminal-result injection. No user-visible control and absent from release source. */
internal object RasvenRuntimeRegistrationGateway {
    @Volatile
    private var qaOverride: RasvenRegistrationGateway? = null

    fun current(): RasvenRegistrationGateway = qaOverride ?: RasvenIax2RegistrationGateway

    internal fun installQaOverride(gateway: RasvenRegistrationGateway?) {
        qaOverride = gateway
    }
}
