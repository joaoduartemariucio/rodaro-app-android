package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceDefaults
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceInterval
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceDefaultsTests {
    @Test
    fun `cars get the suggested interval of each service`() {
        val expected =
            mapOf(
                ServiceKind.OIL_CHANGE to ServiceInterval(10_000.0, 12),
                ServiceKind.OIL_FILTER to ServiceInterval(10_000.0, 12),
                ServiceKind.AIR_FILTER to ServiceInterval(10_000.0, 12),
                ServiceKind.FUEL_FILTER to ServiceInterval(10_000.0, 12),
                ServiceKind.SPARK_PLUGS to ServiceInterval(30_000.0, 36),
                ServiceKind.BRAKES to ServiceInterval(10_000.0, 12),
                ServiceKind.TIRES to ServiceInterval(10_000.0, 12),
                ServiceKind.COOLANT to ServiceInterval(40_000.0, 24),
                ServiceKind.INSPECTION to ServiceInterval(10_000.0, 12),
                ServiceKind.TIMING_BELT to ServiceInterval(50_000.0, 48),
                ServiceKind.BRAKE_FLUID to ServiceInterval(null, 24),
                ServiceKind.BATTERY to ServiceInterval(null, 36),
                ServiceKind.ALIGNMENT to ServiceInterval(10_000.0, 12),
                ServiceKind.CABIN_FILTER to ServiceInterval(10_000.0, 12),
                ServiceKind.TRANSMISSION_OIL to ServiceInterval(60_000.0, 60),
            )

        for ((kind, interval) in expected) {
            assertEquals(interval, ServiceDefaults.interval(kind, VehicleKind.CAR), kind.name)
        }
    }

    @Test
    fun `motorcycles get their own, shorter intervals`() {
        val expected =
            mapOf(
                ServiceKind.OIL_CHANGE to ServiceInterval(3_000.0, 6),
                ServiceKind.OIL_FILTER to ServiceInterval(6_000.0, 12),
                ServiceKind.AIR_FILTER to ServiceInterval(10_000.0, 12),
                ServiceKind.FUEL_FILTER to ServiceInterval(10_000.0, 24),
                ServiceKind.SPARK_PLUGS to ServiceInterval(10_000.0, 24),
                ServiceKind.BRAKES to ServiceInterval(5_000.0, 6),
                ServiceKind.TIRES to ServiceInterval(5_000.0, 6),
                ServiceKind.CHAIN to ServiceInterval(1_000.0, 1),
                ServiceKind.CHAIN_KIT to ServiceInterval(20_000.0, null),
                ServiceKind.COOLANT to ServiceInterval(null, 24),
                ServiceKind.INSPECTION to ServiceInterval(6_000.0, 6),
                ServiceKind.BRAKE_FLUID to ServiceInterval(null, 24),
                ServiceKind.BATTERY to ServiceInterval(null, 24),
                ServiceKind.CVT_BELT to ServiceInterval(20_000.0, 24),
                ServiceKind.VALVE_CLEARANCE to ServiceInterval(12_000.0, null),
            )

        for ((kind, interval) in expected) {
            assertEquals(interval, ServiceDefaults.interval(kind, VehicleKind.MOTORCYCLE), kind.name)
        }
    }

    @Test
    fun `other and services that do not apply have no suggestion`() {
        assertNull(ServiceDefaults.interval(ServiceKind.OTHER, VehicleKind.CAR))
        assertNull(ServiceDefaults.interval(ServiceKind.OTHER, VehicleKind.MOTORCYCLE))
        assertNull(ServiceDefaults.interval(ServiceKind.CHAIN, VehicleKind.CAR))
        assertNull(ServiceDefaults.interval(ServiceKind.TIMING_BELT, VehicleKind.MOTORCYCLE))
    }

    @Test
    fun `timing belt, transmission oil and every motorcycle service need the strong manual hint`() {
        assertTrue(ServiceDefaults.needsStrongManualHint(ServiceKind.TIMING_BELT, VehicleKind.CAR))
        assertTrue(ServiceDefaults.needsStrongManualHint(ServiceKind.TRANSMISSION_OIL, VehicleKind.CAR))
        assertTrue(ServiceDefaults.needsStrongManualHint(ServiceKind.OIL_CHANGE, VehicleKind.MOTORCYCLE))
        assertFalse(ServiceDefaults.needsStrongManualHint(ServiceKind.OIL_CHANGE, VehicleKind.CAR))
    }
}
