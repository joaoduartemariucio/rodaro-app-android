package br.com.joaoduartemariucio.rodaro.presentation.backup

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object BackupSpreadsheet {
    private const val BOM = "\uFEFF"
    private const val LINE_END = "\r\n"
    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')

    private class Row(
        val vehicleName: String,
        val vehicleId: UUID,
        val date: Instant,
        val odometer: Double?,
        val cells: List<String>,
    )

    fun csv(
        snapshot: BackupSnapshot,
        localizer: Localizer,
        zone: ZoneId,
    ): String {
        val decimalSeparator = DecimalFormatSymbols.getInstance(localizer.locale).decimalSeparator
        val separator = if (decimalSeparator == ',') ';' else ','
        val vehicles = snapshot.vehicles.associateBy { it.id }

        fun number(value: BigDecimal): String = value.toPlainString().replace('.', decimalSeparator)

        fun number(value: Double): String = number(BigDecimal(value.toString()).stripTrailingZeros())

        fun day(instant: Instant): String = instant.atZone(zone).toLocalDate().toString()

        val fuelRows =
            snapshot.fuelEntries.mapNotNull { entry ->
                val vehicle = vehicles[entry.vehicleId] ?: return@mapNotNull null
                Row(
                    vehicle.name,
                    vehicle.id,
                    entry.date,
                    entry.odometer,
                    listOf(
                        userText(vehicle.name),
                        userText(vehicle.plate.orEmpty()),
                        localizer.string(R.string.csv_fuel_up),
                        day(entry.date),
                        number(entry.odometer),
                        number(entry.liters),
                        number(entry.pricePerLiter),
                        localizer.string(if (entry.isFullTank) R.string.csv_yes else R.string.csv_no),
                        "",
                        number(entry.totalCost.setScale(2, RoundingMode.HALF_EVEN)),
                        "",
                        entry.fuelType?.let { localizer.string(it.label) }.orEmpty(),
                        "",
                        "",
                    ),
                )
            }
        val serviceRows =
            snapshot.serviceEntries.mapNotNull { entry ->
                val vehicle = vehicles[entry.vehicleId] ?: return@mapNotNull null
                Row(
                    vehicle.name,
                    vehicle.id,
                    entry.date,
                    entry.odometer,
                    listOf(
                        userText(vehicle.name),
                        userText(vehicle.plate.orEmpty()),
                        localizer.string(R.string.csv_service_row),
                        day(entry.date),
                        number(entry.odometer),
                        "",
                        "",
                        "",
                        localizer.string(entry.kind.label),
                        number(entry.cost),
                        userText(entry.notes),
                        "",
                        userText(entry.workshop.orEmpty()),
                        "",
                    ),
                )
            }
        val expenseRows =
            snapshot.expenses.filter { it.isPaid }.mapNotNull { expense ->
                val vehicle = vehicles[expense.vehicleId] ?: return@mapNotNull null
                Row(
                    vehicle.name,
                    vehicle.id,
                    expense.date,
                    expense.odometer,
                    listOf(
                        userText(vehicle.name),
                        userText(vehicle.plate.orEmpty()),
                        localizer.string(R.string.csv_expense_row),
                        day(expense.date),
                        expense.odometer?.let(::number).orEmpty(),
                        "",
                        "",
                        "",
                        "",
                        number(expense.amount),
                        userText(expense.note),
                        "",
                        "",
                        localizer.string(expense.category.label),
                    ),
                )
            }
        val header =
            listOf(
                R.string.csv_vehicle,
                R.string.csv_plate,
                R.string.csv_type,
                R.string.csv_date,
                R.string.csv_odometer,
                R.string.csv_liters,
                R.string.csv_price_per_liter,
                R.string.csv_full_tank,
                R.string.csv_service,
                R.string.csv_total_cost,
                R.string.csv_notes,
                R.string.csv_fuel_type,
                R.string.csv_workshop,
                R.string.csv_category,
            ).map(localizer::string)
        val rows =
            (fuelRows + serviceRows + expenseRows)
                .sortedWith(compareBy<Row>({ it.vehicleName }, { it.vehicleId }, { it.date }, { it.odometer ?: 0.0 }))
                .map { it.cells }
        return BOM +
            (listOf(header) + rows).joinToString("") { cells ->
                cells.joinToString(separator.toString()) { quoted(it, separator) } +
                    LINE_END
            }
    }

    private fun userText(text: String): String = if (text.firstOrNull() in FORMULA_PREFIXES) "'$text" else text

    private fun quoted(
        field: String,
        separator: Char,
    ): String =
        if (field.any { it == separator || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
