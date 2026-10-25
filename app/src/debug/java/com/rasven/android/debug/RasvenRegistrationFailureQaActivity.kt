package com.rasven.android.debug

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.setup.RasvenConnectingScreen
import com.rasven.android.product.setup.RasvenRegistrationErrorCategory
import com.rasven.android.product.setup.RasvenRegistrationFailureScreen
import com.rasven.android.product.setup.RasvenRegistrationGateway
import com.rasven.android.product.setup.RasvenRegistrationResult
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.product.setup.RasvenRegistrationViewModel
import com.rasven.android.product.setup.RasvenRuntimeRegistrationGateway
import com.rasven.android.product.setup.RasvenValidatedAccount

/** Debug-only deterministic Stage5A8 visual host. It is absent from release Product behavior. */
class RasvenRegistrationFailureQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        var starts = 0
        RasvenRuntimeRegistrationGateway.installQaOverride(
            RasvenRegistrationGateway { _, onResult ->
                starts += 1
                if (starts == 1) {
                    onResult(
                        RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown),
                    )
                }
                // Retry intentionally remains Registering so A6 can be inspected from this host.
            },
        )
        val owner = ViewModelProvider(this)[RasvenRegistrationViewModel::class.java]
        owner.startOnce(
            RasvenValidatedAccount(
                server = "qa.invalid",
                port = 4569,
                username = "qa",
                password = "qa-only-nonproduction-value",
            ),
        )

        setContent {
            RasvenTheme {
                val state = owner.state
                when (state) {
                    is RasvenRegistrationState.Failed -> {
                        RasvenRegistrationFailureScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onRetry = owner::retryFailedAttempt,
                            onOpenSettings = {},
                        )
                    }
                    else -> {
                        RasvenConnectingScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            RasvenRuntimeRegistrationGateway.installQaOverride(null)
        }
        super.onDestroy()
    }
}
