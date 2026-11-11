package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.costs.entities.Expense
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDetails
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceRule
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val referenceDate: Instant = Instant.parse("2001-01-01T00:00:00Z")

fun day(offset: Int): Instant = referenceDate.plusSeconds(offset * 86_400L)

fun hour(
    hour: Int,
    dayOffset: Int,
): Instant = day(dayOffset).plusSeconds(hour * 3_600L)

val utc: ZoneId = ZoneOffset.UTC

val testVehicleId: UUID = UUID.fromString("00000000-0000-0000-0000-00000000CA11")

fun decimal(value: String): BigDecimal = BigDecimal(value)

fun assertDecimal(
    expected: String,
    actual: BigDecimal?,
    message: String? = null,
) {
    assertNotNull(actual, message)
    assertEquals(0, BigDecimal(expected).compareTo(actual), message ?: "expected $expected, got $actual")
}

fun full(
    dayOffset: Int,
    odometer: Double,
    liters: Double,
    price: BigDecimal = BigDecimal(5),
    id: UUID = UUID.randomUUID(),
    fuelType: FuelType? = null,
): FuelEntry =
    FuelEntry(
        id = id,
        vehicleId = testVehicleId,
        date = day(dayOffset),
        odometer = odometer,
        liters = liters,
        pricePerLiter = price,
        isFullTank = true,
        fuelType = fuelType,
    )

fun partial(
    dayOffset: Int,
    odometer: Double,
    liters: Double,
    price: BigDecimal = BigDecimal(5),
    id: UUID = UUID.randomUUID(),
    fuelType: FuelType? = null,
): FuelEntry = full(dayOffset, odometer, liters, price, id, fuelType).copy(isFullTank = false)

fun service(
    dayOffset: Int,
    odometer: Double,
    cost: BigDecimal,
): ServiceEntry = ServiceEntry(vehicleId = testVehicleId, date = day(dayOffset), odometer = odometer, cost = cost)

fun sample(
    dayOffset: Int,
    odometer: Double,
): OdometerSample = OdometerSample(day(dayOffset), odometer)

fun rule(
    kilometers: Double?,
    months: Int?,
): ServiceRule = ServiceRule(vehicleId = testVehicleId, kind = ServiceKind.OIL_CHANGE, kilometerInterval = kilometers, monthInterval = months)

fun expense(
    category: ExpenseCategory,
    amount: String,
    dayOffset: Int = 0,
    odometer: Double? = null,
    fine: FineDetails? = null,
    coverageStart: LocalDate? = null,
    coverageEnd: LocalDate? = null,
    vehicleId: UUID = testVehicleId,
): Expense =
    Expense(
        vehicleId = vehicleId,
        category = category,
        date = day(dayOffset),
        amount = BigDecimal(amount),
        odometer = odometer,
        coverageStart = coverageStart,
        coverageEnd = coverageEnd,
        fine = fine,
    )

fun dueItem(
    kind: DueKind,
    dueDate: LocalDate,
    vehicleId: UUID? = testVehicleId,
    amount: String? = null,
    paidAt: Instant? = null,
    id: UUID = UUID.randomUUID(),
): DueItem =
    DueItem(
        id = id,
        vehicleId = vehicleId,
        kind = kind,
        dueDate = dueDate,
        amount = amount?.let(::BigDecimal),
        paidAt = paidAt,
        createdAt = day(0),
    )
