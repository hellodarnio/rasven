package com.rasven.android.product.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RasvenCallSettingsTargetResolverTest {
    private val ready = RasvenCallNotificationReadiness(true, true, true, true)

    @Test
    fun readinessRequiresBothChannelsAppNotificationsAndLockScreenPresentation() {
        assertEquals(true, ready.ready)
        assertEquals(false, ready.copy(appNotifications = false).ready)
        assertEquals(false, ready.copy(incomingChannel = false).ready)
        assertEquals(false, ready.copy(presentationChannel = false).ready)
        assertEquals(false, ready.copy(fullScreenIntent = false).ready)
    }

    @Test
    fun android14WithMissingFullScreenCapabilityOpensItsSettings() {
        val selected = RasvenCallSettingsTargetResolver.resolve(34, true, ready.copy(fullScreenIntent = false)) { true }
        assertEquals(RasvenCallSettingsSystemTarget.FullScreenIntentAccess, selected)
    }

    @Test
    fun missingAppNotificationPermissionTakesPriorityOverFullScreenAccess() {
        val selected = RasvenCallSettingsTargetResolver.resolve(34, true,
            ready.copy(appNotifications = false, fullScreenIntent = false)) { true }
        assertEquals(RasvenCallSettingsSystemTarget.AppNotifications, selected)
    }

    @Test
    fun disabledIncomingChannelOpensThatChannel() {
        val selected = RasvenCallSettingsTargetResolver.resolve(34, true,
            ready.copy(incomingChannel = false)) { true }
        assertEquals(RasvenCallSettingsSystemTarget.IncomingChannel, selected)
    }

    @Test
    fun unavailableSpecializedSurfaceFallsBackToNotificationsThenAppDetails() {
        val notification = RasvenCallSettingsTargetResolver.resolve(34, true, ready.copy(fullScreenIntent = false)) {
            it == RasvenCallSettingsSystemTarget.AppNotifications || it == RasvenCallSettingsSystemTarget.AppDetails
        }
        assertEquals(RasvenCallSettingsSystemTarget.AppNotifications, notification)

        val details = RasvenCallSettingsTargetResolver.resolve(34, false, ready) {
            it == RasvenCallSettingsSystemTarget.AppDetails
        }
        assertEquals(RasvenCallSettingsSystemTarget.AppDetails, details)
    }

    @Test
    fun noResolvableSystemSurfaceReturnsNullDeterministically() {
        assertNull(RasvenCallSettingsTargetResolver.resolve(33, false, ready) { false })
    }
}
