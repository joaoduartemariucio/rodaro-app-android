package br.com.joaoduartemariucio.rodaro.data.valuation.services

import java.math.BigDecimal
import java.text.Normalizer
import java.time.YearMonth
import java.util.Locale

internal object FipeText {
    private val MONTHS =
        listOf("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    private val MONTH_PATTERN = Regex("""^([a-z]+)\s*(?:/|\s+de\s+)\s*(\d{4})$""")

    fun price(text: String): BigDecimal? {
        val kept = text.filter { it.isDigit() || it == ',' }
        if (kept.none { it.isDigit() }) return null
        return kept.replace(',', '.').toBigDecimalOrNull()
    }

    fun month(text: String): YearMonth? {
        val plain =
            Normalizer
                .normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
        val match = MONTH_PATTERN.matchEntire(plain) ?: return null
        val month = MONTHS.indexOf(match.groupValues[1]).takeIf { it >= 0 } ?: return null
        return YearMonth.of(match.groupValues[2].toInt(), month + 1)
    }
}
