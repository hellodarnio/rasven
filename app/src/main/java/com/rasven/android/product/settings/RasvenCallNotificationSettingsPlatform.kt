package com.rasven.android.product.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.rasven.android.product.call.RasvenCallNotification

internal enum class RasvenCallSettingsSystemTarget {
    FullScreenIntentAccess,
    IncomingChannel,
    PresentationChannel,
    AppNotifications,
    AppDetails,
}

internal data class RasvenCallNotificationReadiness(
    val appNotifications: Boolean,
    val incomingChannel: Boolean,
    val presentationChannel: Boolean,
    val fullScreenIntent: Boolean,
) {
    val ready: Boolean get() = appNotifications && incomingChannel && presentationChannel && fullScreenIntent
}

/** Prefer the settings surface for the actual missing capability, with resolvable fallbacks. */
internal object RasvenCallSettingsTargetResolver {
    fun candidates(
        apiLevel: Int,
        fullScreenIntentDeclared: Boolean,
        readiness: RasvenCallNotificationReadiness,
    ): List<RasvenCallSettingsSystemTarget> = buildList {
        if (!readiness.appNotifications) add(RasvenCallSettingsSystemTarget.AppNotifications)
        if (!readiness.incomingChannel) add(RasvenCallSettingsSystemTarget.IncomingChannel)
        if (!readiness.presentationChannel) add(RasvenCallSettingsSystemTarget.PresentationChannel)
        if (!readiness.fullScreenIntent && apiLevel >= 34 && fullScreenIntentDeclared) {
            add(RasvenCallSettingsSystemTarget.FullScreenIntentAccess)
        }
        add(RasvenCallSettingsSystemTarget.AppNotifications)
        add(RasvenCallSettingsSystemTarget.AppDetails)
    }

    fun resolve(
        apiLevel: Int,
        fullScreenIntentDeclared: Boolean,
        readiness: RasvenCallNotificationReadiness,
        canResolve: (RasvenCallSettingsSystemTarget) -> Boolean,
    ): RasvenCallSettingsSystemTarget? =
        candidates(apiLevel, fullScreenIntentDeclared, readiness).firstOrNull(canResolve)
}

internal object RasvenCallNotificationSettingsPlatform {
    fun notificationsEnabled(context: Context): Boolean = readiness(context).ready

    fun readiness(context: Context): RasvenCallNotificationReadiness {
        val manager = context.getSystemService(NotificationManager::class.java)
        fun channelEnabled(id: String): Boolean =
            manager?.getNotificationChannel(id)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true
        return RasvenCallNotificationReadiness(
            appNotifications = manager?.areNotificationsEnabled() == true &&
                (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            incomingChannel = channelEnabled(RasvenCallNotification.INCOMING_CHANNEL),
            presentationChannel = channelEnabled(RasvenCallNotification.PRESENTATION_CHANNEL),
            fullScreenIntent = Build.VERSION.SDK_INT < 34 || manager?.canUseFullScreenIntent() == true,
        )
    }

    fun resolveIntent(context: Context): Intent? {
        val fullScreenDeclared = declaresFullScreenIntent(context)
        val target = RasvenCallSettingsTargetResolver.resolve(
            apiLevel = Build.VERSION.SDK_INT,
            fullScreenIntentDeclared = fullScreenDeclared,
            readiness = readiness(context),
            canResolve = { candidate ->
                val intent = buildIntent(context.packageName, candidate)
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
            },
        ) ?: return null
        return buildIntent(context.packageName, target)
    }

    private fun buildIntent(packageName: String, target: RasvenCallSettingsSystemTarget): Intent = when (target) {
        RasvenCallSettingsSystemTarget.FullScreenIntentAccess -> Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.fromParts("package", packageName, null),
        )
        RasvenCallSettingsSystemTarget.IncomingChannel,
        RasvenCallSettingsSystemTarget.PresentationChannel -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, if (target == RasvenCallSettingsSystemTarget.IncomingChannel)
                RasvenCallNotification.INCOMING_CHANNEL else RasvenCallNotification.PRESENTATION_CHANNEL)
        RasvenCallSettingsSystemTarget.AppNotifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        RasvenCallSettingsSystemTarget.AppDetails -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    }

    @Suppress("DEPRECATION")
    private fun declaresFullScreenIntent(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions?.contains(Manifest.permission.USE_FULL_SCREEN_INTENT) == true
    }.getOrDefault(false)
}
