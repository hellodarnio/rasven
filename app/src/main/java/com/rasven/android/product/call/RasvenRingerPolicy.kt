package com.rasven.android.product.call

internal data class RasvenRingerPolicy(val sound: Boolean, val vibrate: Boolean) {
    companion object {
        fun evaluate(allowed: Boolean, mode: Int, volume: Int, channelSound: Boolean,
                     channelVibration: Boolean, hasVibrator: Boolean, vibrateWhenRinging: Boolean): RasvenRingerPolicy {
            val normal = mode == 2 // AudioManager.RINGER_MODE_NORMAL
            return RasvenRingerPolicy(
                allowed && normal && volume > 0 && channelSound,
                allowed && channelVibration && hasVibrator && (mode == 1 || (normal && vibrateWhenRinging)),
            )
        }
    }
}
