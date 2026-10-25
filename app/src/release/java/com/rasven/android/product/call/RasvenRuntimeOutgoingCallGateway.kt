package com.rasven.android.product.call

/** Production release uses the same Core Telecom aligned IAX2 gateway as debug. */
internal object RasvenRuntimeOutgoingCallGateway {
    fun current(): RasvenOutgoingCallGateway = RasvenIax2OutgoingCallGateway
}
