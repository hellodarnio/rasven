package com.rasven.android.product.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.product.setup.RasvenRegistrationState

internal data class RasvenHomeDialerLocalState(
    val active: Boolean = false,
    val extension: String = "",
    val doNotDisturb: Boolean = false,
    val destination: String = "",
)

internal data class RasvenHomeDialerUiState(
    val connectionState: RasvenRegistrationState,
    val extension: String,
    val doNotDisturb: Boolean,
    val destination: String,
)

internal enum class RasvenHomeConnectionPresentation {
    Connected,
    Disconnected,
    Reconnecting,
}

internal fun homeConnectionPresentation(state: RasvenRegistrationState): RasvenHomeConnectionPresentation = when (state) {
    RasvenRegistrationState.Registered -> RasvenHomeConnectionPresentation.Connected
    RasvenRegistrationState.Unregistered -> RasvenHomeConnectionPresentation.Disconnected
    RasvenRegistrationState.Registering -> RasvenHomeConnectionPresentation.Reconnecting
    RasvenRegistrationState.Idle,
    is RasvenRegistrationState.Failed -> error("Setup-only registration state cannot render active Home")
}

internal val rasvenDialKeys: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

internal fun appendDialCharacter(destination: String, character: String): String {
    require(character in rasvenDialKeys)
    return destination + character
}

internal fun deleteLastDialCharacter(destination: String): String = destination.dropLast(1)

internal fun sanitizeDirectDialInput(value: String): String = value.filter { ch ->
    ch.isDigit() || ch == '*' || ch == '#' || ch == '+'
}

internal fun callEnabled(destination: String): Boolean = destination.isNotEmpty()

internal fun invokeOutgoingCallIfEnabled(destination: String, onStart: (String) -> Unit): Boolean {
    if (!callEnabled(destination)) return false
    onStart(destination)
    return true
}

internal fun toPersianDisplayDigits(value: String): String = buildString(value.length) {
    value.forEach { ch -> append(if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch) }
}

/** Retained local Home state. Connection loss never destroys destination or DND state. */
internal class RasvenHomeDialerViewModel : ViewModel() {
    var currentDestination: RasvenNavDestination by mutableStateOf(RasvenNavDestination.Home)
        private set

    var localState: RasvenHomeDialerLocalState by mutableStateOf(RasvenHomeDialerLocalState())
        private set

    fun enterHome(extension: String) {
        localState = if (localState.active) localState.copy(extension = extension)
        else localState.copy(active = true, extension = extension)
        currentDestination = RasvenNavDestination.Home
    }

    fun navigateTo(destination: RasvenNavDestination) {
        currentDestination = destination
    }

    fun setDestination(value: String) {
        localState = localState.copy(destination = sanitizeDirectDialInput(value))
    }

    fun append(character: String) {
        localState = localState.copy(destination = appendDialCharacter(localState.destination, character))
    }

    fun deleteLast() {
        localState = localState.copy(destination = deleteLastDialCharacter(localState.destination))
    }

    fun setDoNotDisturb(enabled: Boolean) {
        localState = localState.copy(doNotDisturb = enabled)
    }
}
