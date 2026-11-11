package br.com.joaoduartemariucio.rodaro.domain.vehicles.entities

import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeSelection
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Vehicle(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val kind: VehicleKind = VehicleKind.CAR,
    val plate: String? = null,
    val initialOdometer: Double = 0.0,
    val tankCapacity: Double? = null,
    val createdAt: Instant,
    val isArchived: Boolean = false,
    val fuelCapability: FuelCapability = FuelCapability.FLEX,
    val uf: String? = null,
    val modelYear: Int? = null,
    val fipe: FipeSelection? = null,
    val purchaseDate: LocalDate? = null,
    val purchasePrice: BigDecimal? = null,
) {
    companion object {
        val UF_CODES: List<String> =
            listOf(
                "AC",
                "AL",
                "AP",
                "AM",
                "BA",
                "CE",
                "DF",
                "ES",
                "GO",
                "MA",
                "MT",
                "MS",
                "MG",
                "PA",
                "PB",
                "PR",
                "PE",
                "PI",
                "RJ",
                "RN",
                "RS",
                "RO",
                "RR",
                "SC",
                "SP",
                "SE",
                "TO",
            )
    }
}
