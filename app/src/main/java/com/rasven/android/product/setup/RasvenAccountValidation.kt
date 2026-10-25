package com.rasven.android.product.setup

/** Pure validation contract for the approved Stage 5A2 form. No credentials are logged or saved. */
internal enum class RasvenAccountFieldError {
    ServerRequired,
    PortOutOfRange,
    UsernameRequired,
    PasswordRequired,
}

internal data class RasvenAccountErrors(
    val server: RasvenAccountFieldError? = null,
    val port: RasvenAccountFieldError? = null,
    val username: RasvenAccountFieldError? = null,
    val password: RasvenAccountFieldError? = null,
) {
    val isValid: Boolean get() = server == null && port == null && username == null && password == null
}

/**
 * Only the validated handoff owns a password; keep it out of data-class toString/copy/componentN.
 * Password bytes are passed through untouched: no hidden trimming or persistence.
 */
internal class RasvenValidatedAccount(
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
) {
    /** A secure-repository reload creates a different object for the same registration owner. */
    fun sameRegistrationAs(other: RasvenValidatedAccount?): Boolean =
        other != null && server == other.server && port == other.port &&
            username == other.username && password == other.password

    override fun toString(): String = "RasvenValidatedAccount(server=[redacted], port=$port, username=[redacted], password=[redacted])"
}

internal object RasvenAccountValidation {
    /** Accept decimal port digits from Latin, Persian and Arabic-Indic keyboards. */
    fun parsePort(raw: String): Int? {
        val digits = raw.trim()
        if (digits.isEmpty()) return null
        val ascii = buildString(digits.length) {
            for (character in digits) {
                val digit = when (character) {
                    in '0'..'9' -> character - '0'
                    in '\u06f0'..'\u06f9' -> character - '\u06f0'
                    in '\u0660'..'\u0669' -> character - '\u0660'
                    else -> return null
                }
                append(('0'.code + digit).toChar())
            }
        }
        return ascii.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    fun validate(server: String, port: String, username: String, password: String): RasvenAccountErrors =
        RasvenAccountErrors(
            server = RasvenAccountFieldError.ServerRequired.takeIf { server.isBlank() },
            port = RasvenAccountFieldError.PortOutOfRange.takeIf { parsePort(port) == null },
            username = RasvenAccountFieldError.UsernameRequired.takeIf { username.isBlank() },
            // A password may legitimately contain spaces; only an empty password violates required.
            password = RasvenAccountFieldError.PasswordRequired.takeIf { password.isEmpty() },
        )

    fun validatedInput(server: String, port: String, username: String, password: String): RasvenValidatedAccount? {
        if (!validate(server, port, username, password).isValid) return null
        val parsedPort = parsePort(port) ?: return null
        return RasvenValidatedAccount(server.trim(), parsedPort, username.trim(), password)
    }
}
