package br.com.joaoduartemariucio.rodaro.presentation.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.Preferences
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

object DisplayPreferences {
    var current: Preferences by mutableStateOf(Preferences())
        private set

    fun update(preferences: Preferences) {
        current = preferences
    }
}

object UnitConversion {
    private const val KILOMETERS_PER_MILE = 1.609344
    private const val LITERS_PER_GALLON = 3.785411784
    private val KILOMETERS_PER_MILE_DECIMAL = BigDecimal("1.609344")
    private val LITERS_PER_GALLON_DECIMAL = BigDecimal("3.785411784")

    val isImperial: Boolean get() = DisplayPreferences.current.unitSystem == UnitSystem.IMPERIAL

    val preferredCurrency: CurrencyOption? get() = DisplayPreferences.current.currency

    val usesBrazilianReal: Boolean
        get() = (preferredCurrency?.code ?: Formats.currencyFor(Locale.getDefault()).currencyCode) == CurrencyOption.BRL.code

    fun distance(kilometers: Double): Double = if (isImperial) kilometers / KILOMETERS_PER_MILE else kilometers

    fun distanceToKilometers(value: Double): Double = if (isImperial) value * KILOMETERS_PER_MILE else value

    fun volume(liters: Double): Double = if (isImperial) liters / LITERS_PER_GALLON else liters

    fun volumeToLiters(value: Double): Double = if (isImperial) value * LITERS_PER_GALLON else value

    fun consumption(kilometersPerLiter: Double): Double =
        if (isImperial) kilometersPerLiter * LITERS_PER_GALLON / KILOMETERS_PER_MILE else kilometersPerLiter

    fun consumptionToKilometersPerLiter(value: Double): Double = if (isImperial) value * KILOMETERS_PER_MILE / LITERS_PER_GALLON else value

    fun perDistance(kilometers: BigDecimal): BigDecimal = if (isImperial) kilometers * KILOMETERS_PER_MILE_DECIMAL else kilometers

    fun perVolume(liters: BigDecimal): BigDecimal = if (isImperial) liters * LITERS_PER_GALLON_DECIMAL else liters

    fun perVolumeToLiters(value: BigDecimal): BigDecimal =
        if (isImperial) value.divide(LITERS_PER_GALLON_DECIMAL, MathContext.DECIMAL64) else value
}
