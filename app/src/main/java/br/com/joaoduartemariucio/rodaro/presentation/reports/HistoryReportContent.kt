package br.com.joaoduartemariucio.rodaro.presentation.reports

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.VehicleHistory
import br.com.joaoduartemariucio.rodaro.presentation.common.Formats
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedConsumption
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedKilometers
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedLiters
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedPerLiter
import br.com.joaoduartemariucio.rodaro.presentation.common.label
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportBlock
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportDocument
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object HistoryReportContent {
    private const val NONE = "—"
    const val MAX_CELL_CHARACTERS = 500

    fun document(
        history: VehicleHistory,
        localizer: Localizer,
        zone: ZoneId,
    ): ReportDocument {
        val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(localizer.locale)
        val day = { instant: Instant -> dates.format(instant.atZone(zone)) }
        val month = { value: YearMonth -> DateTimeFormatter.ofPattern("MM/yyyy", localizer.locale).format(value) }
        val money = { value: BigDecimal -> Formats.currency(value, 2, localizer.locale) }
        val costs = history.options.includeCosts
        val blocks = mutableListOf<ReportBlock>()

        blocks += ReportBlock.Paragraph(localizer.string(R.string.report_disclaimer), emphasis = true)
        blocks +=
            ReportBlock.Paragraph(localizer.string(R.string.report_generated, day(history.generatedAt), history.recordCount.toString()))

        blocks += ReportBlock.Title(localizer.string(R.string.report_vehicle))
        blocks +=
            ReportBlock.Facts(
                listOfNotNull(
                    localizer.string(R.string.name) to history.vehicle.name,
                    localizer.string(R.string.report_kind) to localizer.string(history.vehicle.kind.label),
                    history.vehicle.modelYear?.let { localizer.string(R.string.vehicle_model_year) to it.toString() },
                    localizer.string(R.string.fuel_capability) to localizer.string(history.vehicle.fuelCapability.label),
                    history.plate?.let { localizer.string(R.string.plate) to it },
                    localizer.string(R.string.report_tracked_since) to day(history.trackedSince),
                    history.firstRecord?.let { localizer.string(R.string.report_first_record) to day(it) },
                    localizer.string(R.string.report_fuel_ups) to history.fuelUps.toString(),
                ),
            )

        if (history.odometerTimeline.isNotEmpty()) {
            blocks += ReportBlock.Title(localizer.string(R.string.report_odometer_timeline))
            blocks +=
                ReportBlock.Table(
                    listOf(localizer.string(R.string.report_col_month), localizer.string(R.string.report_col_km)),
                    listOf(1f, 1f),
                    history.odometerTimeline.map { listOf(month(it.month), it.odometer.formattedKilometers()) },
                )
        }

        if (history.regressions.isNotEmpty()) {
            blocks += ReportBlock.Title(localizer.string(R.string.report_anomalies))
            blocks +=
                ReportBlock.Table(
                    listOf(
                        localizer.string(R.string.report_col_date),
                        localizer.string(R.string.report_col_source),
                        localizer.string(R.string.report_col_previous),
                        localizer.string(R.string.report_col_recorded),
                    ),
                    listOf(1f, 1f, 1f, 1f),
                    history.regressions.map {
                        listOf(
                            day(it.date),
                            source(it.source, localizer),
                            it.previous.formattedKilometers(),
                            it.odometer.formattedKilometers(),
                        )
                    },
                )
        }

        if (history.services.isNotEmpty()) {
            blocks += ReportBlock.Title(localizer.string(R.string.report_services))
            blocks +=
                ReportBlock.Table(
                    listOfNotNull(
                        localizer.string(R.string.report_col_date),
                        localizer.string(R.string.report_col_km),
                        localizer.string(R.string.report_col_service),
                        localizer.string(R.string.report_col_workshop),
                        localizer.string(R.string.report_col_notes),
                        if (costs) localizer.string(R.string.report_col_cost) else null,
                    ),
                    listOfNotNull(1f, 1f, 1.4f, 1.2f, 2f, if (costs) 1f else null),
                    history.services.map { line ->
                        val service = localizer.string(line.entry.kind.label)
                        listOfNotNull(
                            day(line.entry.date),
                            line.entry.odometer.formattedKilometers(),
                            if (line.isRetroactive) "$service (${localizer.string(R.string.report_retroactive)})" else service,
                            line.entry.workshop
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::capped) ?: NONE,
                            capped(line.entry.notes.ifBlank { NONE }),
                            if (costs) money(line.entry.cost) else null,
                        )
                    },
                )
        }

        if (history.consumptionByGroup.isNotEmpty() || history.overallConsumption != null) {
            blocks += ReportBlock.Title(localizer.string(R.string.report_consumption))
            blocks +=
                ReportBlock.Facts(
                    history.consumptionByGroup.map { (group, value) ->
                        localizer.string(group.label) to
                            value.formattedConsumption(localizer)
                    } +
                        listOfNotNull(
                            history.overallConsumption?.let {
                                localizer.string(R.string.report_overall_consumption) to
                                    it.formattedConsumption(localizer)
                            },
                        ),
                )
        }

        if (history.kilometersByYear.isNotEmpty()) {
            blocks += ReportBlock.Title(localizer.string(R.string.report_km_per_year))
            blocks +=
                ReportBlock.Table(
                    listOf(localizer.string(R.string.report_col_year), localizer.string(R.string.report_col_km)),
                    listOf(1f, 1f),
                    history.kilometersByYear.toSortedMap().map { (year, kilometers) ->
                        listOf(year.toString(), kilometers.formattedKilometers())
                    },
                )
        }

        history.fuelLog?.takeIf { it.isNotEmpty() }?.let { log ->
            blocks += ReportBlock.Title(localizer.string(R.string.report_fuel_log))
            blocks +=
                ReportBlock.Table(
                    listOfNotNull(
                        localizer.string(R.string.report_col_date),
                        localizer.string(R.string.report_col_km),
                        localizer.string(R.string.report_col_liters),
                        localizer.string(R.string.report_col_fuel),
                        if (costs) localizer.string(R.string.report_col_price) else null,
                        if (costs) localizer.string(R.string.report_col_total) else null,
                    ),
                    listOfNotNull(1f, 1f, 1f, 1.4f, if (costs) 1f else null, if (costs) 1f else null),
                    log.map { line ->
                        val date = day(line.entry.date)
                        listOfNotNull(
                            if (line.isRetroactive) "$date (${localizer.string(R.string.report_retroactive)})" else date,
                            line.entry.odometer.formattedKilometers(),
                            line.entry.liters.formattedLiters(),
                            line.entry.fuelType?.let { localizer.string(it.label) } ?: NONE,
                            if (costs) line.entry.pricePerLiter.formattedPerLiter(localizer) else null,
                            if (costs) money(line.entry.totalCost) else null,
                        )
                    },
                )
        }

        history.totals?.let { totals ->
            blocks += ReportBlock.Title(localizer.string(R.string.report_totals))
            blocks +=
                ReportBlock.Facts(
                    listOf(
                        localizer.string(R.string.report_total_fuel) to money(totals.fuel),
                        localizer.string(R.string.report_total_services) to money(totals.services),
                    ),
                )
        }

        blocks += ReportBlock.Paragraph(localizer.string(R.string.report_footnote_retroactive))

        return ReportDocument(
            title = localizer.string(R.string.report_title),
            footer = localizer.string(R.string.report_footer, history.vehicle.name),
            blocks = blocks,
        )
    }

    fun capped(text: String): String = if (text.length <= MAX_CELL_CHARACTERS) text else text.take(MAX_CELL_CHARACTERS - 1).trimEnd() + "…"

    private fun source(
        source: VehicleHistory.Source,
        localizer: Localizer,
    ): String =
        when (source) {
            VehicleHistory.Source.VEHICLE -> localizer.string(R.string.report_source_vehicle)
            VehicleHistory.Source.FUEL -> localizer.string(R.string.report_source_fuel)
            VehicleHistory.Source.SERVICE -> localizer.string(R.string.report_source_service)
        }
}
