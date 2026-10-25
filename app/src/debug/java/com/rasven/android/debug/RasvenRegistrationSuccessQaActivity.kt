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
import com.rasven.android.product.setup.RasvenRegistrationGateway
import com.rasven.android.product.setup.RasvenRegistrationResult
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.product.setup.RasvenRegistrationSuccessScreen
import com.rasven.android.product.setup.RasvenRegistrationViewModel
import com.rasven.android.product.setup.RasvenRuntimeRegistrationGateway
import com.rasven.android.product.setup.RasvenValidatedAccount

/** Debug-only owner visual host for Stage5A7. It is never part of release Product behavior. */
class RasvenRegistrationSuccessQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        RasvenRuntimeRegistrationGateway.installQaOverride(
            RasvenRegistrationGateway { _, onResult ->
                onResult(RasvenRegistrationResult.Registered)
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
                if (state is RasvenRegistrationState.Registered) {
                    RasvenRegistrationSuccessScreen(
                        persianFontFamily = RasvenAndroidFontFamilies.Persian,
                        onEnterDialer = {},
                    )
                } else {
                    RasvenConnectingScreen(
                        persianFontFamily = RasvenAndroidFontFamilies.Persian,
                    )
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
