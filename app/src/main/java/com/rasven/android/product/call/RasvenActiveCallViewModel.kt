package com.rasven.android.product.call

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rasven.android.iax2.RasvenIax2Runtime

internal data class RasvenActiveCallControls(
    val sessionId: Long? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val held: Boolean = false,
    val keypadVisible: Boolean = false,
    val lastDtmfAccepted: Char? = null,
)

internal class RasvenActiveCallViewModel : ViewModel() {
    var controls by mutableStateOf(RasvenActiveCallControls())
        private set

    fun bind(sessionId: Long) {
        if (controls.sessionId == sessionId) return
        controls = RasvenActiveCallControls(sessionId = sessionId)
    }

    fun setMuted(enabled: Boolean): Boolean {
        val id = controls.sessionId ?: return false
        if (!RasvenIax2Runtime.setMuted(id, enabled)) return false
        controls = controls.copy(muted = RasvenIax2Runtime.isMuted(id), lastDtmfAccepted = null)
        return controls.muted == enabled
    }

    fun setSpeaker(actual: Boolean) {
        controls = controls.copy(speaker = actual)
    }

    fun setHeld(enabled: Boolean): Boolean {
        val id = controls.sessionId ?: return false
        return RasvenCallDomain.requestHeld(id, enabled) { success ->
            if (success && controls.sessionId == id) controls = controls.copy(held = enabled)
        }
    }

    fun observeHeld(sessionId: Long, held: Boolean) {
        if (controls.sessionId == sessionId && controls.held != held) controls = controls.copy(held = held)
    }

    fun toggleKeypad() {
        controls = controls.copy(keypadVisible = !controls.keypadVisible, lastDtmfAccepted = null)
    }

    fun sendDtmf(digit: Char): Boolean {
        val id = controls.sessionId ?: return false
        if (digit !in "0123456789*#") return false
        val accepted = RasvenIax2Runtime.sendDtmf(id, digit)
        controls = controls.copy(lastDtmfAccepted = if (accepted) digit else null)
        return accepted
    }

    fun clearForSession(sessionId: Long) {
        if (controls.sessionId == sessionId) controls = RasvenActiveCallControls()
    }
}
