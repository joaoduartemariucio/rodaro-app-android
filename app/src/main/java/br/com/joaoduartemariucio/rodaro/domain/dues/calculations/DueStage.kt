package br.com.joaoduartemariucio.rodaro.domain.dues.calculations

sealed interface DueStage {
    val key: String

    data class DaysBefore(
        val days: Int,
    ) : DueStage {
        override val key: String get() = "before-$days"
    }

    data object DueToday : DueStage {
        override val key: String get() = "today"
    }

    data class Overdue(
        val days: Int,
    ) : DueStage {
        override val key: String get() = "overdue-$days"
    }
}
