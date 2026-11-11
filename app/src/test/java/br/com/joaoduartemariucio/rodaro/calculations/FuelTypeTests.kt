package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelGroup
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.FuelCapability
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelTypeTests {
    @Test
    fun `each capability allows only its own fuels`() {
        assertEquals(
            listOf(FuelType.GASOLINA_COMUM, FuelType.GASOLINA_ADITIVADA, FuelType.GASOLINA_PREMIUM, FuelType.ETANOL),
            FuelCapability.FLEX.fuelTypes,
        )
        assertTrue(FuelCapability.GASOLINA.fuelTypes.all { it.group == FuelGroup.GASOLINA })
        assertEquals(listOf(FuelType.ETANOL), FuelCapability.ETANOL.fuelTypes)
        assertEquals(listOf(FuelType.DIESEL_S10, FuelType.DIESEL_S500), FuelCapability.DIESEL.fuelTypes)
    }

    @Test
    fun `motorcycles cannot be diesel`() {
        assertFalse(FuelCapability.DIESEL in FuelCapability.available(VehicleKind.MOTORCYCLE))
        assertEquals(FuelCapability.entries, FuelCapability.available(VehicleKind.CAR))
    }

    @Test
    fun `an unknown fuel type is never guessed, an unknown capability falls back to flex`() {
        assertNull(FuelType.of("gnv"))
        assertNull(FuelType.of(null))
        assertEquals(FuelType.DIESEL_S10, FuelType.of("dieselS10"))
        assertEquals(FuelCapability.FLEX, FuelCapability.of("hydrogen"))
        assertEquals(FuelCapability.DIESEL, FuelCapability.of("diesel"))
    }

    @Test
    fun `Brazil has 27 state codes`() {
        assertEquals(27, Vehicle.UF_CODES.size)
        assertEquals(27, Vehicle.UF_CODES.toSet().size)
        assertTrue("SP" in Vehicle.UF_CODES && "DF" in Vehicle.UF_CODES)
    }

    @Test
    fun `raw values fall back safely`() {
        assertEquals(ExpenseCategory.OUTROS, ExpenseCategory.of("lunch"))
        assertEquals(ExpenseCategory.MULTA, ExpenseCategory.of("fine"))
        assertNull(FineSeverity.of("mild"))
        assertEquals(FineSeverity.GRAVISSIMA, FineSeverity.of("verySerious"))
        assertEquals(FineDiscount.NONE, FineDiscount.of("half"))
        assertEquals(FineDiscount.FORTY, FineDiscount.of("forty"))
        assertNull(DueKind.of("tax"))
        assertEquals(DueKind.CNH, DueKind.of("driverLicense"))
    }

    @Test
    fun `an expense is paid unless it is an unpaid fine`() {
        val base = Expense(vehicleId = testVehicleId, category = ExpenseCategory.MULTA, date = day(0), amount = BigDecimal.TEN)

        assertTrue(base.isPaid)
        assertTrue(base.copy(fine = FineDetails()).isPaid)
        assertFalse(base.copy(fine = FineDetails(isPaid = false)).isPaid)
        assertEquals(day(0), base.createdAt)
    }
}
