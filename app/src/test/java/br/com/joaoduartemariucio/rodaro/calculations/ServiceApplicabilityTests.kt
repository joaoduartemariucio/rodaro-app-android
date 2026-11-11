package br.com.joaoduartemariucio.rodaro.calculations

import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.ServiceApplicability
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceApplicabilityTests {
    @Test
    fun `other is always offered, and always last`() {
        for (vehicleKind in VehicleKind.entries) {
            assertEquals(ServiceKind.OTHER, ServiceApplicability.kinds(vehicleKind).last())
            assertTrue(ServiceApplicability.isApplicable(ServiceKind.OTHER, vehicleKind))
        }
    }

    @Test
    fun `cars have no chain services and motorcycles have no timing belt`() {
        assertFalse(ServiceApplicability.isApplicable(ServiceKind.CHAIN, VehicleKind.CAR))
        assertFalse(ServiceApplicability.isApplicable(ServiceKind.CHAIN_KIT, VehicleKind.CAR))
        assertFalse(ServiceApplicability.isApplicable(ServiceKind.CVT_BELT, VehicleKind.CAR))
        assertFalse(ServiceApplicability.isApplicable(ServiceKind.TIMING_BELT, VehicleKind.MOTORCYCLE))
        assertFalse(ServiceApplicability.isApplicable(ServiceKind.CABIN_FILTER, VehicleKind.MOTORCYCLE))
        assertTrue(ServiceApplicability.isApplicable(ServiceKind.TIMING_BELT, VehicleKind.CAR))
        assertTrue(ServiceApplicability.isApplicable(ServiceKind.CHAIN_KIT, VehicleKind.MOTORCYCLE))
    }

    @Test
    fun `every service kind applies to at least one vehicle kind`() {
        val offered = VehicleKind.entries.flatMap(ServiceApplicability::kinds).toSet()

        assertEquals(ServiceKind.entries.toSet(), offered)
        assertEquals(16, ServiceApplicability.kinds(VehicleKind.CAR).size)
        assertEquals(16, ServiceApplicability.kinds(VehicleKind.MOTORCYCLE).size)
    }
}
