package com.rasven.android.product.setup

import android.os.Handler
import android.os.Looper
import com.rasven.android.iax2.RasvenIax2Account
import com.rasven.android.iax2.RasvenIax2RegistrationEvent
import com.rasven.android.iax2.RasvenIax2Runtime

/** Release registration is backed by the real RFC 5456 UDP/IAX2 signaling runtime. */
internal object RasvenRuntimeRegistrationGateway {
    private val releaseGateway = object : RasvenRegistrationGateway {
        private val main = Handler(Looper.getMainLooper())

        override fun start(account: RasvenValidatedAccount, onResult: (RasvenRegistrationResult) -> Unit) {
            RasvenIax2Runtime.register(
                account = RasvenIax2Account(account.server, account.port, account.username, account.password),
            ) { event ->
                main.post {
                    onResult(
                        when (event) {
                            RasvenIax2RegistrationEvent.Registered -> RasvenRegistrationResult.Registered
                            RasvenIax2RegistrationEvent.Failed ->
                                RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown)
                        },
                    )
                }
            }
        }

        override fun invalidate() {
            RasvenIax2Runtime.invalidate()
        }
    }

    fun current(): RasvenRegistrationGateway = releaseGateway
}
