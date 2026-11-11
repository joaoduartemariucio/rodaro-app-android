package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelRatio
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.FuelRatioEstimate
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuelRatioTests {
    private fun history(
        ethanolKilometers: Double = 400.0,
        ethanolLiters: Double = 40.0,
        gasolineKilometers: Double = 420.0,
        gasolineLiters: Double = 30.0,
        gasolineFills: Int = 4,
    ): List<FuelEntry> {
        val records = mutableListOf<FuelEntry>()
        var odometer = 1_000.0
        var day = 0
        records += full(day, odometer, ethanolLiters, fuelType = FuelType.ETANOL)
        repeat(3) {
            day += 10
            odometer += ethanolKilometers
            records += full(day, odometer, ethanolLiters, fuelType = FuelType.ETANOL)
        }
        day += 10
        odometer += ethanolKilometers
        records += full(day, odometer, ethanolLiters, fuelType = FuelType.GASOLINA_COMUM)
        repeat(gasolineFills) {
            day += 10
            odometer += gasolineKilometers
            records += full(day, odometer, gasolineLiters, fuelType = FuelType.GASOLINA_COMUM)
        }
        return records
    }

    @Test
    fun `enough data on both fuels gives the personal ratio`() {
        val estimate = FuelRatio.estimate(history(), VehicleKind.CAR, day(90), utc)

        val basis = assertIs<FuelRatioEstimate.Basis.Personal>(estimate.basis)
        assertEquals(10.0 / 14.0, estimate.ratio, 0.000_001)
        assertEquals(10.0, basis.ethanolKilometersPerLiter, 0.000_001)
        assertEquals(14.0, basis.gasolineKilometersPerLiter, 0.000_001)
        assertEquals(3, basis.ethanolIntervals)
        assertEquals(3, basis.gasolineIntervals)
        assertTrue(estimate.isPersonal)
    }

    @Test
    fun `two intervals of a fuel are not enough`() {
        val estimate = FuelRatio.estimate(history(gasolineFills = 3), VehicleKind.CAR, day(90), utc)

        assertEquals(FuelRatioEstimate(0.70, FuelRatioEstimate.Basis.GeneralRule(FuelRatioEstimate.Reason.NOT_ENOUGH_DATA)), estimate)
        assertFalse(estimate.isPersonal)
    }

    @Test
    fun `a car needs 600 km per fuel while a motorcycle needs 300`() {
        val records = history(ethanolKilometers = 150.0, ethanolLiters = 15.0, gasolineKilometers = 168.0, gasolineLiters = 12.0)

        assertIs<FuelRatioEstimate.Basis.GeneralRule>(FuelRatio.estimate(records, VehicleKind.CAR, day(90), utc).basis)
        assertIs<FuelRatioEstimate.Basis.Personal>(FuelRatio.estimate(records, VehicleKind.MOTORCYCLE, day(90), utc).basis)
        assertEquals(600.0, FuelRatio.minimumKilometers(VehicleKind.CAR))
        assertEquals(300.0, FuelRatio.minimumKilometers(VehicleKind.MOTORCYCLE))
    }

    @Test
    fun `intervals older than twelve months are ignored`() {
        val estimate = FuelRatio.estimate(history(), VehicleKind.CAR, day(90 + 400), utc)

        assertEquals(FuelRatioEstimate.Basis.GeneralRule(FuelRatioEstimate.Reason.NOT_ENOUGH_DATA), estimate.basis)
    }

    @Test
    fun `a ratio outside the sanity band is reported as inconsistent`() {
        val estimate = FuelRatio.estimate(history(gasolineKilometers = 400.0, gasolineLiters = 40.0), VehicleKind.CAR, day(90), utc)

        assertEquals(FuelRatioEstimate.Basis.GeneralRule(FuelRatioEstimate.Reason.INCONSISTENT), estimate.basis)
        assertEquals(FuelRatio.GENERAL_RULE, estimate.ratio)
    }

    @Test
    fun `switching fuel at every fill-up is reported as alternating`() {
        val types = listOf(FuelType.ETANOL, FuelType.GASOLINA_COMUM)
        val records = (0..6).map { full(it * 10, 1_000.0 + it * 400, 40.0, fuelType = types[it % 2]) }

        val estimate = FuelRatio.estimate(records, VehicleKind.CAR, day(90), utc)

        assertEquals(FuelRatioEstimate.Basis.GeneralRule(FuelRatioEstimate.Reason.ALWAYS_ALTERNATING), estimate.basis)
    }
}
