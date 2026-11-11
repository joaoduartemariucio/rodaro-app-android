package br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.fipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.joaoduartemariucio.rodaro.app.NoAnalytics
import br.com.joaoduartemariucio.rodaro.domain.common.services.Analytics
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.FipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class FipePickerViewModel(
    private val kind: VehicleKind,
    private val catalog: FipeCatalog,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    enum class Step {
        BRAND,
        MODEL,
        YEAR,
    }

    var step by mutableStateOf(Step.BRAND)
        private set
    var brands by mutableStateOf(emptyList<FipeOption>())
        private set
    var models by mutableStateOf(emptyList<FipeOption>())
        private set
    var years by mutableStateOf(emptyList<FipeOption>())
        private set
    var query by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var error: Throwable? by mutableStateOf(null)
        private set

    var brand: FipeOption? by mutableStateOf(null)
        private set
    var model: FipeOption? by mutableStateOf(null)
        private set

    val options: List<FipeOption>
        get() =
            when (step) {
                Step.BRAND -> brands
                Step.MODEL -> models
                Step.YEAR -> years
            }

    val visibleOptions: List<FipeOption>
        get() {
            val words = normalized(query).split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return options
            return options.filter { option -> normalized(option.name).let { name -> words.all { it in name } } }
        }

    suspend fun start() {
        if (brands.isEmpty()) loading { brands = catalog.brands(kind) }
    }

    suspend fun retry() {
        when (step) {
            Step.BRAND -> start()
            Step.MODEL -> brand?.let { loadModels(it) }
            Step.YEAR -> loadYears()
        }
    }

    suspend fun select(option: FipeOption): FipeDetail? {
        when (step) {
            Step.BRAND -> {
                if (brand != option) models = emptyList()
                brand = option
                moveTo(Step.MODEL)
                if (models.isEmpty()) loadModels(option)
            }

            Step.MODEL -> {
                if (model != option) years = emptyList()
                model = option
                moveTo(Step.YEAR)
                if (years.isEmpty()) loadYears()
            }

            Step.YEAR -> {
                val brand = brand ?: return null
                val model = model ?: return null
                var detail: FipeDetail? = null
                loading { detail = catalog.detail(kind, brand.code, model.code, option) }
                detail?.let { analytics.event("fipe_vehicle_selected", mapOf("kind" to kind.name)) }
                return detail
            }
        }
        return null
    }

    fun back(): Boolean {
        error = null
        return when (step) {
            Step.BRAND -> {
                false
            }

            Step.MODEL -> {
                moveTo(Step.BRAND)
                true
            }

            Step.YEAR -> {
                moveTo(Step.MODEL)
                true
            }
        }
    }

    private fun moveTo(next: Step) {
        step = next
        query = ""
    }

    private suspend fun loadModels(brand: FipeOption) {
        loading { models = catalog.models(kind, brand.code) }
    }

    private suspend fun loadYears() {
        val brand = brand ?: return
        val model = model ?: return
        loading { years = catalog.years(kind, brand.code, model.code) }
    }

    private suspend fun loading(block: suspend () -> Unit) {
        isLoading = true
        error = null
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            error = failure
        } finally {
            isLoading = false
        }
    }

    private fun normalized(text: String): String =
        Normalizer
            .normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
}
