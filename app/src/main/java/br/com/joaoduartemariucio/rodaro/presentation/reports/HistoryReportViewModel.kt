package br.com.joaoduartemariucio.rodaro.presentation.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.fueling.repositories.FuelEntryRepository
import br.com.joaoduartemariucio.rodaro.domain.maintenance.repositories.ServiceRepository
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.HistoryOptions
import br.com.joaoduartemariucio.rodaro.domain.reports.calculations.VehicleHistories
import br.com.joaoduartemariucio.rodaro.domain.reports.services.ReportFileStore
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.Localizer
import br.com.joaoduartemariucio.rodaro.presentation.reports.pdf.ReportRenderer
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class HistoryReportViewModel(
    private val vehicle: Vehicle,
    private val fuelEntryRepository: FuelEntryRepository,
    private val serviceRepository: ServiceRepository,
    private val files: ReportFileStore,
    private val renderer: ReportRenderer,
    private val dateProvider: DateProvider,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    var includeCosts by mutableStateOf(false)
    var includeFuelLog by mutableStateOf(false)
    var showFullPlate by mutableStateOf(false)

    var state: FormState by mutableStateOf(FormState.Editing)
        private set

    val fileName: String
        get() {
            val slug =
                Normalizer
                    .normalize(vehicle.name, Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
                    .lowercase(Locale.ROOT)
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                    .ifEmpty { "veiculo" }
            val date = DateTimeFormatter.ISO_LOCAL_DATE.format(dateProvider.now.atZone(dateProvider.zone))
            return "rodaro-historico-$slug-$date.pdf"
        }

    suspend fun share(localizer: Localizer): String? =
        exporting("history_pdf_shared", localizer) { bytes -> files.shareable(fileName, bytes) }

    suspend fun save(
        location: String,
        localizer: Localizer,
    ): Boolean = exporting("history_pdf_saved", localizer) { bytes -> files.write(location, bytes) } != null

    private suspend fun <T> exporting(
        event: String,
        localizer: Localizer,
        block: suspend (ByteArray) -> T,
    ): T? {
        if (state.isSaving) return null
        state = FormState.Saving
        return try {
            val fuel = fuelEntryRepository.entries(vehicle.id)
            val services = serviceRepository.entries(vehicle.id)
            val options = HistoryOptions(includeCosts, includeFuelLog, showFullPlate)
            val history = VehicleHistories.build(vehicle, fuel, services, options, dateProvider.now, dateProvider.zone)
            val document = HistoryReportContent.document(history, localizer, dateProvider.zone)
            val bytes = renderer.render(document) { page, pages -> localizer.string(R.string.report_page, page, pages) }
            val result = block(bytes)
            analytics.event(
                event,
                mapOf(
                    "include_costs" to includeCosts.toString(),
                    "include_fuel_log" to includeFuelLog.toString(),
                    "full_plate" to showFullPlate.toString(),
                ),
            )
            state = FormState.Saved
            result
        } catch (error: CancellationException) {
            state = FormState.Editing
            throw error
        } catch (error: Exception) {
            state = FormState.Failed(error)
            null
        }
    }
}
