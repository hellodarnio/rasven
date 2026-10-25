package com.rasven.android.designsystem.components

import com.rasven.android.R

/** Official Figma glyphs currently used by the call-history filter chips. */
internal enum class RasvenHistoryIcon { All, Incoming, Outgoing, Missed }

internal fun historyIconResource(icon: RasvenHistoryIcon): Int = when (icon) {
    RasvenHistoryIcon.All -> R.drawable.rasven_history_list_all
    RasvenHistoryIcon.Incoming -> R.drawable.rasven_history_call_in
    RasvenHistoryIcon.Outgoing -> R.drawable.rasven_history_call_out
    RasvenHistoryIcon.Missed -> R.drawable.rasven_history_call_missed
}
