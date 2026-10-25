package com.rasven.android.iax2

/** Same mandatory call readiness as foreground setup, without overriding explicit Disconnect. */
internal fun shouldRestoreStandbyConnection(paused: Boolean, savedAccountPresent: Boolean, requiredAccessReady: Boolean): Boolean =
    !paused && savedAccountPresent && requiredAccessReady
