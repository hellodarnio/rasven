package com.rasven.android.designsystem.foundation

internal enum class RasvenLanguage {
    Persian,
    English,
}

internal enum class RasvenLayoutDirection {
    Ltr,
    Rtl,
}

internal enum class RasvenSemanticEdge {
    Start,
    End,
}

internal enum class RasvenDataIslandKind {
    IpAddress,
    Domain,
    Port,
    Extension,
    PhoneNumber,
}

internal object RasvenDirectionContract {
    fun screenDirection(language: RasvenLanguage): RasvenLayoutDirection =
        when (language) {
            RasvenLanguage.Persian -> RasvenLayoutDirection.Rtl
            RasvenLanguage.English -> RasvenLayoutDirection.Ltr
        }

    fun dataIslandDirection(@Suppress("UNUSED_PARAMETER") kind: RasvenDataIslandKind): RasvenLayoutDirection =
        RasvenLayoutDirection.Ltr

    /** Setup brand is End-aligned: x=16 in FA RTL and x=280 in EN LTR at the 360px reference. */
    val setupBrandEdge: RasvenSemanticEdge = RasvenSemanticEdge.End
}
