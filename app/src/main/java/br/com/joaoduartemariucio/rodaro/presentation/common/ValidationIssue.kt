package br.com.joaoduartemariucio.rodaro.presentation.common

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.vehicles.calculations.Odometer
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.OdometerSample
import java.time.Instant

sealed interface ValidationIssue {
    data object NameRequired : ValidationIssue

    data object OdometerRequired : ValidationIssue

    data class OdometerNotAfterPrevious(
        val previous: Double,
    ) : ValidationIssue

    data class OdometerNotBeforeNext(
        val next: Double,
    ) : ValidationIssue

    data object LitersRequired : ValidationIssue

    data object LitersNotPositive : ValidationIssue

    data object PriceRequired : ValidationIssue

    data object PriceNotPositive : ValidationIssue

    data object CostNegative : ValidationIssue

    data object RuleWithoutInterval : ValidationIssue

    data object FuelTypeRequired : ValidationIssue

    data object AmountRequired : ValidationIssue

    data object CoverageEndBeforeStart : ValidationIssue

    data object InstallmentInvalid : ValidationIssue

    data object PurchaseDateInFuture : ValidationIssue

    data object PurchasePriceNotPositive : ValidationIssue

    data class ModelYearOutOfRange(
        val min: Int,
        val max: Int,
    ) : ValidationIssue

    val isBlocking: Boolean
        get() = this !is OdometerNotAfterPrevious && this !is OdometerNotBeforeNext

    fun message(localizer: Localizer): String =
        when (this) {
            NameRequired -> localizer.string(R.string.issue_name_required)
            OdometerRequired -> localizer.string(R.string.issue_odometer_required)
            is OdometerNotAfterPrevious -> localizer.string(R.string.issue_odometer_not_after_previous, previous.formattedKilometers())
            is OdometerNotBeforeNext -> localizer.string(R.string.issue_odometer_not_before_next, next.formattedKilometers())
            LitersRequired -> localizer.string(R.string.issue_liters_required)
            LitersNotPositive -> localizer.string(R.string.issue_liters_not_positive)
            PriceRequired -> localizer.string(R.string.issue_price_required)
            PriceNotPositive -> localizer.string(R.string.issue_price_not_positive)
            CostNegative -> localizer.string(R.string.issue_cost_negative)
            RuleWithoutInterval -> localizer.string(R.string.issue_rule_without_interval)
            FuelTypeRequired -> localizer.string(R.string.issue_fuel_type_required)
            AmountRequired -> localizer.string(R.string.issue_amount_required)
            CoverageEndBeforeStart -> localizer.string(R.string.issue_coverage_end_before_start)
            InstallmentInvalid -> localizer.string(R.string.issue_installment_invalid)
            PurchaseDateInFuture -> localizer.string(R.string.issue_purchase_date_in_future)
            PurchasePriceNotPositive -> localizer.string(R.string.issue_purchase_price_not_positive)
            is ModelYearOutOfRange -> localizer.string(R.string.issue_model_year_out_of_range, min.toString(), max.toString())
        }

    companion object {
        fun odometer(
            odometer: Double?,
            date: Instant,
            otherReadings: List<OdometerSample>,
        ): List<ValidationIssue> {
            odometer ?: return listOf(OdometerRequired)

            val neighbors = Odometer.neighbors(date, otherReadings)
            val issues = mutableListOf<ValidationIssue>()
            neighbors.previous?.let { if (odometer <= it) issues += OdometerNotAfterPrevious(it) }
            neighbors.next?.let { if (odometer >= it) issues += OdometerNotBeforeNext(it) }
            return issues
        }
    }
}

val List<ValidationIssue>.blocksSaving: Boolean get() = any { it.isBlocking }
