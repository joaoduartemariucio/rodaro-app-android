package br.com.joaoduartemariucio.rodaro.domain.reports.calculations

import java.util.Locale

object PlateMask {
    private const val VISIBLE = 5
    private const val MIN_HIDDEN = 2

    fun normalized(plate: String?): String? =
        plate
            ?.uppercase(Locale.ROOT)
            ?.filter { it.isLetterOrDigit() }
            ?.takeIf { it.isNotEmpty() }

    fun mask(plate: String?): String? {
        val text = normalized(plate) ?: return null
        val visible = minOf(VISIBLE, maxOf(0, text.length - MIN_HIDDEN))
        return text.take(visible) + "*".repeat(text.length - visible)
    }
}
