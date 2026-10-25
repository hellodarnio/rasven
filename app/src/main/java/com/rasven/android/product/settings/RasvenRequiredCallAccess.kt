package com.rasven.android.product.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** Required before a first registration so lock-screen/background calls can be presented and answered. */
internal data class RasvenRequiredCallAccessStatus(
    val microphoneGranted: Boolean,
    val callNotificationsReady: Boolean,
) {
    val canConnect: Boolean get() = microphoneGranted && callNotificationsReady
}

/** One auditable access gate shared by onboarding and its return-from-Settings path. */
internal object RasvenRequiredCallAccess {
    @Volatile private var testOverride: RasvenRequiredCallAccessStatus? = null

    fun status(context: Context): RasvenRequiredCallAccessStatus = testOverride ?:
        RasvenRequiredCallAccessStatus(
            microphoneGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            callNotificationsReady = RasvenCallNotificationSettingsPlatform.notificationsEnabled(context),
        )

    internal fun setTestOverride(status: RasvenRequiredCallAccessStatus?) {
        testOverride = status
    }
}
