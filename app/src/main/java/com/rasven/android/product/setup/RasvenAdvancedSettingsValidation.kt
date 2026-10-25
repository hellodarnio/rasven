package com.rasven.android.product.setup

/** Non-secret persisted/current account projection exposed to Stage5A4 UI. */
internal data class RasvenAdvancedSettingsCurrentAccount(
    val server: String,
    val port: Int,
    val username: String,
)

internal data class RasvenAdvancedSettingsErrors(
    val server: RasvenAccountFieldError? = null,
    val port: RasvenAccountFieldError? = null,
    val username: RasvenAccountFieldError? = null,
) {
    val isValid: Boolean get() = server == null && port == null && username == null
}

/**
 * Save boundary for Stage5A4. The current password is intentionally absent.
 * A replacement is nullable and is never included in toString().
 */
internal class RasvenAdvancedSettingsUpdate(
    val server: String,
    val port: Int,
    val username: String,
    val passwordReplacement: String?,
) {
    override fun toString(): String =
        "RasvenAdvancedSettingsUpdate(server=[redacted], port=$port, username=[redacted], passwordReplacement=[redacted])"
}

internal object RasvenAdvancedSettingsValidation {
    fun validate(server: String, port: String, username: String): RasvenAdvancedSettingsErrors =
        RasvenAdvancedSettingsErrors(
            server = RasvenAccountFieldError.ServerRequired.takeIf { server.isBlank() },
            port = RasvenAccountFieldError.PortOutOfRange.takeIf { RasvenAccountValidation.parsePort(port) == null },
            username = RasvenAccountFieldError.UsernameRequired.takeIf { username.isBlank() },
        )

    fun validatedUpdate(
        server: String,
        port: String,
        username: String,
        passwordReplacement: String,
    ): RasvenAdvancedSettingsUpdate? {
        if (!validate(server, port, username).isValid) return null
        val parsedPort = RasvenAccountValidation.parsePort(port) ?: return null
        return RasvenAdvancedSettingsUpdate(
            server = server.trim(),
            port = parsedPort,
            username = username.trim(),
            // Product/Handoff: empty means no password change. Non-empty replacement is forwarded exactly.
            passwordReplacement = passwordReplacement.takeIf { it.isNotEmpty() },
        )
    }
}
