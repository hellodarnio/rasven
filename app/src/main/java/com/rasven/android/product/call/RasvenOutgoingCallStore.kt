package com.rasven.android.product.call

import java.util.concurrent.atomic.AtomicLong

/** Call truth outlives an Activity/Recents task, just like the underlying Telecom/media owner.
 * Contains no Activity, launcher or Compose scope. Gateway identity also isolates QA fixtures.
 */
internal class RasvenOutgoingCallStore {
    private val attemptIds = AtomicLong()
    private var gateway: RasvenOutgoingCallGateway? = null
    private var owner: RasvenOutgoingCallViewModel? = null

    @Synchronized fun current(currentGateway: RasvenOutgoingCallGateway): RasvenOutgoingCallViewModel {
        if (gateway !== currentGateway || owner == null) {
            gateway = currentGateway
            owner = RasvenOutgoingCallViewModel(currentGateway, attemptIds::incrementAndGet)
        }
        return checkNotNull(owner)
    }
}

internal object RasvenProcessOutgoingCall {
    private val store = RasvenOutgoingCallStore()
    fun current(): RasvenOutgoingCallViewModel = store.current(RasvenRuntimeOutgoingCallGateway.current())
}
