package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.calculations.BackupFixtures
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelEntry
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import br.com.joaoduartemariucio.rodaro.presentation.backup.BackupSpreadsheet
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupSpreadsheetTests {
    private val english = TestLocalizer()
    private val portuguese = TestLocalizer("values-pt-rBR", Locale.forLanguageTag("pt-BR"))

    private fun lines(
        snapshot: BackupSnapshot = BackupFixtures.snapshot(),
        localizer: TestLocalizer = english,
        zone: ZoneId = ZoneOffset.UTC,
    ): List<String> =
        BackupSpreadsheet
            .csv(snapshot, localizer, zone)
            .removePrefix("\uFEFF")
            .removeSuffix("\r\n")
            .split("\r\n")

    @Test
    fun `starts with a byte order mark and ends every line with CRLF`() {
        val text = BackupSpreadsheet.csv(BackupFixtures.snapshot(), english, ZoneOffset.UTC)

        assertTrue(text.startsWith("\uFEFF"))
        assertTrue(text.endsWith("\r\n"))
        assertEquals(3, text.split("\r\n").size - 1)
    }

    @Test
    fun `English uses commas between fields and a decimal point`() {
        assertEquals(
            listOf(
                "Vehicle,Plate,Type,Date,Odometer (km),Liters,Price per liter,Full tank,Service,Total cost,Notes",
                "Gol,ABC1D23,Fuel-up,2025-03-01,1500,40.5,5.899,Yes,,238.91,",
                "Gol,ABC1D23,Service,2025-03-05,1600,,,,Oil change,120.00,Synthetic 5W30",
            ),
            lines(),
        )
    }

    @Test
    fun `Brazilian Portuguese uses semicolons and a decimal comma with localized headers`() {
        assertEquals(
            listOf(
                "Veículo;Placa;Tipo;Data;Odômetro (km);Litros;Preço por litro;Tanque cheio;Serviço;Custo total;Observações",
                "Gol;ABC1D23;Abastecimento;2025-03-01;1500;40,5;5,899;Sim;;238,91;",
                "Gol;ABC1D23;Serviço;2025-03-05;1600;;;;Troca de óleo;120,00;Synthetic 5W30",
            ),
            lines(localizer = portuguese),
        )
    }

    @Test
    fun `fields with separators, quotes or line breaks are quoted`() {
        val snapshot =
            BackupFixtures.snapshot().copy(
                serviceEntries = listOf(BackupFixtures.service.copy(notes = "Pads, \"OEM\"\nrear")),
            )

        val text = BackupSpreadsheet.csv(snapshot, english, ZoneOffset.UTC)

        assertTrue(text.contains(",Oil change,120.00,\"Pads, \"\"OEM\"\"\nrear\"\r\n"))
    }

    @Test
    fun `user text that looks like a formula is escaped`() {
        val vehicle = BackupFixtures.gol.copy(name = "=HYPERLINK(\"x\")", plate = "+55")
        val snapshot =
            BackupFixtures.snapshot().copy(
                vehicles = listOf(vehicle, BackupFixtures.cb),
                serviceEntries = listOf(BackupFixtures.service.copy(notes = "@sum")),
            )

        val rows = lines(snapshot).drop(1)

        assertTrue(rows.all { it.startsWith("\"'=HYPERLINK(\"\"x\"\")\",'+55,") })
        assertTrue(rows.last().endsWith(",'@sum"))
    }

    @Test
    fun `rows from every vehicle are combined and sorted by vehicle, date and odometer`() {
        val earlier =
            FuelEntry(
                id = UUID.randomUUID(),
                vehicleId = BackupFixtures.golId,
                date = Instant.parse("2025-03-05T10:00:00Z"),
                odometer = 1_550.0,
                liters = 10.0,
                pricePerLiter = BigDecimal("6"),
                isFullTank = false,
            )
        val bike = earlier.copy(id = UUID.randomUUID(), vehicleId = BackupFixtures.cbId, date = Instant.parse("2024-01-01T00:00:00Z"))
        val snapshot = BackupFixtures.snapshot().let { it.copy(fuelEntries = it.fuelEntries + earlier + bike) }

        val rows = lines(snapshot).drop(1).map { it.split(",").let { cells -> listOf(cells[0], cells[3], cells[4]) } }

        assertEquals(
            listOf(
                listOf("CB 500", "2024-01-01", "1550"),
                listOf("Gol", "2025-03-01", "1500"),
                listOf("Gol", "2025-03-05", "1550"),
                listOf("Gol", "2025-03-05", "1600"),
            ),
            rows,
        )
    }

    @Test
    fun `archived vehicles are included`() {
        val bikeService = BackupFixtures.service.copy(id = UUID.randomUUID(), vehicleId = BackupFixtures.cbId, kind = ServiceKind.TIRES)
        val snapshot = BackupFixtures.snapshot().let { it.copy(serviceEntries = it.serviceEntries + bikeService) }

        assertTrue(lines(snapshot).any { it.startsWith("CB 500,,Service,") && it.contains(",Tires,") })
    }

    @Test
    fun `fuel totals are rounded to two decimals`() {
        val entry = BackupFixtures.fuel.copy(liters = 33.333, pricePerLiter = BigDecimal("5.555"))
        val snapshot = BackupFixtures.snapshot().copy(fuelEntries = listOf(entry), serviceEntries = emptyList())

        assertEquals("185.16", lines(snapshot)[1].split(",")[9])
    }

    @Test
    fun `dates follow the given time zone`() {
        val entry = BackupFixtures.fuel.copy(date = Instant.parse("2025-03-01T01:00:00Z"))
        val snapshot = BackupFixtures.snapshot().copy(fuelEntries = listOf(entry), serviceEntries = emptyList())

        assertEquals("2025-02-28", lines(snapshot, zone = ZoneId.of("America/Sao_Paulo"))[1].split(",")[3])
    }

    @Test
    fun `rows of vehicles with the same name stay grouped by vehicle`() {
        val twin = BackupFixtures.cb.copy(name = "Gol", plate = "TWN0A00")
        val twinFuel = BackupFixtures.fuel.copy(id = UUID.randomUUID(), vehicleId = twin.id, date = Instant.parse("2025-03-03T10:00:00Z"))
        val snapshot =
            BackupFixtures.snapshot().let { it.copy(vehicles = listOf(BackupFixtures.gol, twin), fuelEntries = it.fuelEntries + twinFuel) }

        val plates = lines(snapshot).drop(1).map { it.split(",")[1] }

        assertEquals(listOf("ABC1D23", "ABC1D23", "TWN0A00"), plates)
    }
}
